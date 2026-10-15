package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

/** Free-to-play world loot; rolled instead of the registered table when the killer is not a member. */
@JvmField
public val ankouDropTableFreeToPlayWorlds: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Ankou Free-to-play worlds drops",
    npcs = npcs("npc.sos_death_ankou", "npc.sos_death_ankou2", "npc.sos_death_ankou3", "npc.sos_death_ankou_strongholdcave", "npc.sos_death_ankou_strongholdcave_2", "npc.sos_death_ankou_strongholdcave_3", "npc.wilderness_ankou"),
    mainTable = rsPlayerWeightedTable(total = 124) {
        name("Ankou Free-to-play worlds drops")
        5 weight "obj.black_robe" count 1
        4 weight "obj.adamant_arrow" count 5..14
        20 weight "obj.lawrune" count 2
        5 weight "obj.cert_blankrune" count 15
        5 weight "obj.cert_mithril_ore" count 3..7
        33 weight "obj.coins" count 8 killCondition { player, npc, areaChecker ->
            !npc.coords.isInWilderness(areaChecker)
        }
        10 weight "obj.coins" count 5..204
        3 weight "obj.coins" count 10
        1 weight "obj.coins" count 5
        33 weight "obj.fishing_bait" count 60 killCondition { player, npc, areaChecker ->
            npc.coords.isInWilderness(areaChecker)
        }
        3 weight "obj.sos_half_skull2" count 1 killCondition { player, npc, areaChecker ->
            areaChecker.inArea("area.stronghold_of_security", npc.coords)
        }

        2 weight SharedDropTables.gem
    },
)

@JvmField
public val bryophytaDropTableFreeToPlayWorlds: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Bryophyta Free-to-play worlds drops",
    npcs = npcs("npc.gb_mossgiant"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Bryophyta Free-to-play worlds drops")
        5 weight "obj.black_sq_shield" count 1
        2 weight "obj.magic_staff" count 1
        2 weight "obj.steel_med_helm" count 1
        2 weight "obj.mithril_sword" count 1
        1 weight "obj.steel_kiteshield" count 1
        4 weight "obj.lawrune" count 3
        3 weight "obj.airrune" count 18
        3 weight "obj.earthrune" count 27
        3 weight "obj.chaosrune" count 7
        3 weight "obj.naturerune" count 6
        2 weight "obj.cosmicrune" count 3
        2 weight "obj.iron_arrow" count 15
        1 weight "obj.steel_arrow" count 30
        1 weight "obj.deathrune" count 3
        5 weight "obj.coins" count 10
        35 weight "obj.coins" count 5
        19 weight "obj.coins" count 37
        11 weight "obj.coins" count 2
        10 weight "obj.coins" count 119
        2 weight "obj.coins" count 300
        6 weight "obj.steel_bar" count 1
        1 weight "obj.coal" count 1
        1 weight "obj.spinach_roll" count 1

        4 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 45 weight "obj.trail_clue_beginner" count 1
        1 outOf 16 weight "obj.mossy_key" count 1
    },
)

@JvmField
public val farmerDropTableFreeToPlayWorlds: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Farmer Free-to-play worlds drops",
    npcs = npcs("npc.farmer1", "npc.farmer1_f", "npc.farmer2", "npc.farmer2_f", "npc.farmer3", "npc.farmer3_f", "npc.farmer4"),
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Farmer Free-to-play worlds drops")
        2 weight "obj.bronze_med_helm" count 1
        1 weight "obj.iron_dagger" count 1
        3 weight "obj.bronze_arrow" count 7
        2 weight "obj.earthrune" count 4
        2 weight "obj.firerune" count 6
        2 weight "obj.mindrune" count 9
        1 weight "obj.chaosrune" count 2
        38 weight "obj.coins" count 3
        23 weight "obj.coins" count 10
        9 weight "obj.coins" count 5
        4 weight "obj.coins" count 15
        1 weight "obj.coins" count 25
        30 weight ringNothing()
        5 weight "obj.fishing_bait" count 1
        2 weight "obj.copper_ore" count 1
        2 weight "obj.earth_talisman" count 1
        1 weight "obj.cabbage" count 1
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 90 weight "obj.trail_clue_beginner" count 1
    },
)

// Unknown wiki drop rates (text rarity — need data collection):
//   - Energy potion(3) [main/Unknown]

@JvmField
public val oborDropTableFreeToPlayWorlds: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Obor Free-to-play worlds drops",
    npcs = npcs("npc.hillgiant_boss"),
    mainTable = rsPlayerWeightedTable(total = 135) {
        name("Obor Free-to-play worlds drops")
        5 weight "obj.iron_full_helm" count 1
        4 weight "obj.iron_dagger" count 1
        3 weight "obj.iron_kiteshield" count 1
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
        18 weight "obj.coins" count 5
        14 weight "obj.coins" count 38
        10 weight "obj.coins" count 52
        8 weight "obj.coins" count 15
        7 weight "obj.coins" count 10
        6 weight "obj.coins" count 8
        2 weight "obj.coins" count 88
        11 weight "obj.limpwurt_root" count 1
        6 weight "obj.beer" count 1
        2 weight "obj.body_talisman" count 1
        1 weight ringNothing()
        1 outOf 16 separate "obj.hillgiant_boss_key" count 1

        3 weight SharedDropTables.gem
        // Pool padding (F2P drops removed / subtable access missing from wiki parse)
        8 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 50 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)
