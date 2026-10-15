package org.rsmod.content.quest.area.lumbridge.losttribe

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.HAM_ROBES_FOUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.HAM_SILVERWARE_FOUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.SILVERWARE
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SILVERWARE_MISSING
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The proof against Sigmund: his locked chest in the spinning-wheel room beside the Duke's study,
 * which holds his H.A.M. robes, and the crate by the ladder of the H.A.M. hideout where he hid the
 * Lumbridge silverware.
 */
class SigmundsEvidence
@Inject
constructor(private val lostTribe: LostTribeQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST) { openChest() }
        onOpLoc1(CRATE) { searchCrate() }
    }

    private suspend fun ProtectedAccess.openChest() {
        arriveDelay()
        if (CHEST_KEY !in player.inv) {
            mes("You don't have the key to open this chest.")
            soundSynth(LOCKED_SOUND)
            return
        }
        if (player.lostTribeHam >= HAM_ROBES_FOUND) {
            mes("The chest is empty.")
            return
        }
        anim(OPEN_SEQ)
        soundSynth(CHEST_SOUND)
        delay(1)
        invDel(inv, CHEST_KEY)
        for (robe in ROBES) {
            invAddOrDrop(objRepo, robe)
        }
        player.lostTribeHam = HAM_ROBES_FOUND
        startDialogue {
            mesbox("In the chest you find a set of H.A.M. robes!")
            chatPlayer(shocked, "Sigmund must be a member of the Humans Against Monsters cult!")
        }
    }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        anim(SEARCH_SEQ)
        delay(1)
        val wanted =
            lostTribe.stage(player) == STAGE_SILVERWARE_MISSING && !lostTribe.ownsItem(this, SILVERWARE)
        if (!wanted) {
            mes("You search the crate but find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, SILVERWARE)
        player.lostTribeHam = HAM_SILVERWARE_FOUND
        startDialogue { objbox(SILVERWARE, "You find the Lumbridge Silverware!") }
    }

    private companion object {
        const val CHEST = "loc.lost_tribe_chest"
        const val CRATE = "loc.lost_tribe_crate"
        const val OPEN_SEQ = "seq.human_pickupfloor"
        const val SEARCH_SEQ = "seq.human_pickupfloor"
        const val CHEST_SOUND = "synth.chest_open"
        const val LOCKED_SOUND = "synth.locked"

        val ROBES = listOf("obj.ham_hood", "obj.ham_shirt", "obj.ham_robe")
    }
}
