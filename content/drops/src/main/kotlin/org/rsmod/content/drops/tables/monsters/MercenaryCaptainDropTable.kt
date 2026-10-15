package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val mercenaryCaptainDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Mercenary Captain Drops",
    npcs = npcs("npc.desertminingcaptain"),
    // The metal key is handed over by the Tourist Trap kill hook, straight into the inventory.
    guaranteed = rsPlayerGuaranteedTable {
        "obj.bones" count 1
    },
)
