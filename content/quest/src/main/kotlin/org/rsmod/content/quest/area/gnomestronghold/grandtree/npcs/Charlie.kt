package org.rsmod.content.quest.area.gnomestronghold.grandtree.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.CHARLIE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.INVASION_PLANS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_CHARLIE_QUESTIONED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ESCAPE_BY_GLIDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_GLOUGH_WARNED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KEY_HINTED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KING_DOUBTS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_PRISONER_REPORTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Charlie, the human Glough locked in the cage at the top of the Grand Tree. The map spawns
 * `npc.grandtree_charlie_multi`, which only shows him while the quest varp sits between 40 and
 * 150, so he vanishes once the King frees him.
 */
class Charlie @Inject constructor(private val grandTree: GrandTreeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CHARLIE) { startDialogue(it.npc) { charlie() } }
    }

    private suspend fun Dialogue.charlie() {
        when (grandTree.stage(player)) {
            STAGE_GLOUGH_WARNED, STAGE_PRISONER_REPORTED -> interrogate()
            STAGE_CHARLIE_QUESTIONED -> {
                chatPlayer(happy, "Hello Charlie.")
                chatNpc(quiz, "Hello adventurer. Have you figured out what's going on?")
                chatPlayer(sad, "No idea.")
                chatNpc(neutral, "To get to the bottom of this you'll need to search Glough's home.")
            }
            in STAGE_ESCAPE_BY_GLIDER..STAGE_HAS_LUMBER_ORDER -> {
                chatPlayer(worried, "I can't figure this out Charlie!")
                chatNpc(neutral, "Go and see the foreman in the Karamja jungle, there's a shipyard there, you might find some clues. Don't forget the password is Ka-Lu-Min; if they realise that you're not working for Glough there'll be trouble!")
            }
            STAGE_KING_DOUBTS -> keyHint()
            STAGE_KEY_HINTED -> if (player.inv.contains(INVASION_PLANS)) foundPlans() else keyReminder()
            else -> mesbox("The prisoner is in no mood to talk.")
        }
    }

    private suspend fun Dialogue.interrogate() {
        chatPlayer(angry, "Tell me. Why would you want to kill the Grand Tree?")
        chatNpc(confused, "What do you mean?!")
        chatPlayer(angry, "Don't tell me, you just happened to be caught carrying Daconia rocks!")
        chatNpc(sad, "All I know is that I did what I was asked.")
        chatPlayer(quiz, "I don't understand.")
        chatNpc(neutral, "Glough paid me to go to this gnome on a hill. I gave the gnome a seal and he gave me some rocks to give to Glough.")
        chatNpc(sad, "I've been doing it for weeks, this time though Glough locked me up here! I just don't understand it.")
        chatPlayer(neutral, "Sounds like Glough is hiding something!")
        chatNpc(neutral, "I don't know what he's up to. If you want to find out you'd better search his home.")
        chatPlayer(happy, "Okay. Thanks Charlie.")
        chatNpc(happy, "Good luck!")
        grandTree.advanceTo(access, STAGE_CHARLIE_QUESTIONED)
    }

    private suspend fun Dialogue.keyHint() {
        chatPlayer(quiz, "How are you doing Charlie?")
        chatNpc(sad, "I've been better.")
        chatPlayer(neutral, "Glough has some plan to rule Gielinor!")
        chatNpc(neutral, "I wouldn't put it past him, the Gnome's crazy!")
        chatPlayer(neutral, "I need some proof to convince the King.")
        chatNpc(happy, "Hmm... you could be in luck! Before Glough had me locked up I heard him mention that he'd left his chest key at his girlfriend's house. She lives just west of the toad swamp.")
        chatPlayer(neutral, "Okay, I'll see what I can find.")
        grandTree.advanceTo(access, STAGE_KEY_HINTED)
    }

    private suspend fun Dialogue.keyReminder() {
        chatPlayer(quiz, "How are you doing Charlie?")
        chatNpc(sad, "I've been better. Have you found Glough's chest key yet? He left it at his girlfriend's house, just west of the toad swamp.")
    }

    private suspend fun Dialogue.foundPlans() {
        chatPlayer(happy, "You were right! I searched Glough's chest and found some invasion plans!")
        chatNpc(happy, "Go to the king and tell him! Finally you have proof. Surely King Narnode will believe you now and set me free!")
        chatPlayer(happy, "Hold tight Charlie!")
        chatNpc(neutral, "I'm not going anywhere!")
    }
}
