package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val darkWizardDropTableLevel711: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Dark wizard Level 7 and 11 drops",
    npcs = npcs("npc.fai_dark_wizard_young_1", "npc.fai_dark_wizard_young_2", "npc.qip_ds_young_dark_wizard1", "npc.qip_ds_young_dark_wizard2", "npc.young_dark_wizard"),
    mainTable = rsPlayerWeightedTable(total = 130) {
        name("Dark wizard Level 7 and 11 drops")
        8 weight "obj.plainstaff" count 1
        6 weight "obj.blackwizhat" count 1
        3 weight "obj.black_robe" count 1
        4 weight "obj.earthrune" count 36
        3 weight "obj.airrune" count 10
        3 weight "obj.waterrune" count 10
        3 weight "obj.earthrune" count 10
        3 weight "obj.firerune" count 10
        2 weight "obj.airrune" count 18
        2 weight "obj.waterrune" count 18
        2 weight "obj.earthrune" count 18
        2 weight "obj.firerune" count 18
        7 weight "obj.naturerune" count 4
        6 weight "obj.chaosrune" count 5
        3 weight "obj.mindrune" count 10
        3 weight "obj.bodyrune" count 10
        2 weight "obj.mindrune" count 18
        2 weight "obj.bodyrune" count 18
        2 weight "obj.bloodrune" count 2
        1 weight "obj.cosmicrune" count 2
        1 weight "obj.lawrune" count 3
        17 weight "obj.coins" count 1
        16 weight "obj.coins" count 2
        7 weight "obj.coins" count 4
        3 weight "obj.coins" count 29
        1 weight "obj.coins" count 30
        16 weight ringNothing()
        1 weight "obj.water_talisman" count 1
        1 weight "obj.fire_talisman" count 1
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 15 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 50 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)

@field:RegisterDropTable
@JvmField
public val darkWizardDropTableLevel202223: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Dark wizard Level 20, 22 and 23 drops",
    npcs = npcs("npc.bearded_dark_wizard", "npc.fai_dark_wizard_old_1", "npc.fai_dark_wizard_old_2", "npc.qip_ds_young_dark_wizard3", "npc.qip_ds_young_dark_wizard4"),
    mainTable = rsPlayerWeightedTable(total = 130) {
        name("Dark wizard Level 20, 22 and 23 drops")
        4 weight "obj.plainstaff" count 1
        6 weight "obj.blackwizhat" count 1
        3 weight "obj.black_robe" count 1
        4 weight "obj.earthrune" count 36
        3 weight "obj.airrune" count 10
        3 weight "obj.waterrune" count 10
        3 weight "obj.earthrune" count 10
        3 weight "obj.firerune" count 10
        2 weight "obj.airrune" count 18
        2 weight "obj.waterrune" count 18
        2 weight "obj.earthrune" count 18
        2 weight "obj.firerune" count 18
        7 weight "obj.naturerune" count 4
        6 weight "obj.chaosrune" count 4
        3 weight "obj.mindrune" count 10
        3 weight "obj.bodyrune" count 10
        2 weight "obj.mindrune" count 18
        2 weight "obj.bodyrune" count 18
        2 weight "obj.bloodrune" count 2
        1 weight "obj.cosmicrune" count 2
        1 weight "obj.lawrune" count 3
        17 weight "obj.coins" count 1
        16 weight "obj.coins" count 2
        9 weight "obj.coins" count 4
        3 weight "obj.coins" count 29
        1 weight "obj.coins" count 30
        16 weight ringNothing()
        2 weight "obj.water_talisman" count 1
        2 weight "obj.fire_talisman" count 1
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 7 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 35 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)
