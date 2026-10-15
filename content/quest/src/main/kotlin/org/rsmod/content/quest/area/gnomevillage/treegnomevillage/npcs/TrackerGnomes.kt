package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_FINDING_TRACKERS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_HAS_ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_ORB_RETURNED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three tracker gnomes Montai sent to scout the stronghold: one hiding behind it, one locked
 * in the Khazard cell, and one who has lost his mind and only hints at the x coordinate.
 */
class TrackerGnomes @Inject constructor(private val treeGnomeVillage: TreeGnomeVillageQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(TRACKER_1) { startDialogue(it.npc) { tracker1() } }
        onOpNpc1(TRACKER_2) { startDialogue(it.npc) { tracker2() } }
        onOpNpc1(TRACKER_3) { startDialogue(it.npc) { tracker3() } }
    }

    private suspend fun Dialogue.tracker1() {
        when (treeGnomeVillage.stage(player)) {
            in 0 until STAGE_FINDING_TRACKERS -> {
                chatPlayer(happy, "Hello.")
                chatNpc(angry, "I can't talk now. Can't you see we're trying to win a battle here?")
            }
            STAGE_FINDING_TRACKERS -> {
                chatPlayer(quiz, "Do you know the coordinates of the Khazard stronghold?")
                chatNpc(neutral, "I managed to get one, although it wasn't easy.")
                treeGnomeVillage.knowsHeight.set(player, true)
                treeGnomeVillage.syncVars(player)
                mesbox("The gnome tells you the <col=000080>height</col> coordinate.")
                chatPlayer(happy, "Well done.")
                chatNpc(neutral, "The other two tracker gnomes should have the other coordinates if they're still alive.")
                chatPlayer(neutral, "OK, take care.")
            }
            STAGE_BREACHED -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(happy, "Well done, you've broken down their defences. This battle must be ours.")
            }
            STAGE_HAS_ORB -> {
                chatPlayer(quiz, "How are you tracker?")
                chatNpc(happy, "Now we have the orb I'm much better. They won't stand a chance without it.")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(sad, "When will this battle end? I feel like I've been fighting forever.")
            }
        }
    }

    private suspend fun Dialogue.tracker2() {
        when (treeGnomeVillage.stage(player)) {
            in 0 until STAGE_FINDING_TRACKERS -> {
                chatPlayer(happy, "Hi there.")
                chatNpc(neutral, "The battle is far from over. If you have a pure heart you will help us win.")
            }
            STAGE_FINDING_TRACKERS -> {
                chatPlayer(quiz, "Are you OK?")
                chatNpc(sad, "They caught me spying on the stronghold. They beat and tortured me.")
                chatNpc(angry, "But I didn't crack. I told them nothing. They can't break me!")
                chatPlayer(sad, "I'm sorry little man.")
                chatNpc(happy, "Don't be. I have the position of the stronghold!")
                treeGnomeVillage.knowsY.set(player, true)
                treeGnomeVillage.syncVars(player)
                mesbox("The gnome tells you the <col=000080>y coordinate.</col>")
                chatPlayer(happy, "Well done.")
                chatNpc(worried, "Now leave before they find you and all is lost.")
                chatPlayer(neutral, "Hang in there.")
                chatNpc(worried, "Go!")
            }
            STAGE_BREACHED -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(happy, "Well done, you've broken down their defences. This battle must be ours.")
            }
            STAGE_HAS_ORB -> {
                chatPlayer(quiz, "How are you tracker?")
                chatNpc(happy, "Now we have the orb I'm much better. Soon my comrades will come and free me.")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(sad, "When will this battle end? I feel like I've been locked up my whole life.")
            }
        }
    }

    private suspend fun Dialogue.tracker3() {
        when (treeGnomeVillage.stage(player)) {
            in 0 until STAGE_FINDING_TRACKERS -> {
                chatPlayer(happy, "Hi there.")
                chatNpc(worried, "I can't stand this war. The misery, the pain, it's driving me crazy! When will it end?")
            }
            STAGE_FINDING_TRACKERS -> {
                chatPlayer(quiz, "Are you OK?")
                chatNpc(laugh, "OK? Who's OK? Not me! Hee hee!")
                chatPlayer(quiz, "What's wrong?")
                chatNpc(worried, "You can't see me, no one can. Monsters, demons, they're all around me!")
                chatPlayer(quiz, "What do you mean?")
                chatNpc(laugh, "They're dancing, all of them, hee hee.")
                mesbox("He's clearly lost the plot.")
                chatPlayer(quiz, "Do you have the coordinate for the Khazard stronghold?")
                chatNpc(quiz, "Who holds the stronghold?")
                chatPlayer(confused, "What?")
                val hint =
                    when (treeGnomeVillage.trackerX.get(player)) {
                        1 -> "Less than my hands."
                        2 -> "More than my head, less than my fingers."
                        3 -> "More than we, less than our feet."
                        else -> "My legs and your legs, ha ha ha!"
                    }
                chatNpc(laugh, hint)
                treeGnomeVillage.knowsX.set(player, true)
                treeGnomeVillage.syncVars(player)
                chatPlayer(neutral, "You're mad.")
                chatNpc(laugh, "Dance with me, and Khazard's men are beat.")
                mesbox("The toll of war has affected his mind.")
                chatPlayer(sad, "I'll pray for you little man.")
                chatNpc(laugh, "All day we pray in the hay, hee hee.")
            }
            STAGE_BREACHED -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(worried, "Don't talk to me, you can't see me. No one can, just the demons.")
            }
            STAGE_HAS_ORB -> {
                chatPlayer(quiz, "How are you tracker?")
                chatNpc(happy, "Now we have the orb I'm much better. They won't stand a chance without it.")
            }
            STAGE_ORB_RETURNED -> {
                chatPlayer(happy, "Hello.")
                chatNpc(confused, "I feel dizzy, where am I? Oh dear, oh dear I need some rest.")
                chatPlayer(neutral, "I think you do.")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(confused, "I feel dizzy, where am I? Oh dear, oh dear I need some rest.")
                chatPlayer(neutral, "I think you do.")
            }
        }
    }

    private companion object {
        const val TRACKER_1 = "npc.tracker1"
        const val TRACKER_2 = "npc.tracker2"
        const val TRACKER_3 = "npc.tracker3"
    }
}
