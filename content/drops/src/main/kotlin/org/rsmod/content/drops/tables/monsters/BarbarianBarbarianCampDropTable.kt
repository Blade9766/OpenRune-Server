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
public val barbarianBarbarianCampDropTableLevel910: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Barbarian (Barbarian Camp) Level 9 and 10 drops",
    npcs = npcs("npc.akd_barbarian_1", "npc.akd_barbarian_2", "npc.akd_barbarian_4", "npc.akd_barbarian_5"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Barbarian (Barbarian Camp) Level 9 and 10 drops")
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
        1 outOf 121 weight "obj.trail_clue_easy_simple001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_easy_simple001")
        }
    },
)

@field:RegisterDropTable
@JvmField
public val barbarianBarbarianCampDropTableLevel15: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Barbarian (Barbarian Camp) Level 15 drops",
    npcs = npcs("npc.akd_barbarian_3"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Barbarian (Barbarian Camp) Level 15 drops")
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
        33 weight ringNothing()
        1 weight "obj.cooked_meat" count 1
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
        1 outOf 121 weight "obj.trail_clue_easy_simple001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_easy_simple001")
        }
    },
)

// Unmapped wiki items (Level 9 and 10 drops):
//   - Key (medium)

// Unmapped wiki items (Level 15 drops):
//   - Key (medium)
