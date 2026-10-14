package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val whiteKnightDropTableLevel36: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "White Knight Level 36 drops",
    npcs = npcs("npc.ds2_white_knight", "npc.ds2_white_knight_f", "npc.white_knight", "npc.white_knight_f"),
    mainTable = rsPlayerWeightedTable(total = 129) {
        name("White Knight Level 36 drops")
        2 weight "obj.iron_longsword" count 1
        1 weight "obj.steel_sword" count 1
        1 weight "obj.steel_med_helm" count 1
        11 weight "obj.mindrune" count 5
        4 weight "obj.naturerune" count 4
        3 weight "obj.bodyrune" count 9..13
        3 weight "obj.chaosrune" count 2
        3 weight "obj.waterrune" count 25..30
        2 weight "obj.mithril_arrow" count 5
        1 weight "obj.adamant_arrow" count 2
        1 weight "obj.bloodrune" count 2
        1 weight "obj.lawrune" count 2
        15 weight "obj.coins" count 48
        15 weight "obj.coins" count 15
        11 weight "obj.coins" count 8
        10 weight "obj.coins" count 45..53
        5 weight "obj.coins" count 1
        5 weight "obj.coins" count 2
        1 weight "obj.coins" count 120
        6 weight "obj.iron_bar" count 2
        2 weight "obj.iron_bar" count 1
        2 weight ringNothing()
        1 weight "obj.half_an_apple_pie" count 1
        1 weight "obj.iron_ore" count 1
        1 weight "obj.pot_flour" count 1

        5 weight SharedDropTables.herb
        1 weight SharedDropTables.gem
        15 weight SharedDropTables.seed
    },
)

@field:RegisterDropTable
@JvmField
public val whiteKnightDropTableLevel38: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "White Knight Level 38 drops",
    npcs = npcs("npc.white_knight_yellow_plumes", "npc.white_knight_yellow_plumes_f"),
    mainTable = rsPlayerWeightedTable(total = 129) {
        name("White Knight Level 38 drops")
        2 weight "obj.iron_longsword" count 1
        2 weight "obj.steel_med_helm" count 1
        1 weight "obj.steel_sword" count 1
        8 weight "obj.mindrune" count 7
        5 weight "obj.mithril_arrow" count 5
        4 weight "obj.naturerune" count 5
        3 weight "obj.bodyrune" count 10..14
        3 weight "obj.chaosrune" count 3
        3 weight "obj.waterrune" count 30..35
        3 weight "obj.adamant_arrow" count 2
        1 weight "obj.bloodrune" count 2
        1 weight "obj.lawrune" count 3
        13 weight "obj.coins" count 15
        11 weight "obj.coins" count 10
        10 weight "obj.coins" count 54
        10 weight "obj.coins" count 50..58
        6 weight "obj.coins" count 3
        1 weight "obj.coins" count 1
        1 weight "obj.coins" count 120
        8 weight "obj.iron_bar" count 2
        2 weight "obj.iron_bar" count 1
        1 weight "obj.half_an_apple_pie" count 1
        1 weight "obj.iron_ore" count 1
        1 weight "obj.pot_flour" count 1
        1 weight ringNothing()

        7 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
        18 weight SharedDropTables.seed
    },
)

@field:RegisterDropTable
@JvmField
public val whiteKnightDropTableLevel39: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "White Knight Level 39 drops",
    npcs = npcs("npc.white_knight_green_plumes", "npc.white_knight_green_plumes_f"),
    mainTable = rsPlayerWeightedTable(total = 129) {
        name("White Knight Level 39 drops")
        2 weight "obj.iron_longsword" count 1
        2 weight "obj.steel_med_helm" count 1
        2 weight "obj.steel_sword" count 1
        8 weight "obj.mindrune" count 10
        5 weight "obj.mithril_arrow" count 7
        4 weight "obj.naturerune" count 6
        3 weight "obj.bodyrune" count 15..19
        3 weight "obj.chaosrune" count 4
        3 weight "obj.waterrune" count 35..40
        3 weight "obj.adamant_arrow" count 4
        1 weight "obj.bloodrune" count 3
        1 weight "obj.lawrune" count 3
        13 weight "obj.coins" count 15..19
        11 weight "obj.coins" count 15
        10 weight "obj.coins" count 55..63
        10 weight "obj.coins" count 50..69
        6 weight "obj.coins" count 5
        2 weight "obj.coins" count 120
        1 weight "obj.coins" count 1
        1 weight ringNothing()
        6 weight "obj.iron_bar" count 2
        2 weight "obj.iron_bar" count 1
        1 weight "obj.half_an_apple_pie" count 1
        1 weight "obj.iron_ore" count 1
        1 weight "obj.pot_flour" count 1

        7 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
        18 weight SharedDropTables.seed
    },
)

@field:RegisterDropTable
@JvmField
public val whiteKnightDropTableLevel42: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "White Knight Level 42 drops",
    npcs = npcs("npc.white_knight_blue_plumes", "npc.white_knight_blue_plumes_f"),
    mainTable = rsPlayerWeightedTable(total = 129) {
        name("White Knight Level 42 drops")
        2 weight "obj.iron_longsword" count 1
        2 weight "obj.steel_med_helm" count 1
        2 weight "obj.steel_sword" count 1
        3 weight "obj.mindrune" count 12
        5 weight "obj.mithril_arrow" count 9
        4 weight "obj.naturerune" count 7
        3 weight "obj.bodyrune" count 15..24
        3 weight "obj.chaosrune" count 5
        3 weight "obj.waterrune" count 40..47
        3 weight "obj.adamant_arrow" count 5
        1 weight "obj.bloodrune" count 3
        1 weight "obj.lawrune" count 3
        13 weight "obj.coins" count 51..55
        11 weight "obj.coins" count 15
        10 weight "obj.coins" count 65..73
        6 weight "obj.coins" count 8
        5 weight "obj.coins" count 50..69
        3 weight "obj.coins" count 120
        1 weight "obj.coins" count 1
        1 weight ringNothing()
        5 weight "obj.iron_bar" count 2
        2 weight "obj.iron_bar" count 1
        1 weight "obj.half_an_apple_pie" count 1
        1 weight "obj.iron_ore" count 1
        1 weight "obj.pot_flour" count 1

        7 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
        28 weight SharedDropTables.seed
    },
)
