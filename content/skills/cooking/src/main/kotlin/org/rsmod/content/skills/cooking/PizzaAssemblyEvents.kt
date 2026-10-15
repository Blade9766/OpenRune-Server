package org.rsmod.content.skills.cooking

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PizzaAssemblyEvents : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU("obj.knife", "obj.pineapple") { slicePineapple() }

        onOpHeldU("obj.pizza_base", "obj.tomato") { addTomato() }
        onOpHeldU("obj.incomplete_pizza", "obj.cheese") { addCheese() }

        onOpHeldU("obj.plain_pizza", "obj.anchovies") { addTopping("obj.anchovies", ANCHOVY) }
        onOpHeldU("obj.plain_pizza", "obj.cooked_meat") { addTopping("obj.cooked_meat", MEAT) }
        onOpHeldU("obj.plain_pizza", "obj.cooked_chicken") { addTopping("obj.cooked_chicken", MEAT) }
        onOpHeldU("obj.plain_pizza", "obj.pineapple_chunks") { addTopping("obj.pineapple_chunks", PINEAPPLE) }
        onOpHeldU("obj.plain_pizza", "obj.pineapple_ring") { addTopping("obj.pineapple_ring", PINEAPPLE) }
    }

    private data class Topping(val result: String, val name: String, val level: Int, val xp: Double)

    private suspend fun ProtectedAccess.slicePineapple() {
        openSkillMulti(SkillMultiConfig(
            verb = "cut",
            entries = listOf(
                SkillMultiEntry("obj.pineapple_chunks", listOf(Material("obj.pineapple"))),
                SkillMultiEntry("obj.pineapple_ring", listOf(Material("obj.pineapple"))),
            ),
        )) { selection ->
            val output = selection.entry.internal
            val count = if (output == "obj.pineapple_ring") RINGS_PER_PINEAPPLE else 1
            repeat(selection.amount) {
                if (!inv.contains("obj.pineapple")) return@openSkillMulti
                if (inv.freeSpace() < count - 1) {
                    mes("You don't have enough inventory space to do that.")
                    return@openSkillMulti
                }
                if (invDel(inv, "obj.pineapple", 1).failure) return@openSkillMulti
                invAdd(inv, output, count)
            }
        }
    }

    private fun ProtectedAccess.addTomato() {
        invDel(inv, "obj.pizza_base", 1)
        invDel(inv, "obj.tomato", 1)
        invAdd(inv, "obj.incomplete_pizza", 1)
        mes("You add the tomato to the pizza base.")
    }

    private fun ProtectedAccess.addCheese() {
        invDel(inv, "obj.incomplete_pizza", 1)
        invDel(inv, "obj.cheese", 1)
        invAdd(inv, "obj.uncooked_pizza", 1)
        mes("You add the cheese to the pizza.")
    }

    private suspend fun ProtectedAccess.addTopping(ingredient: String, topping: Topping) {
        if (player.cookingLvl < topping.level) {
            mesbox("You need a Cooking level of ${topping.level} to make ${topping.name}.")
            return
        }
        if (invDel(inv, "obj.plain_pizza", 1).failure) return
        if (invDel(inv, ingredient, 1).failure) {
            invAdd(inv, "obj.plain_pizza", 1)
            return
        }
        invAdd(inv, topping.result, 1)
        statAdvance("stat.cooking", topping.xp)
        mes("You add the topping to the pizza.")
    }

    private companion object {
        const val RINGS_PER_PINEAPPLE = 4
        val MEAT = Topping("obj.meat_pizza", "a meat pizza", 45, 26.0)
        val ANCHOVY = Topping("obj.anchovie_pizza", "an anchovy pizza", 55, 39.0)
        val PINEAPPLE = Topping("obj.pineapple_pizza", "a pineapple pizza", 65, 45.0)
    }
}
