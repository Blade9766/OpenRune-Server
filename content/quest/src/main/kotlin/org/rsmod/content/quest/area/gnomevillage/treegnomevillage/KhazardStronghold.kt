package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_HAS_ORB
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.area.varrock.dragonslayer.WallSide
import org.rsmod.content.quest.area.varrock.dragonslayer.sideOf
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Khazard stronghold at the north end of the battlefield: the wall the ballista crumbles,
 * the door that only opens from inside, the chest holding the first orb and the two commanders
 * guarding them.
 */
class KhazardStronghold
@Inject
constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val doors: DoorPassage,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CRUMBLED_WALL) { climbWall(it.loc) }
        onOpLoc1(DOOR) { openDoor(it.loc) }
        onOpLoc1(CHEST_CLOSED) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { searchChest() }
        onOpLoc2(CHEST_OPEN) { closeChest(it.loc) }
    }

    /**
     * The wall sits on the stronghold's south face; the inside is north of it. Climbing in the
     * first time draws the ground-floor commander.
     */
    private suspend fun ProtectedAccess.climbWall(wall: BoundLocInfo) {
        if (treeGnomeVillage.stage(player) < STAGE_BREACHED) {
            mesbox("The wall is far too high and solid to climb. The gnomes will need to breach it first.")
            return
        }
        val goingIn = player.coords.z < wall.coords.z
        val start = if (goingIn) wall.coords.translateZ(-1) else wall.coords.translateZ(1)
        val end = if (goingIn) wall.coords.translateZ(1) else wall.coords.translateZ(-1)
        if (player.coords != start) {
            playerWalk(start)
            arriveDelay()
        }
        if (goingIn) {
            mesbox("The wall has been reduced to rubble. It should be possible to climb over the remains...")
        }
        soundSynth(CLIMB_SOUND)
        anim(CLIMB_SEQ)
        exactMove(
            start = start,
            end = end,
            delay1 = 0,
            delay2 = CLIMB_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (goingIn) constants.em_face_north else constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(CLIMB_TICKS)
        if (goingIn) {
            alertCommander("Don't even think of taking the orb!")
        }
    }

    private fun ProtectedAccess.alertCommander(shout: String) {
        val commander = npcFind(player.coords, COMMANDER, COMMANDER_RADIUS, HuntVis.Off, search) ?: return
        if (commander.isInCombat()) {
            return
        }
        commander.say(shout)
        commander.opPlayer2(player, aiInteractions)
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        if (door.sideOf(player.coords) == WallSide.SOUTH) {
            mesbox("The door seems to be locked from the inside. I'll need to find another way to get in.")
            return
        }
        doors.walkThrough(this, door, DOOR_OPENED)
    }

    /** Opening the chest is what brings the upstairs commander running. */
    private fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        alertCommander("Oi! You! Get out of there.")
        soundSynth(CHEST_OPEN_SOUND)
        locRepo.del(chest, CHEST_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_OPEN, CHEST_OPEN_TICKS, chest.angle, chest.shape)
    }

    private fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        soundSynth(CHEST_CLOSE_SOUND)
        locRepo.del(chest, CHEST_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_CLOSED, CHEST_OPEN_TICKS, chest.angle, chest.shape)
    }

    private suspend fun ProtectedAccess.searchChest() {
        val stage = treeGnomeVillage.stage(player)
        val orbHere = stage == STAGE_BREACHED || (stage == STAGE_HAS_ORB && !player.inv.contains(ORB))
        if (!orbHere) {
            mesbox("You search the chest but find nothing of interest.")
            return
        }
        anim(SEARCH_SEQ)
        invAddOrDrop(objRepo, ORB)
        treeGnomeVillage.advanceTo(this, STAGE_HAS_ORB)
        objbox(ORB, "You search the chest. Inside you find the gnomes' stolen orb of protection.")
    }

    private companion object {
        const val CRUMBLED_WALL = "loc.khazzacklowwall"
        const val DOOR = "loc.khazard_stronghold_door"
        const val DOOR_OPENED = "loc.poordooropen"
        const val CHEST_CLOSED = "loc.chestclosed_khazard"
        const val CHEST_OPEN = "loc.chestopen_khazard"
        const val COMMANDER = "npc.khazard_commander"
        const val COMMANDER_RADIUS = 12

        const val CLIMB_SEQ = "seq.human_walk_crumbledwall"
        const val CLIMB_SOUND = "synth.climb_wall"
        const val CLIMB_TICKS = 3
        const val CLIENT_CYCLES_PER_TICK = 30

        const val SEARCH_SEQ = "seq.human_pickupfloor"
        const val CHEST_OPEN_SOUND = "synth.chest_open"
        const val CHEST_CLOSE_SOUND = "synth.chest_close"

        /** How long the chest stays open before the closed one is back. */
        const val CHEST_OPEN_TICKS = 100
    }
}
