package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val lizardmanDropTableLevel53: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Lizardman Level 53 drops",
    npcs = npcs("npc.zeah_lizardman_1_vn", "npc.zeah_lizardman_1_vp"),
    mainTable = rsPlayerWeightedTable(total = 87) {
        name("Lizardman Level 53 drops")
        10 weight "obj.lizardman_fang" count 1
        6 weight "obj.xeric_fabric" count 1
        1 outOf 250 separate "obj.xeric_talisman_empty" count 1
        71 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        onBuilder { brimstoneKeyRoll() }
    },
)

@field:RegisterDropTable
@JvmField
public val lizardmanDropTableLevel62: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Lizardman Level 62 drops",
    npcs = npcs("npc.molch_lizardman_1", "npc.zeah_lizardman_2_vn", "npc.zeah_lizardman_2_vp"),
    mainTable = rsPlayerWeightedTable(total = 78) {
        name("Lizardman Level 62 drops")
        14 weight "obj.lizardman_fang" count 1
        8 weight "obj.xeric_fabric" count 1
        1 outOf 250 separate "obj.xeric_talisman_empty" count 1
        56 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        onBuilder { brimstoneKeyRoll() }
    },
)
