package org.rsmod.content.quest.area.tirannwn.rovingelves.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.tirannwn.mourningsend.ElunedErrands
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ELUNED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ELUNED_ENCHANT
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ELUNED_TALK
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.NEW_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.OLD_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_ACCEPTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_GET_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANT_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.guardianSlain
import org.rsmod.content.quest.area.tirannwn.rovingelves.ownsAnywhere
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Eluned, who travels with Islwyn. She explains the consecration, enchants the seed the player
 * wins from the Moss Guardian, and replaces the enchanted seed if it is lost before planting. The
 * guardian must have fallen to the player themselves: a seed picked up from someone else's kill
 * is not enchanted. Once Roving Elves is over she starts Mourning's End Part I and re-enchants
 * spent teleport crystals (see [ElunedErrands]).
 */
class Eluned
@Inject
constructor(private val roving: RovingElvesQuest, private val errands: ElunedErrands) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(ELUNED, ELUNED_TALK, ELUNED_ENCHANT)) {
            onOpNpc1(type) { startDialogue(it.npc) { eluned() } }
        }
        onOpNpc3(ELUNED_ENCHANT) { startDialogue(it.npc) { with(errands) { enchant() } } }
    }

    private suspend fun Dialogue.eluned() {
        val stage = roving.stage(player)
        when {
            roving.isComplete(player) -> afterQuest()
            stage == 0 && QuestRequirements.hasCompleted(player, RovingElvesQuest.QUEST_KEY) -> afterQuest()
            stage < STAGE_ACCEPTED -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(sad, "Hello. You will have to excuse Islwyn and me; we have had troubling news from the river. Speak to him if you wish to know more.")
            }
            stage == STAGE_ACCEPTED -> instructions()
            stage == STAGE_GET_SEED -> seedQuest()
            stage == STAGE_PLANT_SEED -> planting()
            stage == STAGE_PLANTED -> {
                chatNpc(happy, "I felt the song of the seed from here. Go to Islwyn; he will want to thank you himself.")
            }
        }
    }

    private suspend fun Dialogue.instructions() {
        chatPlayer(neutral, "Islwyn said you could tell me how to put things right for Glarial.")
        chatNpc(neutral, "So you are the one who moved her. Then it is fitting that you finish what you started.")
        chatNpc(neutral, "When one of our people is laid to rest, the ground is consecrated with a seed sung for that purpose. Glarial's seed was left behind in her old tomb, and her new resting place has none.")
        chatPlayer(quiz, "So I need to fetch the seed?")
        chatNpc(neutral, "Yes. The tomb's guardian holds it: a moss giant changed by the old magic of that place. It will not give up the seed without a fight.")
        chatNpc(worried, "Remember that the tomb only opens for those who come in peace. You may bring no weapons, no armour and no runes, so you must face the guardian with your bare hands. Food and potions are permitted.")
        chatPlayer(quiz, "And once I have it?")
        chatNpc(neutral, "Bring it to me and I will enchant it. Then you must plant it beside the chalice where Glarial now lies, inside the waterfall.")
        roving.advanceTo(access, STAGE_GET_SEED)
        chatNpc(neutral, "Glarial's pebble opens the tomb. If you no longer have it, the gnome who kept it beneath the Tree Gnome Village may have another.")
    }

    private suspend fun Dialogue.seedQuest() {
        val holding = OLD_SEED in player.inv
        when {
            holding && player.guardianSlain == 1 -> enchant()
            holding -> {
                chatNpc(worried, "That seed was not won by your own hand. The guardian must fall to you, or the rite will mean nothing.")
            }
            access.ownsAnywhere(OLD_SEED) -> {
                chatNpc(neutral, "Bring the seed with you and I will enchant it.")
            }
            else -> {
                chatNpc(neutral, "Have you the consecration seed? The guardian in Glarial's old tomb, near Baxtorian Falls, holds it.")
                val lostPebble = menu("I'm on my way." to false, "How do I get into the tomb again?" to true)
                if (lostPebble) {
                    chatNpc(neutral, "Use Glarial's pebble on her tombstone, and leave your weapons, armour and runes behind. If you have lost the pebble, the gnome Golrie beneath the Tree Gnome Village may find you another.")
                }
            }
        }
    }

    /** The swap is one transaction and the stage only moves once it has gone through. */
    private suspend fun Dialogue.enchant() {
        chatPlayer(happy, "I have the seed from Glarial's tomb.")
        chatNpc(happy, "Then give it here, and be quiet a moment.")
        if (access.invReplace(access.inv, OLD_SEED, 1, NEW_SEED).failure) {
            return
        }
        roving.advanceTo(access, STAGE_PLANT_SEED)
        objbox(NEW_SEED, 400, "Eluned holds the seed in her cupped hands and sings softly. The grey seed begins to glow with a warm light.")
        plantingInstructions()
    }

    private suspend fun Dialogue.planting() {
        if (access.ownsAnywhere(NEW_SEED)) {
            chatNpc(neutral, "Have you planted the seed yet?")
            plantingInstructions()
            return
        }
        chatPlayer(worried, "I've lost the seed you enchanted.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "I can sing you another, but you will need a free space to carry it.")
            return
        }
        chatNpc(neutral, "Then it is lucky that Glarial's song lingers. Hold out your hand.")
        if (access.invAdd(access.inv, NEW_SEED).failure) {
            return
        }
        objbox(NEW_SEED, 400, "Eluned gives you another enchanted consecration seed.")
    }

    private suspend fun Dialogue.plantingInstructions() {
        chatNpc(neutral, "Take the seed into the caves behind Baxtorian Falls. A key in the crates of the eastern room opens the way west to the chamber where the chalice stands.")
        chatNpc(neutral, "Plant it beside the chalice. You will need a spade to dig, and a rope to get down the falls.")
    }

    private suspend fun Dialogue.afterQuest() {
        if (errands.offersQuest(player)) {
            with(errands) { offerQuest() }
            return
        }
        if (errands.offersEnchant(player)) {
            chatPlayer(neutral, "Hello Eluned.")
            chatNpc(quiz, "Hello, friend. Do you need me to re-enchant your teleport crystal?")
            if (choice2("Yes please.", true, "No thanks.", false)) {
                with(errands) { enchant() }
            }
            return
        }
        chatPlayer(neutral, "Hello Eluned.")
        chatNpc(happy, "Hello, friend. Glarial's tree grows strong, and Islwyn sleeps easier for it.")
        chatNpc(neutral, "Our people have troubles of their own in this land. Perhaps one day we will ask for your help again.")
    }
}
