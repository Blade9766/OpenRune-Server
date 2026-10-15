package org.rsmod.content.skills.hunter.rumours

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.roundToInt
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player

/**
 * Hunters' Rumour state. Each guild hunter keeps the rumour it last offered in its own varbit, and
 * [ACTIVE] names the hunter whose rumour the player is currently working on. Catches of that
 * rumour's creature roll for its rare part with a pity counter.
 */
@Singleton
class RumourTracker
@Inject
constructor(private val random: GameRandom, private val objRepo: ObjRepository) {
    fun isUnlocked(player: Player): Boolean = player.vars[UNLOCKED] != 0

    fun unlock(player: Player) {
        VarPlayerIntMapSetter.set(player, UNLOCKED, 1)
    }

    fun backToBack(player: Player): Boolean = player.vars[BACK_TO_BACK] != 0

    fun setBackToBack(player: Player, enabled: Boolean) {
        VarPlayerIntMapSetter.set(player, BACK_TO_BACK, if (enabled) 1 else 0)
    }

    fun activeHunter(player: Player): RumourHunter? =
        RumourHunter.entries.getOrNull(player.vars[ACTIVE] - 1)

    fun assigned(player: Player, hunter: RumourHunter): Rumour? =
        Rumour.byId(player.vars[hunter.assignedVarbit])

    fun activeRumour(player: Player): Rumour? {
        val hunter = activeHunter(player) ?: return null
        return assigned(player, hunter)
    }

    fun completed(player: Player): Int = player.vars[COMPLETED]

    /** Offers [hunter]'s stored rumour, rolling a new one when it has none; null if none fit. */
    fun offer(player: Player, hunter: RumourHunter, level: Int): Rumour? {
        assigned(player, hunter)?.let {
            return it
        }
        val last = Rumour.byId(player.vars[LAST])
        val candidates =
            Rumour.entries.filter {
                hunter in it.hunters && it.level <= level && (backToBack(player) || it != last)
            }
        if (candidates.isEmpty()) {
            return null
        }
        val rumour = candidates[random.of(candidates.size)]
        VarPlayerIntMapSetter.set(player, hunter.assignedVarbit, rumour.id)
        return rumour
    }

    fun activate(player: Player, hunter: RumourHunter) {
        VarPlayerIntMapSetter.set(player, ACTIVE, hunter.ordinal + 1)
        VarPlayerIntMapSetter.set(player, PITY, 0)
    }

    fun complete(player: Player, hunter: RumourHunter, rumour: Rumour): Int {
        VarPlayerIntMapSetter.set(player, hunter.assignedVarbit, 0)
        VarPlayerIntMapSetter.set(player, ACTIVE, 0)
        VarPlayerIntMapSetter.set(player, PITY, 0)
        VarPlayerIntMapSetter.set(player, LAST, rumour.id)
        val completed = completed(player) + 1
        VarPlayerIntMapSetter.set(player, COMPLETED, completed)
        return completed
    }

    fun nextOutfitPiece(player: Player): String = OUTFIT_PIECES[player.vars[OUTFIT]]

    fun advanceOutfit(player: Player) {
        VarPlayerIntMapSetter.set(player, OUTFIT, (player.vars[OUTFIT] + 1) % OUTFIT_PIECES.size)
    }

    fun onCatch(player: Player, creature: String) {
        val rumour = activeRumour(player) ?: return
        if (rumour.key != creature || rumour.part in player.inv) {
            return
        }
        val pity = player.vars[PITY] + 1
        if (random.of(rumour.method.rate) != 0 && pity < pityThreshold(player, rumour.method)) {
            VarPlayerIntMapSetter.set(player, PITY, pity)
            return
        }
        VarPlayerIntMapSetter.set(player, PITY, 0)
        player.invAddOrDrop(objRepo, rumour.part)
        player.mes(
            "<col=ef1020>You find a rare piece of the creature! You should take it back to the " +
                "Hunter Guild.</col>"
        )
    }

    private fun pityThreshold(player: Player, method: RumourMethod): Int {
        val pieces = OUTFIT_PIECES.count { it in player.worn }
        val reduction = (method.pity - method.outfitPity) * pieces / OUTFIT_PIECES.size.toDouble()
        return method.pity - reduction.roundToInt()
    }

    private companion object {
        const val ACTIVE = "varbit.hunter_rumour_active"
        const val UNLOCKED = "varbit.hunter_rumour_unlocked"
        const val BACK_TO_BACK = "varbit.hunter_rumour_back_to_back"
        const val PITY = "varbit.hunter_rumour_pity"
        const val LAST = "varbit.hunter_rumour_last"
        const val OUTFIT = "varbit.hunter_rumour_outfit"
        const val COMPLETED = "varp.hunter_rumours_completed"

        val OUTFIT_PIECES =
            listOf(
                "obj.hg_hunter_hood",
                "obj.hg_hunter_top",
                "obj.hg_hunter_legs",
                "obj.hg_hunter_boots",
            )
    }
}
