package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.skills.construction.data.Oubliette
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.challengeMode
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting into and out of an oubliette.
 *
 * The prison gate only opens from outside for the owner; anyone shut in the pit has to pick its lock
 * (Thieving) or force it (Strength), and nobody else can let them out. It swings open as a single
 * door does and shuts again after [OPEN_TICKS].
 *
 * The ladder climbs to the room above - next to its trapdoor when it has one - and in challenge mode
 * only the owner may use it. A throne room trapdoor opens and closes in place and leads down beside
 * the oubliette's ladder, or into the pit when it has none.
 */
class OublietteScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private class Opened(val closed: String, val coords: CoordGrid, val angle: LocAngle, val shape: LocShape)

    private val opened = HashMap<CoordGrid, Opened>()

    override fun ScriptContext.startup() {
        for (cage in Oubliette.Cage.entries) {
            onOpLoc1(cage.door) { openGate(it.loc, cage) }
            onOpLoc2(cage.door) { escape(it.loc, cage, THIEVING, PICK_ANIM, "pick the lock") }
            onOpLoc3(cage.door) { escape(it.loc, cage, STRENGTH, FORCE_ANIM, "force the gate open") }
            onOpLoc1(cage.openDoor) { closeGate(it.loc) }
        }
        for (ladder in Oubliette.LADDERS) {
            onOpLoc1(ladder) { climbUp() }
        }
        for (trapdoor in Oubliette.TRAPDOORS) {
            val open = Oubliette.openTrapdoor(trapdoor)
            onOpLoc1(trapdoor) { swap(it.loc, open) }
            onOpLoc1(open) { goDown() }
            onOpLoc2(open) { swap(it.loc, trapdoor) }
        }
    }

    private fun ProtectedAccess.house(): ActiveHouse? = registry.houseAt(player.coords)

    private fun ProtectedAccess.inPit(house: ActiveHouse): Boolean = registry.pitAt(house, player.coords) != null

    // ------------------------------------------------------------------------ the prison

    private fun ProtectedAccess.openGate(gate: BoundLocInfo, cage: Oubliette.Cage) {
        val house = house() ?: return
        when {
            house.owner === player -> swing(gate, cage)
            inPit(house) -> mes("The gate is locked.")
            else -> mes("You can't open the cage from the outside.")
        }
    }

    private suspend fun ProtectedAccess.escape(
        gate: BoundLocInfo,
        cage: Oubliette.Cage,
        stat: String,
        anim: String,
        attempt: String,
    ) {
        val house = house() ?: return
        if (!inPit(house)) {
            mes("You can only do that from inside the cage.")
            return
        }
        anim(anim)
        delay(ATTEMPT_TICKS)
        if (!statRandom(stat, cage.chart.first, cage.chart.last, invisibleBoost = 0)) {
            mes("You fail to $attempt.")
            return
        }
        statAdvance(stat, cage.xp * xpMods.get(player, stat))
        mes("You manage to $attempt.")
        swing(gate, cage)
    }

    private fun swing(gate: BoundLocInfo, cage: Oubliette.Cage) {
        val panel = LocInfo(gate.layer, gate.coords, gate.entity)
        val to = DoorTranslations.translateOpen(panel.coords, panel.shape, panel.angle)
        val angle = LocAngle[(panel.angle.id + 1) and 3]
        val record = Opened(cage.door, panel.coords, panel.angle, panel.shape)
        locRepo.del(panel, Int.MAX_VALUE)
        opened[to] = record
        locRepo.add(to, cage.openDoor, OPEN_TICKS, angle, panel.shape) { shut(to, record) }
    }

    private fun closeGate(gate: BoundLocInfo) {
        val record = opened[gate.coords] ?: return
        locRepo.del(gate, Int.MAX_VALUE)
        shut(gate.coords, record)
    }

    private fun shut(at: CoordGrid, record: Opened) {
        if (opened.remove(at) == null) {
            return
        }
        locRepo.add(record.coords, record.closed, Int.MAX_VALUE, record.angle, record.shape)
    }

    // ------------------------------------------------------------- the ladder and trapdoor

    private fun ProtectedAccess.climbUp() {
        val house = house() ?: return
        if (house.owner !== player && house.owner.challengeMode) {
            mes("Only the owner can climb out while the house is in challenge mode.")
            return
        }
        val trapdoor = registry.locAcross(house, player.coords, up = true, TRAPDOOR_LOCS)
        val centre = registry.centreAcross(house, player.coords, up = true)
        val landing = (trapdoor?.coords ?: centre)?.let { mapFindSquareLineOfWalk(it, 1, 2) }
        if (landing == null) {
            mes("The ladder doesn't lead anywhere.")
            return
        }
        anim(CLIMB_ANIM)
        telejump(landing)
    }

    private fun ProtectedAccess.goDown() {
        val house = house() ?: return
        if (registry.pitBelow(house, player.coords) == null) {
            mes("There is nothing beneath the trapdoor.")
            return
        }
        val ladder = registry.locAcross(house, player.coords, up = false, Oubliette.LADDERS)
        val landing = ladder?.let { mapFindSquareLineOfWalk(it.coords, 1, 1) }
        if (landing == null) {
            player.queue(Oubliette.DROP_QUEUE, 1)
            return
        }
        anim(CLIMB_ANIM)
        telejump(landing)
    }

    private fun swap(loc: BoundLocInfo, into: String) {
        locRepo.del(loc, Int.MAX_VALUE)
        locRepo.add(loc.coords, into, Int.MAX_VALUE, loc.angle, loc.shape)
    }

    private companion object {
        private const val THIEVING = "stat.thieving"
        private const val STRENGTH = "stat.strength"
        private const val PICK_ANIM = "seq.human_picklock_cagedoor"
        private const val FORCE_ANIM = "seq.human_forcelock_cagedoor"
        private const val CLIMB_ANIM = "seq.human_reachforladder"
        private const val ATTEMPT_TICKS = 2
        private const val OPEN_TICKS = 100

        private val TRAPDOOR_LOCS =
            Oubliette.TRAPDOORS + Oubliette.TRAPDOORS.map(Oubliette::openTrapdoor)
    }
}
