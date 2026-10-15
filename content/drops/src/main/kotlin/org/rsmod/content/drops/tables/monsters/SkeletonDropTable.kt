package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.canReceiveChampionScroll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val skeletonDropTablePlain: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Skeleton Drops (Plain)",
    npcs = npcs("npc.crypt_of_tonali_skeleton_1", "npc.crypt_of_tonali_skeleton_2", "npc.crypt_of_tonali_skeleton_3", "npc.giantskeleton2", "npc.skeleton_armed2", "npc.skeleton_armed3", "npc.skeleton_armed4", "npc.skeleton_armed5", "npc.skeleton_unagressive2", "npc.skeleton_unagressive3", "npc.skeleton_unarmed2", "npc.skeleton_unarmed3", "npc.skeleton_unarmed4"),
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 6 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 5000 weight "obj.champions_challenge_skeleton" count 1 condition { player ->
            player.canReceiveChampionScroll("skeleton")
        }
        1 outOf 301 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 1144 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)

@field:RegisterDropTable
@JvmField
public val skeletonDropTableUnarmed: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Skeleton Drops (Unarmed)",
    npcs = npcs("npc.skeleton_unagressive", "npc.skeleton_unarmed"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Skeleton Drops (Unarmed)")
        7 weight "obj.bronze_arrow" count 2
        4 weight "obj.bronze_arrow" count 5
        4 weight "obj.iron_arrow" count 1
        2 weight "obj.airrune" count 15
        2 weight "obj.earthrune" count 3
        2 weight "obj.firerune" count 2
        2 weight "obj.chaosrune" count 3
        1 weight "obj.naturerune" count 3
        1 weight "obj.steel_arrow" count 1
        18 weight "obj.coins" count 2
        15 weight "obj.coins" count 12
        7 weight "obj.coins" count 4
        4 weight "obj.coins" count 16
        4 weight "obj.coins" count 25
        4 weight "obj.coins" count 33
        1 weight "obj.coins" count 48
        18 weight ringNothing()
        6 weight "obj.iron_dagger" count 1
        2 weight "obj.fire_talisman" count 1
        1 weight "obj.iron_ore" count 1
        1 weight "obj.grain" count 1

        21 weight SharedDropTables.herb
        1 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 14 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 5000 weight "obj.champions_challenge_skeleton" count 1 condition { player ->
            player.canReceiveChampionScroll("skeleton")
        }
        1 outOf 100 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
        1 outOf 301 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 1109 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)

@field:RegisterDropTable
@JvmField
public val skeletonDropTableArmed: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Skeleton Drops (Armed)",
    npcs = npcs("npc.giantskeleton", "npc.skeleton_armed"),
    mainTable = rsPlayerWeightedTable(total = 148) {
        name("Skeleton Drops (Armed)")
        6 weight "obj.iron_med_helm" count 1
        4 weight "obj.iron_sword" count 1
        2 weight "obj.iron_axe" count 1
        1 weight "obj.iron_scimitar" count 1
        3 weight "obj.airrune" count 1
        3 weight "obj.waterrune" count 9
        3 weight "obj.chaosrune" count 5
        2 weight "obj.iron_arrow" count 12
        2 weight "obj.lawrune" count 2
        1 weight "obj.cosmicrune" count 2
        44 weight "obj.coins" count 10
        25 weight "obj.coins" count 5
        8 weight "obj.coins" count 25
        4 weight "obj.coins" count 45
        3 weight "obj.coins" count 65
        2 weight "obj.coins" count 1
        8 weight ringNothing()
        5 weight "obj.bronze_bar" count 1

        20 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 14 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 5000 weight "obj.champions_challenge_skeleton" count 1 condition { player ->
            player.canReceiveChampionScroll("skeleton")
        }
        1 outOf 100 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
        1 outOf 301 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 1144 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
