package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val banditDropTableLevel130: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Bandit Level 130 drops (members only)",
    npcs = npcs("npc.wilderness_bandit"),
    mainTable = rsPlayerWeightedTable(total = 143) {
        name("Bandit Level 130 drops (members only)")
        4 weight "obj.adamant_scimitar" count 1
        2 weight "obj.mithril_sq_shield" count 1
        1 weight "obj.mithril_axe" count 1
        5 weight "obj.rune_full_helm" count 1
        5 weight "obj.rune_med_helm" count 1
        5 weight "obj.rune_scimitar" count 1
        1 weight "obj.dragon_longsword" count 1
        3 weight "obj.chaosrune" count 10
        3 weight "obj.waterrune" count 11
        1 weight "obj.mindrune" count 12
        2 weight "obj.airrune" count 25
        2 weight "obj.deathrune" count 4
        2 weight "obj.lawrune" count 6
        1 weight "obj.bloodrune" count 6
        1 weight "obj.naturerune" count 3
        26 weight "obj.coins" count 35
        13 weight "obj.coins" count 120
        10 weight "obj.coins" count 53
        2 weight "obj.coins" count 250
        1 weight "obj.coins" count 10
        6 weight "obj.coal" count 5
        2 weight "obj.wilderness_fishing_bait" count 10..24

        41 weight SharedDropTables.herb
        4 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 64 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        // Drops Need Manual (rate): Drop rate is improved within the Wilderness Slayer Cave.
        1 outOf 196 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 90 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
