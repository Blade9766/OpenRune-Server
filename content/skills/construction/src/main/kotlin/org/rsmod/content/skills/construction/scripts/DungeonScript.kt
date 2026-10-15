package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.other.pets.cats.CatCare
import org.rsmod.content.skills.construction.data.Dungeon
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Oubliette
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseAdverts
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HouseGuards
import org.rsmod.content.skills.construction.house.HousePvPHook
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.Pit
import org.rsmod.content.skills.construction.house.challengeMode
import org.rsmod.content.skills.construction.house.houseAttackOp
import org.rsmod.content.skills.construction.house.pvpMode
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A dungeon's locked doors and its traps.
 *
 * The owner simply opens a door; anyone else has to pick the lock (Thieving) or force it
 * (Strength) on the wiki's success charts. Both panels swing open together, placed just as the
 * generic double door script places them, and close again after [OPEN_TICKS].
 *
 * Guards are npcs [HouseGuards] stands in the dungeon.
 *
 * Everyone in a house carries [Dungeon.TRAP_TIMER], which looks after them every cycle. It holds
 * whoever a throne room cage has caught; while the owner has the house in either challenge mode it
 * lets an oubliette's pit hurt whoever is in it; and in PvP challenge mode it shows everyone in the
 * dungeon an Attack op, which [HousePvPHook] limits to fights inside it. Traps and guards only ever turn on visitors: the
 * timer sets nearby guards on them, and a trap underfoot is dodged on the Agility chart or springs,
 * then rests for [TRAP_REST] cycles.
 *
 * A throne room trapdoor drops its victims through [Oubliette.DROP_QUEUE] into the pit below; a
 * teleport trap sends its victim to the house's first oubliette, if it has one.
 */
class DungeonScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val players: PlayerList,
    private val games: HouseGames,
    private val houses: HouseAccess,
    private val adverts: HouseAdverts,
    private val guards: HouseGuards,
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
    private val catCare: CatCare,
) : PluginScript() {
    private class Opened(val closed: String, val coords: CoordGrid, val angle: LocAngle, val shape: LocShape)

    /** Every swung-open panel, by where it now stands, so Close can put it back. */
    private val opened = HashMap<CoordGrid, Opened>()

    override fun ScriptContext.startup() {
        for (door in Dungeon.Door.entries) {
            for (panel in listOf(door.left, door.right)) {
                onOpLoc1(panel) { open(it.loc, door, owner = true) }
                onOpLoc2(panel) { pickLock(it.loc, door) }
                onOpLoc3(panel) { force(it.loc, door) }
                onOpLoc1(door.opened(panel)) { close(it.loc) }
            }
        }
        onPlayerTimer(Dungeon.TRAP_TIMER) { checkTrap() }
        onPlayerQueue(Oubliette.DROP_QUEUE) { dropIntoOubliette() }
        // A guard gives no experience; it is spawned with none, and stays that way when it returns.
        val guardTypes = Dungeon.GUARDS.mapTo(HashSet()) { "npc.$it".asRSCM(RSCMType.NPC) }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.type.id in guardTypes) {
                npc.combatXpMultiplier = 0
            }
        }
    }

    // --------------------------------------------------------------------------- doors

    private fun ProtectedAccess.isOwner(): Boolean = registry.houseAt(player.coords)?.owner === player

    private fun ProtectedAccess.open(door: BoundLocInfo, tier: Dungeon.Door, owner: Boolean) {
        if (owner && !isOwner()) {
            mes("The door is locked.")
            return
        }
        swingOpen(door, tier)
    }

    private suspend fun ProtectedAccess.pickLock(door: BoundLocInfo, tier: Dungeon.Door) {
        val chart = if (LOCKPICK in inv) tier.pickWithLockpick else tier.pick
        anim(PICK_ANIM)
        delay(ATTEMPT_TICKS)
        if (!statRandom(THIEVING, chart.first, chart.last, invisibleBoost = 0)) {
            mes("You fail to pick the lock.")
            return
        }
        statAdvance(THIEVING, tier.xp * xpMods.get(player, THIEVING))
        mes("You pick the lock.")
        swingOpen(door, tier)
    }

    private suspend fun ProtectedAccess.force(door: BoundLocInfo, tier: Dungeon.Door) {
        anim(FORCE_ANIM)
        delay(ATTEMPT_TICKS)
        if (!statRandom(STRENGTH, tier.force.first, tier.force.last, invisibleBoost = 0)) {
            mes("You fail to force the door open.")
            return
        }
        statAdvance(STRENGTH, tier.xp * xpMods.get(player, STRENGTH))
        mes("You force the door open.")
        swingOpen(door, tier)
    }

    /**
     * Opens [door] and its partner panel. As in the double door script, a panel is the left one
     * when its partner stands at [DoorTranslations.translateClose]; the left swings back three
     * turns, the right one.
     */
    private fun swingOpen(door: BoundLocInfo, tier: Dungeon.Door) {
        val panel = LocInfo(door.layer, door.coords, door.entity)
        val partner = partnerOf(panel, tier)
        swing(panel, tier, left = partner == null || partner.coords == closeOf(panel))
        if (partner != null) {
            swing(partner, tier, left = closeOf(partner) == panel.coords)
        }
    }

    private fun partnerOf(panel: LocInfo, tier: Dungeon.Door): LocInfo? {
        val panels = setOf(tier.left, tier.right).mapTo(HashSet()) { RSCM.getRSCM(it) }
        val beside =
            listOf(
                closeOf(panel),
                DoorTranslations.translateCloseOpposite(panel.coords, panel.shape, panel.angle),
            )
        return beside.firstNotNullOfOrNull { coords ->
            locRepo.findAll(coords).firstOrNull { it.id in panels && it.layer == panel.layer }
        }
    }

    private fun closeOf(panel: LocInfo): CoordGrid =
        DoorTranslations.translateClose(panel.coords, panel.shape, panel.angle)

    private fun swing(panel: LocInfo, tier: Dungeon.Door, left: Boolean) {
        val closed = RSCM.getReverseMapping(RSCMType.LOC, panel.id)
        val turn = if (left) LEFT_OPEN_TURN else RIGHT_OPEN_TURN
        val to = DoorTranslations.translateOpen(panel.coords, panel.shape, panel.angle)
        val angle = LocAngle[(panel.angle.id + turn) and 3]
        locRepo.del(panel, Int.MAX_VALUE)
        val record = Opened(closed, panel.coords, panel.angle, panel.shape)
        opened[to] = record
        locRepo.add(to, tier.opened(closed), OPEN_TICKS, angle, panel.shape) { shut(to, record) }
    }

    private fun ProtectedAccess.close(door: BoundLocInfo) {
        val record = opened[door.coords] ?: return
        locRepo.del(door, Int.MAX_VALUE)
        shut(door.coords, record)
    }

    private fun shut(at: CoordGrid, record: Opened) {
        if (opened.remove(at) == null) {
            return
        }
        locRepo.add(record.coords, record.closed, Int.MAX_VALUE, record.angle, record.shape)
    }

    // ---------------------------------------------------------------------------- traps

    /** A house with a pet feeder looks after the kitten following anyone inside it. */
    private fun ProtectedAccess.tendKitten(house: ActiveHouse) {
        val feeder =
            house.state.rooms.values.any { room ->
                (room.type == RoomType.MENAGERIE_INDOOR || room.type == RoomType.MENAGERIE_OUTDOOR) &&
                    room.furniture.containsKey(PET_FEEDER)
            }
        if (feeder && catCare.tend(player)) {
            mes("The pet feeder looks after your kitten.")
        }
    }

    private fun ProtectedAccess.checkTrap() {
        val house = registry.houseAt(player.coords)
        if (house == null) {
            showAttackOp(false)
            clearTimer(Dungeon.TRAP_TIMER)
            // Whoever just left may have been the owner, or the last guest in a house its owner has gone from.
            houses.leftWithoutPortal(player)
            registry.sweep(players)
            return
        }
        games.settle(house.owner)
        adverts.markActive(house.owner)
        showAttackOp(house.owner.pvpMode && player.coords.level == Floor.DUNGEON.regionLevel)
        tendKitten(house)
        holdInCage(house)
        if (!house.owner.challengeMode) {
            return
        }
        hurtInPit(house)
        if (house.owner === player) {
            return
        }
        guards.engage(house.owner, player)
        val tile = player.coords
        val trap = house.traps[tile] ?: return
        if ((house.trapRest[tile] ?: 0) > mapClock) {
            return
        }
        house.trapRest[tile] = mapClock + TRAP_REST
        if (statRandom(AGILITY, Dungeon.DODGE.first, Dungeon.DODGE.last, invisibleBoost = 0)) {
            statAdvance(AGILITY, trap.dodgeXp * xpMods.get(player, AGILITY))
            mes("You dodge the ${trap.label}.")
            return
        }
        spring(house, trap)
    }

    /** In PvP challenge mode everyone in the dungeon gets an Attack op on everyone else. */
    private fun ProtectedAccess.showAttackOp(show: Boolean) {
        if (player.houseAttackOp == show) {
            return
        }
        player.houseAttackOp = show
        if (show) {
            MiscOutput.setPlayerOp(player, ATTACK_SLOT, ATTACK_OP, priority = true)
        } else {
            MiscOutput.clearPlayerOp(player, ATTACK_SLOT, ATTACK_OP)
        }
    }

    /** A throne room cage holds its victims where they stand until it is raised. */
    private fun ProtectedAccess.holdInCage(house: ActiveHouse) {
        for (mat in house.mats.values) {
            if (player !in mat.held) {
                continue
            }
            if (player.coords in mat.tiles) {
                CombatEffects.freeze(player, HOLD_TICKS)
            } else {
                mat.held -= player
            }
        }
    }

    private fun ProtectedAccess.hurtInPit(house: ActiveHouse) {
        val hazard = registry.pitAt(house, player.coords)?.hazard ?: return
        if (hazard.interval == 0 || mapClock % hazard.interval != 0) {
            return
        }
        queueHit(delay = 1, type = HitType.Typeless, damage = random.of(hazard.damage))
    }

    private fun ProtectedAccess.dropIntoOubliette() {
        val house = registry.houseAt(player.coords) ?: return
        val pit = registry.pitBelow(house, player.coords) ?: return
        mes("You fall through the floor!")
        land(house, pit)
    }

    /** Puts the player somewhere in [pit]; spikes catch them on the way down in challenge mode. */
    private fun ProtectedAccess.land(house: ActiveHouse, pit: Pit) {
        val tiles = pit.tiles.toList()
        telejump(tiles[random.of(0, tiles.size - 1)])
        anim(LAND_SEQ)
        if (pit.hazard == Oubliette.Hazard.SPIKES && house.owner.challengeMode) {
            mes(Oubliette.Hazard.SPIKES.message)
            queueHit(delay = 1, type = HitType.Typeless, damage = random.of(Oubliette.Hazard.SPIKES.damage))
        }
    }

    private fun ProtectedAccess.spring(house: ActiveHouse, trap: Dungeon.Trap) {
        when (trap.effect) {
            Dungeon.Effect.SPIKES -> {
                mes("Spikes shoot up from the floor!")
                queueHit(delay = 1, type = HitType.Typeless, damage = random.of(Dungeon.SPIKE_DAMAGE))
            }
            Dungeon.Effect.CRUSH -> {
                mes("The man trap snaps shut on you!")
                queueHit(delay = 1, type = HitType.Typeless, damage = random.of(Dungeon.CRUSH_DAMAGE))
            }
            Dungeon.Effect.TANGLE -> {
                mes("The vines tangle around you!")
                CombatEffects.freeze(player, Dungeon.tangleTicks(player.agilityLvl))
            }
            Dungeon.Effect.DRAIN -> {
                mes("The marble trap trips you up.")
                statDrain(AGILITY, Dungeon.marbleDrain(player.agilityLvl), 0)
            }
            Dungeon.Effect.TELEPORT -> {
                mes("The floor gives way beneath you!")
                val pit = house.pits.values.firstOrNull { it.tiles.isNotEmpty() }
                if (pit != null) {
                    land(house, pit)
                    return
                }
                val landing = randomDungeonTile(house) ?: return
                telejump(landing)
            }
        }
    }

    /** With no oubliette to drop into, a teleport trap sends its victim somewhere in the dungeon. */
    private fun ProtectedAccess.randomDungeonTile(house: ActiveHouse): CoordGrid? {
        val cells = house.state.cells(Floor.DUNGEON)
        if (cells.isEmpty()) {
            return null
        }
        val (gx, gz) = cells[random.of(0, cells.size - 1)]
        return registry.zoneOf(house, Floor.DUNGEON, gx, gz).toCoords().translate(ROOM_CENTRE, ROOM_CENTRE, 0)
    }

    private companion object {

        private const val PET_FEEDER = "pet_feeder"
        private const val LAND_SEQ = "seq.human_falling_end"
        private const val THIEVING = "stat.thieving"
        private const val STRENGTH = "stat.strength"
        private const val AGILITY = "stat.agility"
        private const val LOCKPICK = "obj.lockpick"
        private const val PICK_ANIM = "seq.human_picklock_cagedoor"
        private const val FORCE_ANIM = "seq.human_forcelock_cagedoor"
        private const val ATTEMPT_TICKS = 2
        private const val OPEN_TICKS = 100
        private const val TRAP_REST = 10
        private const val LEFT_OPEN_TURN = 3
        private const val RIGHT_OPEN_TURN = 1
        private const val ROOM_CENTRE = 3
        private const val HOLD_TICKS = 2
        private const val ATTACK_SLOT = 1
        private const val ATTACK_OP = "Attack"
    }
}
