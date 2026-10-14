package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.darkTotemPieceObj
import org.rsmod.content.drops.isOnTreasureTrailKillStep
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val ankouDropTableMembersWorlds: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Ankou Members' worlds drops",
    npcs = npcs("npc.kourend_ankou", "npc.wilderness_ankou"),
    mainTable = rsPlayerWeightedTable(total = 140) {
        name("Ankou Members' worlds drops")
        1 weight "obj.black_knife" count 1
        1 weight "obj.black_robe" count 1 killCondition { player, npc, areaChecker ->
            npc.type.internalName != "npc.wilderness_ankou"
        }
        10 weight "obj.deathrune" count 10
        6 weight "obj.bloodrune" count 11
        4 weight "obj.adamant_arrow" count 5..14
        7 weight "obj.lawrune" count 2
        3 weight "obj.bloodrune" count 5
        5 weight "obj.cert_blankrune_high" count 15
        2 weight "obj.cert_mithril_ore" count 3..7
        33 weight "obj.coins" count 8 killCondition { player, npc, areaChecker ->
            npc.type.internalName != "npc.wilderness_ankou"
        }
        10 weight "obj.coins" count 5..204
        38 weight "obj.wilderness_fishing_bait" count 60 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.wilderness_ankou"
        }
        7 weight ringNothing()
        3 weight "obj.sos_half_skull2" count 1 killCondition { player, npc, areaChecker ->
            areaChecker.inArea("area.stronghold_of_security", npc.coords)
        }
        2 weight "obj.bass" count 1
        2 weight "obj.weapon_poison" count 1
        1 weight "obj.bowl_mushroom_fried" count 1

        3 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            player.isOnTreasureTrailKillStep()
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1 condition { player -> player.isOnTreasureTrailKillStep() }
        1 outOf 3 weight "obj.looting_bag" count 1 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.wilderness_ankou"
        }
        onBuilder { brimstoneKeyRoll() }
        1 outOf 486 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 256 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 293 weight "obj.cata_shard" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords)
        }
        1 outOf 440 chance DropRollItem(
            "obj.cata_totem1",
            1,
            killCondition = { _, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords) },
            transformObj = { player -> player.darkTotemPieceObj() },
        )
        1 outOf 264 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 98 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)

@field:RegisterDropTable
@JvmField
public val ankouDropTableWildernessSlayerCave: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Ankou Wilderness Slayer Cave drops",
    npcs = npcs("npc.wild_cave_ankou"),
    mainTable = rsPlayerWeightedTable(total = 92) {
        name("Ankou Wilderness Slayer Cave drops")
        1 weight "obj.adamant_platelegs" count 1
        4 weight "obj.rune_arrow" count 5..14
        3 weight "obj.deathrune" count 15
        3 weight "obj.deathrune" count 17
        3 weight "obj.bloodrune" count 11
        2 weight "obj.bloodrune" count 8
        1 weight "obj.bloodrune" count 18
        57 weight "obj.wilderness_fishing_bait" count 60
        2 weight "obj.cert_mithril_ore" count 3..7
        10 outOf 95 separate "obj.coins" count 500..999

        3 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
        11 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            player.isOnTreasureTrailKillStep()
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1 condition { player -> player.isOnTreasureTrailKillStep() }
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 243 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 128 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 240 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 82 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
