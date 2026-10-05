package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.construction.data.Workshop
import org.rsmod.content.skills.construction.data.Workshop.ClockworkRecipe
import org.rsmod.content.skills.construction.data.Workshop.Repair
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Tool stores, the clockmaker's bench and the repair benches. */
class WorkshopScript
@Inject
constructor(private val xpMods: XpModifiers, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for ((store, tools) in Workshop.TOOL_STORES) {
            onOpLoc1(store) { takeTool(tools) }
        }
        for ((table, tier) in Workshop.CRAFTING_TABLES) {
            onOpLoc1(table) { craft(tier) }
        }
        for ((bench, tier) in Workshop.REPAIR_BENCHES) {
            for (repair in Workshop.REPAIRS) {
                onOpLocU(bench, repair.item) { repair(repair, tier, bench) }
            }
        }
    }

    // ----------------------------------------------------------------------- tool stores

    private suspend fun ProtectedAccess.takeTool(tools: List<String>) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        val config =
            SkillMultiConfig(
                actionType = SkillingActionType.TAKE,
                verb = "take",
                entries = tools.map { SkillMultiEntry(it) },
                maxCountProvider = { inv, _ -> inv.freeSpace() },
            )
        openSkillMulti(config) { selection ->
            invAdd(inv, selection.entry.internal, selection.amount.coerceAtMost(inv.freeSpace()))
        }
    }

    // ---------------------------------------------------------------------- clockmaking

    private suspend fun ProtectedAccess.craft(tier: Int) {
        val recipes = Workshop.CLOCKWORK_RECIPES.filter { it.table <= tier }
        val config =
            SkillMultiConfig(
                verb = "make",
                entries = recipes.map { SkillMultiEntry(it.output) },
                maxCountProvider = { inv, entry -> makeable(inv, recipes.first { it.output == entry.internal }) },
            )
        openSkillMulti(config) { selection ->
            val recipe = recipes.first { it.output == selection.entry.internal }
            makeClockwork(recipe, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.makeClockwork(recipe: ClockworkRecipe, amount: Int) {
        if (player.craftingLvl < recipe.level) {
            mes("You need a Crafting level of ${recipe.level} to make that.")
            return
        }
        repeat(amount) {
            if (makeable(inv, recipe) <= 0) {
                return
            }
            anim(BENCH_ANIM)
            delay(CRAFT_CYCLE)
            for (ingredient in recipe.ingredients) {
                val obj = ingredient.objs.first { inv.count(it) >= ingredient.count }
                if (invDel(inv, obj, ingredient.count).failure) {
                    return
                }
            }
            invAdd(inv, recipe.output)
            statAdvance(CRAFTING, recipe.xp * xpMods.get(player, CRAFTING))
        }
    }

    private fun makeable(inv: Inventory, recipe: ClockworkRecipe): Int =
        recipe.ingredients.minOf { ingredient ->
            ingredient.objs.maxOf { inv.count(it) } / ingredient.count
        }

    // -------------------------------------------------------------------------- repairs

    private suspend fun ProtectedAccess.repair(repair: Repair, tier: Int, bench: String) {
        if (tier < repair.bench) {
            mes("You need a better repair bench to repair that.")
            return
        }
        if (repair.item !in inv) {
            return
        }
        anim(if (bench == WHETSTONE) WHETSTONE_ANIM else BENCH_ANIM)
        delay(REPAIR_CYCLE)
        if (invDel(inv, repair.item).failure) {
            return
        }
        if (!statRandom(repair.stat, repair.low, repair.high, invisibleBoost = 0)) {
            mes("You fail to repair it, and it breaks beyond repair.")
            return
        }
        statAdvance(repair.stat, repair.xp * xpMods.get(player, repair.stat))
        val reward = roll(repair)
        if (reward.obj == null) {
            mes("You repair it, but there is nothing of value left.")
            return
        }
        val count = random.of(reward.count.first, reward.count.last)
        invAddOrDrop(objRepo, reward.obj, count)
        mes("You manage to repair it.")
    }

    private fun ProtectedAccess.roll(repair: Repair): Workshop.Reward {
        var roll = random.of(0, repair.rewards.sumOf { it.weight } - 1)
        for (reward in repair.rewards) {
            roll -= reward.weight
            if (roll < 0) {
                return reward
            }
        }
        return repair.rewards.last()
    }

    private companion object {
        const val CRAFTING = "stat.crafting"
        const val WHETSTONE = "loc.poh_repair_2"
        const val BENCH_ANIM = "seq.human_poh_build"
        const val WHETSTONE_ANIM = "seq.poh_human_whetstone"
        const val CRAFT_CYCLE = 3
        const val REPAIR_CYCLE = 3
    }
}
