package org.rsmod.content.quest.area.burthorpe.eadgarsruse

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.ALCO_CHUNKS
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PETE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PINEAPPLE_CHUNKS
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_WANTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.VODKA
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The parrot aviary in Ardougne Zoo. Parroty Pete lets slip that the parrots love pineapple chunks
 * and were once given vodka; once both are known, vodka on the chunks makes alco-chunks, which
 * catch a parrot through the aviary hatch. A dropped drunk parrot flies off.
 */
class ArdougneAviary @Inject constructor(private val eadgarsRuse: EadgarsRuseQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(PETE) { startDialogue(it.npc) { pete() } }
        onOpHeldU(VODKA, PINEAPPLE_CHUNKS) { mixAlcoChunks() }
        onOpLocU(HATCH, ALCO_CHUNKS) { catchParrot() }
        onOpLocU(HATCH, PINEAPPLE_CHUNKS) { offerChunks() }
        onOpLocU(HATCH, VODKA) {
            arriveDelay()
            startDialogue { chatPlayer(neutral, "They won't drink it straight from the bottle...") }
        }
        onOpHeld5(PARROT) { releaseParrot(it.slot) }
    }

    private suspend fun Dialogue.pete() {
        chatNpc(happy, "Good day, good day. Come to admire the new parrot aviary have we?")
        when (
            choice3(
                "It's very nice.", 1,
                "When did you add it?", 2,
                "What do you feed them?", 3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "It's very nice.")
                chatNpc(happy, "Isn't it just?")
            }
            2 -> {
                chatPlayer(quiz, "When did you add it?")
                chatNpc(
                    neutral,
                    "Just recently. It would have been sooner, but some wretch thought it would be " +
                        "amusing to replace their drinking water with vodka. The vet had to nurse " +
                        "them back to health for weeks!",
                )
                if (eadgarsRuse.stage(player) >= STAGE_STARTED) player.erAskedPeteWater = true
            }
            else -> {
                chatPlayer(quiz, "What do you feed them?")
                chatNpc(
                    neutral,
                    "Well, fruit and grain mostly. I try to give them a balanced diet, but their " +
                        "favourite treat is pineapple chunks.",
                )
                if (eadgarsRuse.stage(player) >= STAGE_STARTED) player.erAskedPeteFood = true
            }
        }
    }

    private suspend fun ProtectedAccess.mixAlcoChunks() {
        if (!player.erAskedPeteWater || !player.erAskedPeteFood) {
            mes("Why would you want to do that?")
            return
        }
        invDel(inv, VODKA)
        invDel(inv, PINEAPPLE_CHUNKS)
        invAdd(inv, ALCO_CHUNKS)
        mes("You soak the pineapple chunks in the vodka.")
    }

    private fun wantsParrot(access: ProtectedAccess): Boolean =
        eadgarsRuse.stage(access.player) == STAGE_PARROT_WANTED

    private suspend fun ProtectedAccess.catchParrot() {
        arriveDelay()
        val stage = eadgarsRuse.stage(player)
        if (stage > STAGE_PARROT_WANTED && !eadgarsRuse.isComplete(player)) {
            startDialogue {
                chatPlayer(confused, "The parrott flew off, I wonder where it went? Silly thing prefered Eadgar to me!")
            }
            mes("The parrott flew off, you need to find it again.")
            return
        }
        if (!wantsParrot(this) || ownsAnywhere(PARROT)) {
            mes("Nothing interesting happens.")
            return
        }
        invDel(inv, ALCO_CHUNKS)
        invAdd(inv, PARROT)
        mesbox("You manage to attract a parrot and catch it.")
        startDialogue {
            chatPlayer(happy, "Hah! Got you now!")
            chatNpcSpecific("Parrot", PARROT_NPC, drunk, "Sqwaawk...*hic*")
            chatNpcSpecific("Parroty Pete", PETE, angry, "Hey! What are you doing with that parrot?")
            chatPlayer(shifty, "Nothing.")
            chatNpcSpecific("Parroty Pete", PETE, angry, "It looks drunk! You should NEVER feed alcohol to a parrot!")
            chatPlayer(happy, "Well, good thing I found it! I'll just take it to the vet for you, shall I?")
            chatNpcSpecific("Parroty Pete", PETE, happy, "Oh, thank you! We're ever so busy here at the Zoo.")
        }
    }

    private suspend fun ProtectedAccess.offerChunks() {
        arriveDelay()
        if (!wantsParrot(this)) {
            mes("Nothing interesting happens.")
            return
        }
        invDel(inv, PINEAPPLE_CHUNKS)
        mesbox("You manage to attract a parrot, but it flies away when you try to catch it.")
        startDialogue { chatPlayer(worried, "I need some way to slow it down...") }
    }

    private fun ProtectedAccess.releaseParrot(slot: Int) {
        invDel(inv, PARROT, slot = slot)
        mes("The parrot flies away.")
    }

    private fun ProtectedAccess.ownsAnywhere(obj: String): Boolean = player.ownsAnywhere(obj)

    private companion object {
        const val HATCH = "loc.eadgar_aviary_wall_hatch"
    }
}
