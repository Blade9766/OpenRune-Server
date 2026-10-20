package org.rsmod.content.skills.mining.dense

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.mining.MiningRocksRow
import org.rsmod.content.skills.mining.configs.miningXp
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two dense runestones north of Arceuus. Each player has their own copy of both through the
 * cache's `arceuus_runestone_*` varbits: chipping one may deplete it, which wakes the other one up,
 * so a player keeps walking between them.
 */
class DenseRunestones @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    private val rock by lazy { MiningRocksRow.getRow("dbrow.mining_denseessence") }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.arceuus_runestone_base_1") { chip(RUNESTONE_1, RUNESTONE_2) }
        onOpLoc1("loc.arceuus_runestone_base_2") { chip(RUNESTONE_2, RUNESTONE_1) }
    }

    private suspend fun ProtectedAccess.chip(stone: String, other: String) {
        if (vars[stone] != 0) {
            mes("This runestone has been depleted of its essence.")
            return
        }
        val block = rock.oreItem?.internalName ?: return
        while (true) {
            if (!canChip()) {
                resetAnim()
                return
            }
            repeat(BLOCK_TICKS / ANIM_TICKS) {
                anim(CHIP_ANIM)
                delay(ANIM_TICKS)
            }
            if (vars[stone] != 0 || !canChip()) {
                resetAnim()
                return
            }
            if (invAdd(inv, block).failure) {
                return
            }
            statAdvance("stat.mining", rock.miningXp * xpMods.get(player, "stat.mining"))
            statAdvance("stat.crafting", CRAFTING_XP * xpMods.get(player, "stat.crafting"))
            spam("You chip away a block of dense essence.")

            val roll = random.of(DenseRunestoneDepletion.ROLL_RANGE)
            if (DenseRunestoneDepletion.depletes(player.miningLvl, roll)) {
                VarPlayerIntMapSetter.set(player, stone, 1)
                VarPlayerIntMapSetter.set(player, other, 0)
                resetAnim()
                mes("The runestone has been depleted. The other runestone is now ready to mine.")
                return
            }
        }
    }

    private fun ProtectedAccess.canChip(): Boolean {
        if (player.miningLvl < rock.level) {
            mes("You need a Mining level of ${rock.level} to mine this rock.")
            return false
        }
        if (player.craftingLvl < CRAFTING_LEVEL) {
            mes("You need a Crafting level of $CRAFTING_LEVEL to chip dense essence.")
            return false
        }
        if (Mining.findPickaxe(player) == null) {
            mes("You need a pickaxe to mine this rock.")
            return false
        }
        if (CHISEL !in inv) {
            mes("You need a chisel to chip dense essence from the runestone.")
            return false
        }
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more dense essence.")
            return false
        }
        return true
    }

    private companion object {
        const val RUNESTONE_1 = "varbit.arceuus_runestone_1"
        const val RUNESTONE_2 = "varbit.arceuus_runestone_2"
        const val CHISEL = "obj.chisel"
        const val CHIP_ANIM = "seq.arceuus_chisel_essence"
        const val CRAFTING_LEVEL = 38
        const val CRAFTING_XP = 8.0
        const val BLOCK_TICKS = 9
        const val ANIM_TICKS = 3
    }
}
