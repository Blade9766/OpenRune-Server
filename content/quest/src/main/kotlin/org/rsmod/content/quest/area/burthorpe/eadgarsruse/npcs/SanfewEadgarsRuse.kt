package org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.DRUIDIC_RITUAL
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GOUTWEED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STOREROOM
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.needsRobe
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

/**
 * Sanfew's "general questions" once Druidic Ritual is behind the player: he starts Eadgar's Ruse,
 * points the player at Tegid for dirty robes, takes the goutweed that completes it and afterwards
 * swaps any more goutweed for grimy herbs off the standard herb table.
 */
@Singleton
class SanfewEadgarsRuse @Inject constructor(private val eadgarsRuse: EadgarsRuseQuest) {

    /** Druidic Ritual is done for Sanfew's purposes: really finished, or assumed so when unstarted. */
    fun handles(player: Player): Boolean {
        val ritual = Quest.get(DRUIDIC_RITUAL) ?: return false
        val stage = ritual.getQuestStage(player)
        return ritual.isQuestCompleted(player) || (stage == 0 && QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL))
    }

    suspend fun Dialogue.generalQuestions() {
        val stage = eadgarsRuse.stage(player)
        when {
            eadgarsRuse.isComplete(player) -> afterQuest()
            stage == 0 -> beforeQuest()
            stage >= STAGE_STOREROOM && GOUTWEED in player.inv -> complete()
            needsRobe(player) -> robes()
            else -> reminder()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        val work =
            choice2(
                "Have you any more work for me, to help reclaim the circle?", true,
                "Actually, I don't need to speak to you.", false,
            )
        if (!work) {
            dontNeed()
            return
        }
        chatPlayer(quiz, "Have you any more work for me, to help reclaim the circle?")
        if (!eadgarsRuse.meetsQuestRequirements(player)) {
            chatNpc(neutral, "Not just yet, young 'un.")
            mesbox("You do not meet all of the requirements to start the Eadgar's Ruse quest.")
            return
        }
        if (!eadgarsRuse.meetsHerbloreRequirement(player)) {
            chatNpc(
                neutral,
                "I'm afraid you are not yet learned enough in the art of Herblore to be able to help " +
                    "me, adventurer...",
            )
            mesbox("You do not meet all of the requirements to start the Eadgar's Ruse quest.")
            return
        }
        chatNpc(neutral, "Ah, you've come just in time. I need a certain herb for the next part of the purification ritual...")
        chatNpc(
            neutral,
            "It used to be quite common, but nowadays only the trolls know where to find it. They " +
                "use it in their cooking, you see. It's a very prized ingredient.",
        )
        chatPlayer(quiz, "And what exactly do you want me to do?")
        chatNpc(neutral, "Journey to the north into the land of the trolls, and find the secret of the herb the trolls call 'goutweed'.")
        chatNpc(neutral, TASK_EADGAR)
        val accept = choice2("Yes.", true, "No.", false, title = "Start the Eadgar's Ruse quest?")
        if (!accept) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(sad, "Oh well. No doubt some other adventurer will eventually come who can help me.")
            return
        }
        chatPlayer(happy, "I'll do it.")
        eadgarsRuse.advanceTo(access, STAGE_STARTED)
        chatNpc(happy, "Thank you, adventurer!")
    }

    private suspend fun Dialogue.reminder() {
        val again =
            choice2(
                "What was I meant to be doing again?", true,
                "Actually, I don't need to speak to you.", false,
            )
        if (!again) {
            dontNeed()
            return
        }
        chatPlayer(quiz, "What was I meant to be doing again?")
        chatNpc(neutral, "I've told you already.")
        chatNpc(neutral, "Journey to the north into the land of the trolls, and find the secret of the herb the trolls call goutweed.")
        chatNpc(neutral, TASK_EADGAR)
        chatPlayer(neutral, "I'll get on with it.")
    }

    private suspend fun Dialogue.robes() {
        chatPlayer(neutral, "Eadgar says he needs some dirty clothes for his plan, and he said you might be able to help.")
        chatNpc(quiz, "Now why would he need that?")
        chatPlayer(neutral, "It's a long story.")
        chatNpc(
            neutral,
            "Never mind then... I think Tegid is doing his laundry outside. You could ask him if " +
                "you can borrow one of his dirty robes.",
        )
    }

    private suspend fun Dialogue.complete() {
        chatPlayer(happy, "I have some goutweed!")
        access.invDel(access.inv, GOUTWEED)
        chatNpc(
            happy,
            "Excellent! I will be able to complete the next part of the ritual now. I will teach " +
                "you a new spell and give you some of my knowledge in Herblore as a token of thanks.",
        )
        chatNpc(
            neutral,
            "If you ever come across more goutweed, bring it to me; I don't need any more for the " +
                "ritual, but it's still quite difficult for me to get. I'll exchange it for some other herbs.",
        )
        eadgarsRuse.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        val hasGoutweed = GOUTWEED in player.inv
        val picked =
            choice3(
                if (hasGoutweed) "I have some more goutweed for you." else "Did you say you needed more goutweed?", 1,
                "Have you any more work for me, to help reclaim the circle?", 2,
                "Actually, I don't need to speak to you.", 3,
            )
        when (picked) {
            1 ->
                if (hasGoutweed) {
                    chatPlayer(happy, "I have some more goutweed for you.")
                    exchangeGoutweed()
                    chatNpc(happy, "Ah, good. Here are some herbs in exchange.")
                } else {
                    chatPlayer(quiz, "Did you say you needed more goutweed?")
                    chatNpc(
                        neutral,
                        "I don't need any more goutweed for the ritual, but it's still quite " +
                            "difficult for me to get. If you ever come across some, bring it to me; " +
                            "I'll exchange it for some other herbs.",
                    )
                }
            2 -> {
                chatPlayer(quiz, "Have you any more work for me to help reclaim the stone circle?")
                chatNpc(
                    neutral,
                    "Well, not right now I don't think young 'un. In fact, I need to make some more " +
                        "preparations myself for the ritual. Rest assured, if I need any more help I " +
                        "will ask you again.",
                )
            }
            else -> dontNeed()
        }
    }

    /** One grimy herb off the standard herb drop table for every goutweed carried. */
    private fun Dialogue.exchangeGoutweed() {
        val count = access.inv.count(GOUTWEED)
        access.invDel(access.inv, GOUTWEED, count)
        repeat(count) { access.invAdd(access.inv, rollHerb(access.random.of(HERB_TABLE_SIZE))) }
    }

    private suspend fun Dialogue.dontNeed() {
        chatPlayer(neutral, "Actually, I don't need to speak to you.")
        chatNpc(neutral, "Well, we all make mistakes sometimes.")
    }

    companion object {
        const val TASK_EADGAR =
            "My friend Eadgar lives in the area, and he may be able to help you. Bring some goutweed " +
                "back and I will teach you something useful."

        const val HERB_TABLE_SIZE = 128

        val HERB_TABLE =
            listOf(
                "obj.unidentified_guam" to 32,
                "obj.unidentified_marentill" to 24,
                "obj.unidentified_tarromin" to 18,
                "obj.unidentified_harralander" to 14,
                "obj.unidentified_ranarr" to 11,
                "obj.unidentified_irit" to 8,
                "obj.unidentified_avantoe" to 6,
                "obj.unidentified_kwuarm" to 5,
                "obj.unidentified_cadantine" to 4,
                "obj.unidentified_lantadyme" to 3,
                "obj.unidentified_dwarf_weed" to 3,
            )

        fun rollHerb(roll: Int): String {
            var remaining = roll
            for ((herb, weight) in HERB_TABLE) {
                if (remaining < weight) return herb
                remaining -= weight
            }
            return HERB_TABLE.last().first
        }
    }
}
