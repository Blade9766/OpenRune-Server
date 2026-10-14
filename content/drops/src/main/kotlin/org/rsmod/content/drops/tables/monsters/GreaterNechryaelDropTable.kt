package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.darkTotemPieceObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val greaterNechryaelDropTableRegular: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Greater Nechryael Regular",
    npcs = npcs("npc.kourend_nechryael"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Greater Nechryael Regular")
        7 weight "obj.adamant_kiteshield" count 1
        7 weight "obj.rune_axe" count 1
        7 weight "obj.rune_sq_shield" count 1
        5 weight "obj.adamant_battleaxe" count 1
        4 weight "obj.rune_med_helm" count 1
        3 weight "obj.rune_full_helm" count 1
        2 weight "obj.mystic_air_staff" count 1
        1 weight "obj.rune_armoured_boots" count 1
        1 weight "obj.rune_chainbody" count 1
        12 weight "obj.deathrune" count 23
        10 weight "obj.bloodrune" count 20
        10 weight "obj.chaosrune" count 50
        6 weight "obj.airrune" count 150
        5 weight "obj.soulrune" count 25
        10 weight "obj.lobster" count 1
        8 weight "obj.coins" count 2000..2500
        7 weight "obj.cert_gold_bar" count 5
        6 weight "obj.tuna" count 2
        2 weight "obj.cert_wine_of_zamorak" count 3

        7 weight SharedDropTables.herb
        3 weight SharedDropTables.gem
        5 weight SharedDropTables.rareSeed
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 24 weight "obj.prif_crystal_shard" count (3..5) killCondition { player, npc, areaChecker ->
            areaChecker.inArea("area.iorwerth_dungeon", npc.coords)
        }
        onBuilder { brimstoneKeyRoll(konarTaskBonus = true) }
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 196 weight "obj.cata_shard" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords)
        }
        1 outOf 295 chance DropRollItem(
            "obj.cata_totem1",
            1,
            killCondition = { _, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords) },
            transformObj = { player -> player.darkTotemPieceObj() },
        )
    },
)
