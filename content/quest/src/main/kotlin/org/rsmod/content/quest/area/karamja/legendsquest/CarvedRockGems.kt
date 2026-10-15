package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionObjT
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

/**
 * The seven gems of the cavern of pools, each placed over its own carved rock. A placed gem spins
 * above its rock for a few moments before fading; while it can still be seen the player who put
 * it there may take it back, which undoes the placing.
 */
internal object CarvedRockGems {
    data class Gem(val obj: String, val name: String, val rock: CoordGrid, val bit: Int) {
        val id: Int by lazy { obj.asRSCM(RSCMType.OBJ) }
    }

    val GEMS =
        listOf(
            Gem("obj.opal", "opal", CoordGrid(2764, 9309, 0), 1),
            Gem("obj.jade", "jade", CoordGrid(2771, 9303, 0), 2),
            Gem("obj.red_topaz", "red topaz", CoordGrid(2772, 9295, 0), 4),
            Gem("obj.sapphire", "sapphire", CoordGrid(2781, 9291, 0), 8),
            Gem("obj.diamond", "diamond", CoordGrid(2774, 9287, 0), 16),
            Gem("obj.ruby", "ruby", CoordGrid(2767, 9289, 0), 32),
            Gem("obj.emerald", "emerald", CoordGrid(2757, 9297, 0), 64),
        )

    const val ALL_GEMS = 127
    const val SPIN_TICKS = 8
    const val REVEAL_TICKS = 30

    /** The gem [obj] is, if it is lying on its own carved rock. */
    fun placedAt(obj: Obj, type: ItemServerType): Gem? =
        GEMS.firstOrNull { it.id == type.id && it.rock == obj.coords }

    /** Shows [gem] spinning above its rock to [player] for [ticks], unless it already is. */
    fun show(objRepo: ObjRepository, player: Player, gem: Gem, ticks: Int) {
        if (objRepo.findAll(gem.rock).any { it.type == gem.id }) {
            return
        }
        objRepo.add(gem.obj, gem.rock, ticks, receiver = player)
    }
}

/**
 * A placed gem cannot be telegrabbed. Taken by hand, it comes back to the player and the rock
 * forgets it, unless all seven are in place (the book is being conjured) or the player never put
 * that gem there.
 */
class CarvedRockGemTakeHook @Inject constructor() : PlayerObjTakeValidateHook {
    override fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String? {
        val gem = CarvedRockGems.placedAt(obj, objType) ?: return null
        val telegrab = player.interaction is InteractionObjT || player.coords.chebyshevDistance(obj.coords) > 1
        if (telegrab) {
            return "The spell fizzles and dies for some unknown reason."
        }
        val placed = player.legendsGems
        if (placed == CarvedRockGems.ALL_GEMS || placed and gem.bit == 0) {
            return "The gem seems untouchable, as if it wasn't even there."
        }
        if (player.inv.isFull()) {
            return null
        }
        player.legendsGems = placed and gem.bit.inv()
        player.mes("You take the ${gem.name}.")
        return null
    }
}
