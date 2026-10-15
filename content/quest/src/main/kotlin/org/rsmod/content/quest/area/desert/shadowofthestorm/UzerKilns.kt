package org.rsmod.content.quest.area.desert.shadowofthestorm

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_ASKED_GOLEM
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FOUND_TOME
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TOME
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The four broken kilns scattered through the ruins of Uzer. Once the golem has told the player
 * that Denath hid Josef's book in one of them they grow a "Look-in" option, and
 * `varbit.agrith_kiln` counts how many have been emptied - the tome is in the last one, so a
 * player who searches in order finds it on the fourth and a player who guesses may find it sooner.
 */
@Singleton
class UzerKilns @Inject constructor(private val sots: ShadowOfTheStormQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (kiln in KILNS.indices) {
            onOpLoc1(KILNS[kiln]) { search(kiln) }
        }
    }

    private suspend fun ProtectedAccess.search(kiln: Int) {
        arriveDelay()
        anim(SEARCH_SEQ)
        delay(1)
        if (sots.stage(player) != STAGE_ASKED_GOLEM) {
            mes("You find nothing but old ash.")
            return
        }
        if (kiln != player.kilnSearched) {
            mes("You rake through the ash. Nothing but the leavings of a kiln that went out three thousand years ago.")
            return
        }
        if (inv.isFull()) {
            mes("Something is wedged in the flue, but you have no room to carry it.")
            return
        }
        invAdd(inv, TOME)
        objbox(TOME, "Wedged up inside the flue, where the ash hid it, is a heavy black book.")
        sots.advanceTo(this, STAGE_FOUND_TOME)
    }

    companion object {
        /** In the order `varbit.agrith_kiln` numbers them, north-west first. */
        val KILNS =
            listOf(
                "loc.agrith_kiln_1",
                "loc.agrith_kiln_2",
                "loc.agrith_kiln_3",
                "loc.agrith_kiln_4",
            )

        private const val SEARCH_SEQ = "seq.human_pickuptable"
    }
}
