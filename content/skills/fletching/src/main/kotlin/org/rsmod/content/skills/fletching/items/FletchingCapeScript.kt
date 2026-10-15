package org.rsmod.content.skills.fletching.items

import java.time.LocalDate
import java.time.ZoneOffset
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseFletchingLvl
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Fletching cape boosts Fletching by one and can be searched three times a day (resetting at
 * 00:00 UTC) for a mith grapple and a crossbow. `varp.fletching_cape_searches` packs the UTC day
 * into bits 0-15 and the searches used that day above it.
 */
class FletchingCapeScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (cape in CAPES) {
            onOpHeld3(cape) { search() }
            onOpWorn3(cape) { search() }
            onOpWorn2(cape) { boost() }
        }
    }

    private fun ProtectedAccess.boost() {
        if (!isMasterFletcher()) {
            return
        }
        statBoost(STAT, constant = 1, percent = 0)
    }

    private fun ProtectedAccess.search() {
        if (!isMasterFletcher()) {
            return
        }
        val used = searchesToday()
        if (used >= DAILY_SEARCHES) {
            mes("You have already searched your cape $DAILY_SEARCHES times today.")
            return
        }
        if (inv.freeSpace() < REWARDS.size) {
            mes("You need ${REWARDS.size} free inventory spaces to search the cape.")
            return
        }
        REWARDS.forEach { invAdd(inv, it, 1) }
        vars[SEARCH_VARP] = today() or ((used + 1) shl DAY_BITS)
        val left = DAILY_SEARCHES - used - 1
        mes("You find a mith grapple and a crossbow in your cape.")
        when (left) {
            0 -> mes("You cannot search it again today.")
            1 -> mes("You can search it 1 more time today.")
            else -> mes("You can search it $left more times today.")
        }
    }

    private fun ProtectedAccess.searchesToday(): Int {
        val packed = vars[SEARCH_VARP]
        if ((packed and DAY_MASK) != today()) {
            return 0
        }
        return packed ushr DAY_BITS
    }

    private fun ProtectedAccess.isMasterFletcher(): Boolean {
        if (player.baseFletchingLvl >= MAX_LEVEL) {
            return true
        }
        mes("You need to have a Fletching level of $MAX_LEVEL.")
        return false
    }

    private fun today(): Int = (LocalDate.now(ZoneOffset.UTC).toEpochDay() and DAY_MASK.toLong()).toInt()

    private companion object {
        private val CAPES = listOf("obj.skillcape_fletching", "obj.skillcape_fletching_trimmed")
        private val REWARDS = listOf("obj.xbows_grapple_tip_bolt_mithril_rope", "obj.crossbow")

        private const val STAT = "stat.fletching"
        private const val SEARCH_VARP = "varp.fletching_cape_searches"
        private const val DAILY_SEARCHES = 3
        private const val MAX_LEVEL = 99

        private const val DAY_BITS = 16
        private const val DAY_MASK = 0xFFFF
    }
}
