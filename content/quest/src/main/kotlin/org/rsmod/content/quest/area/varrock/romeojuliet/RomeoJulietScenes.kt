package org.rsmod.content.quest.area.varrock.romeojuliet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
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
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * Private copies of the two map squares the Romeo & Juliet cutscenes play in: Jagex's cutscene
 * copy of west Varrock (map square 60,76, which carries the rugs and chair of Juliet's room) and
 * the crypt (36,72). Every level of the square is copied so the balcony has its house beneath it.
 *
 * Scene coordinates are the source square's own tiles; [Visit.at] translates them into the copy.
 */
@Singleton
class RomeoJulietScenes
@Inject
constructor(
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
) {
    class Visit(val session: InstanceSession, val enter: CoordGrid, private val source: CoordGrid) {
        fun at(tile: CoordGrid): CoordGrid =
            CoordGrid(tile.x + enter.x - source.x, tile.z + enter.z - source.z, tile.level)
    }

    fun ProtectedAccess.enterScene(key: String, enter: CoordGrid, exit: CoordGrid): Visit? {
        if (manager.sessionForPlayer(player) != null) {
            return null
        }
        val template =
            RegionTemplate.create {
                copyAllLevels(enter.mx shl 3, enter.mz shl 3) {
                    zoneWidth = ZONES_PER_SQUARE
                    zoneLength = ZONES_PER_SQUARE
                }
            }
        val area =
            InstanceArea.template(
                template = template,
                enterCoord = RegionLocal(enter.level, enter.mx, enter.mz, enter.lx, enter.lz),
                exitCoord = exit,
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
        val (session, dest) =
            when (val result = manager.create(player, key, spec, InstanceAccess.Private, mapClock)) {
                is InstanceManager.Result.Failed -> return null
                is InstanceManager.Result.Created -> result.session to result.enter
                is InstanceManager.Result.Joined -> result.session to result.enter
            }
        telejump(dest, TeleportType.Exempt)
        if (player.coords != dest) {
            manager.leave(player, session, mapClock)
            return null
        }
        manager.finalizeEntry(player, session, mapClock)
        return Visit(session, dest, enter)
    }

    fun ProtectedAccess.leaveScene() {
        val session = manager.sessionForPlayer(player) ?: return
        manager.leave(player, session, mapClock)
    }

    fun spawn(visit: Visit, type: String, tile: CoordGrid, face: Direction): Npc {
        val npc = Npc(type, visit.at(tile))
        npc.mode = NpcMode.None
        npc.moveRestrict = MoveRestrict.NoMove
        npc.movementLocked = true
        npc.respawnDir = face
        npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(visit.session.id, npc)
        npc.lockFacingDirection(face)
        return npc
    }

    /** Takes a closed door out of the copy so actors can walk through its doorway. */
    fun removeLoc(visit: Visit, loc: String, tile: CoordGrid) {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)) ?: return
        val found = locRepo.findExact(visit.at(tile), type) ?: return
        locRepo.del(found, Int.MAX_VALUE)
    }

    private companion object {
        const val ZONES_PER_SQUARE = 8
        const val RECLAIM_TICKS = 100
        const val GRACE_TICKS = 50
    }
}
