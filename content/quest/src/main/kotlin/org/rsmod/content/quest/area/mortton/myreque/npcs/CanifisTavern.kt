package org.rsmod.content.quest.area.mortton.myreque.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.REQUIRED_AGILITY
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_SHORTCUT_OPENED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STRANGER_NPC
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.VANSTROM_SITTING
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The seat in the Canifis tavern: Vanstrom Klause until the weapons reach the Myreque, then a
 * stranger who has never heard of him. The cache multinpc swaps them on `thsfm_vanstrom_hide`.
 */
class CanifisTavern @Inject constructor(private val myq: InSearchOfTheMyrequeQuest) : PluginScript() {

    private val quest
        get() = myq.quest

    override fun ScriptContext.startup() {
        onOpNpc1(VANSTROM_SITTING) { startDialogue(it.npc) { vanstrom() } }
        onOpNpc1(STRANGER_NPC) { startDialogue(it.npc) { stranger() } }
    }

    private suspend fun Dialogue.vanstrom() {
        if (myq.stage(player) == 0) {
            firstMeeting()
        } else {
            reminder()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(neutral, "Sit, if you can bear the smell. Nobody drinks in Canifis for the company.")
        when (
            choice3(
                "Who are you?", 1,
                "Do you know of any work around here?", 2,
                "I'll leave you to your drink.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(neutral, "Vanstrom Klause. A merchant, of sorts. I deal in whatever these lands are short of, and these lands are short of nearly everything.")
                chatPlayer(quiz, "Is there anything you need?")
                offer()
            }
            2 -> {
                chatPlayer(quiz, "Do you know of any work around here?")
                offer()
            }
            3 -> chatPlayer(neutral, "I'll leave you to your drink.")
        }
    }

    private suspend fun Dialogue.offer() {
        chatNpc(neutral, "Perhaps. Have you heard of the Myreque?")
        chatPlayer(confused, "Can't say I have.")
        chatNpc(neutral, "A handful of people who still refuse to kneel to the Drakans, the family that rules this land. They hide out in the swamp and strike when they can.")
        chatNpc(sad, "Brave, but hopelessly under-equipped. Sticks and kitchen knives against the masters of Morytania.")
        chatNpc(neutral, "I'd like them to have proper steel. A steel longsword, two steel swords, a steel mace, a steel warhammer and a steel dagger would arm them well.")
        chatPlayer(quiz, "Why not take them yourself?")
        chatNpc(shifty, "My face is too well known in the wrong places. Someone like you, though, would pass unnoticed.")
        if (!myq.meetsRequirements(player, access.statBase(AGILITY))) {
            chatNpc(neutral, "But the swamp would eat you alive. Come back when you've made a name for yourself against the ghasts of Mort Myre and can move a little more nimbly.")
            access.mes("You need level $REQUIRED_AGILITY Agility and to have completed Nature Spirit to start this quest.")
            return
        }
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "I don't think I want to get involved.")
            chatNpc(neutral, "As you wish. The offer stands.")
            return
        }
        chatPlayer(happy, "I'll do it. Where do I find them?")
        myq.advanceTo(access, STAGE_STARTED)
        chatNpc(happy, "Splendid. I don't know where they hide, but a boatman in Mort'ton does. His name is Cyreg Paddlehorn. He's wary of strangers, so you'll have to win him over.")
        chatNpc(neutral, "You'll be crossing ghast country. Take your druid pouch, and something to fight with besides the delivery.")
    }

    private suspend fun Dialogue.reminder() {
        chatNpc(quiz, "Back so soon? Have you found the Myreque?")
        when (
            choice3(
                "What did they need again?", 1,
                "Tell me more about the Myreque.", 2,
                "I'm still looking.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What did they need again?")
                chatNpc(neutral, "A steel longsword, two steel swords, a steel mace, a steel warhammer and a steel dagger. Keep them in your pack; they're a gift, not your own arms.")
                chatNpc(neutral, "Cyreg Paddlehorn in Mort'ton will know the way.")
            }
            2 -> {
                chatPlayer(quiz, "Tell me more about the Myreque.")
                chatNpc(neutral, "The name means 'Hidden in Myre'. Fitting, if they want to stay alive. Their leader is said to be a man named Veliaf Hurtz.")
                chatNpc(neutral, "Beyond that I know very little. Which is rather the point of them.")
            }
            3 -> {
                chatPlayer(neutral, "I'm still looking.")
                chatNpc(neutral, "Patience, then. Mort'ton is south-east, across the swamp.")
            }
        }
    }

    private suspend fun Dialogue.stranger() {
        val stage = myq.stage(player)
        when {
            myq.isComplete(player) -> {
                chatPlayer(quiz, "Hello again.")
                chatNpc(neutral, "I don't believe we've met. Do let me drink in peace.")
            }
            stage >= STAGE_SHORTCUT_OPENED -> confrontation()
            else -> {
                chatPlayer(quiz, "Have you seen a man called Vanstrom Klause? He sat here.")
                chatNpc(neutral, "Never heard of him. This seat was empty when I came in.")
            }
        }
    }

    private suspend fun Dialogue.confrontation() {
        chatPlayer(angry, "You're sitting where Vanstrom Klause sat.")
        chatNpc(neutral, "I sit wherever there is a chair. Is that a crime in Canifis now?")
        chatPlayer(angry, "He sent me to the Myreque with weapons. He followed me there and murdered two of them.")
        chatNpc(neutral, "A sad tale. And you're telling it loudly, in a tavern full of werewolves.")
        chatPlayer(quiz, "Are you him?")
        chatNpc(neutral, "If I were a vampyre, would I sit here and warn you to lower your voice? I've never heard the name before tonight.")
        chatNpc(neutral, "Go home, friend. Whatever hunts your Myreque has a long memory, and so do the walls of this town.")
        mesbox("The stranger turns back to his drink. Whoever he is, you doubt you have seen the last of Vanstrom Klause.")
        chatPlayer(worried, "The Myreque are still out there. Fewer than before, but still fighting. I hope they're ready for him next time.")
        myq.complete(access)
    }

    private companion object {
        const val AGILITY = "stat.agility"
    }
}
