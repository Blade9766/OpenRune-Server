package org.rsmod.content.skills.cooking

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.cooking.CookingAlesRow
import org.rsmod.api.utils.time.epochMinute
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BrewingEvents @Inject constructor() : PluginScript() {

    private val ales = CookingAlesRow.all()

    data class Brewery(
        val vatVarbit: String,
        val barrelVarbit: String,
        val fermentStartVarp: String,
    )

    private val breweries = (1..4).map { index ->
        Brewery(
            "varbit.brewing_vat_varbit_$index",
            "varbit.brewing_barrel_varbit_$index",
            "varp.brewing_ferment_start_$index",
        )
    }

    private val vatChildLocs = buildList {
        addAll(listOf("loc.vat_empty", "loc.vat_water", "loc.vat_bad_ale", "loc.vat_bad_cider", "loc.vat_barley"))
        val aleNames = listOf(
            "dwarven_stout", "asgarnian_ale", "greenmans_ale", "wizards_mind_bomb",
            "dragon_bitter", "moonlight_mead", "axemans_folly", "chefs_delight",
            "slayers_respite", "cider",
        )
        aleNames.forEach { ale ->
            add("loc.vat_${ale}_hops")
            add("loc.vat_${ale}_brewing_1")
            add("loc.vat_${ale}_brewing_2")
            add("loc.vat_${ale}_normal")
            add("loc.vat_${ale}_mature")
        }
    }

    private val barrelChildLocs = buildList {
        add("loc.barrel_empty")
        add("loc.barrel_unfinished_ale")
        add("loc.barrel_bad_ale")
        add("loc.barrel_bad_cider")
        val aleNames = listOf(
            "dwarven_stout", "asgarnian_ale", "greenmans_ale", "wizards_mind_bomb",
            "dragon_bitter", "moonlight_mead", "axemans_folly", "chefs_delight",
            "slayers_respite", "cider",
        )
        aleNames.forEach { ale ->
            add("loc.barrel_$ale")
            add("loc.barrel_${ale}_mature")
        }
    }

    private val valveLocs = listOf(
        "loc.vat_valve_1",
        "loc.vat_valve_2",
        "loc.vat_valve_3",
    )

    private val valveBaseToBrewery = mapOf(23936 to 0, 23937 to 1, 55339 to 2)
    private val vatBaseToBrewery = mapOf(11670 to 0, 24956 to 1, 55338 to 2)
    private val barrelBaseToBrewery = mapOf(24957 to 0, 20795 to 1, 55340 to 2)

    private fun breweryIndexFromVat(baseLocId: Int) = vatBaseToBrewery[baseLocId] ?: 0
    private fun breweryIndexFromBarrel(baseLocId: Int) = barrelBaseToBrewery[baseLocId] ?: 0

    override fun ScriptContext.startup() {
        vatChildLocs.forEach { vat ->
            onOpLoc1(vat) { handleVatClick(it.loc.id) }
            onOpLocU(vat) { handleVatItemUse(it.loc.id, it.objType.internalName) }
        }

        valveLocs.forEach { valve ->
            onOpLoc1(valve) { turnValve(it.loc.id) }
        }

        barrelChildLocs.forEach { barrel ->
            onOpLoc1(barrel) { checkBarrel(it.loc.id) }
            onOpLocU(barrel, "obj.beer_glass") { collectBeer(it.loc.id) }
        }

        onPlayerLogin { breweries.indices.forEach { player.refreshFermentation(it) } }
    }

    private suspend fun ProtectedAccess.handleVatItemUse(baseLocId: Int, obj: String) {
        player.refreshFermentation(breweryIndexFromVat(baseLocId))
        when (obj) {
            "obj.bucket_water" -> addWater(baseLocId)
            "obj.barley_malt" -> addMalt(baseLocId)
            "obj.brew_hyper_yeast" -> addTheStuff(baseLocId)
            "obj.ale_yeast" -> addYeast(baseLocId)
            else -> {
                if (ales.any { it.ingredient.internalName == obj }) {
                    addHopsForIngredient(obj, baseLocId)
                } else {
                    mes("Nothing interesting happens.")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.handleVatClick(baseLocId: Int) {
        val breweryIndex = breweryIndexFromVat(baseLocId)
        player.refreshFermentation(breweryIndex)
        val state = vars[breweries[breweryIndex].vatVarbit]

        when {
            state == 0 -> {
                if (inv.count("obj.bucket_water") >= 2) addWater(baseLocId)
                else mes("The vat is empty. You need 2 buckets of water to start.")
            }
            state == 1 -> {
                if (inv.count("obj.barley_malt") >= 2) addMalt(baseLocId)
                else mes("The vat has water. You need 2 barley malt.")
            }
            state == 2 -> {
                val recipe = findRecipeInInventory()
                if (recipe != null) addHops(recipe, baseLocId)
                else mes("The vat has water and malt. Add your hops or ingredient.")
            }
            ales.any { it.vatOffset == state } -> {
                if (inv.contains("obj.ale_yeast")) addYeast(baseLocId)
                else mes("The vat has the ingredients. Add ale yeast to begin brewing.")
            }
            ales.any { state == it.brewingState || state == it.brewingState + 1 } -> {
                mes("The ale is still fermenting. Come back later.")
            }
            ales.any { state == it.normalState || state == it.matureState } -> {
                mes("The ale is ready. Turn the valve to transfer it to the barrel.")
            }
            else -> mes("The vat is in use.")
        }
    }

    private suspend fun ProtectedAccess.findRecipeInInventory(): CookingAlesRow? {
        val available = ales.filter {
            inv.count(it.ingredient.internalName) >= it.ingredientCount && player.cookingLvl >= it.level
        }
        if (available.isEmpty()) return null
        if (available.size == 1) return available.first()

        var chosen: CookingAlesRow? = null
        openSkillMulti(SkillMultiConfig(
            verb = "brew",
            entries = available.map { SkillMultiEntry(it.result.internalName, listOf(Material(it.ingredient.internalName, it.ingredientCount))) },
        )) { selection ->
            chosen = available.firstOrNull { it.result.internalName == selection.entry.internal }
        }
        return chosen
    }

    private suspend fun ProtectedAccess.addHopsForIngredient(ingredient: String, baseLocId: Int) {
        val brewery = breweries[breweryIndexFromVat(baseLocId)]
        if (vars[brewery.vatVarbit] != 2) {
            mes("The vat isn't ready for ingredients yet.")
            return
        }
        val matching = ales.filter { it.ingredient.internalName == ingredient && player.cookingLvl >= it.level }
        if (matching.isEmpty()) {
            mes("You don't have the Cooking level to brew anything with that.")
            return
        }
        val recipe = if (matching.size == 1) {
            matching.first()
        } else {
            var chosen: CookingAlesRow? = null
            openSkillMulti(SkillMultiConfig(
                verb = "brew",
                entries = matching.map { SkillMultiEntry(it.result.internalName, listOf(Material(it.ingredient.internalName, it.ingredientCount))) },
            )) { selection ->
                chosen = matching.firstOrNull { it.result.internalName == selection.entry.internal }
            }
            chosen ?: return
        }
        if (inv.count(recipe.ingredient.internalName) < recipe.ingredientCount) {
            mes("You need ${recipe.ingredientCount} of that ingredient.")
            return
        }
        addHops(recipe, baseLocId)
    }

    private fun ProtectedAccess.addWater(baseLocId: Int) {
        val brewery = breweries[breweryIndexFromVat(baseLocId)]
        if (vars[brewery.vatVarbit] != 0) {
            mes("The vat already has something in it.")
            return
        }
        if (inv.count("obj.bucket_water") < 2) {
            mes("You need 2 buckets of water.")
            return
        }
        invDel(inv, "obj.bucket_water", 2)
        invAdd(inv, "obj.bucket_empty", 2)
        vars[brewery.vatVarbit] = 1
        mes("You add the water to the vat.")
    }

    private fun ProtectedAccess.addMalt(baseLocId: Int) {
        val brewery = breweries[breweryIndexFromVat(baseLocId)]
        if (vars[brewery.vatVarbit] != 1) {
            mes("You need to add water first.")
            return
        }
        if (inv.count("obj.barley_malt") < 2) {
            mes("You need 2 barley malt.")
            return
        }
        invDel(inv, "obj.barley_malt", 2)
        vars[brewery.vatVarbit] = 2
        mes("You add the barley malt to the vat.")
    }

    private fun ProtectedAccess.addHops(recipe: CookingAlesRow, baseLocId: Int) {
        val brewery = breweries[breweryIndexFromVat(baseLocId)]
        if (vars[brewery.vatVarbit] != 2) {
            mes("The vat isn't ready for ingredients yet.")
            return
        }
        if (invDel(inv, recipe.ingredient.internalName, recipe.ingredientCount).failure) {
            return
        }
        vars[brewery.vatVarbit] = recipe.vatOffset
        mes("You add the ingredient to the vat.")
    }

    private fun ProtectedAccess.addTheStuff(baseLocId: Int) {
        val brewery = breweries[breweryIndexFromVat(baseLocId)]
        val state = vars[brewery.vatVarbit]
        val hasIngredients = state == 2 || ales.any { it.vatOffset == state }
        if (!hasIngredients) {
            mes("There's nothing to add this to.")
            return
        }
        invDel(inv, "obj.brew_hyper_yeast", 1)
        mes("You add the secret ingredient to the vat.")
    }

    private fun ProtectedAccess.addYeast(baseLocId: Int) {
        val brewery = breweries[breweryIndexFromVat(baseLocId)]
        val state = vars[brewery.vatVarbit]
        val recipe = ales.firstOrNull { it.vatOffset == state }
        if (recipe == null) {
            mes("The vat isn't ready for yeast. Add your ingredient first.")
            return
        }
        if (invDel(inv, "obj.ale_yeast", 1).failure) {
            return
        }
        invAdd(inv, "obj.pot_empty", 1)
        vars[brewery.vatVarbit] = recipe.brewingState
        vars[brewery.fermentStartVarp] = epochMinute()
        mes("You add the ale yeast and the mixture begins to ferment.")
    }

    private fun ProtectedAccess.turnValve(baseLocId: Int) {
        val breweryIndex = valveBaseToBrewery[baseLocId] ?: 0
        player.refreshFermentation(breweryIndex)
        val brewery = breweries[breweryIndex]
        val vatState = vars[brewery.vatVarbit]

        val recipe = ales.firstOrNull { vatState == it.normalState || vatState == it.matureState }
        if (recipe == null) {
            mes("The vat isn't ready to transfer yet.")
            return
        }
        if (vars[brewery.barrelVarbit] != 0) {
            mes("The barrel is still in use. Empty it first.")
            return
        }

        val mature = vatState == recipe.matureState
        val base = if (mature) recipe.matureBarrelBase else recipe.normalBarrelBase
        vars[brewery.vatVarbit] = 0
        vars[brewery.barrelVarbit] = base + PINTS_PER_BARREL - 1
        statAdvance("stat.cooking", recipe.xp.toDouble())
        mes("You turn the valve and the ale drains into the barrel.")
    }

    private fun ProtectedAccess.checkBarrel(baseLocId: Int) {
        val brewery = breweries[breweryIndexFromBarrel(baseLocId)]
        val state = vars[brewery.barrelVarbit]
        if (state in DRAINABLE_BARREL_STATES) {
            vars[brewery.barrelVarbit] = 0
            mes("You drain the barrel.")
            return
        }
        val contents = barrelContents(state)
        if (contents == null) {
            mes("The barrel is empty.")
            return
        }
        val (recipe, mature, pints) = contents
        val name = if (mature) recipe.matureResult.name else recipe.result.name
        val unit = if (pints == 1) "pint" else "pints"
        mes("The barrel contains $pints $unit of $name.")
    }

    private fun ProtectedAccess.collectBeer(baseLocId: Int) {
        val brewery = breweries[breweryIndexFromBarrel(baseLocId)]
        val state = vars[brewery.barrelVarbit]

        val contents = barrelContents(state)
        if (contents == null) {
            mes("The barrel is empty.")
            return
        }
        val (recipe, mature, pints) = contents
        val glasses = inv.count("obj.beer_glass")
        if (glasses <= 0) {
            mes("You need a beer glass to collect the ale.")
            return
        }

        val result = if (mature) recipe.matureResult.internalName else recipe.result.internalName
        val filled = minOf(glasses, pints)
        var poured = 0
        repeat(filled) {
            if (invDel(inv, "obj.beer_glass", 1).failure) {
                return@repeat
            }
            if (invAdd(inv, result, 1).failure) {
                invAdd(inv, "obj.beer_glass", 1)
                return@repeat
            }
            poured++
        }
        if (poured == 0) {
            return
        }

        val remaining = pints - poured
        val base = if (mature) recipe.matureBarrelBase else recipe.normalBarrelBase
        vars[brewery.barrelVarbit] = if (remaining > 0) base + remaining - 1 else 0
        val unit = if (poured == 1) "glass" else "glasses"
        mes("You fill $poured $unit from the barrel.")
        if (remaining <= 0) {
            mes("The barrel is now empty.")
        }
    }

    private data class BarrelContents(val recipe: CookingAlesRow, val mature: Boolean, val pints: Int)

    private fun barrelContents(state: Int): BarrelContents? {
        for (recipe in ales) {
            if (state in recipe.normalBarrelBase until recipe.normalBarrelBase + PINTS_PER_BARREL) {
                return BarrelContents(recipe, false, state - recipe.normalBarrelBase + 1)
            }
            if (state in recipe.matureBarrelBase until recipe.matureBarrelBase + PINTS_PER_BARREL) {
                return BarrelContents(recipe, true, state - recipe.matureBarrelBase + 1)
            }
        }
        return null
    }

    private fun Player.refreshFermentation(breweryIndex: Int) {
        val brewery = breweries.getOrNull(breweryIndex) ?: return
        val state = vars[brewery.vatVarbit]
        val recipe = ales.firstOrNull { state == it.brewingState || state == it.brewingState + 1 }
        if (recipe == null) {
            if (vars[brewery.fermentStartVarp] != 0) {
                VarPlayerIntMapSetter.set(this, brewery.fermentStartVarp, 0)
            }
            return
        }
        val start =
            vars[brewery.fermentStartVarp].takeIf { it > 0 }
                ?: epochMinute().also { VarPlayerIntMapSetter.set(this, brewery.fermentStartVarp, it) }
        val cycles = epochMinute() / BREW_CYCLE_MINUTES - start / BREW_CYCLE_MINUTES
        val target =
            when {
                cycles >= BREW_CYCLES -> {
                    VarPlayerIntMapSetter.set(this, brewery.fermentStartVarp, 0)
                    if (Random.nextInt(100) < MATURE_CHANCE_PERCENT) recipe.matureState else recipe.normalState
                }
                cycles == BREW_CYCLES - 1 -> recipe.brewingState + 1
                else -> recipe.brewingState
            }
        if (target != state) {
            VarPlayerIntMapSetter.set(this, brewery.vatVarbit, target)
        }
    }

    private val CookingAlesRow.brewingState: Int get() = vatOffset + 1
    private val CookingAlesRow.normalState: Int get() = vatOffset + 3
    private val CookingAlesRow.matureState: Int get() = vatOffset + 4

    private val CookingAlesRow.recipeIndex: Int get() = (vatOffset - FIRST_VAT_OFFSET) / VAT_STRIDE
    private val CookingAlesRow.normalBarrelBase: Int get() = NORMAL_BARREL_BASE + recipeIndex * PINTS_PER_BARREL
    private val CookingAlesRow.matureBarrelBase: Int get() = MATURE_BARREL_BASE + recipeIndex * PINTS_PER_BARREL

    private companion object {
        const val PINTS_PER_BARREL = 8
        const val BREW_CYCLE_MINUTES = 640
        const val BREW_CYCLES = 3
        const val MATURE_CHANCE_PERCENT = 5
        const val FIRST_VAT_OFFSET = 4
        const val VAT_STRIDE = 6
        const val NORMAL_BARREL_BASE = 8
        const val MATURE_BARREL_BASE = 136
        val DRAINABLE_BARREL_STATES = setOf(1, 2, 4)
    }
}
