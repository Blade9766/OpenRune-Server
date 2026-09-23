package org.rsmod.content.quest.area.varrock.romeojuliet

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Romeo & Juliet.
 *
 * Stages live in the whole cache varp `varp.rjquest` (144), endstate [STAGE_COMPLETE] from
 * `dbrow.quest_romeoandjuliet`; the values are 2004Scape's `romeojuliet.constant`:
 * [STAGE_STARTED] Romeo asked for help, [STAGE_HAS_MESSAGE] Juliet wrote him a letter,
 * [STAGE_MESSAGE_DELIVERED] Romeo read it, [STAGE_SEEN_FATHER] Father Lawrence proposed the
 * cadava potion, [STAGE_SEEN_APOTHECARY] the Apothecary asked for berries and [STAGE_JULIET_CRYPT]
 * Juliet drank the potion and was carried to the crypt.
 *
 * Juliet is spawned as `npc.juliet_multi_visible`, a multinpc on `varbit.romjul_juliet_visible`
 * (odd values hide her); she is hidden while she lies in the crypt and back afterwards.
 */
@Singleton
class RomeoJulietQuest : QuestScript(
    QUEST_KEY,
    "varp.rjquest",
    rewards {},
    ItemRewardDisplay(CADAVA_POTION, zoom = 120),
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun setStage(access: ProtectedAccess, stage: Int) {
        quest.setQuestStage(access, stage)
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun owns(access: ProtectedAccess, obj: String): Boolean =
        access.inv.count(obj) > 0 || access.bank.count(obj) > 0

    private fun syncVars(player: Player) {
        val hidden = if (stage(player) == STAGE_JULIET_CRYPT) JULIET_HIDDEN else JULIET_VISIBLE
        if (player.julietVisibility != hidden) {
            player.julietVisibility = hidden
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Romeo</col> in <col=800000>Varrock Square</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            objective(
                "<red>Romeo</red> asked me to find his beloved <red>Juliet</red> and tell her he " +
                    "longs to be with her. She lives in her father's mansion just west of " +
                    "Varrock, beyond the west bank.",
            ) {
                visibleWhen { stage(p) >= STAGE_STARTED }
                stageAtLeast(
                    STAGE_HAS_MESSAGE,
                    "I found Juliet on the balcony of her father's mansion west of Varrock.",
                    strike = true,
                )
            }
            objective("Juliet gave me a <red>message</red> to take back to <red>Romeo</red>.") {
                visibleWhen { stage(p) >= STAGE_HAS_MESSAGE }
                stageAtLeast(STAGE_MESSAGE_DELIVERED, "I delivered Juliet's message to Romeo.", strike = true)
            }
            objective(
                "Juliet's father forbids the marriage. Romeo thinks <red>Father Lawrence</red> " +
                    "may be able to help. He preaches in the church north-east of Varrock Square.",
            ) {
                visibleWhen { stage(p) >= STAGE_MESSAGE_DELIVERED }
                stageAtLeast(
                    STAGE_SEEN_FATHER,
                    "Father Lawrence suggested a potion that will make Juliet appear dead.",
                    strike = true,
                )
            }
            objective(
                "I need to ask the <red>Apothecary</red> in south-west Varrock for a " +
                    "<red>cadava potion</red>.",
            ) {
                visibleWhen { stage(p) >= STAGE_SEEN_FATHER }
                stageAtLeast(
                    STAGE_SEEN_APOTHECARY,
                    "The Apothecary will make the potion if I bring him some <red>cadava " +
                        "berries</red>. They grow just west of the south-east Varrock mine.",
                )
            }
            objective("I have the <red>cadava potion</red>. I should take it to <red>Juliet</red>.") {
                visibleWhen {
                    stage(p) == STAGE_SEEN_APOTHECARY && access.inv.count(CADAVA_POTION) > 0
                }
            }
            objective(
                "Juliet drank the potion and has been carried to the crypt. I should tell " +
                    "<red>Romeo</red> to go and collect her.",
            ) {
                visibleWhen { stage(p) >= STAGE_JULIET_CRYPT }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Romeo asked me to find Juliet, who was being kept from him by her father, Draul " +
                    "Leptoc. I carried letters between them, and their old confidant Father " +
                    "Lawrence came up with a plan.",
            )
            line(
                "The Apothecary brewed a cadava potion to make Juliet appear dead, so that she " +
                    "would be laid in the crypt for Romeo to collect.",
            )
            line(
                "When I took Romeo to the crypt he met Juliet's cousin Phillipa and forgot all " +
                    "about Juliet. So much for true love.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_romeoandjuliet"

        const val STAGE_STARTED = 10
        const val STAGE_HAS_MESSAGE = 20
        const val STAGE_MESSAGE_DELIVERED = 30
        const val STAGE_SEEN_FATHER = 40
        const val STAGE_SEEN_APOTHECARY = 50
        const val STAGE_JULIET_CRYPT = 60
        const val STAGE_COMPLETE = 100

        const val JULIET_VISIBLE = 0
        const val JULIET_HIDDEN = 1

        const val MESSAGE = "obj.julietmessage"
        const val CADAVA_BERRIES = "obj.cadavaberries"
        const val CADAVA_POTION = "obj.cadava"

        const val ROMEO = "npc.romeo"
        const val JULIET = "npc.juliet"
        const val JULIET_MULTI = "npc.juliet_multi_visible"
        const val PHILLIPA = "npc.phillipa"
        const val DRAUL = "npc.draul_leptoc"
        const val FATHER_LAWRENCE = "npc.father_lawrence"
        const val APOTHECARY = "npc.apothecary"

        const val HEART_SPOTANIM = "spotanim.trollromance_heart"
    }
}
