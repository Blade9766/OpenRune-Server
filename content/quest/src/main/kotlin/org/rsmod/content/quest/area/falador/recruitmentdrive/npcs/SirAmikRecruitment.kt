package org.rsmod.content.quest.area.falador.recruitmentdrive.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player

/** Sir Amik Varze's part in Recruitment Drive: putting the player forward to the Temple Knights. */
@Singleton
class SirAmikRecruitment @Inject constructor(private val quest: RecruitmentDriveQuest) {
    /**
     * Whether "Do you have any other quests for me to do?" leads anywhere for [player]: until the
     * quest is really complete, so it can be played even where the requirement policy already counts
     * it done.
     */
    fun hasSomethingToSay(player: Player): Boolean = !quest.isComplete(player)

    suspend fun Dialogue.otherQuests() {
        chatPlayer(quiz, "Do you have any other quests for me to do?")
        if (quest.isStarted(player)) {
            chatNpc(neutral, "No, I am afraid not. I suggest you go meet Sir Tiffy in Falador Park, he will be expecting you.")
            return
        }
        if (!quest.meetsRequirements(player)) {
            chatNpc(neutral, "Nothing I can think of at the moment...")
            mesbox("You do not meet all of the requirements to start the Recruitment Drive quest.")
            return
        }
        chatNpc(neutral, "Quests, eh? Well, I don't have anything on the go at the moment, but there is an organisation that is always looking for capable adventurers to assist them.")
        chatNpc(happy, "Your excellent work sorting out those Black Knights means I will happily write you a letter of recommendation.")
        chatNpc(quiz, "Would you like me to put your name forwards to them?")
        if (!startQuestPrompt(quest.quest)) {
            chatPlayer(neutral, "No thanks, that doesn't sound like the kind of thing that would interest me...")
            chatNpc(neutral, "As you wish adventurer, let's say no more about it.")
            return
        }
        chatPlayer(happy, "Sure thing Sir Amik, sign me up!")
        quest.start(access)
        chatNpc(shifty, "Erm, well, this is a little embarrassing, I already HAVE put you forward as a potential member.")
        chatNpc(neutral, "They are called the Temple Knights, and you are to meet Sir Tiffy Cashien in Falador park for testing immediately.")
        chatPlayer(happy, "Okey dokey, I'll go do that then.")
    }
}
