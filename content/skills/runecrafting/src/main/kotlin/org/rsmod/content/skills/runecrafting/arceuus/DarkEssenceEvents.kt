package org.rsmod.content.skills.runecrafting.arceuus

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.runecrafting.action.RunecraftAction.advanceRunecraftingXp
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.CHISEL_CRAFTING_XP
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.CHISEL_LEVEL
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.DARK_BLOCK
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.DENSE_BLOCK
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.FRAGMENTS
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.MAX_FRAGMENTS
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.VENERATE_XP
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.chiselDarkBlock
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.venerateDenseBlocks
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Venerating dense essence at the Dark Altar and chiselling the dark blocks into fragments. */
class DarkEssenceEvents @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(DARK_ALTAR) { venerate() }
        onOpLocU(DARK_ALTAR, DENSE_BLOCK) { venerate() }
        onOpHeldU(CHISEL, DARK_BLOCK) { chiselBlocks() }
    }

    private suspend fun ProtectedAccess.venerate() {
        if (DENSE_BLOCK !in inv) {
            mes("You have no dense essence blocks to venerate.")
            return
        }
        anim(VENERATE_ANIM)
        delay(VENERATE_TICKS)
        val converted = player.venerateDenseBlocks()
        if (converted <= 0) {
            return
        }
        val drain = DarkEssence.prayerDrain(converted, player.prayerLvl)
        if (drain > 0) {
            statSub("stat.prayer", drain, 0)
        }
        advanceRunecraftingXp(converted * VENERATE_XP, xpMods)
        mes("You bind the temple's power into dark essence blocks.")
    }

    private suspend fun ProtectedAccess.chiselBlocks() {
        if (player.craftingLvl < CHISEL_LEVEL) {
            mes("You need a Crafting level of $CHISEL_LEVEL to chisel dark essence.")
            return
        }
        var first = true
        while (DARK_BLOCK in inv && CHISEL in inv) {
            if (DarkEssence.chiselableBlocks(inv.count(FRAGMENTS), 1) == 0) {
                mes("You can't carry more than $MAX_FRAGMENTS dark essence fragments.")
                return
            }
            anim(CHISEL_ANIM)
            delay(if (first) FIRST_CHISEL_TICKS else CHISEL_TICKS)
            first = false
            if (DARK_BLOCK !in inv || CHISEL !in inv) {
                return
            }
            if (!player.chiselDarkBlock()) {
                return
            }
            statAdvance("stat.crafting", CHISEL_CRAFTING_XP * xpMods.get(player, "stat.crafting"))
        }
    }

    private companion object {
        const val DARK_ALTAR = "loc.archeus_altar_dark"
        const val CHISEL = "obj.chisel"
        const val VENERATE_ANIM = "seq.human_pray"
        const val CHISEL_ANIM = "seq.arceuus_chisel_essenceblock"
        const val VENERATE_TICKS = 2
        const val FIRST_CHISEL_TICKS = 1
        const val CHISEL_TICKS = 3
    }
}
