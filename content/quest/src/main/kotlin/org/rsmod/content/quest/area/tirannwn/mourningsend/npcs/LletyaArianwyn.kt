package org.rsmod.content.quest.area.tirannwn.mourningsend.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.tirannwn.mourningsend.ArianwynBriefing
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BLOODY_TOP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.RIPPED_LEGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_REVEALED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.tirannwn.mourningsend.wearsDisguise
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.ArianwynTempleTalk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Arianwyn in his hall in Lletya: the briefing that sets the player on the mourners, advice about
 * the bloody top and ripped trousers, encouragement while the player is undercover, and the
 * debrief that completes the quest. Once Part I counts as done he speaks for Mourning's End
 * Part II ([ArianwynTempleTalk]).
 */
class LletyaArianwyn
@Inject
constructor(
    private val mourning: MourningsEndQuest,
    private val briefing: ArianwynBriefing,
    private val temple: ArianwynTempleTalk,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(ARIANWYN, ARIANWYN_VIS)) {
            onOpNpc1(type) { startDialogue(it.npc) { arianwyn() } }
        }
    }

    private suspend fun Dialogue.arianwyn() {
        val stage = mourning.stage(player)
        when {
            mourning.unlocked(player) -> with(temple) { arianwyn() }
            stage == 0 -> chatNpc(angry, "How did you get in here? You must leave.")
            stage == STAGE_STARTED -> with(briefing) { brief() }
            stage == STAGE_BRIEFED -> disguiseAdvice()
            stage < STAGE_REVEALED -> {
                chatNpc(quiz, "How goes it ${player.displayName}?")
                chatPlayer(neutral, "I've managed to get in with the mourners. Now I need to find out what they're really up to.")
                chatNpc(happy, "Great work! Stick with it ${player.displayName}.")
            }
            else -> with(briefing) { debrief() }
        }
    }

    private suspend fun Dialogue.disguiseAdvice() {
        val bloody = player.inv.contains(BLOODY_TOP)
        val ripped = player.inv.contains(RIPPED_LEGS)
        if (bloody || ripped) {
            chatPlayer(neutral, "I ambushed one of the mourners on the mountain pass and took his clothes.")
            chatNpc(happy, "Ah, a disguise. Good thinking.")
            chatPlayer(worried, "There's a snag. The top is covered in blood and the trousers are torn.")
            chatNpc(neutral, "Oronwen, our seamstress here, may be able to mend the trousers.")
            chatNpc(neutral, "As for the top, find someone who washes a great deal of laundry. Someone who wears a lot of white, perhaps.")
            chatPlayer(neutral, "Okay, thanks for the help.")
            return
        }
        if (player.wearsDisguise()) {
            chatPlayer(neutral, "I ambushed one of the mourners on the mountain pass and took his clothes.")
            chatNpc(happy, "Ah, a disguise. Good thinking. Use it to get into the Mourner Headquarters in West Ardougne, then find out what they are doing in the city.")
            chatPlayer(neutral, "Okay, will do.")
            return
        }
        chatNpc(quiz, "How goes it ${player.displayName}?")
        chatPlayer(quiz, "How was I supposed to get in with the mourners again?")
        chatNpc(neutral, "I can't give you a plan, but mourners cross the Arandar mountain pass regularly. I'm sure you can make use of that.")
    }

    companion object {
        const val ARIANWYN = "npc.mourning_arianwyn"
        const val ARIANWYN_VIS = "npc.mourning_arianwyn_vis"
    }
}
