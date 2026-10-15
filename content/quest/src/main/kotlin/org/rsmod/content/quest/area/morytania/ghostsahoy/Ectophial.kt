package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOPHIAL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOPHIAL_EMPTY
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ectophial, Velorina's reward. Emptying it pours the slime at the player's feet and carries
 * them to the Ectofuntus, where they refill it straight away; an empty ectophial can also be
 * refilled by using it on the Ectofuntus, which is how a refill cut short is finished.
 *
 * The phial is swapped for the empty one in the same step the teleport is validated, before
 * anything waits, so a second click finds no full ectophial to empty: no extra teleport is
 * queued and no phial is duplicated. It obeys the usual teleport restrictions (level 20
 * Wilderness and below).
 */
class Ectophial
@Inject
constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(ECTOPHIAL) { empty(it.slot) }
        onOpLocU(ECTOFUNTUS, ECTOPHIAL_EMPTY) { refillAt() }
    }

    private suspend fun ProtectedAccess.empty(slot: Int) {
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return
        }
        val emptyType = checkNotNull(ServerCacheManager.getItem(ECTOPHIAL_EMPTY.asRSCM()))
        if (inv[slot]?.id != ECTOPHIAL.asRSCM() || invReplaceSlot(inv, slot, 1, emptyType).failure) {
            return
        }
        anim(EMPTY_SEQ)
        spotanim(POUR_SPOTANIM)
        mes("You empty the ectoplasm onto the ground around your feet...")
        delay(EMPTY_TICKS)
        telejump(ARRIVAL, TeleportType.Exempt)
        mes("...and the world changes around you.")
        delay(1)
        refill()
    }

    private suspend fun ProtectedAccess.refillAt() {
        arriveDelay()
        refill()
    }

    private suspend fun ProtectedAccess.refill() {
        if (!isWithinDistance(ECTOFUNTUS_TILE, REFILL_RANGE, width = ECTOFUNTUS_SIZE, length = ECTOFUNTUS_SIZE)) {
            return
        }
        val slot = inv.indexOfFirst { it?.id == ECTOPHIAL_EMPTY.asRSCM() }
        if (slot < 0) {
            return
        }
        faceSquare(ECTOFUNTUS_TILE)
        anim(REFILL_SEQ)
        delay(REFILL_TICKS)
        val full = checkNotNull(ServerCacheManager.getItem(ECTOPHIAL.asRSCM()))
        if (inv[slot]?.id != ECTOPHIAL_EMPTY.asRSCM() || invReplaceSlot(inv, slot, 1, full).failure) {
            return
        }
        mes("You refill the ectophial from the Ectofuntus.")
    }

    companion object {
        const val ECTOFUNTUS = "loc.ahoy_ectofuntus"
        val ECTOFUNTUS_TILE = CoordGrid(3658, 3518, 0)
        const val ECTOFUNTUS_SIZE = 4
        val ARRIVAL = CoordGrid(3659, 3522, 0)
        private const val REFILL_RANGE = 3
        private const val EMPTY_SEQ = "seq.ahoy_ecto_teleport"
        private const val POUR_SPOTANIM = "spotanim.ectophial_pour_spotanim"
        private const val REFILL_SEQ = "seq.quest_ahoy_human_filling_bucket"
        private const val EMPTY_TICKS = 3
        private const val REFILL_TICKS = 2
    }
}
