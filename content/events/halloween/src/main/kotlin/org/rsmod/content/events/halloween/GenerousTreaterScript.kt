package org.rsmod.content.events.halloween

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GenerousTreaterScript
@Inject
constructor(private val season: HalloweenSeason, private val world: HalloweenWorld) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (treater in TREATERS) {
            onOpNpc1(treater) { talk(it.npc) }
            onOpNpc4(treater) { trickOrTreat(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        var trickOrTreat = false
        startDialogue(npc) {
            when (
                choice4(
                    "Trick or treat!",
                    Topic.TrickOrTreat,
                    "Hello, what are you doing?",
                    Topic.Doing,
                    "Nice costume!",
                    Topic.Costume,
                    "Nevermind, bye.",
                    Topic.Bye,
                )
            ) {
                Topic.TrickOrTreat -> trickOrTreat = true
                Topic.Doing -> whatAreYouDoing()
                Topic.Costume -> niceCostume()
                Topic.Bye -> {
                    chatPlayer(neutral, "Never mind, bye.")
                    chatNpc(happy, "Happy Halloween!")
                }
            }
        }
        if (trickOrTreat) {
            trickOrTreat(npc)
        }
    }

    private suspend fun ProtectedAccess.trickOrTreat(npc: Npc) {
        if (!startTrickOrTreat(season, npc)) {
            return
        }
        val visit = world.treaterVisit
        if (!world.isCurrentTreater(npc) || player.generousClaimed == visit) {
            startDialogue(npc) { chatNpc(neutral, random.pick(GREEDY_LINES)) }
            return
        }
        player.generousClaimed = visit
        addTreats(GENEROUS_TREATS)
        startDialogue(npc) { chatNpc(happy, random.pick(TREAT_LINES)) }
    }

    private suspend fun Dialogue.whatAreYouDoing() {
        chatPlayer(quiz, "Hello, what are you doing?")
        chatNpc(
            happy,
            "I just love seeing all these costumes, so I'm giving out treats to " +
                "Trick-or-Treaters!",
        )
        chatPlayer(quiz, "So you're not playing tricks like these other people?")
        chatNpc(happy, "Oh no, that's not my style. Though I do appreciate their spooky spirit!")
        chatPlayer(
            neutral,
            "I suppose so, but I thought we were supposed to play tricks on them...",
        )
        chatNpc(
            laugh,
            "It's all in good fun! Perhaps you could find a way to turn their tricks against " +
                "them?",
        )
        chatPlayer(shifty, "Yes, perhaps I could...")
    }

    private suspend fun Dialogue.niceCostume() {
        chatPlayer(happy, "Nice costume!")
        chatNpc(happy, "Thank you!")
        chatNpc(happy, "I always look forward to creating a different costume each year...")
        chatNpc(happy, "Not to mention seeing everyone else's costumes!")
        chatPlayer(happy, "It is nice to see everyone dressed up.")
        chatNpc(happy, "It certainly is!")
        if (player.hasCostume()) {
            chatNpc(happy, "I really like your costume too!")
            chatPlayer(happy, "Thank you! I spent a lot of time on it.")
        }
        chatNpc(happy, "Well, I'd better get back to treating. Happy Halloween!")
    }

    private enum class Topic {
        TrickOrTreat,
        Doing,
        Costume,
        Bye,
    }

    private companion object {
        const val GENEROUS_TREATS = 100

        val TREATERS =
            listOf(
                "npc.hw22_generous_treater_1",
                "npc.hw22_generous_treater_2",
                "npc.hw22_generous_treater_3",
                "npc.hw22_generous_treater_4",
            )

        val TREAT_LINES =
            listOf(
                "I choose treat! Have a happy Halloween!",
                "Ooh spooky costume! I do love this time of year!",
                "Treats for everyone! Enjoy!",
                "Fantastically frightful! Thank you for showing me your costume, enjoy these " +
                    "treats!",
            )
        val GREEDY_LINES =
            listOf(
                "As much as I love your costume, I'd like to see some other people's as well!",
                "Don't be greedy now, there needs to be enough treats for everyone!",
                "You've had some treats from me already!",
            )
    }
}
