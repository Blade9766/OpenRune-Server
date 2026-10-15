package org.rsmod.content.quest.area.ardougne.regicide.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CAMP_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CAMP_GUARD_2
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.HINING
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_FOUND_CAMP
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GUARD_KILLED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TYRAS_DEAD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TENT_GUARD
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * General Hining and the guards of Tyras Camp. Hining turns the player away from the king, which
 * is the proof of the camp Lord Iorwerth wants; once Tyras is dead the camp only waits for the
 * coming darkness.
 */
class TyrasCamp @Inject constructor(private val regicide: RegicideQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(HINING) { startDialogue(it.npc) { hining() } }
        for (guard in listOf(CAMP_GUARD, CAMP_GUARD_2, TENT_GUARD)) {
            onOpNpc1(guard) { startDialogue(it.npc) { guard() } }
        }
    }

    private fun Dialogue.tyrasDead(): Boolean =
        regicide.isComplete(player) || regicide.stage(player) >= STAGE_TYRAS_DEAD

    private suspend fun Dialogue.hining() {
        if (tyrasDead()) {
            chatPlayer(neutral, "Hello.")
            chatNpc(sad, "This is not a good place to be, go and prepare for the coming darkness.")
            chatPlayer(quiz, "What do you mean?")
            chatNpc(sad, "There is no great hero left to fight back the forces that face us now... Go, leave, prepare for the darkness. While you still have time!")
            return
        }
        chatNpc(neutral, "Yes, is there anything I can help with? I'm General of what's left of this army and I'm rather busy.")
        chatPlayer(quiz, "If you're a General can you help me meet the king?")
        chatNpc(angry, "The king will see no one. He can trust no one!")
        chatPlayer(quiz, "Is there no way I can see him?")
        chatNpc(neutral, "In the coming battle he will stand between us and the end. Then you will see him.")
        chatPlayer(sad, "Not quite what I had in mind.")
        if (regicide.stage(player) == STAGE_GUARD_KILLED) {
            regicide.advanceTo(access, STAGE_FOUND_CAMP)
        }
    }

    private suspend fun Dialogue.guard() {
        chatPlayer(neutral, "Hello.")
        if (tyrasDead()) {
            chatNpc(sad, "These are dark times, I must prepare for the coming darkness.")
            return
        }
        chatNpc(neutral, "Sorry, can't stop to talk. You should go to General Hining if you need something.")
    }
}
