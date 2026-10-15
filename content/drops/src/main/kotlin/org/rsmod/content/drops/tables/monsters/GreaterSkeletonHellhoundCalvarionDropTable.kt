package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.content.drops.canReceiveChampionScroll
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val greaterSkeletonHellhoundCalvarionDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Greater Skeleton Hellhound (Calvar'ion) Drops",
    npcs = npcs("npc.vetion_hellhound_snr_singles"),
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 5000 weight "obj.champions_challenge_skeleton" count 1 condition { player ->
            player.canReceiveChampionScroll("skeleton")
        }
        1 outOf 296 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 88 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
