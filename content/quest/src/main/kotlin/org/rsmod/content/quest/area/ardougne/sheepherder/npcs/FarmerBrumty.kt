package org.rsmod.content.quest.area.ardougne.sheepherder.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepColour
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.BRUMTY
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CATTLEPROD
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepState
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Farmer Brumty, beside his enclosure. Explains how to herd, hands out a spare cattleprod, and
 * keeps the remains of any sheep the player has fed but whose bones have since gone missing.
 */
class FarmerBrumty @Inject constructor(private val sheep: SheepHerderQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BRUMTY) { startDialogue(it.npc) { brumty() } }
    }

    private suspend fun Dialogue.brumty() {
        val stage = sheep.stage(player)
        if (stage == 0) {
            chatNpc(sad, "Four of my sheep have gone all colours, and the council says it's the plague. I don't know what I'll do if it spreads to the rest of the flock.")
            return
        }
        if (stage > STAGE_STARTED) {
            chatNpc(happy, "Thanks to you, the rest of my flock's as healthy as can be. I owe you one.")
            return
        }
        chatNpc(worried, "You're the one the council sent? Thank goodness. Those poor beasts can't stay out there.")
        while (true) {
            val options = buildList {
                add("How do I get the sheep into the enclosure?" to Topic.HERDING)
                if (!ownsProd()) add("I need a cattleprod." to Topic.PROD)
                if (lostBones().isNotEmpty()) add("I've lost some sheep bones." to Topic.BONES)
                add("I'd better get on with it." to Topic.LEAVE)
            }
            when (menu(options)) {
                Topic.HERDING -> herding()
                Topic.PROD -> prod()
                Topic.BONES -> bones()
                Topic.LEAVE -> {
                    chatPlayer(neutral, "I'd better get on with it.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.herding() {
        chatPlayer(quiz, "How do I get the sheep into the enclosure?")
        chatNpc(neutral, "Wield a cattleprod and give 'em a poke. A sheep runs straight away from whoever prods it: stand south of one and it goes north.")
        chatNpc(neutral, "It'll stop at fences, rocks and trees, so walk round and prod it from a new side to turn it. Drive it to the gate on the west side of the enclosure.")
        chatNpc(neutral, "The red ones are south of here, the green ones east, the blue ones well to the north-west and the yellow ones north. One of each, mind.")
        chatNpc(worried, "Don't leave a sheep standing about. It'll bleat a bit, then wander off home and you'll have to start over.")
    }

    private suspend fun Dialogue.prod() {
        chatPlayer(neutral, "I need a cattleprod.")
        if (access.inv.freeSpace() == 0) {
            chatNpc(neutral, "I've a spare, but your hands are full. Make some room.")
            return
        }
        access.invAdd(access.inv, CATTLEPROD, 1)
        objbox(CATTLEPROD, zoom = 400, "Farmer Brumty hands you a spare cattleprod.")
        chatNpc(neutral, "Wield it before you go poking sheep with it.")
    }

    private suspend fun Dialogue.bones() {
        val lost = lostBones()
        chatPlayer(sad, "I've lost some sheep bones.")
        if (access.inv.freeSpace() < lost.size) {
            chatNpc(neutral, "I picked up what you left lying about, but you've not got room for ${if (lost.size == 1) "them" else "all of them"}.")
            return
        }
        chatNpc(neutral, "I gathered up what you left lying around. I wasn't going to touch them without gloves, mind.")
        for (colour in lost) {
            access.invAdd(access.inv, colour.bones, 1)
        }
        mes("Farmer Brumty gives you the ${lost.joinToString { it.label }} sheep's bones.")
    }

    private fun Dialogue.ownsProd(): Boolean =
        CATTLEPROD in access.inv || CATTLEPROD in access.worn || CATTLEPROD in access.bank

    /** Colours fed but not yet burned whose bones are nowhere on the player or in the bank. */
    private fun Dialogue.lostBones(): List<SheepColour> =
        SheepColour.entries.filter {
            sheep.state(player, it) == SheepState.BONES && it.bones !in access.inv && it.bones !in access.bank
        }

    private fun Dialogue.mes(text: String) = access.mes(text)

    private enum class Topic {
        HERDING,
        PROD,
        BONES,
        LEAVE,
    }
}
