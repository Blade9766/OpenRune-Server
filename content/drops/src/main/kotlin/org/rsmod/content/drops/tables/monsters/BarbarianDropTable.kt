package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val barbarianDropTableLevel8910: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Barbarian Level 8, 9, and 10 drops",
    npcs = npcs("npc.barbarianwoman_varrock", "npc.fai_barbarian_10", "npc.fai_barbarian_11", "npc.fai_barbarian_12", "npc.fai_barbarian_13", "npc.fai_barbarian_2", "npc.fai_barbarian_5", "npc.fai_barbarian_6", "npc.fai_barbarian_7", "npc.fai_barbarian_female_2", "npc.fai_barbarian_female_3", "npc.fai_barbarian_female_4"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Barbarian Level 8, 9, and 10 drops")
        6 weight "obj.bronze_axe" count 1
        4 weight "obj.plainstaff" count 1
        1 weight "obj.iron_mace" count 1
        4 weight "obj.chaosrune" count 2
        3 weight "obj.bronze_arrow" count 15
        3 weight "obj.earthrune" count 2
        2 weight "obj.firerune" count 5
        2 weight "obj.mindrune" count 5
        1 weight "obj.lawrune" count 2
        42 weight "obj.coins" count 5
        9 weight "obj.coins" count 8
        5 weight "obj.coins" count 17
        3 weight "obj.coins" count 27
        36 weight ringNothing()
        1 weight "obj.tin_ore" count 1
        1 weight "obj.fur" count 1
        1 weight "obj.beer" count 1
        1 weight "obj.cooked_meat" count 1
        1 weight "obj.flier" count 1
        1 weight "obj.ring_mould" count 1

        1 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 75 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)

@field:RegisterDropTable
@JvmField
public val barbarianDropTableLevel1517: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Barbarian Level 15 and 17 drops",
    npcs = npcs("npc.fai_barbarian_1", "npc.fai_barbarian_14", "npc.fai_barbarian_3", "npc.fai_barbarian_4", "npc.fai_barbarian_8", "npc.fai_barbarian_female_1"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Barbarian Level 15 and 17 drops")
        6 weight "obj.iron_axe" count 1
        4 weight "obj.bronze_battleaxe" count 1
        1 weight "obj.iron_mace" count 1
        4 weight "obj.bronze_arrow" count 10
        4 weight "obj.chaosrune" count 3
        3 weight "obj.iron_arrow" count 8
        3 weight "obj.earthrune" count 5
        2 weight "obj.mindrune" count 10
        2 weight "obj.firerune" count 8
        1 weight "obj.lawrune" count 2
        42 weight "obj.coins" count 8
        9 weight "obj.coins" count 12
        5 weight "obj.coins" count 25
        3 weight "obj.coins" count 32
        32 weight ringNothing()
        1 weight "obj.cooked_meat" count 1
        1 weight "obj.tin_ore" count 1
        1 weight "obj.amulet_mould" count 1
        1 weight "obj.beer" count 1
        1 weight "obj.fur" count 1
        1 weight "obj.flier" count 1

        1 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 55 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)

// Unmapped wiki items (Level 8, 9, and 10 drops):
//   - Key (medium)

// Unmapped wiki items (Level 15 and 17 drops):
//   - Key (medium)
