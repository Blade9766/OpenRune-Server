package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.darkTotemPieceObj
import org.rsmod.content.drops.onWildernessSlayerTask
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val hellhoundDropTableLevel122: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Hellhound Level 122 drops",
    npcs = npcs("npc.hellhound", "npc.hellhound_strongholdcave", "npc.kourend_hellhound"),
    mainTable = rsPlayerWeightedTable(total = 50) {
        name("Hellhound Level 122 drops")
        6 weight "obj.deathrune" count 10
        3 weight "obj.deathrune" count 20
        1 weight "obj.deathrune" count 50
        1 outOf 32768 separate "obj.smouldering_stone" count 1
        40 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 40 weight "obj.arceuus_corpse_hellhound" count 1
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            // Drops Need Manual: Elite clue scrolls and reward caskets are only dropped when completing an elite clue scroll asking you to kill a hellhound.
             true
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        onBuilder { brimstoneKeyRoll() }
        1 outOf 60 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 32 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 256 weight "obj.cata_shard" count 1 killCondition {
            player, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords)
        }
        1 outOf 384 chance DropRollItem(
            "obj.cata_totem1",
            1,
            killCondition = { _, npc, areaChecker -> areaChecker.inArea("area.catacombs_of_kourend", npc.coords) },
            transformObj = { player -> player.darkTotemPieceObj() },
        )
        1 outOf 228 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 91 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)

@field:RegisterDropTable
@JvmField
public val hellhoundDropTableLevel136WildernessSlayerCave: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Hellhound Level 136 (Wilderness Slayer Cave) drops",
    npcs = npcs("npc.wild_cave_hell_hound"),
    mainTable = rsPlayerWeightedTable(total = 16384) {
        name("Hellhound Level 136 (Wilderness Slayer Cave) drops")
        1 weight "obj.smouldering_stone" count 1
        16383 weight nothing()
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 40 weight "obj.arceuus_corpse_hellhound" count 1
        1 outOf 1 weight "obj.trail_elite_emote_exp1" count 1 condition { player ->
            // Drops Need Manual: Elite clue scrolls and reward caskets are only dropped when completing an elite clue scroll asking you to kill a hellhound.
             true
        }
        1 outOf 1 weight "obj.trail_reward_casket_elite" count 1
        1 outOf 3 weight "obj.looting_bag" count 1 condition {
            player -> player.shouldDropLootingBag()
        }
        1 outOf 60 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 32 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 200 weight "obj.slayer_enchantment" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
        1 outOf 76 weight "obj.slayer_wilderness_key" count 1 killCondition {
            player, npc, areaChecker -> player.onWildernessSlayerTask(npc, areaChecker)
        }
    },
)
