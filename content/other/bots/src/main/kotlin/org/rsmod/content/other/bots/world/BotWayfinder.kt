package org.rsmod.content.other.bots.world

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.content.travel.wayfinder.WayfinderDestination
import org.rsmod.content.travel.wayfinder.WayfinderMages
import org.rsmod.content.travel.wayfinder.WayfinderRegistry
import org.rsmod.content.travel.wayfinder.WayfinderResult
import org.rsmod.content.travel.wayfinder.WayfinderTraveller
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid

/**
 * Gets a bot to a wayfinder destination the way a player would: walk to the nearest mage it can
 * reach, ask for the destination (the exchange, or the bot-allowed one closest to its site), and
 * let the same
 * [org.rsmod.content.travel.wayfinder.WayfinderTravel] that serves players validate and cast it.
 *
 * Everything is bounded: [MAX_ATTEMPTS] requests per trip, [WALK_BUDGET] ticks to reach a mage and
 * [CAST_BUDGET] ticks for the cast. A mage the bot fails to reach and a destination the service
 * rejects are not tried again on the same trip; once the budget is gone the caller casts Home
 * Teleport to Lumbridge, where Elowen stands, and tries again.
 */
class BotWayfinder(
    private val player: Player,
    private val site: BotSite,
    private val world: BotWorldServices,
    private val anchors: () -> List<CoordGrid>,
) {
    sealed class Step {
        data object Moving : Step()

        data object Arrived : Step()

        data class Failed(val reason: String) : Step()
    }

    private var goal: String? = null
    private var mage: Npc? = null
    private var destination: WayfinderDestination? = null
    private var result: WayfinderResult? = null
    private var casting = false
    private var phaseTicks = 0
    private var attempts = 0
    private var lastCoords = CoordGrid.NULL
    private val unreachableMages = HashSet<Npc>()
    private val rejected = HashSet<String>()

    /** True while a trip is underway or has been used up; cleared by [reset] once back on site. */
    var engaged = false
        private set

    fun reset() {
        engaged = false
        clearAttempt()
        attempts = 0
        unreachableMages.clear()
        rejected.clear()
    }

    /** True when a wayfinder mage stands within reach, so no Home Teleport is needed. */
    fun mageInReach(): Boolean = chooseMage() != null

    /** Advances the trip to [goal], a destination key, or to the site's own destination when null. */
    fun tick(goal: String? = null): Step {
        engaged = true
        this.goal = goal
        if (casting) {
            return cast()
        }
        val target = mage?.takeIf { it.isSlotAssigned } ?: return begin()
        if (world.wayfinder.isNear(player, target) && player.coords.chebyshevDistance(target.coords) <= REQUEST_DISTANCE) {
            return request(target)
        }
        if (++phaseTicks > WALK_BUDGET) {
            logger.info { "Bot ${player.displayName} could not reach ${WayfinderMages.name(target)}." }
            unreachableMages += target
            clearAttempt()
            return begin()
        }
        if (player.coords == lastCoords && phaseTicks % REROUTE_TICKS == 0) {
            approach(target)
        }
        lastCoords = player.coords
        return Step.Moving
    }

    private fun begin(): Step {
        if (attempts >= MAX_ATTEMPTS) {
            return Step.Failed("wayfinder gave up after $attempts attempts")
        }
        val chosen = chooseDestination() ?: return Step.Failed("no wayfinder destination near ${site.key}")
        val found = chooseMage() ?: return Step.Failed("no reachable wayfinder within $MAGE_SEARCH_RADIUS tiles")
        attempts++
        destination = chosen
        mage = found
        phaseTicks = 0
        approach(found)
        logger.info { "Bot ${player.displayName} heading to ${WayfinderMages.name(found)} for ${chosen.key}." }
        return Step.Moving
    }

    /** The pathfinder only searches 64 tiles around the walker, so a farther mage is walked to in hops. */
    private fun approach(target: Npc) {
        if (player.coords.chebyshevDistance(target.coords) > WALK_HOP) {
            world.walkTo(player, hopToward(player.coords, target.coords))
        } else {
            player.routeRequest = RouteRequestPathingEntity(target.avatar)
        }
    }

    private fun request(target: Npc): Step {
        val key = destination?.key ?: return begin()
        player.routeRequest = null
        player.faceNpc(target)
        result = null
        casting = true
        phaseTicks = 0
        val launched =
            world.launch(player) {
                val outcome = world.wayfinder.travel(this, target, key, WayfinderTraveller.Bot)
                if (outcome == WayfinderResult.Arrived) {
                    logger.info { "Bot ${player.displayName} arrived at $key by wayfinder." }
                }
                result = outcome
            }
        if (!launched) {
            casting = false
            return Step.Moving
        }
        return cast()
    }

    private fun cast(): Step {
        when (val outcome = result) {
            null -> {
                if (++phaseTicks > CAST_BUDGET) {
                    casting = false
                    clearAttempt()
                    return begin()
                }
                return Step.Moving
            }
            WayfinderResult.Arrived -> {
                destination?.let { rejected += it.key }
                clearAttempt()
                return Step.Arrived
            }
            is WayfinderResult.Denied -> {
                logger.info { "Bot ${player.displayName} wayfinder request refused: ${outcome.reason}" }
                destination?.let { rejected += it.key }
            }
            WayfinderResult.Cancelled -> destination?.let { rejected += it.key }
        }
        clearAttempt()
        return begin()
    }

    private fun clearAttempt() {
        mage = null
        destination = null
        result = null
        casting = false
        phaseTicks = 0
    }

    private fun chooseDestination(): WayfinderDestination? =
        pickDestination(world.wayfinder.registry, goal ?: site.wayfinder, site.bank.center(), anchors(), rejected)

    private fun chooseMage(): Npc? =
        world
            .findNpcs(player.coords, MAGE_SEARCH_RADIUS) { WayfinderMages.isMage(it) && it !in unreachableMages }
            .minByOrNull { it.coords.chebyshevDistance(player.coords) }

    companion object {
        private val logger = InlineLogger("bots")

        private const val MAX_ATTEMPTS = 3
        private const val WALK_BUDGET = 200
        private const val WALK_HOP = 48
        private const val CAST_BUDGET = 20
        private const val REQUEST_DISTANCE = 2
        private const val REROUTE_TICKS = 5

        /** Far enough to walk from the Grand Exchange to Vaelor at Edgeville rather than spend a Home Teleport. */
        private const val MAGE_SEARCH_RADIUS = 96
        private const val ARRIVAL_RANGE = 40

        /** The tile [maxHop] tiles from [from] on the straight line to [to], or [to] when it is closer. */
        fun hopToward(from: CoordGrid, to: CoordGrid, maxHop: Int = WALK_HOP): CoordGrid {
            val distance = from.chebyshevDistance(to)
            if (distance <= maxHop) {
                return to
            }
            val x = from.x + (to.x - from.x) * maxHop / distance
            val z = from.z + (to.z - from.z) * maxHop / distance
            return CoordGrid(x, z, from.level)
        }

        /**
         * The site's [configured] destination, else the bot-allowed one closest to [bank] that
         * lands within walking reach of one of the site's route [anchors].
         */
        fun pickDestination(
            registry: WayfinderRegistry,
            configured: String,
            bank: CoordGrid,
            anchors: List<CoordGrid>,
            rejected: Set<String>,
        ): WayfinderDestination? {
            if (configured.isNotBlank()) {
                return registry[configured]?.takeIf { it.botAllowed && it.key !in rejected }
            }
            return registry.all
                .filter { it.botAllowed && it.key !in rejected && it.coords.level == bank.level }
                .filter { dest -> anchors.any { it.level == dest.coords.level && it.chebyshevDistance(dest.coords) <= ARRIVAL_RANGE } }
                .minByOrNull { it.coords.chebyshevDistance(bank) }
        }
    }
}
