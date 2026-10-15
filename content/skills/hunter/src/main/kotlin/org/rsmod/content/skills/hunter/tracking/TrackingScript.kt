package org.rsmod.content.skills.hunter.tracking

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kebbit tracking. Inspecting a den rolls a hidden path through the area's node graph that ends at
 * the first hiding spot reached once [TrackingArea.minNodes] nodes (junctions excluded, repeats
 * counted) have been passed. Each segment's footprints are revealed through its varbit when the
 * player inspects that segment's connector, and the kebbit is caught by attacking the final hiding
 * spot with a noose wand.
 */
class TrackingScript
@Inject
constructor(
    private val playerList: PlayerList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val rumours: RumourTracker,
) : PluginScript() {
    private class Trail(val area: TrackingArea, val path: List<TrackSegment>, val nodes: List<Int>) {
        var revealed: Int = 0

        val target: Int
            get() = nodes.last()

        val complete: Boolean
            get() = revealed == path.size
    }

    private val trails = HashMap<PlayerUid, Trail>()

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { tick() }
        onPlayerLogin { clearAll(player) }
        onPlayerLogout {
            clearAll(player)
            trails.remove(player.uid)
        }

        for (den in DENS) {
            onOpLoc1(den) { inspectDen(it.loc) }
        }
        for (connector in TrackingArea.byConnector.keys) {
            onOpLoc1(connector) { inspectConnector(connector, it.loc) }
        }
        for (hide in HIDES) {
            onOpLoc1(hide) { search(it.loc) }
            onOpLoc2(hide) { attack(it.loc) }
        }
    }

    private fun tick() {
        if (trails.isEmpty() || mapClock.cycle % CHECK_INTERVAL != 0) {
            return
        }
        for ((uid, trail) in trails.entries.toList()) {
            val player = uid.resolve(playerList) ?: continue
            val anchor = trail.area.nodes.first { it.dens.isNotEmpty() }.dens.first()
            val dx = abs(player.coords.x - anchor.x)
            val dz = abs(player.coords.z - anchor.z)
            if (maxOf(dx, dz) > LEAVE_DISTANCE) {
                clear(player, trail)
                trails.remove(uid)
            }
        }
    }

    private suspend fun ProtectedAccess.inspectDen(loc: BoundLocInfo) {
        val area = TrackingArea.forDen(loc.coords) ?: return
        val start = area.nodeWithDen(loc.coords) ?: return
        if (player.hunterLvl < area.level) {
            mes("You need a Hunter level of ${area.level} to track a ${area.displayName}.")
            return
        }
        faceSquare(loc.coords)
        anim(INSPECT_SEQ)
        delay(INSPECT_CYCLES)
        trails.remove(player.uid)?.let { clear(player, it) }
        val trail = generate(area, start.index)
        if (trail == null) {
            mes("You find no signs of a kebbit here.")
            return
        }
        trails[player.uid] = trail
        reveal(player, trail)
        mes("You discover some tracks leading away from here.")
    }

    private suspend fun ProtectedAccess.inspectConnector(connector: String, loc: BoundLocInfo) {
        val (area, segment) = TrackingArea.byConnector[connector] ?: return
        faceSquare(loc.coords)
        anim(INSPECT_SEQ)
        delay(INSPECT_CYCLES)
        val trail = trails[player.uid]
        if (trail == null || trail.area != area || trail.complete) {
            mes("You search but find nothing out of the ordinary.")
            return
        }
        if (trail.path[trail.revealed] != segment) {
            mes("You search but find no tracks leading this way.")
            return
        }
        reveal(player, trail)
        mes("You find some tracks!")
    }

    private suspend fun ProtectedAccess.search(loc: BoundLocInfo) {
        val area = TrackingArea.forHide(loc.coords) ?: return
        faceSquare(loc.coords)
        anim(INSPECT_SEQ)
        delay(INSPECT_CYCLES)
        if (isTarget(area, loc)) {
            mes("There seems to be something hiding in here.")
        } else {
            mes("You search but find nothing.")
        }
    }

    private suspend fun ProtectedAccess.attack(loc: BoundLocInfo) {
        val area = TrackingArea.forHide(loc.coords) ?: return
        val trail = trails[player.uid]
        if (trail == null || !isTarget(area, loc)) {
            mes("There's nothing hiding here.")
            return
        }
        if (!player.righthand.isType(NOOSE_WAND)) {
            mes("You need to be wielding a noose wand to catch the ${area.displayName}.")
            return
        }
        if (inv.freeSpace() < area.loot.size) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        faceSquare(loc.coords)
        anim(NOOSE_SEQ)
        delay(NOOSE_CYCLES)
        if (trails[player.uid] !== trail) {
            return
        }
        trails.remove(player.uid)
        clear(player, trail)
        if (random.randomDouble() >= catchChance(area)) {
            mes("The ${area.displayName} escapes!")
            return
        }
        area.loot.forEach { invAdd(inv, it) }
        statAdvance(TrapManager.STAT, area.xp * xpMods.get(player, TrapManager.STAT))
        mes("You've caught a ${area.displayName}.")
        rumours.onCatch(player, area.name)
    }

    private fun ProtectedAccess.catchChance(area: TrackingArea): Double =
        (CATCH_BASE + CATCH_PER_LEVEL * (player.hunterLvl - area.level)).coerceAtMost(1.0)

    private fun ProtectedAccess.isTarget(area: TrackingArea, loc: BoundLocInfo): Boolean {
        val trail = trails[player.uid] ?: return false
        if (trail.area != area || !trail.complete) {
            return false
        }
        return area.nodeWithHide(loc.coords)?.index == trail.target
    }

    private fun generate(area: TrackingArea, start: Int): Trail? {
        repeat(GENERATE_ATTEMPTS) {
            val used = HashSet<TrackSegment>()
            val path = ArrayList<TrackSegment>()
            val nodes = arrayListOf(start)
            var node = start
            var visited = 1
            while (path.size < MAX_PATH) {
                val options = area.segmentsAt(node).filter { it !in used }
                if (options.isEmpty()) {
                    break
                }
                val segment = options[random.of(options.size)]
                used += segment
                path += segment
                node = segment.other(node)
                nodes += node
                val next = area.nodes[node]
                if (!next.junction) {
                    visited++
                }
                if (visited >= area.minNodes && next.hides.isNotEmpty()) {
                    return Trail(area, path, nodes)
                }
            }
        }
        return null
    }

    private fun reveal(player: Player, trail: Trail) {
        val index = trail.revealed
        if (index > 0) {
            setSegment(player, trail, index - 1, fade = true)
        }
        setSegment(player, trail, index, fade = false)
        trail.revealed = index + 1
    }

    private fun setSegment(player: Player, trail: Trail, index: Int, fade: Boolean) {
        val segment = trail.path[index]
        val fromA = trail.nodes[index] == segment.a
        val forward = fromA == segment.forwardFromA
        val base = if (forward) VISIBLE else VISIBLE_REVERSED
        VarPlayerIntMapSetter.set(player, segment.varbit, if (fade) base + FADE_OFFSET else base)
    }

    private fun clear(player: Player, trail: Trail) {
        for (segment in trail.area.segments) {
            if (player.vars[segment.varbit] != 0) {
                VarPlayerIntMapSetter.set(player, segment.varbit, 0)
            }
        }
    }

    private fun clearAll(player: Player) {
        for (area in TrackingArea.entries) {
            for (segment in area.segments) {
                if (player.vars[segment.varbit] != 0) {
                    VarPlayerIntMapSetter.set(player, segment.varbit, 0)
                }
            }
        }
    }

    private companion object {
        const val VISIBLE = 3
        const val VISIBLE_REVERSED = 4
        const val FADE_OFFSET = 2
        const val INSPECT_CYCLES = 2
        const val NOOSE_CYCLES = 2
        const val CHECK_INTERVAL = 5
        const val LEAVE_DISTANCE = 40
        const val GENERATE_ATTEMPTS = 50
        const val MAX_PATH = 20
        const val CATCH_BASE = 0.93
        const val CATCH_PER_LEVEL = 0.035
        const val INSPECT_SEQ = "seq.hunting_searching_bushes"
        const val NOOSE_SEQ = "seq.hunting_noose_catch"
        const val NOOSE_WAND = "obj.noose_wand"

        val DENS =
            listOf(
                "loc.hunting_trail_spawn_polar1",
                "loc.hunting_trail_spawn_polar2",
                "loc.hunting_trail_spawn1",
                "loc.hunting_trail_spawn2",
                "loc.hunting_trail_spawn3",
                "loc.hunting_trail_spawn4",
                "loc.hunting_trail_spawn5",
                "loc.hunting_trail_spawn6",
                "loc.hunting_trail_spawn_jungle1",
                "loc.hunting_trail_spawn_jungle2",
                "loc.hunting_trail_spawn_desert1",
                "loc.hunting_trail_spawn_desert2",
            )

        val HIDES =
            listOf(
                "loc.hunting_trail_end_polar",
                "loc.hunting_trail_end_bush29",
                "loc.hunting_trail_end_bush35_55",
                "loc.hunting_trail_end_jungle",
                "loc.hunting_trail_end_desert",
            )
    }
}
