package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Tears of Guthix.
 *
 * The stage is `varbit.tog_juna_bowl` (bits 0-1 of `varp.tog_minigame`, which also holds the
 * minigame's story count, collecting flag, tear count and the quest points at the last visit).
 * Juna's multiloc gains its Story op and the light creatures their Attract op from the cache
 * multis on that varbit, so the stage values are pinned: [STAGE_STARTED] once Juna has heard a
 * story and [STAGE_COMPLETE] once she keeps the stone bowl.
 */
@Singleton
class TearsOfGuthixQuest @Inject constructor(private val rules: TearsOfGuthixRules) :
    QuestScript(
        QUEST_KEY,
        "varp.tog_minigame",
        rewards {
            xp("stat.crafting", CRAFTING_XP)
            extra("Access to the Tears of Guthix cave")
        },
        ItemRewardDisplay(STONE_BOWL, zoom = 200),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
        questVarbit = "varbit.tog_juna_bowl",
    ) {
    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun start(access: ProtectedAccess) {
        if (stage(access.player) == 0) {
            quest.setQuestStage(access, STAGE_STARTED)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Juna</col> in the <col=800000>Chasm of Tears</col>, deep in the " +
            "<col=800000>Lumbridge Swamp Caves</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = access.player
            objective(
                "<red>Juna</red> the serpent guards the <red>Tears of Guthix</red>. I entertained her " +
                    "with stories of my adventures, and she will let me collect the tears once I bring " +
                    "her a bowl made from the stone in the cave on the <red>south side</red> of the " +
                    "chasm.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED }
            }
            objective(
                "The <red>light creatures</red> floating in the chasm are drawn to their own colour. " +
                    "A lit <red>sapphire lantern</red> might attract one to carry me across.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED }
                custom(LIT_LANTERN in p.inv, "I have a lit sapphire lantern.")
            }
            objective(
                "I need to mine some <red>magic stone</red> and carve it into a bowl with a " +
                    "<red>chisel</red>.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED }
                custom(MAGIC_STONE in p.inv, "I have mined some magic stone.")
                custom(STONE_BOWL in p.inv, "I have made a stone bowl. I should give it to Juna.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I told Juna stories of my adventures, rode a light creature across the Chasm of " +
                    "Tears and carved a bowl from the magic stone there. Juna keeps the bowl for me, " +
                    "and I may collect the Tears of Guthix once a week.",
            )
            line(rules.journalNote(access.player))
        }

    companion object {
        const val QUEST_KEY = "quest_tearsofguthix"

        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val QP_REQ = 43
        const val FIREMAKING_REQ = 49
        const val CRAFTING_REQ = 20
        const val MINING_REQ = 20
        const val CRAFTING_XP = 1000.0

        const val STONE_BOWL = "obj.tog_bowl"
        const val MAGIC_STONE = "obj.tog_stone"
        const val EMPTY_LANTERN = "obj.tog_sapphire_lantern_empty"
        const val UNLIT_LANTERN = "obj.tog_sapphire_lantern_unlit"
        const val LIT_LANTERN = "obj.tog_sapphire_lantern_lit"
        const val JUNA = "loc.tog_juna"
        const val JUNA_CHATHEAD = "npc.tog_juna_dummy"
    }
}
