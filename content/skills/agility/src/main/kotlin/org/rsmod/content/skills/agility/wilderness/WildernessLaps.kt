package org.rsmod.content.skills.agility.wilderness

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal var Player.dispenserDeposit by boolVarBit("varbit.wildy_agility_deposit")
internal var Player.lootWaiting by boolVarBit("varbit.wildy_agility_reward_available")
internal var Player.ticketWaiting by boolVarBit("varbit.wildy_agility_token_available")
internal var Player.skipStartWarning by boolVarBit("varbit.wildy_agility_start_warning")
internal var Player.skipExitWarning by boolVarBit("varbit.wildy_agility_exit_warning")
internal var Player.skipPaymentWarning by boolVarBit("varbit.wildy_agility_payment_warning")
internal var Player.lapStreak by intVarBit("varbit.wildy_agility_reward_lap_new")

/**
 * Lap bookkeeping for the Wilderness course. A finished lap leaves a ticket (and, with the fee
 * paid, loot) waiting in the dispenser until it is tagged; starting another lap first throws
 * both away and, for a paying player, resets the lap streak the loot is scaled by.
 */
@Singleton
class WildernessLaps {
    /** Runs as the first obstacle is started; returns false if the player backs out. */
    suspend fun beforeLap(access: ProtectedAccess): Boolean {
        val player = access.player
        if (!player.ticketWaiting && !player.lootWaiting) {
            return true
        }
        if (!player.skipStartWarning) {
            val choice =
                access.choice3(
                    "Start the lap anyway.",
                    START,
                    "No, I'll tag the dispenser first.",
                    CANCEL,
                    "Start the lap, and don't warn me again.",
                    START_AND_SKIP,
                    title = "You haven't tagged the dispenser since your last lap.",
                )
            if (choice == CANCEL) {
                return false
            }
            if (choice == START_AND_SKIP) {
                player.skipStartWarning = true
            }
        }
        if (player.lootWaiting && player.lapStreak > 0) {
            player.lapStreak = 0
            access.mes("You didn't tag the dispenser, so your lap streak has been reset.")
        }
        player.ticketWaiting = false
        player.lootWaiting = false
        return true
    }

    fun lapCompleted(access: ProtectedAccess) {
        val player = access.player
        player.ticketWaiting = true
        if (player.dispenserDeposit) {
            player.lootWaiting = true
            player.lapStreak = (player.lapStreak + 1).coerceAtMost(MAX_STREAK)
        }
        access.mes("Tag the Agility dispenser to collect your reward.")
    }

    fun forfeitDeposit(player: Player) {
        player.dispenserDeposit = false
        player.lootWaiting = false
        player.lapStreak = 0
    }

    companion object {
        private const val START = 1
        private const val CANCEL = 2
        private const val START_AND_SKIP = 3

        /** The streak varbit is 17 bits wide. */
        const val MAX_STREAK = (1 shl 17) - 1

        /** The course itself, the gated yard and the spiked dungeon under it. */
        fun inCourse(coords: CoordGrid): Boolean {
            if (coords.level != 0 || coords.x !in COURSE_X) {
                return false
            }
            return coords.z in SURFACE_Z || coords.z in DUNGEON_Z
        }

        private val COURSE_X = 2988..3012
        private val SURFACE_Z = 3931..3966
        private val DUNGEON_Z = 10336..10366
    }
}
