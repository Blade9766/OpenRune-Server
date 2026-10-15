package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
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
    private val npcList: NpcList,
    private val questRequirements: Set<SpellQuestRequirement>,
    private val collision: CollisionFlagMap,
) {
    fun telejump(player: Player, dest: CoordGrid) {
        player.clearPendingAction(eventBus)
        PathingEntityCommon.telejump(player, collision, dest)
    }

    /** [quest] is a `dbrow.quest_*` key such as `quest_runemysteries`; follows the active policy. */
    fun hasCompletedQuest(player: Player, quest: String): Boolean =
        questRequirements.all { it.hasCompleted(player, quest) }

    fun walkTo(player: Player, dest: CoordGrid) {
        if (player.interaction != null && !player.isDelayed) {
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
        val found = ArrayList<Pair<LocInfo, ObjectServerType>>()
        for (zone in zones) {
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
        npcList
            .filter { it.type.id == npcTypeId && it.coords.chebyshevDistance(center) <= radius }
            .minByOrNull { it.coords.chebyshevDistance(center) }

    private companion object {
        private const val ZONE_SIZE = 8
    }
}
