package org.rsmod.content.quest.area.zanaris.fairytale1.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.GARDENERS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.GARDENERS_NEEDED
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_GARDENERS_ASKED
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Group of Advanced Gardeners: every farmer who minds a patch. During Growing Pains each of
 * them has their own theory about why nothing is growing, and the fifth one the player asks is
 * always the one who blames the fairies, whichever five they pick.
 */
class AdvancedGardeners
@Inject
constructor(private val fairytale: Fairytale1Quest) : PluginScript() {

    override fun ScriptContext.startup() {
        for ((index, gardener) in GARDENERS.withIndex()) {
            onOpNpc1(gardener) { startDialogue(it.npc) { gardener(index) } }
        }
    }

    private suspend fun Dialogue.gardener(index: Int) {
        val stage = fairytale.stage(player)
        if (stage != STAGE_STARTED) {
            idleChat()
            return
        }
        if (fairytale.hasAskedGardener(player, index)) {
            chatPlayer(quiz, "Are you a member of the Group of Advanced Gardeners?")
            chatNpc(neutral, "I am, and I've already told you what I think. Go and ask someone else.")
            return
        }
        chatPlayer(quiz, "Are you a member of the Group of Advanced Gardeners?")
        chatNpc(happy, "The G.A.G.? I am indeed. Why do you ask?")
        chatPlayer(
            quiz,
            "Martin in Draynor says nothing will grow for him any more. Have you had the same " +
                "trouble?",
        )
        fairytale.markGardenerAsked(player, index)
        val asked = fairytale.gardenersAskedCount(player)
        if (asked >= GARDENERS_NEEDED) {
            fairyTheory()
            fairytale.advanceTo(access, STAGE_GARDENERS_ASKED)
            chatPlayer(neutral, "That's the fifth answer I've had, and the strangest. I'll take it back to Martin.")
            return
        }
        chatNpc(neutral, THEORIES[index % THEORIES.size])
        chatPlayer(neutral, "Thanks. I'll see what the others say.")
    }

    /** Whoever the player asks fifth is the one who says it out loud. */
    private suspend fun Dialogue.fairyTheory() {
        chatNpc(worried, "Aye, and I'll tell you what nobody else will: it's the fairies.")
        chatPlayer(confused, "The fairies?")
        chatNpc(
            neutral,
            "Who do you think makes things grow? Not the rain, not the sun, not the muck we " +
                "spread. The fairies tend every growing thing there is, and they've stopped.",
        )
        chatNpc(
            worried,
            "Something's gone wrong in their world. Until it's put right, nothing of ours will " +
                "come up either.",
        )
    }

    private suspend fun Dialogue.idleChat() {
        chatPlayer(neutral, "Hello there.")
        if (fairytale.isComplete(player)) {
            chatNpc(happy, "Hello again! Whatever you did, the patches are coming along nicely now.")
            return
        }
        chatNpc(neutral, "Hello. I'd stop and chat, but there's weeding to be done.")
    }

    private companion object {
        /** One theory per gardener, in [GARDENERS] order; none of them agree with each other. */
        val THEORIES =
            listOf(
                "It's the rain, or the lack of it. We've had three dry months and the soil's " +
                    "gone to dust.",
                "Insects. I've never seen so many aphids, and the ladybirds have all gone.",
                "The seasons are out of step. The frost came late and the seed never woke up.",
                "Adventurers. Tramping over the beds day and night with their big muddy boots.",
                "The soil's tired, that's all. Land needs a rest same as a man does.",
                "Bad seed. Whoever the seed merchants are buying from, they're being cheated.",
                "Too much compost, if you ask me. Folk think you can't overfeed a plant. You can.",
                "It's the moon. Nothing planted on a waning moon ever came to much.",
                "Something in the water. I've tasted it; it's gone flat, like old ale.",
                "Magic, I shouldn't wonder. There's far too much of it about these days.",
            )
    }
}
