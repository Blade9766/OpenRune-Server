package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.ObjInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.ifCloseInputDialog
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.content.travel.wayfinder.WayfinderTravel
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionObjOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The bot's "mouse": every click goes through the same interaction and routing objects a client
 * packet would create (see `OpLocHandler` / `OpNpcHandler`), so all normal content scripts, level
 * checks, timings and depletion rules apply to bots unchanged.
 */
class BotWorldServices
@Inject
constructor(
    val eventBus: EventBus,
    private val locRegistry: LocRegistry,
    private val locInteractions: LocInteractions,
    private val npcInteractions: NpcInteractions,
    private val npcRegistry: NpcRegistry,
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
    private val objRegistry: ObjRegistry,
    private val objInteractions: ObjInteractions,
    private val heldInteractions: HeldInteractions,
    private val protectedAccess: ProtectedAccessLauncher,
    val wayfinder: WayfinderTravel,
) {
    /** Ground objs [player] can see within [radius] of [center]. */
    fun findObjs(player: Player, center: CoordGrid, radius: Int, filter: (Obj) -> Boolean): List<Obj> =
        zonesAround(center, radius)
            .flatMap { objRegistry.findAll(it) }
            .filter { it.coords.chebyshevDistance(center) <= radius && it.isVisibleTo(player) && filter(it) }

    fun pickUp(player: Player, obj: Obj): Boolean {
        val type = ServerCacheManager.getItem(obj.type) ?: return false
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceSquare(obj.coords)
        player.interaction =
            InteractionObjOp(
                target = obj,
                op = InteractionOp.Op3,
                hasOpTrigger = objInteractions.hasOpTrigger(obj, InteractionOp.Op3, type),
                hasApTrigger = objInteractions.hasApTrigger(obj, InteractionOp.Op3, type),
            )
        player.routeRequest = RouteRequestCoord(obj.coords)
        return true
    }

    /** Runs [block] with [player]'s protected access, as a script triggered by a click would. */
    fun launch(player: Player, block: suspend ProtectedAccess.() -> Unit): Boolean = protectedAccess.launch(player, block = block)

    /** Clicks "Open" on the nearest closed door or gate within [radius] tiles of [player]. */
    fun openNearbyDoor(player: Player, radius: Int): Boolean {
        val (loc, type) =
            findLocs(player.coords, radius) { openOp(it) != null }
                .minByOrNull { (loc, _) -> loc.coords.chebyshevDistance(player.coords) }
                ?: return false
        val op = openOp(type) ?: return false
        return opLoc(player, loc, type, op)
    }

    /**
     * Clicks [op] on the loc named [name] nearest [at] (a ladder or trapdoor), or "Open" on it when
     * it is closed and has no [op] yet.
     */
    fun climb(player: Player, at: CoordGrid, name: String, op: String): Boolean {
        val candidates = findLocs(at, CLIMB_RADIUS) { it.name.equals(name, ignoreCase = true) }
        for (action in listOf(op, "Open")) {
            val (loc, type) =
                candidates
                    .filter { (_, type) -> namedOp(type, action) != null }
                    .minByOrNull { (loc, _) -> loc.coords.chebyshevDistance(at) }
                    ?: continue
            return opLoc(player, loc, type, namedOp(type, action)!!)
        }
        return false
    }

    private fun openOp(type: ObjectServerType): InteractionOp? = namedOp(type, "Open")

    private fun namedOp(type: ObjectServerType, action: String): InteractionOp? =
        when ((0 until 5).firstOrNull { type.actions.getOpOrNull(it).equals(action, ignoreCase = true) }) {
            0 -> InteractionOp.Op1
            1 -> InteractionOp.Op2
            2 -> InteractionOp.Op3
            3 -> InteractionOp.Op4
            4 -> InteractionOp.Op5
            else -> null
        }

    /** Uses op 1 ("Eat"/"Drink") on the obj in [slot], exactly as the inventory button would. */
    fun eat(player: Player, slot: Int): Boolean =
        protectedAccess.launch(player) {
            clearPendingAction()
            heldInteractions.interact(this, player.inv, slot, HeldOp.Op1)
        }

    /** Presses op 1 of [component] on [player]'s open modal, as an `If3Button` packet would. */
    @OptIn(InternalApi::class)
    fun clickModalButton(player: Player, component: String): Boolean {
        val type =
            runCatching { ServerCacheManager.fromComponent(component.asRSCM(RSCMType.COMPONENT)) }.getOrNull()
                ?: return false
        player.ifCloseInputDialog()
        protectedAccess.launchLenient(player) {
            eventBus.publish(this, IfModalButton(type, comsub = -1, obj = null, op = IfButtonOp.Op1))
        }
        return true
    }

    fun telejump(player: Player, dest: CoordGrid) {
        player.clearPendingAction(eventBus)
        PathingEntityCommon.telejump(player, collision, dest)
    }

    /** Casts a standard-spellbook teleport to [dest], with the spell's animation, graphic and delay. */
    fun teleport(player: Player, dest: CoordGrid): Boolean =
        protectedAccess.launch(player) {
            clearPendingAction()
            anim(TELEPORT_ANIM)
            spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
            soundSynth(TELEPORT_SOUND)
            delay(TELEPORT_DELAY)
            telejump(dest, TeleportType.Exempt)
            anim(TELEPORT_END_ANIM)
        }

    fun walkTo(player: Player, dest: CoordGrid) {
        if ((player.interaction != null || player.weakQueueList.isNotEmpty) && !player.isDelayed) {
            player.clearPendingAction(eventBus)
        }
        player.routeRequest = RouteRequestCoord(dest)
    }

    fun opLoc(player: Player, loc: LocInfo, type: ObjectServerType, op: InteractionOp): Boolean {
        val bound = BoundLocInfo(loc, type)
        if (!locInteractions.hasOp(bound, type, player.vars, op)) {
            return false
        }
        val interaction =
            InteractionLocOp(
                target = bound,
                op = op,
                hasOpTrigger = locInteractions.hasOpTrigger(player, bound, op, type),
                hasApTrigger = locInteractions.hasApTrigger(player, bound, op, type),
            )
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceLoc(loc, type.width, type.length)
        player.interaction = interaction
        player.routeRequest = RouteRequestLoc(loc, type)
        return true
    }

    fun opNpc(player: Player, npc: Npc, op: InteractionOp): Boolean {
        if (npc.isDelayed || !npcInteractions.hasOp(npc, player.vars, op)) {
            return false
        }
        val interaction =
            InteractionNpcOp(
                target = npc,
                op = op,
                hasOpTrigger = npcInteractions.hasOpTrigger(player, npc, op),
                hasApTrigger = npcInteractions.hasApTrigger(player, npc, op),
            )
        player.clearPendingAction(eventBus)
        player.faceNpc(npc)
        player.interaction = interaction
        player.routeRequest = RouteRequestPathingEntity(npc.avatar)
        return true
    }

    /** Every loc within [radius] tiles of [center] whose type satisfies [filter]. */
    fun findLocs(
        center: CoordGrid,
        radius: Int,
        filter: (ObjectServerType) -> Boolean,
    ): List<Pair<LocInfo, ObjectServerType>> {
        val found = ArrayList<Pair<LocInfo, ObjectServerType>>()
        for (zone in zonesAround(center, radius)) {
            for (loc in locRegistry.findAll(zone)) {
                if (loc.coords.chebyshevDistance(center) > radius) {
                    continue
                }
                val type = ServerCacheManager.getObject(loc.id) ?: continue
                if (filter(type)) {
                    found += loc to type
                }
            }
        }
        return found
    }

    fun findNpc(center: CoordGrid, radius: Int, npcTypeId: Int): Npc? =
        findNpcs(center, radius) { it.type.id == npcTypeId }.minByOrNull { it.coords.chebyshevDistance(center) }

    fun findNpcs(center: CoordGrid, radius: Int, filter: (Npc) -> Boolean): List<Npc> =
        zonesAround(center, radius)
            .flatMap { npcRegistry.findAll(it) }
            .filter { it.coords.chebyshevDistance(center) <= radius && filter(it) }

    /** Npcs near [player] fighting it right now; anyone being hit can see who is hitting them. */
    fun attackers(player: Player): List<Npc> =
        findNpcs(player.coords, ATTACKER_RADIUS) { it.isInCombat() && it.facingTarget(playerList) === player }

    /**
     * The locs and npcs other players near [center] are visibly working on: whatever their current
     * click targets, the same thing someone standing there would see.
     */
    fun claims(player: Player, center: CoordGrid, radius: Int): BotClaims {
        val locs = HashMap<CoordGrid, Int>()
        val npcs = HashSet<Npc>()
        for (other in playerList) {
            if (other === player || other.coords.level != center.level ||
                other.coords.chebyshevDistance(center) > radius + CLAIM_MARGIN
            ) {
                continue
            }
            when (val interaction = other.interaction) {
                is InteractionLocOp -> locs.merge(interaction.target.coords, 1, Int::plus)
                is InteractionNpcOp -> npcs += interaction.target
                else -> {}
            }
        }
        return BotClaims(locs, npcs)
    }

    private fun zonesAround(center: CoordGrid, radius: Int): Set<ZoneKey> {
        val zones = LinkedHashSet<ZoneKey>()
        var dx = -radius
        while (dx <= radius + ZONE_SIZE) {
            var dz = -radius
            while (dz <= radius + ZONE_SIZE) {
                val x = (center.x + dx).coerceAtMost(center.x + radius)
                val z = (center.z + dz).coerceAtMost(center.z + radius)
                zones += ZoneKey.from(CoordGrid(x, z, center.level))
                dz += ZONE_SIZE
            }
            dx += ZONE_SIZE
        }
        return zones
    }

    private companion object {
        private const val ZONE_SIZE = 8
        private const val ATTACKER_RADIUS = 10
        private const val CLAIM_MARGIN = 4
        private const val CLIMB_RADIUS = 3
        private const val TELEPORT_DELAY = 4
        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.teleport_all"
    }
}

class BotClaims(private val locs: Map<CoordGrid, Int>, private val npcs: Set<Npc>) {
    fun on(loc: LocInfo): Int = locs[loc.coords] ?: 0

    fun taken(npc: Npc): Boolean = npc in npcs

    companion object {
        val NONE: BotClaims = BotClaims(emptyMap(), emptySet())
    }
}
