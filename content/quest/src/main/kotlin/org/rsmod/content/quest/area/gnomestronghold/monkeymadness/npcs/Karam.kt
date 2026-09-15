package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.KARAM
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.KARAM_TALKER
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Karam, the squad's assassin, all but invisible in the long grass of Marim. */
class Karam @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val greegree: Greegree) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KARAM) { startDialogue(it.npc) { talk() } }
        onOpNpc1(KARAM_TALKER) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        when {
            monkeyMadness.stage(player) == STAGE_COMPLETE -> {
                chatNpc(neutral, "Quiet. I'm still here, and so are they.")
                chatPlayer(neutral, "Understood.")
            }
            greegree.isMonkey(player) -> {
                chatNpc(neutral, "Don't look at me. As far as any monkey knows I'm a trick of the light.")
                chatPlayer(neutral, "Sorry.")
            }
            !monkeyMadness.metKaram.get(player) -> firstMeeting()
            !monkeyMadness.metGarkor.get(player) -> chatNpc(neutral, "The Sergeant is south of the palace, by the white house. Keep to the grass.")
            else -> chatNpc(neutral, "Keep moving. Standing still is how they spot you.")
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(shocked, "Who's there? I can barely see you!")
        chatNpc(neutral, "That is rather the idea. Karam, 10th squad. Zooknock's handiwork keeps me out of sight; it doesn't work on anyone larger than a gnome, before you ask.")
        chatPlayer(quiz, "Where are the others?")
        chatNpc(neutral, "Three in the jail you just left. The Sergeant is south of the palace, between it and a white house. Zooknock and the sappers are under the island.")
        chatNpc(neutral, "I'm working on a way to free the prisoners. Anyone helping is welcome; anyone getting caught is not. Stay in the long grass, keep away from the walls, and you might get to Garkor in one piece.")
        chatPlayer(neutral, "Thanks.")
        chatNpc(neutral, "Don't thank me. Move.")
        monkeyMadness.metKaram.set(player, true)
        monkeyMadness.syncVars(player)
    }
}
