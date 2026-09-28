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
import org.rsmod.game.loc.LocAngle
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
    private val traps = LinkedHashMap<CoordGrid, Trap>()
    private val engagedNpcs = HashSet<Int>()

    fun at(coords: CoordGrid): Trap? = traps[coords]

    fun countOwnedBy(player: Player): Int = traps.values.count { it.owner == player.uid }

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

    fun isTileFree(coords: CoordGrid): Boolean =
        coords !in traps &&
            locRepo.findExact(coords, LocShape.CentrepieceStraight) == null &&
            locRepo.findExact(coords, LocShape.CentrepieceDiagonal) == null

    fun lay(player: Player, kind: TrapKind, coords: CoordGrid): Trap {
        val loc = spawnLoc(coords, kind.setLoc)
        val trap = Trap(kind, player.uid, player.displayName, coords, loc)
        traps[coords] = trap
        enterState(trap, TrapState.Set)
        trap.nextHuntCycle = mapClock + HUNT_INTERVAL
        return trap
    }

    fun remove(trap: Trap) {
        releaseTarget(trap)
        traps.remove(trap.coords)
        locRepo.del(trap.loc, Int.MAX_VALUE)
    }

    fun collapseAll(owner: PlayerUid) {
        traps.values.filter { it.owner == owner }.forEach { collapse(it, owner = null) }
    }

    fun tick() {
        if (traps.isEmpty()) {
            return
        }
        for (trap in traps.values.toList()) {
            if (traps[trap.coords] !== trap) {
                continue
            }
            process(trap)
        }
    }

    private fun process(trap: Trap) {
        val owner = trap.owner.resolve(playerList)
        if (owner == null) {
            collapse(trap, owner = null)
            return
        }
        if (owner.coords.level != trap.coords.level ||
            owner.coords.chebyshevDistance(trap.coords) > MAX_OWNER_DISTANCE
        ) {
            collapse(trap, owner)
            return
        }
        when (trap.state) {
            TrapState.Set -> processSet(trap, owner)
            TrapState.Luring -> processLuring(trap, owner)
            TrapState.Trapping -> advance(trap, TrapState.Full, trap.prey?.fullLoc)
            TrapState.Failing -> advance(trap, TrapState.Failed, trap.kind.failedLoc)
            TrapState.Full,
            TrapState.Failed -> if (mapClock >= trap.expireCycle) collapse(trap, owner)
        }
    }

    private fun processSet(trap: Trap, owner: Player) {
        if (mapClock >= trap.expireCycle) {
            collapse(trap, owner)
            return
        }
        if (mapClock < trap.nextHuntCycle) {
            return
        }
        trap.nextHuntCycle = mapClock + HUNT_INTERVAL
        if (isPlayerOnTile(trap.coords)) {
            return
        }
        val target = findPrey(trap) ?: return
        val prey = TrapPrey.byNpc[target.type.internalName] ?: return
        lure(trap, target, prey)
    }

    private fun findPrey(trap: Trap): Npc? {
        val preyTypes = TrapPrey.forKind(trap.kind).map { it.npc }
        val candidates =
            npcRepo.findAll(ZoneKey.from(trap.coords), zoneRadius = 1).filter { npc ->
                npc.isSlotAssigned &&
                    npc.isVisible &&
                    npc.coords.level == trap.coords.level &&
                    npc.coords.chebyshevDistance(trap.coords) <= PREY_RANGE &&
                    npc.uid.packed !in engagedNpcs &&
                    preyTypes.any { npc.isType(it) }
            }.toList()
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
            return trap.coords
        }
        val dir = sideOf(trap.coords, target.coords)
        return trap.coords.translate(dir.xOff, dir.zOff)
    }

    private fun processLuring(trap: Trap, owner: Player) {
        val target = trap.target
        val prey = trap.prey
        if (target == null || prey == null || !isTargetValid(trap, target)) {
            cancelLure(trap)
            return
        }
        val distance = target.coords.chebyshevDistance(trap.coords)
        val arrived = distance <= trap.kind.catchRange && target.routeDestination.isEmpty()
        if (!arrived && mapClock < trap.lureDeadline) {
            return
        }
        if (distance > trap.kind.catchRange + 1 || isPlayerOnTile(trap.coords)) {
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
        val trappingLoc = prey.trappingLocs.getValue(side)
        releaseTarget(trap)
        npcRepo.despawn(target, PREY_RESPAWN)
        trap.loc = spawnLoc(trap.coords, trappingLoc)
        enterState(trap, TrapState.Trapping)
    }

    private fun escape(trap: Trap, target: Npc, prey: TrapPrey) {
        releaseTarget(trap)
        prey.escapeAnim?.let { target.anim(it, delay = 0, priority = 0) }
        trap.loc = spawnLoc(trap.coords, trap.kind.failingLoc)
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
        trap.loc = spawnLoc(trap.coords, loc)
        enterState(trap, next)
    }

    private fun collapse(trap: Trap, owner: Player?) {
        remove(trap)
        val receiver = owner?.takeIf { it.isSlotAssigned }
        objRepo.add(trap.kind.item, trap.coords, GROUND_DURATION, receiver = receiver)
        owner?.mes("Your ${trap.kind.trapName} has collapsed.")
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

    private fun isPlayerOnTile(coords: CoordGrid): Boolean =
        playerList.any { it.coords == coords }

    private fun spawnLoc(coords: CoordGrid, loc: String) =
        locRepo.add(coords, loc, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)

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
        const val TRAP_LIFETIME = 100
        const val PREY_RESPAWN = 10
        const val GROUND_DURATION = 300
        const val MAX_OWNER_DISTANCE = 32
        const val SMOKE_BONUS = 0.02
    }
}
