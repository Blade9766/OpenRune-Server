package org.rsmod.content.events.halloween

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.table.HalloweenTreatRewardsRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GreatCauldronScript @Inject constructor(private val world: HalloweenWorld) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(GREAT_CAULDRON) { deposit() }
        onOpLoc2(GREAT_CAULDRON) { check() }
        onOpLoc3(GREAT_CAULDRON) { claim() }
    }

    private suspend fun ProtectedAccess.deposit() {
        if (!player.hasTreatCauldron()) {
            startDialogue {
                chatNpcSpecific(
                    CHILD,
                    JACK_LANTERN,
                    happy,
                    "Hey, you need a Treat cauldron to deposit treats from! Come speak to me if " +
                        "you need one.",
                )
            }
            return
        }
        val stored = player.treats
        if (stored == 0) {
            startDialogue { objbox(TREAT_CAULDRON, "Your Treat cauldron is empty.") }
            return
        }
        val amount =
            startDialogueFor {
                choice3(
                    "Deposit All",
                    stored,
                    "Deposit X",
                    -1,
                    "Cancel",
                    0,
                    title = "You have $stored treats stored.",
                )
            }
        val deposit =
            if (amount == -1) countDialog("Deposit how many? (0 - $stored)").coerceIn(0, stored)
            else amount
        if (deposit <= 0 || deposit > player.treats) {
            return
        }
        player.treats -= deposit
        player.treatsDeposited += deposit
        world.cheerDeposit()
        mes("You deposit $deposit treats into the Great cauldron.")
        startDialogue { claimGifts() }
    }

    private suspend fun ProtectedAccess.check() {
        val next = nextReward()
        startDialogue {
            mesbox(
                "You have deposited ${player.treatsDeposited} treats into the Great cauldron. " +
                    "Your Treat cauldron holds ${player.treats} more." +
                    (next?.let { "<br>Your next gift is at ${it.treats} treats." } ?: ""),
            )
        }
    }

    private suspend fun ProtectedAccess.claim() {
        startDialogue {
            if (claimGifts()) {
                return@startDialogue
            }
            val next = access.nextReward()
            if (next == null) {
                mesbox("You've received every gift the Great cauldron has to offer!")
            } else {
                mesbox(
                    "You'll receive your next gift after depositing " +
                        "${next.treats - player.treatsDeposited} more treats!",
                )
            }
        }
    }

    private fun ProtectedAccess.nextReward(): HalloweenTreatRewardsRow? =
        HalloweenTreatRewardsRow.all().sortedBy { it.index }.firstOrNull { !received(it) }

    private fun ProtectedAccess.received(row: HalloweenTreatRewardsRow): Boolean =
        vars["varbit.hw23_reward_received_${row.index}"] != 0

    /** Hands out every earned, unclaimed gift in ladder order; returns whether any was due. */
    private suspend fun Dialogue.claimGifts(): Boolean {
        val due =
            HalloweenTreatRewardsRow.all()
                .sortedBy { it.index }
                .filter { it.treats <= player.treatsDeposited && !access.received(it) }
        for (row in due) {
            if (!giveGift(row)) {
                return true
            }
            vars["varbit.hw23_reward_received_${row.index}"] = 1
        }
        return due.isNotEmpty()
    }

    private suspend fun Dialogue.giveGift(row: HalloweenTreatRewardsRow): Boolean {
        val obj = row.obj
        when (row.kind) {
            REWARD_EMOTE -> {
                vars[TRICK_EMOTE] = 1
                mesbox("You've received a gift: the <col=0000b2>Trick</col> emote!")
            }
            REWARD_STYLE -> {
                checkNotNull(obj) { "Style gift ${row.index} has no obj." }
                objbox(
                    obj.internalName,
                    "You've received a gift: the <col=0000b2>${row.styleName ?: obj.name}</col> " +
                        "style for your Spider hat!",
                )
            }
            else -> {
                checkNotNull(obj) { "Item gift ${row.index} has no obj." }
                val count = row.count ?: 1
                if (access.invAdd(access.inv, obj.internalName, count).failure) {
                    val spaces = if (count == 1) "1 inventory space" else "$count inventory spaces"
                    objbox(
                        obj.internalName,
                        "You need to free up $spaces to receive your gift! You can come back and " +
                            "claim it when you have space.",
                    )
                    return false
                }
                row.holidayIndex?.let { access.unlockHolidayItem(it) }
                objbox(obj.internalName, "You've received a gift: <col=0000b2>${obj.name}</col>")
            }
        }
        return true
    }

    private suspend fun <T> ProtectedAccess.startDialogueFor(block: suspend Dialogue.() -> T): T {
        var result: T? = null
        startDialogue { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private companion object {
        const val GREAT_CAULDRON = "loc.hw22_communitygoal_0"
        const val TREAT_CAULDRON = "obj.hw22_treat_cauldron_0"
        const val TRICK_EMOTE = "varbit.emote_trick"
        const val REWARD_STYLE = 1
        const val REWARD_EMOTE = 2
    }
}
