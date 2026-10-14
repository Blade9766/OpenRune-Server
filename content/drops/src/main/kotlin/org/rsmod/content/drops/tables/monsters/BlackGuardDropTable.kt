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
public val blackGuardDropTableLevel25: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Black Guard Drops (level 25)",
    npcs = npcs("npc.fai_black_guard_01", "npc.fai_black_guard_02", "npc.fai_black_guard_03", "npc.fai_black_guard_04"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Black Guard Drops (level 25)")
        13 weight "obj.bronze_pickaxe" count 1
        4 weight "obj.bronze_med_helm" count 1
        2 weight "obj.bronze_battleaxe" count 1
        1 weight "obj.iron_battleaxe" count 1
        7 weight "obj.bolt" count (2..12) condition { player ->
            // Drops Need Manual: Only dropped in members worlds.
             true
        }
        4 weight "obj.chaosrune" count 2
        4 weight "obj.naturerune" count 2
        20 weight "obj.coins" count 4
        15 weight "obj.coins" count 10
        2 weight "obj.coins" count 30
        23 weight ringNothing()
        10 weight "obj.hammer" count 1
        7 weight "obj.bronze_bar" count 1
        4 weight "obj.iron_ore" count 1
        3 weight "obj.tin_ore" count 1
        3 weight "obj.copper_ore" count 1
        3 weight "obj.iron_bar" count 1
        2 weight "obj.coal" count 1

        1 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 100 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)
