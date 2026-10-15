package org.rsmod.content.quest.area.tirannwn.mourningsend

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_REVEALED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STARTED

/**
 * Arianwyn's two long conversations. The briefing runs straight after Eluned brings the player to
 * Lletya, or when Arianwyn is next spoken to if it was cut short; the stage only moves once it is
 * over. The debrief is the only way to finish the quest: the rewards come from the quest manager
 * as the stage reaches its end, once.
 */
@Singleton
class ArianwynBriefing @Inject constructor(private val mourning: MourningsEndQuest) {

    suspend fun Dialogue.brief() {
        if (mourning.stage(player) != STAGE_STARTED) {
            return
        }
        arianwynSays(happy, "Ah ${player.displayName}, I'm glad you made it. Welcome to Lletya, home of the Elven Resistance.")
        chatPlayer(neutral, "Thank you. It took some work to convince Islwyn I could be trusted.")
        arianwynSays(neutral, "He is no friend of humans. If you won his trust, I was right to take a chance on you.")
        chatPlayer(neutral, "I'm glad you did. I hate to think I'd still be working for King Lathas and Lord Iorwerth without knowing it.")
        arianwynSays(neutral, "You have a chance to put right what you did before.")
        chatPlayer(quiz, "There's a lot I still don't understand. Why are Lathas and Iorwerth working together? And where does the plague come into it?")
        arianwynSays(neutral, "Much is still unknown to us too, but I can give you an outline. There are books around the village if you want to know more.")
        val history = choice2("Okay, let's begin.", true, "Let's skip the backstory.", false)
        if (history) {
            chatPlayer(neutral, "Okay, let's begin.")
            arianwynSays(neutral, "Long ago all elves lived in the crystal city of Prifddinas with our goddess, Seren. When the God Wars ended she was lost to us, and Baxtorian Cadarn became the first king of the elves.")
            arianwynSays(neutral, "Under Baxtorian the kingdom spread east. But Lord Iorwerth turned from Seren to Zamorak, and while our forces were spread thin he seized Prifddinas.")
            arianwynSays(neutral, "The city could not be retaken, so Baxtorian and the clan elders sang it back into a crystal seed to keep it from him.")
            chatPlayer(neutral, "So that's why the city looks like an empty field.")
            arianwynSays(sad, "Indeed. Baxtorian returned east to find the rest of his kingdom fallen and his wife, Glarial, taken.")
            chatPlayer(neutral, "I know this part. He shut himself away beneath the waterfall, and I laid Glarial's remains there beside him.")
            arianwynSays(neutral, "Then I see how you won Islwyn over. With Baxtorian gone and the city lost, our land fell into civil war, and we have fought ever since.")
            chatPlayer(quiz, "And King Lathas?")
        } else {
            chatPlayer(neutral, "Let's skip the backstory.")
            arianwynSays(neutral, "Understanding matters for what comes next, but as you wish. What concerns you is the alliance between King Lathas and Lord Iorwerth.")
        }
        arianwynSays(neutral, "As far as we can tell, Iorwerth has promised to help Lathas win back land his family once held.")
        chatPlayer(quiz, "What does Lord Iorwerth get out of it?")
        arianwynSays(neutral, "That is what we need you to discover. Our spies say he has used the alliance to slip his people into West Ardougne. Something in that city matters to him.")
        chatPlayer(quiz, "Slip his people in? How?")
        arianwynSays(neutral, "The ones you know as mourners are elves in Iorwerth's service. We often see them crossing the Arandar mountain pass.")
        chatPlayer(shocked, "The mourners are elves?")
        arianwynSays(neutral, "They are. The plague lets them come and go unchallenged, though we don't know why. You know that city and can move about it freely. Infiltrate the mourners and find out what they are doing.")
        chatPlayer(quiz, "How am I supposed to manage that?")
        arianwynSays(neutral, "I have no plan for you. But mourners cross the Arandar pass regularly, and I'm sure you can take advantage of that.")
        mourning.advanceTo(access, STAGE_BRIEFED)
        chatPlayer(neutral, "Alright, I'll see what I can do.")
    }

    suspend fun Dialogue.debrief() {
        if (mourning.stage(player) != STAGE_REVEALED) {
            return
        }
        arianwynSays(quiz, "How goes it ${player.displayName}?")
        chatPlayer(neutral, "The Iorwerth elves are searching for an ancient temple deep beneath West Ardougne.")
        arianwynSays(shocked, "A temple? That must be the Temple of Light!")
        chatPlayer(quiz, "What's that?")
        arianwynSays(worried, "Seren had it built to guard a dark and ancient power. I fear Iorwerth means to use that power to summon the Dark Lord.")
        chatPlayer(neutral, "They haven't found it yet, but they know it's down there.")
        arianwynSays(neutral, "Then there is still time. Thank you for all you have done. Now we must prepare for what comes next.")
        mourning.quest.completeQuest(access)
    }

    /** Arianwyn speaks for himself, or through his chathead when Eluned's conversation leads into his. */
    private suspend fun Dialogue.arianwynSays(mesanim: MesAnimType, text: String) {
        val speaking = npc
        if (speaking != null && (speaking.isType(ARIANWYN) || speaking.isType(ARIANWYN_VIS))) {
            chatNpc(mesanim, text)
        } else {
            chatNpcSpecific("Arianwyn", ARIANWYN_VIS, mesanim, text)
        }
    }

    companion object {
        const val ARIANWYN = "npc.mourning_arianwyn"
        const val ARIANWYN_VIS = "npc.mourning_arianwyn_vis"
    }
}
