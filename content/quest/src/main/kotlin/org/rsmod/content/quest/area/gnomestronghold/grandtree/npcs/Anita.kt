package org.rsmod.content.quest.area.gnomestronghold.grandtree.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.ANITA
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GLOUGHS_KEY
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_TWIGS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KEY_HINTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Anita, Glough's girlfriend, upstairs in the north-west of the stronghold by the tortoise pens. */
class Anita
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ANITA) { startDialogue(it.npc) { anita() } }
    }

    private suspend fun Dialogue.anita() {
        val stage = grandTree.stage(player)
        val keyNeeded = stage in STAGE_KEY_HINTED until STAGE_HAS_TWIGS && !grandTree.plansFound.get(player)
        when {
            player.inv.contains(GLOUGHS_KEY) -> {
                chatNpc(quiz, "Have you taken that key to Glough yet?")
                chatPlayer(neutral, "No, I'm still carrying it around.")
                chatNpc(neutral, "Oh. Please take it to Glough!")
            }
            keyNeeded -> giveKey()
            else -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(happy, "Oh hello, I've seen you with the King.")
                chatPlayer(happy, "Yes, I'm helping him with a problem.")
                chatNpc(happy, "Well, good luck with it. Do say hello to Glough if you see him.")
            }
        }
    }

    private suspend fun Dialogue.giveKey() {
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Oh hello, I've seen you with the King.")
        chatPlayer(happy, "Yes, I'm helping him with a problem.")
        chatNpc(quiz, "You must know my boyfriend Glough then?")
        chatPlayer(neutral, "Indeed!")
        chatNpc(quiz, "Could you do me a favour?")
        when (choice2("I suppose so.", 1, "No, I'm busy.", 2)) {
            1 -> {
                chatPlayer(neutral, "I suppose so.")
                chatNpc(happy, "Please give this key to Glough, he left it here last night.")
                access.invAddOrDrop(objRepo, GLOUGHS_KEY)
                objbox(GLOUGHS_KEY, "Anita gives you a key.")
                chatNpc(happy, "Thanks a lot.")
                chatPlayer(happy, "No... thank you!")
            }
            2 -> chatPlayer(neutral, "No, I'm busy.")
        }
    }
}
