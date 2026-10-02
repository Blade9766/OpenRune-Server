package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_GIVEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_RETURNED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.OLD_MAN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT_REPAIRED
import org.rsmod.content.quest.area.morytania.ghostsahoy.owns
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Old Man on the deck of the wreck, the Old Crone's runaway son, still loyal to his skeletal
 * captain. He recognises the model ship only once its flag is mended and dyed exactly as the
 * wreck's own flag, keeps it, and in exchange has the Captain give up the chest key. A lost key is
 * replaced as long as the captain's chest still holds a scrap the player needs.
 */
class OldMan @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(OLD_MAN) { startDialogue(it.npc) { oldMan() } }
        onOpLoc1(CAPTAIN_SKELETON) { mes("The skeletal captain stares back at you with empty eyes.") }
    }

    private suspend fun Dialogue.oldMan() {
        chatNpc(neutral, "Ahoy there, landlubber! Welcome aboard the finest ship ever to sail. Well. She was.")
        if (ahoy.stage(player) != STAGE_GATHER) {
            return
        }
        when (ahoy.toyBoat(player)) {
            BOAT_GIVEN -> boatQuestion()
            BOAT_RETURNED -> afterBoat()
            else -> askKey()
        }
    }

    private suspend fun Dialogue.boatQuestion() {
        val carriesBoat = player.inv.contains(TOY_BOAT) || player.inv.contains(TOY_BOAT_REPAIRED)
        val options = buildList {
            if (carriesBoat) add("Is this your toy boat?" to true)
            add("Could I have the key to the captain's chest?" to false)
        }
        if (!menu(options)) {
            askKey()
            return
        }
        chatPlayer(quiz, "Is this your toy boat?")
        when {
            player.inv.contains(TOY_BOAT) -> {
                chatNpc(confused, "Hmm. I made a model just like it as a boy, but mine had a flag.")
            }
            !ahoy.isFlagCorrect(player) -> {
                chatNpc(confused, "It looks very like the one I made... but the colours on mine were different.")
            }
            else -> returnBoat()
        }
    }

    private suspend fun Dialogue.returnBoat() {
        chatNpc(shocked, "It is! My little ship, flag and all! My mother made me that before I ran away to sea!")
        chatPlayer(neutral, "She still lives in the hut west of the farm. She wanted you to have it.")
        val swapped =
            player.invTransaction(player.inv) {
                val pack = select(player.inv)
                delete {
                    from = pack
                    obj = TOY_BOAT_REPAIRED.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = CHEST_KEY.asRSCM()
                    strictCount = 1
                }
            }
        if (swapped.failure) {
            return
        }
        ahoy.setToyBoat(player, BOAT_RETURNED)
        chatNpc(happy, "You've done me a great kindness. Let me ask the Captain if he can repay it.")
        chatNpc(happy, "The Captain says you may have the key to his chest.")
        objbox(CHEST_KEY, "The old man keeps the model ship and gives you a chest key.")
    }

    private suspend fun Dialogue.askKey() {
        chatPlayer(quiz, "Could I have the key to the captain's chest?")
        chatNpc(neutral, "Hang on, let me ask the Captain.")
        chatNpc(sad, "The Captain says no.")
    }

    private suspend fun Dialogue.afterBoat() {
        if (player.owns(CHEST_KEY) || !ahoy.needsScrap(player, SCRAP_1)) {
            chatNpc(happy, "Thank you again for my little ship, friend.")
            return
        }
        chatPlayer(sad, "I've lost the key you gave me.")
        chatNpc(confused, "Lost it? Between here and the chest? Well, the Captain keeps a spare.")
        if (access.invAdd(access.inv, CHEST_KEY).failure) {
            chatNpc(neutral, "Free a hand and I'll give it to you.")
            return
        }
        objbox(CHEST_KEY, "The old man gives you another chest key.")
    }

    private companion object {
        const val CAPTAIN_SKELETON = "loc.ahoy_skull_captain"
    }
}
