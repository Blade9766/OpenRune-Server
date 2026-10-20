package org.rsmod.content.skills.runecrafting.arceuus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.game.entity.Player

object DarkEssence {
    const val DENSE_BLOCK = "obj.arceuus_essence_block"
    const val DARK_BLOCK = "obj.arceuus_essence_block_dark"
    const val FRAGMENTS = "obj.bigblankrune"
    const val FRAGMENTS_PER_BLOCK = 4
    const val MAX_FRAGMENTS = 108
    const val VENERATE_XP = 2.5
    const val CHISEL_CRAFTING_XP = 8.0
    const val CHISEL_LEVEL = 38

    /** How many dark blocks can be chiselled before the fragment stack reaches its cap. */
    fun chiselableBlocks(fragments: Int, blocks: Int): Int {
        val room = max(0, MAX_FRAGMENTS - fragments) / FRAGMENTS_PER_BLOCK
        return min(max(0, blocks), room)
    }

    fun prayerDrain(blocks: Int, prayerPoints: Int): Int = min(max(0, blocks), max(0, prayerPoints))

    /** Turns every dense essence block into a dark one in the same slot; returns how many. */
    fun Player.venerateDenseBlocks(): Int {
        val dense = DENSE_BLOCK.asRSCM(RSCMType.OBJ)
        val dark = DARK_BLOCK.asRSCM(RSCMType.OBJ)
        var converted = 0
        for (slot in inv.indices) {
            val obj = inv[slot] ?: continue
            if (obj.id != dense) {
                continue
            }
            val result =
                invTransaction(inv) {
                    val target = select(inv)
                    delete(target, dense, obj.count, slot = slot)
                    add(target, dark, obj.count, slot = slot)
                }
            if (result.success) {
                converted += obj.count
            }
        }
        return converted
    }

    /** Swaps one dark essence block for its fragments, unless that would pass the fragment cap. */
    fun Player.chiselDarkBlock(): Boolean {
        if (chiselableBlocks(inv.count(FRAGMENTS), 1) == 0) {
            return false
        }
        val result =
            invTransaction(inv) {
                val target = select(inv)
                delete(target, DARK_BLOCK.asRSCM(RSCMType.OBJ), 1)
                add(target, FRAGMENTS.asRSCM(RSCMType.OBJ), FRAGMENTS_PER_BLOCK)
            }
        return result.success
    }
}
