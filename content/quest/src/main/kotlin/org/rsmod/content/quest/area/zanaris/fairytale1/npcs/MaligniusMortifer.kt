package org.rsmod.content.quest.area.zanaris.fairytale1.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.DRAYNOR_SKULL
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.MORTIFER
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.MORTIFER_ITEMS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.MORTIFER_ITEM_NAMES
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_HAS_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_MORTIFER
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_ZANDAR
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Malignius Mortifer, the necromancer at the crossroads south of Falador. He reads a skull dug
 * out of the Draynor Manor grave and names the three ingredients the Nature Spirit needs; the
 * three are always consecutive entries of [MORTIFER_ITEMS], so one roll decides all of them.
 */
class MaligniusMortifer
@Inject
constructor(private val fairytale: Fairytale1Quest, private val random: GameRandom) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MORTIFER) { startDialogue(it.npc) { mortifer() } }
        onOpNpcU(MORTIFER) {
            if (it.objType.internalName == DRAYNOR_SKULL) {
                startDialogue(it.npc) { mortifer() }
            } else {
                mes("Malignius Mortifer waves the offering away.")
            }
        }
    }

    private suspend fun Dialogue.mortifer() {
        val stage = fairytale.stage(player)
        when {
            fairytale.isComplete(player) -> afterQuest()
            stage >= STAGE_HAS_SECATEURS -> nothingMoreToSay()
            stage == STAGE_SEEN_MORTIFER && fairytale.mortiferListGiven.get(player) -> repeatList()
            stage == STAGE_SEEN_MORTIFER && DRAYNOR_SKULL in player.inv -> readTheSkull()
            stage == STAGE_SEEN_MORTIFER -> remindAboutSkull()
            stage == STAGE_SEEN_ZANDAR -> askForSkull()
            else -> notInterested()
        }
    }

    private suspend fun Dialogue.notInterested() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(
            happy,
            "A visitor! Malignius Mortifer, master of necromancy, at your service. No, I do not " +
                "raise pets. Everybody asks.",
        )
    }

    private suspend fun Dialogue.askForSkull() {
        chatPlayer(neutral, "Zandar Horfyre sent me. I need help with fighting a Tanglefoot.")
        chatNpc(
            laugh,
            "Horfyre! Still shut up in that draughty tower. And a Tanglefoot, no less. You do " +
                "pick them.",
        )
        chatPlayer(quiz, "Can you help or not?")
        chatNpc(
            neutral,
            "Of course I can help. Steel is useless against a Tanglefoot; you need a growing " +
                "thing, enchanted. A pair of secateurs will do nicely, once they have been " +
                "properly worked on.",
        )
        chatPlayer(quiz, "And who works on them?")
        chatNpc(
            neutral,
            "The Nature Spirit, in the grotto in Mort Myre. He has the power. What he does not " +
                "have is a list of what to put into the enchantment, and that is where I come in.",
        )
        chatNpc(
            neutral,
            "Bring me a skull. Not just any skull - the one in the grave behind Draynor Manor. " +
                "Bring a spade, unless you intend to use your hands.",
        )
        fairytale.advanceTo(access, STAGE_SEEN_MORTIFER)
        chatPlayer(neutral, "A skull from a grave. Naturally.")
        chatNpc(happy, "You are catching on!")
    }

    private suspend fun Dialogue.remindAboutSkull() {
        chatNpc(quiz, "Have you got my skull?")
        chatPlayer(sad, "Not yet.")
        chatNpc(
            neutral,
            "The grave behind Draynor Manor, and a spade. It will not dig itself, though I have " +
                "known a few that tried.",
        )
    }

    private suspend fun Dialogue.readTheSkull() {
        chatPlayer(happy, "One Draynor skull, as requested.")
        chatNpc(happy, "Excellent! Let us see what the poor fellow remembers.")
        access.invDel(access.inv, DRAYNOR_SKULL)
        access.anim(CAST_SEQ)
        access.spotanim(CAST_SPOTANIM)
        access.soundSynth(CAST_SOUND)
        delay(CAST_TICKS)
        chatNpc(neutral, "Mmm. Yes. He is very insistent about the details.")
        fairytale.rollIngredients(player, random.of(MORTIFER_ITEMS.size))
        fairytale.mortiferListGiven.set(player, true)
        listIngredients()
        chatNpc(
            neutral,
            "Take those, and a pair of ordinary secateurs, to the Nature Spirit in Mort Myre. He " +
                "will know what to do with them.",
        )
        chatPlayer(neutral, "Thank you. I think.")
    }

    private suspend fun Dialogue.repeatList() {
        chatPlayer(quiz, "Could you go over the list again?")
        chatNpc(neutral, "The skull was quite clear about it.")
        listIngredients()
        chatNpc(neutral, "Those, and the secateurs, to the Nature Spirit in Mort Myre.")
    }

    private suspend fun Dialogue.listIngredients() {
        val indices = fairytale.ingredientIndices(player)
        if (indices.size < INGREDIENT_COUNT) {
            chatNpc(confused, "...and now it has gone quiet on me. Ask me again in a moment.")
            return
        }
        val names = indices.map { MORTIFER_ITEM_NAMES[it].lowercase() }
        chatNpc(
            neutral,
            "The enchantment wants ${names[0]}, ${names[1]}, and ${names[2]}. Nothing else will " +
                "do, and no, I cannot negotiate with a skull.",
        )
        for (index in indices) {
            objbox(
                MORTIFER_ITEMS[index],
                "The Nature Spirit will need ${MORTIFER_ITEM_NAMES[index].lowercase()}.",
            )
        }
    }

    private suspend fun Dialogue.nothingMoreToSay() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(happy, "The secateurs are enchanted, the Tanglefoot is waiting. Off you go.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "The Tanglefoot is dead. Your enchantment worked.")
        chatNpc(
            happy,
            "Of course it did. If you ever need another pair of secateurs enchanting, you know " +
                "where the crossroads are.",
        )
    }

    private companion object {
        const val INGREDIENT_COUNT = 3
        const val CAST_SEQ = "seq.human_castteleport"
        const val CAST_SPOTANIM = "spotanim.druidicspirit_effect"
        const val CAST_SOUND = "synth.godspell_charge"
        const val CAST_TICKS = 3
    }
}
