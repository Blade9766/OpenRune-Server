package org.rsmod.content.quest.area.zanaris.fairytale1.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_HAS_SYMPTOMS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_ZANDAR
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SYMPTOMS_LIST
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.ZANDAR
import org.rsmod.content.quest.area.zanaris.fairytale1.carries
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Zandar Horfyre, at the top of the Dark Wizards' Tower west of Falador. He reads Fairy Nuff's
 * symptoms list, names the Tanglefoot, and sends the player on to Malignius Mortifer.
 */
class ZandarHorfyre @Inject constructor(private val fairytale: Fairytale1Quest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ZANDAR) { startDialogue(it.npc) { zandar() } }
        onOpNpcU(ZANDAR) {
            if (it.objType.internalName == SYMPTOMS_LIST) {
                startDialogue(it.npc) { zandar() }
            } else {
                mes("Zandar Horfyre has no interest in that.")
            }
        }
    }

    private suspend fun Dialogue.zandar() {
        val stage = fairytale.stage(player)
        when {
            fairytale.isComplete(player) -> afterQuest()
            stage >= STAGE_SEEN_ZANDAR -> remind()
            stage == STAGE_HAS_SYMPTOMS && access.carries(SYMPTOMS_LIST) -> readTheList()
            stage == STAGE_HAS_SYMPTOMS -> noList()
            else -> notInterested()
        }
    }

    private suspend fun Dialogue.notInterested() {
        chatPlayer(neutral, "Hello.")
        chatNpc(
            angry,
            "This tower is not a tourist attraction. I am working. Whatever it is you want, the " +
                "answer is no.",
        )
    }

    private suspend fun Dialogue.noList() {
        chatPlayer(neutral, "I need your help with something very unusual.")
        chatNpc(neutral, "Everyone does. Come back when you have something for me to actually look at.")
    }

    private suspend fun Dialogue.readTheList() {
        chatPlayer(neutral, "I've been told you know about things other wizards won't touch.")
        chatNpc(neutral, "You have been told correctly. What is it?")
        chatPlayer(neutral, "A fairy is ill. These are her symptoms.")
        chatNpc(confused, "A fairy. Ill. You are wasting my - ")
        access.anim(HAND_OVER_SEQ)
        objbox(SYMPTOMS_LIST, "Zandar Horfyre snatches the list out of your hand.")
        chatNpc(neutral, "...Sleep without waking. Cold to the touch. No colour left in the wings.")
        chatNpc(
            neutral,
            "This is not an illness. A fairy cannot be ill. A fairy can only be cut off from the " +
                "growing of things, and there is precisely one creature that does that.",
        )
        chatPlayer(quiz, "And that is?")
        chatNpc(
            neutral,
            "A Tanglefoot. A walking thorn bush, and a spiteful one. It will have taken something " +
                "of hers - a tool, a token - and while it holds it she will not wake.",
        )
        when (
            choice2(
                "Then I'll go and kill it.", 1,
                "How do you kill a Tanglefoot?", 2,
            )
        ) {
            1 -> chatPlayer(neutral, "Then I'll go and kill it.")
            2 -> chatPlayer(quiz, "How do you kill a Tanglefoot?")
        }
        chatNpc(
            laugh,
            "With a sword? You will blunt it. Nothing forged will mark a Tanglefoot. You need " +
                "something grown, and something enchanted, and I do not do enchantments.",
        )
        chatNpc(
            neutral,
            "Malignius Mortifer does. A necromancer, south of Falador where the roads cross. Tell " +
                "him I sent you, and try not to let him talk about his career.",
        )
        access.invDel(access.inv, SYMPTOMS_LIST)
        fairytale.advanceTo(access, STAGE_SEEN_ZANDAR)
        chatPlayer(neutral, "Thank you.")
        chatNpc(neutral, "I am keeping the list. It is the most interesting thing I have read all year.")
    }

    private suspend fun Dialogue.remind() {
        chatPlayer(neutral, "About the fairy...")
        chatNpc(
            neutral,
            "A Tanglefoot has her token. Go to Malignius Mortifer, south of Falador, and stop " +
                "coming up my stairs.",
        )
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(neutral, "The Tanglefoot is dead. You were right.")
        chatNpc(happy, "I am always right. It is the one consolation of a career like mine.")
    }

    private companion object {
        const val HAND_OVER_SEQ = "seq.human_pickuptable"
    }
}
