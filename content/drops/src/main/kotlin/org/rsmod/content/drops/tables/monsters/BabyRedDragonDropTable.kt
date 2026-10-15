package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.fewestTatteredPage
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val babyRedDragonDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Baby red dragon Drops",
    npcs = npcs("npc.babyreddragon", "npc.babyreddragon2", "npc.babyreddragon3"),
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 25 chance DropRollItem(
            "obj.hosdun_moon_page",
            1,
            killCondition = { _, npc, areaChecker -> areaChecker.inArea("area.forthos_dungeon", npc.coords) },
            transformObj = { player -> player.fewestTatteredPage() },
        )
        1 outOf 80 weight "obj.hosdun_grubby_key" count 1 killCondition { _, npc, areaChecker -> areaChecker.inArea("area.forthos_dungeon", npc.coords) }
        onBuilder { brimstoneKeyRoll() }
    },
)
