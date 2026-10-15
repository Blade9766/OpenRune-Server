package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onArea
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.ARIANWYN
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER_UNSEALED
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Arianwyn, leader of the elven resistance, stops a player carrying Lord Iorwerth's letter as they
 * come into Ardougne Castle, breaks its seal and has them read it.
 *
 * Coming into the castle grounds by any route, by teleport or by logging in there all set him off,
 * and King Lathas sends for him too if the player somehow reaches the throne first. He appears for
 * the player alone, as their own npc, and leaves when the conversation ends however it ends. The
 * seal counts as broken from the moment he parts it, before the letter is shown, so a
 * conversation cut short there does not have to be repeated.
 */
@Singleton
class Arianwyn
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val scenes: RegicideScenes,
    private val collision: CollisionFlagMap,
    private val clock: MapClock,
) : PluginScript() {
    private val meeting = HashSet<PlayerUid>()

    override fun ScriptContext.startup() {
        onArea(CASTLE_AREA) {
            if (isWaiting(player)) {
                stopAction()
                intercept()
            }
        }
    }

    /** Whether Arianwyn still has to stop [player]: they carry Iorwerth's sealed letter. */
    fun isWaiting(player: Player): Boolean =
        regicide.stage(player) == STAGE_LETTER && player.inv.contains(IORWERTH_MESSAGE)

    suspend fun ProtectedAccess.intercept() {
        if (!isWaiting(player) || !meeting.add(player.uid)) {
            return
        }
        var elf: Npc? = null
        try {
            val tile = collision.nearestFree(coords.translate(1, 0), SPAWN_RADIUS) ?: coords
            elf = scenes.spawn(ARIANWYN, tile, LIFESPAN).also { it.assignSpawnOwner(player, clock.cycle) }
            elf.facePlayer(player)
            faceSquare(tile)
            startDialogue(elf) { conversation() }
        } finally {
            elf?.let(scenes::remove)
            meeting.remove(player.uid)
        }
    }

    private suspend fun org.rsmod.api.player.dialogue.Dialogue.conversation() {
        chatNpc(quiz, "Are you the human by the name ${player.displayName}?")
        chatPlayer(confused, "Yes, that's me?")
        chatNpc(neutral, "Thank Seren, maybe all is not yet lost.")
        chatPlayer(quiz, "What do you mean?")
        chatNpc(neutral, "I am Arianwyn, leader of the Elven Resistance. We've been watching you since you arrived in Tirannwn. We know it was you that murdered King Tyras.")
        chatNpc(neutral, "There is much to explain and little time. You carry a message from Lord Iorwerth, do you not?")
        chatPlayer(neutral, "Well you seem to know everything else so I'll not lie. Yes.")
        chatNpc(neutral, "Good, we understand each other well. Now hand it here and find the truth of who your real employer is.")
        regicide.advanceTo(access, STAGE_LETTER_UNSEALED)
        objbox(IORWERTH_MESSAGE, "You show the message to the elf, he moves his lips silently and the seal on the message is parted.")
        RegicideReading.showLetter(access)
        chatPlayer(shocked, "I had no idea! Why would the king do this?")
        chatNpc(neutral, "I have a few ideas, now is not the time to discuss them though. For now, you should go to King Lathas as normal. Give him the message and act like nothing unusual has happened.")
        chatNpc(neutral, "Once you are done, return to Tirannwn and seek out Islwyn and Eluned. You'll find them roaming the Isafdar forest. They will bring you to me.")
        chatPlayer(quiz, "You want me to help you? Okay, what's in it for me?")
        chatNpc(neutral, "The chance for redemption after all you've done? If that doesn't take your fancy, consider this...")
        chatNpc(worried, "This isn't a struggle for land or title. It is a war for life, all life. If Iorwerth and Lathas manage to summon Zamorak, we all die.")
        chatNpc(neutral, "Deliver your message, act normal. We'll meet again soon.")
    }

    companion object {
        const val CASTLE_AREA = "area.regicide_ardougne_castle"
        private const val SPAWN_RADIUS = 2
        private const val LIFESPAN = 300
    }
}
