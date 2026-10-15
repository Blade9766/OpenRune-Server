package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.PLANK
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STEEL_BAR
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SWAMP_PASTE

/**
 * The town's carpentry: look at the damage, agree to fix it, and - only if every material and a
 * hammer are carried - the materials go and the repair's varbit is set in the same tick, with no
 * suspension in between, so nothing is taken for a repair that does not happen.
 */
@Singleton
class Repairs {

    data class Cost(val planks: Int = 0, val nails: Int = 0, val swampPaste: Int = 0, val steelBars: Int = 0)

    suspend fun ProtectedAccess.repair(
        describe: String,
        question: String,
        allowed: Boolean,
        cost: Cost,
        varbit: String,
        done: String,
        value: Int = 1,
    ): Boolean {
        mesbox(describe)
        if (!allowed) {
            return false
        }
        var agreed = false
        startDialogue { agreed = choice2("Yes.", true, "No.", false, title = question) }
        if (!agreed) {
            return false
        }
        if (!hasMaterials(cost)) {
            missing(cost)
            return false
        }
        if (cost.planks > 0) invDel(inv, PLANK, cost.planks)
        if (cost.nails > 0) takeNails(cost.nails)
        if (cost.swampPaste > 0) invDel(inv, SWAMP_PASTE, cost.swampPaste)
        if (cost.steelBars > 0) invDel(inv, STEEL_BAR, cost.steelBars)
        VarPlayerIntMapSetter.set(player, varbit, value)
        anim(HAMMER_SEQ)
        soundSynth(HAMMER_SOUND)
        val icon = if (cost.steelBars > 0) STEEL_BAR else PLANK
        val second = if (cost.swampPaste > 0) SWAMP_PASTE else NAILS_ICON
        if (cost.steelBars > 0) objbox(icon, done) else doubleobjbox(second, icon, done)
        return true
    }

    fun ProtectedAccess.hasMaterials(cost: Cost): Boolean =
        hasHammer() &&
            inv.count(PLANK) >= cost.planks &&
            nailCount(inv) >= cost.nails &&
            inv.count(SWAMP_PASTE) >= cost.swampPaste &&
            inv.count(STEEL_BAR) >= cost.steelBars

    private suspend fun ProtectedAccess.missing(cost: Cost) {
        if (cost.steelBars > 0) {
            objbox(STEEL_BAR, "You do not have the required items to do that. You need a hammer and ${cost.steelBars} steel bars.")
            return
        }
        val paste = if (cost.swampPaste > 0) "${cost.swampPaste} swamp paste, " else ""
        doubleobjbox(
            PLANK,
            NAILS_ICON,
            "You do not have the required items to do that. You need a hammer, $paste${cost.planks} basic planks and ${cost.nails} nails of any kind.",
        )
    }

    private companion object {
        const val NAILS_ICON = "obj.nails"
        const val HAMMER_SEQ = "seq.human_hammer_hit"
        const val HAMMER_SOUND = "synth.hammer_and_build"
    }
}
