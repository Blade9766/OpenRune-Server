package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.skills.construction.data.Oubliette
import org.rsmod.content.skills.construction.data.Oubliette.ThroneFloor
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseGuards
import org.rsmod.content.skills.construction.house.HouseMode
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.ThroneMat
import org.rsmod.content.skills.construction.house.challengeMode
import org.rsmod.content.skills.construction.house.houseMode
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The throne room lever. Pulling it springs the trap built on the room's floor, catching everyone
 * standing on the mat: a trapdoor drops them into the oubliette below, and a cage comes down over
 * them. Pulling it again lifts a steel cage; a magic cage instead asks the puller what to do with
 * its prisoners - release them, drop them into the oubliette, or (a greater cage) put them out of
 * the house. The greater cage's Teleport to... is not offered.
 *
 * A trapdoor floor looks like the style's floor decoration until it is sprung: then every tile of
 * the mat turns into the open pitfall for a few cycles, and its victims hang there looking down
 * before they drop, as the wiki describes. The cages play their own drop and lift animations.
 *
 * Its Challenge-mode op lets the owner put the house into challenge mode, which switches the
 * dungeon's traps and guards on against guests, or PvP challenge mode, which also lets players fight
 * each other anywhere in the dungeon; using it again switches either off.
 */
class ThroneRoomScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val guards: HouseGuards,
    private val players: PlayerList,
    private val houses: HouseAccess,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (lever in LEVERS) {
            onOpLoc1(lever) { pull(it.loc) }
            onOpLoc2(lever) { toggleChallengeMode() }
        }
    }

    private suspend fun ProtectedAccess.pull(lever: BoundLocInfo) {
        anim(PULL_ANIM)
        delay(1)
        val house = registry.houseAt(player.coords) ?: return
        val mat = registry.matAt(house, lever.coords)
        when {
            mat == null || mat.floor == ThroneFloor.DECORATION -> mes("Nothing interesting happens.")
            mat.lifting -> mes("The cage is still moving.")
            mat.floor == ThroneFloor.TRAPDOOR -> openTrapdoor(house, mat)
            mat.cage == null -> dropCage(mat)
            mat.floor == ThroneFloor.STEEL_CAGE -> release(mat)
            else -> magicCage(house, mat)
        }
    }

    private fun standingOn(mat: ThroneMat): List<Player> =
        players.filter { it.coords in mat.tiles }

    private fun ProtectedAccess.openTrapdoor(house: ActiveHouse, mat: ThroneMat) {
        if (registry.pitBelow(house, mat.corner) == null) {
            mes("Nothing interesting happens.")
            return
        }
        openFloor(mat)
        for (victim in standingOn(mat)) {
            victim.anim(FALL_SEQ)
            victim.queue(Oubliette.DROP_QUEUE, FALL_DELAY)
        }
    }

    /** Swaps each tile of the mat's floor decoration for the open pitfall, and back again after. */
    private fun openFloor(mat: ThroneMat) {
        for (tile in mat.tiles) {
            val floor = locRepo.findAll(tile).firstOrNull { locName(it.id)?.startsWith(FLOOR_PREFIX) == true } ?: continue
            val name = locName(floor.id) ?: continue
            val angle = floor.angle
            val shape = floor.shape
            locRepo.del(floor, Int.MAX_VALUE)
            locRepo.add(tile, PITFALL, OPEN_TICKS, angle, shape) {
                locRepo.add(tile, name, Int.MAX_VALUE, angle, shape)
            }
        }
    }

    private fun locName(id: Int): String? = runCatching { RSCM.getReverseMapping(RSCMType.LOC, id) }.getOrNull()

    private fun ProtectedAccess.dropCage(mat: ThroneMat) {
        val cage = mat.floor.cage ?: return
        val dropped = locRepo.add(mat.corner, cage, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        mat.cage = dropped
        cageAnim(mat.floor)?.let { locAnim(worldRepo, dropped, it) }
        for (victim in standingOn(mat)) {
            mat.held += victim
            victim.mes("You are trapped!")
        }
    }

    /** The animation a cage plays as it comes down. */
    private fun cageAnim(floor: ThroneFloor): String? =
        when (floor) {
            ThroneFloor.STEEL_CAGE -> STEEL_CAGE_DROP
            ThroneFloor.LESSER_MAGIC_CAGE -> LESSER_MAGIC_CAGE
            ThroneFloor.GREATER_MAGIC_CAGE -> GREATER_MAGIC_CAGE
            else -> null
        }

    private suspend fun ProtectedAccess.release(mat: ThroneMat) {
        if (mat.lifting) {
            return
        }
        val cage: LocInfo? = mat.cage
        // Taken off the mat before the lift, so a pull meanwhile can't lift or drop it a second time.
        mat.cage = null
        mat.lifting = true
        try {
            if (cage != null && mat.floor == ThroneFloor.STEEL_CAGE) {
                locAnim(worldRepo, cage, STEEL_CAGE_RAISE)
                delay(RAISE_TICKS)
            }
        } finally {
            mat.lifting = false
            cage?.let { locRepo.del(it, Int.MAX_VALUE) }
            // Freed even if the lift was cut short, or they would be held under no cage at all.
            for (prisoner in mat.held) {
                prisoner.mes("The cage lifts and you are free.")
            }
            mat.held.clear()
        }
        for (prisoner in mat.held) {
            prisoner.mes("The cage lifts and you are free.")
        }
        mat.held.clear()
    }

    private suspend fun ProtectedAccess.magicCage(house: ActiveHouse, mat: ThroneMat) {
        val choice =
            if (mat.floor == ThroneFloor.GREATER_MAGIC_CAGE) {
                choice3("Release", RELEASE, "Drop into oubliette", DROP, "Expel from house", EXPEL)
            } else {
                choice2("Release", RELEASE, "Drop into oubliette", DROP)
            }
        val prisoners = mat.held.toList()
        when (choice) {
            DROP -> {
                if (registry.pitBelow(house, mat.corner) == null) {
                    mes("There is no oubliette beneath this room.")
                    return
                }
                release(mat)
                for (prisoner in prisoners) {
                    prisoner.queue(Oubliette.DROP_QUEUE, FALL_DELAY)
                }
            }
            EXPEL -> {
                release(mat)
                for (prisoner in prisoners.filter { it !== house.owner }) {
                    houses.sendOut(prisoner, house)
                }
            }
            else -> release(mat)
        }
    }

    /** Asks which mode to set, or switches back to normal when a mode is already on. */
    private suspend fun ProtectedAccess.toggleChallengeMode() {
        val house = registry.houseAt(player.coords)
        if (house == null || house.owner !== player) {
            mes("Only the owner of this house can do that.")
            return
        }
        val mode =
            if (player.challengeMode) {
                HouseMode.OFF
            } else {
                choice2("Challenge mode", HouseMode.CHALLENGE, "PvP challenge mode", HouseMode.PVP)
            }
        player.houseMode = mode
        if (mode == HouseMode.OFF) {
            guards.standDown(player)
        }
        val notice =
            when (mode) {
                HouseMode.OFF -> "The house is no longer in challenge mode."
                HouseMode.CHALLENGE -> "The house is now in challenge mode."
                HouseMode.PVP -> "The house is now in PvP challenge mode: players may fight in the dungeon."
            }
        mes(notice)
        for (guest in registry.guests(house, players)) {
            guest.mes(notice)
        }
    }

    private companion object {
        val LEVERS = listOf("loc.poh_lever_oak_4", "loc.poh_lever_teak_4", "loc.poh_lever_mag_4")
        const val PULL_ANIM = "seq.poh_lever_pull"
        const val FALL_DELAY = 2
        const val FALL_SEQ = "seq.human_falling"

        /** The Desert Treasure pitfall, which the wiki gives as the sprung trapdoor's loc. */
        const val PITFALL = "loc.deserttreasure_pitfall_animated"
        const val FLOOR_PREFIX = "loc.poh_floordecor_"
        const val OPEN_TICKS = 4

        const val STEEL_CAGE_DROP = "seq.cage_throneroom_fall"
        const val STEEL_CAGE_RAISE = "seq.cage_throneroom_raise"
        const val LESSER_MAGIC_CAGE = "seq.magic_cage_lesser"
        const val GREATER_MAGIC_CAGE = "seq.magic_cage_greater"
        const val RAISE_TICKS = 2
        const val RELEASE = 0
        const val DROP = 1
        const val EXPEL = 2
    }
}
