package org.rsmod.content.quest.area.ardougne.regicide.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.regicide.CrystalPendant
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.PENDANT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_DENSE_FOREST
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_FOUND_CAMP
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_FOUND_TRACKS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GOT_PENDANT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GUARD_KILLED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MAKE_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MET_IORWERTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_HELPING
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_REFUSED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TYRAS_DEAD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TRACKER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TRACKER_VIS
import org.rsmod.content.quest.area.ardougne.regicide.trackerReport
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lord Iorwerth's tracker at Tyras's old camp, north of the Poison Waste, and the footprints at
 * the west end of the camp. He wants proof before he helps a human, and once he has seen the
 * crystal pendant sets the player searching; the footprints lead into dense forest, and the
 * lesson he gives on hearing that is what lets the player through it.
 */
class ElfTracker
@Inject
constructor(private val regicide: RegicideQuest, private val pendant: CrystalPendant) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(TRACKER, TRACKER_VIS)) {
            onOpNpc1(type) { startDialogue(it.npc) { tracker() } }
        }
        for (type in FOOTPRINTS) {
            onOpLoc1(type) { followFootprints() }
        }
    }

    private suspend fun Dialogue.tracker() {
        val stage = regicide.stage(player)
        when {
            regicide.isComplete(player) || stage > STAGE_TYRAS_DEAD -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(neutral, "How goes it?")
                chatPlayer(happy, "Well thanks.")
            }
            stage < STAGE_MET_IORWERTH -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(angry, "Human! You must be one of Tyras's men. I have no time for brigands or outlaws.")
            }
            stage < STAGE_TRACKER_HELPING -> proof()
            stage == STAGE_TRACKER_HELPING -> {
                chatPlayer(quiz, "What should I be doing again?")
                chatNpc(neutral, "Check out the west end of the camp and see if you can find anything. I'll keep looking here. Let me know if you find anything.")
            }
            stage == STAGE_FOUND_TRACKS || stage == STAGE_DENSE_FOREST -> denseForest()
            stage == STAGE_GUARD_KILLED -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(quiz, "How goes the hunt for that bandit camp?")
                chatPlayer(neutral, "One of Tyras's guards attacked me in the forest. The camp can't be far.")
                chatNpc(neutral, "Then keep looking. You're close.")
            }
            stage == STAGE_FOUND_CAMP || stage == STAGE_MAKE_BOMB -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(quiz, "How goes the hunt for that bandit camp?")
                chatPlayer(happy, "I found it a short distance west of here.")
                player.trackerReport = 1
                chatNpc(neutral, "I'm sure Lord Iorwerth will be pleased to hear that. You should let him know.")
            }
            else -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(quiz, "I take it that the huge explosion I just heard was you?")
                chatPlayer(happy, "Yes, I've finally dealt with Tyras, he should cause you no more problems.")
                chatNpc(happy, "This is good news indeed. Lord Iorwerth will be very happy. You should go and let him know.")
            }
        }
    }

    private suspend fun Dialogue.proof() {
        chatPlayer(neutral, "Hello.")
        chatNpc(angry, "Human! You must be one of Tyras's men...")
        chatPlayer(neutral, "No I'm ${player.displayName}! Lord Iorwerth said you might be able to help me.")
        chatNpc(quiz, "And you have something to prove this?")
        val stage = regicide.stage(player)
        if (stage < STAGE_GOT_PENDANT || !with(pendant) { access.isCarried() }) {
            chatPlayer(sad, "Well... Err... No.")
            regicide.advanceTo(access, STAGE_TRACKER_REFUSED)
            chatNpc(angry, "As I was saying... I have no time for brigands or outlaws.")
            return
        }
        objbox(PENDANT, "You show the tracker the crystal pendant.")
        chatNpc(neutral, "That's Lord Iorwerth's pendant. He must have a lot of faith in you. Now, what is it I can help you with?")
        chatPlayer(neutral, "I need to find Tyras and kill him. Do you know where his camp is?")
        chatNpc(neutral, "Well this was his old camp. After the battle a few days ago they moved. We're yet to find them again.")
        chatPlayer(quiz, "Can I help at all?")
        chatNpc(neutral, "As it goes I'm not actually tracking them at the moment. I'm currently trying to trace our renegade brethren instead. This here is the best lead we've found so far.")
        chatPlayer(confused, "What is?")
        chatNpc(laugh, "Ahh I guess you can't see it with those human eyes.")
        regicide.advanceTo(access, STAGE_TRACKER_HELPING)
        chatNpc(neutral, "I tell you what. Now that you're here I may as well give you a hand. I'll search here on the east side. You check out the west end of the camp. Come and tell me if you find anything.")
    }

    private suspend fun Dialogue.denseForest() {
        chatPlayer(neutral, "I've found tracks leading off to the west. But they trail off into the trees. Beyond that I am unable to follow.")
        regicide.advanceTo(access, STAGE_DENSE_FOREST)
        chatNpc(neutral, "These forests aren't always as dense as you'd think. If you look closer, you might see ways that you can get through. With that in mind, why don't you give it another go?")
        chatPlayer(neutral, "Thanks... I'll see what I can find.")
    }

    private suspend fun ProtectedAccess.followFootprints() {
        arriveDelay()
        val stage = regicide.stage(player)
        if (stage !in STAGE_TRACKER_HELPING..STAGE_DENSE_FOREST) {
            return
        }
        mesbox("You try to follow the footprints but they lead into impassable woodland.")
        regicide.advanceTo(this, STAGE_FOUND_TRACKS)
    }

    private companion object {
        /** The multiloc on `varp.regicide_quest` the map places, and the form that carries "Follow". */
        val FOOTPRINTS = listOf("loc.regicide_old_camp_footprints", "loc.regicide_old_camp_footprints_vis_op")
    }
}
