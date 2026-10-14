package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

private val Player.openedMimicScrollCase by boolVarBit("varbit.scroll_case_mimic")

/*
 * The Mimic's loot depends on the casket tier that spawned it, so neither table is registered for the
 * npc: the Treasure Trails casket encounter rolls [mimicDropTable] for its tier. Weights are the wiki
 * first-attempt rates scaled to whole numbers (x23 so each 3rd age piece is an integer share).
 */

@JvmField
public val eliteMimicDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "The Mimic (Elite)",
    npcs = npcs("npc.trail_mimic_combat"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.plank_mahogany" count 1
        "obj.scroll_case_mimic" count 1 condition { player -> !player.openedMimicScrollCase }
    },
    mainTable = rsPlayerWeightedTable(total = 28750) {
        name("The Mimic (Elite)")
        690 weight "obj.ring_of_3rd_age" count 1
        5 weight "obj.trail_fighter_helm" count 1
        5 weight "obj.trail_silver_plate_chest" count 1
        5 weight "obj.trail_silver_plate_legs" count 1
        5 weight "obj.trail_silver_plate_skirt" count 1
        5 weight "obj.trail_fighter_shield" count 1
        5 weight "obj.trail_ranger_coif" count 1
        5 weight "obj.trail_ranger_torso" count 1
        5 weight "obj.trail_ranger_legs" count 1
        5 weight "obj.trail_ranger_vambraces" count 1
        5 weight "obj.trail_mage_hat" count 1
        5 weight "obj.trail_mage_torso" count 1
        5 weight "obj.trail_mage_legs" count 1
        5 weight "obj.trail_mage_amulet" count 1
        5 weight "obj.trail_fighter_sword" count 1
        5 weight "obj.trail_ranger_bow" count 1
        5 weight "obj.trail_mage_wand" count 1
        5 weight "obj.trail_third_cape" count 1
        5 weight "obj.3a_druidic_top" count 1
        5 weight "obj.3a_druidic_bottoms" count 1
        5 weight "obj.3a_druidic_cloak" count 1
        5 weight "obj.3a_druidic_staff" count 1
        5 weight "obj.3a_pickaxe" count 1
        5 weight "obj.3a_axe" count 1
        5589 weight "obj.deathrune" count 600
        5589 weight "obj.bloodrune" count 500
        5589 weight "obj.cert_unidentified_ranarr" count 25
        5589 weight "obj.cert_wine_of_zamorak" count 25
        5589 weight "obj.cert_raw_mantaray" count 15
    },
)

@JvmField
public val masterMimicDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "The Mimic (Master)",
    npcs = npcs("npc.trail_mimic_combat"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.plank_mahogany" count 1
        "obj.scroll_case_mimic" count 1 condition { player -> !player.openedMimicScrollCase }
    },
    mainTable = rsPlayerWeightedTable(total = 26220) {
        name("The Mimic (Master)")
        690 weight "obj.ring_of_3rd_age" count 1
        5 weight "obj.trail_fighter_helm" count 1
        5 weight "obj.trail_silver_plate_chest" count 1
        5 weight "obj.trail_silver_plate_legs" count 1
        5 weight "obj.trail_silver_plate_skirt" count 1
        5 weight "obj.trail_fighter_shield" count 1
        5 weight "obj.trail_ranger_coif" count 1
        5 weight "obj.trail_ranger_torso" count 1
        5 weight "obj.trail_ranger_legs" count 1
        5 weight "obj.trail_ranger_vambraces" count 1
        5 weight "obj.trail_mage_hat" count 1
        5 weight "obj.trail_mage_torso" count 1
        5 weight "obj.trail_mage_legs" count 1
        5 weight "obj.trail_mage_amulet" count 1
        5 weight "obj.trail_fighter_sword" count 1
        5 weight "obj.trail_ranger_bow" count 1
        5 weight "obj.trail_mage_wand" count 1
        5 weight "obj.trail_third_cape" count 1
        5 weight "obj.3a_druidic_top" count 1
        5 weight "obj.3a_druidic_bottoms" count 1
        5 weight "obj.3a_druidic_cloak" count 1
        5 weight "obj.3a_druidic_staff" count 1
        5 weight "obj.3a_pickaxe" count 1
        5 weight "obj.3a_axe" count 1
        5083 weight "obj.deathrune" count 600
        5083 weight "obj.bloodrune" count 500
        5083 weight "obj.cert_unidentified_ranarr" count 25
        5083 weight "obj.cert_wine_of_zamorak" count 25
        5083 weight "obj.cert_raw_mantaray" count 15
    },
)

public fun mimicDropTable(master: Boolean): RSDropTable<Player, DropRollItem> =
    if (master) masterMimicDropTable else eliteMimicDropTable
