package org.rsmod.api.net.rsprot

import jakarta.inject.Inject
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

/** A client with no connection: writes go nowhere, but its info protocol slot is released. */
class HeadlessClient(private val infos: Infos) : Client<NetworkService<Player>, OutgoingGameMessage> {
    override fun close() {}

    override fun write(message: OutgoingGameMessage) {}

    override fun read(player: Player) {}

    override fun flush() {}

    override fun flushHighPriority() {}

    override fun unregister(service: NetworkService<Player>, player: Player) {
        service.infoProtocols.dealloc(infos)
    }
}

/**
 * Gives a server-driven character (no network session) a real player-info avatar, so connected
 * players see it walk, animate and wear equipment like anyone else.
 */
class HeadlessSessions
@Inject
constructor(private val service: NetworkService<Player>, private val regions: RegionRegistry) {
    @Suppress("UNCHECKED_CAST")
    fun attach(player: Player) {
        val infos = service.infoProtocols.alloc(player.slotId, OldSchoolClientType.DESKTOP)
        val cycle = RspCycle(null, infos, regions)
        player.client = HeadlessClient(infos) as Client<Any, Any>
        player.clientCycle = cycle
        cycle.init(player)
    }
}
