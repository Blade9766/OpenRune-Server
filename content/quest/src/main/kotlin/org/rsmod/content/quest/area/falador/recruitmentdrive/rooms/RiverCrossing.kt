package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

/** The two banks of Sir Spishyus's river, and the player's hands. */
enum class Bank {
    EAST,
    WEST,
    CARRIED,
}

/** The three things to be carried across. */
enum class Cargo {
    FOX,
    CHICKEN,
    GRAIN,
}

/**
 * The fox, chicken and grain puzzle as a pure configuration: where each piece of cargo is and which
 * bank the player stands on. The player starts on the east bank with everything; the puzzle is
 * solved when all three are set down on the west bank. Only one piece may be carried over the
 * bridge, and the bank the player walks away from must not hold the fox with the chicken or the
 * chicken with the grain.
 */
data class RiverCrossing(val positions: Map<Cargo, Bank>, val player: Bank) {
    sealed class Outcome {
        data class Crossed(val state: RiverCrossing) : Outcome()

        data object Overloaded : Outcome()

        data class Eaten(val state: RiverCrossing, val eater: Cargo, val eaten: Cargo, val bank: Bank) : Outcome()
    }

    val carried: List<Cargo>
        get() = Cargo.entries.filter { positions[it] == Bank.CARRIED }

    fun on(bank: Bank): List<Cargo> = Cargo.entries.filter { positions[it] == bank }

    val solved: Boolean
        get() = Cargo.entries.all { positions[it] == Bank.WEST }

    fun pickUp(cargo: Cargo): RiverCrossing? {
        if (positions[cargo] != player) return null
        return copy(positions = positions + (cargo to Bank.CARRIED))
    }

    fun putDown(cargo: Cargo): RiverCrossing? {
        if (positions[cargo] != Bank.CARRIED) return null
        return copy(positions = positions + (cargo to player))
    }

    fun cross(): Outcome {
        if (carried.size > 1) {
            return Outcome.Overloaded
        }
        val left = player
        val arrived = copy(player = if (player == Bank.EAST) Bank.WEST else Bank.EAST)
        val unattended = on(left)
        if (Cargo.FOX in unattended && Cargo.CHICKEN in unattended) {
            return Outcome.Eaten(arrived, Cargo.FOX, Cargo.CHICKEN, left)
        }
        if (Cargo.CHICKEN in unattended && Cargo.GRAIN in unattended) {
            return Outcome.Eaten(arrived, Cargo.CHICKEN, Cargo.GRAIN, left)
        }
        return Outcome.Crossed(arrived)
    }

    companion object {
        val START = RiverCrossing(Cargo.entries.associateWith { Bank.EAST }, Bank.EAST)
    }
}
