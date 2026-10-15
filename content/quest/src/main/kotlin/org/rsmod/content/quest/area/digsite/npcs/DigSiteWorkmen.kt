package org.rsmod.content.quest.area.digsite.npcs

import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.DOUG_DEEPING
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.INVITATION
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.INVITATION_VARBIT
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.WORKMEN
import org.rsmod.content.quest.area.digsite.carriesOrBanks
import org.rsmod.content.quest.area.digsite.setVarBit
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Digsite workmen, and Doug Deeping at the bottom of the west shaft.
 *
 * Any of them will take Terry's invitation letter and let the player into the dig shafts. Doug is
 * one of them - the same npc type with a name - and is the one who remembers the chemist that used
 * to work the other shaft, along with the chest key he left behind.
 */
class DigSiteWorkmen : PluginScript() {

    override fun ScriptContext.startup() {
        for (workman in WORKMEN) {
            onOpNpcU(workman) { useOnWorkman(it.npc, it.objType.internalName) }
        }
        onOpNpc1(DOUG_DEEPING) { startDialogue(it.npc) { doug() } }
        for (workman in WORKMEN - DOUG_DEEPING) {
            onOpNpc1(workman) { startDialogue(it.npc) { workman() } }
        }
    }

    private suspend fun ProtectedAccess.useOnWorkman(npc: Npc, obj: String) {
        if (obj != INVITATION) {
            mes("Nothing interesting happens.")
            return
        }
        showInvitation(npc)
    }

    private suspend fun ProtectedAccess.showInvitation(npc: Npc) {
        startDialogue(npc) {
            if (access.player.vars[INVITATION_VARBIT] != 0) {
                chatNpc(quiz, "What on earth are you giving me another scroll for?")
                return@startDialogue
            }
            chatPlayer(neutral, "Here, have a look at this...")
            chatNpc(
                neutral,
                "I give permission... blah de blah... etc. Okay, that's all in order; you may use " +
                    "the mineshaft now. I'll hang onto this scroll, shall I?",
            )
            access.invDel(access.inv, INVITATION)
            setVarBit(access.player, INVITATION_VARBIT, 1)
        }
    }

    private suspend fun Dialogue.workman() {
        chatPlayer(neutral, "Hello there.")
        if (access.player.vars[INVITATION_VARBIT] != 0) {
            chatNpc(happy, "Back down the shaft, are you? Mind your head on the way.")
            return
        }
        chatNpc(
            bored,
            "Can't stop, there's earth to shift. If you want down the shafts you'll need a note " +
                "from the archaeological expert.",
        )
    }

    private suspend fun Dialogue.doug() {
        chatPlayer(neutral, "Hello.")
        chatNpc(quiz, "Well, well... I have a visitor. What are you doing here?")
        val choice =
            choice4(
                "I have been invited to research here.",
                1,
                "I am not sure really.",
                2,
                "I'm here to get rich, rich, rich!",
                3,
                "How could I move a large pile of rocks?",
                4,
            )
        when (choice) {
            1 -> invitedHere()
            2 -> {
                chatPlayer(neutral, "I am not sure really.")
                chatNpc(laugh, "A miner without a clue - how funny!")
            }
            3 -> {
                chatPlayer(happy, "I'm here to get rich, rich, rich!")
                chatNpc(neutral, "Oh, well, don't forget that wealth and riches aren't everything.")
            }
            else -> aboutTheRocks()
        }
    }

    private suspend fun Dialogue.invitedHere() {
        chatPlayer(neutral, "I have been invited to research here.")
        chatNpc(happy, "Indeed, you must be someone special to be allowed down here.")
        val choice =
            choice2(
                "Do you know where to find a specimen jar?",
                true,
                "I have things to do...",
                false,
            )
        if (!choice) {
            chatPlayer(neutral, "I have things to do...")
            chatNpc(neutral, "Of course, don't let me keep you.")
            return
        }
        chatPlayer(quiz, "Do you know where to find a specimen jar?")
        chatNpc(neutral, "Hmmm, let me think... Nope, can't help you there I'm afraid.")
    }

    private suspend fun Dialogue.aboutTheRocks() {
        chatPlayer(quiz, "How do you move a large pile of rocks?")
        chatNpc(
            neutral,
            "There used to be this chap that worked in the other shaft. He was working on an " +
                "explosive chemical mixture to be used for clearing blocked areas underground.",
        )
        chatNpc(
            worried,
            "He left in a hurry one day; something in the shaft scared him to death, but he " +
                "didn't say what.",
        )
        chatPlayer(quiz, "Oh?")
        chatNpc(
            neutral,
            "Rumour has it he'd been writing a book on his chemical mixture. I'm not sure what " +
                "goes in it but I'm sure you'll find the stuff he was using scattered around the " +
                "digsite. He left so quickly he didn't take anything with",
        )
        chatNpc(
            neutral,
            "him. In fact, I still have a chest key he gave me to look after; perhaps it's more " +
                "useful to you.",
        )
        if (access.carriesOrBanks(CHEST_KEY)) {
            chatPlayer(neutral, "It's ok, I already have one.")
            return
        }
        access.invAdd(access.inv, CHEST_KEY)
        objbox(CHEST_KEY, "Doug hands you a key.")
    }
}
