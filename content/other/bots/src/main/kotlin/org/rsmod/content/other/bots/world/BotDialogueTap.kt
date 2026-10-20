package org.rsmod.content.other.bots.world

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

/**
 * Sits between a bot and the client its session gave it, reading the chat menu lines a real player
 * would see: the server only ever sends them to the client, through `chatbox_multi_init`.
 */
class BotDialogueTap(private val delegate: Client<Any, Any>) : Client<Any, Any> {
    var choices: List<String> = emptyList()
        private set

    override fun write(message: Any) {
        if (message is RunClientScript && message.id == CHATBOX_MULTI_INIT) {
            choices = (message.values.getOrNull(1) as? String)?.split('|').orEmpty()
        }
        delegate.write(message)
    }

    override fun close() = delegate.close()

    override fun read(player: Player) = delegate.read(player)

    override fun flush() = delegate.flush()

    override fun flushHighPriority() = delegate.flushHighPriority()

    override fun unregister(service: Any, player: Player) = delegate.unregister(service, player)

    private companion object {
        private val CHATBOX_MULTI_INIT: Int by lazy {
            "clientscript.[clientscript,chatbox_multi_init]".asRSCM(RSCMType.CLIENTSCRIPT)
        }
    }
}
