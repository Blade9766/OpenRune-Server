package org.rsmod.content.areas.misc.waterbirth

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sublevel 1's doors: the pressure doors out of the main cavern, held open by a player or a pet
 * rock on each plate, and the door-supports propping the ladder room shut, which fall when struck
 * and stand back up a minute later.
 */
class WaterbirthDoorScript
@Inject
constructor(
    private val players: PlayerList,
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val rocks: PetRockPads,
    private val held: HeldInteractions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (path in WaterbirthPath.entries) {
            onOpLoc1(path.door) { openPressureDoor(path, it.loc) }
            val support = checkNotNull(ServerCacheManager.getNpc(path.support.asRSCM(RSCMType.NPC)))
            onNpcQueue(support, "queue.death") { knockDown(path) }
            onNpcQueue(support, "queue.com_retaliate_player") {}
        }
        onEvent<NpcStateEvents.Create> { if (WaterbirthPath.ofSupportId(npc.type.id) != null) npc.movementLocked = true }
        onEvent<NpcStateEvents.Respawn> { if (WaterbirthPath.ofSupportId(npc.type.id) != null) npc.movementLocked = true }
        onOpHeld5(PetRockPads.PET_ROCK) {
            if (!rocks.place(this, it.slot, coords)) {
                held.drop(this, it.inventory, it.slot)
            }
        }
        onOpNpc1(PetRockPads.PET_ROCK_NPC) { rocks.take(this, it.npc) }
    }

    private suspend fun ProtectedAccess.openPressureDoor(path: WaterbirthPath, door: BoundLocInfo) {
        arriveDelay()
        val pressed = players.mapTo(HashSet()) { it.coords } + rocks.pressed()
        if (!WaterbirthDoors.opens(path, coords, pressed)) {
            mes("The door won't budge. Both of the pressure pads in front of it need weighing down.")
            return
        }
        if (WaterbirthDoors.fromCavernSide(path, coords)) {
            rocks.collect(this, path)
        }
        val dest = path.throughDoor(coords)
        anim(WALK_ANIM)
        exactMove(
            start = coords,
            end = dest,
            delay1 = 0,
            delay2 = PASS_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (dest.x > coords.x) constants.em_face_east else constants.em_face_west,
            teleportType = TeleportType.Exempt,
        )
        locRepo.change(door, OPEN_DOOR, OPEN_TICKS)
    }

    private fun StandardNpcAccess.knockDown(path: WaterbirthPath) {
        npc.hitpoints = npc.baseHitpointsLvl
        val falling = checkNotNull(ServerCacheManager.getNpc(path.supportFalling.asRSCM(RSCMType.NPC)))
        val fallen = checkNotNull(ServerCacheManager.getNpc(path.supportFallen.asRSCM(RSCMType.NPC)))
        npc.transmog(falling, DOWN_TICKS)
        val blocker = locRepo.findExact(path.supportCoords, BLOCKER_TYPE)
        if (blocker != null) {
            locRepo.del(blocker, DOWN_TICKS)
        }
        val support = npc
        worldQueues.add(FALL_TICKS) { support.transmog(fallen, DOWN_TICKS - FALL_TICKS) }
    }

    private companion object {
        const val OPEN_DOOR = "loc.dagannoth_pressure_open"
        const val WALK_ANIM = "seq.human_walk_f"
        const val CLIENT_CYCLES_PER_TICK = 30
        const val PASS_TICKS = 2
        const val OPEN_TICKS = 8
        const val FALL_TICKS = 2
        const val DOWN_TICKS = 100

        val BLOCKER_TYPE by lazy {
            checkNotNull(ServerCacheManager.getObject("loc.dagcave_invisibleblocking".asRSCM(RSCMType.LOC)))
        }
    }
}
