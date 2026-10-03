package org.rsmod.content.quest.area.karamja.piratestreasure

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA_CRATE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CRATE_CAPACITY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.KARAMJA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.SLICED_BANANA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Luthas's export crate outside his hut. Each player fills their own crate, and only once Luthas
 * has hired them; while Redbeard Frank is waiting on his rum, a bottle can be hidden in it too.
 */
class BananaCrate @Inject constructor(private val treasure: PiratesTreasureQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(BANANA_CRATE) { search() }
        onOpLoc2(BANANA_CRATE) { fill() }
        onOpLocU(BANANA_CRATE, BANANA) { packOne() }
        onOpLocU(BANANA_CRATE, KARAMJA_RUM) { stashRum() }
        onOpLocU(BANANA_CRATE, BANANA_RUM) {
            mesbox("Pirates don't usually take banana with their Karamjan rum.")
        }
        onOpLocU(BANANA_CRATE, SLICED_BANANA_RUM) {
            mesbox("Pirates don't usually take sliced banana with their Karamjan rum.")
        }
    }

    private suspend fun ProtectedAccess.search() {
        val bananas = player.ptCrateBananas
        if (bananas >= CRATE_CAPACITY) {
            mesbox("The crate is full of bananas.")
        } else {
            mes("The crate has $bananas banana${if (bananas == 1) "" else "s"} inside.")
        }
        if (player.ptCrateRum) {
            mesbox("There is also some rum stashed in here too.")
        }
    }

    private suspend fun ProtectedAccess.fill() {
        if (!player.ptLuthasJob) {
            mes(NOT_EMPLOYED)
            return
        }
        val space = CRATE_CAPACITY - player.ptCrateBananas
        if (space <= 0) {
            mes(CRATE_FULL)
            return
        }
        val carried = inv.count(BANANA)
        if (carried == 0) {
            mes("You don't have any bananas to put in the crate.")
            return
        }
        val packed = minOf(space, carried)
        if (invDel(inv, BANANA, packed).failure) {
            return
        }
        player.ptCrateBananas += packed
        mesbox("You pack all your bananas into the crate.")
    }

    private suspend fun ProtectedAccess.packOne() {
        if (!player.ptLuthasJob) {
            mes(NOT_EMPLOYED)
            return
        }
        if (player.ptCrateBananas >= CRATE_CAPACITY) {
            mes(CRATE_FULL)
            return
        }
        if (invDel(inv, BANANA, 1).failure) {
            return
        }
        player.ptCrateBananas += 1
        mesbox("You pack a banana into the crate.")
    }

    private suspend fun ProtectedAccess.stashRum() {
        if (!player.ptLuthasJob || treasure.stage(player) != STAGE_STARTED) {
            mes(NOT_EMPLOYED)
            return
        }
        if (player.ptCrateRum) {
            mes("There's already some rum in here...")
            return
        }
        if (invDel(inv, KARAMJA_RUM, 1).failure) {
            return
        }
        player.ptCrateRum = true
        mesbox("You stash the rum in the crate.")
    }

    private companion object {
        const val NOT_EMPLOYED = "Why would I want to do that?"
        const val CRATE_FULL = "The crate is already full."
    }
}
