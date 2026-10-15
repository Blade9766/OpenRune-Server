package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.KINGS_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.MESSENGER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.UNDERGROUND_PASS
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The King's Messenger, who finds a player some minutes after they log in once the Underground
 * Pass is done and Regicide not yet begun, and hands them King Lathas's summons. He comes once;
 * the quest is accepted from King Lathas whether or not he ever did.
 */
class KingsMessenger
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val launcher: ProtectedAccessLauncher,
    private val scenes: RegicideScenes,
    private val objRepo: ObjRepository,
    private val collision: CollisionFlagMap,
    private val clock: MapClock,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerLogin {
            if (isDue(player)) {
                player.softTimer(TIMER, random.of(MIN_DELAY, MAX_DELAY))
            }
        }
        onPlayerSoftTimer(TIMER) {
            player.clearSoftTimer(TIMER)
            if (!isDue(player)) {
                return@onPlayerSoftTimer
            }
            if (player.hitpoints <= 0 || player.sceneReturn != 0 || !launcher.launch(player) { deliver() }) {
                player.softTimer(TIMER, RETRY_DELAY)
            }
        }
    }

    /** Only players who have really finished the Underground Pass are sent for, whatever the requirement policy. */
    fun isDue(player: Player): Boolean =
        player.messengerSeen == 0 &&
            regicide.stage(player) == 0 &&
            Quest.get(UNDERGROUND_PASS)?.isQuestCompleted(player) == true

    suspend fun ProtectedAccess.deliver() {
        val tile = collision.nearestFree(coords.translate(1, 0), SPAWN_RADIUS) ?: coords
        val messenger = scenes.spawn(MESSENGER, tile, LIFESPAN)
        try {
            messenger.assignSpawnOwner(player, clock.cycle)
            messenger.facePlayer(player)
            startDialogue(messenger) {
                chatNpc(neutral, "I take it you are ${player.displayName}? King Lathas sent this message for you. He's not a man you'd want to keep waiting.")
                player.messengerSeen = 1
                access.invAddOrDrop(objRepo, KINGS_MESSAGE)
                objbox(KINGS_MESSAGE, "The messenger gives you a scroll.")
            }
        } finally {
            scenes.remove(messenger)
        }
    }

    companion object {
        const val TIMER = "timer.regicide_messenger"
        const val MIN_DELAY = 400
        const val MAX_DELAY = 1200
        const val RETRY_DELAY = 100
        private const val SPAWN_RADIUS = 2
        private const val LIFESPAN = 100
    }
}
