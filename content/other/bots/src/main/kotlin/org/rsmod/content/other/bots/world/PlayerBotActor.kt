package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invMoveAll
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.other.bots.core.BotActor
import org.rsmod.content.other.bots.core.BotDestination
import org.rsmod.content.other.bots.core.DepositOutcome
import org.rsmod.content.other.bots.core.GatherStatus
import org.rsmod.content.other.bots.core.SupplyOutcome
import org.rsmod.content.other.bots.core.TravelStatus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
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
) : BotActor {
    private val coinsId = COINS.asRSCM(RSCMType.OBJ)
    private val bank: Inventory = player.invMap.getOrPut(BANK)

    private var travelDestination: BotDestination? = null
    private val plan = ArrayDeque<CoordGrid>()
    private var lastCoords = CoordGrid.NULL
    private var stillTicks = 0
    private var reroutes = 0
    private var gatherBaseline: Map<Int, Int>? = null

    override val inventoryUsed: Int
        get() = player.inv.occupiedSpace()

    override val inventoryCapacity: Int
        get() = player.inv.size

    override val isBusy: Boolean
        get() {
            if (player.ui.modals.isNotEmpty() && !player.isDelayed) {
                player.clearPendingAction(world.eventBus)
                return true
            }
            return player.isAccessProtected || player.isDelayed
        }

    override fun requirementProblem(): String? = behaviour.requirementProblem(player)

    override fun needsSupplies(): Boolean = behaviour.needsSupplies(player)

    override fun travel(destination: BotDestination): TravelStatus {
        if (behaviour.escape(player, destination)) {
            return TravelStatus.Moving
        }
        if (isAt(destination)) {
            resetTravel()
            return TravelStatus.Arrived
        }
        if (isOffSite()) {
            resetTravel()
            world.telejump(player, site.bank.center())
            return TravelStatus.Moving
        }
        if (travelDestination != destination || plan.isEmpty()) {
            travelDestination = destination
            planRoute(destination)
            reroutes = 0
            stillTicks = 0
            if (plan.isEmpty()) {
                return TravelStatus.Blocked("site has no route to ${destination.name.lowercase()}")
            }
            world.walkTo(player, plan.first())
        }
        while (plan.isNotEmpty() && player.coords.chebyshevDistance(plan.first()) <= WAYPOINT_REACHED) {
            plan.removeFirst()
            plan.firstOrNull()?.let { world.walkTo(player, it) }
        }
        if (plan.isEmpty()) {
            return if (isAt(destination)) TravelStatus.Arrived else TravelStatus.Moving
        }
        if (player.coords == lastCoords) {
            if (++stillTicks >= STUCK_TICKS) {
                stillTicks = 0
                if (world.openNearbyDoor(player)) {
                    reroutes++
                    return TravelStatus.Moving
                }
                if (++reroutes > MAX_REROUTES) {
                    val at = player.coords
                    resetTravel()
                    return TravelStatus.Blocked("stuck at ${at.x},${at.z} heading to ${destination.name.lowercase()}")
                }
                world.walkTo(player, plan.first())
            }
        } else {
            stillTicks = 0
            lastCoords = player.coords
        }
        return TravelStatus.Moving
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
            val (chain, index) = nearestChainPoint(allChains()) ?: (emptyList<CoordGrid>() to -1)
            for (i in index downTo 0) {
                plan += chain[i]
            }
            plan += bank
            return
        }
        val chain = chainTo(destination) ?: return
        val nearest = chain.indices.minByOrNull { chain[it].chebyshevDistance(player.coords) } ?: return
        if (chain[nearest].chebyshevDistance(player.coords) > REJOIN_DISTANCE &&
            !site.bank.contains(player.coords)
        ) {
            val (back, index) = nearestChainPoint(allChains()) ?: (emptyList<CoordGrid>() to -1)
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
            BotDestination.Resource -> site.resourceRoute.map { it.toCoords() } + site.resource.center()
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
        )

    private fun nearestChainPoint(chains: List<List<CoordGrid>>): Pair<List<CoordGrid>, Int>? =
        chains
            .flatMap { chain -> chain.indices.map { chain to it } }
            .minByOrNull { (chain, i) -> chain[i].chebyshevDistance(player.coords) }

    /** Death respawns and stray teleports leave a bot where no route can reach its site. */
    private fun isOffSite(): Boolean {
        if (behaviour.isAt(player, BotDestination.Resource) || behaviour.isAt(player, BotDestination.SupplySource)) {
            return false
        }
        val anchors = allChains().flatten() + site.bank.center()
        return anchors.none { it.level == player.coords.level && it.chebyshevDistance(player.coords) <= OFF_SITE_DISTANCE }
    }

    private fun resetTravel() {
        travelDestination = null
        plan.clear()
        stillTicks = 0
        reroutes = 0
    }

    override fun gather(target: BotDestination): GatherStatus {
        if (isOffSite()) {
            resetTravel()
            world.telejump(player, site.bank.center())
            return GatherStatus.NeedsSupplies
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
        private const val COINS = "obj.coins"
        private const val BANK = "inv.bank"
        private const val DEFAULT_BANK_CAPACITY = 800
        private const val WAYPOINT_REACHED = 2
        private const val REJOIN_DISTANCE = 20
        private const val STUCK_TICKS = 8
        private const val MAX_REROUTES = 5
        private const val OFF_SITE_DISTANCE = 64
    }
}
