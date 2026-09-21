package org.rsmod.content.drops

import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarPiece
import org.rsmod.game.entity.Player

/**
 * A Suqah only drops Meteora's tiara once she has asked for it back, and only while the player
 * still needs it for the amulet trade.
 */
public fun Player.wantsLunarTiara(): Boolean {
    if (vars[LUNAR_STAGE] != LunarDiplomacyQuest.STAGE_EQUIPMENT || vars[AMULET_INTRO] != 1) {
        return false
    }
    val amulet = LunarPiece.Amulet
    return !amulet.given(this) && TIARA !in inv && amulet.obj !in inv && amulet.obj !in worn
}

private const val LUNAR_STAGE = "varbit.lunar_quest_main"
private const val AMULET_INTRO = "varbit.lunar_monk_amulet_intro"
private const val TIARA = "obj.lunar_tiara"
