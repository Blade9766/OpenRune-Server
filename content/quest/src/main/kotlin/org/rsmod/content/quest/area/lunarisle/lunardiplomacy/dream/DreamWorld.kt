package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarCoords
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

/**
 * The Dream World: a private copy of map squares 27_79 (the six islands and the centre) and 28_79
 * (the arena where the player meets themselves) for each dreaming player. Scripts work in world
 * coordinates and translate through [at] and [toWorld]; the copy keeps the world's layout, so a
 * single offset covers both squares.
 *
 * The Ethereal beings are spawned into the copy with it, so each player has their own Mimic to
 * follow and their own Expert to race.
 */
@Singleton
class DreamWorld @Inject constructor(private val manager: InstanceManager) {
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

    /** The copy's tile for world tile [world], or [world] itself when the player is not dreaming. */
    fun at(player: Player, world: CoordGrid): CoordGrid {
        val dream = dreams[player.uid] ?: return world
        return CoordGrid(world.x + dream.dx, world.z + dream.dz, world.level)
    }

    fun toWorld(player: Player, coords: CoordGrid): CoordGrid {
        val dream = dreams[player.uid] ?: return coords
        return CoordGrid(coords.x - dream.dx, coords.z - dream.dz, coords.level)
    }

    fun worldCoords(player: Player): CoordGrid = toWorld(player, player.coords)

    fun npc(player: Player, type: String): Npc? {
        val dream = dreams[player.uid] ?: return null
        val id = type.asRSCM(RSCMType.NPC)
        return manager.npcsForInstance(dream.session.id).firstOrNull { it.isSlotAssigned && it.id == id }
    }

    /** Ties [npc] to the player's dream so it goes when the dream does. */
    fun attach(player: Player, npc: Npc) {
        val dream = dreams[player.uid] ?: return
        manager.attachNpc(dream.session.id, npc)
    }

    /** Copies the dream for the player and puts them at [arrival]; false when no copy could be made. */
    fun ProtectedAccess.enterDream(arrival: CoordGrid): Boolean {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return false
        }
        val area =
            InstanceArea.copyRegions(
                regionIds = REGIONS,
                level = arrival.level,
                enterCoord = RegionLocal(ANCHOR.level, ANCHOR.mx, ANCHOR.mz, ANCHOR.lx, ANCHOR.lz),
                exitCoord = LunarCoords.BRAZIER_SIDE,
                npcSpawns = spawns(isBodyTypeB()),
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
            mes("You can't seem to fall asleep here.")
            return false
        }
        manager.finalizeEntry(player, session, mapClock)
        val dream = Dream(session, enter.x - ANCHOR.x, enter.z - ANCHOR.z)
        dreams[player.uid] = dream
        telejump(CoordGrid(arrival.x + dream.dx, arrival.z + dream.dz, arrival.level), TeleportType.Exempt)
        return true
    }

    /** Wakes the player up beside the ceremonial brazier. */
    fun ProtectedAccess.wake() {
        val dream = dreams.remove(player.uid)
        val session = dream?.session ?: manager.sessionForPlayer(player)
        if (session != null) {
            manager.leave(player, session, mapClock)
        }
        telejump(LunarCoords.BRAZIER_SIDE, TeleportType.Exempt)
    }

    fun forget(player: Player) {
        dreams.remove(player.uid)
    }

    /**
     * Flings the player from a spring platform to world tile [dest]: thrown up off the platform,
     * then falling out of the sky onto their face and picking themselves up.
     */
    suspend fun ProtectedAccess.throwTo(dest: CoordGrid) {
        anim(THROWN_SEQ)
        soundSynth(EJECT_SOUND)
        delay(THROWN_TICKS)
        telejump(at(player, dest), TeleportType.Exempt)
        anim(FALLING_SEQ)
        delay(1)
        anim(LANDING_SEQ)
        soundSynth(LAND_SOUND)
        delay(LANDING_TICKS)
        anim(STAND_SEQ)
        delay(1)
    }

    private fun spawns(female: Boolean): List<InstanceNpc> =
        buildList {
            add(InstanceNpc(if (female) BEING_LADY else BEING_MAN, BEING_TILE))
            for ((type, tile) in CHALLENGERS) {
                add(InstanceNpc(type, tile))
            }
        }

    companion object {
        const val KEY = "lunar_dream_world"

        private val REGIONS = listOf((27 shl 8) or 79, (28 shl 8) or 79)
        private const val RECLAIM_TICKS = 100
        private const val GRACE_TICKS = 50

        const val BEING_MAN = "npc.lunar_moon_dream_man"
        const val BEING_LADY = "npc.lunar_moon_dream_lady"
        const val FLUKE = "npc.lunar_moon_dream_dice_game_man"
        const val GUIDE = "npc.lunar_moon_dream_jumping_game_man"
        const val MIMIC = "npc.lunar_moon_dream_music_game_man"
        const val NUMERATOR = "npc.lunar_moon_dream_numbers_game_man"
        const val EXPERT = "npc.lunar_moon_dream_power_game_man"
        const val PERCEPTIVE = "npc.lunar_moon_dream_trees_game_man"

        val BEING_TILE = CoordGrid(1761, 5088, 2)
        val CENTRE = CoordGrid(1763, 5088, 2)

        /**
         * The tile the instance is anchored on. The instance manager drops anyone more than 64
         * tiles from it, so it sits between the islands and Me's arena rather than on the centre.
         */
        private val ANCHOR = CoordGrid(1785, 5079, 2)

        val CHALLENGERS =
            listOf(
                FLUKE to CoordGrid(1737, 5068, 2),
                GUIDE to CoordGrid(1734, 5111, 2),
                MIMIC to CoordGrid(1770, 5070, 2),
                NUMERATOR to CoordGrid(1786, 5066, 2),
                EXPERT to CoordGrid(1787, 5079, 2),
                PERCEPTIVE to CoordGrid(1765, 5112, 2),
            )

        const val THROWN_SEQ = "seq.quest_lunar_ejector_thrown"
        const val FALLING_SEQ = "seq.quest_lunar_falling_on_platform"
        const val LANDING_SEQ = "seq.quest_lunar_landing_on_face"
        const val STAND_SEQ = "seq.quest_lunar_stand_from_face_down"
        const val EJECT_SOUND = "synth.moon_ejector"
        const val LAND_SOUND = "synth.moon_fall_land"
        const val THROWN_TICKS = 2
        const val LANDING_TICKS = 2

        fun onCentreIsland(world: CoordGrid): Boolean = world.x in 1746..1772 && world.z in 5076..5100

        fun inArena(world: CoordGrid): Boolean = world.x in 1808..1844 && world.z in 5072..5104

        /** Whether the stage allows the player to be in the dream at all. */
        fun dreamStage(stage: Int): Boolean =
            stage in LunarDiplomacyQuest.STAGE_ENTER_DREAM..LunarDiplomacyQuest.STAGE_DEFEATED_SELF
    }
}
