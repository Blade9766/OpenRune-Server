package org.rsmod.content.skills.hunter.traps

import dev.openrune.ServerCacheManager
import dev.openrune.map.MapSingletons.collision
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj4
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TrapScript
@Inject
constructor(
    private val traps: TrapManager,
    private val objRepo: ObjRepository,
    private val objRegistry: ObjRegistry,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { traps.tick() }
        onPlayerLogout { traps.collapseAll(player.uid) }

        for (kind in TrapKind.entries) {
            onOpHeld1(kind.item) { layFromInventory(kind) }
            onOpObj4(itemType(kind.item)) { layFromGround(kind, it.obj) }
            onOpLoc1(kind.setLoc) { dismantle(it.loc) }
            onOpLoc2(kind.setLoc) { investigate(it.loc) }
            onOpLoc1(kind.failedLoc) { dismantle(it.loc) }
            onOpLocU(kind.setLoc, "obj.torch_lit") { smoke(it.loc) }
        }
        onOpLoc2(TrapKind.BoxTrap.failedLoc) { reset(it.loc) }

        for (prey in TrapPrey.entries) {
            onOpLoc1(prey.fullLoc) { check(it.loc, relay = false) }
            if (prey.kind == TrapKind.BoxTrap) {
                onOpLoc2(prey.fullLoc) { check(it.loc, relay = true) }
            }
        }
    }

    private suspend fun ProtectedAccess.layFromInventory(kind: TrapKind) {
        val tile = coords
        if (!canLay(kind, tile)) {
            return
        }
        invDel(inv, kind.item)
        if (!layTrap(kind, tile)) {
            invAdd(inv, kind.item)
        }
    }

    private suspend fun ProtectedAccess.layFromGround(kind: TrapKind, obj: Obj) {
        val tile = obj.coords
        if (!objRegistry.isValid(player, obj) || !canLay(kind, tile)) {
            return
        }
        objRepo.del(obj)
        if (!layTrap(kind, tile)) {
            invAddOrDrop(objRepo, kind.item)
        }
    }

    private suspend fun ProtectedAccess.layTrap(kind: TrapKind, tile: CoordGrid): Boolean {
        stopAction()
        mes("You begin setting up the trap.")
        anim("seq.human_laytrap")
        delay(LAY_CYCLES)
        if (!traps.isTileFree(tile) || traps.countOwnedBy(player) >= traps.maxTraps(player, tile)) {
            mes("You can't lay a trap here.")
            return false
        }
        traps.lay(player, kind, tile)
        if (coords == tile) {
            collision.firstStepDestination(tile, STEP_DIRECTIONS)?.let(::walk)
        }
        faceSquare(tile)
        return true
    }

    private fun ProtectedAccess.canLay(kind: TrapKind, tile: CoordGrid): Boolean {
        if (player.hunterLvl < kind.levelReq) {
            mes("You need a Hunter level of ${kind.levelReq} to set up a ${kind.trapName}.")
            return false
        }
        val max = traps.maxTraps(player, tile)
        if (traps.countOwnedBy(player) >= max) {
            val plural = if (max == 1) "trap" else "traps"
            mes("You don't have a high enough Hunter level to set up more than $max $plural.")
            return false
        }
        if (!traps.isTileFree(tile)) {
            mes("You can't lay a trap here.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.ownedTrapAt(loc: BoundLocInfo): Trap? {
        val trap = traps.at(loc.coords) ?: return null
        if (trap.owner != player.uid) {
            mes("This isn't your trap.")
            return null
        }
        return trap
    }

    private suspend fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        if (!trap.isDismantlable) {
            return
        }
        if (!hasSpaceFor(listOf(trap.kind.item to 1))) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        anim("seq.human_laytrap")
        delay(1)
        if (traps.at(trap.coords) !== trap || !trap.isDismantlable) {
            return
        }
        traps.remove(trap)
        invAdd(inv, trap.kind.item)
        mes("You dismantle the trap.")
    }

    private suspend fun ProtectedAccess.reset(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        if (trap.state != TrapState.Failed) {
            return
        }
        anim("seq.human_laytrap")
        delay(1)
        if (traps.at(trap.coords) !== trap || trap.state != TrapState.Failed) {
            return
        }
        traps.remove(trap)
        relay(trap)
    }

    private suspend fun ProtectedAccess.check(loc: BoundLocInfo, relay: Boolean) {
        val trap = ownedTrapAt(loc) ?: return
        val prey = trap.prey
        if (!trap.isCheckable || prey == null) {
            return
        }
        val loot = prey.loot.map { it.obj to random.of(it.min, it.max) }
        val needed = if (relay) loot else loot + (trap.kind.item to 1)
        if (!hasSpaceFor(needed)) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        anim("seq.human_laytrap")
        delay(1)
        if (traps.at(trap.coords) !== trap || !trap.isCheckable) {
            return
        }
        traps.remove(trap)
        for ((obj, count) in loot) {
            invAdd(inv, obj, count)
        }
        statAdvance(TrapManager.STAT, prey.xp * xpMods.get(player, TrapManager.STAT))
        mes("You've caught a ${prey.displayName}.")
        if (relay) {
            relay(trap)
        } else {
            invAdd(inv, trap.kind.item)
        }
    }

    private suspend fun ProtectedAccess.relay(old: Trap) {
        if (player.hunterLvl < old.kind.levelReq || !traps.isTileFree(old.coords)) {
            invAddOrDrop(objRepo, old.kind.item)
            return
        }
        anim("seq.human_laytrap")
        delay(LAY_CYCLES)
        if (!traps.isTileFree(old.coords)) {
            invAddOrDrop(objRepo, old.kind.item)
            return
        }
        traps.lay(player, old.kind, old.coords)
    }

    private fun ProtectedAccess.investigate(loc: BoundLocInfo) {
        val trap = traps.at(loc.coords) ?: return
        if (trap.owner == player.uid) {
            mes("This trap is yours.")
        } else {
            mes("This trap belongs to ${trap.ownerName}.")
        }
    }

    private suspend fun ProtectedAccess.smoke(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        if (trap.state != TrapState.Set) {
            return
        }
        if (trap.smoked) {
            mes("This trap has already been smoked.")
            return
        }
        anim("seq.human_laytrap")
        delay(1)
        trap.smoked = true
        mes("You use the smoke from the torch to remove your scent from the trap.")
    }

    private fun ProtectedAccess.hasSpaceFor(items: List<Pair<String, Int>>): Boolean {
        var slots = 0
        for ((obj, count) in items) {
            val type = itemType(obj)
            slots +=
                when {
                    type.stackable -> if (inv.count(obj) > 0) 0 else 1
                    else -> count
                }
        }
        return inv.freeSpace() >= slots
    }

    private companion object {
        const val LAY_CYCLES = 3

        val STEP_DIRECTIONS =
            listOf(Direction.West, Direction.East, Direction.South, Direction.North)

        fun itemType(obj: String) =
            ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("Unknown obj: $obj")
    }
}
