package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.game.entity.Player

/* The three god followers of Mage Arena II: each always drops the remains Kolodion is after. */

@field:RegisterDropTable
@JvmField
public val justiciarZachariahDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Justiciar Zachariah Drops",
    npcs = npcs("npc.ma2_boss_saradomin"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.ma2_saradomin_heart" count 1
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
    },
)

@field:RegisterDropTable
@JvmField
public val derwenDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Derwen Drops",
    npcs = npcs("npc.ma2_boss_guthix"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.ma2_guthix_heart" count 1
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
    },
)

@field:RegisterDropTable
@JvmField
public val porazdirDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Porazdir Drops",
    npcs = npcs("npc.ma2_boss_zamorak"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.ma2_zamorak_heart" count 1
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
    },
)
