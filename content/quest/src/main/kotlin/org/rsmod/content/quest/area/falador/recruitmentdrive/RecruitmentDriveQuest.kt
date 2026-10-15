package org.rsmod.content.quest.area.falador.recruitmentdrive

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Recruitment Drive.
 *
 * The stage is the cache varbit `varbit.rd_main` (endstate 2 from `dbrow.quest_recruitmentdrive`):
 * [STAGE_STARTED] once Sir Amik Varze has put the player forward, [STAGE_COMPLETE] once Sir Tiffy
 * Cashien has welcomed them as an initiate. The attempt itself (room order, passed rooms, each
 * room's answer) is kept apart from the stage, in [RecruitmentState].
 *
 * The coins and the sallet are handed over by Sir Tiffy with the usual drop-if-full delivery rather
 * than through the reward table, so a full inventory never loses them; the experience is the
 * reward table's and goes through the player's xp rate like every quest reward.
 */
@Singleton
class RecruitmentDriveQuest :
    QuestScript(
        QUEST_KEY,
        "varp.recruitmentdrive",
        rewards {
            xp("stat.prayer", REWARD_XP)
            xp("stat.herblore", REWARD_XP)
            xp("stat.agility", REWARD_XP)
            scroll(
                "1,000.5 Prayer XP",
                "1,000.5 Herblore XP",
                "1,000.5 Agility XP",
                "3,000 Coins",
                "Initiate Temple Knight armour",
                "The Gaze of Saradomin",
            )
        },
        ItemRewardDisplay(SALLET, zoom = 300),
        completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
        questVarbit = "varbit.rd_main",
    ) {

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /**
     * What the quest unlocks (initiate armour, the Gaze of Saradomin, later Temple Knight quests): the
     * quest really completed, or never started while the requirement policy counts it complete.
     */
    fun unlocked(player: Player): Boolean =
        isComplete(player) || (stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY))

    fun meetsRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, BlackKnightsFortressQuest.QUEST_KEY) &&
            QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL)

    fun start(access: ProtectedAccess) {
        if (stage(access.player) == 0) {
            quest.setQuestStage(access, STAGE_STARTED)
        }
    }

    fun complete(access: ProtectedAccess): Boolean {
        if (stage(access.player) != STAGE_STARTED) {
            return false
        }
        quest.completeQuest(access)
        return true
    }

    override fun subTitle(): String =
        "talking to <col=800000>Sir Amik Varze</col> on the 2nd floor of the western tower of the " +
            "<col=800000>White Knights' Castle</col> in <col=800000>Falador</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val passed = RecruitmentState.passedCount(p)
            line(
                "Sir Amik Varze has put my name forward to the Temple Knights. I should meet " +
                    "<col=800000>Sir Tiffy Cashien</col> in <col=800000>Falador Park</col> for testing.",
            )
            line(
                "I can't take any items or equipment into the training grounds, so I'll need an " +
                    "empty inventory and nothing worn.",
            )
            if (RecruitmentState.allPassed(p)) {
                line("I have passed all five tests! I should tell Sir Tiffy Cashien.")
            } else if (RecruitmentState.order(p) != null) {
                line("I have passed $passed of the five tests in a row so far.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Sir Amik Varze put my name forward to the Temple Knights, and Sir Tiffy Cashien " +
                    "took me to their secret training grounds.",
            )
            line(
                "I passed five of their tests in a row and was welcomed into the order with the " +
                    "rank of Initiate. Sir Tiffy will sell me initiate armour, and can let me " +
                    "respawn in Falador instead of Lumbridge.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_recruitmentdrive"
        const val DRUIDIC_RITUAL = "quest_druidicritual"

        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val REWARD_XP = 1000.5
        const val REWARD_COINS = 3000

        const val SIR_AMIK = "npc.sir_amik_varze"
        const val SIR_TIFFY = "npc.rd_teleporter_guy"

        const val COINS = "obj.coins"
        const val SALLET = "obj.basic_tk_helm"
        const val HAUBERK = "obj.basic_tk_body"
        const val CUISSE = "obj.basic_tk_legs"
        const val HARNESS = "obj.basic_tk_pack_yellow"
        const val SHOP_INV = "inv.templeknight_armoury1"
        const val SHOP_TITLE = "Initiate Temple Knight Armoury"
    }
}
