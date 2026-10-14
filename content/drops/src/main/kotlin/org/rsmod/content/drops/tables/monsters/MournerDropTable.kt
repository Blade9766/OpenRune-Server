package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.isOnQuest
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val mournerDropTableLevel11: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Mourner Level 11 drops",
    npcs = npcs("npc.mourning_overpass_mourner_vis"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.mourning_mourner_cloak" count 1 condition { player ->
            // Drops Need Manual: The mourner pieces and mourner letters are only dropped by the mourner's near the gates of Arandar.
             true
        }
        "obj.mourning_mourner_boots" count 1
        "obj.mourning_bloody_mourner_top" count 1 condition {
            player -> player.isOnQuest("quest_mourningsendparti")
        }
        "obj.mourning_mourner_gloves" count 1
        "obj.gasmask" count 1
        "obj.mourning_ripped_mourner_legs" count 1 condition { player ->
            // Drops Need Manual: Mourner trousers will be ripped and need to be repaired.
             true
        }
        "obj.mourning_mourner_message" count 1
    },
)

@field:RegisterDropTable
@JvmField
public val mournerDropTableLevel108: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Mourner Level 108 drops",
    npcs = npcs("npc.mourner_hideout_guard_vis"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.mourning_mourner_cloak" count 1 condition { player ->
            // Drops Need Manual: The mourner pieces and mourner letters are only dropped by the mourners inside the Mourner Headquarters.
             true
        }
        "obj.mourning_mourner_boots" count 1
        "obj.mourning_bloody_mourner_top" count 1 condition {
            player -> player.isOnQuest("quest_mourningsendparti")
        }
        "obj.mourning_mourner_top" count 1
        "obj.mourning_mourner_gloves" count 1
        "obj.gasmask" count 1
        "obj.mourning_mourner_legs" count 1
        "obj.mourning_mourner_message" count 1
    },
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Mourner Level 108 drops")
        3 weight "obj.dragonhide_chaps" count 1
        4 weight "obj.dragonhide_body" count 1
        2 weight "obj.mithril_spear" count 1
        1 weight "obj.mithril_kiteshield" count 1
        1 weight "obj.adamant_full_helm" count 1
        1 weight "obj.rune_dagger" count 1
        8 weight "obj.waterrune" count 70
        5 weight "obj.naturerune" count 12
        3 weight "obj.lawrune" count 2
        1 weight "obj.firerune" count 37
        5 weight "obj.coins" count 20
        29 weight "obj.coins" count 44
        25 weight "obj.coins" count 132
        10 weight "obj.coins" count 180
        1 weight "obj.coins" count 440
        3 weight "obj.bass" count 1
        3 weight "obj.shark" count 1
        3 weight "obj.adamantite_ore" count 1

        15 weight SharedDropTables.herb
        5 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
    },
)
