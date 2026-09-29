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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj4
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.rumours.RumourTracker
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
    private val worldRepo: WorldRepository,
    private val xpMods: XpModifiers,
    private val invisibleLevels: InvisibleLevels,
    private val pets: PetRewards,
    private val rumours: RumourTracker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { traps.tick() }
        onPlayerLogout { traps.collapseAll(player.uid) }

        for (kind in TrapKind.entries) {
            val item = kind.item
            if (item != null) {
                onOpHeld1(item) { layFromInventory(kind) }
                onOpObj4(itemType(item)) { layFromGround(kind, it.obj) }
            }
            kind.baseLoc?.let { base -> onOpLoc1(base) { placeTrap(kind, it.loc) } }
            onOpLoc1(kind.setLoc) { dismantle(it.loc) }
            onOpLoc2(kind.setLoc) { investigate(it.loc) }
            kind.netLoc?.let { net ->
                onOpLoc1(net) { dismantle(it.loc) }
                onOpLoc2(net) { investigate(it.loc) }
                onOpLocU(net, TORCH) { smoke(it.loc) }
            }
            kind.failedLoc?.let { failed ->
                onOpLoc1(failed) { dismantle(it.loc) }
                if (kind.resettable) {
                    onOpLoc2(failed) { reset(it.loc) }
                }
            }
            if (kind.hunts) {
                onOpLocU(kind.setLoc, TORCH) { smoke(it.loc) }
            }
        }

        for (prey in TrapPrey.entries) {
            onOpLoc1(prey.fullLoc) { check(it.loc, relay = false) }
            if (prey.kind.resettable) {
                onOpLoc2(prey.fullLoc) { check(it.loc, relay = true) }
            }
        }

        onOpLoc1(RABBIT_HOLE) { flush(it.loc) }
    }

    private val TrapKind.resettable: Boolean
        get() = this == TrapKind.BoxTrap || family == TrapFamily.Net

    private suspend fun ProtectedAccess.layFromInventory(kind: TrapKind) {
        val item = kind.item ?: return
        val tile = coords
        if (!canLay(kind, tile)) {
            return
        }
        invDel(inv, item)
        if (!layTrap(kind, tile)) {
            invAdd(inv, item)
        }
    }

    private suspend fun ProtectedAccess.layFromGround(kind: TrapKind, obj: Obj) {
        val item = kind.item ?: return
        val tile = obj.coords
        if (!objRegistry.isValid(player, obj) || !canLay(kind, tile)) {
            return
        }
        objRepo.del(obj)
        if (!layTrap(kind, tile)) {
            invAddOrDrop(objRepo, item)
        }
    }

    private suspend fun ProtectedAccess.layTrap(kind: TrapKind, tile: CoordGrid): Boolean {
        stopAction()
        mes("You begin setting up the trap.")
        anim(LAY_SEQ)
        delay(LAY_CYCLES)
        if (!traps.isTileFree(tile) || !traps.hasRoomFor(player, kind, tile)) {
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
        if (!meetsRequirements(kind) || !hasRoom(kind, tile)) {
            return false
        }
        if (!traps.isTileFree(tile)) {
            mes("You can't lay a trap here.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.meetsRequirements(kind: TrapKind): Boolean {
        if (player.hunterLvl < kind.levelReq) {
            mes("You need a Hunter level of ${kind.levelReq} to set up a ${kind.trapName}.")
            return false
        }
        if (kind == TrapKind.BoxTrap && !QuestRequirements.hasCompleted(player, EAGLES_PEAK)) {
            mes("You need to complete Eagles' Peak to set up a box trap.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.hasRoom(kind: TrapKind, tile: CoordGrid): Boolean {
        if (traps.hasRoomFor(player, kind, tile)) {
            return true
        }
        val max = traps.limitFor(player, kind, tile)
        val plural = if (max == 1) "trap" else "traps"
        if (kind.maxTraps != null && traps.countOwnedBy(player, kind) >= kind.maxTraps) {
            mes("You can only set up $max ${kind.trapName} $plural at a time.")
        } else {
            mes("You don't have a high enough Hunter level to set up more than $max $plural.")
        }
        return false
    }

    private suspend fun ProtectedAccess.placeTrap(kind: TrapKind, base: BoundLocInfo) {
        if (!meetsRequirements(kind) || !hasRoom(kind, base.coords)) {
            return
        }
        if (!traps.isBaseFree(base)) {
            mes("Someone has already set up a trap here.")
            return
        }
        val spent = takeMaterials(kind) ?: return
        stopAction()
        mes("You begin setting up the trap.")
        anim(if (kind.family == TrapFamily.Net) NET_SET_SEQ else LAY_SEQ)
        traps.showSetting(base, kind)
        delay(LAY_CYCLES)
        if (!traps.isBaseFree(base) || !traps.hasRoomFor(player, kind, base.coords)) {
            spent.forEach { invAddOrDrop(objRepo, it) }
            return
        }
        val trap = traps.place(player, kind, base)
        stepOff(trap)
        faceSquare(base.coords)
    }

    private fun ProtectedAccess.takeMaterials(kind: TrapKind): List<String>? =
        when (kind.family) {
            TrapFamily.Net -> {
                if (!inv.contains("obj.rope") || !inv.contains("obj.net")) {
                    mes("You need a rope and a small fishing net to set up this trap.")
                    null
                } else {
                    TrapManager.NET_MATERIALS.onEach { invDel(inv, it) }
                }
            }
            TrapFamily.Deadfall -> {
                val log = DEADFALL_LOGS.firstOrNull { inv.contains(it) }
                if (KNIVES.none { inv.contains(it) } || log == null) {
                    mes("You need a knife and some logs to set up this trap.")
                    null
                } else if (random.of(LOG_KEEP_ROLL) == 0) {
                    invDel(inv, log)
                    listOf(log)
                } else {
                    emptyList()
                }
            }
            TrapFamily.Laid -> emptyList()
        }

    private fun returnedItems(kind: TrapKind): List<String> =
        when (kind.family) {
            TrapFamily.Laid -> listOfNotNull(kind.item)
            TrapFamily.Net -> TrapManager.NET_MATERIALS
            TrapFamily.Deadfall -> emptyList()
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
        val returned = returnedItems(trap.kind)
        if (!hasSpaceFor(returned.map { it to 1 })) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        anim(LAY_SEQ)
        delay(1)
        if (traps.at(trap.coords) !== trap || !trap.isDismantlable) {
            return
        }
        traps.remove(trap)
        returned.forEach { invAdd(inv, it) }
        mes("You dismantle the trap.")
    }

    private suspend fun ProtectedAccess.reset(loc: BoundLocInfo) {
        val trap = ownedTrapAt(loc) ?: return
        if (trap.state != TrapState.Failed) {
            return
        }
        anim(LAY_SEQ)
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
        val loot = rollLoot(prey)
        val returned = if (relay) emptyList() else returnedItems(trap.kind).map { it to 1 }
        if (!hasSpaceFor(loot.map { it.first to it.second } + returned)) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        anim(LAY_SEQ)
        delay(1)
        if (traps.at(trap.coords) !== trap || !trap.isCheckable) {
            return
        }
        traps.remove(trap)
        for ((obj, count, rare) in loot) {
            invAdd(inv, obj, count)
            if (rare) {
                prey.rareMessage?.let(::mes)
            }
        }
        statAdvance(TrapManager.STAT, prey.xp * xpMods.get(player, TrapManager.STAT))
        mes(prey.catchMessage ?: "You've caught a ${prey.displayName}.")
        rumours.onCatch(player, prey.name)
        if (prey.petChance > 0) {
            pets.rollSkillingPet(player, CHINCHOMPA_PET, TrapManager.STAT, prey.petChance)
        }
        if (relay) {
            relay(trap)
        } else {
            returned.forEach { (obj, count) -> invAdd(inv, obj, count) }
        }
    }

    private fun ProtectedAccess.rollLoot(prey: TrapPrey): List<Triple<String, Int, Boolean>> =
        prey.loot.map { loot ->
            val rare = loot.rareObj != null && random.of(loot.rareChance) == 0
            val obj = if (rare) loot.rareObj!! else loot.obj
            Triple(obj, random.of(loot.min, loot.max), rare)
        }

    private suspend fun ProtectedAccess.relay(old: Trap) {
        val returned = returnedItems(old.kind)
        if (player.hunterLvl < old.kind.levelReq || !isRelayFree(old)) {
            returned.forEach { invAddOrDrop(objRepo, it) }
            return
        }
        anim(if (old.kind.family == TrapFamily.Net) NET_SET_SEQ else LAY_SEQ)
        delay(LAY_CYCLES)
        if (!isRelayFree(old)) {
            returned.forEach { invAddOrDrop(objRepo, it) }
            return
        }
        stepOff(traps.relay(player, old))
    }

    private fun ProtectedAccess.stepOff(trap: Trap) {
        if (coords !in trap.tiles) {
            return
        }
        val directions =
            STEP_DIRECTIONS.filter { coords.translate(it.xOff, it.zOff) !in trap.tiles }
        collision.firstStepDestination(coords, directions)?.let(::walk)
    }

    private fun isRelayFree(old: Trap): Boolean = old.tiles.all { traps.at(it) == null }

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
        anim(LAY_SEQ)
        delay(1)
        trap.smoked = true
        mes("You use the smoke from the torch to remove your scent from the trap.")
    }

    private suspend fun ProtectedAccess.flush(hole: BoundLocInfo) {
        if (player.hunterLvl < TrapKind.RabbitSnare.levelReq) {
            mes("You need a Hunter level of ${TrapKind.RabbitSnare.levelReq} to flush out rabbits.")
            return
        }
        if (!inv.contains(FERRET)) {
            mes("You need a ferret to flush out the rabbit.")
            return
        }
        stopAction()
        faceSquare(hole.coords)
        anim(LAY_SEQ)
        delay(2)
        if (!statRandom(TrapManager.STAT, FERRET_KEEP_LOW, FERRET_KEEP_HIGH, invisibleLevels)) {
            invDel(inv, FERRET)
            mes("Your ferret runs off.")
        }
        val snare = traps.flush(player, hole.coords)
        if (snare == null) {
            mes("The rabbit darts out of the hole and escapes.")
            return
        }
        spotanimMap(worldRepo, RABBIT_CAUGHT_SPOT, snare.coords)
        mes("The rabbit runs straight into your snare.")
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
        const val EAGLES_PEAK = "quest_eaglespeak"
        const val CHINCHOMPA_PET = "obj.skillpethunter_grey"
        const val LAY_CYCLES = 3
        const val LOG_KEEP_ROLL = 4
        const val FERRET_KEEP_LOW = 190
        const val FERRET_KEEP_HIGH = 255
        const val LAY_SEQ = "seq.human_laytrap"
        const val NET_SET_SEQ = "seq.hunting_setting_sapling_trap"
        const val TORCH = "obj.torch_lit"
        const val FERRET = "obj.hunting_ferret"
        const val RABBIT_HOLE = "loc.hunting_rabbit_launcher1"
        const val RABBIT_CAUGHT_SPOT = "spotanim.hunting_rabbit_caught0"

        val KNIVES = listOf("obj.knife", "obj.fletching_knife")

        val DEADFALL_LOGS =
            listOf(
                "obj.logs",
                "obj.oak_logs",
                "obj.willow_logs",
                "obj.teak_logs",
                "obj.maple_logs",
                "obj.mahogany_logs",
                "obj.yew_logs",
                "obj.magic_logs",
                "obj.achey_tree_logs",
                "obj.blisterwood_logs",
                "obj.camphor_logs",
                "obj.ironwood_logs",
                "obj.rosewood_logs",
            )

        val STEP_DIRECTIONS =
            listOf(Direction.West, Direction.East, Direction.South, Direction.North)

        fun itemType(obj: String) =
            ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("Unknown obj: $obj")
    }
}
