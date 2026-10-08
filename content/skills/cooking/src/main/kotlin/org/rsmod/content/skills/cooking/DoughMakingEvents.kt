package org.rsmod.content.skills.cooking

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DoughMakingEvents : PluginScript() {

    private val waterSources = listOf(
        "obj.jug_water" to "obj.jug_empty",
        "obj.bucket_water" to "obj.bucket_empty",
        "obj.bowl_water" to "obj.bowl_empty",
    )

    override fun ScriptContext.startup() {
        waterSources.forEach { (water, emptyContainer) ->
            onOpHeldU("obj.pot_flour", water) { chooseDough(water, emptyContainer) }
        }
        onPlayerQueueWithArgs<DoughTask>(QUEUE) { processDough(it.args) }
    }

    private suspend fun ProtectedAccess.chooseDough(water: String, emptyContainer: String) {
        val materials = listOf(Material("obj.pot_flour"), Material(water))
        openSkillMulti(SkillMultiConfig(
            verb = "make",
            entries = listOf(
                SkillMultiEntry("obj.bread_dough", materials),
                SkillMultiEntry("obj.pastry_dough", materials),
                SkillMultiEntry("obj.pizza_base", materials),
            ),
        )) { selection ->
            val task = DoughTask(
                dough = selection.entry.internal,
                name = selection.entry.item.name.lowercase(),
                water = water,
                emptyContainer = emptyContainer,
                remaining = selection.amount,
            )
            if (mixDough(task)) {
                queueNext(task)
            }
        }
    }

    private fun ProtectedAccess.processDough(task: DoughTask) {
        if (mixDough(task)) {
            queueNext(task)
        }
    }

    private fun ProtectedAccess.queueNext(task: DoughTask) {
        val remaining = task.remaining - 1
        if (remaining > 0) {
            weakQueue(QUEUE, 1, task.copy(remaining = remaining))
        }
    }

    private fun ProtectedAccess.mixDough(task: DoughTask): Boolean {
        if (!inv.contains("obj.pot_flour") || !inv.contains(task.water)) {
            return false
        }
        if (inv.freeSpace() < 1) {
            mes("You don't have enough inventory space to do that.")
            return false
        }
        if (invDel(inv, "obj.pot_flour", 1).failure) {
            return false
        }
        if (invDel(inv, task.water, 1).failure) {
            invAdd(inv, "obj.pot_flour", 1)
            return false
        }
        invAdd(inv, task.dough, 1)
        invAdd(inv, "obj.pot_empty", 1)
        invAdd(inv, task.emptyContainer, 1)
        mes("You mix the flour and water to make some ${task.name}.")
        return true
    }

    private data class DoughTask(
        val dough: String,
        val name: String,
        val water: String,
        val emptyContainer: String,
        val remaining: Int,
    )

    private companion object {
        const val QUEUE = "queue.cooking_dough_make"
    }
}
