package org.rsmod.content.quest.area.lunarisle.dreammentor

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

/**
 * Cyrisus' dream: a private copy of map square 28_80, whose top floor is the round arena where
 * his fears wait. Scripts work in world coordinates and translate through [at] and [toWorld]. The
 * instance's exit, where anyone leaving or dying in the dream ends up (or their grave does), is
 * beside the Dream Hall brazier.
 */
@Singleton
class DreamArena @Inject constructor(private val manager: InstanceManager) {
    private class Dream(val session: InstanceSession, val dx: Int, val dz: Int)

    private val dreams = HashMap<PlayerUid, Dream>()

    fun isDreaming(player: Player): Boolean {
        val dream = dreams[player.uid] ?: return false
        if (manager.sessionForPlayer(player) !== dream.session) {
            dreams.remove(player.uid)
            return false
        }
        return true
    }

    fun at(player: Player, world: CoordGrid): CoordGrid {
        val dream = dreams[player.uid] ?: return world
        return CoordGrid(world.x + dream.dx, world.z + dream.dz, world.level)
    }

    fun toWorld(player: Player, coords: CoordGrid): CoordGrid {
        val dream = dreams[player.uid] ?: return coords
        return CoordGrid(coords.x - dream.dx, coords.z - dream.dz, coords.level)
    }

    fun worldCoords(player: Player): CoordGrid = toWorld(player, player.coords)

    /** Ties [npc] to the player's dream so it is removed with it. */
    fun attach(player: Player, npc: Npc) {
        val dream = dreams[player.uid] ?: return
        manager.attachNpc(dream.session.id, npc)
    }

    /** Copies the dream for the player and puts them at [PLAYER_START]; false if none could be made. */
    fun ProtectedAccess.enterDream(): Boolean {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return false
        }
        val area =
            InstanceArea.copyRegions(
                regionIds = REGIONS,
                level = ANCHOR.level,
                enterCoord = RegionLocal(ANCHOR.level, ANCHOR.mx, ANCHOR.mz, ANCHOR.lx, ANCHOR.lz),
                exitCoord = DREAM_HALL,
                npcSpawns = emptyList(),
            )
        val spec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = RECLAIM_TICKS,
                graceTicks = GRACE_TICKS,
                destroyWhenEmpty = true,
                area = area,
                settingsRowId = -1,
                bossName = "",
            )
        val (session, enter) =
            when (val result = manager.create(player, KEY, spec, InstanceAccess.Private, mapClock)) {
                is InstanceManager.Result.Failed -> {
                    mes(result.reason)
                    return false
                }
                is InstanceManager.Result.Created -> result.session to result.enter
                is InstanceManager.Result.Joined -> result.session to result.enter
            }
        telejump(enter, TeleportType.Exempt)
        if (player.coords != enter) {
            manager.leave(player, session, mapClock)
            mes("Somehow you can't drift off here.")
            return false
        }
        manager.finalizeEntry(player, session, mapClock)
        val dream = Dream(session, enter.x - ANCHOR.x, enter.z - ANCHOR.z)
        dreams[player.uid] = dream
        telejump(at(player, PLAYER_START), TeleportType.Exempt)
        return true
    }

    /** Leaves the dream, waking beside the Dream Hall brazier. */
    fun ProtectedAccess.wake() {
        val dream = dreams.remove(player.uid)
        val session = dream?.session ?: manager.sessionForPlayer(player)?.takeIf { it.key == KEY }
        if (session != null) {
            manager.leave(player, session, mapClock)
        }
        telejump(DREAM_HALL, TeleportType.Exempt)
    }

    fun forget(player: Player) {
        dreams.remove(player.uid)
    }

    companion object {
        const val KEY = "dream_mentor_dream"

        private val REGIONS = listOf((28 shl 8) or 80)
        private const val RECLAIM_TICKS = 100
        private const val GRACE_TICKS = 50

        /** The middle of the arena; the instance manager drops anyone more than 64 tiles from it. */
        private val ANCHOR = CoordGrid(1824, 5151, 2)

        val DREAM_HALL = CoordGrid(2075, 3911, 0)

        /** Below the lectern ("Our lives") at the north edge of the arena. */
        val LECTERN = CoordGrid(1824, 5166, 2)
        val PLAYER_START = CoordGrid(1822, 5161, 2)
        val CYRISUS_START = CoordGrid(1826, 5161, 2)
        val BOSS_SPAWN = CoordGrid(1822, 5147, 2)

        fun inArena(world: CoordGrid): Boolean =
            world.level == ANCHOR.level && world.x in 1809..1840 && world.z in 5136..5167
    }
}
