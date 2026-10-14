package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val vasaNistirioDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Vasa Nistirio Drops",
    npcs = npcs("npc.raids_vasanistirio_healing", "npc.raids_vasanistirio_walking"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.raids_vasanistirio_book" count 1 condition { player ->
            player.vars["varbit.raids_book_vasanistirio_read"] == 0
        }
        "obj.raids_vial_overload_strong_4" count 1
        "obj.raids_endarkened_juice" count 5
        "obj.raids_vial_xericaid_strong_4" count 2
        "obj.raids_vial_twisted_strong_4" count 2
    },
)
