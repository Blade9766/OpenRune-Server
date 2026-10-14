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
public val baBaDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Ba-Ba Drops",
    npcs = npcs("npc.toa_baba", "npc.toa_baba_coffin", "npc.toa_baba_digging"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.toa_book_baba" count 1 condition { player ->
            !player.hasObjInInventoryOrBank("obj.toa_book_baba")
        }
        "obj.toa_baba_banana" count 1 killCondition { player, npc, areaChecker ->
            player.isTopDamagerOf(npc)
        }
    },
)
