package org.rsmod.content.other.achievementdiaries

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.StatType
import dev.openrune.types.enums.enum
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.table.AchievementDiaryTiersRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal fun lampStat(subcomponent: Int): String? =
    enum<Int, StatType>("fake_xp_drops").backing[subcomponent + 1]?.internalName

class DiaryLampScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in AchievementDiaryTiersRow.all().distinctBy { it.lamp.id }) {
            onOpHeld1(tier.lamp.internalName) { rubLamp(tier, it.slot) }
        }
    }

    private suspend fun ProtectedAccess.rubLamp(tier: AchievementDiaryTiersRow, slot: Int) {
        VarPlayerIntMapSetter.set(player, "varp.if1", tier.lampLevel)
        VarPlayerIntMapSetter.set(player, "varp.if2", ALL_SKILLS)
        ifOpenMainModal("interface.xpreward")
        ifSetText(
            "component.xpreward:title",
            "Choose the stat you wish to be advanced by ${"%,d".format(tier.lampXp)} experience!",
        )
        ifSetEvents("component.xpreward:universe", 0 until SKILL_BUTTONS, IfEvent.PauseButton)
        val input = pauseButton()
        val stat = lampStat(input.subcomponent) ?: return
        if (statBase(stat) < tier.lampLevel) {
            return
        }
        if (invDel(inv, tier.lamp.internalName, count = 1, slot = slot).failure) {
            return
        }
        statAdvance(stat, tier.lampXp.toDouble(), rate = 1.0, globalRate = 1.0)
        mes("Your wish has been granted!")
    }

    private companion object {
        const val ALL_SKILLS = -1
        const val SKILL_BUTTONS = 24
    }
}
