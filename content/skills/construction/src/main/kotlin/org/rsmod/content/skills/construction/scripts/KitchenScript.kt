package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.construction.data.Kitchen
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The kitchen: shelves and the larder hand out their tier's stock, a barrel fills an empty beer
 * glass with its house drink, and tea is brewed by hand - the kettle filled at the sink and boiled
 * on an oven, leaves put in the teapot, the hot water poured on for a pot of four cups, then poured
 * out a cup at a time and milked to taste.
 */
class KitchenScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((larder, stock) in Kitchen.LARDERS) {
            onOpLoc1(larder) { take(stock) }
        }
        for (sink in Kitchen.SINKS) {
            onOpLocU(sink, Kitchen.KETTLE_EMPTY) { fillKettle() }
        }
        for (oven in Kitchen.OVENS) {
            onOpLocU(oven, Kitchen.KETTLE_WATER) { boilKettle() }
        }
        for (firepit in Kitchen.FIREPITS) {
            onOpLocU(firepit, Kitchen.KETTLE_WATER) { mes("You need an oven or a range to boil the kettle.") }
        }
        for (pot in Kitchen.Cup.entries) {
            onOpHeldU(Kitchen.TEA_LEAVES, pot.teapot) { addLeaves(pot) }
            onOpHeldU(Kitchen.KETTLE_BOILED, pot.teapotWithLeaves) { brew(pot) }
            for (servings in 1..Kitchen.SERVINGS) {
                for (cup in Kitchen.Cup.entries) {
                    onOpHeldU(pot.teapot(servings), cup.empty) { pour(pot, servings, cup) }
                }
            }
        }
        for (cup in Kitchen.Cup.entries) {
            onOpHeldU(Kitchen.MILK, cup.tea) { addMilk(cup) }
        }
        for ((shelves, index) in Kitchen.SHELVES) {
            onOpLoc1(shelves) { search(index) }
        }
        for ((barrel, drink) in Kitchen.BARRELS.zip(Kitchen.BARREL_DRINKS)) {
            for (glass in Kitchen.FILLABLE_GLASSES) {
                onOpLocU(barrel, glass) { fill(glass, drink) }
            }
        }
    }

    private suspend fun ProtectedAccess.search(index: Int) {
        take(Kitchen.shelfItems(index))
    }

    private suspend fun ProtectedAccess.take(stock: List<String>) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        val config =
            SkillMultiConfig(
                actionType = SkillingActionType.TAKE,
                verb = "take",
                entries = stock.map { SkillMultiEntry(it) },
                maxCountProvider = { inv, _ -> inv.freeSpace() },
            )
        openSkillMulti(config) { selection ->
            invAdd(inv, selection.entry.internal, selection.amount.coerceAtMost(inv.freeSpace()))
        }
    }

    /**
     * Fills [glass] from a barrel. A house glass takes the house drink; the player's own glass
     * takes the ordinary one it copies, so their glass isn't lost with the house's when they leave.
     */
    private fun ProtectedAccess.fill(glass: String, drink: String) {
        if (invDel(inv, glass).failure) {
            return
        }
        invAdd(inv, if (glass == Kitchen.BEER_GLASS) drink else Kitchen.ordinaryDrink(drink))
        anim(FILL_ANIM)
        mes("You fill the glass from the barrel.")
    }

    // ---------------------------------------------------------------------------- tea

    private fun ProtectedAccess.fillKettle() {
        if (invReplace(inv, Kitchen.KETTLE_EMPTY, 1, Kitchen.KETTLE_WATER).failure) {
            return
        }
        anim(FILL_ANIM)
        mes("You fill the kettle from the sink.")
    }

    private suspend fun ProtectedAccess.boilKettle() {
        anim(COOK_ANIM)
        mes("You put the kettle on the stove.")
        delay(BOIL_TICKS)
        if (invReplace(inv, Kitchen.KETTLE_WATER, 1, Kitchen.KETTLE_BOILED).success) {
            mes("The kettle boils.")
        }
    }

    private fun ProtectedAccess.addLeaves(pot: Kitchen.Cup) {
        if (invDel(inv, Kitchen.TEA_LEAVES).failure) {
            return
        }
        invReplace(inv, pot.teapot, 1, pot.teapotWithLeaves)
        mes("You put the tea leaves in the teapot.")
    }

    private fun ProtectedAccess.brew(pot: Kitchen.Cup) {
        if (player.cookingLvl < Kitchen.BREWING_LEVEL) {
            mes("You need a Cooking level of ${Kitchen.BREWING_LEVEL} to make tea.")
            return
        }
        if (invReplace(inv, Kitchen.KETTLE_BOILED, 1, Kitchen.KETTLE_EMPTY).failure) {
            return
        }
        invReplace(inv, pot.teapotWithLeaves, 1, pot.teapot(Kitchen.SERVINGS))
        statAdvance(COOKING, Kitchen.BREWING_XP * xpMods.get(player, COOKING))
        mes("You pour the boiling water into the teapot.")
    }

    private fun ProtectedAccess.pour(pot: Kitchen.Cup, servings: Int, cup: Kitchen.Cup) {
        val left = if (servings > 1) pot.teapot(servings - 1) else pot.teapot
        if (invReplace(inv, pot.teapot(servings), 1, left).failure) {
            return
        }
        invReplace(inv, cup.empty, 1, cup.tea)
        mes("You pour some tea.")
    }

    private fun ProtectedAccess.addMilk(cup: Kitchen.Cup) {
        if (invReplace(inv, Kitchen.MILK, 1, BUCKET).failure) {
            return
        }
        invReplace(inv, cup.tea, 1, cup.milky)
        mes("You add some milk to the tea.")
    }

    private companion object {
        const val FILL_ANIM = "seq.poh_barrel_use1"
        const val COOK_ANIM = "seq.human_cooking"
        const val COOKING = "stat.cooking"
        const val BUCKET = "obj.bucket_empty"

        /** How long a kettle takes to boil. */
        const val BOIL_TICKS = 3
    }
}
