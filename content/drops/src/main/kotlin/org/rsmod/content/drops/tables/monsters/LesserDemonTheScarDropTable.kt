package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.canReceiveChampionScroll
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val lesserDemonTheScarDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Lesser demon (The Scar) Drops",
    npcs = npcs("npc.dt2_scar_lesser_demon_1", "npc.dt2_scar_maze_3_link_chanter", "npc.dt2_scar_maze_mage_demon_normal", "npc.dt2_scar_maze_melee_demon_normal", "npc.dt2_scar_maze_ranged_demon_normal"),
    mainTable = rsPlayerWeightedTable(total = 5000) {
        name("Lesser demon (The Scar) Drops")
        1 weight "obj.champions_challenge_lesserdemon" count 1 condition { player ->
            player.canReceiveChampionScroll("lesserdemon")
        }
        4999 weight nothing()
    },
)
