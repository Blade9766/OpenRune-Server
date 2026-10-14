package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerPrerollTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.darkTotemPieceObj
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val hillGiantDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Hill Giant Drops",
    npcs = npcs("npc.avium_giant1", "npc.avium_giant2", "npc.avium_giant3", "npc.gh_giant1", "npc.gh_giant2", "npc.gh_giant3", "npc.giant", "npc.giant2", "npc.giant3", "npc.giant4", "npc.giant5", "npc.giant6", "npc.wilderness_hill_giant", "npc.wilderness_hill_giant2", "npc.wilderness_hill_giant3"),
    preRoll = rsPlayerPrerollTable {
        1 outOf 128 weight "obj.hillgiant_boss_key" count 1
    },
    mainTable = rsPlayerWeightedTable(total = 134) {
        name("Hill Giant Drops")
        4 weight "obj.iron_dagger" count 1
        5 weight "obj.iron_med_helm" count 1 killCondition { player, npc, areaChecker ->
            npc.type.internalName.startsWith("npc.gh_giant")
        }
        5 weight "obj.iron_full_helm" count 1 killCondition { player, npc, areaChecker ->
            !npc.type.internalName.startsWith("npc.gh_giant")
        }
        3 weight "obj.iron_kiteshield" count 1
        2 weight "obj.steel_scimitar" count 1
        2 weight "obj.steel_longsword" count 1
        6 weight "obj.iron_arrow" count 3
        3 weight "obj.firerune" count 15
        3 weight "obj.waterrune" count 7
        3 weight "obj.lawrune" count 2
        2 weight "obj.steel_arrow" count 10
        2 weight "obj.mindrune" count 3
        2 weight "obj.cosmicrune" count 2
        2 weight "obj.naturerune" count 6
        1 weight "obj.chaosrune" count 2
        1 weight "obj.deathrune" count 2
        14 weight "obj.coins" count 38
        10 weight "obj.coins" count 52
        8 weight "obj.coins" count 15
        6 weight "obj.coins" count 8
        2 weight "obj.coins" count 88
        11 weight "obj.limpwurt_root" count 1
        6 weight "obj.beer" count 1
        2 weight "obj.body_talisman" count 1
        1 weight ringNothing()

        7 weight SharedDropTables.herb
        3 weight SharedDropTables.gem
        18 weight SharedDropTables.seed
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 5 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 25 weight "obj.arceuus_corpse_giant" count 1
        1 outOf 400 weight "obj.dorgesh_construction_bone" count 1
        1 outOf 5000 weight "obj.champions_challenge_giant" count 1
        1 outOf 5013 weight "obj.dorgesh_construction_bone_curved" count 1
        1 outOf 50 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
        1 outOf 310 weight "obj.cata_shard" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords)
        }
        1 outOf 465 chance DropRollItem(
            "obj.cata_totem1",
            1,
            killCondition = { _, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords) },
            transformObj = { player -> player.darkTotemPieceObj() },
        )
        1 outOf 292 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 911 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
