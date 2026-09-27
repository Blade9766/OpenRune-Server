package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.client.NoopClientCycle
import org.rsmod.game.entity.Player

/**
 * Player-info avatars for players the server drives itself, which have no network session. Call
 * [attach] once the player has a slot and before it is added to the player registry, the same point
 * a real login allocates its avatar; call [detach] after removing it from the registry.
 */
@Singleton
class BotSessions
@Inject
constructor(private val service: NetworkService<Player>, private val regions: RegionRegistry) {
    private val allocated = HashMap<Player, Infos>()

    fun attach(player: Player) {
        val infos = service.infoProtocols.alloc(player.slotId, OldSchoolClientType.DESKTOP)
        allocated[player] = infos
        val cycle = RspCycle(session = null, infos = infos, regions = regions)
        player.client = BotClient(player)
        player.clientCycle = cycle
        cycle.init(player)
    }

    fun detach(player: Player) {
        val infos = allocated.remove(player) ?: return
        service.infoProtocols.dealloc(infos)
        player.client = NoopClient
        player.clientCycle = NoopClientCycle
    }
}

/** Drops everything sent to a bot, logging its game messages at debug level for diagnosis. */
private class BotClient(private val player: Player) : Client<Any, Any> {
    private val logger = InlineLogger()

    override fun close() {}

    override fun write(message: Any) {
        if (message is MessageGame) {
            logger.debug { "[bot ${player.displayName}] ${message.message}" }
        }
    }

    override fun read(player: Player) {}

    override fun flush() {}

    override fun flushHighPriority() {}

    override fun unregister(service: Any, player: Player) {}
}
