package org.rsmod.content.other.castlewars.bots

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.content.interfaces.prayer.tab.Prayer
import org.rsmod.content.interfaces.prayer.tab.PrayerRepository
import org.rsmod.content.other.castlewars.CastleWarsGame
import org.rsmod.content.other.castlewars.FlagState
import org.rsmod.content.other.castlewars.Team
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid

internal sealed interface BotOrder {
    data class Walk(val coords: CoordGrid) : BotOrder

    data class UseLoc(val loc: String, val coords: CoordGrid, val op: Int) : BotOrder

    data class Attack(val target: Player) : BotOrder
}

/**
 * Decides, every cycle, what each bot does next. In priority order a bot eats when low, carries a
 * captured standard home, chases whoever holds its own standard, recovers a dropped standard,
 * fights enemies close by, and otherwise plays its role: attackers raid the enemy standard (and
 * escort a team-mate carrying it), defenders hold their own standard room, hunters patrol the
 * middle of the arena. Every action goes through the same interactions a player's clicks create.
 */
@Singleton
internal class BotBrain
@Inject
constructor(
    private val game: CastleWarsGame,
    private val eventBus: EventBus,
    private val locRegistry: LocRegistry,
    private val locInteractions: LocInteractions,
    private val playerInteractions: PlayerInteractions,
    private val heldInteractions: HeldInteractions,
    private val protectedAccess: ProtectedAccessLauncher,
    private val attackTypes: AttackTypes,
    private val prayers: PrayerRepository,
) {
    private val logger = InlineLogger()

    fun think(bot: Bot, cycle: Int) {
        val player = bot.player
        val team = game.playingTeamOf(player) ?: return
        if (player.isDelayed || player.isAccessProtected || player.hitpoints <= 0) {
            return
        }
        if (eatIfHurt(bot, cycle) || restorePrayer(bot, cycle)) {
            return
        }
        pray(bot, team, cycle)
        val order = decide(bot, team) ?: return
        perform(bot, order, cycle)
    }

    private fun eatIfHurt(bot: Bot, cycle: Int): Boolean {
        val player = bot.player
        if (player.hitpoints * 100 >= player.baseHitpointsLvl * EAT_BELOW_PERCENT || cycle - bot.ateAt < EAT_DELAY) {
            return false
        }
        val slot = player.inv.indices.firstOrNull { player.inv[it]?.id in FOOD_IDS } ?: return false
        bot.ateAt = cycle
        return protectedAccess.launch(player) { heldInteractions.interact(this, player.inv, slot, HeldOp.Op1) }
    }

    private fun restorePrayer(bot: Bot, cycle: Int): Boolean {
        val player = bot.player
        if (player.prayerLvl * 100 >= player.basePrayerLvl * RESTORE_BELOW_PERCENT || cycle - bot.restoredAt < EAT_DELAY) {
            return false
        }
        val slot = player.inv.indices.firstOrNull { player.inv[it]?.id in PRAYER_POTION_IDS } ?: return false
        bot.restoredAt = cycle
        return protectedAccess.launch(player) { heldInteractions.interact(this, player.inv, slot, HeldOp.Op1) }
    }

    /**
     * Keeps the right protection prayer up against the nearest enemy attacking this bot, reading
     * their style the way combat does. Switches come a few cycles late and are sometimes missed,
     * and the prayer is dropped once nobody has attacked for a while to spare prayer points.
     */
    private fun pray(bot: Bot, team: Team, cycle: Int) {
        val player = bot.player
        val threat =
            game.state(team.opponent).playing
                .filter { (it.interaction as? InteractionPlayerOp)?.target === player }
                .filter { it.coords.chebyshevDistance(player.coords) <= THREAT_RADIUS }
                .minByOrNull { it.coords.chebyshevDistance(player.coords) }
        val active = protections.entries.firstOrNull { player.vars[it.value.enabled] == 1 }
        if (threat == null) {
            if (active != null && cycle - bot.threatenedAt > DROP_PRAYER_AFTER) {
                togglePrayer(player, active.value)
            }
            return
        }
        bot.threatenedAt = cycle
        val wanted = styleOf(threat)
        if (active?.key == wanted || cycle - bot.prayedAt < PRAYER_REACTION || player.prayerLvl == 0) {
            return
        }
        if (Random.nextInt(100) < PRAYER_MISS_PERCENT) {
            bot.prayedAt = cycle
            return
        }
        val prayer = protections[wanted] ?: return
        togglePrayer(player, prayer)
        bot.prayedAt = cycle
    }

    private fun togglePrayer(player: Player, prayer: Prayer) {
        if (PRAYER_TOGGLE_QUEUE !in player.queueList) {
            player.strongQueue(PRAYER_TOGGLE_QUEUE, 1, args = prayer)
        }
    }

    private fun styleOf(attacker: Player): BotStyle {
        if (attacker.vars["varbit.autocast_set"] == 1) {
            return BotStyle.Magic
        }
        val type = attackTypes.get(attacker)
        return when {
            type?.isRanged == true -> BotStyle.Ranged
            type?.isMagic == true -> BotStyle.Magic
            else -> BotStyle.Melee
        }
    }

    private val protections: Map<BotStyle, Prayer> by lazy {
        val byVarbit = prayers.prayerList.associateBy { it.enabled }
        mapOf(
            BotStyle.Melee to "varbit.prayer_protectfrommelee",
            BotStyle.Ranged to "varbit.prayer_protectfrommissiles",
            BotStyle.Magic to "varbit.prayer_protectfrommagic",
        ).mapNotNull { (style, varbit) -> byVarbit[varbit]?.let { style to it } }.toMap()
    }

    private fun decide(bot: Bot, team: Team): BotOrder? {
        val player = bot.player
        val enemy = team.opponent
        if (game.carriedFlag(player) == enemy) {
            val stand = if (game.state(team).flag == FlagState.Safe) team.standLoc else team.emptyStandLoc
            return reach(player, team, team.standCoords, BotOrder.UseLoc(stand, team.standCoords, 1))
        }
        bot.ignored.values.removeIf { it <= player.currentMapClock }
        val thief = game.carrierOf(team)
        if (thief != null && canFight(bot, thief, CHASE_RADIUS)) {
            return BotOrder.Attack(thief)
        }
        game.state(team).droppedAt?.let { dropped ->
            if (bot.role != BotRole.Attacker || player.coords.chebyshevDistance(dropped) <= RECOVER_RADIUS) {
                return reach(player, team, dropped, BotOrder.UseLoc(team.droppedBannerLoc, dropped, 1))
            }
        }
        game.state(enemy).droppedAt?.let { dropped ->
            if (game.carriedFlag(player) == null) {
                return reach(player, team, dropped, BotOrder.UseLoc(enemy.droppedBannerLoc, dropped, 1))
            }
        }
        if (BotMap.placeOf(player.coords) == Place.Spawn(team) && foodCount(player) < RESTOCK_BELOW) {
            val (table, coords) = BotMap.bandageTable(team)
            return BotOrder.UseLoc(table, coords, TAKE_FIVE_OP)
        }
        if (bot.role == BotRole.Attacker) {
            return raid(bot, team)
        }
        val current = (player.interaction as? InteractionPlayerOp)?.target
        if (current != null && canFight(bot, current, KEEP_FIGHTING_RADIUS)) {
            return BotOrder.Attack(current)
        }
        nearestEnemy(bot, enemy, engageRadius(bot.role))?.let { return BotOrder.Attack(it) }
        return when (bot.role) {
            BotRole.Attacker -> raid(bot, team)
            BotRole.Defender -> reach(player, team, BotMap.guardPost(team), null)
            BotRole.Hunter -> hunt(bot, team)
        }
    }

    /**
     * Attackers run for the standard rather than trade blows: inside the enemy castle they ignore
     * everyone, and elsewhere they only turn on an enemy who is attacking them.
     */
    private fun raid(bot: Bot, team: Team): BotOrder? {
        val player = bot.player
        val enemy = team.opponent
        val inEnemyCastle = (BotMap.placeOf(player.coords) as? Place.Floor)?.team == enemy
        if (!inEnemyCastle) {
            val assailant =
                game.state(enemy).playing.firstOrNull {
                    (it.interaction as? InteractionPlayerOp)?.target === player && canFight(bot, it, ATTACKER_ENGAGE)
                }
            if (assailant != null) {
                return BotOrder.Attack(assailant)
            }
        }
        val state = game.state(enemy)
        if (state.flag == FlagState.Safe) {
            return reach(player, team, enemy.standCoords, BotOrder.UseLoc(enemy.standLoc, enemy.standCoords, 1))
        }
        val carrier = state.carrier ?: return hunt(bot, team)
        return reach(player, team, carrier.coords, null)
    }

    private fun hunt(bot: Bot, team: Team): BotOrder? {
        nearestEnemy(bot, team.opponent, HUNT_RADIUS)?.let { return BotOrder.Attack(it) }
        return reach(bot.player, team, BotMap.midfield, null)
    }

    /**
     * The order that moves [player] towards [dest]: the next stair, barrier or gate when [dest] is
     * in another part of the arena, otherwise [arrive] (or a plain walk when that is null).
     */
    private fun reach(player: Player, team: Team, dest: CoordGrid, arrive: BotOrder?): BotOrder? {
        val here = BotMap.placeOf(player.coords)
        val there = BotMap.placeOf(dest)
        if (here != there) {
            return when (val hop = BotMap.nextHop(here, there, team) { game.state(it).mainDoor }) {
                is Hop.Walk -> BotOrder.Walk(hop.coords)
                is Hop.UseLoc -> BotOrder.UseLoc(hop.loc, hop.coords, hop.op)
                null -> null
            }
        }
        if (arrive != null) {
            return arrive
        }
        return if (player.coords.chebyshevDistance(dest) <= ARRIVED_RADIUS) null else BotOrder.Walk(dest)
    }

    private fun engageRadius(role: BotRole): Int =
        when (role) {
            BotRole.Attacker -> ATTACKER_ENGAGE
            BotRole.Defender -> DEFENDER_ENGAGE
            BotRole.Hunter -> HUNTER_ENGAGE
        }

    private fun nearestEnemy(bot: Bot, enemy: Team, radius: Int): Player? =
        game.state(enemy).playing
            .filter { canFight(bot, it, radius) }
            .minByOrNull { bot.player.coords.chebyshevDistance(it.coords) }

    private fun canFight(bot: Bot, target: Player, radius: Int): Boolean {
        val player = bot.player
        if (target in bot.ignored) {
            return false
        }
        val team = game.playingTeamOf(player) ?: return false
        if (game.playingTeamOf(target) != team.opponent || target.hitpoints <= 0) {
            return false
        }
        if (target.coords in team.opponent.spawnArea || target.coords.level != player.coords.level) {
            return false
        }
        return player.coords.chebyshevDistance(target.coords) <= radius &&
            BotMap.placeOf(player.coords) == BotMap.placeOf(target.coords)
    }

    private fun perform(bot: Bot, order: BotOrder, cycle: Int) {
        val player = bot.player
        val repeated = order == bot.order
        when (order) {
            is BotOrder.Attack -> {
                val current = (player.interaction as? InteractionPlayerOp)?.target
                if (current === order.target) {
                    val stale = cycle - bot.orderedAt > STALE_ATTACK && player.currentMapClock - player.actionDelay > STALE_ATTACK
                    if (repeated && stale) {
                        bot.ignored[order.target] = player.currentMapClock + IGNORE_TICKS
                        player.clearPendingAction(eventBus)
                        bot.order = null
                    }
                    return
                }
                attack(player, order.target)
            }
            is BotOrder.Walk -> {
                if (repeated && cycle - bot.orderedAt < REISSUE_DELAY && player.hasMovedPreviousCycle) {
                    return
                }
                walk(player, order.coords)
            }
            is BotOrder.UseLoc -> {
                if (repeated && cycle - bot.orderedAt < REISSUE_DELAY && player.interaction != null) {
                    return
                }
                if (!useLoc(player, order)) {
                    return
                }
            }
        }
        if (!repeated) {
            logger.debug { "[bot ${player.displayName}] ${player.coords} -> $order" }
        }
        bot.order = order
        bot.orderedAt = cycle
    }

    private fun walk(player: Player, dest: CoordGrid) {
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.routeRequest = RouteRequestCoord(dest, clientRequest = true)
    }

    private fun attack(player: Player, target: Player) {
        val op = InteractionOp.Op2
        player.clearPendingAction(eventBus)
        player.facePlayer(target)
        player.interaction =
            InteractionPlayerOp(
                target = target,
                op = op,
                hasOpTrigger = playerInteractions.hasOpTrigger(target, op),
                hasApTrigger = playerInteractions.hasApTrigger(target, op),
            )
        player.routeRequest = RouteRequestPathingEntity(target.avatar, clientRequest = true)
    }

    private fun useLoc(player: Player, order: BotOrder.UseLoc): Boolean {
        val id = order.loc.asRSCM(RSCMType.LOC)
        val loc = locRegistry.findType(order.coords, id) ?: return false
        val type = ServerCacheManager.getObject(id) ?: return false
        val bound = BoundLocInfo(loc, type)
        val op = InteractionOp.entries[order.op - 1]
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceLoc(loc, type.width, type.length)
        player.interaction =
            InteractionLocOp(
                target = bound,
                op = op,
                hasOpTrigger = locInteractions.hasOpTrigger(player, bound, op, type),
                hasApTrigger = locInteractions.hasApTrigger(player, bound, op, type),
            )
        player.routeRequest =
            RouteRequestLoc(
                destination = order.coords,
                width = type.width,
                length = type.length,
                shape = loc.entity.shape,
                angle = loc.entity.angle,
                forceApproachFlags = type.forceApproachFlags,
                clientRequest = true,
            )
        return true
    }

    companion object {
        private val FOOD_IDS: Set<Int> by lazy {
            setOf("obj.shark", "obj.swordfish", "obj.lobster", "obj.castlewars_bandages")
                .map { it.asRSCM(RSCMType.OBJ) }
                .toSet()
        }

        private val PRAYER_POTION_IDS: Set<Int> by lazy {
            (1..4).map { "obj.${it}doseprayerrestore".asRSCM(RSCMType.OBJ) }.toSet()
        }

        fun foodCount(player: Player): Int = player.inv.objs.count { it != null && it.id in FOOD_IDS }

        private const val PRAYER_TOGGLE_QUEUE = "queue.prayer_toggle"
        private const val THREAT_RADIUS = 10
        private const val PRAYER_REACTION = 3
        private const val PRAYER_MISS_PERCENT = 20
        private const val DROP_PRAYER_AFTER = 16
        private const val RESTORE_BELOW_PERCENT = 25

        private const val RESTOCK_BELOW = 3
        private const val TAKE_FIVE_OP = 2
        private const val STALE_ATTACK = 15
        private const val IGNORE_TICKS = 50
        private const val EAT_BELOW_PERCENT = 50
        private const val EAT_DELAY = 3
        private const val CHASE_RADIUS = 25
        private const val RECOVER_RADIUS = 10
        private const val KEEP_FIGHTING_RADIUS = 12
        private const val ATTACKER_ENGAGE = 3
        private const val DEFENDER_ENGAGE = 8
        private const val HUNTER_ENGAGE = 10
        private const val HUNT_RADIUS = 18
        private const val ARRIVED_RADIUS = 2
        private const val REISSUE_DELAY = 8
    }
}
