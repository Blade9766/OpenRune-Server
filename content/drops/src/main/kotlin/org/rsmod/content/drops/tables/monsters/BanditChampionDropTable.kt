package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.content.drops.carries
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val banditChampionDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Bandit champion Drops",
    npcs = npcs("npc.feud_bandit_toughguy"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.blackjack_willow" count 1 condition { player ->
            !player.carries("obj.blackjack_willow")
        }
        "obj.adamant_scimitar" count 1 condition { player ->
            player.carries("obj.blackjack_willow")
        }
    },
)
