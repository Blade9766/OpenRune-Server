package org.rsmod.content.quest.area.ardougne

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.torso
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.generic.locs.gate.GateTranslations
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/* Helpers shared by the Plague City and Biohazard scripts. */

/** The quarantined half of Ardougne, west of the great wall. */
internal object WestArdougne {
    private const val MIN_X = 2460
    private const val MAX_X = 2556
    private const val MIN_Z = 3264
    private const val MAX_Z = 3336

    fun contains(coords: CoordGrid): Boolean =
        coords.x in MIN_X..MAX_X && coords.z in MIN_Z..MAX_Z

    /** The Mourners' headquarters in the north-east corner of the city, ground and first floor. */
    fun inMournerHeadquarters(coords: CoordGrid): Boolean =
        coords.x in 2540..2557 && coords.z in 3319..3334 && coords.level <= 1
}

internal const val GAS_MASK = "obj.gasmask"
internal const val MEDICAL_GOWN = "obj.doctor_gown"
internal const val PRIEST_GOWN_TOP = "obj.priest_gown"
internal const val PRIEST_GOWN_BOTTOM = "obj.priest_robe"

private val gasMaskId = GAS_MASK.asRSCM(RSCMType.OBJ)
private val medicalGownId = MEDICAL_GOWN.asRSCM(RSCMType.OBJ)
private val priestTopId = PRIEST_GOWN_TOP.asRSCM(RSCMType.OBJ)
private val priestBottomId = PRIEST_GOWN_BOTTOM.asRSCM(RSCMType.OBJ)

internal fun Player.wearingGasMask(): Boolean = hat?.id == gasMaskId

internal fun Player.wearingMedicalGown(): Boolean = torso?.id == medicalGownId

internal fun Player.wearingPriestGown(): Boolean =
    torso?.id == priestTopId && legs?.id == priestBottomId

/**
 * Opens and closes the quest doors and gates that carry no generic door content group. Opened
 * locs revert on their own after [DURATION] cycles.
 */
@Singleton
class QuestDoors @Inject constructor(private val locRepo: LocRepository) {

    /**
     * Swings a single door open; [rotations] is 1 for a normal door and 3 for a left half. A
     * door normally swings onto the tile inside the room; with [outward] it swings the other way
     * around the same hinge, onto the doorway tile itself, so it never blocks a small room.
     */
    fun open(
        access: ProtectedAccess,
        closed: BoundLocInfo,
        opened: String,
        sound: String = DOOR_OPEN,
        rotations: Int = 1,
        outward: Boolean = false,
    ) {
        access.soundSynth(sound)
        val coords =
            if (outward) closed.coords
            else DoorTranslations.translateOpen(closed.coords, closed.shape, closed.angle)
        locRepo.del(closed, DURATION)
        locRepo.add(coords, opened, DURATION, closed.turnAngle(rotations), closed.shape)
    }

    /** Closes an opened single door early, before it reverts by itself. */
    fun close(
        access: ProtectedAccess,
        opened: BoundLocInfo,
        closed: String,
        sound: String = DOOR_CLOSE,
        rotations: Int = -1,
    ) {
        access.soundSynth(sound)
        val coords = DoorTranslations.translateClose(opened.coords, opened.shape, opened.angle)
        locRepo.del(opened, DURATION)
        locRepo.add(coords, closed, DURATION, opened.turnAngle(rotations), opened.shape)
    }

    /**
     * Opens both halves of a double door. [left] and [right] are the closed halves (either may
     * be missing if someone already opened it); the open forms are named explicitly because the
     * quest doors carry no `next_loc_stage` param.
     */
    fun openDouble(
        access: ProtectedAccess,
        left: LocInfo?,
        leftOpened: String,
        right: LocInfo?,
        rightOpened: String,
        sound: String = DOOR_OPEN,
    ) {
        access.soundSynth(sound)
        left?.let {
            val coords = DoorTranslations.translateOpen(it.coords, it.shape, it.angle)
            locRepo.del(it, DURATION)
            locRepo.add(coords, leftOpened, DURATION, it.angle.turn(3), it.shape)
        }
        right?.let {
            val coords = DoorTranslations.translateOpen(it.coords, it.shape, it.angle)
            locRepo.del(it, DURATION)
            locRepo.add(coords, rightOpened, DURATION, it.angle.turn(1), it.shape)
        }
    }

    /**
     * Opens a gate pair. Picket gates fold both leaves onto the left post (the generic behaviour);
     * with [symmetric] each leaf swings on its own post instead, the way the metal gates do.
     */
    fun openGate(
        access: ProtectedAccess,
        left: LocInfo?,
        leftOpened: String,
        right: LocInfo?,
        rightOpened: String,
        sound: String = GATE_OPEN,
        symmetric: Boolean = true,
    ) {
        access.soundSynth(sound)
        left?.let {
            val coords = it.coords + GateTranslations.leftGateOpen(it.shape, it.angle)
            locRepo.del(it, DURATION)
            locRepo.add(coords, leftOpened, DURATION, it.angle.turn(3), it.shape)
        }
        right?.let {
            val coords =
                if (symmetric) it.coords + GateTranslations.leftGateOpen(it.shape, it.angle)
                else it.coords + GateTranslations.rightGateOpen(it.shape, it.angle)
            val angle = if (symmetric) it.angle.turn(1) else it.angle.turn(3)
            locRepo.del(it, DURATION)
            locRepo.add(coords, rightOpened, DURATION, angle, it.shape)
        }
    }

    /** The other half of a gate, looked up by the left half's placement rule. */
    fun rightOfGate(left: BoundLocInfo, rightType: String): LocInfo? =
        find(left.coords + GateTranslations.leftGateRightPair(left.shape, left.angle), rightType)

    fun leftOfGate(right: BoundLocInfo, leftType: String): LocInfo? =
        find(right.coords - GateTranslations.leftGateRightPair(right.shape, right.angle), leftType)

    fun find(coords: CoordGrid, type: String): LocInfo? =
        locRepo.findAll(coords).firstOrNull { it.id == type.asRSCM(RSCMType.LOC) }

    fun asInfo(loc: BoundLocInfo): LocInfo = LocInfo(loc.layer, loc.coords, loc.entity)

    companion object {
        /** Shorter than the generic doors: an open door blocks part of a small room. */
        const val DURATION = 100
        const val DOOR_OPEN = "synth.door_open"
        const val DOOR_CLOSE = "synth.door_close"
        const val GATE_OPEN = "synth.picketgate_open"
    }
}

/* Fade helpers for the short cutscenes. Transparency 255 is fully see-through. */

internal suspend fun ProtectedAccess.fadeToBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 255,
        endColour = 0,
        endTransparency = 0,
        clientDuration = FADE_CLIENT_DURATION,
    )
    delay(FADE_CYCLES)
}

internal suspend fun ProtectedAccess.fadeFromBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 0,
        endColour = 0,
        endTransparency = 255,
        clientDuration = FADE_CLIENT_DURATION,
    )
    delay(FADE_CYCLES)
    closeFadeOverlay()
}

private const val FADE_CLIENT_DURATION = 50
private const val FADE_CYCLES = 3
