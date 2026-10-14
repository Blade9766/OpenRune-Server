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
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val hobgoblinDropTableUnarmedHobgoblin: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Hobgoblin Unarmed hobgoblin drops",
    npcs = npcs("npc.hobgoblin_unarmed", "npc.rimmington_hobgoblin_unarmed_1", "npc.rimmington_hobgoblin_unarmed_2", "npc.rimmington_hobgoblin_unarmed_3"),
    mainTable = rsPlayerWeightedTable(total = 187) {
        name("Hobgoblin Unarmed hobgoblin drops")
        3 weight "obj.bronze_spear" count 1
        3 weight "obj.iron_sword" count 1
        3 weight "obj.steel_dagger" count 1
        2 weight "obj.iron_spear" count 1
        2 weight "obj.steel_spear" count 1
        1 weight "obj.steel_longsword" count 1
        3 weight "obj.lawrune" count 2
        2 weight "obj.waterrune" count 2
        2 weight "obj.firerune" count 7
        2 weight "obj.bodyrune" count 6
        2 weight "obj.chaosrune" count 3
        2 weight "obj.naturerune" count 4
        1 weight "obj.cosmicrune" count 2
        16 weight "obj.coins" count 15
        16 weight "obj.coins" count 28
        12 weight "obj.coins" count 5
        4 weight "obj.coins" count 62
        3 weight "obj.coins" count 42
        1 weight "obj.coins" count 1
        1 weight "obj.village_trade_sticks" count 1 killCondition { player, npc, areaChecker ->
            areaChecker.inArea("area.karamja", npc.coords)
        }
        12 weight "obj.village_trade_sticks" count 5
        16 weight "obj.village_trade_sticks" count 15
        16 weight "obj.village_trade_sticks" count 28
        3 weight "obj.village_trade_sticks" count 42
        4 weight "obj.village_trade_sticks" count 62
        22 weight "obj.limpwurt_root" count 1
        5 weight "obj.goblin_armour" count 1
        1 weight ringNothing()

        7 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
        18 weight SharedDropTables.seed
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 5 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 5000 weight "obj.champions_challenge_hobgoblin" count 1
        1 outOf 70 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_beginner")
        }
    },
)

@field:RegisterDropTable
@JvmField
public val hobgoblinDropTableArmedHobgoblin: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Hobgoblin Armed hobgoblin drops",
    npcs = npcs("npc.godwars_ancient_hobgoblin", "npc.hobgoblin_armed", "npc.rimmington_hobgoblin_armed_1"),
    mainTable = rsPlayerWeightedTable(total = 132) {
        name("Hobgoblin Armed hobgoblin drops")
        3 weight "obj.iron_sword" count 1
        3 weight "obj.steel_dagger" count 1
        1 weight "obj.steel_longsword" count 1
        3 weight "obj.lawrune" count 2
        2 weight "obj.waterrune" count 2
        2 weight "obj.firerune" count 7
        2 weight "obj.bodyrune" count 6
        2 weight "obj.chaosrune" count 3
        2 weight "obj.naturerune" count 4
        1 weight "obj.cosmicrune" count 2
        1 weight "obj.iron_javelin" count 5
        34 weight "obj.coins" count 15
        24 weight "obj.coins" count 5
        4 weight "obj.coins" count 28
        4 weight "obj.coins" count 62
        3 weight "obj.coins" count 42
        3 weight "obj.coins" count 1 condition { player ->
            player.members
        }
        1 weight "obj.coins" count 1
        21 weight "obj.limpwurt_root" count 1
        5 weight "obj.goblin_armour" count 1
        2 weight ringNothing()

        7 weight SharedDropTables.herb
        2 weight SharedDropTables.gem
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 5000 weight "obj.champions_challenge_hobgoblin" count 1
    },
)
