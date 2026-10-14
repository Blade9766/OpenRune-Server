package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.darkTotemPieceObj
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val lesserDemonDropTableLevel828794: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Lesser demon Level 82, 87, and 94 drops",
    npcs = npcs("npc.kourend_lesser_demon1", "npc.kourend_lesser_demon2", "npc.lesser_demon", "npc.lesser_demon2", "npc.lesser_demon3", "npc.lesser_demon4", "npc.lesser_demon5", "npc.lesser_demon_slayercave_1", "npc.lesser_demon_slayercave_2", "npc.lesser_demon_slayercave_3"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Lesser demon Level 82, 87, and 94 drops")
        4 weight "obj.steel_full_helm" count 1
        4 weight "obj.steel_axe" count 1
        3 weight "obj.steel_scimitar" count 1
        1 weight "obj.mithril_sq_shield" count 1
        1 weight "obj.mithril_chainbody" count 1
        1 weight "obj.rune_med_helm" count 1
        8 weight "obj.firerune" count 60
        5 weight "obj.chaosrune" count 12
        3 weight "obj.deathrune" count 3
        1 weight "obj.firerune" count 30
        40 weight "obj.coins" count 120
        29 weight "obj.coins" count 40
        10 weight "obj.coins" count 200
        7 weight "obj.coins" count 10
        1 weight "obj.coins" count 450
        3 weight "obj.jug_wine" count 1
        2 weight "obj.gold_ore" count 1

        1 weight SharedDropTables.herb
        4 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 50 weight "obj.arceuus_corpse_demon" count 1
        1 outOf 5000 weight "obj.champions_challenge_lesserdemon" count 1 condition { player ->
            // Drops Need Manual: Only dropped if access to Champions' Guild is available, and the Lesser Demon Champion hasn't been previously killed in the Champions' Challenge.
             true
        }
        1 outOf 123 chance DropRollItem("obj.yama_special_contract", 1, condition = { player ->
            // Drops Need Manual: 5x more likely to drop when the corresponding sigil is active and the kill condition is met.
             true
        }, killCondition = { player, npc, areaChecker -> areaChecker.inArea("area.chasm_of_fire", npc.coords) })
        1 outOf 123 chance DropRollItem("obj.yama_2h_contract", 1, condition = { player ->
            // Drops Need Manual: 2x more likely to drop when the corresponding sigil is active and the kill condition is met.
             true
        }, killCondition = { player, npc, areaChecker -> areaChecker.inArea("area.chasm_of_fire", npc.coords) })
        1 outOf 123 weight "obj.yama_heavyranged_contract" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.chasm_of_fire", npc.coords)
        }
        1 outOf 123 weight "obj.yama_binding_contract" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.chasm_of_fire", npc.coords)
        }
        1 outOf 123 weight "obj.yama_spell_contract" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.chasm_of_fire", npc.coords)
        }
        1 outOf 275 weight "obj.cata_shard" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords)
        }
        1 outOf 413 chance DropRollItem(
            "obj.cata_totem1",
            1,
            killCondition = { _, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords) },
            transformObj = { player -> player.darkTotemPieceObj() },
        )
        1 outOf 256 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 98 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)

@field:RegisterDropTable
@JvmField
public val lesserDemonDropTableWildernessSlayerCave: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Lesser demon Wilderness Slayer Cave drops",
    npcs = npcs("npc.wild_cave_lesser_demon", "npc.wild_cave_lesser_demon2", "npc.wild_cave_lesser_demon3"),
    mainTable = rsPlayerWeightedTable(total = 39) {
        name("Lesser demon Wilderness Slayer Cave drops")
        4 weight "obj.black_full_helm" count 1
        4 weight "obj.mithril_axe" count 1
        1 weight "obj.adamant_dagger" count 1
        1 weight "obj.adamant_chainbody" count 1
        1 weight "obj.adamant_platebody" count 1
        1 weight "obj.rune_med_helm" count 1
        5 weight "obj.chaosrune" count 20
        3 weight "obj.deathrune" count 10
        1 weight "obj.deathrune" count 30
        1 weight "obj.bloodrune" count 10
        6 weight "obj.coins" count 100
        3 weight "obj.jug_wine" count 1
        2 weight "obj.cert_gold_ore" count 5

        1 weight SharedDropTables.herb
        5 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 50 weight "obj.arceuus_corpse_demon" count 1
        1 outOf 5000 weight "obj.champions_challenge_lesserdemon" count 1 condition { player ->
            // Drops Need Manual: Only dropped if access to Champions' Guild is available, and the Lesser Demon Champion hasn't been previously killed in the Champions' Challenge.
             true
        }
        1 outOf 232 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 83 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
