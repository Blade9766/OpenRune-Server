package org.rsmod.content.bosses.barrows

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal enum class BarrowsPuzzleType(
    val answer: Int,
    val sequence: List<Int>,
    val options: List<Int>,
) {
    ARROWS(6713, listOf(6716, 6717, 6718), listOf(6713, 6714, 6715)),
    SQUARES(6719, listOf(6722, 6723, 6724), listOf(6719, 6720, 6721)),
    SQUARES_OFFSET(6725, listOf(6728, 6729, 6730), listOf(6725, 6726, 6727)),
    SHAPES(6731, listOf(6734, 6735, 6736), listOf(6731, 6732, 6733)),
}

internal class BarrowsPuzzle(val type: BarrowsPuzzleType, val correctSlot: Int) {
    val options: List<Int> =
        type.options.filterTo(mutableListOf()) { it != type.answer }.apply {
            add(correctSlot, type.answer)
        }

    companion object {
        const val SLOTS = 3
    }
}

private var Player.puzzleTypeIndex by intVarBit("varbit.barrows_puzzle_type")
private var Player.puzzleAnswerSlot by intVarBit("varbit.barrows_puzzle_answer")

internal val Player.puzzle: BarrowsPuzzle
    get() {
        val types = BarrowsPuzzleType.entries
        val type = types[puzzleTypeIndex.coerceIn(types.indices)]
        return BarrowsPuzzle(type, puzzleAnswerSlot.coerceIn(0, BarrowsPuzzle.SLOTS - 1))
    }

internal fun Player.rollPuzzle() {
    puzzleTypeIndex = BarrowsPuzzleType.entries.indices.random()
    puzzleAnswerSlot = (0 until BarrowsPuzzle.SLOTS).random()
}
