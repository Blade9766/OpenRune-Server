package org.rsmod.content.other.castlewars.bots

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocTInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.ObjInteractions
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.content.interfaces.prayer.tab.Prayer
import org.rsmod.content.interfaces.prayer.tab.PrayerRepository
import org.rsmod.content.other.castlewars.CastleWars
import org.rsmod.content.other.castlewars.CastleWarsGame
import org.rsmod.content.other.castlewars.CatapultState
import org.rsmod.content.other.castlewars.FlagState
import org.rsmod.content.other.castlewars.Team
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionLocT
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionObj
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid

internal sealed interface BotOrder {
    data class Walk(val coords: CoordGrid) : BotOrder

    data class UseLoc(val loc: String, val coords: CoordGrid, val op: Int) : BotOrder

    data class Attack(val target: Player) : BotOrder

    data class NpcOp(val target: Npc, val op: Int) : BotOrder

    data class Take(val obj: String, val coords: CoordGrid) : BotOrder

    data class UseItemOnLoc(val item: String, val loc: String, val coords: CoordGrid) : BotOrder

    data object SetUpBarricade : BotOrder

    data class Fire(val target: CoordGrid) : BotOrder
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
    private val npcInteractions: NpcInteractions,
    private val locTInteractions: LocTInteractions,
    private val objInteractions: ObjInteractions,
    private val objRegistry: ObjRegistry,
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
        if (player.ui.modals.isNotEmpty() && !player.isDelayed) {
            player.ifClose(eventBus)
        }
        if (player.isDelayed || player.isAccessProtected || player.hitpoints <= 0) {
            return
        }
        wearCloak(player, team)
        if (eatIfHurt(bot, cycle) || restorePrayer(bot, cycle) || keepRunning(bot, cycle)) {
            return
        }
        pray(bot, team, cycle)
        trackProgress(bot)
        val order = decide(bot, team) ?: return
        perform(bot, order, cycle)
    }

    private fun wearCloak(player: Player, team: Team) {
        if (player.worn[Wearpos.Back.slot]?.id == team.cloak.asRSCM(RSCMType.OBJ)) {
            return
        }
        player.worn[Wearpos.Back.slot] = InvObj(team.cloak)
        player.rebuildAppearance()
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

    /**
     * Turns run back on once energy has recovered (the game switches it off at empty); a bot
     * carrying a standard runs on any energy and bandages itself when it is running low.
     */
    private fun keepRunning(bot: Bot, cycle: Int): Boolean {
        val player = bot.player
        val carrying = game.carriedFlag(player) != null
        if (carrying && player.runEnergy < CARRIER_BANDAGE_ENERGY && cycle - bot.ateAt >= EAT_DELAY) {
            val slot = player.inv.indices.firstOrNull { player.inv[it]?.id == BANDAGES_ID }
            if (slot != null) {
                bot.ateAt = cycle
                return protectedAccess.launch(player) { heldInteractions.interact(this, player.inv, slot, HeldOp.Op1) }
            }
        }
        val resume = if (carrying) MIN_RUN_ENERGY else RESUME_RUN_ENERGY
        if (player.varMoveSpeed != MoveSpeed.Run && player.runEnergy >= resume) {
            player.setActiveMoveSpeed(MoveSpeed.Run)
        }
        return false
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
        if (thief != null) {
            if (canFight(bot, thief, CHASE_RADIUS)) {
                return BotOrder.Attack(thief)
            }
            if (bot.role != BotRole.Attacker || player.coords.chebyshevDistance(thief.coords) <= CHASE_RADIUS) {
                reach(player, team, thief.coords, null)?.let { return it }
            }
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
        if (BotMap.placeOf(player.coords) == Place.Spawn(team) && needsBandages(bot)) {
            val (table, coords) = BotMap.bandageTable(team)
            return BotOrder.UseLoc(table, coords, TAKE_FIVE_OP)
        }
        grabTinderbox(bot, team)?.let { return it }
        blockingBarricade(bot, team)?.let { return clearBarricade(bot, it) }
        if (bot.role == BotRole.Attacker) {
            return raid(bot, team)
        }
        val current = (player.interaction as? InteractionPlayerOp)?.target
        if (current != null && canFight(bot, current, KEEP_FIGHTING_RADIUS)) {
            return BotOrder.Attack(current)
        }
        nearestEnemy(bot, enemy, engageRadius(bot.role))?.let { return BotOrder.Attack(it) }
        burnableBarricade(bot, team)?.let { return BotOrder.NpcOp(it, BURN_OP) }
        return when (bot.role) {
            BotRole.Attacker -> raid(bot, team)
            BotRole.Defender -> barricade(bot, team) ?: reach(player, team, BotMap.guardPost(team), null)
            BotRole.Hunter -> siege(bot, team) ?: hunt(bot, team)
        }
    }

    /** Attackers always carry bandages to keep running with a standard; everyone restocks when low on food. */
    private fun needsBandages(bot: Bot): Boolean {
        val player = bot.player
        if (player.inv.freeSpace() == 0) {
            return false
        }
        return foodCount(player) < RESTOCK_BELOW || (bot.role == BotRole.Attacker && !player.inv.contains(BANDAGES))
    }

    /** A bot in its respawn room without a tinderbox picks up the one lying there. */
    private fun grabTinderbox(bot: Bot, team: Team): BotOrder? {
        val player = bot.player
        if (BotMap.placeOf(player.coords) != Place.Spawn(team) || player.inv.contains(TINDERBOX)) {
            return null
        }
        val spawn = BotMap.tinderboxSpawn(team)
        if (objRegistry.findAll(spawn).none { it.type == TINDERBOX_ID }) {
            return null
        }
        return BotOrder.Take(TINDERBOX, spawn)
    }

    /** Burns a barricade in the way when the bot has a tinderbox, and hacks at it otherwise. */
    private fun clearBarricade(bot: Bot, barricade: Npc): BotOrder =
        if (bot.player.inv.contains(TINDERBOX) && !game.isBurning(barricade)) {
            BotOrder.NpcOp(barricade, BURN_OP)
        } else {
            BotOrder.NpcOp(barricade, ATTACK_OP)
        }

    /** An enemy barricade close by that a bot carrying a tinderbox can set alight. */
    private fun burnableBarricade(bot: Bot, team: Team): Npc? {
        val player = bot.player
        if (!player.inv.contains(TINDERBOX)) {
            return null
        }
        val place = BotMap.placeOf(player.coords)
        return game.state(team.opponent).barricades.firstOrNull {
            !game.isBurning(it) && BotMap.placeOf(it.coords) == place &&
                it.coords.chebyshevDistance(player.coords) <= BURN_RADIUS
        }
    }

    private fun trackProgress(bot: Bot) {
        val coords = bot.player.coords
        bot.stillFor = if (coords == bot.lastCoords) bot.stillFor + 1 else 0
        bot.lastCoords = coords
    }

    /** An enemy barricade next to a bot that has been trying and failing to move past it. */
    private fun blockingBarricade(bot: Bot, team: Team): Npc? {
        val moving = bot.order is BotOrder.Walk || bot.order is BotOrder.UseLoc
        if (!moving || bot.stillFor < STUCK_TICKS) {
            return null
        }
        val coords = bot.player.coords
        return game.state(team.opponent).barricades.firstOrNull {
            it.coords.level == coords.level && it.coords.chebyshevDistance(coords) <= BLOCKED_RADIUS
        }
    }

    /**
     * Keeps the team's catapult firing: fetch rocks from the supply room, climb onto the wall,
     * work the catapult and lob a rock at the thickest knot of enemies in the field that has no
     * team-mate close enough to be caught in the blast. Null when the catapult is out of action.
     */
    private fun siege(bot: Bot, team: Team): BotOrder? {
        val player = bot.player
        when (game.catapultState(team)) {
            CatapultState.Broken -> return repairCatapult(player, team)
            CatapultState.Burning -> return extinguishCatapult(player, team)
            CatapultState.Operational -> Unit
        }
        fetchWater(player, team)?.let { return it }
        if (!player.inv.contains(ROCK)) {
            val (table, coords) = BotMap.rockTable(team)
            return reach(player, team, coords, BotOrder.UseLoc(table, coords, TAKE_FIVE_OP))
        }
        val post = BotMap.catapultPost(team)
        if (BotMap.placeOf(player.coords) != Place.Wall(team)) {
            return reach(player, team, post, null)
        }
        val target = catapultTarget(team) ?: return burnEnemyCatapult(player, team) ?: reach(player, team, post, null)
        val centre = team.catapultCoords.translate(1, 1)
        if (player.coords.chebyshevDistance(centre) > CATAPULT_REACH) {
            return BotOrder.UseLoc(team.catapultLoc, team.catapultCoords, 1)
        }
        return BotOrder.Fire(target)
    }

    /** Fetches a toolkit from the supply room and repairs the team's broken catapult. */
    private fun repairCatapult(player: Player, team: Team): BotOrder? {
        if (!player.inv.contains(TOOLKIT)) {
            val (table, coords) = BotMap.toolboxTable(team)
            return reach(player, team, coords, BotOrder.UseLoc(table, coords, TAKE_ONE_OP))
        }
        val repair = BotOrder.UseLoc(team.brokenCatapultLoc, team.catapultCoords, 1)
        return reach(player, team, BotMap.catapultPost(team), repair)
    }

    /** Pours a bucket of water over the team's burning catapult. */
    private fun extinguishCatapult(player: Player, team: Team): BotOrder? {
        fetchWater(player, team)?.let { return it }
        val douse = BotOrder.UseItemOnLoc(WATER, team.burningCatapultLoc, team.catapultCoords)
        return reach(player, team, BotMap.catapultPost(team), douse)
    }

    /** Hunters keep a bucket of water on hand: a fire burns out long before a round trip to the tap. */
    private fun fetchWater(player: Player, team: Team): BotOrder? {
        if (player.inv.contains(WATER)) {
            return null
        }
        if (player.inv.contains(BUCKET)) {
            val tap = BotMap.tap(team)
            return reach(player, team, tap, BotOrder.UseItemOnLoc(BUCKET, TAP, tap))
        }
        val (table, coords) = BotMap.bucketTable(team)
        return reach(player, team, coords, BotOrder.UseLoc(table, coords, TAKE_ONE_OP))
    }

    /** With nothing to shoot at, a hunter carrying a tinderbox sets the enemy catapult on fire. */
    private fun burnEnemyCatapult(player: Player, team: Team): BotOrder? {
        val enemy = team.opponent
        if (!player.inv.contains(TINDERBOX) || game.catapultState(enemy) != CatapultState.Operational) {
            return null
        }
        val burn = BotOrder.UseItemOnLoc(TINDERBOX, enemy.catapultLoc, enemy.catapultCoords)
        return reach(player, team, BotMap.catapultPost(enemy), burn)
    }

    private fun catapultTarget(team: Team): CoordGrid? {
        val allies = game.state(team).playing
        val enemies =
            game.state(team.opponent).playing.filter {
                it.hitpoints > 0 && BotMap.placeOf(it.coords) == Place.Field &&
                    it.coords.chebyshevDistance(team.catapultCoords) >= CATAPULT_MIN_RANGE
            }
        return enemies
            .filter { enemy -> allies.none { it.coords.chebyshevDistance(enemy.coords) <= BLAST_SAFETY } }
            .maxByOrNull { enemy -> enemies.count { it.coords.chebyshevDistance(enemy.coords) <= BLAST_RADIUS } }
            ?.coords
    }

    /**
     * Sets barricades up on the spots flanking the team's gate: fetch them from the supply room,
     * stand on a free spot and put one down. Null once the spots are filled or the team is at its
     * barricade limit.
     */
    private fun barricade(bot: Bot, team: Team): BotOrder? {
        val player = bot.player
        if (!game.canPlaceBarricade(team)) {
            return null
        }
        val spot = BotMap.barricadeSpots(team).firstOrNull { game.barricadeAt(it) == null } ?: return null
        if (!player.inv.contains(BARRICADE)) {
            val (table, coords) = BotMap.barricadeTable(team)
            return reach(player, team, coords, BotOrder.UseLoc(table, coords, TAKE_FIVE_OP))
        }
        if (player.coords == spot) {
            return BotOrder.SetUpBarricade
        }
        return reach(player, team, spot, BotOrder.Walk(spot))
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
            is BotOrder.NpcOp -> {
                val current = player.interaction as? InteractionNpcOp
                if (current?.target === order.target && current.op.slot == order.op) {
                    return
                }
                opNpc(player, order.target, order.op)
            }
            is BotOrder.Take -> {
                if (repeated && cycle - bot.orderedAt < REISSUE_DELAY && player.interaction != null) {
                    return
                }
                if (!takeObj(player, order)) {
                    return
                }
            }
            is BotOrder.UseItemOnLoc -> {
                if (repeated && cycle - bot.orderedAt < REISSUE_DELAY && player.interaction != null) {
                    return
                }
                if (!useItemOnLoc(player, order)) {
                    return
                }
            }
            BotOrder.SetUpBarricade -> {
                val slot = player.inv.indices.firstOrNull { player.inv[it]?.id == BARRICADE_ID } ?: return
                if (!protectedAccess.launch(player) { heldInteractions.interact(this, player.inv, slot, HeldOp.Op1) }) {
                    return
                }
            }
            is BotOrder.Fire -> {
                if (cycle - bot.firedAt < FIRE_DELAY) {
                    return
                }
                fire(player, order.target)
                bot.firedAt = cycle
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

    private fun opNpc(player: Player, target: Npc, slot: Int) {
        val op = InteractionOp.entries[slot - 1]
        player.clearPendingAction(eventBus)
        player.faceNpc(target)
        player.interaction =
            InteractionNpcOp(
                target = target,
                op = op,
                hasOpTrigger = npcInteractions.hasOpTrigger(player, target, op),
                hasApTrigger = npcInteractions.hasApTrigger(player, target, op),
            )
        player.routeRequest = RouteRequestPathingEntity(target.avatar, clientRequest = true)
    }

    private fun takeObj(player: Player, order: BotOrder.Take): Boolean {
        val id = order.obj.asRSCM(RSCMType.OBJ)
        val obj = objRegistry.findAll(order.coords).firstOrNull { it.type == id } ?: return false
        val op = InteractionOp.Op3
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.interaction =
            InteractionObj(
                target = obj,
                op = op,
                hasOpTrigger = objInteractions.hasOpTrigger(obj, op),
                hasApTrigger = objInteractions.hasApTrigger(obj, op),
            )
        player.routeRequest = RouteRequestCoord(order.coords, clientRequest = true)
        return true
    }

    /** Uses an inventory item on a loc: the same interaction as selecting the item and clicking. */
    private fun useItemOnLoc(player: Player, order: BotOrder.UseItemOnLoc): Boolean {
        val itemId = order.item.asRSCM(RSCMType.OBJ)
        val slot = player.inv.indices.firstOrNull { player.inv[it]?.id == itemId } ?: return false
        val objType = ServerCacheManager.getItem(itemId) ?: return false
        val locId = order.loc.asRSCM(RSCMType.LOC)
        val loc = locRegistry.findType(order.coords, locId) ?: return false
        val type = ServerCacheManager.getObject(locId) ?: return false
        val bound = BoundLocInfo(loc, type)
        val component = ServerCacheManager.fromComponent(INVENTORY_ITEMS.asRSCM(RSCMType.COMPONENT))
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceLoc(loc, type.width, type.length)
        player.interaction =
            InteractionLocT(
                target = bound,
                comsub = slot,
                objType = objType,
                component = component,
                hasOpTrigger = locTInteractions.hasOpTrigger(player, bound, type, objType, component, slot),
                hasApTrigger = locTInteractions.hasApTrigger(player, bound, type, objType, component, slot),
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

    /** Aims the catapult at [target] and presses Fire, the same button a player clicks. */
    private fun fire(player: Player, target: CoordGrid) {
        val arena = CastleWars.ARENA
        val aimX = ((target.x - arena.x0) * AIM_MAX + (arena.x1 - arena.x0) / 2) / (arena.x1 - arena.x0)
        val aimZ = ((target.z - arena.z0) * AIM_MAX + (arena.z1 - arena.z0) / 2) / (arena.z1 - arena.z0)
        VarPlayerIntMapSetter.set(player, AIM_X, aimX.coerceIn(0, AIM_MAX))
        VarPlayerIntMapSetter.set(player, AIM_Z, aimZ.coerceIn(0, AIM_MAX))
        val button = ServerCacheManager.fromComponent(FIRE_BUTTON.asRSCM(RSCMType.COMPONENT))
        val event = IfModalButton(button, -1, null, IfButtonOp.Op1)
        protectedAccess.launch(player) { eventBus.publish(this, event) }
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

        private const val BANDAGES = "obj.castlewars_bandages"
        private val BANDAGES_ID: Int by lazy { BANDAGES.asRSCM(RSCMType.OBJ) }
        private const val RESUME_RUN_ENERGY = 3000
        private const val MIN_RUN_ENERGY = 100
        private const val CARRIER_BANDAGE_ENERGY = 2500
        private const val TINDERBOX = "obj.tinderbox"
        private val TINDERBOX_ID: Int by lazy { TINDERBOX.asRSCM(RSCMType.OBJ) }
        private const val TOOLKIT = "obj.castlewars_toolkit"
        private const val BUCKET = "obj.bucket_empty"
        private const val WATER = "obj.bucket_water"
        private const val TAP = "loc.castlewars_tap"
        private const val INVENTORY_ITEMS = "component.inventory:items"
        private const val ATTACK_OP = 2
        private const val BURN_OP = 3
        private const val TAKE_ONE_OP = 1
        private const val BURN_RADIUS = 8
        private const val ROCK = "obj.castlewars_catapult_rock"
        private const val BARRICADE = "obj.castlewars_barricade"
        private val BARRICADE_ID: Int by lazy { BARRICADE.asRSCM(RSCMType.OBJ) }
        private const val FIRE_BUTTON = "component.castlewars_catapult:fire_catapult_button"
        private const val AIM_X = "varbit.castlewars_catapultx"
        private const val AIM_Z = "varbit.castlewars_catapultz"
        private const val AIM_MAX = 30
        private const val CATAPULT_REACH = 2
        private const val CATAPULT_MIN_RANGE = 6
        private const val BLAST_RADIUS = 2
        private const val BLAST_SAFETY = 3
        private const val FIRE_DELAY = 6
        private const val STUCK_TICKS = 6
        private const val BLOCKED_RADIUS = 2
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
