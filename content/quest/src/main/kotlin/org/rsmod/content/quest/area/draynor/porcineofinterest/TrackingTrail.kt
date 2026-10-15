package org.rsmod.content.quest.area.draynor.porcineofinterest

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The trail north-east from the crossroads: Sarah's wrecked cart, the produce dropped along the
 * way and the dead trees something heavy shouldered over.
 *
 * All of it is `varbit.porcine` multiloc scenery that only exists while the quest is running, so
 * the handlers need no stage checks of their own - the ops simply are not there otherwise.
 */
class TrackingTrail @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CART) { inspectCart() }
        onOpLoc1(CABBAGE) {
            mesbox("The cabbage is damaged and has begun to yellow.")
        }
        onOpLoc1(CARROT) { inspectCarrot() }
        onOpLoc1(POTATOES) { inspectPotatoes() }
        onOpLoc1(TREE) {
            startDialogue {
                chatPlayer(
                    quiz,
                    "Something pretty big must have come running through here to knock this over.",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.inspectCart() {
        mesbox(
            "It seems as though the monster was trying to run off with some of Sarah's produce. " +
                "From the looks of things, it was heading north-east.",
        )
        player.porcineInspectedCart = true
    }

    private suspend fun ProtectedAccess.inspectCarrot() {
        mesbox("The carrot has been slightly gnawed on.")
        startDialogue {
            chatPlayer(
                quiz,
                "Weird... What sort of monster charges at someone and then steals a bunch of " +
                    "carrots?",
            )
        }
    }

    private suspend fun ProtectedAccess.inspectPotatoes() {
        mesbox("These potatoes seem to have been largely untouched.")
        startDialogue {
            chatPlayer(laugh, "Would you look at that... Apparently our monster is picky.")
        }
    }

    private companion object {
        const val CART = "loc.porcine_tracking_cart_visible"
        const val CABBAGE = "loc.porcine_tracking_cabbage_visible"
        const val CARROT = "loc.porcine_tracking_carrot_visible"
        const val POTATOES = "loc.porcine_tracking_potatoes_visible"
        const val TREE = "loc.porcine_tracking_tree_visible"
    }
}
