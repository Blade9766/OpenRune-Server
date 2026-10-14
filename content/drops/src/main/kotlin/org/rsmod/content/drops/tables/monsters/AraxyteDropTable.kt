package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.isOnSlayerTaskFor
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val araxyteDropTableLevel96: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Araxyte Level 96 drops",
    npcs = npcs("npc.araxyte_normal", "npc.araxyte_normal_bigwander"),
    mainTable = rsPlayerWeightedTable(total = 127) {
        name("Araxyte Level 96 drops")
        5 weight "obj.adamant_longsword" count 1
        5 weight "obj.adamant_battleaxe" count 1
        3 weight "obj.rune_dagger" count 1
        2 weight "obj.rune_med_helm" count 1
        2 weight "obj.rune_platelegs" count 1
        10 weight "obj.airrune" count 120..140
        10 weight "obj.waterrune" count 120..140
        10 weight "obj.earthrune" count 120..140
        10 weight "obj.firerune" count 120..140
        5 weight "obj.cosmicrune" count 7..12
        5 weight "obj.chaosrune" count 10..15
        5 weight "obj.naturerune" count 15..20
        5 weight "obj.deathrune" count 20..25
        5 weight "obj.lawrune" count 12..15
        5 weight "obj.bloodrune" count 15..18
        5 weight "obj.soulrune" count 9..12
        5 weight "obj.araxyte_venom_sack" count 2
        10 weight "obj.coins" count 800..1200
        1 outOf 2000 separate "obj.poh_araxyte_head" count 1

        10 weight SharedDropTables.herb
        10 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 4000 weight "obj.aranea_boots" count 1 killCondition { player, npc, _ -> !player.isOnSlayerTaskFor(npc) }
        1 outOf 1600 weight "obj.aranea_boots" count 1 killCondition { player, npc, _ -> player.isOnSlayerTaskFor(npc) }
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
    },
)

@field:RegisterDropTable
@JvmField
public val araxyteDropTableLevel146: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Araxyte Level 146 drops",
    npcs = npcs("npc.araxyte_large", "npc.araxyte_large_bigwander"),
    mainTable = rsPlayerWeightedTable(total = 127) {
        name("Araxyte Level 146 drops")
        5 weight "obj.adamant_longsword" count 2
        5 weight "obj.adamant_battleaxe" count 2
        3 weight "obj.rune_dagger" count 2
        2 weight "obj.rune_med_helm" count 2
        2 weight "obj.rune_platelegs" count 2
        10 weight "obj.airrune" count 240..280
        10 weight "obj.waterrune" count 240..280
        10 weight "obj.earthrune" count 240..280
        10 weight "obj.firerune" count 240..280
        5 weight "obj.cosmicrune" count 14..24
        5 weight "obj.chaosrune" count 20..30
        5 weight "obj.naturerune" count 30..40
        5 weight "obj.deathrune" count 40..50
        5 weight "obj.lawrune" count 24..30
        5 weight "obj.bloodrune" count 30..36
        5 weight "obj.soulrune" count 18..24
        5 weight "obj.araxyte_venom_sack" count 4
        10 weight "obj.coins" count 1600..2400
        1 outOf 2000 separate "obj.poh_araxyte_head" count 1

        10 weight SharedDropTables.herb
        10 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 2000 weight "obj.aranea_boots" count 1 killCondition { player, npc, _ -> !player.isOnSlayerTaskFor(npc) }
        1 outOf 800 weight "obj.aranea_boots" count 1 killCondition { player, npc, _ -> player.isOnSlayerTaskFor(npc) }
        1 outOf 81 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
    },
)
