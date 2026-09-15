package org.rsmod.content.quest.area.karamja.shilovillage

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONES
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.DOORS_OPEN
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.DOORS_REVEALED
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.DOOR_VARBIT
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.NAZASTAROOL_FORM_COUNT
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA_CORPSE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_CORPSE_RETRIEVED
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ENTERED_BERVIRIUS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_PASSED_GATE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_TOMB_DOOR_OPEN
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_TOMB_UNLOCKED
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.TOMB_DOOR_RECESSES
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rashiliyia's tomb, beneath the hills north of Ah Za Rhoon.
 *
 * Searching the palm trees reveals carved doors in the hillside; the player's own copy of the
 * doors is `varbit.zqdoor_multi` (jungle plants, carved doors, open entrance), so revealing and
 * unlocking them is per player. Inside, the ancient gate and the rock slope below it will not let
 * anyone past without the Beads of the Dead, the tomb doors need three bones set into their
 * carvings, and disturbing the dolmen behind them raises Nazastarool. Every step taken inside
 * without the beads brings Rashiliyia herself.
 */
class RashiliyiaTomb
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val undead: ShiloUndead,
    private val nazastarool: Nazastarool,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc2(PALM_TREES) { searchPalmTrees() }
        for (door in HILLSIDE_DOORS) {
            onOpLoc1(door) { openHillsideDoor() }
            onOpLoc2(door) { searchHillsideDoor() }
            onOpLocU(door) { useOnHillsideDoor(it.objType.internalName) }
        }
        for (door in CARVED_DOORS) {
            onOpLocU(door) { useOnHillsideDoor(it.objType.internalName) }
        }
        for (exit in TOMB_EXITS) {
            onOpLoc1(exit) { openTombExit() }
            onOpLoc2(exit) {
                rashiliyia()
                mesbox("You can see a small recepticle, not unlike the one on the opposite side of the door!")
            }
            onOpLocU(exit) { useOnTombExit(it.objType.internalName) }
        }
        for (gate in ANCIENT_GATES) {
            onOpLoc1(gate) { openAncientGate(it.loc) }
            onOpLoc2(gate) {
                mesbox("There is an ancient symbol on the gate. It looks like a human figure with something around its neck. It looks pretty scary.")
            }
        }
        onOpLoc1(ROCK_SLOPE) { climbSlope() }
        for (door in TOMB_DOORS) {
            onOpLoc1(door) { openTombDoor(it.loc) }
            onOpLoc2(door) { searchTombDoor() }
            onOpLocU(door) { useOnTombDoor(it.loc, it.objType.internalName) }
        }
        onOpLoc1(DOLMEN) { searchDolmen() }
        onOpLoc2(DOLMEN) { searchDolmen() }
    }

    private fun ProtectedAccess.rashiliyia() {
        with(undead) { rashiliyiaAppears() }
    }

    /* The hillside */

    private suspend fun ProtectedAccess.searchPalmTrees() {
        arriveDelay()
        if (player.vars[DOOR_VARBIT] != 0) {
            mes("The trees are already pulled apart revealing some doors.")
            return
        }
        anim(SEARCH_SEQ)
        soundSynth(SEARCH_SOUND)
        mes("You search the palm trees...")
        delay(1)
        mes("...and reveal an ancient doorway set into the side of the hill!")
        shilo.revealedHillsideDoors.set(player, true)
        shilo.syncVars(player)
    }

    private suspend fun ProtectedAccess.openHillsideDoor() {
        arriveDelay()
        when (player.vars[DOOR_VARBIT]) {
            DOORS_OPEN -> enterTomb()
            DOORS_REVEALED -> mesbox("There seems to be some sort of recepticle on the door. Perhaps it needs a key?")
        }
    }

    private suspend fun ProtectedAccess.searchHillsideDoor() {
        arriveDelay()
        if (player.vars[DOOR_VARBIT] != DOORS_REVEALED) {
            return
        }
        if (shilo.stage(player) >= STAGE_ENTERED_BERVIRIUS) {
            shilo.examinedBoneLock.set(player, true)
        }
        mesbox("Examining the door, you see that it has a very strange lock. You're shocked to find that it seems to be made out of bone!")
    }

    private suspend fun ProtectedAccess.useOnHillsideDoor(obj: String) {
        arriveDelay()
        if (obj != BONE_KEY || player.vars[DOOR_VARBIT] == 0) {
            mes("Nothing interesting happens.")
            return
        }
        if (shilo.stage(player) < STAGE_ENTERED_BERVIRIUS) {
            mes("Nothing interesting happens.")
            return
        }
        mes("You try the bone key with the lock.")
        delay(1)
        if (shilo.stage(player) == STAGE_ENTERED_BERVIRIUS) {
            shilo.advanceTo(this, STAGE_TOMB_UNLOCKED)
        }
        soundSynth(BONE_DOOR_OPEN_SOUND)
        mes("A shimmering light dances over the doors, before you can blink, the doors creak open.")
        shilo.syncVars(player)
        delay(1)
        enterTomb()
    }

    private suspend fun ProtectedAccess.enterTomb() {
        mes("You walk into the darkness of the cavern.")
        soundSynth(BONE_DOOR_CLOSE_SOUND)
        fadeToBlack()
        telejump(ShiloCoords.TOMB_ENTRY, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        mesbox("The doors close behind you with the sound of crunching bone. Before you stretches a winding tunnel blocked by an ancient gate.")
        rashiliyia()
    }

    private suspend fun ProtectedAccess.openTombExit() {
        rashiliyia()
        delay(3)
        if (player.inv.contains(BONE_KEY)) {
            mes("The door seems to be locked!")
            delay(1)
            say("Oh no, I'm going to be stuck in here forever!")
            delay(1)
            say("How will I ever get out!")
            delay(1)
            say("I'm too young to die!")
            return
        }
        leaveTomb()
        mesbox("The doors creak open revealing the bright daylight. You walk outside into the warmth of the jungle heat.")
    }

    private suspend fun ProtectedAccess.useOnTombExit(obj: String) {
        rashiliyia()
        if (obj != BONE_KEY) {
            mes("Nothing interesting happens.")
            return
        }
        mes("You unlock the door with the key.")
        soundSynth(BONE_DOOR_OPEN_SOUND)
        delay(1)
        mes("The doors creak open revealing bright daylight.")
        delay(1)
        mes("You walk outside into the warmth of the jungle heat.")
        delay(1)
        if (!shilo.isComplete(player) && !player.inv.contains(RASHILIYIA_CORPSE)) {
            mes("You get a sense that something seems incomplete.")
        }
        leaveTomb()
    }

    private suspend fun ProtectedAccess.leaveTomb() {
        fadeToBlack()
        telejump(ShiloCoords.HILLSIDE_OUTSIDE, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
    }

    /* The ancient gate and the rock slope */

    private suspend fun ProtectedAccess.openAncientGate(gate: BoundLocInfo) {
        arriveDelay()
        val protectedFromQueen = shilo.isComplete(player) || player.wearsBeadsOfTheDead()
        val fromNorth = player.coords.z > gate.coords.z
        if (fromNorth && !protectedFromQueen) {
            rashiliyia()
            return
        }
        soundSynth(GATE_OPEN_SOUND)
        if (fromNorth) {
            telejump(ShiloCoords.GATE_SOUTH, TeleportType.Exempt)
            if (shilo.stage(player) == STAGE_TOMB_UNLOCKED) {
                shilo.advanceTo(this, STAGE_PASSED_GATE)
            }
        } else {
            telejump(ShiloCoords.GATE_NORTH, TeleportType.Exempt)
            rashiliyia()
        }
        soundSynth(GATE_CLOSE_SOUND)
        if (player.wearsBeadsOfTheDead()) {
            mes("The Beads of the Dead start to glow...")
        }
    }

    private suspend fun ProtectedAccess.climbSlope() {
        arriveDelay()
        rashiliyia()
        val failed = !statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)
        val fromTop = player.coords.z > ShiloCoords.ROCKS_TOP_Z
        if (fromTop && !shilo.isComplete(player) && !player.wearsBeadsOfTheDead()) {
            mes("You simply cannot concentrate enough to climb down the rocks.")
            return
        }
        mes(if (fromTop) "You carefully pick your way down the rocks." else "You carefully pick your way through the rocks.")
        if (failed) {
            mes("You fall!")
        }
        anim(CLIMB_SEQ)
        telejump(ShiloCoords.ROCKS_MIDWAY, TeleportType.Exempt)
        delay(2)
        if (failed) {
            telejump(ShiloCoords.ROCKS_BOTTOM, TeleportType.Exempt)
            delay(2)
            resetAnim()
            mes("You take damage!")
            say("Ooooff!")
            queueHit(delay = 1, type = HitType.Typeless, damage = 1)
            return
        }
        telejump(if (fromTop) ShiloCoords.ROCKS_BOTTOM else ShiloCoords.GATE_SOUTH, TeleportType.Exempt)
        delay(2)
        resetAnim()
        mes(if (fromTop) "You manage to carefully clamber down." else "You manage to carefully clamber up.")
        statAdvance(AGILITY, SLOPE_XP)
    }

    /* The tomb doors */

    private fun ProtectedAccess.tombDoorOpen(): Boolean =
        shilo.isComplete(player) || shilo.stage(player) >= STAGE_TOMB_DOOR_OPEN

    private suspend fun ProtectedAccess.openTombDoor(door: BoundLocInfo) {
        arriveDelay()
        rashiliyia()
        if (!tombDoorOpen()) {
            mes("This door is completely sealed, it is very ornately carved.")
            return
        }
        passTombDoor(door)
    }

    private suspend fun ProtectedAccess.passTombDoor(door: BoundLocInfo) {
        soundSynth(COFFIN_OPEN_SOUND)
        val dest = if (player.coords.z <= door.coords.z) door.coords.translate(0, 1) else door.coords
        telejump(dest.copy(x = player.coords.x.coerceIn(door.coords.x, door.coords.x + 1)), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.searchTombDoor() {
        arriveDelay()
        rashiliyia()
        val recesses = if (tombDoorOpen()) 0 else TOMB_DOOR_RECESSES - shilo.tombDoorBones.get(player)
        val text =
            when (recesses) {
                0 -> "The door is ornately carved with depictions of skeletal warriors. All the skeletons are complete. It looks as if you walk through this door."
                1 -> "The door is ornately carved with depictions of skeletal warriors. You notice that some of the skeletal warriors depictions are not complete. Instead, there are recesses where some of the bones should be. There is one recess."
                2 -> "The door is ornately carved with depictions of skeletal warriors. You notice that some of the skeletal warriors depictions are not complete. Instead, there are recesses where some of the bones should be. There are two recesses."
                else -> "The door is ornately carved with depictions of skeletal warriors. You notice that some of the skeletal warriors depictions are not complete. Instead, there are recesses where some of the bones should be. There are three recesses."
            }
        mesbox(text)
    }

    private suspend fun ProtectedAccess.useOnTombDoor(door: BoundLocInfo, obj: String) {
        arriveDelay()
        rashiliyia()
        if (obj != BONES) {
            mes("Nothing interesting happens.")
            return
        }
        if (tombDoorOpen()) {
            mesbox("There are no more recesses to fill, you filled them all.")
            return
        }
        if (shilo.stage(player) < STAGE_TOMB_UNLOCKED) {
            mes("Nothing interesting happens.")
            return
        }
        val placed = shilo.tombDoorBones.get(player)
        faceSquare(door.coords)
        anim(PLACE_SEQ)
        delay(1)
        mes(if (placed == 0) "You carefully place a bone in the door..." else "You carefully place another bone in the door...")
        invDel(inv, BONES)
        shilo.tombDoorBones.set(player, placed + 1)
        when (placed + 1) {
            1 -> {
                objbox(BONES, "You place the bone into the skeletal door and it fits. There are two recesses left now.")
                mes("You fit the bone into the recess.")
            }
            2 -> {
                objbox(BONES, "You place the bone into the skeletal door and it fits. There is just one recess left now.")
                mes("You fit the bone into the recess.")
            }
            else -> {
                mes("You fit the last bone into the recess.")
                objbox(BONES, "You place the bone into the last recess on the skeletal door and it fits. All the recesses are filled...")
                shilo.advanceTo(this, STAGE_TOMB_DOOR_OPEN)
                soundSynth(SKELETON_SOUND)
                mesbox("The door seems to change slightly. Two depictions of skeletal warriors turn their heads towards you. They are alive! The Skeletons wrench themselves free of the door. Stepping out of the door, with grinning teeth they push the huge doors open.")
                passTombDoor(door)
            }
        }
    }

    /* Rashiliyia's dolmen */

    private suspend fun ProtectedAccess.searchDolmen() {
        arriveDelay()
        rashiliyia()
        if (shilo.isComplete(player)) {
            mes("You have already completed this quest.")
            delay(1)
            mes("Rashiliyia's remains have been put to rest.")
            return
        }
        if (owns(RASHILIYIA_CORPSE)) {
            mes("You find nothing new on the dolmen.")
            return
        }
        if (shilo.nazastaroolForms.get(player) >= NAZASTAROOL_FORM_COUNT) {
            anim(SEARCH_SEQ)
            objbox(RASHILIYIA_CORPSE, "You search the dolmen... And find the mumified remains of a human female.")
            if (player.inv.isFull()) {
                mes("You don't have enough inventory space to carry the remains.")
                return
            }
            invAdd(inv, RASHILIYIA_CORPSE)
            shilo.nazastaroolForms.set(player, 0)
            if (shilo.stage(player) >= STAGE_TOMB_UNLOCKED) {
                shilo.advanceTo(this, STAGE_CORPSE_RETRIEVED)
            }
            objbox(RASHILIYIA_CORPSE, "You take the mummified remains of a human female. You feel certain that these are Rashiliyia's remains. You carefully place the remains in your inventory.")
            return
        }
        if (nazastarool.hasLivingForm(player)) {
            mes("The dolmen remains silent.")
            return
        }
        soundSynth(RUMBLE_SOUND)
        mesbox("You touch the dolmen, and the ground starts to shake. You hear an unearthly voice booming and you step away from the dolmen in anticipation.")
        delay(random.of(RISE_DELAY_MIN, RISE_DELAY_MAX))
        with(nazastarool) { rise() }
    }

    private companion object {
        const val PALM_TREES = "loc.zqquest_hidytree"
        val HILLSIDE_DOORS = listOf("loc.hillsidedoorl_multi", "loc.hillsidedoorr_multi")
        val CARVED_DOORS = listOf("loc.hillsideclosedl", "loc.hillsideclosedr")
        val TOMB_EXITS = listOf("loc.hillsideexitclosedl", "loc.hillsideexitclosedr")
        val ANCIENT_GATES = listOf("loc.zombiequeengateclosedl", "loc.zombiequeengateclosedr")
        const val ROCK_SLOPE = "loc.zq_rashrocks"
        val TOMB_DOORS = listOf("loc.thzq_tombrooml1", "loc.thzq_tombroomr1")
        const val DOLMEN = "loc.zqrashdolmen"

        const val AGILITY = "stat.agility"
        const val SLOPE_XP = 1.0
        const val ROLL_LOW = 125
        const val ROLL_HIGH = 250
        const val RISE_DELAY_MIN = 4
        const val RISE_DELAY_MAX = 7

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val PLACE_SEQ = "seq.human_pickuptable"
        const val CLIMB_SEQ = "seq.human_climbing"

        const val SEARCH_SOUND = "synth.search_vine"
        const val BONE_DOOR_OPEN_SOUND = "synth.bone_door_open"
        const val BONE_DOOR_CLOSE_SOUND = "synth.bone_door_close"
        const val GATE_OPEN_SOUND = "synth.grate_open"
        const val GATE_CLOSE_SOUND = "synth.grate_close"
        const val COFFIN_OPEN_SOUND = "synth.coffin_open"
        const val SKELETON_SOUND = "synth.skeleton_resurrect"
        const val RUMBLE_SOUND = "synth.rumbling"
    }
}
