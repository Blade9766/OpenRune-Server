package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.ObjInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifCloseInputDialog
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.other.bots.core.BotGeometry
import org.rsmod.content.other.bots.core.DialogueScreen
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
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

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
    private val spells: MagicSpellRegistry,
    val wayfinder: WayfinderTravel,
) {
    private val routeFinding = RouteFinding(collision)

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

    /**
     * Clicks "Open" on the closed door or gate nearest [player] that lies within [slack] tiles of
     * the straight line to [toward], no further than [reach] tiles away, so a stuck bot only opens
     * doors in its way rather than every door in sight.
     */
    fun openDoorToward(player: Player, toward: CoordGrid, slack: Int, reach: Int): Boolean {
        val from = player.coords
        val (loc, type) =
            findLocs(from, reach) { openOp(it) != null }
                .filter { (loc, _) ->
                    loc.coords.level == from.level &&
                        BotGeometry.distanceToSegment(loc.coords.x, loc.coords.z, from.x, from.z, toward.x, toward.z) <= slack
                }
                .minByOrNull { (loc, _) -> loc.coords.chebyshevDistance(from) }
                ?: return false
        val op = openOp(type) ?: return false
        return opLoc(player, loc, type, op)
    }

    /**
     * Clicks [op] on the loc named [name] nearest [at] (a ladder, trapdoor or obstacle), or "Open"
     * on it when it is closed and has no [op] yet. Names and ops are read from the variant [player]
     * sees, so multilocs such as Shilo Village's broken cart match too.
     */
    fun climb(player: Player, at: CoordGrid, name: String, op: String): Boolean {
        val candidates =
            findLocs(at, CLIMB_RADIUS) { true }
                .map { (loc, type) -> Triple(loc, type, visibleType(player, loc, type)) }
                .filter { (_, _, seen) -> seen.name.equals(name, ignoreCase = true) }
        for (action in listOf(op, "Open")) {
            val (loc, type, seen) =
                candidates
                    .filter { (_, _, seen) -> namedOp(seen, action) != null }
                    .minByOrNull { (loc, _, _) -> loc.coords.chebyshevDistance(at) }
                    ?: continue
            return opLoc(player, loc, type, namedOp(seen, action)!!)
        }
        return false
    }

    private fun visibleType(player: Player, loc: LocInfo, type: ObjectServerType): ObjectServerType {
        val multi = locInteractions.multiLoc(BoundLocInfo(loc, type), type, player.vars) ?: return type
        return ServerCacheManager.getObject(multi.id) ?: type
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

    /** Uses op 2 ("Wear"/"Wield") on the obj in [slot], exactly as the inventory button would. */
    fun wear(player: Player, slot: Int): Boolean =
        protectedAccess.launch(player) {
            clearPendingAction()
            heldInteractions.interact(this, player.inv, slot, HeldOp.Op2)
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

    /**
     * Answers [player]'s paused modal (a make-x menu) with [component] and [sub], as a
     * `ResumePauseButton` packet would; false when no script is waiting on it.
     */
    @OptIn(InternalApi::class)
    fun resumePauseButton(player: Player, component: String, sub: Int): Boolean {
        if (player.activeCoroutine?.isSuspended != true) {
            return false
        }
        val packed = runCatching { component.asRSCM(RSCMType.COMPONENT) }.getOrNull() ?: return false
        val modal = player.ui.modals.getComponent(UserInterface(ServerCacheManager.fromInterface(packed))) ?: return false
        player.ui.queueClose(modal)
        player.resumeActiveCoroutine(ResumePauseButtonInput(component, sub))
        return true
    }

    /** The dialogue [player] has open, or null when its modal is something else. */
    fun dialogueScreen(player: Player, tap: BotDialogueTap): DialogueScreen? {
        if (player.ui.containsModal(CHAT_MENU)) {
            return DialogueScreen.Choice(tap.choices)
        }
        val page = DIALOGUE_PAGES.entries.firstOrNull { player.ui.containsModal(it.key) } ?: return null
        return DialogueScreen.Page(page.value)
    }

    /** Clicks the option named [op] on the nearest npc matching [npc] (a gameval or `name:`) within [radius] of [at]. */
    fun talkTo(player: Player, at: CoordGrid, radius: Int, npc: String, op: String): Boolean {
        val matches = npcMatcher(npc) ?: return false
        val target =
            findNpcs(at, radius) { matches(it) && namedNpcOp(it, op) != null }.minByOrNull { it.coords.chebyshevDistance(player.coords) }
                ?: return false
        return opNpc(player, target, namedNpcOp(target, op)!!)
    }

    private fun npcMatcher(npc: String): ((Npc) -> Boolean)? {
        if (npc.startsWith(NAME_PREFIX)) {
            val name = npc.removePrefix(NAME_PREFIX)
            return { it.type.name.equals(name, ignoreCase = true) }
        }
        val id = runCatching { npc.asRSCM(RSCMType.NPC) }.getOrNull() ?: return null
        return { it.type.id == id }
    }

    private fun namedNpcOp(npc: Npc, action: String): InteractionOp? =
        when ((0 until 5).firstOrNull { npc.type.actions.getOpOrNull(it).equals(action, ignoreCase = true) }) {
            0 -> InteractionOp.Op1
            1 -> InteractionOp.Op2
            2 -> InteractionOp.Op3
            3 -> InteractionOp.Op4
            4 -> InteractionOp.Op5
            else -> null
        }

    fun telejump(player: Player, dest: CoordGrid) {
        player.clearPendingAction(eventBus)
        PathingEntityCommon.telejump(player, collision, dest)
    }

    /**
     * Clicks Home Teleport in the spellbook, as an `If3Button` packet would: the spell script
     * applies its own checks, animation, delay and destination.
     */
    fun castHomeTeleport(player: Player): Boolean {
        val obj = runCatching { ServerCacheManager.getItem(HOME_TELEPORT.asRSCM(RSCMType.OBJ)) }.getOrNull() ?: return false
        val spell = spells.getObjSpell(obj) ?: return false
        return protectedAccess.launch(player) {
            clearPendingAction()
            eventBus.publish(this, IfOverlayButton(spell.component, comsub = -1, obj = null, op = IfButtonOp.Op1))
        }
    }

    /**
     * True when the pathfinder can walk [player] to [dest] with doors as they are now. A waypoint on
     * a tile nobody can stand on (a tree, a crate) counts once the bot can get next to it.
     */
    fun canReach(player: Player, dest: CoordGrid): Boolean {
        if (dest.level != player.coords.level) {
            return false
        }
        val from = player.coords
        val route = routeFinding.findRoute(from.level, from.x, from.z, dest.x, dest.z)
        if (!route.success || !route.alternative) {
            return route.success
        }
        if (collision[dest.x, dest.z, dest.level] and (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK) == 0) {
            return false
        }
        val end = route.waypoints.lastOrNull()?.let { CoordGrid(it.x, it.z, it.level) } ?: from
        return end.chebyshevDistance(dest) <= 1
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
        private const val HOME_TELEPORT = "obj.48_home_teleport"
        private const val NAME_PREFIX = "name:"
        private const val CHAT_MENU = "interface.chatmenu"
        private val DIALOGUE_PAGES =
            mapOf(
                "interface.chat_left" to "component.chat_left:continue",
                "interface.chat_right" to "component.chat_right:continue",
                "interface.messagebox" to "component.messagebox:continue",
                "interface.objectbox" to "component.objectbox:universe",
            )
    }
}

class BotClaims(private val locs: Map<CoordGrid, Int>, private val npcs: Set<Npc>) {
    fun on(loc: LocInfo): Int = locs[loc.coords] ?: 0

    fun taken(npc: Npc): Boolean = npc in npcs

    companion object {
        val NONE: BotClaims = BotClaims(emptyMap(), emptySet())
    }
}
