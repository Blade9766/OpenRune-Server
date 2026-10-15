package org.rsmod.content.quest.area.burthorpe.trollstronghold

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.feet
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.DAD
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The climb from Tenzing's hut to Trollheim: the rocks that need climbing boots and 15 Agility,
 * the short rocks off the Burthorpe side of Death Plateau, Dad's arena and the cave under the
 * mountain north of it.
 *
 * Every pair of rocks is crossed straight over: the player is carried along the axis the rocks
 * block to the first tile past them, so each [RockCrossing] only records which tiles hold rock
 * and which way it is crossed.
 */
class TrollCountry
@Inject
constructor(
    private val quest: TrollStrongholdQuest,
    private val passages: GenericPassageScript,
    private val stairs: StairNavigator,
    private val dad: DadsArena,
) : PluginScript() {

    private val arenaEntrances = ARENA_ENTRANCES.associateWith(::locType)
    private val arenaExits = ARENA_EXITS.associateWith(::locType)

    override fun ScriptContext.startup() {
        for (rock in TROLL_ROCKS) {
            onOpLoc1(rock) { climbTrollRocks(it.loc) }
        }
        for (rock in PLATEAU_ROCKS) {
            onOpLoc1(rock) { climbPlateauRocks(it.loc) }
        }
        for ((gate, type) in arenaEntrances) {
            onOpLoc1(gate) { arenaEntrance(it.loc, type) }
        }
        for ((gate, type) in arenaExits) {
            onOpLoc1(gate) { arenaExit(it.loc, type) }
        }
        onOpLoc1(PASS_ENTRANCE) { passEntrance(it.loc) }
        onOpLoc1(PASS_EXIT) { passExit(it.loc) }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    /* Rocks */

    private suspend fun ProtectedAccess.climbTrollRocks(rock: BoundLocInfo) {
        arriveDelay()
        if (player.agilityLvl < AGILITY_REQ) {
            mes("You need an Agility level of at least $AGILITY_REQ to attempt this.")
            return
        }
        if (!player.wearingClimbingBoots()) {
            mes("You'll need some climbing boots to go that way.")
            return
        }
        val dest = crossingFrom(rock.coords) ?: return
        mes("You climb onto the rock...")
        if (random.of(FAIL_ROLL) < failChance(player.agilityLvl)) {
            anim(CLIMB_SEQ)
            delay(CLIMB_TICKS)
            resetAnim()
            mes("...but you slip back down.")
            player.say("Ouch")
            return
        }
        cross(dest)
        mes("...and you step down the other side.")
    }

    private suspend fun ProtectedAccess.climbPlateauRocks(rock: BoundLocInfo) {
        arriveDelay()
        if (!QuestRequirements.hasCompleted(player, DeathPlateauQuest.QUEST_KEY)) {
            mes("That looks dangerous. I'll need a good reason before I venture that way.")
            return
        }
        val dest = crossingFrom(rock.coords) ?: return
        cross(dest)
    }

    private fun ProtectedAccess.crossingFrom(rock: CoordGrid): CoordGrid? {
        val crossing = CROSSINGS.firstOrNull { rock in it.rocks } ?: return null
        val dest = crossing.far(coords)
        if (dest == null || !stairs.walkable(dest)) {
            mes("You can't climb over from here.")
            return null
        }
        return dest
    }

    private suspend fun ProtectedAccess.cross(dest: CoordGrid) {
        val start = coords
        val ticks = start.chebyshevDistance(dest).coerceAtLeast(1)
        anim(CLIMB_SEQ)
        exactMove(
            start = start,
            end = dest,
            delay1 = 0,
            delay2 = ticks * CLIENT_CYCLES_PER_TICK,
            dir = faceTowards(start, dest),
            teleportType = TeleportType.Exempt,
        )
        delay(ticks)
    }

    /* Dad's arena */

    /**
     * The arena gates stand open to anyone. Dad stops the player the first time they walk in
     * during the quest; the exit gates north are where he holds anyone who has not beaten him.
     */
    private suspend fun ProtectedAccess.arenaEntrance(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val entering = coords.x < gate.coords.x
        with(passages) { walkThrough(gate, type) }
        if (!entering || quest.stage(player) != STAGE_STARTED || player.tsAcceptedChallenge) {
            return
        }
        delay(WALK_IN_TICKS)
        startDialogue { with(dad) { challenge() } }
    }

    private suspend fun ProtectedAccess.arenaExit(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.z < gate.coords.z
        if (!leaving || quest.hasBeatenDad(player)) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        startDialogue {
            chatNpcSpecific("Dad", DAD, angry, "No human pass through arena without defeating Dad!")
        }
    }

    /* The cave under the mountain */

    private suspend fun ProtectedAccess.passEntrance(mouth: BoundLocInfo) {
        arriveDelay()
        val dest = if (mouth.coords == SOUTH_MOUTH) CAVE_SOUTH_LANDING else CAVE_NORTH_LANDING
        delay(1)
        telejump(stairs.landing(dest) ?: dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.passExit(exit: BoundLocInfo) {
        arriveDelay()
        val dest = if (exit.coords == CAVE_SOUTH_EXIT) SOUTH_MOUTH_LANDING else NORTH_MOUTH_LANDING
        delay(1)
        telejump(stairs.landing(dest) ?: dest, TeleportType.Exempt)
    }

    private fun Player.wearingClimbingBoots(): Boolean =
        CLIMBING_BOOTS_TYPES.any { feet?.isType(it) == true }

    private fun failChance(agility: Int): Int =
        (BASE_FAIL_CHANCE - (agility - AGILITY_REQ)).coerceAtLeast(0)

    /** Rock tiles and whether they are crossed north-south ([vertical]) or east-west. */
    private class RockCrossing(val rocks: Set<CoordGrid>, private val vertical: Boolean) {
        private val minX = rocks.minOf { it.x }
        private val maxX = rocks.maxOf { it.x }
        private val minZ = rocks.minOf { it.z }
        private val maxZ = rocks.maxOf { it.z }

        fun far(from: CoordGrid): CoordGrid? =
            if (vertical) {
                val x = from.x.coerceIn(minX, maxX)
                when {
                    from.z < minZ -> CoordGrid(x, maxZ + 1, from.level)
                    from.z > maxZ -> CoordGrid(x, minZ - 1, from.level)
                    else -> null
                }
            } else {
                val z = from.z.coerceIn(minZ, maxZ)
                when {
                    from.x < minX -> CoordGrid(maxX + 1, z, from.level)
                    from.x > maxX -> CoordGrid(minX - 1, z, from.level)
                    else -> null
                }
            }
    }

    private companion object {
        val TROLL_ROCKS =
            listOf("loc.troll_climbingrocks", "loc.troll_climbingrocks_top", "loc.troll_climbingrocks_bottom")
        val PLATEAU_ROCKS = listOf("loc.death_climbingrocks_top", "loc.death_climbingrocks_bottom")

        val ARENA_ENTRANCES =
            listOf("loc.troll_stronghold_arena_entrance_left", "loc.troll_stronghold_arena_entrance_right")
        val ARENA_EXITS =
            listOf("loc.troll_stronghold_arena_exit_left", "loc.troll_stronghold_arena_exit_right")

        const val PASS_ENTRANCE = "loc.troll_pass_entrance"
        const val PASS_EXIT = "loc.troll_pass_exit"

        /** The cave mouth north of the arena and the one on the far side of the mountain. */
        val SOUTH_MOUTH = CoordGrid(2903, 3644, 0)
        val CAVE_SOUTH_EXIT = CoordGrid(2906, 10017, 0)
        val CAVE_SOUTH_LANDING = CoordGrid(2907, 10019, 0)
        val CAVE_NORTH_LANDING = CoordGrid(2907, 10035, 0)
        val SOUTH_MOUTH_LANDING = CoordGrid(2904, 3643, 0)
        val NORTH_MOUTH_LANDING = CoordGrid(2908, 3654, 0)

        fun tiles(vararg coords: Pair<Int, Int>): Set<CoordGrid> =
            coords.map { (x, z) -> CoordGrid(x, z, 0) }.toSet()

        val CROSSINGS =
            listOf(
                RockCrossing(tiles(2856 to 3612, 2857 to 3612), vertical = true),
                RockCrossing(tiles(2833 to 3628, 2834 to 3628), vertical = true),
                RockCrossing(tiles(2821 to 3635), vertical = false),
                RockCrossing(tiles(2910 to 3686, 2910 to 3687), vertical = false),
                RockCrossing(tiles(2859 to 3626, 2859 to 3627, 2860 to 3626, 2860 to 3627), vertical = false),
                RockCrossing(tiles(2878 to 3622, 2878 to 3623, 2879 to 3622, 2879 to 3623), vertical = false),
                RockCrossing(tiles(2880 to 3594, 2881 to 3594, 2880 to 3595, 2881 to 3595), vertical = true),
            )

        val CLIMBING_BOOTS_TYPES =
            listOf("obj.death_climbingboots", "obj.climbing_boots_g", "obj.br_climbing_boots")

        const val FAIL_ROLL = 100
        const val BASE_FAIL_CHANCE = 20
        const val CLIMB_SEQ = "seq.human_walk_style"
        const val CLIMB_TICKS = 2
        const val WALK_IN_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30

        fun faceTowards(from: CoordGrid, to: CoordGrid): Int =
            when {
                to.z > from.z -> constants.em_face_north
                to.z < from.z -> constants.em_face_south
                to.x > from.x -> constants.em_face_east
                else -> constants.em_face_west
            }
    }
}
