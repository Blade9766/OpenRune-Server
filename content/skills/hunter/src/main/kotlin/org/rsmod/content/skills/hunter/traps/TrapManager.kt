package org.rsmod.content.skills.hunter.traps

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

@Singleton
class TrapManager
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val areaChecker: AreaChecker,
    private val invisibleLevels: InvisibleLevels,
) {
    private val traps = LinkedHashSet<Trap>()
    private val byTile = HashMap<CoordGrid, Trap>()
    private val engagedNpcs = HashSet<Int>()

    fun at(coords: CoordGrid): Trap? = byTile[coords]

    fun countOwnedBy(player: Player): Int = traps.count { it.owner == player.uid }

    fun countOwnedBy(player: Player, kind: TrapKind): Int =
        traps.count { it.owner == player.uid && it.kind.family == kind.family }

    fun maxTraps(player: Player, coords: CoordGrid): Int {
        val level = player.hunterLvl
        val base =
            when {
                level >= 80 -> 5
                level >= 60 -> 4
                level >= 40 -> 3
                else -> 2
            }
        return if (coords.isInWilderness(areaChecker)) base + 1 else base
    }

    fun limitFor(player: Player, kind: TrapKind, coords: CoordGrid): Int {
        val max = maxTraps(player, coords)
        return kind.maxTraps?.let { minOf(it, max) } ?: max
    }

    fun hasRoomFor(player: Player, kind: TrapKind, coords: CoordGrid): Boolean {
        if (countOwnedBy(player) >= maxTraps(player, coords)) {
            return false
        }
        val familyMax = kind.maxTraps ?: return true
        return countOwnedBy(player, kind) < familyMax
    }

    fun isTileFree(coords: CoordGrid): Boolean =
        coords !in byTile &&
            locRepo.findExact(coords, LocShape.CentrepieceStraight) == null &&
            locRepo.findExact(coords, LocShape.CentrepieceDiagonal) == null

    fun lay(player: Player, kind: TrapKind, coords: CoordGrid): Trap {
        val trap =
            Trap(
                kind = kind,
                owner = player.uid,
                ownerName = player.displayName,
                coords = coords,
                angle = LocAngle.West,
                shape = LocShape.CentrepieceStraight,
                baseLoc = null,
                footprint = listOf(coords),
                netCoords = null,
            )
        register(trap)
        show(trap, kind.setLoc)
        enterState(trap, TrapState.Set)
        trap.nextHuntCycle = mapClock + HUNT_INTERVAL
        return trap
    }

    fun isBaseFree(base: BoundLocInfo): Boolean = footprintOf(base).none { it in byTile }

    fun showSetting(base: BoundLocInfo, kind: TrapKind) {
        val setting = kind.settingLoc ?: return
        locRepo.change(base, setting, SETTING_CYCLES)
    }

    fun place(player: Player, kind: TrapKind, base: BoundLocInfo): Trap {
        val baseLoc = LocInfo(base.layer, base.coords, base.entity)
        val angle = base.angle
        val shape = base.shape
        val net = if (kind.family == TrapFamily.Net) netTile(base.coords, angle) else null
        val trap =
            Trap(
                kind = kind,
                owner = player.uid,
                ownerName = player.displayName,
                coords = base.coords,
                angle = angle,
                shape = shape,
                baseLoc = baseLoc,
                footprint = footprintOf(base),
                netCoords = net,
            )
        register(trap)
        show(trap, kind.setLoc)
        enterState(trap, TrapState.Set)
        trap.nextHuntCycle = mapClock + HUNT_INTERVAL
        return trap
    }

    fun relay(player: Player, old: Trap): Trap {
        val trap =
            Trap(
                kind = old.kind,
                owner = player.uid,
                ownerName = player.displayName,
                coords = old.coords,
                angle = old.angle,
                shape = old.shape,
                baseLoc = old.baseLoc,
                footprint = old.footprint,
                netCoords = old.netCoords,
            )
        register(trap)
        show(trap, trap.kind.setLoc)
        enterState(trap, TrapState.Set)
        trap.nextHuntCycle = mapClock + HUNT_INTERVAL
        return trap
    }

    fun remove(trap: Trap) {
        releaseTarget(trap)
        traps.remove(trap)
        trap.tiles.forEach { if (byTile[it] === trap) byTile.remove(it) }
        byTile.entries.removeIf { it.value === trap }
        clearLocs(trap)
        val base = trap.baseLoc
        if (trap.baseHidden && base != null) {
            locRepo.add(base, Int.MAX_VALUE)
            trap.baseHidden = false
        }
    }

    fun collapseAll(owner: PlayerUid) {
        traps.filter { it.owner == owner }.forEach { collapse(it, owner = null) }
    }

    fun flush(player: Player, hole: CoordGrid): Trap? {
        val snare =
            traps
                .filter {
                    it.owner == player.uid &&
                        it.kind == TrapKind.RabbitSnare &&
                        it.state == TrapState.Set &&
                        it.coords.level == hole.level &&
                        it.coords.chebyshevDistance(hole) <= RABBIT_RANGE
                }
                .minByOrNull { it.coords.chebyshevDistance(hole) } ?: return null
        snare.prey = TrapPrey.WhiteRabbit
        show(snare, TrapPrey.WhiteRabbit.fullLoc)
        enterState(snare, TrapState.Full)
        return snare
    }

    fun tick() {
        if (traps.isEmpty()) {
            return
        }
        for (trap in traps.toList()) {
            if (trap in traps) {
                process(trap)
            }
        }
    }

    private fun register(trap: Trap) {
        traps += trap
        trap.tiles.forEach { byTile[it] = trap }
        byTile[trap.wideAnchor] = trap
    }

    private fun process(trap: Trap) {
        val owner = trap.owner.resolve(playerList)
        if (owner == null) {
            collapse(trap, owner = null)
            return
        }
        if (
            owner.coords.level != trap.coords.level ||
                owner.coords.chebyshevDistance(trap.coords) > MAX_OWNER_DISTANCE
        ) {
            collapse(trap, owner)
            return
        }
        when (trap.state) {
            TrapState.Set -> processSet(trap, owner)
            TrapState.Luring -> processLuring(trap, owner)
            TrapState.Trapping -> advance(trap, TrapState.Full, trap.prey?.fullLoc)
            TrapState.Failing -> processFailing(trap)
            TrapState.Full,
            TrapState.Failed -> if (mapClock >= trap.expireCycle) collapse(trap, owner)
        }
    }

    private fun processSet(trap: Trap, owner: Player) {
        if (mapClock >= trap.expireCycle) {
            collapse(trap, owner)
            return
        }
        if (!trap.kind.hunts || mapClock < trap.nextHuntCycle) {
            return
        }
        trap.nextHuntCycle = mapClock + HUNT_INTERVAL
        if (isBlockedByPlayer(trap)) {
            return
        }
        val target = findPrey(trap) ?: return
        val prey = TrapPrey.byNpc[target.type.internalName] ?: return
        lure(trap, target, prey)
    }

    private fun processFailing(trap: Trap) {
        if (mapClock < trap.stateCycle + TRANSITION_CYCLES) {
            return
        }
        val failed = trap.kind.failedLoc
        if (failed == null) {
            remove(trap)
            return
        }
        show(trap, failed)
        enterState(trap, TrapState.Failed)
    }

    private fun findPrey(trap: Trap): Npc? {
        val preyTypes = TrapPrey.forKind(trap.kind).mapNotNull { it.npc }
        val candidates =
            npcRepo
                .findAll(ZoneKey.from(trap.coords), zoneRadius = 1)
                .filter { npc ->
                    npc.isSlotAssigned &&
                        npc.isVisible &&
                        npc.coords.level == trap.coords.level &&
                        distanceTo(trap, npc.coords) <= PREY_RANGE &&
                        npc.uid.packed !in engagedNpcs &&
                        preyTypes.any { npc.isType(it) }
                }
                .toList()
        if (candidates.isEmpty()) {
            return null
        }
        return candidates[random.of(candidates.size)]
    }

    private fun lure(trap: Trap, target: Npc, prey: TrapPrey) {
        trap.state = TrapState.Luring
        trap.prey = prey
        trap.target = target
        trap.targetUid = target.uid
        trap.lureDeadline = mapClock + LURE_TIMEOUT
        engagedNpcs += target.uid.packed
        target.noneMode()
        target.walk(approachTile(trap, target))
    }

    private fun approachTile(trap: Trap, target: Npc): CoordGrid {
        if (trap.kind.catchRange == 0) {
            return trap.catchCoords
        }
        val nearest = trap.footprint.minBy { it.chebyshevDistance(target.coords) }
        val dir = sideOf(nearest, target.coords)
        return nearest.translate(dir.xOff, dir.zOff)
    }

    private fun distanceTo(trap: Trap, coords: CoordGrid): Int {
        if (trap.kind.catchRange == 0) {
            return trap.catchCoords.chebyshevDistance(coords)
        }
        return trap.footprint.minOf { it.chebyshevDistance(coords) }
    }

    private fun processLuring(trap: Trap, owner: Player) {
        val target = trap.target
        val prey = trap.prey
        if (target == null || prey == null || !isTargetValid(trap, target)) {
            cancelLure(trap)
            return
        }
        val distance = distanceTo(trap, target.coords)
        val arrived = distance <= trap.kind.catchRange && target.routeDestination.isEmpty()
        if (!arrived && mapClock < trap.lureDeadline) {
            return
        }
        if (distance > trap.kind.catchRange + 1 || isBlockedByPlayer(trap)) {
            cancelLure(trap)
            return
        }
        if (rollCatch(owner, trap, prey)) {
            catch(trap, target, prey)
        } else {
            escape(trap, target, prey)
        }
    }

    private fun rollCatch(owner: Player, trap: Trap, prey: TrapPrey): Boolean {
        if (owner.hunterLvl < prey.level) {
            return false
        }
        val level = owner.hunterLvl + invisibleLevels.get(owner, STAT)
        var chance = SkillingSuccessRate.successRate(prey.low, prey.high, level, MAX_LEVEL)
        if (trap.smoked) {
            chance += SMOKE_BONUS
        }
        return random.randomDouble() < chance
    }

    private fun catch(trap: Trap, target: Npc, prey: TrapPrey) {
        val side = sideOf(trap.coords, target.coords)
        val trappingLoc = prey.trappingLocs[side] ?: prey.fullLoc
        releaseTarget(trap)
        npcRepo.despawn(target, PREY_RESPAWN)
        show(trap, trappingLoc)
        enterState(trap, TrapState.Trapping)
    }

    private fun escape(trap: Trap, target: Npc, prey: TrapPrey) {
        releaseTarget(trap)
        prey.escapeAnim?.let { target.anim(it, delay = 0, priority = 0) }
        val failing = trap.kind.failingLoc ?: trap.kind.failedLoc
        if (failing == null) {
            remove(trap)
            return
        }
        show(trap, failing)
        enterState(trap, TrapState.Failing)
    }

    private fun cancelLure(trap: Trap) {
        releaseTarget(trap)
        trap.prey = null
        trap.state = TrapState.Set
    }

    private fun advance(trap: Trap, next: TrapState, loc: String?) {
        if (mapClock < trap.stateCycle + TRANSITION_CYCLES || loc == null) {
            return
        }
        show(trap, loc)
        enterState(trap, next)
    }

    private fun collapse(trap: Trap, owner: Player?) {
        remove(trap)
        val receiver = owner?.takeIf { it.isSlotAssigned }
        for (obj in collapseDrops(trap.kind)) {
            objRepo.add(obj, trap.catchCoords, GROUND_DURATION, receiver = receiver)
        }
        owner?.mes("Your ${trap.kind.trapName} has collapsed.")
    }

    private fun collapseDrops(kind: TrapKind): List<String> =
        when (kind.family) {
            TrapFamily.Laid -> listOfNotNull(kind.item)
            TrapFamily.Net -> NET_MATERIALS
            TrapFamily.Deadfall -> emptyList()
        }

    /**
     * Shows [loc] for the trap's current state. Net traps use a one-tile tree plus a separate net
     * loc while set, and a two-tile loc spanning tree and net afterwards; when that loc anchors
     * west or south of the tree, the map tree is hidden so it does not show through.
     */
    private fun show(trap: Trap, loc: String) {
        clearLocs(trap)
        val wide = trap.kind.family == TrapFamily.Net && loc != trap.kind.setLoc
        if (!wide) {
            trap.loc = spawnLoc(trap, trap.coords, loc)
            val netLoc = trap.kind.netLoc
            val net = trap.netCoords
            if (netLoc != null && net != null && loc == trap.kind.setLoc) {
                trap.netLoc =
                    locRepo.add(net, netLoc, Int.MAX_VALUE, trap.angle, LocShape.GroundDecor)
            }
            return
        }
        val anchor = trap.wideAnchor
        val base = trap.baseLoc
        if (anchor != trap.coords && base != null && !trap.baseHidden) {
            locRepo.del(base, Int.MAX_VALUE)
            trap.baseHidden = true
        }
        trap.loc = spawnLoc(trap, anchor, loc)
    }

    private fun clearLocs(trap: Trap) {
        trap.loc?.let { locRepo.del(it, Int.MAX_VALUE) }
        trap.netLoc?.let { locRepo.del(it, Int.MAX_VALUE) }
        trap.loc = null
        trap.netLoc = null
    }

    private fun enterState(trap: Trap, state: TrapState) {
        trap.state = state
        trap.stateCycle = mapClock.cycle
        trap.expireCycle = mapClock + TRAP_LIFETIME
    }

    private fun releaseTarget(trap: Trap) {
        val target = trap.target ?: return
        engagedNpcs -= trap.targetUid.packed
        if (isTargetValid(trap, target)) {
            target.defaultMode()
        }
        trap.target = null
    }

    private fun isTargetValid(trap: Trap, target: Npc): Boolean =
        target.isSlotAssigned && target.isVisible && target.uid == trap.targetUid

    private fun isBlockedByPlayer(trap: Trap): Boolean {
        if (trap.kind.family == TrapFamily.Deadfall) {
            return false
        }
        return playerList.any { it.coords == trap.catchCoords }
    }

    private fun spawnLoc(trap: Trap, coords: CoordGrid, loc: String): LocInfo =
        locRepo.add(coords, loc, Int.MAX_VALUE, trap.angle, trap.shape)

    private fun footprintOf(base: BoundLocInfo): List<CoordGrid> =
        (0 until base.adjustedWidth).flatMap { x ->
            (0 until base.adjustedLength).map { z -> base.coords.translate(x, z) }
        }

    private fun netTile(tree: CoordGrid, angle: LocAngle): CoordGrid =
        when (angle) {
            LocAngle.West -> tree.translate(0, 1)
            LocAngle.North -> tree.translate(1, 0)
            LocAngle.East -> tree.translate(0, -1)
            LocAngle.South -> tree.translate(-1, 0)
        }

    private fun sideOf(trap: CoordGrid, from: CoordGrid): Direction {
        val dx = from.x - trap.x
        val dz = from.z - trap.z
        if (dx == 0 && dz == 0) {
            return Direction.South
        }
        return if (abs(dz) >= abs(dx)) {
            if (dz > 0) Direction.North else Direction.South
        } else {
            if (dx > 0) Direction.East else Direction.West
        }
    }

    companion object {
        const val STAT = "stat.hunter"
        const val MAX_LEVEL = 99
        const val HUNT_INTERVAL = 3
        const val PREY_RANGE = 2
        const val LURE_TIMEOUT = 6
        const val TRANSITION_CYCLES = 2
        const val SETTING_CYCLES = 2
        const val TRAP_LIFETIME = 100
        const val PREY_RESPAWN = 10
        const val GROUND_DURATION = 300
        const val MAX_OWNER_DISTANCE = 32
        const val RABBIT_RANGE = 5
        const val SMOKE_BONUS = 0.02

        val NET_MATERIALS: List<String> = listOf("obj.rope", "obj.net")
    }
}
