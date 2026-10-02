package org.rsmod.content.quest.area.falador.recruitmentdrive

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/**
 * Everything the training grounds hand out. The player arrives with nothing, so these are the only
 * items a room can leave them holding; leaving a room deletes them, from the inventory and from the
 * worn slots, and nothing else is touched.
 */
object SessionItems {
    val TYPES: List<String> =
        listOf(
            "obj.steel_sword",
            "obj.steel_claws",
            "obj.steel_battleaxe",
            "obj.steel_warhammer",
            "obj.rd_fox",
            "obj.rd_chicken",
            "obj.rd_sack",
            "obj.rd_hourglass",
            "obj.rd_cupric_sulphate",
            "obj.rd_acetic_acid",
            "obj.rd_gypsum",
            "obj.rd_sodium_chloride",
            "obj.rd_nitorus_oxide",
            "obj.rd_dihydrogen_monoxide",
            "obj.rd_tin_ore_powder",
            "obj.rd_copper_ore_powder",
            "obj.rd_puzzleroom_key",
            "obj.rd_metal_spade",
            "obj.rd_metal_spade_no_handle",
            "obj.rd_chem_book",
            "obj.rd_cupric_sulphate2",
            "obj.rd_plaster_vial",
            "obj.rd_spoilt_potion",
            "obj.rd_tin",
            "obj.rd_tinfull",
            "obj.rd_keymould",
            "obj.rd_full_keymould_tin",
            "obj.rd_full_keymould_copper",
            "obj.rd_full_keymould_unheated",
            "obj.rd_full_keymould_complete",
            "obj.rd_tin_of_crap",
            "obj.rd_tin_of_crap_empty",
            "obj.rd_chisel",
            "obj.rd_wire",
            "obj.rd_shears",
            "obj.rd_magnet",
            "obj.rd_knife",
            "obj.vial_empty",
            "obj.ashes",
        )

    private val ids: Set<Int> by lazy { TYPES.map { it.asRSCM(RSCMType.OBJ) }.toSet() }

    fun isSessionItem(id: Int): Boolean = id in ids

    /** Removes every session item the player carries or wears; returns how many stacks went. */
    fun strip(player: Player): Int {
        var removed = strip(player.inv)
        val worn = strip(player.worn)
        removed += worn
        if (worn > 0) {
            player.rebuildAppearance()
        }
        return removed
    }

    private fun strip(inv: Inventory): Int {
        var removed = 0
        for (slot in inv.indices) {
            val obj = inv[slot] ?: continue
            if (obj.id in ids) {
                inv[slot] = null
                removed++
            }
        }
        return removed
    }
}
