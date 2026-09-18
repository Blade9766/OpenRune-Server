package org.rsmod.content.quest.area.desert.icthlarin

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.TILE_COUNT
import org.rsmod.game.entity.Player

/**
 * The door puzzle of Klenter's western chamber (`interface.icthalarins_tile_game`): a five by
 * five board of tiles, gold on one face and dark on the other. Turning a tile turns the three by
 * three block around it as well, and the door opens once every tile shows gold. The golden bird
 * beside the board starts the puzzle over from one of its set layouts.
 *
 * Tile states are `varbit.ics_tile1`-`25` (1 = dark face up) on `varp.main_ics_var`. The client
 * draws the tiles from their models, so every change is shown by turning the tile over with its
 * own animation.
 */
@Singleton
class TilePuzzle {

    fun open(access: ProtectedAccess) {
        val player = access.player
        if (isSolved(player)) {
            scramble(player)
        }
        access.ifOpenMainModal(INTERFACE)
        for (tile in 1..TILE_COUNT) {
            access.ifSetEvents(tileComponent(tile), 0..0, IfEvent.Op1)
            if (isDark(player, tile)) {
                turn(access, tile, dark = true)
            }
        }
        access.ifSetEvents(RESET_COMPONENT, 0..0, IfEvent.Op1)
    }

    /** Turns [tile] and its neighbours; returns true when that leaves every tile gold. */
    fun press(access: ProtectedAccess, tile: Int): Boolean {
        val player = access.player
        val row = (tile - 1) / SIDE
        val column = (tile - 1) % SIDE
        for (r in row - 1..row + 1) {
            for (c in column - 1..column + 1) {
                if (r !in 0 until SIDE || c !in 0 until SIDE) {
                    continue
                }
                val neighbour = r * SIDE + c + 1
                val dark = !isDark(player, neighbour)
                setDark(player, neighbour, dark)
                turn(access, neighbour, dark)
            }
        }
        access.soundSynth(TILE_MOVE)
        return isSolved(player)
    }

    /** The golden bird: lays the board out afresh. */
    fun reset(access: ProtectedAccess) {
        val player = access.player
        val before = (1..TILE_COUNT).map { isDark(player, it) }
        scramble(player)
        for (tile in 1..TILE_COUNT) {
            val dark = isDark(player, tile)
            if (dark != before[tile - 1]) {
                turn(access, tile, dark)
            }
        }
        access.soundSynth(TILE_RESET)
    }

    fun isSolved(player: Player): Boolean = (1..TILE_COUNT).none { isDark(player, it) }

    /**
     * Picks one of the set layouts. Each is made by pressing a handful of tiles on a gold board,
     * so pressing the same tiles again always solves it.
     */
    private fun scramble(player: Player) {
        val current = (1..TILE_COUNT).filter { isDark(player, it) }.toSet()
        val choices = LAYOUTS.map(::layout).filter { it != current }
        val next = choices[player.ilhPuzzleResets % choices.size]
        player.ilhPuzzleResets = (player.ilhPuzzleResets + 1) % MAX_RESET_COUNT
        for (tile in 1..TILE_COUNT) {
            setDark(player, tile, tile in next)
        }
    }

    private fun layout(presses: Set<Int>): Set<Int> {
        val dark = BooleanArray(TILE_COUNT)
        for (tile in presses) {
            val row = (tile - 1) / SIDE
            val column = (tile - 1) % SIDE
            for (r in row - 1..row + 1) {
                for (c in column - 1..column + 1) {
                    if (r in 0 until SIDE && c in 0 until SIDE) {
                        val index = r * SIDE + c
                        dark[index] = !dark[index]
                    }
                }
            }
        }
        return (1..TILE_COUNT).filter { dark[it - 1] }.toSet()
    }

    private fun turn(access: ProtectedAccess, tile: Int, dark: Boolean) {
        val seq = if (dark) FRONT_TO_BACK else BACK_TO_FRONT
        access.ifSetAnim(tileComponent(tile), ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)))
    }

    private fun isDark(player: Player, tile: Int): Boolean = player.vars[tileVarbit(tile)] == 1

    private fun setDark(player: Player, tile: Int, dark: Boolean) {
        VarPlayerIntMapSetter.set(player, tileVarbit(tile), if (dark) 1 else 0)
    }

    private fun tileVarbit(tile: Int): String = "varbit.ics_tile$tile"

    companion object {
        const val INTERFACE = "interface.icthalarins_tile_game"
        const val RESET_COMPONENT = "component.icthalarins_tile_game:ics_tilegame_picture"

        const val SIDE = 5

        /** Wraps before the varbit's three bits run out. */
        const val MAX_RESET_COUNT = 7

        const val FRONT_TO_BACK = "seq.ics_tile_turn_f2b"
        const val BACK_TO_FRONT = "seq.ics_tile_turn_b2f"
        const val TILE_MOVE = "synth.tile_move"
        const val TILE_RESET = "synth.tile_reset"

        /** The tiles pressed on a gold board to make each layout; the same presses solve it. */
        val LAYOUTS: List<Set<Int>> =
            listOf(
                setOf(1, 5, 13, 21, 25),
                setOf(3, 7, 9, 17, 19, 23),
                setOf(2, 8, 12, 14, 18, 24),
                setOf(6, 10, 11, 13, 15, 16, 20),
            )

        fun tileComponent(tile: Int): String = "component.icthalarins_tile_game:ics_$tile"

        fun tileOf(component: String): Int? =
            component.substringAfter("icthalarins_tile_game:ics_", "").toIntOrNull()
    }
}
