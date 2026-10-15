package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * The quest's journeys: Lokar's boat between Rellekka and the Pirates' Cove, the Lady Zay between
 * the Cove and Lunar Isle, and the Moon Clan's way of seeing off anyone who turns up on their
 * island without a Seal of Passage.
 *
 * The Lady Zay's crossings play on `interface.quest_lunar_galleon`, the chart of the northern sea
 * with the ship model that the cache animates round in a circle, out to the island or back.
 */
@Singleton
class LunarTravel @Inject constructor() {
    suspend fun ProtectedAccess.rowWithLokar(dest: CoordGrid) {
        fadeToBlack()
        telejump(dest, TeleportType.Exempt)
        delay(2)
        fadeFromBlack()
        closeFadeOverlay()
    }

    /** The voyage that never gets anywhere while the cabin boy's jinx is on the ship. */
    suspend fun ProtectedAccess.sailInCircle() {
        chart(SAIL_WRONG_SEQ)
        mes("The Lady Zay sails a wide circle and drifts back to where she started.")
    }

    /** From the Pirates' Cove to Lunar Isle, landing on the Lunar pier copy of the deck. */
    suspend fun ProtectedAccess.sailToLunarIsle() {
        chart(SAIL_RIGHT_SEQ)
        val dest = if (LunarCoords.onCoveShip(coords)) LunarCoords.toLunarShip(coords) else LunarCoords.LUNAR_DECK
        fadeToBlack()
        telejump(dest, TeleportType.Exempt)
        if (coords != dest) {
            telejump(LunarCoords.LUNAR_DECK, TeleportType.Exempt)
        }
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
    }

    suspend fun ProtectedAccess.sailToPiratesCove() {
        chart(SAIL_RETURN_SEQ)
        val dest = if (LunarCoords.onLunarShip(coords)) LunarCoords.toCoveShip(coords) else LunarCoords.COVE_DECK
        fadeToBlack()
        telejump(dest, TeleportType.Exempt)
        if (coords != dest) {
            telejump(LunarCoords.COVE_DECK, TeleportType.Exempt)
        }
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
    }

    private suspend fun ProtectedAccess.chart(seq: String) {
        ifOpenMainModal(CHART_INTERFACE)
        ifSetAnim(CHART_SHIP, ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)))
        delay(CHART_TICKS)
        ifClose()
    }

    fun hasSeal(access: ProtectedAccess): Boolean =
        SEAL in access.player.inv || SEAL in access.player.worn

    /**
     * Anyone on Lunar Isle without a Seal of Passage who tries to deal with the Moon Clan is
     * magicked back to Rellekka. Returns true when the player was sent away.
     */
    suspend fun ProtectedAccess.expelWithoutSeal(npc: Npc?): Boolean {
        if (hasSeal(this) || !LunarCoords.onLunarIsle(coords) && !LunarCoords.inBabaYagaHouse(coords)) {
            return false
        }
        if (npc != null) {
            npc.say("Fremennik spy! Be gone!")
            npc.anim(CAST_SEQ)
            faceEntitySquare(npc)
        }
        mes("Without your Seal of Passage the Moon Clan see you as an enemy, and send you away.")
        anim(TELEPORT_SEQ)
        spotanim(TELEPORT_SPOT)
        soundSynth(TELEPORT_SOUND)
        delay(3)
        telejump(LunarCoords.RELLEKKA_DOCK, TeleportType.Exempt)
        resetAnim()
        return true
    }

    companion object {
        const val SEAL = LunarDiplomacyQuest.SEAL_OF_PASSAGE

        const val CHART_INTERFACE = "interface.quest_lunar_galleon"
        const val CHART_SHIP = "component.quest_lunar_galleon:lunar_galleon"
        const val CHART_TICKS = 12

        const val SAIL_WRONG_SEQ = "seq.quest_lunar_set_sail_wrong"
        const val SAIL_RIGHT_SEQ = "seq.quest_lunar_set_sail_right"
        const val SAIL_RETURN_SEQ = "seq.quest_lunar_set_sail_return"

        const val CAST_SEQ = "seq.moonclan_staff_cast"
        const val TELEPORT_SEQ = "seq.lunar_teleport"
        const val TELEPORT_SPOT = "spotanim.lunar_teleport_spotanim"
        const val TELEPORT_SOUND = "synth.teleport_all"
    }
}
