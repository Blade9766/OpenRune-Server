package org.rsmod.content.quest.area.burthorpe.heroesquest

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Taverley Dungeon's locked ways on the long route to the lava eels: the Black Knights' jail
 * cells, opened with the jailer's key, and the gate between the lesser demons and the blue
 * dragons, opened with the dusty key that Velrak the explorer hands out once he is freed.
 *
 * Every one of them opens freely from the far side and locks behind the player.
 */
class TaverleyDungeonDoors @Inject constructor(private val passages: GenericPassageScript) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(JAIL_DOOR) { lockedDoor(it.vis, it.type, JAIL_KEY, keyUsed = false) }
        onOpLocU(JAIL_DOOR, JAIL_KEY) { lockedDoor(it.vis, it.type, JAIL_KEY, keyUsed = true) }
        onOpLoc1(DUSTY_GATE) { lockedDoor(it.vis, it.type, DUSTY_KEY, keyUsed = false) }
        onOpLocU(DUSTY_GATE, DUSTY_KEY) { lockedDoor(it.vis, it.type, DUSTY_KEY, keyUsed = true) }
        onOpNpc1(VELRAK) { startDialogue(it.npc) { velrak() } }
    }

    /**
     * The side a door's own tile is on is the locked side: the corridor outside each cell, and
     * the demons' side of the dusty gate.
     */
    private suspend fun ProtectedAccess.lockedDoor(
        door: BoundLocInfo,
        type: ObjectServerType,
        key: String,
        keyUsed: Boolean,
    ) {
        val name = type.name.lowercase()
        if (!onDoorSide(door)) {
            mes("The $name locks shut behind you.")
            with(passages) { walkThrough(door, type) }
            return
        }
        if (!keyUsed && inv.count(key) == 0) {
            arriveDelay()
            soundSynth(LOCKED_SOUND)
            mes("This $name is locked.")
            return
        }
        soundSynth(UNLOCK_SOUND)
        mes("You unlock the $name.")
        with(passages) { walkThrough(door, type) }
    }

    private fun ProtectedAccess.onDoorSide(door: BoundLocInfo): Boolean =
        when (door.angle) {
            LocAngle.West -> coords.x >= door.coords.x
            LocAngle.North -> coords.z <= door.coords.z
            LocAngle.East -> coords.x <= door.coords.x
            LocAngle.South -> coords.z >= door.coords.z
        }

    private suspend fun Dialogue.velrak() {
        if (access.inv.count(DUSTY_KEY) > 0) {
            chatPlayer(quiz, "Are you still here?")
            chatNpc(
                sad,
                "Yes... I'm still plucking up the courage to run out past those Black Knights...",
            )
            return
        }
        chatNpc(happy, "Thank you for rescuing me! It isn't very comfy in this cell!")
        val explore =
            choice2(
                "So... do you know anywhere good to explore?",
                true,
                "Do I get a reward for freeing you?",
                false,
            )
        if (!explore) {
            chatPlayer(quiz, "Do I get a reward? For freeing you and all...")
            chatNpc(
                sad,
                "Well... not really... The Black Knights took all of my stuff before throwing me " +
                    "in here to rot!",
            )
            return
        }
        chatPlayer(quiz, "So... do you know anywhere good to explore?")
        chatNpc(
            neutral,
            "Well, this dungeon was quite good to explore ...until I got captured, anyway. I was " +
                "given a key to an inner part of this dungeon by a mysterious cloaked stranger!",
        )
        chatNpc(
            sad,
            "It's rather tough for me to get that far into the dungeon however... I just keep " +
                "getting captured! Would you like to give it a go?",
        )
        if (!choice2("Yes please!", true, "No, it's too dangerous for me too.", false)) {
            chatPlayer(neutral, "No, it's too dangerous for me too.")
            chatNpc(neutral, "I don't blame you!")
            return
        }
        chatPlayer(happy, "Yes please!")
        access.invAdd(access.inv, DUSTY_KEY)
        objbox(DUSTY_KEY, "Velrak reaches somewhere mysterious and passes you a key.")
    }

    private companion object {
        const val JAIL_DOOR = "loc.dungeonjail"
        const val DUSTY_GATE = "loc.deepdungeondoor"
        const val JAIL_KEY = "obj.jail_key"
        const val DUSTY_KEY = "obj.dusty_key"
        const val VELRAK = "npc.velrak_the_explorer"

        const val UNLOCK_SOUND = "synth.unlock"
        const val LOCKED_SOUND = "synth.locked"
    }
}
