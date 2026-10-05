package org.rsmod.content.generic.locs.doors

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/**
 * Swings a pair of double doors open or shut together. Each call returns the tiles the doors moved
 * off, for callers that need to tidy up whatever those tiles show once the doors are gone.
 */
class DoubleDoors @Inject constructor(private val locRepo: LocRepository) {
    fun ProtectedAccess.openLeftDoor(
        left: BoundLocInfo,
        type: ObjectServerType,
        duration: Int = DoorConstants.DURATION,
    ): List<CoordGrid> {
        soundSynth(type.param(params.opensound))
        swing(left, type.param(params.next_loc_stage), left.turnAngle(rotations = 3), left.openCoords(), duration)
        val right =
            locRepo.findExact(
                coords = left.closeCoords(),
                content = "content.closed_right_door",
                shape = left.shape,
            ) ?: return listOf(left.coords)
        val openedLoc = locParamOrNull(right, params.next_loc_stage) ?: return listOf(left.coords)
        swing(right, openedLoc, right.turnAngle(rotations = 1), right.openCoords(), duration)
        return listOf(left.coords, right.coords)
    }

    fun ProtectedAccess.openRightDoor(
        right: BoundLocInfo,
        type: ObjectServerType,
        duration: Int = DoorConstants.DURATION,
    ): List<CoordGrid> {
        soundSynth(type.param(params.opensound))
        swing(right, type.param(params.next_loc_stage), right.turnAngle(rotations = 1), right.openCoords(), duration)
        val left =
            locRepo.findExact(
                coords = right.closeCoordsOpposite(),
                content = "content.closed_left_door",
                shape = right.shape,
            ) ?: return listOf(right.coords)
        val openedLoc = locParamOrNull(left, params.next_loc_stage) ?: return listOf(right.coords)
        swing(left, openedLoc, left.turnAngle(rotations = 3), left.openCoords(), duration)
        return listOf(right.coords, left.coords)
    }

    fun ProtectedAccess.closeLeftDoor(
        left: BoundLocInfo,
        type: ObjectServerType,
        duration: Int = DoorConstants.DURATION,
    ): List<CoordGrid> {
        soundSynth(type.param(params.closesound))
        swing(left, type.param(params.next_loc_stage), left.turnAngle(rotations = 1), left.closeCoordsOpposite(), duration)
        val right =
            locRepo.findExact(
                coords = left.openCoordsOpposite(),
                content = "content.opened_right_door",
                shape = left.shape,
            ) ?: return listOf(left.coords)
        val closedLoc = locParamOrNull(right, params.next_loc_stage) ?: return listOf(left.coords)
        swing(right, closedLoc, right.turnAngle(rotations = 3), right.closeCoords(), duration)
        return listOf(left.coords, right.coords)
    }

    fun ProtectedAccess.closeRightDoor(
        right: BoundLocInfo,
        type: ObjectServerType,
        duration: Int = DoorConstants.DURATION,
    ): List<CoordGrid> {
        soundSynth(type.param(params.closesound))
        swing(right, type.param(params.next_loc_stage), right.turnAngle(rotations = 3), right.closeCoords(), duration)
        val left =
            locRepo.findExact(
                coords = right.openCoordsOpposite(),
                content = "content.opened_left_door",
                shape = right.shape,
            ) ?: return listOf(right.coords)
        val closedLoc = locParamOrNull(left, params.next_loc_stage) ?: return listOf(right.coords)
        swing(left, closedLoc, left.turnAngle(rotations = 1), left.closeCoordsOpposite(), duration)
        return listOf(right.coords, left.coords)
    }

    private fun swing(door: BoundLocInfo, into: ObjectServerType, angle: LocAngle, to: CoordGrid, duration: Int) {
        locRepo.del(door, duration)
        locRepo.add(to, into, duration, angle, door.shape)
    }

    private fun swing(door: LocInfo, into: ObjectServerType, angle: LocAngle, to: CoordGrid, duration: Int) {
        locRepo.del(door, duration)
        locRepo.add(to, into, duration, angle, door.shape)
    }

    private fun BoundLocInfo.openCoords(): CoordGrid = DoorTranslations.translateOpen(coords, shape, angle)

    private fun BoundLocInfo.openCoordsOpposite(): CoordGrid =
        DoorTranslations.translateOpenOpposite(coords, shape, angle)

    private fun BoundLocInfo.closeCoords(): CoordGrid = DoorTranslations.translateClose(coords, shape, angle)

    private fun BoundLocInfo.closeCoordsOpposite(): CoordGrid =
        DoorTranslations.translateCloseOpposite(coords, shape, angle)

    private fun LocInfo.openCoords(): CoordGrid = DoorTranslations.translateOpen(coords, shape, angle)

    private fun LocInfo.openCoordsOpposite(): CoordGrid =
        DoorTranslations.translateOpenOpposite(coords, shape, angle)

    private fun LocInfo.closeCoords(): CoordGrid = DoorTranslations.translateClose(coords, shape, angle)

    private fun LocInfo.closeCoordsOpposite(): CoordGrid =
        DoorTranslations.translateCloseOpposite(coords, shape, angle)
}
