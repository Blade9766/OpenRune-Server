package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.PlayerQueueList
import org.rsmod.game.queue.QueueCategory

public class PlayerQueueProcessor
@Inject
constructor(private val eventBus: EventBus, private val protectedAccess: ProtectedAccessLauncher) {
    public fun process(player: Player) {
        // Only a strong queue that fires this cycle interrupts. A pending one (a spell that lands
        // in three cycles, say) must leave the player free to keep fighting until it does.
        if (player.queueList.strongQueues > 0 && player.hasStrongQueueDue()) {
            player.interruptForStrongQueue()
        }
        player.publishExpiredQueues()
        player.publishExpiredWeakQueues()
    }

    private fun Player.hasStrongQueueDue(): Boolean =
        queueList.anyDue(QueueCategory.Strong, currentMapClock)

    /**
     * A strong queue that fires this cycle closes the player's modals so the queue can launch. It
     * does _not_ drop the current interaction or route: every hit and auto-retaliate is a strong
     * queue, so clearing them here would replace whatever the player was doing (a manually cast
     * spell, say) with a plain retaliation attack on every hit they take. Scripts that need to
     * stop the player's action, such as the death sequence, do so themselves.
     *
     * A script that is already mid-way - suspended in a delay or a dialogue - is left to finish:
     * closing the modals is enough to unwind a dialogue, and a delayed script (the death sequence
     * itself, for one) must not be cancelled by the very queue it is servicing. The strong queue
     * launches once the script has ended, which [canLaunchQueue] enforces.
     */
    private fun Player.interruptForStrongQueue() {
        ifClose(eventBus)
    }

    private fun Player.publishExpiredQueues() {
        while (queueList.isNotEmpty) {
            var processedNone = true

            val iterator = queueList.iterator() ?: break
            while (iterator.hasNext()) {
                val queue = iterator.next()

                if (queue.shouldCloseModals()) {
                    ifClose(eventBus)
                }

                if (queue.processedCycle != currentMapClock) {
                    queue.processedCycle = currentMapClock
                    queue.remainingCycles--
                }

                val accelerate = pendingLogout && queue.category == QueueCategory.LongAccelerate.id
                if (accelerate) {
                    queue.remainingCycles = 0
                }

                if (queue.remainingCycles > 0) {
                    continue
                }

                if (canLaunchQueue(queue)) {
                    processedNone = false
                    iterator.remove()
                    publish(queue)
                }
            }
            iterator.cleanUp()

            if (processedNone || queueList.size == 1) {
                break
            }
        }
    }

    private fun PlayerQueueList.Queue.shouldCloseModals(): Boolean =
        category == QueueCategory.Strong.id || category == QueueCategory.Soft.id

    private fun Player.canLaunchQueue(queue: PlayerQueueList.Queue): Boolean =
        when (queue.category) {
            QueueCategory.Soft.id -> true
            // Modals were closed by [interruptForStrongQueue]; an interaction never blocks a
            // queue, so only a delay or a coroutine that is still suspended holds a strong queue
            // back.
            QueueCategory.Strong.id -> !isModalButtonProtected
            else -> !isAccessProtected
        }

    private fun Player.publish(queue: PlayerQueueList.Queue) {
        if (queue.category == QueueCategory.Soft.id) {
            val event = PlayerQueueEvents.Soft(this, queue.args, queue.id)
            eventBus.publish(event)
            return
        }
        publishProtected(queue)
    }

    private fun Player.publishProtected(queue: PlayerQueueList.Queue) {
        val event = PlayerQueueEvents.Protected(queue.args, queue.id)
        protectedAccess.launch(this) { eventBus.publish(this, event) }
    }

    private fun Player.publishExpiredWeakQueues() {
        while (weakQueueList.isNotEmpty) {
            var processedNone = true

            val iterator = weakQueueList.iterator() ?: break
            while (iterator.hasNext()) {
                val queue = iterator.next()

                if (queue.processedCycle != currentMapClock) {
                    queue.processedCycle = currentMapClock
                    queue.remainingCycles--
                }

                if (queue.remainingCycles > 0) {
                    continue
                }

                if (!isAccessProtected) {
                    processedNone = false
                    iterator.remove()
                    publishProtected(queue)
                }
            }
            iterator.cleanUp()

            if (processedNone || weakQueueList.size == 1) {
                break
            }
        }
    }
}
