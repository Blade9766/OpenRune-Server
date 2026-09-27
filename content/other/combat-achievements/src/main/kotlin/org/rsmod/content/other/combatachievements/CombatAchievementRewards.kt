package org.rsmod.content.other.combatachievements

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.enums.enum
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.combatachievements.CombatAchievementTier
import org.rsmod.api.combatachievements.CombatAchievements
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.utils.time.runeday
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The antique lamps and Ghommal's hilts handed out for each Combat Achievement tier. */
class CombatAchievementRewards
@Inject
constructor(private val achievements: CombatAchievements, private val areas: AreaChecker) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (tier in CombatAchievementTier.entries) {
            onOpHeld1(tier.lamp) { rubLamp(tier, it.slot) }
            onOpHeld3(tier.hilt) { teleport(tier, Destination.Trollheim) }
            onOpWorn2(tier.hilt) { teleport(tier, Destination.Trollheim) }
            if (tier.level >= CombatAchievementTier.Elite.level) {
                onOpHeld4(tier.hilt) { teleport(tier, Destination.MorUlRek) }
                onOpWorn3(tier.hilt) { teleport(tier, Destination.MorUlRek) }
            }
        }
    }

    private suspend fun ProtectedAccess.rubLamp(tier: CombatAchievementTier, slot: Int) {
        VarPlayerIntMapSetter.set(player, "varp.if1", tier.lampMinLevel)
        VarPlayerIntMapSetter.set(player, "varp.if2", ALL_SKILLS)
        ifOpenMainModal("interface.xpreward")
        ifSetText(
            "component.xpreward:title",
            "Choose the stat you wish to be advanced by ${"%,d".format(tier.lampXp)} experience!",
        )
        ifSetEvents("component.xpreward:universe", 0 until SKILL_BUTTONS, IfEvent.PauseButton)
        val input = pauseButton()
        val statId = enum<Int, Int>(LAMP_SKILL_ENUM).backing[input.subcomponent + 1] ?: return
        val stat = RSCM.getReverseMapping(RSCMType.STAT, statId)
        if (statBase(stat) < tier.lampMinLevel) {
            return
        }
        if (invDel(inv, tier.lamp, count = 1, slot = slot).failure) {
            return
        }
        statAdvance(stat, tier.lampXp.toDouble(), rate = 1.0, globalRate = 1.0)
        mes("Your wish has been granted!")
    }

    private suspend fun ProtectedAccess.teleport(hilt: CombatAchievementTier, dest: Destination) {
        val tier = CombatAchievementTier.entries.lastOrNull { achievements.isUnlocked(player, it) }
        if (tier == null || tier.level < dest.minTier.level) {
            mes("You need to reach the ${dest.minTier.label} tier of Combat Achievements to use this teleport.")
            return
        }
        if (dest == Destination.MorUlRek && player.vars["varp.total_jad_kills"] == 0) {
            mes("You need to have obtained a fire cape to teleport to Mor Ul Rek.")
            return
        }
        if (player.coords.wildernessLevel(areas) > MAX_WILDERNESS_LEVEL) {
            mes("A mysterious force prevents you from teleporting.")
            return
        }
        resetDailyCounts()
        val limit = dest.dailyLimit(tier)
        val used = player.vars[dest.countVarbit]
        if (limit != null && used >= limit) {
            mes("You have used all of your ${dest.label} teleports for today.")
            return
        }
        val n = hilt.level
        val red = if (dest == Destination.MorUlRek) "_red" else ""
        anim("seq.combat_achievements_teleport_player_tier_$n")
        spotanim("spotanim.combat_achievements_teleport_spotanim_tier_$n$red")
        delay(TELEPORT_TICKS)
        telejump(dest.coords)
        anim("seq.combat_achievements_teleport_reappear_player_tier_$n")
        spotanim("spotanim.combat_achievements_teleport_reappear_spotanim_tier_$n$red")
        if (limit != null) {
            VarPlayerIntMapSetter.set(player, dest.countVarbit, used + 1)
            val left = limit - used - 1
            mes("You have $left ${dest.label} teleport${if (left == 1) "" else "s"} left today.")
        }
    }

    private fun ProtectedAccess.resetDailyCounts() {
        val today = runeday()
        if (player.vars[DAY_VARP] == today) {
            return
        }
        VarPlayerIntMapSetter.set(player, DAY_VARP, today)
        for (dest in Destination.entries) {
            VarPlayerIntMapSetter.set(player, dest.countVarbit, 0)
        }
    }

    private enum class Destination(
        val label: String,
        val coords: CoordGrid,
        val countVarbit: String,
        val minTier: CombatAchievementTier,
    ) {
        Trollheim(
            "Trollheim",
            CoordGrid(2898, 3713),
            "varbit.ca_teleport_count_trollheim",
            CombatAchievementTier.Easy,
        ) {
            override fun dailyLimit(tier: CombatAchievementTier): Int? =
                when (tier) {
                    CombatAchievementTier.Easy -> 3
                    CombatAchievementTier.Medium -> 5
                    else -> null
                }
        },
        MorUlRek(
            "Mor Ul Rek",
            CoordGrid(2546, 5135),
            "varbit.ca_teleport_count_morulrek",
            CombatAchievementTier.Elite,
        ) {
            override fun dailyLimit(tier: CombatAchievementTier): Int? =
                when (tier) {
                    CombatAchievementTier.Elite -> 3
                    CombatAchievementTier.Master -> 5
                    else -> null
                }
        };

        abstract fun dailyLimit(tier: CombatAchievementTier): Int?
    }

    private companion object {
        const val DAY_VARP = "varp.ca_teleport_day"
        const val ALL_SKILLS = -1
        const val SKILL_BUTTONS = 24
        const val LAMP_SKILL_ENUM = 681
        const val TELEPORT_TICKS = 5
        const val MAX_WILDERNESS_LEVEL = 20
    }
}
