package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.content.skills.agility.CLIENT_CYCLES_PER_TICK
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.emFaceTowards
import org.rsmod.content.skills.agility.hopTo
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.line
import org.rsmod.content.skills.agility.pipeThrough
import org.rsmod.content.skills.agility.seqGlideTicks
import org.rsmod.content.skills.agility.seqTicks
import org.rsmod.content.skills.agility.stepOnto
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Moves players across the two-way agility shortcuts listed in [AgilityShortcuts]. */
class AgilityShortcutScript : PluginScript() {
    override fun ScriptContext.startup() {
        val byLoc = mutableMapOf<String, MutableList<AgilityShortcut>>()
        for (shortcut in AgilityShortcuts.all) {
            for (loc in shortcut.locs) {
                byLoc.getOrPut(loc) { mutableListOf() } += shortcut
            }
        }
        for ((loc, shortcuts) in byLoc) {
            onOpLoc1(loc) { use(shortcuts) }
            val apRange = shortcuts.maxOf { it.apRange }
            if (apRange > 0) {
                onApLoc1(loc) {
                    if (isWithinApRange(it.loc, apRange)) {
                        use(shortcuts)
                    }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.use(candidates: List<AgilityShortcut>) {
        arriveDelay()
        val shortcut = candidates.minBy { it.distanceTo(coords) }
        if (player.agilityLvl < shortcut.level) {
            mes("You need an Agility level of ${shortcut.level} to use this shortcut.")
            return
        }
        val quest = shortcut.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest.key)) {
            mes("You need to complete ${quest.name} to use this shortcut.")
            return
        }
        val fromA = shortcut.startsFromA(coords)
        if (shortcut.oneWay && !fromA) {
            mes("You can't climb the rocks from this side.")
            return
        }
        val from = if (fromA) shortcut.sideA else shortcut.sideB
        val dest = if (fromA) shortcut.sideB else shortcut.sideA
        stepOnto(from)
        cross(shortcut.move, dest, fromA)
        if (shortcut.xp > 0.0) {
            statAdvance("stat.agility", shortcut.xp)
        }
    }

    private suspend fun ProtectedAccess.cross(move: ShortcutMove, dest: CoordGrid, fromA: Boolean) {
        when (move) {
            is ShortcutMove.Climb -> climbTo(dest, move.seq, move.ticks)
            is ShortcutMove.Scramble -> balanceAlong(line(coords, dest), BalanceStyle.Climbing)
            is ShortcutMove.ClimbOver -> {
                val start = coords
                anim(move.seq, delay = CLIENT_CYCLES_PER_TICK)
                exactMove(
                    start = start,
                    end = dest,
                    delay1 = CLIENT_CYCLES_PER_TICK,
                    delay2 = CLIMB_OVER_LANDING_CYCLE,
                    dir = emFaceTowards(start, dest),
                    teleportType = TeleportType.Exempt,
                )
                delay(CLIMB_OVER_TICKS)
            }
            is ShortcutMove.Jump -> {
                val ticks = move.ticks ?: seqGlideTicks(move.seq, fallback = 2)
                leapTo(dest, move.seq, ticks)
            }
            is ShortcutMove.Squeeze -> {
                faceSquare(dest)
                anim(move.enter)
                delay(move.ticks)
                telejump(dest, TeleportType.Exempt)
                anim(move.leave)
            }
            is ShortcutMove.Tunnel -> {
                faceSquare(dest)
                anim(move.enter)
                delay(seqTicks(move.enter, fallback = 2))
                val path = line(coords, dest)
                for (tile in path.dropLast(1)) {
                    teleport(tile, TeleportType.Exempt)
                    anim(move.walk)
                    delay(1)
                }
                telejump(dest, TeleportType.Exempt)
                anim(move.leave)
            }
            is ShortcutMove.Balance -> {
                val path = (if (fromA) move.tiles else move.tiles.reversed()) + dest
                balanceAlong(path, BalanceStyle.Tightrope)
            }
            is ShortcutMove.Hop -> {
                val stones = (if (fromA) move.stones else move.stones.reversed()) + dest
                for (stone in stones) {
                    hopTo(stone)
                }
            }
            is ShortcutMove.Pipe -> pipeThrough(dest)
        }
    }

    private companion object {
        const val CLIMB_OVER_LANDING_CYCLE = 100
        const val CLIMB_OVER_TICKS = 3
    }
}
