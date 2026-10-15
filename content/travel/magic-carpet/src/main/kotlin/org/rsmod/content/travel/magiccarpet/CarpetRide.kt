package org.rsmod.content.travel.magiccarpet

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import dev.openrune.util.Wearpos
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.game.movement.MoveSpeed

internal object CarpetRide {
    private const val FULL_FARE = 200
    private const val DISCOUNT_FARE = 100
    private const val DOUBLE_DISCOUNT_FARE = 75

    /** Magic Carpet Ride, js5 archive 11 group 132; it lasts about as long as a flight. */
    private const val RIDE_JINGLE = 132

    private const val SIT_DOWN_TICKS = 3
    private const val RAISE_TICKS = 4
    private const val LAND_TICKS = 4

    fun fare(access: ProtectedAccess): Int {
        if (access.vars["varbit.desert_diary_hard_complete"] == 1) {
            return 0
        }
        val charosRing = access.worn.count("obj.ring_of_charos_unlocked") > 0
        val rogueTrader = access.vars["varp.roguetrader_var"] > 0
        return when {
            charosRing && rogueTrader -> DOUBLE_DISCOUNT_FARE
            charosRing || rogueTrader -> DISCOUNT_FARE
            else -> FULL_FARE
        }
    }

    /**
     * Flies the player from their current station to [route]'s destination.
     *
     * The carpet is shown in the weapon slot as an appearance override so the worn inventory is
     * never touched, and the carpet base animations make every step of the flight a hover. The
     * flight is a scripted walk at run speed that ignores collision. If the ride is torn down
     * before landing, the player is put down on the destination pad so nobody is left stranded
     * over the dunes.
     */
    suspend fun ProtectedAccess.fly(route: CarpetRoute) {
        val path = CarpetRoutes.flightPath(route)
        val landing = route.to.pad
        if (player.coords != route.from.pad) {
            forcedWalk(route.from.pad, crossTiles = player.coords.chebyshevDistance(route.from.pad))
        }
        faceSquare(path.first())
        var landed = false
        try {
            mountCarpet()
            anim("seq.carpet_sit_down")
            delay(SIT_DOWN_TICKS)
            anim("seq.carpet_raise")
            player.midiJingle(RIDE_JINGLE)
            delay(RAISE_TICKS)
            forcedWalk(path, crossTiles = (path.size + 1) / 2, moveSpeed = MoveSpeed.Run)
            anim("seq.carpet_land")
            delay(LAND_TICKS)
            landed = true
        } finally {
            dismountCarpet()
            if (!landed && player.coords != landing) {
                telejump(landing, TeleportType.Exempt)
            }
        }
    }

    private fun ProtectedAccess.mountCarpet() {
        val flying = "seq.carpet_flying".asRSCM(RSCMType.SEQ)
        player.appearance.setWornOverride(
            Wearpos.RightHand.slot,
            "obj.magic_carpet".asRSCM(RSCMType.OBJ),
        )
        player.bas =
            BasType(
                id = -1,
                readyAnim = flying,
                turnOnSpot = flying,
                walkForward = flying,
                walkBack = flying,
                walkLeft = flying,
                walkRight = flying,
                running = flying,
            )
        rebuildAppearance()
    }

    private fun ProtectedAccess.dismountCarpet() {
        player.appearance.clearWornOverride(Wearpos.RightHand.slot)
        player.bas = null
        resetAnim()
        rebuildAppearance()
    }
}
