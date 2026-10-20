package org.rsmod.content.areas.misc.waterbirth

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way in and out of Waterbirth Island Dungeon: the cave mouth and the steps below it, the
 * mountain-top cave and the iron ladder in the ladder room, the ladder chain through sublevels 2-5
 * and the Kings' ladder down to the Dagannoth Kings' lair.
 */
class WaterbirthDungeon
@Inject
constructor(
    private val playerList: PlayerList,
    private val locRepo: LocRepository,
    private val passages: GenericPassageScript,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.dagannoth_caveentrance_rock") { travel(WaterbirthCoords.CAVERN_STEPS) }
        onOpLoc1("loc.dagannoth_rocky_steps") { climb(WaterbirthCoords.CAVE_MOUTH, up = true) }
        onOpLoc1("loc.dagannoth_caveentrance_ice") { enterMountainCave(it.loc, it.type) }

        onOpLoc1("loc.dagannoth_ladder_base_ext2") { ironLadder() }
        onOpLoc2("loc.dagannoth_ladder_base_ext2") { climb(WaterbirthCoords.MOUNTAIN_TOP, up = true) }
        onOpLoc3("loc.dagannoth_ladder_base_ext2") {
            climb(WaterbirthCoords.SUBLEVEL_2_ENTRANCE, up = false)
        }

        onOpLoc1("loc.dagexp_entrance_ladder") { climb(WaterbirthCoords.LADDER_ROOM, up = true) }
        onOpLoc1("loc.dagexp_exit_ladder") { lighthouseExit() }
        for (n in DUNGEON_LADDERS) {
            onOpLoc1(ladderName(n)) { dungeonLadder(n, it.loc, it.type) }
        }

        for (ladder in KINGS_LADDERS_DOWN) {
            onOpLoc1(ladder) { climb(WaterbirthCoords.KINGS_LAIR, up = false) }
            onOpLoc2(ladder) { slayerLair() }
            onOpLoc4(ladder) { peek() }
        }
        onOpLoc3("loc.dagexp_bossroomladder_down_private") {
            mes("Private instances of the lair are not available here.")
        }
        onOpLoc1("loc.dagexp_bossroomladder_up") {
            climb(WaterbirthCoords.KINGS_LADDER_TOP, up = true)
        }
    }

    private suspend fun ProtectedAccess.travel(dest: CoordGrid) {
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.enterMountainCave(loc: BoundLocInfo, type: ObjectServerType) {
        if (loc.coords.chebyshevDistance(WaterbirthCoords.MOUNTAIN_TOP) > MOUNTAIN_CAVE_RADIUS) {
            with(passages) { passage(loc, type, 0) }
            return
        }
        travel(WaterbirthCoords.LADDER_ROOM)
    }

    private suspend fun ProtectedAccess.ironLadder() {
        val up = choice2("Climb up.", true, "Climb down.", false, title = "Climb up or down?")
        if (up) {
            climb(WaterbirthCoords.MOUNTAIN_TOP, up = true)
        } else {
            climb(WaterbirthCoords.SUBLEVEL_2_ENTRANCE, up = false)
        }
    }

    private suspend fun ProtectedAccess.dungeonLadder(n: Int, loc: BoundLocInfo, type: ObjectServerType) {
        val up = type.actions.getOpOrNull(0) != CLIMB_DOWN
        val level = loc.coords.level + if (up) 1 else -1
        val partnerType = checkNotNull(ServerCacheManager.getObject(ladderName(partnerOf(n)).asRSCM(RSCMType.LOC)))
        val partner =
            ORTHOGONAL.map { (dx, dz) -> CoordGrid(loc.coords.x + dx, loc.coords.z + dz, level) }
                .firstOrNull { locRepo.findExact(it, partnerType) != null }
        if (partner == null) {
            mes(if (up) "You cannot see a way up from here." else "You cannot see a way down from here.")
            return
        }
        climb(ladderLanding(loc.coords, partner), up)
    }

    private suspend fun ProtectedAccess.lighthouseExit() {
        if (!QuestRequirements.hasCompleted(player, HORROR_FROM_THE_DEEP)) {
            mes("You need to complete Horror from the Deep to use this ladder.")
            return
        }
        climb(WaterbirthCoords.LIGHTHOUSE_CAVE, up = true)
    }

    private suspend fun ProtectedAccess.slayerLair() {
        val task = SlayerTaskManager.getCurrentSlayerTask(this)
        if (task == null || DAGANNOTH !in task.nameLowercase) {
            mes("You need a dagannoth slayer task to enter this lair.")
            return
        }
        climb(WaterbirthCoords.SLAYER_LAIR, up = false)
    }

    private fun ProtectedAccess.peek() {
        val inside =
            playerList.count {
                it.coords.x in WaterbirthCoords.KINGS_LAIR_MIN_X..WaterbirthCoords.KINGS_LAIR_MAX_X &&
                    it.coords.z in WaterbirthCoords.KINGS_LAIR_MIN_Z..WaterbirthCoords.KINGS_LAIR_MAX_Z
            }
        when (inside) {
            0 -> mes("You peek down and see no adventurers inside the lair.")
            1 -> mes("You peek down and see 1 adventurer inside the lair.")
            else -> mes("You peek down and see $inside adventurers inside the lair.")
        }
    }

    private suspend fun ProtectedAccess.climb(dest: CoordGrid, up: Boolean) {
        anim(if (up) CLIMB_UP_ANIM else CLIMB_DOWN_ANIM)
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    companion object {
        private const val CLIMB_DOWN = "Climb-down"
        private const val CLIMB_UP_ANIM = "seq.human_reachforladder"
        private const val CLIMB_DOWN_ANIM = "seq.human_pickupfloor"
        private const val MOUNTAIN_CAVE_RADIUS = 16
        private const val HORROR_FROM_THE_DEEP = "quest_horrorfromthedeep"
        private const val DAGANNOTH = "dagannoth"
        val DUNGEON_LADDERS = 1..34

        private val ORTHOGONAL = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)

        fun ladderName(n: Int): String = "loc.dagexp_ladder$n"

        /** Sublevel ladders come in pairs, 1 with 2, 3 with 4 and so on. */
        fun partnerOf(n: Int): Int = if (n % 2 == 1) n + 1 else n - 1

        /**
         * Each sublevel ladder stands one tile from its partner on the next level, with its back
         * to the partner's back, so the climb lands in front of the partner.
         */
        fun ladderLanding(ladder: CoordGrid, partner: CoordGrid): CoordGrid =
            CoordGrid(2 * partner.x - ladder.x, 2 * partner.z - ladder.z, partner.level)
        private val KINGS_LADDERS_DOWN =
            listOf("loc.dagexp_bossroomladder_down_normal", "loc.dagexp_bossroomladder_down_private")
    }
}
