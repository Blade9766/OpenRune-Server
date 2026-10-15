package org.rsmod.content.quest.area.morytania.ghostsahoy

/**
 * One game of Rune-Draw against Robin, as he explains it: a bag of ten runes worth 1 (air) to
 * 9 (nature) plus a death rune, drawn in turns starting with the player. Drawing the death rune
 * loses on the spot. A player who holds stops drawing and the other carries on alone until they
 * beat the held score or draw death; a tie is not a win, so drawing continues.
 *
 * Robin holds once he is ahead with at least [ROBIN_STANDS_ON]; the player then has to draw past
 * him or bust. The bag always ends in the death rune, so every game has a winner.
 */
class RuneDrawGame(bag: List<Rune>) {
    private val bag = ArrayDeque(bag)
    val playerRunes = mutableListOf<Rune>()
    val robinRunes = mutableListOf<Rune>()
    var playerHeld = false
        private set
    var robinHeld = false
        private set
    var result: Result? = null
        private set

    val playerScore: Int
        get() = playerRunes.sumOf { it.value }

    val robinScore: Int
        get() = robinRunes.sumOf { it.value }

    val isOver: Boolean
        get() = result != null

    fun playerDraw() {
        if (isOver || playerHeld) {
            return
        }
        val rune = bag.removeFirst()
        playerRunes += rune
        if (rune == Rune.DEATH) {
            result = Result.LOST
            return
        }
        if (robinHeld) {
            if (playerScore > robinScore) result = Result.WON
            return
        }
        robinTurn()
    }

    fun playerHold() {
        if (isOver || playerHeld) {
            return
        }
        playerHeld = true
        if (robinHeld) {
            result = if (playerScore > robinScore) Result.WON else Result.LOST
            return
        }
        while (!isOver && robinScore <= playerScore) {
            robinDraw()
        }
        if (!isOver) {
            result = Result.LOST
        }
    }

    private fun robinTurn() {
        if (robinScore > playerScore && robinScore >= ROBIN_STANDS_ON) {
            robinHeld = true
            return
        }
        robinDraw()
    }

    private fun robinDraw() {
        val rune = bag.removeFirst()
        robinRunes += rune
        if (rune == Rune.DEATH) {
            result = Result.WON
        }
    }

    enum class Result {
        WON,
        LOST,
    }

    enum class Rune(val value: Int, val obj: String, val label: String) {
        AIR(1, "obj.airrune", "Air"),
        MIND(2, "obj.mindrune", "Mind"),
        WATER(3, "obj.waterrune", "Water"),
        EARTH(4, "obj.earthrune", "Earth"),
        FIRE(5, "obj.firerune", "Fire"),
        BODY(6, "obj.bodyrune", "Body"),
        COSMIC(7, "obj.cosmicrune", "Cosmic"),
        CHAOS(8, "obj.chaosrune", "Chaos"),
        NATURE(9, "obj.naturerune", "Nature"),
        DEATH(0, "obj.deathrune", "Death"),
    }

    companion object {
        const val ROBIN_STANDS_ON = 15
        const val BAG_SIZE = 10

        /** A shuffled bag, using [pick] (0 until n) as the source of randomness. */
        fun shuffledBag(pick: (Int) -> Int): List<Rune> {
            val runes = Rune.entries.toMutableList()
            for (i in runes.indices.reversed()) {
                val j = pick(i + 1)
                val swap = runes[i]
                runes[i] = runes[j]
                runes[j] = swap
            }
            return runes
        }
    }
}
