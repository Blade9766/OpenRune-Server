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
        1 weight "obj.black_robe" count 1 condition { player ->
            // Drops Need Manual: Black robes are not dropped in the Forgotten Cemetery.
             true
        }
        10 weight "obj.deathrune" count 10
        6 weight "obj.bloodrune" count 11
        4 weight "obj.adamant_arrow" count 5..14
        7 weight "obj.lawrune" count 2
        3 weight "obj.bloodrune" count 5
        5 weight "obj.cert_blankrune_high" count 15
        2 weight "obj.cert_mithril_ore" count 3..7
        33 weight "obj.coins" count 8 condition { player ->
            // Drops Need Manual: The standalone eight coins do not drop in the Forgotten Cemetery.
             true
        }
        10 weight "obj.coins" count 5..204
        38 weight "obj.wilderness_fishing_bait" count 60 condition { player ->
            // Drops Need Manual: Dark fishing bait is only dropped in the Forgotten Cemetery.
             true
        }
        7 weight ringNothing()
        3 weight "obj.sos_half_skull2" count 1 condition { player ->
            // Drops Need Manual: Left skull halves are only dropped in the Stronghold of Security.
             true
        }
        2 weight "obj.bass" count 1
        2 weight "obj.weapon_poison" count 1
        1 weight "obj.bowl_mushroom_fried" count 1

        3 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            // Drops Need Manual: The elite clue scroll and casket are only dropped when completing an Treasure Trails/Full guide/Elite#Cryptic clues
             true
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1
        1 outOf 3 weight "obj.looting_bag" count 1 condition { player ->
            // Drops Need Manual: Looting bags are only dropped in the Forgotten Cemetery.
             true
        }
        onBuilder { brimstoneKeyRoll() }
        1 outOf 486 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 256 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
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
            // Drops Need Manual: This clue scroll and casket are only dropped when completing a Treasure Trails/Full guide/Elite#Cryptic clues
             true
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 243 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 128 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
    },
)
