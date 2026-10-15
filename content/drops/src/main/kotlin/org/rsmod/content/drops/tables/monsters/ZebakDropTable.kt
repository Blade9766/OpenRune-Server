package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.content.drops.hasObjInInventoryOrBank
import org.rsmod.content.drops.isTopDamagerOf
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val zebakDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Zebak Drops",
    npcs = npcs("npc.toa_zebak", "npc.toa_zebak_enraged"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.toa_book_zebak" count 1 condition { player ->
            !player.hasObjInInventoryOrBank("obj.toa_book_zebak")
        }
        "obj.toa_zebak_fang" count 1 killCondition { player, npc, areaChecker ->
            player.isTopDamagerOf(npc)
        }
    },
)
