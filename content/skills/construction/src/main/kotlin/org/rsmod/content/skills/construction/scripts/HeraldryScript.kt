package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Heraldry
import org.rsmod.content.skills.construction.data.Heraldry.Product
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Painting the family crest onto helmets, kiteshields and banners at the heraldry stands. */
class HeraldryScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((stand, tier) in Heraldry.STANDS) {
            onOpLoc1(stand) { paint(tier) }
        }
    }

    private suspend fun ProtectedAccess.paint(tier: Int) {
        val crest = familyCrest()
        if (crest == null) {
            mes("You need a family crest to paint. Sir Renitee in Falador castle keeps the records.")
            return
        }
        val products = Heraldry.products(crest).filter { it.stand <= tier }
        val config =
            SkillMultiConfig(
                verb = "make",
                entries =
                    products.map { product ->
                        SkillMultiEntry(
                            product.output,
                            product.materials.map { (obj, count) -> Material(obj, count) },
                        )
                    },
            )
        openSkillMulti(config) { selection ->
            make(products.first { it.output == selection.entry.internal }, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.make(product: Product, amount: Int) {
        if (player.craftingLvl < product.level) {
            mes("You need a Crafting level of ${product.level} to make that.")
            return
        }
        repeat(amount) {
            if (product.materials.any { (obj, count) -> inv.count(obj) < count }) {
                return
            }
            anim(Construction.BUILD_ANIM)
            delay(PAINT_CYCLE)
            for ((obj, count) in product.materials) {
                if (invDel(inv, obj, count).failure) {
                    return
                }
            }
            invAdd(inv, product.output)
            statAdvance(CRAFTING, product.xp * xpMods.get(player, CRAFTING))
        }
    }

    private companion object {
        const val CRAFTING = "stat.crafting"
        const val PAINT_CYCLE = 6
    }
}
