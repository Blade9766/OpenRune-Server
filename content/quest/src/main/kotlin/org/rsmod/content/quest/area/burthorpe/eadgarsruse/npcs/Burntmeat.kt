package org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.BURNTMEAT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.BURNT_MEAT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.FAKE_MAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.FAKE_MAN_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_ASKED_EADGAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COOK_FIRST
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COOK_FOOLED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_FAKE_MAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_MET_COOK
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Burntmeat, the troll chief cook in the Troll Stronghold kitchen. He wants a tasty human for his
 * stew, takes Eadgar's fake man for one and lets slip where the storeroom key is hidden.
 */
class Burntmeat
@Inject
constructor(
    private val eadgarsRuse: EadgarsRuseQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BURNTMEAT) { startDialogue(it.npc) { burntmeat() } }
    }

    private suspend fun Dialogue.burntmeat() {
        val stage = eadgarsRuse.stage(player)
        when {
            stage == STAGE_STARTED -> {
                introduction()
                wantsHuman()
                eadgarsRuse.advanceTo(access, STAGE_COOK_FIRST)
            }
            stage == STAGE_ASKED_EADGAR -> {
                introduction()
                wantsHuman()
                eadgarsRuse.advanceTo(access, STAGE_MET_COOK)
            }
            stage == STAGE_FAKE_MAN && FAKE_MAN in player.inv -> fooled()
            stage in STAGE_COOK_FIRST until STAGE_COOK_FOOLED -> {
                chatNpc(quiz, "Did you find tasty human?")
                chatPlayer(worried, "Erm, not yet, but I'm working on it...")
            }
            stage >= STAGE_COOK_FOOLED && !eadgarsRuse.isComplete(player) -> {
                chatPlayer(quiz, "How's the stew?")
                chatNpc(happy, "Slurp, mmm... Human stew cheer Burntmeat up!")
                afterStew()
            }
            else -> {
                introduction()
                chatPlayer(neutral, "Nothing. I was just leaving.")
            }
        }
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(neutral, "Er, hi.")
        chatNpc(neutral, "Hmm? What human do in troll kitchen? Burntmeat tired of cooking goats. Human look tasty.")
        chatPlayer(
            worried,
            "Oh, you don't want to eat me! I'm a tough, hardened adventurer, not tender or tasty at all!",
        )
        chatNpc(neutral, "Hmm. Burntmeat think you probably right. What human doing here?")
    }

    private suspend fun Dialogue.wantsHuman() {
        chatPlayer(neutral, "I'm on a quest to find some goutweed.")
        chatNpc(laugh, "Bwahahaha! Burntmeat not give his greatest cooking secret away so easily.")
        chatNpc(happy, "But Burntmeat also has quest for human!")
        chatPlayer(quiz, "Really? What is it?")
        chatNpc(
            happy,
            "Bring back a tasty human for Burntmeat's stew. If you find tasty human, Burntmeat will " +
                "give you good reward.",
        )
        chatPlayer(worried, "Right. I'll just...go fetch that for you then. Bye!")
    }

    private suspend fun Dialogue.fooled() {
        chatNpc(happy, "Did you find tasty human? Burntmeat smell something good.")
        chatPlayer(happy, "Yes! Look!")
        fakeMan("Heeeeeeelp!")
        chatNpc(happy, "Ah, dat look like nice tasty human.")
        fakeMan("Aaaargh! Somebody save me!")
        chatNpc(happy, "Yep, sound like human too. Burntmeat put it in stew. Good work, human. Burntmeat give precious reward.")
        access.invDel(access.inv, FAKE_MAN)
        access.invAddOrDrop(objRepo, BURNT_MEAT)
        eadgarsRuse.advanceTo(access, STAGE_COOK_FOOLED)
        chatPlayer(confused, "This is burnt meat.")
        chatNpc(happy, "It first thing I ever try to cook! Very precious to Burntmeat.")
        chatPlayer(neutral, "Thank you... and how's the stew?")
        chatNpc(happy, "Slurp, mmm... Human stew cheer Burntmeat up!")
        afterStew()
    }

    private suspend fun Dialogue.afterStew() {
        val ask = choice2("So, where can I get some goutweed?", true, "I'll be going now.", false)
        if (!ask) {
            chatPlayer(neutral, "I'll be going now.")
            chatNpc(happy, "Bye, bye.")
            return
        }
        chatPlayer(quiz, "So, where can I get some goutweed?")
        chatNpc(
            neutral,
            "Hah! Trolls pick it all until none left, many years ago. Only remaining stock in storeroom.",
        )
        chatNpc(
            neutral,
            "It well guarded, and Burntmeat hide key in fake bottom of kitchen drawer. Nobody find it there.",
        )
        chatPlayer(happy, "That's some well-guarded secret alright. I'll just be off on my way now.")
    }

    private suspend fun Dialogue.fakeMan(text: String) = chatNpcSpecific("Fake man", FAKE_MAN_NPC, shocked, text)
}
