package org.rsmod.content.quest.area.falador.blackknightsfortress

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Black Knights' Fortress.
 *
 * The stage is the whole cache varp `varp.spy`, endstate 4 from `dbrow.quest_blackknightsfortress`,
 * with the values of the original script:
 * - [STAGE_STARTED]: Sir Amik Varze has sent the player to spy on the fortress.
 * - [STAGE_OVERHEARD]: the player listened at the grill and learnt of the invincibility potion.
 * - [STAGE_SABOTAGED]: a plain cabbage went down the hole into the witch's cauldron.
 * - [STAGE_COMPLETE]: Sir Amik has paid the reward.
 *
 * The cauldron below the hole is a multiloc on `varbit.spy_cauldron_multi`, derived from the stage
 * on every sync so it never needs saving on its own. `varbit.spy_armour_hint` remembers that a
 * guard explained the uniform, which adds the disguise to the journal.
 */
@Singleton
class BlackKnightsFortressQuest :
    QuestScript(
        QUEST_KEY,
        "varp.spy",
        rewards { item(COINS, REWARD_COINS, label = "2,500 Coins") },
        ItemRewardDisplay(COINS, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncWorld)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Sir Amik Varze</col> on the 2nd floor of the " +
            "<col=800000>White Knights' Castle</col> in <col=800000>Falador</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val stage = stage(access.player)
            val briefing =
                "Sir Amik Varze asked me to investigate the Black Knights' Fortress which is " +
                    "located on Ice Mountain."
            if (stage == STAGE_STARTED) {
                line(briefing)
                line("I need to find out what their secret weapon is, and then sabotage it.")
                if (access.player.armourHint == 1) {
                    line(
                        "The Fortress Guards wear an iron chainbody and a bronze med helm. " +
                            "Wearing the same might get me past the guards' entrance.",
                    )
                }
                return@questJournal
            }
            strike("$briefing I sneaked inside disguised as a guard.")
            val overheard =
                "I eavesdropped on a Witch and the Black Knight Captain and discovered that " +
                    "their invincibility potion can be destroyed with a normal cabbage."
            if (stage == STAGE_OVERHEARD) {
                line(overheard)
                line(
                    "A cabbage from Draynor Manor won't do; it would only help the witch. I " +
                        "should drop an ordinary cabbage into her cauldron.",
                )
                return@questJournal
            }
            strike(overheard)
            line(
                "Now that I have sabotaged the witch's potion, I can claim my reward from Sir " +
                    "Amik Varze in the White Knights' Castle.",
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Sir Amik Varze asked me to investigate the Black Knights' Fortress. I sneaked " +
                    "inside disguised as a guard.",
            )
            line(
                "I eavesdropped on a Witch and the Black Knight Captain and discovered that their " +
                    "invincibility potion could be destroyed with a normal cabbage.",
            )
            line(
                "I found a cabbage, and used it to destroy the potion, then claimed my reward " +
                    "for a job well done.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun hasQuestPoints(player: Player): Boolean = player.questPoints >= REQUIRED_QUEST_POINTS

    fun advance(access: ProtectedAccess) {
        quest.advanceQuestStage(access)
    }

    fun isDisguised(player: Player): Boolean = isGuardDisguise(player) || isBlackKnightDisguise(player)

    fun isBlackKnightDisguise(player: Player): Boolean =
        player.worn.contains(BLACK_FULL_HELM) &&
            player.worn.contains(BLACK_PLATEBODY) &&
            (player.worn.contains(BLACK_PLATELEGS) || player.worn.contains(BLACK_PLATESKIRT))

    private fun isGuardDisguise(player: Player): Boolean =
        player.worn.contains(BRONZE_MED_HELM) && player.worn.contains(IRON_CHAINBODY)

    fun learnUniform(player: Player) {
        if (stage(player) >= STAGE_STARTED) {
            player.armourHint = 1
        }
    }

    fun showSabotagedCauldron(player: Player, sabotaged: Boolean) {
        player.cauldronState = if (sabotaged) CAULDRON_SABOTAGED else CAULDRON_BREWING
    }

    private fun syncWorld(player: Player) {
        val stage = stage(player)
        showSabotagedCauldron(player, stage >= STAGE_SABOTAGED)
        if (stage == 0) {
            player.armourHint = 0
        }
    }

    companion object {
        const val QUEST_KEY = "quest_blackknightsfortress"

        const val STAGE_STARTED = 1
        const val STAGE_OVERHEARD = 2
        const val STAGE_SABOTAGED = 3
        const val STAGE_COMPLETE = 4

        const val REQUIRED_QUEST_POINTS = 12
        const val REWARD_COINS = 2500

        const val CAULDRON_BREWING = 0
        const val CAULDRON_SABOTAGED = 1

        const val COINS = "obj.coins"
        const val DOSSIER = "obj.bk_dossier"
        const val CABBAGE = "obj.cabbage"
        const val DRAYNOR_CABBAGE = "obj.magic_cabbage"

        const val BRONZE_MED_HELM = "obj.bronze_med_helm"
        const val IRON_CHAINBODY = "obj.iron_chainbody"
        const val BLACK_FULL_HELM = "obj.black_full_helm"
        const val BLACK_PLATEBODY = "obj.black_platebody"
        const val BLACK_PLATELEGS = "obj.black_platelegs"
        const val BLACK_PLATESKIRT = "obj.black_plateskirt"

        const val SIR_AMIK = "npc.sir_amik_varze"
        const val GUARD = "npc.fortressguard"
        const val CAPTAIN = "npc.grillknight"
        const val WITCH = "npc.fortwitch"
        const val GRELDO = "npc.greldo"
        const val CAT = "npc.bkf_cat"

        val BLACK_KNIGHTS = listOf("npc.aggressive_black_knight", "npc.aggressive_black_knight_f")

        val GUARDS =
            listOf(
                GUARD,
                "npc.fortressguard_01",
                "npc.fortressguard_02",
                "npc.fortressguard_03",
                "npc.fortressguard_04",
            )
    }
}

private val Player.questPoints: Int by intVarp("varp.qp")
private var Player.cauldronState: Int by intVarBit("varbit.spy_cauldron_multi")
private var Player.armourHint: Int by intVarBit("varbit.spy_armour_hint")
