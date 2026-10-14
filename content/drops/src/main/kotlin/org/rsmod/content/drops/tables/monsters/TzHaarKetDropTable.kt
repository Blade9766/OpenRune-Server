package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val tzHaarKetDropTableLevel149: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "TzHaar-Ket Drops (Level 149)",
    npcs = npcs("npc.tzhaar_guard_border", "npc.tzhaar_ket1", "npc.tzhaar_ket2", "npc.tzhaar_ket3", "npc.tzhaar_ket4", "npc.tzhaar_ket5", "npc.tzhaar_ket6", "npc.tzhaar_ket7"),
    mainTable = rsPlayerWeightedTable(total = 512) {
        name("TzHaar-Ket Drops (Level 149)")
        1 weight "obj.tzhaar_maul" count 1
        1 weight "obj.tzhaar_spikeshield" count 1
        1 weight "obj.tzhaar_cape_obsidian" count 1
        16 weight "obj.uncut_sapphire" count 1
        8 weight "obj.uncut_emerald" count 1
        4 weight "obj.uncut_ruby" count 1
        1 weight "obj.uncut_diamond" count 1
        160 weight "obj.tzhaar_token" count 1..64
        1280 outOf 2048 separate ringNothing()
        320 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 35 weight "obj.arceuus_corpse_tzhaar" count 1
    },
)

@field:RegisterDropTable
@JvmField
public val tzHaarKetDropTableLevel221: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "TzHaar-Ket Drops (Level 221)",
    npcs = npcs("npc.tzhaar_ket_strong"),
    mainTable = rsPlayerWeightedTable(total = 512) {
        name("TzHaar-Ket Drops (Level 221)")
        1 weight "obj.tzhaar_maul" count 1
        1 weight "obj.tzhaar_spikeshield" count 1
        1 weight "obj.tzhaar_cape_obsidian" count 1
        16 weight "obj.uncut_sapphire" count 1
        8 weight "obj.uncut_emerald" count 1
        6 weight "obj.uncut_ruby" count 1
        3 weight "obj.uncut_diamond" count 1
        10 weight "obj.xbows_bolt_tips_onyx" count 1..4
        192 weight "obj.tzhaar_token" count 20..83
        1 outOf 2000 separate rsPlayerWeightedTable {
            1 weight "obj.obsidian_helmet" count 1
            1 weight "obj.obsidian_platebody" count 1
            1 weight "obj.obsidian_platelegs" count 1
        }
        1096 outOf 2048 separate ringNothing()
        274 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 25 weight "obj.arceuus_corpse_tzhaar" count 1
    },
)
