package org.rsmod.content.quest.area.draynor.porcineofinterest

import dev.openrune.types.ItemServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hat
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.KNIFE
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.REINFORCED_GOGGLES
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

/** Whether the player carries or wears [obj]. */
internal fun ProtectedAccess.carries(obj: String): Boolean =
    player.inv.contains(obj) || player.worn.contains(obj)

/**
 * Whether the player's eyes are covered. The goggles only work on the face, and once the quest is
 * over a slayer helm has a pair fastened into its eye holes and does the same job.
 */
internal fun Player.wearsGoggles(): Boolean {
    if (worn.contains(REINFORCED_GOGGLES)) {
        return true
    }
    val helm = hat ?: return false
    return SLAYER_HELMS.any { helm.isType(it) }
}

internal fun ProtectedAccess.wearsGoggles(): Boolean = player.wearsGoggles()

/** Every slayer helm, plain and imbued, in the order the cache names its recolours. */
private val SLAYER_HELMS =
    listOf(
        "",
        "_black",
        "_green",
        "_red",
        "_purple",
        "_turquoise",
        "_hydra",
        "_twisted",
        "_jad",
        "_verzik",
        "_zuk",
        "_araxyte",
        "_hooded",
    )
        .flatMap { listOf("obj.slayer_helm$it", "obj.slayer_helm_i$it") }

/**
 * Whether the player is holding something that could take a foot off a carcass: a knife, or a
 * wielded weapon with a slashing edge.
 *
 * The quest turns down the whips and claws, which tear rather than cut.
 */
internal fun ProtectedAccess.holdsCuttingEdge(): Boolean {
    if (player.inv.contains(KNIFE)) {
        return true
    }
    val weapon = player.righthand ?: return false
    return cutsFlesh(getInvObj(weapon))
}

/** Whether [type] is sharp enough to take a foot off the carcass. */
internal fun cutsFlesh(type: ItemServerType): Boolean {
    if (type.isType(KNIFE)) {
        return true
    }
    if (BLUNT_SLASH_WEAPONS.any { type.isType(it) }) {
        return false
    }
    return (type.paramOrNull(params.attack_slash) ?: 0) > 0
}

/** Slash weapons the quest refuses: they tear rather than cut. */
private val BLUNT_SLASH_WEAPONS =
    listOf(
        "obj.abyssal_whip",
        "obj.abyssal_tentacle",
        "obj.noxious_halberd",
        "obj.dragon_claws",
    )

internal object PorcineCoords {
    /** The notice board behind Fortunato's wine stall in the Draynor market. */
    val NOTICE_BOARD = CoordGrid(3086, 3251, 0)

    /** Where the player comes round on Spria's floor, beside her spawn tile. */
    val SPRIA_BEDSIDE = CoordGrid(3092, 3266, 0)

    /** The strange hole by the River Lum, and the tile the rope hangs beside. */
    val HOLE = CoordGrid(3150, 3347, 0)
    val HOLE_SIDE = CoordGrid(3149, 3347, 0)

    /** Beside the climbing rope at the top of the Sourhog Cave. */
    val CAVE_ENTRANCE = CoordGrid(3159, 9714, 0)

    /** Either side of the blockage in the two-tile corridor south of the entrance chamber. */
    val BLOCKAGE_NORTH = CoordGrid(3156, 9705, 0)
    val BLOCKAGE_SOUTH = CoordGrid(3156, 9703, 0)

    /** The gnawed skeleton at the far end of the cave, and the note lying beside it. */
    val SKELETON = CoordGrid(3163, 9676, 0)

    /** Where the Pig Thing rears up behind the player during the ambush. */
    val AMBUSH_PIG = CoordGrid(3165, 9676, 0)

    /** Where Spria walks in from once the pig has wandered off. */
    val AMBUSH_SPRIA = CoordGrid(3160, 9679, 0)

    /** The whole cave, used to keep the quest's checks off the rest of the world. */
    fun inSourhogCave(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3136..3199 && coords.z in 9664..9727

    /** The chamber the rope drops into, north of the blockage. */
    fun inEntranceChamber(coords: CoordGrid): Boolean =
        inSourhogCave(coords) && coords.z >= 9704
}
