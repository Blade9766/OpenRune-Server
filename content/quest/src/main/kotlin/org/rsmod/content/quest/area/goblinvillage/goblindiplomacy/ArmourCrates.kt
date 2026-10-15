package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.GOBLIN_MAIL
import org.rsmod.content.quest.manager.QuestAttribute
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three crates of spare goblin mail around Goblin Village: inside the north wall of the
 * generals' hut, in the western hut, and on the balcony up the ladders by the gate. Each gives
 * up one goblin mail, once.
 */
class ArmourCrates @Inject constructor(private val goblinDiplomacy: GoblinDiplomacyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CRATE_1) { search(it.loc, goblinDiplomacy.crate1Searched) }
        onOpLoc1(CRATE_2) { search(it.loc, goblinDiplomacy.crate2Searched) }
        onOpLoc1(CRATE_3) { search(it.loc, goblinDiplomacy.crate3Searched) }
    }

    private suspend fun ProtectedAccess.search(crate: BoundLocInfo, searched: QuestAttribute<Boolean>) {
        arriveDelay()
        faceLoc(crate)
        anim(SEARCH_SEQ)
        delay(1)
        if (searched.get(player)) {
            mesbox("You search the crate, but find nothing of interest.")
            return
        }
        if (inv.isFull()) {
            mes("You find some goblin mail in the crate, but you don't have room to take it.")
            return
        }
        invAdd(inv, GOBLIN_MAIL)
        searched.set(player, true)
        goblinDiplomacy.syncVars(player)
        objbox(GOBLIN_MAIL, "You find some goblin mail in the crate.")
    }

    private companion object {
        const val CRATE_1 = "loc.goblin_outpost_large_crate_armour1"
        const val CRATE_2 = "loc.goblin_outpost_large_crate_armour2"
        const val CRATE_3 = "loc.goblin_outpost_large_crate_armour3"
        const val SEARCH_SEQ = "seq.human_pickuptable"
    }
}
