package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.isOnQuest
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val zombieTarnsLairDropTableLevel4056: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Zombie (Tarn's Lair) Level 40–56 drops",
    npcs = npcs("npc.lotr_zombie1_lvl_44", "npc.lotr_zombie1_lvl_53", "npc.lotr_zombie2_lvl_44", "npc.lotr_zombie2_lvl_53", "npc.lotr_zombie3_lvl_53"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Zombie (Tarn's Lair) Level 40–56 drops")
        8 weight "obj.steel_platelegs" count 1
        5 weight "obj.bronze_battleaxe" count 1
        5 weight "obj.steel_knife" count 1
        3 weight "obj.bronze_2h_sword" count 1
        3 weight "obj.staff_of_air" count 1
        2 weight "obj.iron_warhammer" count 1
        2 weight "obj.steel_mace" count 1
        1 weight "obj.steel_sq_shield" count 1
        1 weight "obj.mithril_sword" count 1
        5 weight "obj.waterrune" count 4
        5 weight "obj.mudrune" count 3
        3 weight "obj.mcannonball" count 3
        3 weight "obj.steel_arrow" count 7
        3 weight "obj.firerune" count 12
        3 weight "obj.lawrune" count 2
        2 weight "obj.earthrune" count 8
        2 weight "obj.chaosrune" count 6
        5 weight "obj.coins" count 19
        5 weight "obj.coins" count 33
        5 weight "obj.coins" count 60
        3 weight "obj.coins" count 86
        2 weight "obj.coins" count 2
        2 weight "obj.coins" count 37
        2 weight "obj.coins" count 54
        1 weight "obj.coins" count 126
        5 weight "obj.bowl_water" count 1
        5 weight "obj.1dosefisherspotion" count 1
        5 weight "obj.pot_flour" count 1
        5 weight "obj.potlid" count 1
        5 weight "obj.nails" count 1
        3 weight "obj.iron_ore" count 2
        3 weight "obj.plantpot_empty" count 1
        3 weight ringNothing()
        2 weight "obj.gold_ore" count 3
        2 weight "obj.plank_oak" count 1
        2 weight "obj.rooftile" count 1
        2 weight "obj.unholy_symbol_mould" count 1
        1 weight "obj.earth_orb" count 1

        3 weight SharedDropTables.herb
        1 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 4 weight "obj.rag_zombie_bone" count 1 condition {
            player -> player.isOnQuest("quest_ragandboneman2")
        }
        1 outOf 5000 weight "obj.champions_challenge_zombie" count 1
    },
)
