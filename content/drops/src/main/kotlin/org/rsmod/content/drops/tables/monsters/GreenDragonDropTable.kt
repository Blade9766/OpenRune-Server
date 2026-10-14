package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val greenDragonDropTableLevel79: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Green dragon Level 79 drops",
    npcs = npcs("npc.ds2_green_dragon", "npc.ds2_green_dragon_cutscene_flying", "npc.ds2_green_dragon_flying", "npc.green_dragon", "npc.green_dragon2", "npc.green_dragon3", "npc.green_dragon4", "npc.green_dragon5"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.dragonhide_green" count 1
    },
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Green dragon Level 79 drops")
        4 weight "obj.steel_platelegs" count 1
        3 weight "obj.steel_battleaxe" count 1
        3 weight "obj.mithril_axe" count 1
        2 weight "obj.mithril_spear" count 1
        1 weight "obj.mithril_kiteshield" count 1
        1 weight "obj.adamant_full_helm" count 1
        1 weight "obj.rune_dagger" count 1
        8 weight "obj.waterrune" count 75
        1 weight "obj.firerune" count 37
        5 weight "obj.naturerune" count 15
        3 weight "obj.lawrune" count 3
        29 weight "obj.coins" count 44
        25 weight "obj.coins" count 132
        10 weight "obj.coins" count 200
        5 weight "obj.coins" count 11
        1 weight "obj.coins" count 440
        3 weight "obj.bass" count 1
        3 weight "obj.adamantite_ore" count 1

        15 weight SharedDropTables.herb
        5 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            // Drops Need Manual: The elite clue scroll and casket are only dropped when completing an Treasure Trails/Full guide/Elite#Cryptic clues
             true
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 35 weight "obj.arceuus_corpse_dragon" count 1
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 64 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 260 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 100 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)

@field:RegisterDropTable
@JvmField
public val greenDragonDropTableWildernessSlayerCave: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Green dragon Wilderness Slayer Cave drops",
    npcs = npcs("npc.wild_cave_green_dragon", "npc.wild_cave_green_dragon2", "npc.wild_cave_green_dragon3"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.dragonhide_green" count 1
    },
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Green dragon Wilderness Slayer Cave drops")
        5 weight "obj.mithril_2h_sword" count 1
        2 weight "obj.mithril_platelegs" count 1
        1 weight "obj.mithril_battleaxe" count 1
        1 weight "obj.adamant_dagger" count 1
        1 weight "obj.adamant_sword" count 1
        1 weight "obj.adamant_full_helm" count 1
        1 weight "obj.adamant_kiteshield" count 1
        3 weight "obj.lawrune" count 3..10
        1 weight "obj.naturerune" count 10
        1 weight "obj.deathrune" count 15
        1 weight "obj.bloodrune" count 5
        29 weight "obj.coins" count 100..199
        25 weight "obj.coins" count 250..499
        14 weight "obj.coins" count 500..749
        6 weight "obj.coins" count 110
        6 weight "obj.coins" count 500..999
        7 weight "obj.monkfish" count 1
        3 weight "obj.cert_mithril_ore" count 2

        15 weight SharedDropTables.herb
        5 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            // Drops Need Manual: The elite clue scroll and casket are only dropped when completing an Treasure Trails/Full guide/Elite#Cryptic clues
             true
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 35 weight "obj.arceuus_corpse_dragon" count 1
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 64 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 240 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 84 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
