package org.rsmod.content.other.barrows

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BarrowsTunnelScript
@Inject
constructor(
    private val spawns: BarrowsSpawns,
    private val chest: BarrowsChest,
    private val locRepo: LocRepository,
    private val random: GameRandom,
) : PluginScript() {
    private val corridorsByDoor: Map<Int, TunnelCorridor> by lazy {
        TunnelCorridor.entries
            .flatMap { listOf(it.rightDoor to it, it.leftDoor to it) }
            .associate { (door, corridor) -> door.asRSCM(RSCMType.LOC) to corridor }
    }

    /** The door crossing waiting on the puzzle, so a right answer can finish walking through. */
    private val pendingCrossings = HashMap<PlayerUid, Crossing>()

    override fun ScriptContext.startup() {
        onGameStartup { spawnChest() }
        onOpLoc1(UNLOCKED_DOOR_RIGHT) { openDoor(it.loc) }
        onOpLoc1(UNLOCKED_DOOR_LEFT) { openDoor(it.loc) }
        onOpLoc1(LADDER) { climbLadder() }
        onOpLoc1(CHEST_CLOSED) { chest.open(this, it.loc) }
        onOpLoc1(CHEST_OPEN) { chest.search(this, it.loc) }
        onOpLoc2(CHEST_OPEN) { chest.close(it.loc) }
        onIfModalButton(PUZZLE_A) { answerPuzzle(0) }
        onIfModalButton(PUZZLE_B) { answerPuzzle(1) }
        onIfModalButton(PUZZLE_C) { answerPuzzle(2) }
        onIfClose(PUZZLE) { pendingCrossings.remove(player.uid) }
    }

    private fun spawnChest() {
        locRepo.add(
            BarrowsCoords.CHEST,
            CHEST_CLOSED,
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        val corridor = corridorsByDoor[door.id] ?: return
        if (BarrowsRun.isLocked(player, corridor)) {
            mes("The door is locked.")
            return
        }
        val dest = tileAcross(door, player.coords)
        val intoCentre = BarrowsCoords.inCentreRoom(dest) && !BarrowsCoords.inCentreRoom(player.coords)
        if (intoCentre && !BarrowsRun.isPuzzleSolved(player)) {
            showPuzzle(Crossing(player.coords, dest))
            return
        }
        walkThrough(player.coords, dest)
        delay(1)
        summonBehindDoor()
    }

    private fun ProtectedAccess.walkThrough(from: CoordGrid, dest: CoordGrid) {
        soundSynth(DOOR_SOUND)
        exactMove(from, dest, delay1 = 0, delay2 = DOOR_MOVE_CYCLES, dir = faceTowards(from, dest))
    }

    /**
     * Every door wakes something unless the room is already crowded: 12 in 128 a brother still
     * standing, otherwise a skeleton, bloodworm or crypt rat. Once the chest is looted each door
     * raises one of the brothers the player left alive.
     */
    private fun ProtectedAccess.summonBehindDoor() {
        if (spawns.isCrowded(player.coords)) {
            return
        }
        val brotherFree = spawns.activeBrother(player) == null
        val remaining = Brother.entries.filter { !BarrowsRun.isKilled(player, it) }
        if (BarrowsRun.isLooted(player)) {
            if (brotherFree && remaining.isNotEmpty()) {
                spawns.summonBrother(player, remaining[random.of(remaining.size)], shout = null)
            }
            return
        }
        val brotherRoll =
            random.of(BarrowsSpawns.DOOR_CHANCE_OUT_OF) < BarrowsSpawns.BROTHER_DOOR_CHANCE
        if (brotherRoll && brotherFree && remaining.isNotEmpty()) {
            spawns.summonBrother(player, remaining[random.of(remaining.size)], shout = null)
            return
        }
        spawns.summonMonster(player)
    }

    private fun ProtectedAccess.showPuzzle(crossing: Crossing) {
        val type = player.vars[BarrowsRun.PUZZLE_TYPE]
        val answerSlot = player.vars[BarrowsRun.PUZZLE_ANSWER]
        val sequence = BarrowsPuzzle.sequence(type)
        val answers = BarrowsPuzzle.answers(type, answerSlot)
        ifOpenMainModal(PUZZLE)
        for ((index, component) in SEQUENCE_COMPONENTS.withIndex()) {
            ifSetModel(component, sequence[index])
        }
        for ((index, component) in ANSWER_COMPONENTS.withIndex()) {
            ifSetModel(component, answers[index])
        }
        pendingCrossings[player.uid] = crossing
    }

    private suspend fun ProtectedAccess.answerPuzzle(slot: Int) {
        val crossing = pendingCrossings.remove(player.uid)
        ifClose()
        if (crossing == null || !BarrowsCoords.inTunnels(player.coords)) {
            return
        }
        if (slot != player.vars[BarrowsRun.PUZZLE_ANSWER]) {
            BarrowsRun.shuffleTunnels(player, random)
            mes("You got the puzzle wrong! You can hear the catacombs moving around you.")
            return
        }
        BarrowsRun.markPuzzleSolved(player)
        mes("You hear the doors' locking mechanism grind open.")
        if (player.coords != crossing.from) {
            return
        }
        walkThrough(crossing.from, crossing.dest)
        delay(1)
    }

    private fun ProtectedAccess.climbLadder() {
        val brother = BarrowsRun.tunnelBrother(player) ?: Brother.Ahrim
        anim(CLIMB_SEQ)
        spawns.dismissAll(player)
        telejump(brother.cryptLanding)
    }

    private data class Crossing(val from: CoordGrid, val dest: CoordGrid)

    companion object {
        const val UNLOCKED_DOOR_RIGHT = "loc.barrows_door_unlocked_r"
        const val UNLOCKED_DOOR_LEFT = "loc.barrows_door_unlocked_l"
        const val LADDER = "loc.barrows_ladder"
        const val CHEST_CLOSED = "loc.barrows_stone_chest_closed"
        const val CHEST_OPEN = "loc.barrows_stone_chest_open"

        const val PUZZLE = "interface.barrows_puzzle"
        const val PUZZLE_A = "component.barrows_puzzle:a"
        const val PUZZLE_B = "component.barrows_puzzle:b"
        const val PUZZLE_C = "component.barrows_puzzle:c"
        val SEQUENCE_COMPONENTS =
            listOf("component.barrows_puzzle:1", "component.barrows_puzzle:2", "component.barrows_puzzle:3")
        val ANSWER_COMPONENTS =
            listOf(
                "component.barrows_puzzle:pic_a",
                "component.barrows_puzzle:pic_b",
                "component.barrows_puzzle:pic_c",
            )

        const val DOOR_SOUND = "synth.door_open"
        const val DOOR_MOVE_CYCLES = 30
        const val CLIMB_SEQ = "seq.human_reachforladder"

        /**
         * The tile on the other side of a wall door from [from]. A wall sits on one edge of its own
         * tile (angle 0 west, 1 north, 2 east, 3 south); the player keeps their row or column so
         * either leaf of a double door works.
         */
        fun tileAcross(door: BoundLocInfo, from: CoordGrid): CoordGrid {
            val tile = door.coords
            return when (door.angle) {
                LocAngle.West ->
                    CoordGrid(if (from.x >= tile.x) tile.x - 1 else tile.x, from.z, from.level)
                LocAngle.East ->
                    CoordGrid(if (from.x <= tile.x) tile.x + 1 else tile.x, from.z, from.level)
                LocAngle.North ->
                    CoordGrid(from.x, if (from.z <= tile.z) tile.z + 1 else tile.z, from.level)
                LocAngle.South ->
                    CoordGrid(from.x, if (from.z >= tile.z) tile.z - 1 else tile.z, from.level)
            }
        }

        fun faceTowards(from: CoordGrid, to: CoordGrid): Int =
            when {
                to.x < from.x -> constants.em_face_west
                to.x > from.x -> constants.em_face_east
                to.z > from.z -> constants.em_face_north
                else -> constants.em_face_south
            }
    }
}
