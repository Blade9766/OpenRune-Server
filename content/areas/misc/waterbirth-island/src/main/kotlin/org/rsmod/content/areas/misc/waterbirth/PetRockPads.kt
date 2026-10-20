package org.rsmod.content.areas.misc.waterbirth

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/**
 * Pet rocks left on the pressure door plates. A rock on a plate is an npc standing in for a
 * player; the item leaves the inventory when it is put down and comes back exactly once, whether
 * the owner picks it up, walks through the door it was holding open, or leaves it there too long.
 */
@Singleton
class PetRockPads
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val players: PlayerList,
    private val worldQueues: WorldQueueList,
) {
    private data class PlacedRock(val npc: Npc, val owner: PlayerUid)

    private val rocks = HashMap<CoordGrid, PlacedRock>()

    fun pressed(): Set<CoordGrid> = rocks.filterValues { it.npc.isAliveInWorld() }.keys

    fun padOf(npc: Npc): CoordGrid? = rocks.entries.firstOrNull { it.value.npc === npc }?.key

    /** @return `false` when [pad] is not a pressure plate, so the rock should drop normally. */
    fun place(access: ProtectedAccess, slot: Int, pad: CoordGrid): Boolean {
        if (WaterbirthPath.ofPad(pad) == null) {
            return false
        }
        if (rocks[pad]?.npc?.isAliveInWorld() == true) {
            access.mes("There is already something weighing down this pressure pad.")
            return true
        }
        val held = access.inv[slot]
        if (held == null || held.id != PET_ROCK_ID) {
            return true
        }
        if (!access.invDel(access.inv, PET_ROCK, count = 1, slot = slot).success) {
            return true
        }
        val npc = Npc(PET_ROCK_NPC, pad)
        npcRepo.add(npc, ROCK_DURATION)
        npc.movementLocked = true
        rocks[pad] = PlacedRock(npc, access.player.uid)
        worldQueues.add(ROCK_DURATION - 1) { expire(pad, npc) }
        access.mes("You place your pet rock on the pressure pad.")
        return true
    }

    fun take(access: ProtectedAccess, npc: Npc): Boolean {
        val pad = padOf(npc) ?: return false
        val rock = rocks.getValue(pad)
        if (rock.owner != access.player.uid) {
            access.mes("That isn't your pet rock.")
            return false
        }
        rocks.remove(pad)
        if (!access.invAdd(access.inv, PET_ROCK).success) {
            rocks[pad] = rock
            access.mes("You don't have enough inventory space to pick up your pet rock.")
            return false
        }
        npcRepo.del(npc, Int.MAX_VALUE)
        return true
    }

    /** Picks up [access]'s rock from [path]'s plates, if it can, as they go through the door. */
    fun collect(access: ProtectedAccess, path: WaterbirthPath) {
        for (pad in path.pads) {
            val rock = rocks[pad] ?: continue
            if (rock.owner == access.player.uid && take(access, rock.npc)) {
                access.mes("You pick up your pet rock on your way through.")
            }
        }
    }

    /** Returns rocks left on a plate for [ROCK_DURATION] to their owner, or to the floor. */
    fun expire(pad: CoordGrid, npc: Npc) {
        val rock = rocks[pad]?.takeIf { it.npc === npc } ?: return
        rocks.remove(pad)
        npcRepo.del(npc, Int.MAX_VALUE)
        val owner = rock.owner.resolve(players)
        if (owner != null && owner.invAdd(owner.inv, PET_ROCK).success) {
            owner.mes("Your pet rock rolls back to you.")
            return
        }
        objRepo.add(PET_ROCK, pad, ROCK_DROP_DURATION, owner)
    }

    companion object {
        const val PET_ROCK = "obj.vt_useless_rock"
        const val PET_ROCK_NPC = "npc.dagannoth_dungeon_pressure_pet_rock"
        const val ROCK_DURATION = 500
        private const val ROCK_DROP_DURATION = 200
        private val PET_ROCK_ID by lazy { PET_ROCK.asRSCM(RSCMType.OBJ) }
    }
}
