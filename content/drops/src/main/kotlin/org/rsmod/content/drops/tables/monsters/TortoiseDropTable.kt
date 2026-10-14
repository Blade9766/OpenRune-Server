package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val tortoiseDropTableWithoutRiders: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Tortoise Drops without riders",
    npcs = npcs("npc.battle_tortoise"),
    mainTable = rsPlayerWeightedTable(total = 138) {
        name("Tortoise Drops without riders")
        10 weight "obj.naturerune" count 1..3
        20 weight "obj.steel_arrow" count 12..23
        5 weight "obj.earthrune" count 10..14
        20 weight "obj.cert_cabbage" count 20..59
        10 weight "obj.pineapple" count 1
        8 weight "obj.worm_batta" count 1
        20 weight "obj.swamp_toad" count 1
        10 weight ringNothing()
        10 weight "obj.battle_tortoise_shell" count 1..3
        2 weight "obj.mahogany_logs" count 1
        2 weight "obj.teak_logs" count 1
        1 weight "obj.teak_logs" count 2

        10 weight SharedDropTables.herb
        10 weight SharedDropTables.seed
    },
)

@field:RegisterDropTable
@JvmField
public val tortoiseDropTableWithRiders: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Tortoise Drops with riders",
    npcs = npcs("npc.battle_tortoise_war2"),
    mainTable = rsPlayerWeightedTable(total = 138) {
        name("Tortoise Drops with riders")
        1 weight "obj.gnome_boots_pink" count 1
        1 weight "obj.gnome_robebottoms_pink" count 1
        1 weight "obj.gnome_robetop_pink" count 1
        10 weight "obj.earthrune" count 10..14
        20 weight "obj.mithril_arrow" count 6..11
        10 weight "obj.naturerune" count 1..3
        10 weight "obj.steel_arrow" count 12..23
        20 weight "obj.cert_cabbage" count 20..59
        10 weight "obj.pineapple" count 1
        10 weight "obj.toad_crunchies" count 1
        4 weight "obj.worm_batta" count 1
        1 weight "obj.blurberry_special" count 1
        8 weight "obj.battle_tortoise_shell" count 1..3
        5 weight "obj.swamp_toad" count 1
        2 weight "obj.mahogany_logs" count 2
        2 weight "obj.dorgesh_tortoise_shell" count 1..3
        2 weight "obj.teak_logs" count 2
        1 weight "obj.teak_logs" count 4

        10 weight SharedDropTables.herb
        10 weight SharedDropTables.seed
    },
)
