package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val vanguardDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Vanguard Drops",
    npcs = npcs("npc.raids_vanguard_magic", "npc.raids_vanguard_melee", "npc.raids_vanguard_ranged", "npc.raids_vanguard_walking"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.raids_vanguard_book" count 1 condition { player ->
            player.vars["varbit.raids_book_vanguard_read"] == 0
        }
        "obj.raids_vial_elder_strong_4" count 1 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.raids_vanguard_melee"
        }
        "obj.raids_vial_twisted_strong_4" count 1
        "obj.raids_vial_kodai_strong_4" count 1
        "obj.raids_vial_xericaid_strong_4" count 1..2
        "obj.raids_vial_revitalisation_strong_4" count 2 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.raids_vanguard_ranged"
        }
        "obj.raids_vial_prayer_strong_4" count 1 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.raids_vanguard_magic"
        }
    },
    mainTable = rsPlayerWeightedTable(total = 3) {
        name("Vanguard Drops")
        1 weight "obj.raids_vial_overload_strong_4" count 1
        2 weight nothing()
    },
)
