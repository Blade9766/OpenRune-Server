package org.rsmod.content.skills.fletching

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FletchingScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs<FletchingTask>(QUEUE) { processTask(it.args) }

        val (toolRecipes, combineRecipes) =
            FLETCHING_RECIPES.partition { it.tool == KNIFE || it.tool == CHISEL }

        for ((key, recipes) in toolRecipes.groupBy { it.tool!! to it.primary }) {
            val (tool, material) = key
            val sorted = recipes.sortedBy { menuOrder(it.output) }
            for (obj in tool.objs) {
                onOpHeldU(obj, material) { select(sorted) }
            }
        }

        val byPair = linkedMapOf<Pair<String, String>, FletchingRecipe>()
        for (recipe in combineRecipes) {
            val (first, second) = recipe.inputs.map { it.obj }
            for (a in variants(first)) {
                for (b in variants(second)) {
                    val pair = if (a < b) a to b else b to a
                    val previous = byPair.put(pair, recipe)
                    check(previous == null) { "Duplicate fletching trigger $pair" }
                }
            }
        }
        for ((pair, recipe) in byPair) {
            onOpHeldU(pair.first, pair.second) { select(listOf(recipe)) }
        }
    }

    private suspend fun ProtectedAccess.select(recipes: List<FletchingRecipe>) {
        val unlocked = recipes.filter { isUnlocked(it) }
        if (unlocked.isEmpty()) {
            mes("You need to learn how to fletch broad ammunition from a Slayer master first.")
            return
        }
        val single = unlocked.singleOrNull()
        if (single != null && !canMake(single, verbose = true)) {
            return
        }
        if (single != null && single.ticks == 0) {
            makeInstantly(single)
            return
        }
        val craftable = unlocked.filter { maxActions(it) > 0 }
        if (craftable.isEmpty()) {
            mes("You don't have the materials to make anything with that.", ChatType.Spam)
            return
        }
        val config =
            SkillMultiConfig(
                verb = if (craftable.all { it.isStringing }) "string" else "make",
                entries = craftable.map { SkillMultiEntry(it.output) },
                maxCountProvider = { _, entry ->
                    craftable.firstOrNull { it.output == entry.internal }?.let { maxActions(it) } ?: 0
                },
            )
        openSkillMulti(config) { selection ->
            val recipe = craftable.first { it.output == selection.entry.internal }
            start(recipe, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.start(recipe: FletchingRecipe, amount: Int) {
        if (!canMake(recipe, verbose = true)) {
            return
        }
        if (recipe.ticks == 0) {
            makeInstantly(recipe)
            return
        }
        beginCycle(recipe)
        val queuedBeforeProcessing = player.currentMapClock != player.processedMapClock
        val compensation = if (queuedBeforeProcessing) 1 else 0
        weakQueue(QUEUE, recipe.ticks + compensation, FletchingTask(recipe, amount, completed = 0))
    }

    private fun ProtectedAccess.processTask(task: FletchingTask) {
        val recipe = task.recipe
        if (!canMakeSilently(recipe) || !makeOnce(recipe)) {
            resetAnim()
            return
        }
        val completed = task.completed + 1
        if (completed >= task.amount || !canMakeSilently(recipe)) {
            return
        }
        beginCycle(recipe)
        weakQueue(QUEUE, recipe.ticks, task.copy(completed = completed))
    }

    private fun ProtectedAccess.makeInstantly(recipe: FletchingRecipe) {
        if (actionDelay > mapClock) {
            return
        }
        actionDelay = mapClock + 1
        beginCycle(recipe)
        makeOnce(recipe)
    }

    private fun ProtectedAccess.beginCycle(recipe: FletchingRecipe) {
        recipe.anim?.let { anim(it) }
        recipe.sound?.let { soundSynth(it) }
    }

    private suspend fun ProtectedAccess.canMake(recipe: FletchingRecipe, verbose: Boolean): Boolean {
        if (player.fletchingLvl < recipe.level) {
            if (verbose) {
                mesbox(
                    "You need a Fletching level of at least ${recipe.level} to make " +
                        "${article(recipe.displayName)} ${recipe.displayName}."
                )
            }
            return false
        }
        return canMakeSilently(recipe, verbose)
    }

    private fun ProtectedAccess.canMakeSilently(
        recipe: FletchingRecipe,
        verbose: Boolean = false,
    ): Boolean {
        if (player.fletchingLvl < recipe.level || !isUnlocked(recipe)) {
            return false
        }
        val tool = recipe.tool
        if (tool != null && !holds(tool)) {
            if (verbose) {
                mes(tool.missing)
            }
            return false
        }
        if (maxActions(recipe) <= 0) {
            if (verbose) {
                mes("You don't have enough materials to do that.", ChatType.Spam)
            }
            return false
        }
        return true
    }

    private fun ProtectedAccess.makeOnce(recipe: FletchingRecipe): Boolean {
        val units = if (recipe.batch) minOf(recipe.outputCount, availableUnits(recipe)) else 1
        if (units <= 0) {
            return false
        }
        val removed = mutableListOf<Pair<String, Int>>()
        for (input in recipe.inputs) {
            val needed = input.count * units
            val taken = takeInput(input.obj, needed)
            removed += taken
            if (taken.sumOf { it.second } < needed) {
                removed.forEach { (obj, count) -> invAdd(inv, obj, count) }
                return false
            }
        }
        val produced = if (recipe.batch) units else recipe.outputCount
        if (invAdd(inv, recipe.output, produced).failure) {
            removed.forEach { (obj, count) -> invAdd(inv, obj, count) }
            mes("You don't have enough inventory space to do that.", ChatType.Spam)
            return false
        }
        val xp = if (recipe.batch) recipe.xp * units / recipe.outputCount else recipe.xp
        statAdvance(STAT, xp)
        mes(message(recipe, produced), ChatType.Spam)
        return true
    }

    private fun ProtectedAccess.takeInput(obj: String, count: Int): List<Pair<String, Int>> {
        val taken = mutableListOf<Pair<String, Int>>()
        var remaining = count
        for (variant in variants(obj)) {
            if (remaining == 0) {
                break
            }
            val take = minOf(remaining, inv.count(variant))
            if (take > 0 && invDel(inv, variant, take).success) {
                taken += variant to take
                remaining -= take
            }
        }
        return taken
    }

    private fun ProtectedAccess.maxActions(recipe: FletchingRecipe): Int {
        if (recipe.batch) {
            val units = availableUnits(recipe)
            return (units + recipe.outputCount - 1) / recipe.outputCount
        }
        return recipe.inputs.minOf { countOf(it.obj) / it.count }
    }

    private fun ProtectedAccess.availableUnits(recipe: FletchingRecipe): Int =
        recipe.inputs.minOf { countOf(it.obj) / it.count }

    private fun ProtectedAccess.countOf(obj: String): Int = variants(obj).sumOf { inv.count(it) }

    private fun ProtectedAccess.holds(tool: FletchingTool): Boolean =
        tool.objs.any { inv.contains(it) || it in player.worn }

    private fun ProtectedAccess.isUnlocked(recipe: FletchingRecipe): Boolean =
        recipe.unlockVarbit == null || vars[recipe.unlockVarbit] > 0

    private fun message(recipe: FletchingRecipe, produced: Int): String {
        val name = recipe.displayName
        val plural = if (name.endsWith("s")) name else "${name}s"
        val described = if (produced > 1) "$produced $plural" else "${article(name)} $name"
        return recipe.message
            .replace("{count}", produced.toString())
            .replace("{plural}", plural)
            .replace("{an}", described)
    }

    private fun article(name: String): String =
        if (name.firstOrNull()?.lowercaseChar() in VOWELS) "an" else "a"

    private val FletchingRecipe.isStringing: Boolean
        get() = inputs.any { it.obj == BOW_STRING || it.obj == CROSSBOW_STRING }

    private data class FletchingTask(
        val recipe: FletchingRecipe,
        val amount: Int,
        val completed: Int,
    )

    private companion object {
        private const val QUEUE = "queue.fletching_make"
        private const val STAT = "stat.fletching"
        private const val BOW_STRING = "obj.bow_string"
        private const val CROSSBOW_STRING = "obj.xbows_crossbow_string"

        private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    }
}

private fun menuOrder(output: String): Int =
    MENU_ORDER.indexOfFirst { it in output }.let { if (it == -1) MENU_ORDER.size else it }

private val MENU_ORDER = listOf("shaft", "shortbow", "longbow", "stock", "shield")

private fun variants(obj: String): List<String> = if (obj == FEATHER) FEATHERS else listOf(obj)
