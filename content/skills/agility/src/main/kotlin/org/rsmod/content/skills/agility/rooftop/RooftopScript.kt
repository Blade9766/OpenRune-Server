package org.rsmod.content.skills.agility.rooftop

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.dropTo
import org.rsmod.content.skills.agility.fallTo
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.pipeThrough
import org.rsmod.content.skills.agility.seqGlideTicks
import org.rsmod.content.skills.agility.stepOnto
import org.rsmod.content.skills.agility.successChance
import org.rsmod.content.skills.agility.zipTo
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Handles every obstacle of the rooftop agility courses and the lap-based ground courses.
 *
 * Lap progress is a per-session bit mask of the obstacles completed on the current course. The
 * final obstacle only pays its lap bonus (and rolls for a mark of grace) when every other obstacle
 * of that course has been completed since the last finish, which mirrors the live game where
 * skipping an obstacle forfeits the completion bonus.
 */
class RooftopScript
@Inject
constructor(
    private val marks: MarksOfGrace,
    private val worldRepo: WorldRepository,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val byLoc = LinkedHashMap<String, MutableList<CourseStep>>()
        for (layout in RooftopCourses.layouts) {
            for ((index, obstacle) in layout.obstacles.withIndex()) {
                for (loc in obstacle.locs) {
                    byLoc.getOrPut(loc) { mutableListOf() } += CourseStep(layout, index, obstacle)
                }
            }
        }
        for ((loc, steps) in byLoc) {
            onOpLoc1(loc) {
                val step = steps.stepAt(it.loc) ?: return@onOpLoc1
                attempt(step.layout, step.index, step.obstacle, it.loc)
            }
            val apRange = steps.maxOf { it.obstacle.apRange }
            if (apRange > 0) {
                onApLoc1(loc) {
                    val step = steps.stepAt(it.loc) ?: return@onApLoc1
                    if (isWithinApRange(it.loc, step.obstacle.apRange)) {
                        attempt(step.layout, step.index, step.obstacle, it.loc)
                    }
                }
            }
        }
    }

    private class CourseStep(val layout: CourseLayout, val index: Int, val obstacle: RooftopObstacle)

    private fun List<CourseStep>.stepAt(loc: BoundLocInfo): CourseStep? =
        singleOrNull() ?: firstOrNull { it.obstacle.locAt == loc.coords }

    private suspend fun ProtectedAccess.attempt(
        layout: CourseLayout,
        index: Int,
        obstacle: RooftopObstacle,
        loc: BoundLocInfo,
    ) {
        val course = layout.course
        arriveDelay()
        val quest = course.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest.key)) {
            mes("You need to complete ${quest.name} to use this course.")
            return
        }
        if (player.agilityLvl < course.level) {
            mes("You need an Agility level of ${course.level} to use this course.")
            return
        }
        if (obstacle.isBehind(coords)) {
            val verb = if (obstacle.locAt != null) "climb over" else "cross"
            mes("You can't $verb the ${obstacle.name.lowercase()} from this side.")
            return
        }
        stepOnto(obstacle.start)

        obstacle.shout?.let { shout -> layout.nearestTrainer(coords)?.say(shout) }
        obstacle.messages.first?.let { mes(it) }
        obstacle.locSeq?.let { locAnim(worldRepo, loc, it) }
        val failure = obstacle.failure?.takeIf { rollFailure(course, it) }
        val completed = perform(obstacle.move, failure)
        if (!completed) {
            player.courseProgress = 0
            return
        }

        obstacle.messages.second?.let { mes(it) }
        statAdvance(AGILITY, obstacle.xp)
        val progress = player.progressOn(course) or (1 shl layout.steps[index])
        if (!obstacle.isFinish) {
            player.courseProgress = pack(course, progress)
            return
        }

        player.courseProgress = 0
        if (progress == layout.fullMask) {
            statAdvance(AGILITY, obstacle.lapBonusXp)
            if (obstacle.lapBonusStrengthXp > 0.0) {
                statAdvance(STRENGTH, obstacle.lapBonusStrengthXp)
            }
            marks.roll(player, random, layout)
        }
    }

    private fun CourseLayout.nearestTrainer(coords: CoordGrid): Npc? {
        val trainer = trainer ?: return null
        return npcRepo
            .findAll(ZoneKey.from(coords), TRAINER_ZONE_RADIUS)
            .filter { it.isType(trainer) && it.coords.level == coords.level }
            .minByOrNull { it.coords.chebyshevDistance(coords) }
    }

    private fun ProtectedAccess.rollFailure(course: RooftopCourse, failure: ObstacleFailure): Boolean {
        val level = player.agilityLvl
        if (level >= failure.noFailLevel) {
            return false
        }
        failure.chance?.let {
            return !statRandom(AGILITY, it.first, it.last, invisibleBoost = 0)
        }
        val chance = successChance(level, course.level, failure.noFailLevel)
        return random.of(100) >= chance
    }

    /** Runs [move], or the failure sequence when [failure] is set. Returns `false` on a fail. */
    private suspend fun ProtectedAccess.perform(move: ObstacleMove, failure: ObstacleFailure?): Boolean {
        when (move) {
            is ObstacleMove.Climb -> {
                val ticks = move.ticks ?: seqGlideTicks(move.seq, fallback = 2)
                climbTo(move.dest, move.seq, ticks)
            }
            is ObstacleMove.Drop -> {
                if (failure != null) {
                    fall(failure, AgilityAnims.JUMP_DOWN)
                    return false
                }
                dropTo(move.dest, move.ticks, glideLevel = move.glideLevel ?: coords.level)
            }
            is ObstacleMove.Leap -> {
                if (failure != null) {
                    fall(failure, move.seq)
                    return false
                }
                val ticks = move.ticks ?: seqGlideTicks(move.seq, fallback = 2)
                leapTo(move.dest, move.seq, ticks, glideLevel = move.glideLevel ?: coords.level)
            }
            is ObstacleMove.Zipline -> {
                if (failure != null) {
                    fall(failure, AgilityAnims.ZIPLINE_GRAB)
                    return false
                }
                zipTo(move.dest, move.ticks)
            }
            is ObstacleMove.Pipe -> pipeThrough(move.dest)
            is ObstacleMove.Balance -> {
                val stopAt = if (failure != null) move.path.size / 2 else -1
                val crossed = balanceAlong(move.path, move.style, stopAt)
                if (!crossed && failure != null) {
                    fall(failure, AgilityAnims.BALANCE_STUMBLE)
                    return false
                }
            }
        }
        return true
    }

    private suspend fun ProtectedAccess.fall(failure: ObstacleFailure, seq: String) {
        fallTo(
            failure.landing,
            failure.seq ?: seq,
            failure.minDamage,
            failure.maxDamage,
            failure.message,
        )
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val STRENGTH = "stat.strength"
        const val TRAINER_ZONE_RADIUS = 1

        /** Packed `course ordinal shl 16 or completed-obstacle mask`; not persisted. */
        val COURSE_PROGRESS = AttributeKey<Int>()

        var Player.courseProgress: Int
            get() = attr[COURSE_PROGRESS] ?: 0
            set(value) {
                attr[COURSE_PROGRESS] = value
            }

        fun Player.progressOn(course: RooftopCourse): Int {
            val packed = courseProgress
            return if (packed shr 16 == course.ordinal) packed and 0xFFFF else 0
        }

        fun pack(course: RooftopCourse, mask: Int): Int = (course.ordinal shl 16) or mask
    }
}
