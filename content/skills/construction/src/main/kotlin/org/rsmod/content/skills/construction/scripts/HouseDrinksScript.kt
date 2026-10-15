package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.table.FoodRow
import org.rsmod.content.other.consumables.food.FoodEffectService
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Kitchen
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drinking the tea and drinks served in a house. A house tea boosts Construction by its cup's
 * tier. A barrel drink works as the ordinary drink it copies - the same healing from the food table
 * and the same effect from [FoodEffectService] - except that house cider boosts Farming by 2 rather
 * than 1. Anything the food table already handles is left to it, since a held op can only be bound
 * once.
 */
class HouseDrinksScript @Inject constructor(private val effects: FoodEffectService) : PluginScript() {
    override fun ScriptContext.startup() {
        val eaten = FoodRow.all().flatMap { it.items }.mapTo(HashSet()) { it.id }
        for (cup in Kitchen.Cup.entries) {
            for (tea in listOf(cup.tea, cup.milky)) {
                if (tea.asRSCM(RSCMType.OBJ) !in eaten) {
                    onOpHeld1(tea) { drinkTea(it.slot, tea, cup) }
                }
            }
        }
        for (drink in Kitchen.BARREL_DRINKS) {
            if (drink.asRSCM(RSCMType.OBJ) !in eaten) {
                onOpHeld1(drink) { drinkFromBarrel(it.slot, drink) }
            }
        }
    }

    private fun ProtectedAccess.drinkTea(slot: Int, tea: String, cup: Kitchen.Cup) {
        if (invDel(inv, tea, 1, slot = slot).failure) {
            return
        }
        invAdd(inv, cup.empty)
        anim(DRINK_ANIM)
        statBoost(Construction.STAT, cup.boost, 0)
        mes("You drink the tea. It boosts your Construction.")
    }

    private fun ProtectedAccess.drinkFromBarrel(slot: Int, drink: String) {
        val ordinary = Kitchen.ordinaryDrink(drink)
        val food = FoodRow.all().firstOrNull { row -> row.items.any { it.id == ordinary.asRSCM(RSCMType.OBJ) } }
        if (invDel(inv, drink, 1, slot = slot).failure) {
            return
        }
        invAdd(inv, Kitchen.BEER_GLASS)
        anim(DRINK_ANIM)
        mes("You drink the ${ocName(ocType(drink)).lowercase()}.")
        if (food == null) {
            return
        }
        heal(food.heal)
        if (food.effect.isNotBlank()) {
            effects.apply(this, food.effect)
        }
        if (drink == HOUSE_CIDER) {
            statBoost(FARMING, HOUSE_CIDER_FARMING, 0)
        }
    }

    private fun ProtectedAccess.heal(amount: Int) {
        val missing = statBase(HITPOINTS) - player.hitpoints
        val healed = amount.coerceAtMost(missing)
        if (healed > 0) {
            statAdd(HITPOINTS, healed, 0)
        }
    }

    private fun ProtectedAccess.ocType(obj: String) =
        requireNotNull(dev.openrune.ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { obj }

    private companion object {
        const val DRINK_ANIM = "seq.human_eat"
        const val HITPOINTS = "stat.hitpoints"
        const val FARMING = "stat.farming"
        const val HOUSE_CIDER = "obj.poh_cider"
        const val HOUSE_CIDER_FARMING = 2
    }
}
