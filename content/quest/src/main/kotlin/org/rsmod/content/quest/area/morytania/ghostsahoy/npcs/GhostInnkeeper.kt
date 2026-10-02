package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BEDSHEET
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BEDSHEET_SLIMED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET_OF_SLIME
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.INNKEEPER
import org.rsmod.content.quest.area.morytania.ghostsahoy.hasGhostspeak
import org.rsmod.content.quest.area.morytania.ghostsahoy.owns
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ghost innkeeper, whose one living guest needs fresh bedding, and the slime that turns that
 * bedsheet into a passable ghost costume: a bucket of slime poured over it, or the sheet dipped
 * straight into the pool of slime under the Ectofuntus. The green bedsheet is worn in the head
 * slot with its own cache model.
 */
class GhostInnkeeper @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(INNKEEPER) { innkeeper(it.npc) }
        onOpHeldU(BUCKET_OF_SLIME, BEDSHEET) { slimeWithBucket() }
        onOpLocU(SLIME_POOL, BEDSHEET) { dipInPool() }
    }

    private suspend fun ProtectedAccess.innkeeper(npc: org.rsmod.game.entity.Npc) {
        if (!player.hasGhostspeak()) {
            startDialogue(npc) { chatNpc(neutral, "Woooo woo wooooo?") }
            mes("You can't understand what the ghost is saying.")
            return
        }
        startDialogue(npc) { talk() }
    }

    private suspend fun Dialogue.talk() {
        chatNpc(happy, "Welcome to my inn, traveller. We do not get many visitors with a pulse.")
        val topic = menu("Do you have any job I can do?" to true, "Just looking around, thanks." to false)
        if (!topic) {
            chatPlayer(neutral, "Just looking around, thanks.")
            return
        }
        chatPlayer(quiz, "Do you have any job I can do?")
        if (player.owns(BEDSHEET) || player.owns(BEDSHEET_SLIMED)) {
            chatNpc(quiz, "You've still got the bedsheet for Robin, haven't you? That's job enough.")
            return
        }
        if (!ahoy.isSheetRequested(player)) {
            chatNpc(neutral, "Funny you should ask. We've a living guest, Robin, and he keeps complaining about his bedding.")
            chatNpc(quiz, "Would you take him a clean bedsheet?")
            if (!menu("Yes, I'd be delighted." to true, "No, sorry." to false)) {
                chatPlayer(neutral, "No, sorry.")
                return
            }
            chatPlayer(happy, "Yes, I'd be delighted.")
        } else {
            chatNpc(confused, "Lost the bedsheet already? Here, have another. Don't tell the laundry.")
        }
        if (access.invAdd(access.inv, BEDSHEET).failure) {
            chatNpc(neutral, "You'll need a free hand to carry it.")
            return
        }
        ahoy.setSheetRequested(player)
        objbox(BEDSHEET, "The innkeeper hands you a clean bedsheet.")
    }

    private fun ProtectedAccess.slimeWithBucket() {
        val dyed =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = BUCKET_OF_SLIME.asRSCM()
                    strictCount = 1
                }
                delete {
                    from = pack
                    obj = BEDSHEET.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = BEDSHEET_SLIMED.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = BUCKET.asRSCM()
                    strictCount = 1
                }
            }
        if (dyed.failure) {
            return
        }
        mes("You pour the slime over the bedsheet. It is now a lovely shade of ghostly green.")
    }

    private suspend fun ProtectedAccess.dipInPool() {
        arriveDelay()
        if (invDel(inv, BEDSHEET).failure) {
            return
        }
        invAdd(inv, BEDSHEET_SLIMED)
        mes("You dip the bedsheet in the pool of slime. It is now a lovely shade of ghostly green.")
    }

    private companion object {
        const val SLIME_POOL = "loc.ahoy_new_green_floor"
    }
}
