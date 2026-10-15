package org.rsmod.content.quest.area.burthorpe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SABA
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpSabaAsked
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpTenzingAsked
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Saba, the hermit in the cave north-west of Burthorpe, who points the way to Tenzing. */
class Saba
@Inject
constructor(
    private val deathPlateau: DeathPlateauQuest,
    private val trollStronghold: TrollStrongholdQuest,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(SABA) { startDialogue(it.npc) { saba() } }
    }

    private suspend fun Dialogue.saba() {
        when {
            trollStronghold.isStarted(player) -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(angry, "Have you got rid of those pesky trolls yet?")
                chatPlayer(worried, "I'm afraid there's been some trouble...")
                chatNpc(angry, "You told me you'd get rid of the trolls!")
                chatPlayer(neutral, "I'm sure the Imperial Guard will deal with them in due course.")
                chatNpc(angry, "Hmph! They'd better!")
            }
            deathPlateau.isComplete(player) -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(angry, "Have you got rid of those pesky trolls yet?")
                chatPlayer(
                    happy,
                    "They will be gone soon! The Imperial Guard will use a secret way that " +
                        "starts from the back of the Sherpa's hut to destroy the troll camp!",
                )
                chatNpc(happy, "I shall have peace again at last!")
                chatNpc(
                    angry,
                    "If those pesky humans don't start trampling all over Death Plateau again " +
                        "that is!",
                )
            }
            !deathPlateau.isStarted(player) -> {
                chatPlayer(neutral, "Hello!")
                chatNpc(angry, "Why won't people leave me alone?!")
            }
            player.dpTenzingAsked -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(angry, "Have you got rid of those pesky trolls yet?")
                chatPlayer(neutral, "I'm working on it!")
                chatNpc(angry, "Grr!")
            }
            player.dpSabaAsked -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(angry, "Have you got rid of those pesky trolls yet?")
                chatPlayer(quiz, "Where did you say this Sherpa was?")
                chatNpc(angry, "I dunno but he must live around here somewhere!")
            }
            else -> questions()
        }
    }

    private suspend fun Dialogue.questions() {
        chatPlayer(neutral, "Hello!")
        chatNpc(angry, "What?!")
        when (
            choice3(
                "I'm looking for the guard that was on last night.",
                1,
                "Do you know of another way up Death Plateau?",
                2,
                "Nothing, sorry!",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "I'm looking for the guard that was on duty at the castle last night.")
                chatNpc(angry, "Who?!")
                chatNpc(angry, "Buzz off!")
            }
            2 -> anotherWayUp()
            else -> chatPlayer(neutral, "Nothing, sorry!")
        }
    }

    private suspend fun Dialogue.anotherWayUp() {
        chatPlayer(quiz, "Do you know of another way up Death Plateau?")
        chatNpc(angry, "Why would I want to go up there? I just want to be left in peace!")
        chatNpc(
            angry,
            "It used to be just humans trampling past my cave and making a racket. Now there's " +
                "those blasted trolls too! Not only do they stink and argue with each other " +
                "loudly but they are always fighting the humans.",
        )
        chatNpc(angry, "I just want to be left in peace!")
        chatPlayer(neutral, "Ah... I might be able to help you.")
        chatNpc(quiz, "How?!")
        chatPlayer(
            neutral,
            "I'm trying to help the...er...humans to reclaim back Death Plateau. If you help me " +
                "then at least you'd be rid of the trolls.",
        )
        chatNpc(bored, "Hmph.")
        chatNpc(neutral, "Let me see...")
        chatNpc(
            angry,
            "I've only been up Death Plateau once to complain about the noise but those pesky " +
                "trolls started throwing rocks at me!",
        )
        chatNpc(
            neutral,
            "Before the trolls came there used to be a nettlesome Sherpa that took humans " +
                "exploring or something equally stupid. Perhaps he'd know another way.",
        )
        player.dpSabaAsked = true
        chatPlayer(quiz, "Where does this Sherpa live?")
        chatNpc(
            neutral,
            "I don't know but it can't be far as he used to be around all the time!",
        )
    }
}
