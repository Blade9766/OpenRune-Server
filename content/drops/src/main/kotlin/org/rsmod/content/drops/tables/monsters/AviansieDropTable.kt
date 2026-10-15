package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.hasCompletedDiary
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val aviansieDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Aviansie Drops",
    npcs = npcs("npc.godwars_armadyl_female_armor01_blue", "npc.godwars_armadyl_female_armor01_green", "npc.godwars_armadyl_female_armor01_red", "npc.godwars_armadyl_female_armor02_blue", "npc.godwars_armadyl_female_armor02_green", "npc.godwars_armadyl_female_armor02_red", "npc.godwars_armadyl_female_armor03_blue", "npc.godwars_armadyl_male_armor01_blue", "npc.godwars_armadyl_male_armor01_green", "npc.godwars_armadyl_male_armor01_red", "npc.godwars_armadyl_male_armor02_blue", "npc.godwars_armadyl_male_armor02_green", "npc.godwars_armadyl_male_armor02_red", "npc.godwars_armadyl_male_armor03_green", "npc.godwars_armadyl_male_armor03_red"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.feather" count 1..6
    },
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Aviansie Drops")
        11 weight "obj.rune_dagger_p+" count 1
        18 weight "obj.airrune" count 15
        13 weight "obj.waterrune" count 30
        4 weight "obj.lawrune" count 2
        4 weight "obj.naturerune" count 9
        3 weight "obj.chaosrune" count 3
        2 weight "obj.bodyrune" count 12
        2 weight "obj.bloodrune" count 11
        1 weight "obj.mindrune" count 5
        1 weight "obj.chaosrune" count 16
        30 weight "obj.adamantite_bar" count 4 killTransformObj { player, npc, areaChecker ->
            if (player.hasCompletedDiary("varbit.fremennik_diary_hard_complete") && areaChecker.inArea("area.godwars_dungeon", npc.coords)) "obj.cert_adamantite_bar" else null
        }
        10 weight "obj.silver_ore" count 1
        1 weight "obj.xbows_crossbow_limbs_runite" count 1
        5 weight "obj.3doseantipoison" count 5
        3 weight ringNothing()
        2 weight "obj.swordfish" count 5

        15 weight SharedDropTables.herb
        3 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 35 weight "obj.arceuus_corpse_aviansie" count 1 killCondition { _, npc, areaChecker ->
            !areaChecker.inArea("area.wilderness_dungeons", npc.coords)
        }
        1 outOf 20 weight "obj.arceuus_corpse_aviansie" count 1 killCondition { _, npc, areaChecker ->
            areaChecker.inArea("area.wilderness_dungeons", npc.coords)
        }
        onBuilder { brimstoneKeyRoll() }
        1 outOf 121 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 64 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 264 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 136 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
