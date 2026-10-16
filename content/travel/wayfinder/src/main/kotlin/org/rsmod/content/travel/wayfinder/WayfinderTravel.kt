package org.rsmod.content.travel.wayfinder

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player

enum class WayfinderTraveller {
    Player,
    Bot,
}

sealed class WayfinderResult {
    data object Arrived : WayfinderResult()

    data object Cancelled : WayfinderResult()

    data class Denied(val reason: String) : WayfinderResult()
}

/**
 * The one way onto the wayfinder network: the mages' dialogue and Teleport op, the teleport
 * interface and bots all travel through [travel], so every rule below applies to all of them.
 * Nothing a client sends is trusted: a destination is only ever named by its registry key, and
 * its tile and requirements come from the registry.
 */
@Singleton
class WayfinderTravel
@Inject
constructor(
    val registry: WayfinderRegistry,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
    private val npcList: NpcList,
) {
    /** The Wilderness level of [destination]'s arrival tile, or `-1` outside the Wilderness. */
    fun wildernessLevel(destination: WayfinderDestination): Int = destination.coords.wildernessLevel(areaChecker)

    /** True for any arrival tile in or bordering the Wilderness, even where no level applies. */
    fun isWilderness(destination: WayfinderDestination): Boolean =
        destination.category == WayfinderCategory.Wilderness ||
            destination.coords.isInWildernessBasic() ||
            wildernessLevel(destination) > 0

    fun needsConfirmation(destination: WayfinderDestination): Boolean =
        destination.needsConfirmation || isWilderness(destination)

    /** The wayfinder mage [player] is standing beside, if any. */
    fun mageNear(player: Player): Npc? =
        npcList.filter { WayfinderMages.isMage(it) && isNear(player, it) }.minByOrNull { it.coords.chebyshevDistance(player.coords) }

    /**
     * Why [player] may not travel to [destination] through [mage] right now, or `null` if they
     * may. [starting] also refuses a second request while one is still being cast.
     */
    fun denial(
        player: Player,
        mage: Npc?,
        destination: WayfinderDestination?,
        traveller: WayfinderTraveller,
        now: Int,
        starting: Boolean = true,
    ): String? {
        if (destination == null || !destination.enabled) {
            return "That destination is not on the wayfinder network."
        }
        if (traveller == WayfinderTraveller.Bot && !destination.botAllowed) {
            return "Bots may not travel to ${destination.name}."
        }
        if (mage == null || !WayfinderMages.isMage(mage) || !isNear(player, mage)) {
            return "You need to stand beside a wayfinder to travel."
        }
        if (player.hitpoints <= 0 || DEATH_QUEUE in player.queueList) {
            return "You can't travel while you're dying."
        }
        if (starting && player.actionDelay > now) {
            return "You're already being teleported."
        }
        val quest = destination.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            val questName = Quest.get(quest)?.displayName ?: "a quest"
            return "You need to complete $questName to travel to ${destination.name}."
        }
        return teleportValidator.validate(player, TeleportType.Standard, areaChecker)
    }

    fun isNear(player: Player, mage: Npc): Boolean =
        mage.isSlotAssigned &&
            mage.coords.level == player.coords.level &&
            mage.coords.chebyshevDistance(player.coords) <= MAGE_RANGE

    /**
     * Sends [access]'s player to the destination named [key]. Dangerous destinations need
     * [confirm] to agree first (a player who cancels stays where they are); bots never confirm
     * and only reach destinations the registry allows them. The request is checked when it is
     * made and again right before the player moves, after the cast animation.
     */
    suspend fun travel(
        access: ProtectedAccess,
        mage: Npc,
        key: String,
        traveller: WayfinderTraveller,
        confirm: (suspend ProtectedAccess.(WayfinderDestination) -> Boolean)? = null,
    ): WayfinderResult {
        val player = access.player
        val destination = registry[key]
        denial(player, mage, destination, traveller, access.mapClock)?.let { return deny(access, it) }
        checkNotNull(destination)
        if (needsConfirmation(destination) && traveller == WayfinderTraveller.Player) {
            val confirmed = confirm?.invoke(access, destination) ?: false
            if (!confirmed) {
                access.mes("You decide to stay where you are.")
                return WayfinderResult.Cancelled
            }
            denial(player, mage, destination, traveller, access.mapClock)?.let { return deny(access, it) }
        }
        access.actionDelay = access.mapClock + ACTION_DELAY
        access.ifClose()
        val incantation = WayfinderMages.incantation(mage)
        mage.facePlayer(player)
        mage.say(incantation)
        mage.anim(MAGE_CAST_ANIM)
        access.anim(TELEPORT_ANIM)
        access.spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        access.soundSynth(TELEPORT_SOUND)
        access.delay(TELEPORT_DELAY)
        mage.resetFaceEntity()
        val late = denial(player, mage, registry[key], traveller, access.mapClock, starting = false)
        if (late != null) {
            access.anim(TELEPORT_END_ANIM)
            return deny(access, late)
        }
        access.telejump(destination.coords, TeleportType.Exempt)
        if (player.coords != destination.coords) {
            return deny(access, "The magic fizzles out. Try again in a moment.")
        }
        access.anim(TELEPORT_END_ANIM)
        if (traveller == WayfinderTraveller.Player) {
            WayfinderPlayerState.addRecent(player, destination.slot)
        }
        access.mes("${WayfinderMages.name(mage)}'s magic carries you to ${destination.name}.")
        return WayfinderResult.Arrived
    }

    private fun deny(access: ProtectedAccess, reason: String): WayfinderResult.Denied {
        access.mes(reason)
        return WayfinderResult.Denied(reason)
    }

    companion object {
        /** Tiles from a mage a traveller may stand. Players are walked beside the mage first. */
        const val MAGE_RANGE = 3

        private const val DEATH_QUEUE = "queue.death"
        private const val ACTION_DELAY = 5
        private const val TELEPORT_DELAY = 3
        private const val MAGE_CAST_ANIM = "seq.human_casting"
        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.teleport_all"
    }
}
