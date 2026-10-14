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
public val zombieDropTableLevel13: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Zombie Drops (level 13)",
    npcs = npcs("npc.zombie_unarmed", "npc.zombie_unarmed2", "npc.zombie_unarmed3", "npc.zombie_unarmed4", "npc.zombie_unarmed5", "npc.zombie_unarmed6", "npc.zombie_unarmed_city1", "npc.zombie_unarmed_city3", "npc.zombie_unarmed_city6", "npc.zombie_unarmed_sewer1", "npc.zombie_unarmed_sewer2", "npc.zombie_unarmed_sewer3", "npc.zombie_unarmed_sewer4"),
    mainTable = rsPlayerWeightedTable(total = 153) {
        name("Zombie Drops (level 13)")
        4 weight "obj.bronze_med_helm" count 1
        1 weight "obj.bronze_longsword" count 1
        1 weight "obj.iron_axe" count 1
        7 weight "obj.iron_arrow" count 5
        5 weight "obj.bodyrune" count 6
        5 weight "obj.mindrune" count 5
        4 weight "obj.airrune" count 13
        4 weight "obj.iron_arrow" count 8
        2 weight "obj.steel_arrow" count 5
        1 weight "obj.naturerune" count 6
        36 weight "obj.coins" count 10
        4 weight "obj.coins" count 4
        3 weight "obj.coins" count 18
        2 weight "obj.coins" count 13
        2 weight "obj.coins" count 28
        37 weight "obj.fishing_bait" count 5
        8 weight ringNothing()
        2 weight "obj.copper_ore" count 1

        25 weight SharedDropTables.herb
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 4 weight "obj.rag_zombie_bone" count 1 condition {
            player -> player.isOnQuest("quest_ragandboneman2")
        }
        1 outOf 5000 weight "obj.champions_challenge_zombie" count 1
    },
)

@field:RegisterDropTable
@JvmField
public val zombieDropTableLevel24: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Zombie Drops (level 24)",
    npcs = npcs("npc.zombie_armed", "npc.zombie_armed2", "npc.zombie_armed3", "npc.zombie_armed_city1", "npc.zombie_armed_city3", "npc.zombie_armed_sewer1", "npc.zombie_armed_sewer2", "npc.zombie_armed_sewer3", "npc.zombie_armed_sewer4"),
    mainTable = rsPlayerWeightedTable(total = 158) {
        name("Zombie Drops (level 24)")
        3 weight "obj.iron_mace" count 1
        2 weight "obj.iron_dagger" count 1
        1 weight "obj.bronze_kiteshield" count 1
        3 weight "obj.mithril_arrow" count 1
        2 weight "obj.bodyrune" count 3
        3 weight "obj.airrune" count 3
        1 weight "obj.chaosrune" count 4
        1 weight "obj.cosmicrune" count 2
        1 weight "obj.firerune" count 7
        40 weight "obj.coins" count 10
        21 weight "obj.coins" count 18
        8 weight "obj.coins" count 26
        6 weight "obj.coins" count 35
        2 weight "obj.coins" count 1
        26 weight "obj.fishing_bait" count 7
        3 weight ringNothing()
        2 weight "obj.tinderbox" count 1
        1 weight "obj.eye_of_newt" count 1
        1 weight "obj.tin_ore" count 1

        30 weight SharedDropTables.herb
        1 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 4 weight "obj.rag_zombie_bone" count 1 condition {
            player -> player.isOnQuest("quest_ragandboneman2")
        }
        1 outOf 5000 weight "obj.champions_challenge_zombie" count 1
    },
)
