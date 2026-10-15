package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.lunarBrazierLit by boolVarBit("varbit.lunar_brazier_lit")
internal var Player.lunarWasInDream by intVarBit("varbit.lunar_was_indream")
internal var Player.lunarSpokenCentre by boolVarBit("varbit.lunar_spoken_centre")
internal var Player.lunarCapeIntro by boolVarBit("varbit.lunar_monk_cape_intro")
internal var Player.lunarRingIntro by boolVarBit("varbit.lunar_monk_ring_intro")
internal var Player.lunarAmuletIntro by boolVarBit("varbit.lunar_monk_amulet_intro")
internal var Player.lunarClothesIntro by boolVarBit("varbit.lunar_monk_tanclothes_intro")

/** Rubbing out one of the cabin boy's symbols: 0 hidden, 1 lit up by the lantern, 2 rubbed away. */
enum class ShipSymbol(val loc: String, val varbit: String, val label: String) {
    Support("loc.quest_lunar_support_symbol_multi", "varbit.lunar_quest_symbolpres1", "support"),
    Cannon("loc.quest_lunar_cannon_symbol_multi", "varbit.lunar_quest_symbolpres2", "cannon"),
    Crate("loc.quest_lunar_sack_pile_symbol_multi", "varbit.lunar_quest_symbolpres3", "crate"),
    Wallchart("loc.quest_lunar_wallchart_symbol_multi", "varbit.lunar_quest_symbolpres4", "wallchart"),
    Chest("loc.quest_lunar_chest_symbol_multi", "varbit.lunar_quest_symbolpres5", "chest");

    fun state(player: Player): Int = player.vars[varbit]

    fun isRubbed(player: Player): Boolean = state(player) == RUBBED

    companion object {
        const val HIDDEN = 0
        const val REVEALED = 1
        const val RUBBED = 2
    }
}

/** A piece of the ceremonial clothing and the varbit recording that the Oneiromancer has it. */
enum class LunarPiece(val obj: String, val varbit: String, val label: String) {
    Helm("obj.lunar_helmet", "varbit.lunar_pt2_oneiro_given_helm", "helm"),
    Cape("obj.lunar_cape", "varbit.lunar_pt2_oneiro_given_cape", "cape"),
    Amulet("obj.lunar_amulet", "varbit.lunar_pt2_oneiro_given_amulet", "amulet"),
    Torso("obj.lunar_torso", "varbit.lunar_pt2_oneiro_given_torso", "torso"),
    Gloves("obj.lunar_gloves", "varbit.lunar_pt2_oneiro_given_gloves", "gloves"),
    Boots("obj.lunar_boots", "varbit.lunar_pt2_oneiro_given_boots", "boots"),
    Trousers("obj.lunar_legs", "varbit.lunar_pt2_oneiro_given_trousers", "legs"),
    Ring("obj.lunar_ring", "varbit.lunar_pt2_oneiro_given_ring", "ring");

    fun given(player: Player): Boolean = player.vars[varbit] == 1

    fun give(player: Player) {
        LunarDiplomacyQuest.setVarBit(player, varbit, 1)
    }
}

/** The six islands of the Dream World and the progress each needs, from the client's quest helper. */
enum class DreamChallenge(val title: String, val progressVarbit: String, val needed: Int) {
    Numbers("Communicating in numbers", "varbit.lunar_num_prog", 6),
    Mimic("Anything you can do...", "varbit.lunar_emote_prog", 5),
    Race("The race is on!", "varbit.lunar_skill_prog", 1),
    Trees("Chop, chop, chop away", "varbit.lunar_tree_prog", 1),
    Memory("Where am I?", "varbit.lunar_floor_prog", 1),
    Dice("A game of chance", "varbit.lunar_dice_prog", 5);

    fun progress(player: Player): Int = player.vars[progressVarbit]

    fun isComplete(player: Player): Boolean = progress(player) >= needed

    fun setProgress(player: Player, value: Int) {
        LunarDiplomacyQuest.setVarBit(player, progressVarbit, value)
    }

    companion object {
        fun allComplete(player: Player): Boolean = entries.all { it.isComplete(player) }
    }
}
