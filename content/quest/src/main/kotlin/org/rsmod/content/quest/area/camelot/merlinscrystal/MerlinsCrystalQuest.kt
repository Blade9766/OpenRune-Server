package org.rsmod.content.quest.area.camelot.merlinscrystal

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Merlin's Crystal.
 *
 * The stage is the whole of `varp.arthur` (14), endstate 7 from `dbrow.quest_merlinscrystal`. No
 * cache varbit sits on that varp, so everything the quest remembers besides the stage lives on
 * its own server varbits (see `MerlinsCrystalVars`).
 * - [STAGE_STARTED]: King Arthur has asked for Merlin to be freed.
 * - [STAGE_SPOKEN_GAWAIN]: Gawain has named Morgan Le Faye and her stronghold.
 * - [STAGE_SPOKEN_LANCELOT]: Lancelot has named the sea entrance, which opens the Catherby crate.
 * - [STAGE_SPOKEN_MORGAN]: Morgan has bought her son's life with the ritual, so the black candle,
 *   the chaos altar and Excalibur are all in play.
 * - [STAGE_SPIRIT_BOUND]: Thrantax has been bound and has lifted the crystal's protection.
 * - [STAGE_FREED_MERLIN]: the crystal is shattered and Merlin is loose.
 */
@Singleton
class MerlinsCrystalQuest : QuestScript(
    QUEST_KEY,
    "varp.arthur",
    rewards {
        extra("Excalibur")
        scroll("Excalibur", "Honorary Knight of the Round Table")
    },
    ItemRewardDisplay(EXCALIBUR, zoom = 155),
    completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

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

    /** Clears every sub-state varbit when the quest is reset to not started. */
    fun syncVars(player: Player) {
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
        "talking to <col=800000>King Arthur</col> in <col=800000>Camelot</col>, north-west of " +
            "Seers' Village."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            objective(
                "<red>King Arthur</red> will only make me a Knight of the Round Table once I " +
                    "have freed <red>Merlin</red>, who is sealed inside a giant crystal in the " +
                    "south-east tower of Camelot.",
            ) {
                visibleWhen { stage >= STAGE_STARTED }
            }
            objective("None of the knights can break the crystal. I should ask them what they know.") {
                visibleWhen { stage == STAGE_STARTED }
            }
            objective(
                "<red>Sir Gawain</red> blames <red>Morgan Le Faye</red>, who holds a stronghold " +
                    "south of Camelot guarded by renegade knights under <red>Sir Mordred</red>.",
            ) {
                visibleWhen { stage >= STAGE_SPOKEN_GAWAIN }
            }
            objective(
                "<red>Sir Lancelot</red> says the keep can only be entered from the sea. Its " +
                    "deliveries are shipped from the <red>crates</red> on the Catherby dock.",
            ) {
                visibleWhen { stage >= STAGE_SPOKEN_LANCELOT }
            }
            objective("I should hide in a crate on the <red>Catherby</red> dock and let it be shipped over.") {
                visibleWhen { stage == STAGE_SPOKEN_LANCELOT }
            }
            objective(
                "Morgan Le Faye bought her son's life by telling me how to free Merlin: drop " +
                    "<red>bat bones</red> on the <red>magical symbol</red> in Camelot's garden " +
                    "while holding a <red>lit black candle</red>, bind the spirit that comes " +
                    "with the magic words, then shatter the crystal with <red>Excalibur</red>.",
            ) {
                visibleWhen { stage in STAGE_SPOKEN_MORGAN until STAGE_SPIRIT_BOUND }
                custom(
                    access.player.merlinKnowsWords,
                    "I found the words of binding on a chaos altar: 'Snarthon Candtrick Termanto'.",
                ).strike()
                hasItem("unlit_black_candle", "The Catherby candle maker made me a black candle.").strike()
                hasItem("lit_black_candle", "My black candle is lit.").strike()
                hasItem("bat_bones", "I have some bat bones.").strike()
                hasItem("excalibur", "The Lady of the Lake has given me Excalibur.").strike()
            }
            objective(
                "<red>Thrantax the Mighty</red> has lifted the dark force protecting the " +
                    "crystal. I should climb the south-east tower of Camelot and shatter it with " +
                    "<red>Excalibur</red>.",
            ) {
                visibleWhen { stage == STAGE_SPIRIT_BOUND }
            }
            objective("Merlin is free. I should go and tell <red>King Arthur</red>.") {
                visibleWhen { stage == STAGE_FREED_MERLIN }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Arthur's court could not free Merlin from the crystal Morgan Le Faye had " +
                    "sealed him in, so I shipped myself to her keep in a crate and spared Sir " +
                    "Mordred in exchange for her secret.",
            )
            line(
                "I gathered bat bones, a black candle and the words of binding from a chaos " +
                    "altar, and earned Excalibur from the Lady of the Lake by giving bread to a " +
                    "beggar.",
            )
            line(
                "Thrantax the Mighty lifted Morgan's protection, Excalibur shattered the " +
                    "crystal, and King Arthur made me an honorary Knight of the Round Table.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_merlinscrystal"

        const val STAGE_STARTED = 1
        const val STAGE_SPOKEN_GAWAIN = 2
        const val STAGE_SPOKEN_LANCELOT = 3
        const val STAGE_SPOKEN_MORGAN = 4
        const val STAGE_SPIRIT_BOUND = 5
        const val STAGE_FREED_MERLIN = 6

        /** Values of `varbit.merlin_excalibur_test`. */
        const val TEST_NOT_SET = 0
        const val TEST_SET = 1
        const val TEST_MET_BEGGAR = 2
        const val TEST_REWARDED = 3

        const val KING_ARTHUR = "npc.king_arthur"
        const val SIR_GAWAIN = "npc.sir_gawain"
        const val SIR_LANCELOT = "npc.sir_lancelot"
        const val SIR_MORDRED = "npc.sir_mordred"
        const val MORGAN_LE_FAYE = "npc.morgan_le_faye"
        const val MERLIN = "npc.merlin"
        const val THRANTAX = "npc.thrantax"
        const val LADY_OF_THE_LAKE = "npc.ladyofthelake"
        const val BEGGAR = "npc.lake_beggar"
        const val CANDLE_MAKER = "npc.candle_maker"
        const val RENEGADE_KNIGHT = "npc.renegade_knight"

        const val CRYSTAL = "loc.merlins_crystal"
        const val MAGIC_SYMBOL = "loc.merlin_star"
        const val BEEHIVE = "loc.merlin_beehive"
        const val CHAOS_ALTAR = "loc.thrantaxaltar"
        const val CATHERBY_CRATE = "loc.merlincrate_empty"
        const val KEEP_CRATE = "loc.merlincrate_empty2"
        const val CRATE_WALL = "loc.merlincrate_wall"
        const val BUCKET_CRATE = "loc.merlincrate_bucket"
        val KEEP_FRONT_DOORS = listOf("loc.lefayeunopenabledoorl", "loc.lefayeunopenabledoorr")

        const val EXCALIBUR = "obj.excalibur"
        const val BAT_BONES = "obj.bat_bones"
        const val BLACK_CANDLE = "obj.unlit_black_candle"
        const val LIT_BLACK_CANDLE = "obj.lit_black_candle"
        const val CANDLE = "obj.unlit_candle"
        const val LIT_CANDLE = "obj.lit_candle"
        const val BUCKET = "obj.bucket_empty"
        const val BUCKET_OF_WAX = "obj.bucket_wax"
        const val INSECT_REPELLENT = "obj.insect_repellent"
        const val TINDERBOX = "obj.tinderbox"
        const val BREAD = "obj.bread"

        const val WORDS_OF_BINDING = "Snarthon Candtrick Termanto"

        const val SMOKE_PUFF_SPOTANIM = "spotanim.smokepuff"
        const val SMOKE_PUFF_SOUND = "synth.smokepuff"
        const val SUMMON_SOUND = "synth.summon_npc"
        const val GLASS_BREAK_SOUND = "synth.glass_break"
        const val BEES_SOUND = "synth.bees_1"
        const val LIGHT_CANDLE_SOUND = "synth.light_candle"
        const val DOOR_OPEN_SOUND = "synth.door_open"
        const val PUT_DOWN_SOUND = "synth.put_down"

        const val PICK_UP_SEQ = "seq.human_pickupfloor"

        /** Beside the crates on the Catherby dock, where the return trip puts the player down. */
        val CATHERBY_ARRIVAL = CoordGrid(2802, 3441, 0)

        /** In Keep Le Faye's first-floor store room, next to the crate that carried the player. */
        val KEEP_ARRIVAL = CoordGrid(2778, 3401, 1)

        /** Inside the crate walls on the shipping map, where the voyage is played out. */
        val CRATE_INTERIOR = CoordGrid(2777, 9849, 0)

        /** The magical symbol just inside the fence at the north-east of Camelot's garden. */
        val SYMBOL_COORDS = CoordGrid(2780, 3515, 0)

        /** Two tiles west of the symbol: where the summoning backs the player off to. */
        val SUMMON_STEP_BACK = CoordGrid(2778, 3515, 0)

        /** Beside Sir Mordred on the keep's top floor, where Morgan Le Faye appears. */
        val MORGAN_COORDS = CoordGrid(2770, 3403, 2)

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.merlin_knows_words",
                "varbit.merlin_candle_promised",
                "varbit.merlin_excalibur_test",
                "varbit.merlin_beehive_free",
                "varbit.merlin_crate_dest",
                "varbit.merlin_in_crate",
            )
    }
}
