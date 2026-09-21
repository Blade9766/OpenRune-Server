package org.rsmod.content.quest.area.varrock.familycrest

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Family Crest.
 *
 * The stage is `varp.crestquest`, endstate 11 from `dbrow.quest_familycrest`. Each of the three
 * sons is a multinpc indexing that varp directly: Avan is an anonymous "Man" until the stage
 * reaches [STAGE_AVAN_FOUND], and all three gain their "Gauntlets" op at [STAGE_COMPLETE].
 *
 * The three errands can be done in any order, so the errands themselves are tracked on
 * `varp.famcrest_state` and the stage is recomputed from them by [syncStage], which only ever
 * moves forward. The stage numbers below are therefore the order the quest is *usually* done in,
 * not an order the player is held to - the only value the cache pins is [STAGE_AVAN_FOUND].
 */
@Singleton
class FamilyCrestQuest :
    QuestScript(
        QUEST_KEY,
        "varp.crestquest",
        rewards {
            item(STEEL_GAUNTLETS)
            extra("Gauntlets the Fitzharmon brothers will enchant")
        },
        ItemRewardDisplay(STEEL_GAUNTLETS, zoom = 110),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearSubState)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** True while Avan is still waiting for his jewellery, which is Boot's cue to talk gold. */
    fun needsPerfectGold(player: Player): Boolean =
        isStarted(player) && player.avanAsked && !player.avanDone

    /** Boot has named the seam under Witchaven. */
    fun learnedGoldSource(access: ProtectedAccess) {
        access.player.bootTold = true
        syncStage(access)
    }

    fun start(access: ProtectedAccess) {
        advanceTo(access, STAGE_STARTED)
    }

    /**
     * Recomputes the stage from the errand flags and moves it forward if it has grown. Called
     * after every flag change; the errands can be finished in any order, so the stage is always
     * the furthest point reached and never drops back.
     */
    fun syncStage(access: ProtectedAccess) {
        val player = access.player
        if (stage(player) !in STAGE_STARTED until STAGE_COMPLETE) {
            return
        }
        var target = STAGE_STARTED
        if (player.calebAsked) target = maxOf(target, STAGE_CALEB_ASKED)
        if (player.calebDone) target = maxOf(target, STAGE_CALEB_DONE)
        if (player.knowsBrothers) target = maxOf(target, STAGE_KNOWS_BROTHERS)
        if (player.johnathonCured) target = maxOf(target, STAGE_JOHNATHON_CURED)
        if (player.avanFound) target = maxOf(target, STAGE_AVAN_FOUND)
        if (player.avanAsked) target = maxOf(target, STAGE_AVAN_ASKED)
        if (player.bootTold) target = maxOf(target, STAGE_BOOT_TOLD)
        if (player.avanDone) target = maxOf(target, STAGE_AVAN_DONE)
        if (player.avanDone && player.calebDone && player.johnathonDone) {
            target = maxOf(target, STAGE_ALL_PARTS)
        }
        advanceTo(access, target)
    }

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

    /** Clears every errand flag when the quest is reset to not started. */
    private fun clearSubState(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            if (player.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(player, varbit, 0)
            }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Dimintheis</col> in the fenced house in south-east " +
            "<col=800000>Varrock</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val hero = player.player
            objective(
                "<red>Dimintheis Fitzharmon</red> has lost his family crest. His three sons " +
                    "split it between them and scattered, and he wants all three pieces back.",
            ) {
                visibleWhen { stage(hero) >= STAGE_STARTED }
            }
            objective(
                "<red>Caleb</red>, the eldest, is a chef in <red>Catherby</red>. He will hand " +
                    "over his piece for a <red>cooked shrimps</red>, <red>salmon</red>, " +
                    "<red>tuna</red>, <red>bass</red> and <red>swordfish</red> for his salad.",
            ) {
                visibleWhen { stage(hero) >= STAGE_STARTED }
                custom(hero.calebAsked, "Caleb has told me what he wants.").strike()
                custom(hero.calebDone, "Caleb gave me his part of the crest.").strike()
            }
            objective(
                "I should ask Caleb what became of the rest of the crest before I go looking " +
                    "for his brothers.",
            ) {
                visibleWhen { stage(hero) >= STAGE_STARTED && !hero.knowsBrothers }
            }
            objective(
                "<red>Avan</red> is somewhere around the <red>Al Kharid mine</red>. The " +
                    "<red>gem trader</red> in the town should know which of them he is.",
            ) {
                visibleWhen { hero.knowsBrothers }
                custom(hero.avanFound, "The gem trader pointed Avan out to me.").strike()
                custom(
                    hero.avanAsked,
                    "Avan wants a <red>'perfect' ring</red> and a <red>'perfect' necklace</red>, " +
                        "both set with <red>rubies</red>.",
                )
                custom(
                    hero.bootTold,
                    "<red>Boot</red> in the <red>Dwarven Mine</red> says the only 'perfect' gold " +
                        "left lies in the dungeon under <red>Witchaven</red>, behind a lever " +
                        "puzzle and a pack of hellhounds.",
                )
                custom(hero.avanDone, "Avan gave me his part of the crest.").strike()
            }
            objective(
                "<red>Johnathon</red> drinks in the <red>Jolly Boar Inn</red>, north-east of " +
                    "Varrock. He has been bitten by a poison spider and needs an " +
                    "<red>antipoison</red> before he will talk.",
            ) {
                visibleWhen { hero.knowsBrothers }
                custom(hero.johnathonCured, "I cured Johnathon's poisoning.").strike()
                custom(
                    hero.johnathonAsked,
                    "The demon <red>Chronozon</red> in <red>Edgeville Dungeon</red> took " +
                        "Johnathon's piece. It can only be killed once all four " +
                        "<red>blast</red> spells have struck it.",
                )
                custom(hero.johnathonDone, "I took Johnathon's part from Chronozon.").strike()
            }
            objective(
                "I have all three pieces. Putting them together should make the family crest.",
            ) {
                visibleWhen {
                    stage(hero) >= STAGE_ALL_PARTS && !player.inv.contains(FAMILY_CREST)
                }
            }
            objective("I should take the <red>family crest</red> back to Dimintheis.") {
                visibleWhen { player.inv.contains(FAMILY_CREST) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Dimintheis Fitzharmon's three sons had split the family crest between them " +
                    "and gone their separate ways.",
            )
            line(
                "I cooked Caleb his salad in Catherby, mined 'perfect' gold under Witchaven for " +
                    "Avan's jewellery, and beat Johnathon's piece out of the demon Chronozon.",
            )
            line(
                "The crest is whole again, and Dimintheis rewarded me with a pair of gauntlets " +
                    "any of his sons will enchant for me.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_familycrest"

        const val STAGE_STARTED = 1
        const val STAGE_CALEB_ASKED = 2
        const val STAGE_CALEB_DONE = 3
        const val STAGE_KNOWS_BROTHERS = 4
        const val STAGE_JOHNATHON_CURED = 5

        /** Pinned by the cache: the `npc.avan` multinpc stops being an anonymous "Man" here. */
        const val STAGE_AVAN_FOUND = 6

        const val STAGE_AVAN_ASKED = 7
        const val STAGE_BOOT_TOLD = 8
        const val STAGE_AVAN_DONE = 9
        const val STAGE_ALL_PARTS = 10

        /** Pinned by the cache: all three sons gain their "Gauntlets" op here. */
        const val STAGE_COMPLETE = 11

        const val MINING_REQ = 40
        const val SMITHING_REQ = 40
        const val CRAFTING_REQ = 40
        const val MAGIC_REQ = 59
        const val RECOMMENDED_COMBAT = 55

        const val DIMINTHEIS = "npc.dimintheis"
        const val CALEB = "npc.caleb_fitzharmon"
        const val AVAN = "npc.avan"
        const val JOHNATHON = "npc.johnathon_fitzharmon"
        const val GEM_TRADER = "npc.gem_trader"
        const val CHRONOZON = "npc.chronozon"

        const val CALEB_CREST = "obj.caleb_crest"
        const val AVAN_CREST = "obj.avan_crest"
        const val JOHNATHON_CREST = "obj.johnathon_crest"
        const val FAMILY_CREST = "obj.family_crest"
        const val STEEL_GAUNTLETS = "obj.steel_gauntlets"
        const val COOKING_GAUNTLETS = "obj.gauntlets_of_cooking"
        const val GOLDSMITH_GAUNTLETS = "obj.gauntlets_of_goldsmithing"
        const val CHAOS_GAUNTLETS = "obj.gauntlets_of_chaos"

        const val PERFECT_GOLD_ORE = "obj.perfect_gold_ore"
        const val PERFECT_GOLD_BAR = "obj.perfect_gold_bar"
        const val PERFECT_RING = "obj.perfect_ruby_ring"
        const val PERFECT_NECKLACE = "obj.perfect_ruby_necklace"
        const val COINS = "obj.coins"

        /** Caleb's salad, in the order he lists it. */
        val SALAD_FISH =
            listOf("obj.shrimp", "obj.salmon", "obj.tuna", "obj.bass", "obj.swordfish")

        const val PERFECT_GOLD_NEEDED = 2

        /** What each brother charges to re-enchant the gauntlets after the first, free change. */
        const val ENCHANT_COST = 25_000

        const val ENCHANT_SOUND = "synth.enchant_diamond_ring"
        const val ENCHANT_SPOTANIM = "spotanim.enchant_ring"
        const val ASSEMBLE_SOUND = "synth.hammer_and_build"

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.famcrest_caleb_asked",
                "varbit.famcrest_caleb_done",
                "varbit.famcrest_knows_brothers",
                "varbit.famcrest_avan_found",
                "varbit.famcrest_avan_asked",
                "varbit.famcrest_boot_told",
                "varbit.famcrest_avan_done",
                "varbit.famcrest_john_cured",
                "varbit.famcrest_john_asked",
                "varbit.famcrest_john_done",
                "varbit.famcrest_gold_mined",
                "varbit.famcrest_puzzle_solved",
                "varbit.famcrest_gauntlets_free",
                "varbit.famcrest_gauntlets_kind",
                "varbit.famcrest_chronozon_weaken",
            )
    }
}
