package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lunar Diplomacy.
 *
 * The stage is `varbit.lunar_quest_main` (bits 0-19 of `varp.lunar_quest`), endstate 190 from
 * `dbrow.quest_lunardiplomacy`. The values are the ones the cache's multi-npcs switch on: Lokar
 * offers the Pirates' Cove trip from [STAGE_SAILED_TO_COVE], Captain Bentley gains his Travel
 * option at [STAGE_AT_LUNAR_ISLE], and the cabin boy on the Lady Zay shows only on the ship stages.
 *
 * Everything else lives on the quest's own cache varbits in `varp.lunar_quest1..5`, which the
 * client reads for the ship's symbols, the dice, the log piles and the brazier. Several of those
 * varbits share bits (the Dream World challenges reuse the ship's symbol bits and each other's),
 * so a challenge clears its scratch varbits whenever the player leaves its island.
 */
@Singleton
class LunarDiplomacyQuest : QuestScript(
    QUEST_KEY,
    "varp.lunar_quest",
    rewards {
        xp("stat.magic", REWARD_XP)
        xp("stat.runecraft", REWARD_XP)
        scroll(
            "5,000 Magic XP",
            "5,000 Runecraft XP",
            "$ASTRAL_REWARD Astral runes",
            "Access to Lunar Isle",
            "Access to the Lunar spellbook",
            "Access to the Astral altar",
        )
    },
    ItemRewardDisplay(LUNAR_STAFF, zoom = 240),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    questVarbit = "varbit.lunar_quest_main",
) {
    /** The challenge the player last finished and has not yet talked through with the Ethereal Being. */
    val pendingLesson = quest.attribute(name = "PENDING_LESSON", default = 0)

    /** Whether the Oneiromancer is holding a lunar staff for the player. */
    val staffHeld = quest.attribute(name = "STAFF_HELD", default = false)

    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    /** The quests and levels a player still lacks before Lokar will take them to his ship. */
    fun missingRequirements(access: ProtectedAccess): List<String> {
        val missing = mutableListOf<String>()
        for ((key, name) in REQUIRED_QUESTS) {
            if (!QuestRequirements.hasCompleted(access.player, key)) {
                missing += name
            }
        }
        for ((stat, level) in REQUIRED_LEVELS) {
            if (access.statBase(stat) < level) {
                val name = stat.removePrefix("stat.").replaceFirstChar { it.uppercase() }
                missing += "level $level $name"
            }
        }
        return missing
    }

    /** Clears every sub-state varbit once the quest has been reset to not started. */
    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            setVarBit(player, varbit, 0)
        }
        pendingLesson.set(player, 0)
        staffHeld.set(player, false)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Lokar Searunner</col> on the westernmost dock of " +
            "<col=800000>Rellekka</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            objective(
                "<red>Lokar Searunner</red>, a Fremennik turned pirate, offered to take me to " +
                    "<red>Lunar Isle</red>, home of the <red>Moon Clan</red>, the Fremenniks' " +
                    "oldest enemies. I will need a <red>Seal of Passage</red> from " +
                    "<red>Brundt the Chieftain</red> to be safe there.",
            ) {
                visibleWhen { stage in STAGE_STARTED until STAGE_SAILED_TO_COVE }
                stageAtLeast(STAGE_GOT_SEAL, "Brundt gave me a Seal of Passage. I should go back to Lokar.")
            }
            objective(
                "Lokar brought me to his ship, the <red>Lady Zay</red>, at the Pirates' Cove. " +
                    "<red>Captain Bentley</red> will sail me to Lunar Isle.",
            ) {
                visibleWhen { stage == STAGE_SAILED_TO_COVE }
            }
            objective(
                "The ship sailed in a big circle and never reached the island. Someone on board " +
                    "must know what went wrong.",
            ) {
                visibleWhen { stage in STAGE_SAILED_IN_CIRCLE until STAGE_FOUND_CULPRIT }
                stageAtLeast(STAGE_BLAMED_NAVIGATOR, "The captain agreed it might be the navigator's fault.").strike()
                stageAtLeast(STAGE_LEARNT_OF_JINX, "'Birds-Eye' Jack thinks the ship has been <red>jinxed</red>.").strike()
                stageAtLeast(STAGE_ASKED_LOOKOUT, "'Eagle-eye' Shultz says the Moon Clan must have been offended at a feast.").strike()
                stageAtLeast(STAGE_ASKED_COOK, "'Beefy' Burns told me the whole crew went to that feast.").strike()
                stageAtLeast(STAGE_ASKED_LEE, "'Lecherous' Lee saw the <red>First Mate</red> slip away from it.").strike()
                stageAtLeast(STAGE_ASKED_FIRST_MATE, "The First Mate says the <red>Cabin boy</red> was missing all night.").strike()
            }
            objective(
                "The Cabin boy drew five jinx symbols around the ship for a Moon Clan girl. I need " +
                    "an <red>emerald lantern</red> to see them so I can rub them away.",
            ) {
                visibleWhen { stage in STAGE_FOUND_CULPRIT until STAGE_JINX_LIFTED }
                custom(symbolsRubbed(access.player) > 0, "I have rubbed away ${symbolsRubbed(access.player)} of the five symbols.")
            }
            objective("The jinx is lifted. I should ask <red>Captain Bentley</red> to set sail again.") {
                visibleWhen { stage == STAGE_JINX_LIFTED }
            }
            objective("I've arrived at <red>Lunar Isle</red>. I should explore the Moon Clan's town.") {
                visibleWhen { stage == STAGE_AT_LUNAR_ISLE }
            }
            objective(
                "The Moon Clan have no leader, but the <red>Oneiromancer</red> in the south-east " +
                    "of the island leads their initiations.",
            ) {
                visibleWhen { stage == STAGE_ENTERED_TOWN }
            }
            objective(
                "To understand the Moon Clan I must take their 'View of Self Dream'. First I " +
                    "need a <red>waking sleep potion</red>; <red>Baba Yaga</red> in her chicken " +
                    "house can help.",
            ) {
                visibleWhen { stage in STAGE_MET_ONEIROMANCER until STAGE_STAFF }
                stageAtLeast(
                    STAGE_POTION,
                    "The potion needs a <red>guam leaf</red>, a <red>marrentill</red> and a ground " +
                        "<red>Suqah tooth</red> in her special vial filled with water.",
                )
            }
            objective(
                "The Oneiromancer has my potion. Now I must enchant a <red>Dramen staff</red> at " +
                    "the <red>air</red>, <red>fire</red>, <red>water</red> and <red>earth</red> " +
                    "altars, in that order, to make a <red>Lunar staff</red>.",
            ) {
                visibleWhen { stage == STAGE_STAFF }
            }
            objective("Next I need the eight pieces of the Moon Clan's ceremonial clothing.") {
                visibleWhen { stage == STAGE_EQUIPMENT }
                for (piece in LunarPiece.entries) {
                    custom(piece.given(access.player), "I have given the Oneiromancer the ${piece.label}.").strike()
                }
            }
            objective(
                "I have everything. Wearing the full set and holding the Lunar staff, I must burn " +
                    "the soaked <red>kindling</red> on the <red>ceremonial brazier</red> in the town.",
            ) {
                visibleWhen { stage == STAGE_ENTER_DREAM }
            }
            objective(
                "I am in the Dream World. The <red>Ethereal Being</red> in the centre says there " +
                    "are six challenges for me to face.",
            ) {
                visibleWhen { stage in STAGE_IN_DREAM until STAGE_FACE_SELF }
                for (challenge in DreamChallenge.entries) {
                    custom(challenge.isComplete(access.player), "I have completed '${challenge.title}'.").strike()
                }
            }
            objective("There is one more challenge. I must face <red>myself</red>.") {
                visibleWhen { stage == STAGE_FACE_SELF }
            }
            objective(
                "I defeated myself! I should wake up by reading the book of my life and tell the " +
                    "<red>Oneiromancer</red> what I have learnt.",
            ) {
                visibleWhen { stage == STAGE_DEFEATED_SELF }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Lokar Searunner took me to the Lady Zay, where I lifted a Moon Clan jinx the " +
                    "cabin boy had drawn around the ship so it could sail to Lunar Isle.",
            )
            line(
                "The Oneiromancer had me brew a waking sleep potion, enchant a Lunar staff and " +
                    "gather the Moon Clan's ceremonial clothing.",
            )
            line(
                "In the Dream World I learnt six lessons about myself and defeated my own " +
                    "reflection. The Moon Clan have agreed to talk with the Fremennik at last.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_lunardiplomacy"

        const val STAGE_STARTED = 10
        const val STAGE_GOT_SEAL = 20
        const val STAGE_SAILED_TO_COVE = 30
        const val STAGE_SAILED_IN_CIRCLE = 40
        const val STAGE_ASKED_NAVIGATOR = 45
        const val STAGE_BLAMED_NAVIGATOR = 50
        const val STAGE_LEARNT_OF_JINX = 60
        const val STAGE_ASKED_LOOKOUT = 70
        const val STAGE_ASKED_COOK = 80
        const val STAGE_ASKED_LEE = 90
        const val STAGE_ASKED_FIRST_MATE = 100
        const val STAGE_FOUND_CULPRIT = 110
        const val STAGE_JINX_LIFTED = 120
        const val STAGE_AT_LUNAR_ISLE = 125
        const val STAGE_ENTERED_TOWN = 130
        const val STAGE_MET_ONEIROMANCER = 135
        const val STAGE_POTION = 140
        const val STAGE_STAFF = 145
        const val STAGE_EQUIPMENT = 155
        const val STAGE_ENTER_DREAM = 160
        const val STAGE_IN_DREAM = 165
        const val STAGE_CHALLENGES = 170
        const val STAGE_FACE_SELF = 175
        const val STAGE_DEFEATED_SELF = 180

        const val SYMBOL_STAGE_STEP = 2

        const val REWARD_XP = 5000.0
        const val ASTRAL_REWARD = 50

        const val SEAL_OF_PASSAGE = "obj.lunar_seal_of_passage"
        const val LUNAR_STAFF = "obj.lunar_moonclan_liminal_staff"
        const val ASTRAL_RUNE = "obj.astralrune"

        val REQUIRED_QUESTS =
            listOf(
                "quest_fremenniktrials" to "The Fremennik Trials",
                "quest_lostcity" to "Lost City",
                "quest_runemysteries" to "Rune Mysteries",
                "quest_shilovillage" to "Shilo Village",
            )

        val REQUIRED_LEVELS =
            listOf(
                "stat.herblore" to 5,
                "stat.crafting" to 61,
                "stat.defence" to 40,
                "stat.firemaking" to 49,
                "stat.magic" to 65,
                "stat.mining" to 60,
                "stat.woodcutting" to 55,
            )

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.lunar_quest_symbolpres1",
                "varbit.lunar_quest_symbolpres2",
                "varbit.lunar_quest_symbolpres3",
                "varbit.lunar_quest_symbolpres4",
                "varbit.lunar_quest_symbolpres5",
                "varbit.lunar_pt2_oneiro_given_helm",
                "varbit.lunar_pt2_oneiro_given_cape",
                "varbit.lunar_pt2_oneiro_given_amulet",
                "varbit.lunar_pt2_oneiro_given_torso",
                "varbit.lunar_pt2_oneiro_given_gloves",
                "varbit.lunar_pt2_oneiro_given_boots",
                "varbit.lunar_pt2_oneiro_given_trousers",
                "varbit.lunar_pt2_oneiro_given_ring",
                "varbit.lunar_monk_cape_intro",
                "varbit.lunar_monk_ring_intro",
                "varbit.lunar_monk_amulet_intro",
                "varbit.lunar_monk_tanclothes_intro",
                "varbit.lunar_quest_dicepos6",
                "varbit.lunar_emote_prog",
                "varbit.lunar_num_prog",
                "varbit.lunar_tree_prog",
                "varbit.lunar_floor_prog",
                "varbit.lunar_skill_prog",
                "varbit.lunar_dice_prog",
                "varbit.lunar_dice_curnum",
                "varbit.lunar_floor_col_a",
                "varbit.lunar_floor_col_b",
                "varbit.lunar_floor_col_c",
                "varbit.lunar_floor_col_d",
                "varbit.lunar_num_intro",
                "varbit.lunar_num_curseq",
                "varbit.lunar_emote_loc",
                "varbit.lunar_pt3_num_seq_n",
                "varbit.lunar_pt3_tree_npc_col",
                "varbit.lunar_skill_intro",
                "varbit.lunar_logmulti_npc",
                "varbit.lunar_logmulti_pl",
                "varbit.lunar_logcol",
                "varbit.lunar_battle_pos",
                "varbit.lunar_spoken_centre",
                "varbit.lunar_tree_playing",
                "varbit.lunar_was_indream",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }

        fun symbolsRubbed(player: Player): Int = ShipSymbol.entries.count { it.isRubbed(player) }
    }
}
