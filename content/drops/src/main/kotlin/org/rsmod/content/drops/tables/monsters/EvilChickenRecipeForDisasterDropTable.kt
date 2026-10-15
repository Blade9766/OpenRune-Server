package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.content.drops.isOnQuest
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val evilChickenRecipeForDisasterDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Evil Chicken (Recipe for Disaster) Drops",
    npcs = npcs("npc.chickenquest_evil_chicken"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.raw_chicken" count 1
        "obj.feather" count 50..250
        "obj.chickenquest_evil_chicken_egg" count 1 condition {
            player -> player.isOnQuest("quest_recipefordisaster")
        }
    },
)
