package org.rsmod.content.quest.area.zanaris.fairytale1.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.MARTIN
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_GARDENERS_ASKED
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SENT_TO_ZANARIS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Martin the Master Gardener, by the pig pen in Draynor Village. He starts the quest, sends the
 * player round the Group of Advanced Gardeners, and once they have all blamed the fairies he
 * points the player at Zanaris.
 */
class MartinTheMasterGardener
@Inject
constructor(private val fairytale: Fairytale1Quest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MARTIN) { startDialogue(it.npc) { martin() } }
    }

    private suspend fun Dialogue.martin() {
        when (fairytale.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> askingAround()
            STAGE_GARDENERS_ASKED -> theFairies()
            else ->
                if (fairytale.isComplete(player)) {
                    afterQuest()
                } else {
                    stillBusy()
                }
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(sad, "Hello there. Sorry, I'm not much company today. My roses have got me worried.")
        when (
            choice3(
                "What's wrong with your roses?", 1,
                "Who are you?", 2,
                "Never mind, then.", 3,
            )
        ) {
            1 -> theRoses()
            2 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(
                    neutral,
                    "Martin's the name. Master Gardener, they call me, and I've the flowers to " +
                        "show for it. Or I did have.",
                )
                theRoses()
            }
            3 -> chatPlayer(neutral, "Never mind, then.")
        }
    }

    private suspend fun Dialogue.theRoses() {
        chatPlayer(quiz, "What's wrong with your roses?")
        chatNpc(
            sad,
            "Nothing, and that's the trouble. They're not diseased, they're not eaten, they're " +
                "not even wilting. They've simply stopped growing.",
        )
        chatNpc(
            neutral,
            "Thirty years I've worked this patch and I've never seen the like. And it isn't just " +
                "me: half the Group of Advanced Gardeners say the same.",
        )
        when (
            choice3(
                "Is there anything I can do to help?", 1,
                "What's the Group of Advanced Gardeners?", 2,
                "That does sound worrying.", 3,
            )
        ) {
            1 -> offerHelp()
            2 -> {
                chatPlayer(quiz, "What's the Group of Advanced Gardeners?")
                chatNpc(
                    happy,
                    "The G.A.G., we call it. Every farmer who minds an allotment, a herb patch " +
                        "or a tree patch across the whole of Gielinor belongs to it. If there's " +
                        "something wrong with the soil, they'll have noticed.",
                )
                offerHelp()
            }
            3 -> {
                chatPlayer(sad, "That does sound worrying.")
                chatNpc(neutral, "It is. Come back if you've a mind to look into it.")
            }
        }
    }

    private suspend fun Dialogue.offerHelp() {
        chatPlayer(quiz, "Is there anything I can do to help?")
        chatNpc(
            neutral,
            "Now that I think about it, you're right - there might be. I can't leave my patch, " +
                "but you could go and ask the others what they make of it.",
        )
        chatNpc(
            neutral,
            "Find five of the G.A.G. and hear them out. Between the five of them we might have " +
                "an answer.",
        )
        if (!fairytale.meetsRequirements(player)) {
            chatPlayer(neutral, "I'll see what I can do.")
            mesbox("You do not meet all of the requirements to start the Fairytale I - Growing Pains quest.")
            return
        }
        when (
            choice2(
                "All right, I'll ask around.", 1,
                "Sorry, I've other things to do.", 2,
                title = "Start the Fairytale I - Growing Pains quest?",
            )
        ) {
            1 -> {
                chatPlayer(happy, "All right, I'll ask around.")
                fairytale.advanceTo(access, STAGE_STARTED)
                chatNpc(
                    happy,
                    "Good lad. Er - good adventurer. Any five of them will do; they're scattered " +
                        "from here to the Gnome Stronghold.",
                )
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I've other things to do.")
                chatNpc(sad, "Aye, well. The roses aren't going anywhere. Neither am I.")
            }
        }
    }

    private suspend fun Dialogue.askingAround() {
        val asked = fairytale.gardenersAskedCount(player)
        chatPlayer(neutral, "Hello again, Martin.")
        if (asked == 0) {
            chatNpc(
                neutral,
                "Haven't spoken to any of the G.A.G. yet? Get out there - allotments, herb " +
                    "patches, tree patches. Five of them.",
            )
            return
        }
        chatNpc(
            quiz,
            "How many of the G.A.G. have you spoken to now? I make it $asked of the five.",
        )
        chatPlayer(neutral, "I'll find the rest.")
    }

    private suspend fun Dialogue.theFairies() {
        chatPlayer(happy, "I've spoken to five of your Group of Advanced Gardeners.")
        chatNpc(quiz, "And? What's the verdict?")
        chatPlayer(
            neutral,
            "No two of them agree. Rain, insects, the seasons, adventurers trampling the beds - " +
                "and one of them blamed the fairies.",
        )
        chatNpc(
            neutral,
            "The fairies. Of course it's the fairies. Everything grows because the fairies keep " +
                "it growing; every gardener worth the name knows that, even if they'd never say " +
                "it out loud.",
        )
        chatPlayer(quiz, "So what do we do about it?")
        chatNpc(
            neutral,
            "We? You. I've a patch to mind. Get yourself to Zanaris and find out what the " +
                "fairies think they're playing at.",
        )
        fairytale.advanceTo(access, STAGE_SENT_TO_ZANARIS)
        chatPlayer(quiz, "Zanaris? How do I get there?")
        chatNpc(
            neutral,
            "The shed in the middle of Lumbridge Swamp, with a dramen staff in your hand. Don't " +
                "look at me like that - you asked.",
        )
    }

    private suspend fun Dialogue.stillBusy() {
        chatPlayer(neutral, "Hello again, Martin.")
        chatNpc(quiz, "Any luck with the fairies?")
        chatPlayer(neutral, "Not yet. I'm working on it.")
        chatNpc(neutral, "Well, the roses will keep. Somehow.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "I've sorted out your fairy problem, Martin.")
        chatNpc(
            neutral,
            "Hmm, right. You'll forgive me if I reserve judgement on that until I actually have " +
                "some crops grow.",
        )
        chatPlayer(
            neutral,
            "There was a Fairy Godfather sitting on the Queen's throne, and a Tanglefoot had her " +
                "secateurs. It's dealt with.",
        )
        chatNpc(
            confused,
            "Godfather... Tanglefoot... I don't know what you're talking about and I don't much " +
                "care. Come back once things have had a chance to grow.",
        )
    }
}
