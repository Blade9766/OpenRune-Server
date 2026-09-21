package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val chompyBirdDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Chompy bird Drops",
    // The carcass, its bones and its feathers come from plucking the dead bird, not from the
    // death drop; see the Big Chompy Bird Hunting content.
    npcs = npcs("npc.chompybird"),
    mainTable = rsPlayerWeightedTable(total = 500) {
        name("Chompy bird Drops")
        1 weight "obj.chompybird_pet" count 1 condition { player ->
            // Drops Need Manual: Only players who have completed the elite Western Provinces Diary are eligible to receive this pet.
             true
        }
        499 weight nothing()
    },
)
