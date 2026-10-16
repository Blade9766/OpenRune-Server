package org.rsmod.content.other.bots.meister.world

import com.github.michaelbull.logging.InlineLogger
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.config.constants
import org.rsmod.api.inv.map.InvMapInit
import org.rsmod.api.net.rsprot.BotSessions
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.isSuccess
import org.rsmod.content.other.bots.core.SavedCoords
import org.rsmod.content.other.bots.meister.MeisterEvent
import org.rsmod.content.other.bots.meister.MeisterTrader
import org.rsmod.content.other.bots.world.BotAppearance
import org.rsmod.content.other.bots.world.BotWorldServices
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.world.WorldType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Master Meister's body: a player bot, brought into the world the way the gathering bots are (no
 * network session, a [BotSessions] avatar so others see him, a negative account id that keeps him
 * out of every account save). He walks with the normal pathfinder, strolls a few tiles at a time,
 * emotes and chats now and then, and goes to an Exchange booth when he places an offer. He carries
 * nothing - his wealth is the trader's ledger - so leaving the world loses nothing. A walk that
 * makes no progress is abandoned; repeated failures or leaving his area put him back at his spawn
 * tile, and a body the engine removed is brought back.
 */
@Singleton
class MeisterPresence
@Inject
constructor(
    private val playerRegistry: PlayerRegistry,
    private val eventBus: EventBus,
    private val invMapInit: InvMapInit,
    private val sessions: BotSessions,
    private val services: BotWorldServices,
    private val collision: CollisionFlagMap,
    private val mapClock: MapClock,
) {
    private val logger = InlineLogger("bots.meister")
    private val random = Random.Default

    var player: Player? = null
        private set

    private var restoreAt: CoordGrid? = null
    private var target: CoordGrid? = null
    private var targetBooth: MeisterBooth? = null
    private var targetSince = 0
    private var lastCoords: CoordGrid? = null
    private var lastMoveCycle = 0
    private var failures = 0
    private var nextIdleCycle = 0
    private var nextSpawnAttempt = 0
    private var shuttingDown = false

    fun restore(coords: SavedCoords?, config: MeisterWorldConfig) {
        restoreAt = coords?.let { CoordGrid(it.x, it.z, it.level) }?.takeIf { config.area.contains(it) }
    }

    fun tick(trader: MeisterTrader, config: MeisterWorldConfig) {
        if (shuttingDown) return
        val player = ensurePlayer(config) ?: return
        val cycle = mapClock.cycle
        if (!config.area.contains(player.coords)) {
            logger.warn { "[Master Meister] left his area at ${player.coords}; returning him to his spawn." }
            home(player, config)
            return
        }
        if (player.coords != lastCoords) {
            lastCoords = player.coords
            lastMoveCycle = cycle
        }
        for (event in trader.drainEvents()) {
            react(player, event, config)
        }
        val walking = target
        if (walking != null) {
            followTarget(player, walking, config)
            return
        }
        if (cycle >= nextIdleCycle) {
            nextIdleCycle = cycle + config.idleIntervalTicks + random.nextInt(config.idleIntervalTicks + 1)
            idle(player, config)
        }
    }

    fun sendHome(config: MeisterWorldConfig): Boolean {
        val player = player ?: return false
        home(player, config)
        return true
    }

    /** The engine logged him out by itself (shutdown or a forced removal); [tick] brings him back unless it is shutting down. */
    fun onLoggedOut(player: Player) {
        if (player !== this.player) return
        if (player.pendingShutdown) shuttingDown = true
        restoreAt = player.coords
        this.player = null
        logger.info { "[Master Meister] left the world${if (shuttingDown) " for the shutdown" else ""}." }
    }

    private fun ensurePlayer(config: MeisterWorldConfig): Player? {
        val current = player
        if (current != null && current.isSlotAssigned) {
            return current
        }
        if (mapClock.cycle < nextSpawnAttempt) {
            return null
        }
        nextSpawnAttempt = mapClock.cycle + SPAWN_RETRY_TICKS
        val slot = playerRegistry.nextFreeSlot() ?: return null
        val coords = restoreAt ?: config.spawn.toCoords()
        val spawned = Player()
        assignIdentity(spawned)
        spawned.coords = coords
        invMapInit.init(spawned)
        BotAppearance.randomize(spawned, APPEARANCE_SEED)
        spawned.runEnergy = constants.run_max_energy
        spawned.slotId = slot
        sessions.attach(spawned)
        val result = playerRegistry.add(spawned)
        if (!result.isSuccess()) {
            sessions.detach(spawned)
            logger.warn { "[Master Meister] could not enter the world: $result; retrying in $SPAWN_RETRY_TICKS ticks." }
            return null
        }
        eventBus.publish(SessionStateEvent.Login(spawned))
        eventBus.publish(SessionStateEvent.EngineLogin(spawned))
        spawned.rebuildAppearance()
        restoreAt = null
        target = null
        failures = 0
        player = spawned
        logger.info { "[Master Meister] entered the world at $coords." }
        return spawned
    }

    private fun assignIdentity(player: Player) {
        val id = MeisterTrader.OWNER_ID
        player.accountId = id
        player.characterId = id
        player.userId = id.toLong()
        player.accountHash = id.toLong()
        player.userHash = id.toLong()
        player.uuid = id.toLong()
        player.observerUUID = id.toLong()
        player.worldType = WorldType.DEFAULT
        player.username = "bot.meister"
        player.displayName = MeisterTrader.NAME
        player.members = true
        player.modLevel = Rights.NONE
    }

    private fun react(player: Player, event: MeisterEvent, config: MeisterWorldConfig) {
        when (event) {
            is MeisterEvent.OfferPlaced -> {
                val booth = config.booths.minByOrNull { it.stand.toCoords().chebyshevDistance(player.coords) }
                if (booth != null) {
                    walkTo(player, booth.stand.toCoords(), booth)
                }
                val lines = if (event.buy) config.buyLines else config.sellLines
                if (lines.isNotEmpty() && random.nextInt(3) == 0) {
                    player.say(lines.random(random).replace("{item}", event.itemName))
                }
            }
            is MeisterEvent.Traded -> {}
        }
    }

    private fun followTarget(player: Player, dest: CoordGrid, config: MeisterWorldConfig) {
        val cycle = mapClock.cycle
        if (player.coords == dest) {
            target = null
            failures = 0
            targetBooth?.let { booth ->
                player.faceSquare(booth.face.toCoords())
                runCatching { player.anim(config.boothAnim) }
            }
            targetBooth = null
            return
        }
        val stalled = cycle - lastMoveCycle >= STALL_TICKS
        val tooLong = cycle - targetSince >= WALK_BUDGET_TICKS
        if (!stalled && !tooLong) {
            return
        }
        target = null
        targetBooth = null
        if (++failures >= MAX_FAILURES) {
            logger.warn { "[Master Meister] stuck at ${player.coords} $failures times; returning him to his spawn." }
            home(player, config)
        }
    }

    private fun idle(player: Player, config: MeisterWorldConfig) {
        val roll = random.nextInt(100)
        when {
            roll < 50 -> stroll(player, config)
            roll < 70 && config.idleAnims.isNotEmpty() -> runCatching { player.anim(config.idleAnims.random(random)) }
            roll < 80 && config.idleLines.isNotEmpty() -> player.say(config.idleLines.random(random))
            else -> {}
        }
    }

    private fun stroll(player: Player, config: MeisterWorldConfig) {
        val radius = config.strollRadius.coerceAtLeast(1)
        repeat(STROLL_ATTEMPTS) {
            val dest = player.coords.translate(random.nextInt(-radius, radius + 1), random.nextInt(-radius, radius + 1))
            if (dest != player.coords && config.area.contains(dest) && isWalkable(dest)) {
                walkTo(player, dest, null)
                return
            }
        }
    }

    private fun walkTo(player: Player, dest: CoordGrid, booth: MeisterBooth?) {
        target = dest
        targetBooth = booth
        targetSince = mapClock.cycle
        lastMoveCycle = mapClock.cycle
        services.walkTo(player, dest)
    }

    private fun home(player: Player, config: MeisterWorldConfig) {
        target = null
        targetBooth = null
        failures = 0
        services.telejump(player, config.spawn.toCoords())
    }

    private fun isWalkable(coords: CoordGrid): Boolean = collision[coords.x, coords.z, coords.level] and BLOCKED == 0

    private companion object {
        private const val APPEARANCE_SEED = 20_261_016L
        private const val SPAWN_RETRY_TICKS = 100
        private const val STALL_TICKS = 8
        private const val WALK_BUDGET_TICKS = 40
        private const val MAX_FAILURES = 3
        private const val STROLL_ATTEMPTS = 6
        private const val BLOCKED =
            CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.BLOCK_PLAYERS
    }
}
