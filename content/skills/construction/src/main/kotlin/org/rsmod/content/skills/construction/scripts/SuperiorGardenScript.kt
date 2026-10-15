package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.Constants
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The superior garden's pools.
 *
 * Each pool restores what the one before it does and one thing more, as the wiki lists them:
 * special attack energy, run energy, prayer points, lowered stats, then hitpoints along with curing
 * poison and venom. Only lowered levels are raised, so a boost survives a drink. The teleport
 * space is [HouseTravelScript]'s and the fairy ring module's, and the topiary [TopiaryScript]'s.
 */
class SuperiorGardenScript : PluginScript() {
    private var Player.specialEnergy by intVarp("varp.sa_energy")

    override fun ScriptContext.startup() {
        for ((tier, pool) in POOLS.withIndex()) {
            onOpLoc1(pool) { drink(tier + 1) }
        }
    }

    private suspend fun ProtectedAccess.drink(tier: Int) {
        anim(DRINK_ANIM)
        delay(DRINK_TICKS)
        player.specialEnergy = Constants.sa_max_energy
        if (tier >= REVITALISATION) {
            player.runEnergy = Constants.run_max_energy
            UpdateRun.energy(player, player.runEnergy)
        }
        if (tier >= REJUVENATION) {
            raiseToBase(PRAYER)
        }
        if (tier >= FANCY) {
            for (stat in allStats().filter { it != HITPOINTS && it != PRAYER }) {
                raiseToBase(stat)
            }
        }
        if (tier >= ORNATE) {
            raiseToBase(HITPOINTS)
            player.cureAllToxins()
        }
        mes("You feel refreshed.")
    }

    private fun ProtectedAccess.raiseToBase(stat: String) {
        val missing = statBase(stat) - stat(stat)
        if (missing > 0) {
            statAdd(stat, missing, 0)
        }
    }

    private fun allStats(): List<String> =
        ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }

    private companion object {
        val POOLS =
            listOf("restoration", "revitalisation", "rejuvenation", "recovery", "regeneration")
                .map { "loc.poh_pool_$it" }

        const val REVITALISATION = 2
        const val REJUVENATION = 3
        const val FANCY = 4
        const val ORNATE = 5

        const val PRAYER = "stat.prayer"
        const val HITPOINTS = "stat.hitpoints"
        const val DRINK_ANIM = "seq.poh_pool_drink"
        const val DRINK_TICKS = 2
    }
}
