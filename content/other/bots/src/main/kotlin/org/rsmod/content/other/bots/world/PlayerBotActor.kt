package org.rsmod.content.other.bots.world

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invMoveAll
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.other.bots.core.BotActor
import org.rsmod.content.other.bots.core.BotDestination
import org.rsmod.content.other.bots.core.BotDialogue
import org.rsmod.content.other.bots.core.DepositOutcome
import org.rsmod.content.other.bots.core.DialogueReply
import org.rsmod.content.other.bots.core.GatherStatus
import org.rsmod.content.other.bots.core.SupplyLoads
import org.rsmod.content.other.bots.core.SupplyOutcome
import org.rsmod.content.other.bots.core.TravelStatus
import org.rsmod.content.skills.magic.spell.teleports.HomeTeleportCooldown
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.type.cert
import org.rsmod.map.CoordGrid

/**
 * A bot's character in the world. Travel follows the site's waypoint routes with the normal
 * pathfinder; gathering clicks locs exactly like a client would; every bank/inventory change is a
 * single item transaction that either fully applies or changes nothing.
 */
class PlayerBotActor(
    val player: Player,
    private val site: BotSite,
    private val behaviour: BotRoleBehaviour,
    private val world: BotWorldServices,
    private val tap: BotDialogueTap,
) : BotActor {
    private val coinsId = COINS.asRSCM(RSCMType.OBJ)
    private val kitWornIds: List<Int> =
        site.kit?.worn.orEmpty().mapNotNull { runCatching { it.item.asRSCM(RSCMType.OBJ) }.getOrNull() }
    private val bank: Inventory = player.invMap.getOrPut(BANK)

    private var travelDestination: BotDestination? = null
    private var arrivedVia: List<CoordGrid>? = null
    private val plan = ArrayDeque<CoordGrid>()
    private var lastCoords = CoordGrid.NULL
    private var stillTicks = 0
    private var reroutes = 0
    private var gatherBaseline: Map<Int, Int>? = null
    private var climbing: PendingClimb? = null
    private var dialogue: BotDialogue? = null
    private var dialogueFailure: String? = null

    /** A door that walks the bot through and shuts again leaves it standing still on the far side. */
    private var openedDoor = false
    private val climbs: Map<CoordGrid, SiteTile> =
        (site.resourceRoute + site.exchangeRoute + site.supplyRoute + site.returnRoute + site.hunts.flatMap { it.resourceRoute })
            .filter { it.travels }
            .associateBy { it.toCoords() }
    private val wayfinder = BotWayfinder(player, site, world) { allChains().flatten() + site.bank.center() }
    private val returnLanding: CoordGrid? =
        if (site.exchangeByWayfinder) world.wayfinder.registry[site.wayfinder]?.coords else null
    private var wayfinderGoal: String? = null

    /** The destination an exchange trip is riding to; kept until it lands, wherever a Home Teleport puts it. */
    private var tripGoal: String? = null
    private var homeTeleports = 0
    private var homeWait: String? = null
    private var homeWaitTicks = 0

    override val inventoryUsed: Int
        get() = player.inv.occupiedSpace()

    override val inventoryCapacity: Int
        get() = player.inv.size

    override val isBusy: Boolean
        get() {
            if (player.ui.modals.isNotEmpty() && !player.isDelayed) {
                if (!answerDialogue() && !behaviour.answerModal(player)) {
                    player.clearPendingAction(world.eventBus)
                }
                return true
            }
            return player.isAccessProtected || player.isDelayed
        }

    /** Answers the dialogue of a route step's conversation; false when there is none to answer. */
    private fun answerDialogue(): Boolean {
        val talk = dialogue ?: return false
        val screen = world.dialogueScreen(player, tap) ?: return false
        return when (val reply = talk.reply(screen)) {
            is DialogueReply.Continue -> world.resumePauseButton(player, reply.button, CONTINUE_SUB)
            is DialogueReply.Pick -> world.resumePauseButton(player, CHAT_MENU_OPTIONS, reply.option)
            is DialogueReply.GiveUp -> {
                dialogueFailure = reply.reason
                dialogue = null
                false
            }
        }
    }

    override val progressMark: Int
        get() = player.inv.objs.fold(1) { acc, obj -> 31 * acc + (obj?.let { it.id * 31 + it.count } ?: 0) }

    override val isActing: Boolean
        get() = player.interaction != null || player.weakQueueList.isNotEmpty

    override val focus: String?
        get() = behaviour.focus

    override fun requirementProblem(): String? {
        rewearKit()
        val lost = missingKit().filter { bankCount(it) <= 0 }
        if (lost.isNotEmpty()) {
            val names = lost.joinToString { ServerCacheManager.getItem(it)?.name ?: "item $it" }
            return "lost its kit ($names) and has no spare in the bank"
        }
        return behaviour.requirementProblem(player)
    }

    /** Worn kit gear neither worn nor carried, as after a death in the Wilderness. */
    private fun missingKit(): List<Int> =
        kitWornIds.filter { id -> player.worn.objs.none { it?.id == id } && player.inv.objs.none { it?.id == id } }

    override fun kitToFetch(): Boolean = missingKit().isNotEmpty()

    override fun fetchKit() {
        for (id in missingKit()) {
            val slot = bank.indices.firstOrNull { bank[it]?.id == id } ?: continue
            player.invTransfer(from = bank, fromSlot = slot, count = 1, into = player.inv)
        }
    }

    /** A death leaves the kit's kept gear (a tiara, a weapon) in the inventory; put it back on. */
    private fun rewearKit() {
        if (player.isAccessProtected || player.isDelayed) {
            return
        }
        for (id in kitWornIds) {
            if (player.worn.objs.any { it?.id == id }) {
                continue
            }
            val slot = player.inv.indices.firstOrNull { player.inv[it]?.id == id } ?: continue
            world.wear(player, slot)
        }
    }

    override fun needsSupplies(): Boolean = behaviour.needsSupplies(player)

    override fun needsSourceVisit(): Boolean = behaviour.needsSourceVisit(player)

    override fun minesOwnSupplies(): Boolean = behaviour.minesOwnSupplies()

    override fun canWorkWithoutSupplies(): Boolean = behaviour.canWorkWithoutSupplies(player)

    override fun restsWithoutSupplies(): Boolean = behaviour.restsWithoutSupplies()

    override fun needsRest(): Boolean = !behaviour.restsWithoutSupplies() && hurtBelow(RESTED_PERCENT)

    private fun hurtBelow(percent: Int): Boolean = player.hitpoints * 100 < player.baseHitpointsLvl * percent

    override fun travel(destination: BotDestination): TravelStatus {
        if (HomeTeleportCooldown.isCasting(player)) {
            return TravelStatus.Moving
        }
        pace()
        if (behaviour.escape(player, destination)) {
            return TravelStatus.Moving
        }
        if (isAt(destination)) {
            arrive(destination)
            return TravelStatus.Arrived
        }
        exchangeTrip(destination)?.let { return it }
        if (isOffSite()) {
            resetTravel()
            return recoverOffSite()
        }
        if (wayfinder.engaged) {
            endWayfinderTrip()
        }
        if (travelDestination != destination || plan.isEmpty()) {
            travelDestination = destination
            planRoute(destination)
            reroutes = 0
            stillTicks = 0
            if (plan.isEmpty()) {
                return TravelStatus.Blocked("site has no route to ${destination.name.lowercase()}")
            }
            head(plan.first())
        }
        climbing?.let { return climb(it) }
        while (plan.isNotEmpty() && reached(plan.first())) {
            val reached = plan.removeFirst()
            reroutes = 0
            val next = plan.firstOrNull() ?: break
            val climb = climbs[reached]
            if (climb != null && (!walkable(reached, next) || crossesTo(climb, next))) {
                climbing = PendingClimb(reached, climb)
                return climb(climbing!!)
            }
            head(next)
        }
        if (plan.isEmpty()) {
            if (!isAt(destination)) {
                return TravelStatus.Moving
            }
            arrive(destination)
            return TravelStatus.Arrived
        }
        if (openedDoor && player.interaction == null) {
            openedDoor = false
            world.walkTo(player, plan.first())
            return TravelStatus.Moving
        }
        if (player.coords == lastCoords) {
            if (++stillTicks >= STUCK_TICKS) {
                stillTicks = 0
                if (++reroutes > MAX_REROUTES) {
                    val at = player.coords
                    val next = plan.first()
                    resetTravel()
                    return TravelStatus.Blocked(
                        "stuck at ${at.x},${at.z} heading to ${destination.name.lowercase()} (next waypoint ${next.x},${next.z})"
                    )
                }
                if (world.openDoorToward(player, plan.first(), DOOR_PATH_SLACK, DOOR_REACH)) {
                    openedDoor = true
                    return TravelStatus.Moving
                }
                world.walkTo(player, plan.first())
            }
        } else {
            stillTicks = 0
            lastCoords = player.coords
        }
        return TravelStatus.Moving
    }

    /**
     * Route tiles with a `climb` are ladders, trapdoors and obstacles: a bot standing on one whose
     * next waypoint lies on another level or map square, or is the far side of the same obstacle,
     * clicks the loc instead of walking, opening a closed trapdoor first, until the climb has
     * carried it away.
     */
    private fun climb(pending: PendingClimb): TravelStatus {
        if (player.coords.level != pending.from.level || player.coords.chebyshevDistance(pending.from) > CLIMB_DONE ||
            crossed(pending)
        ) {
            climbing = null
            dialogue = null
            if (pending.tile.talk.isNotBlank()) {
                skipToNearestWaypoint()
            }
            plan.firstOrNull()?.let { world.walkTo(player, it) }
            return TravelStatus.Moving
        }
        if (player.interaction != null) {
            return TravelStatus.Moving
        }
        dialogueFailure?.let { reason ->
            resetTravel()
            return TravelStatus.Blocked("talking to ${pending.tile.talk} at ${pending.from.x},${pending.from.z}: $reason")
        }
        if (pending.clicks > MAX_CLIMB_CLICKS || pending.waits > MAX_CLIMB_WAIT_TICKS) {
            val what = pending.tile.climb.ifBlank { pending.tile.talk }
            resetTravel()
            return TravelStatus.Blocked("could not ${pending.tile.op.lowercase()} the ${what.lowercase()} at ${pending.from.x},${pending.from.z}")
        }
        if (pending.tile.talk.isNotBlank()) {
            return talk(pending)
        }
        if (world.climb(player, pending.from, pending.tile.climb, pending.tile.op)) {
            pending.clicks++
        } else {
            // Someone else may have left it in another state (a door standing open); it resets.
            pending.waits++
            world.walkTo(player, pending.from)
        }
        return TravelStatus.Moving
    }

    /**
     * Talks to a ferryman-like npc, answering its dialogue with the tile's options, until the
     * conversation has carried the bot away.
     */
    private fun talk(pending: PendingClimb): TravelStatus {
        val op = pending.tile.op.ifBlank { TALK_OP }
        if (world.talkTo(player, pending.from, TALK_RADIUS, pending.tile.talk, op)) {
            pending.clicks++
            dialogue = BotDialogue(pending.tile.options)
        } else {
            pending.waits++
            world.walkTo(player, pending.from)
        }
        return TravelStatus.Moving
    }

    /** A boat lands the bot on a later stretch of the route; it carries on from the waypoint nearest to it. */
    private fun skipToNearestWaypoint() {
        val nearest = plan.indices.minByOrNull { routeDistance(plan[it], player.coords) } ?: return
        repeat(nearest) { plan.removeFirst() }
    }

    /**
     * Walks to [target], first opening a door on the way when the pathfinder cannot get there with
     * doors as they are; otherwise it would lead the bot to the nearest tile on the wrong side.
     */
    private fun head(target: CoordGrid) {
        if (!world.canReach(player, target) && world.openDoorToward(player, target, DOOR_PATH_SLACK, DOOR_REACH)) {
            openedDoor = true
            return
        }
        world.walkTo(player, target)
    }

    /**
     * A waypoint counts as reached a couple of tiles away, but never through a fence or a closed
     * door: then the bot walks on until it is stuck there and opens the door in its way.
     */
    private fun reached(waypoint: CoordGrid): Boolean {
        val distance = routeDistance(player.coords, waypoint)
        if (distance > reachDistance(waypoint)) {
            return false
        }
        return distance == 0 || waypoint in climbs || world.canReach(player, waypoint)
    }

    /** A ladder may sit behind a door or wall, so a climb tile only counts once the bot is beside it. */
    private fun reachDistance(waypoint: CoordGrid): Int =
        if (waypoint in climbs) CLIMB_REACHED else WAYPOINT_REACHED

    /** A ditch or pipe: the next waypoint is the obstacle's other side, a climb tile of the same loc. */
    private fun crossesTo(climb: SiteTile, next: CoordGrid): Boolean =
        climb.climb.isNotBlank() && climbs[next]?.climb.equals(climb.climb, ignoreCase = true)

    private fun crossed(pending: PendingClimb): Boolean {
        val next = plan.firstOrNull() ?: return false
        return crossesTo(pending.tile, next) &&
            player.coords.chebyshevDistance(next) < player.coords.chebyshevDistance(pending.from)
    }

    /** Waypoints on another level (a watchtower walkway above the path) are never "near". */
    private fun routeDistance(a: CoordGrid, b: CoordGrid): Int =
        if (a.level == b.level) a.chebyshevDistance(b) else OTHER_LEVEL_DISTANCE

    private fun walkable(from: CoordGrid, to: CoordGrid): Boolean =
        from.level == to.level && from.chebyshevDistance(to) <= OFF_SITE_DISTANCE

    /**
     * A site too far to walk to the exchange rides a wayfinder mage there, and Vaelor back to the
     * site's own destination after, walking [BotSite.returnRoute] home from where it lands.
     */
    private fun exchangeTrip(destination: BotDestination): TravelStatus? {
        if (!site.exchangeByWayfinder) {
            return null
        }
        val nearExchange = nearExchange()
        val wanted = if (destination == BotDestination.Exchange) EXCHANGE_DESTINATION else site.wayfinder
        if (tripGoal != wanted) {
            tripGoal = null
        }
        val goal =
            tripGoal
                ?: when {
                    destination == BotDestination.Exchange && !nearExchange -> wanted
                    destination != BotDestination.Exchange && nearExchange -> wanted
                    else -> return null
                }
        tripGoal = goal
        resetTravel()
        val status = ride(goal)
        if (status == null || wayfinderGoal == null) {
            tripGoal = null
        }
        return status ?: TravelStatus.Blocked("no wayfinder or Home Teleport gets it to $goal")
    }

    private fun nearExchange(): Boolean {
        val exchange = site.exchange.center()
        return player.coords.level == exchange.level && player.coords.chebyshevDistance(exchange) <= EXCHANGE_RANGE
    }

    /** A far site's exchange trip needs Home Teleport unless a mage is in reach; it waits for the recharge at work. */
    override fun exchangeTripHold(): String? {
        if (!site.exchangeByWayfinder || tripGoal != null || nearExchange() || wayfinder.mageInReach()) {
            return null
        }
        val minutes = HomeTeleportCooldown.minutesLeft(player)
        return if (minutes > 0) "Home Teleport recharges in $minutes min" else null
    }

    /**
     * Rides a wayfinder mage to [goal] (the site's own destination when null). With no mage in
     * reach the bot casts Home Teleport to Lumbridge, where Elowen stands, waiting out its cooldown
     * first, and tries again; null once that has not helped either.
     */
    private fun ride(goal: String?): TravelStatus? {
        if (goal != wayfinderGoal) {
            endWayfinderTrip()
            wayfinderGoal = goal
        }
        homeWait?.let { reason ->
            if (--homeWaitTicks > 0) {
                return TravelStatus.Waiting(reason)
            }
            homeWait = null
        }
        when (val step = wayfinder.tick(goal)) {
            BotWayfinder.Step.Moving -> return TravelStatus.Moving
            BotWayfinder.Step.Arrived -> {
                endWayfinderTrip()
                return TravelStatus.Moving
            }
            is BotWayfinder.Step.Failed -> {
                if (homeTeleports >= MAX_HOME_TELEPORTS) {
                    logger.info { "Bot ${player.displayName}: ${step.reason}, even after Home Teleport." }
                    endWayfinderTrip()
                    return null
                }
                val minutes = HomeTeleportCooldown.minutesLeft(player)
                if (minutes > 0) {
                    wayfinder.reset()
                    homeWait = "Waiting $minutes min for Home Teleport (${step.reason})"
                    homeWaitTicks = HOME_WAIT_RECHECK_TICKS
                    return TravelStatus.Waiting(homeWait!!)
                }
                homeTeleports++
                wayfinder.reset()
                if (!world.castHomeTeleport(player)) {
                    logger.info { "Bot ${player.displayName}: ${step.reason}, and Home Teleport could not be cast." }
                    endWayfinderTrip()
                    return null
                }
                logger.info { "Bot ${player.displayName}: ${step.reason}; casting Home Teleport." }
                return TravelStatus.Moving
            }
        }
    }

    private fun endWayfinderTrip() {
        wayfinder.reset()
        wayfinderGoal = null
        homeTeleports = 0
        homeWait = null
        homeWaitTicks = 0
    }

    private fun arrive(destination: BotDestination) {
        resetTravel()
        arrivedVia = chainTo(destination)
    }

    private fun isAt(destination: BotDestination): Boolean =
        when (destination) {
            BotDestination.Bank -> site.bank.contains(player.coords)
            BotDestination.Exchange -> site.exchange.contains(player.coords)
            BotDestination.Resource,
            BotDestination.SupplySource -> behaviour.isAt(player, destination)
        }

    /**
     * Routes are bank-relative chains. Heading to the bank walks backwards along whichever chain
     * the bot is closest to; heading elsewhere walks forwards along that destination's chain,
     * returning to the bank first when the bot is nowhere near it.
     */
    private fun planRoute(destination: BotDestination) {
        plan.clear()
        val bank = site.bank.center()
        if (destination == BotDestination.Bank) {
            val (chain, index) = wayBack() ?: (emptyList<CoordGrid>() to -1)
            for (i in index downTo 0) {
                plan += chain[i]
            }
            plan += bank
            return
        }
        val chain = chainTo(destination) ?: return
        val nearest = chain.indices.minByOrNull { routeDistance(chain[it], player.coords) } ?: return
        if (routeDistance(chain[nearest], player.coords) > REJOIN_DISTANCE &&
            !site.bank.contains(player.coords)
        ) {
            val (back, index) = wayBack() ?: (emptyList<CoordGrid>() to -1)
            for (i in index downTo 0) {
                plan += back[i]
            }
            plan += bank
            plan += chain
            return
        }
        for (i in nearest until chain.size) {
            plan += chain[i]
        }
    }

    private fun chainTo(destination: BotDestination): List<CoordGrid>? =
        when (destination) {
            BotDestination.Resource -> {
                val route = behaviour.resourceRoute(player) ?: site.resourceRoute
                val area = behaviour.resourceArea(player) ?: site.resource
                route.map { it.toCoords() } + area.center()
            }
            BotDestination.Exchange -> site.exchangeRoute.map { it.toCoords() } + site.exchange.center()
            BotDestination.SupplySource ->
                site.supplyArea?.let { area -> site.supplyRoute.map { it.toCoords() } + area.center() }
            BotDestination.Bank -> null
        }

    private fun allChains(): List<List<CoordGrid>> =
        listOfNotNull(
            chainTo(BotDestination.Resource),
            chainTo(BotDestination.Exchange),
            chainTo(BotDestination.SupplySource),
            returnChain(),
        ) + huntChains()

    private fun returnChain(): List<CoordGrid>? =
        returnLanding?.let { landing -> site.returnRoute.map { it.toCoords() } + landing }

    private fun huntChains(): List<List<CoordGrid>> =
        site.hunts.map { hunt -> hunt.resourceRoute.map { it.toCoords() } + hunt.resource.center() }

    /**
     * Where a trip home starts. A bot leaving the place it walked to retraces that route from the
     * waypoint nearest to it, so it leaves a fenced field through the gate it came in by.
     */
    private fun wayBack(): Pair<List<CoordGrid>, Int>? {
        val leaving =
            arrivedVia?.takeIf { it.size > 1 && routeDistance(it.last(), player.coords) <= LEAVE_DISTANCE }
                ?: huntChains()
                    .filter { it.size > 1 && routeDistance(it.last(), player.coords) <= LEAVE_DISTANCE }
                    .minByOrNull { routeDistance(it.last(), player.coords) }
        return leaving?.let { nearestChainPoint(listOf(it)) } ?: nearestChainPoint(allChains())
    }

    /**
     * The waypoint the bot can walk to that is nearest to it; on a tie the one nearer the bank, so
     * a bot outside a pen stays out. Waypoints behind a fence or closed gate only count when none
     * is reachable.
     */
    private fun nearestChainPoint(chains: List<List<CoordGrid>>): Pair<List<CoordGrid>, Int>? {
        val points =
            chains
                .flatMap { chain -> chain.indices.map { chain to it } }
                .sortedWith(compareBy({ (chain, i) -> routeDistance(chain[i], player.coords) }, { (_, i) -> i }))
        return points.take(REACH_CHECKS).firstOrNull { (chain, i) -> world.canReach(player, chain[i]) } ?: points.firstOrNull()
    }

    /**
     * A stray bot (a death respawn, say) rides a wayfinder mage back towards its site, casting Home
     * Teleport first when no mage is in reach. A bot neither gets home is blocked, never placed.
     */
    private fun recoverOffSite(): TravelStatus {
        ride(null)?.let { return it }
        val at = player.coords
        logger.warn { "Bot ${player.displayName} is stranded at $at; no mage or Home Teleport gets it home." }
        return TravelStatus.Blocked("stranded at ${at.x},${at.z}; no wayfinder mage or Home Teleport gets it home")
    }

    /** Death respawns and stray teleports leave a bot where no route can reach its site. */
    private fun isOffSite(): Boolean {
        if (behaviour.isAt(player, BotDestination.Resource) || behaviour.isAt(player, BotDestination.SupplySource)) {
            return false
        }
        val anchors = allChains().flatten() + site.bank.center()
        return anchors.none { it.level == player.coords.level && it.chebyshevDistance(player.coords) <= OFF_SITE_DISTANCE }
    }

    /** Runs while there is energy to spare and walks to recover it, like a player would. */
    private fun pace() {
        val running = player.varMoveSpeed == MoveSpeed.Run
        if (!running && player.runEnergy >= RUN_ENERGY_ON) {
            player.varMoveSpeed = MoveSpeed.Run
        } else if (running && player.runEnergy <= RUN_ENERGY_OFF) {
            player.varMoveSpeed = MoveSpeed.Walk
        }
    }

    private fun resetTravel() {
        travelDestination = null
        climbing = null
        dialogue = null
        dialogueFailure = null
        openedDoor = false
        plan.clear()
        stillTicks = 0
        reroutes = 0
    }

    override fun gather(target: BotDestination): GatherStatus {
        if (isOffSite()) {
            resetTravel()
            return GatherStatus.NeedsSupplies
        }
        if (!behaviour.restsWithoutSupplies() && hurtBelow(FLEE_PERCENT)) {
            return GatherStatus.Hurt
        }
        val current = countByItem(player.inv)
        val arrived = gatherBaseline?.let { produced(it, current, target) } ?: 0
        if (arrived > 0) {
            gatherBaseline = current
            return GatherStatus.Working(arrived)
        }
        val status = behaviour.gather(player, target)
        val after = countByItem(player.inv)
        gatherBaseline = after
        return if (status is GatherStatus.Working) status.copy(gained = produced(current, after, target)) else status
    }

    private fun produced(before: Map<Int, Int>, after: Map<Int, Int>, target: BotDestination): Int =
        after.entries.sumOf { (id, count) ->
            val supply = behaviour.keep(id)
            val counted = if (target == BotDestination.SupplySource) supply else !supply
            if (counted) (count - (before[id] ?: 0)).coerceAtLeast(0) else 0
        }

    override fun stopActivity() {
        resetTravel()
        if (!player.isDelayed) {
            player.clearPendingAction(world.eventBus)
        }
        player.routeRequest = null
    }

    override fun depositGathered(): DepositOutcome {
        val keep = player.inv.mapSlots { _, obj -> obj != null && behaviour.keep(obj.id) }
        val before = countByItem(player.inv)
        val result =
            player.invMoveAll(
                from = player.inv,
                into = bank,
                uncert = true,
                untransform = true,
                intoCapacity = bankCapacityOf(),
                keepSlots = keep,
            )
        if (result.failure) {
            return DepositOutcome.BankFull
        }
        val after = countByItem(player.inv)
        val moved =
            before
                .mapValues { (id, count) -> count - (after[id] ?: 0) }
                .filterValues { it > 0 }
                .mapKeys { (id, _) -> baseId(id) }
        gatherBaseline = null
        return DepositOutcome.Deposited(moved)
    }

    override fun withdrawSupplies(): SupplyOutcome {
        val supplies = behaviour.supplyIds()
        if (supplies.isEmpty() || !behaviour.needsSupplies(player)) {
            return SupplyOutcome.NotNeeded
        }
        val free = player.inv.freeSpace()
        if (free == 0) {
            return SupplyOutcome.NoSpace
        }
        val set = behaviour.supplySet()
        if (set.isNotEmpty()) {
            val load = SupplyLoads.plan(set, free, ::bankCount)
            if (load.isEmpty() || !withdrawLoad(player, bank, load)) {
                return SupplyOutcome.Unavailable
            }
            gatherBaseline = null
            return SupplyOutcome.Withdrawn(load.values.sum())
        }
        for (id in supplies) {
            val slot = bank.indices.firstOrNull { bank[it]?.id == id } ?: continue
            val wanted = behaviour.supplyAmount(free)
            val stackable = ServerCacheManager.getItem(id)?.stackable == true
            val count = minOf(if (stackable) wanted else minOf(wanted, free), bank[slot]!!.count)
            val result = player.invTransfer(from = bank, fromSlot = slot, count = count, into = player.inv)
            if (result.success) {
                gatherBaseline = null
                return SupplyOutcome.Withdrawn(result.completed())
            }
        }
        return SupplyOutcome.Unavailable
    }

    override fun suppliesToBuy(): List<Int> = behaviour.suppliesToBuy(::bankCount)

    override fun bankCount(itemId: Int): Long = bank.objs.sumOf { if (it?.id == itemId) it.count.toLong() else 0L }

    override fun withdrawNoted(itemId: Int, count: Int): Int {
        val slot = bank.indices.firstOrNull { bank[it]?.id == itemId } ?: return 0
        val amount = minOf(count, bank[slot]!!.count)
        val result =
            player.invTransfer(from = bank, fromSlot = slot, count = amount, into = player.inv, cert = true)
        gatherBaseline = null
        return if (result.success) result.completed() else 0
    }

    override fun held(itemId: Int): Int {
        val noted = notedId(itemId)
        return player.inv.objs.sumOf { if (it != null && (it.id == itemId || it.id == noted)) it.count else 0 }
    }

    override fun heldNoted(itemId: Int): Int {
        val noted = notedId(itemId) ?: return 0
        return player.inv.objs.sumOf { if (it?.id == noted) it.count else 0 }
    }

    override fun takeHeld(itemId: Int, count: Int): Boolean {
        val noted = notedId(itemId)
        val notedHeld = if (noted != null) player.inv.objs.sumOf { if (it != null && it.id == noted) it.count else 0 } else 0
        val unnotedHeld = player.inv.objs.sumOf { if (it?.id == itemId) it.count else 0 }
        if (notedHeld.toLong() + unnotedHeld < count) {
            return false
        }
        val fromNoted = minOf(notedHeld, count)
        val result =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                if (fromNoted > 0 && noted != null) {
                    delete(inv = inv, obj = noted, count = fromNoted)
                }
                if (count - fromNoted > 0) {
                    delete(inv = inv, obj = itemId, count = count - fromNoted)
                }
            }
        gatherBaseline = null
        return result.success
    }

    override fun returnHeld(itemId: Int, count: Int): Boolean {
        gatherBaseline = null
        return player.invAdd(player.inv, notedId(itemId) ?: itemId, count).success
    }

    override fun creditBank(itemId: Int, count: Int): Boolean = player.invAdd(bank, itemId, count).success

    override fun creditBankCoins(coins: Long): Long {
        val room = Int.MAX_VALUE.toLong() - bankCount(coinsId)
        val amount = minOf(room, coins).toInt()
        if (amount <= 0) {
            return 0
        }
        return if (player.invAdd(bank, coinsId, amount).success) amount.toLong() else 0
    }

    override fun debitBankCoins(coins: Long): Boolean {
        if (coins !in 1..bankCount(coinsId)) {
            return coins == 0L
        }
        return player.invDel(bank, coinsId, coins.toInt()).success
    }

    override fun coins(): Long =
        bankCount(coinsId) + player.inv.objs.sumOf { if (it?.id == coinsId) it.count.toLong() else 0L }

    private fun bankCapacityOf(): Int = player.bankCapacity.takeIf { it > 0 } ?: DEFAULT_BANK_CAPACITY

    private fun notedId(itemId: Int): Int? {
        val type = ServerCacheManager.getItem(itemId) ?: return null
        return if (type.canCert) cert(type.internalName).id else null
    }

    private fun baseId(id: Int): Int {
        val type = ServerCacheManager.getItem(id) ?: return id
        return if (type.isCert) type.certlink else id
    }

    private fun countByItem(inv: Inventory): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        for (obj in inv.objs) {
            if (obj != null) {
                counts.merge(obj.id, obj.count, Int::plus)
            }
        }
        return counts
    }

    private companion object {
        private val logger = InlineLogger("bots")

        private const val COINS = "obj.coins"
        private const val BANK = "inv.bank"
        private const val DEFAULT_BANK_CAPACITY = 800
        private const val WAYPOINT_REACHED = 2
        private const val REJOIN_DISTANCE = 20
        private const val LEAVE_DISTANCE = 30
        private const val STUCK_TICKS = 8
        private const val MAX_REROUTES = 5
        private const val OFF_SITE_DISTANCE = 64
        private const val EXCHANGE_RANGE = 30
        private const val EXCHANGE_DESTINATION = "grand_exchange"
        private const val MAX_HOME_TELEPORTS = 2
        private const val HOME_WAIT_RECHECK_TICKS = 50
        private const val FLEE_PERCENT = 50
        private const val RUN_ENERGY_ON = 5000
        private const val RUN_ENERGY_OFF = 1500
        private const val RESTED_PERCENT = 90
        private const val REACH_CHECKS = 6
        private const val DOOR_PATH_SLACK = 2
        private const val DOOR_REACH = 16
        private const val CLIMB_DONE = 20
        private const val CLIMB_REACHED = 1
        private const val MAX_CLIMB_CLICKS = 10
        private const val MAX_CLIMB_WAIT_TICKS = 600
        private const val OTHER_LEVEL_DISTANCE = 10_000
        private const val TALK_OP = "Talk-to"
        private const val TALK_RADIUS = 8
        private const val CONTINUE_SUB = -1
        private const val CHAT_MENU_OPTIONS = "component.chatmenu:options"
    }
}

private class PendingClimb(val from: CoordGrid, val tile: SiteTile) {
    var clicks = 0
    var waits = 0
}
