package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.MapClock
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * The world's three blue and three green streams on the nine weeping walls. Each stream stays on
 * its wall for [STREAM_TICKS] cycles and then jumps to a dry wall; the six move one after another
 * in a fixed order that is shuffled once per world, as on the real game. The walls only advance
 * while someone is collecting, since nobody else can see into the cave.
 */
@Singleton
class TearStreams
@Inject
constructor(
    private val locRepo: LocRepository,
    private val random: GameRandom,
    private val clock: MapClock,
) {
    enum class Colour {
        Blue,
        Green,
        Dry,
    }

    private class Stream(val colour: Colour, var wall: Int, var nextMove: Int)

    private val colours = WALLS.map { it.initial }.toTypedArray()
    private val streams = mutableListOf<Stream>()
    private var lastAdvance = -1

    fun colourAt(coords: CoordGrid): Colour {
        val index = WALLS.indexOfFirst { it.coords == coords }
        return if (index < 0) Colour.Dry else colours[index]
    }

    fun advance() {
        val now = clock.cycle
        if (now == lastAdvance) {
            return
        }
        lastAdvance = now
        if (streams.isEmpty()) {
            start(now)
            return
        }
        for (stream in streams) {
            if (stream.nextMove > now) {
                continue
            }
            val overdue = now - stream.nextMove
            stream.nextMove = now + STREAM_TICKS - overdue % STREAM_TICKS
            move(stream)
        }
    }

    private fun start(now: Int) {
        val order = (0 until WALLS.size).filter { colours[it] != Colour.Dry }.shuffledBy(random)
        for ((position, wall) in order.withIndex()) {
            val offset = 1 + position * STREAM_TICKS / order.size
            streams += Stream(colours[wall], wall, now + offset)
        }
    }

    private fun move(stream: Stream) {
        val dry = WALLS.indices.filter { colours[it] == Colour.Dry }
        if (dry.isEmpty()) {
            return
        }
        val target = dry[random.of(maxExclusive = dry.size)]
        paint(stream.wall, Colour.Dry)
        paint(target, stream.colour)
        stream.wall = target
    }

    private fun paint(index: Int, colour: Colour) {
        colours[index] = colour
        val wall = WALLS[index]
        locRepo.add(wall.coords, wall.decor(colour), Int.MAX_VALUE, wall.angle, LocShape.WallDecorStraightNoOffset)
    }

    private fun <T> List<T>.shuffledBy(random: GameRandom): List<T> {
        val copy = toMutableList()
        for (i in copy.indices.reversed()) {
            val j = random.of(maxExclusive = i + 1)
            val swap = copy[i]
            copy[i] = copy[j]
            copy[j] = swap
        }
        return copy
    }

    class Wall(val coords: CoordGrid, val angle: LocAngle, private val left: Boolean, val initial: Colour) {
        fun decor(colour: Colour): String {
            val side = if (left) "l" else "r"
            return when (colour) {
                Colour.Blue -> "loc.tog_weeping_wall_good_$side"
                Colour.Green -> "loc.tog_weeping_wall_bad_$side"
                Colour.Dry -> "loc.tog_weeping_wall_off_$side"
            }
        }
    }

    companion object {
        const val STREAM_TICKS = 16

        val WALLS =
            listOf(
                Wall(CoordGrid(3257, 9514, 2), LocAngle.North, left = false, Colour.Green),
                Wall(CoordGrid(3258, 9514, 2), LocAngle.North, left = true, Colour.Green),
                Wall(CoordGrid(3259, 9514, 2), LocAngle.North, left = false, Colour.Green),
                Wall(CoordGrid(3257, 9520, 2), LocAngle.South, left = true, Colour.Blue),
                Wall(CoordGrid(3258, 9520, 2), LocAngle.South, left = false, Colour.Blue),
                Wall(CoordGrid(3259, 9520, 2), LocAngle.South, left = true, Colour.Blue),
                Wall(CoordGrid(3261, 9516, 2), LocAngle.West, left = false, Colour.Dry),
                Wall(CoordGrid(3261, 9517, 2), LocAngle.West, left = true, Colour.Dry),
                Wall(CoordGrid(3261, 9518, 2), LocAngle.West, left = false, Colour.Dry),
            )
    }
}
