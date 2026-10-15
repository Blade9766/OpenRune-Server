package org.rsmod.content.quest.area.desert.icthlarin

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.HOLY_SYMBOL
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FIRST_FLASHBACK
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_JAR_RETURNED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SECOND_FLASHBACK
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SECOND_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_THIRD_FLASHBACK
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_THIRD_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.UNHOLY_SYMBOL
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/** The three memories the hypnotised player relives inside Klenter's pyramid. */
enum class Flashback {
    /** Breaking in: the traps, the pit and the western chamber's door puzzle. */
    BreakIn,

    /** Stealing the jar from the western chamber, past its guardian. */
    TheftOfTheJar,

    /** Hiding the Devourer's unholy symbol in the ceremonial chamber. */
    HidingTheSymbol,
}

/** Fading borrows the overlay slot the flashback tint lives in, so a playing flashback takes it back. */
internal suspend fun ProtectedAccess.fadeBackIn() {
    fadeFromBlack()
    if (player.ilhInFlashback) {
        ifOpenFullOverlay(Flashbacks.OVERLAY)
    } else {
        closeFadeOverlay()
    }
}

/**
 * The flashbacks. While one plays the screen is tinted by `interface.ics_flashback`, the player
 * cannot teleport away, and the item they carry for the present day (the stolen jar, or the new
 * holy symbol) is swapped for the unholy symbol they carried for their mistress. However a
 * flashback ends - finished, abandoned by climbing out, by death or by logging out - the swap is
 * undone, so the jar and the holy symbol can never be lost to a memory.
 */
@Singleton
class Flashbacks
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val eventBus: EventBus,
) {
    fun active(player: Player): Flashback? {
        if (!player.ilhInFlashback) {
            return null
        }
        return when (quest.stage(player)) {
            STAGE_FIRST_FLASHBACK -> Flashback.BreakIn
            in STAGE_SECOND_FLASHBACK until STAGE_SECOND_FLASHBACK_DONE -> Flashback.TheftOfTheJar
            in STAGE_THIRD_FLASHBACK until STAGE_THIRD_FLASHBACK_DONE -> Flashback.HidingTheSymbol
            else -> null
        }
    }

    fun begin(player: Player, flashback: Flashback) {
        val present = presentItem(player, flashback)
        if (present != null) {
            player.invDel(player.inv, present, count = player.inv.count(present), strict = false)
        }
        if (UNHOLY_SYMBOL !in player.inv) {
            player.invAdd(player.inv, UNHOLY_SYMBOL, strict = false)
        }
        player.ilhInFlashback = true
        if (flashback == Flashback.TheftOfTheJar) {
            val jar = quest.jar(player)
            if (jar != null && jar.obj !in player.inv) {
                VarPlayerIntMapSetter.set(player, jar.shelfVarbit, 0)
            }
        }
        player.ifOpenFullOverlay(OVERLAY, eventBus)
        quest.syncVars(player)
    }

    /**
     * Ends whatever flashback [player] is in and gives back what it took. Safe to call when no
     * flashback is playing.
     */
    fun end(player: Player) {
        val flashback = active(player)
        if (!player.ilhInFlashback) {
            return
        }
        player.ilhInFlashback = false
        player.ifCloseOverlay(OVERLAY, eventBus)
        val unholy = player.inv.count(UNHOLY_SYMBOL)
        if (unholy > 0) {
            player.invDel(player.inv, UNHOLY_SYMBOL, count = unholy, strict = false)
        }
        val present = flashback?.let { presentItem(player, it) } ?: restoreFor(player)
        if (present != null && present !in player.inv) {
            player.invAdd(player.inv, present, strict = false)
        }
        quest.syncVars(player)
    }

    /** What the player carries in the present that the memory replaces. */
    private fun presentItem(player: Player, flashback: Flashback): String? =
        when (flashback) {
            Flashback.BreakIn, Flashback.TheftOfTheJar ->
                if (quest.stage(player) < STAGE_JAR_RETURNED) quest.jar(player)?.obj else null
            Flashback.HidingTheSymbol -> HOLY_SYMBOL
        }

    /** A flashback flag left over from a stage the memory no longer matches (a `::queststage` jump). */
    private fun restoreFor(player: Player): String? {
        val stage = quest.stage(player)
        return when {
            stage < STAGE_JAR_RETURNED -> quest.jar(player)?.obj
            stage < STAGE_THIRD_FLASHBACK_DONE -> HOLY_SYMBOL
            else -> null
        }
    }

    companion object {
        const val OVERLAY = "interface.ics_flashback"
    }
}

/** Memories are not so easily escaped: no teleporting out of a flashback. */
class FlashbackTeleportHook @Inject constructor(private val flashbacks: Flashbacks) : PlayerTeleportValidateHook {
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        if (flashbacks.active(player) == null || !SophanemCoords.inPyramid(player.coords)) {
            return null
        }
        return "A strange force holds you within the pyramid."
    }
}
