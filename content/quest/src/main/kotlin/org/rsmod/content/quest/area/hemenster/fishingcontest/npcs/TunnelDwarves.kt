package org.rsmod.content.quest.area.hemenster.fishingcontest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.AUSTRI
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.FISHING_LEVEL
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.PASS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_TROPHY
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.TROPHY
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.VESTRI
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Austri and Vestri, who keep the east and west ends of the dwarven tunnel under White Wolf
 * Mountain. Either brother starts the quest, replaces a lost pass and takes the trophy; they share
 * one quest stage, so it makes no difference which end the player turns up at.
 */
class TunnelDwarves @Inject constructor(private val fc: FishingContestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(AUSTRI) { startDialogue(it.npc) { dwarf(brother = "Vestri", end = "west") } }
        onOpNpc1(VESTRI) { startDialogue(it.npc) { dwarf(brother = "Austri", end = "east") } }
    }

    private suspend fun Dialogue.dwarf(brother: String, end: String) {
        when (fc.stage(player)) {
            0 -> beforeQuest(brother)
            in STAGE_STARTED until STAGE_TROPHY -> duringContest()
            STAGE_TROPHY -> trophy()
            else -> afterQuest(brother, end)
        }
    }

    private suspend fun Dialogue.beforeQuest(brother: String) {
        chatNpc(neutral, "Halt! Who goes there?")
        chatPlayer(quiz, "Just a traveller. Where does this staircase lead?")
        chatNpc(neutral, "Down into the mountain. It's a tunnel right under White Wolf Mountain, dug by my clan. Saves a long, cold climb.")
        chatPlayer(happy, "Perfect! Mind if I use it?")
        chatNpc(angry, "Mind? I'm practically made of minding. The tunnel is for friends of the dwarves, and you are not one.")
        val choice = choice2("How do I become a friend of the dwarves?", true, "Never mind, I'll climb.", false)
        if (!choice) {
            chatPlayer(neutral, "Never mind. I'll take the scenic route.")
            chatNpc(laugh, "Mind the wolves. They're very friendly. Too friendly.")
            return
        }
        chatPlayer(quiz, "How does someone become a friend of the dwarves?")
        chatNpc(neutral, "Well, that's a good question. Me and my brother $brother have been wanting the trophy from the Hemenster fishing competition for years.")
        chatNpc(sad, "Our clan's never won it. Dwarves and boats don't mix, and the humans won't let us fish off the jetty in armour.")
        chatNpc(happy, "Win that trophy and bring it here, and you'd be the best friend a dwarf ever had.")
        if (!fc.meetsFishingLevel(player)) {
            chatPlayer(neutral, "I'll give it a go!")
            chatNpc(confused, "Hmm. No offence, but you hold a rod like it owes you money. Get your Fishing up to level $FISHING_LEVEL and come back.")
            access.mes("You need a Fishing level of $FISHING_LEVEL to take part in the competition.")
            return
        }
        if (!startQuestPrompt(fc.quest)) {
            chatPlayer(neutral, "I'm not much of a fisherman, really.")
            chatNpc(neutral, "Suit yourself. The offer stands, and so do I.")
            return
        }
        chatPlayer(happy, "Leave it to me. One trophy, coming up.")
        fc.advanceTo(access, STAGE_STARTED)
        chatNpc(happy, "That's the spirit! The competition is held in Hemenster, just south of McGrubor's Wood.")
        givePass(first = true)
    }

    private suspend fun Dialogue.givePass(first: Boolean) {
        if (access.invAdd(access.inv, PASS, 1).failure) {
            chatNpc(confused, "I'd give you a fishing pass to get in, but you've no room for it. Clear a space and come back.")
            return
        }
        objbox(PASS, zoom = 400, "The dwarf hands you a fishing competition pass.")
        if (first) {
            chatNpc(neutral, "Show that to the fellow on the gate. Only members of the fishing club and pass holders get in, and passes are hard to come by.")
            chatNpc(happy, "Good luck. And don't come back smelling of fish unless you're carrying a trophy.")
        } else {
            chatNpc(neutral, "There. Try not to lose this one, they're not cheap.")
        }
    }

    private suspend fun Dialogue.duringContest() {
        chatNpc(quiz, "Back already? Have you won the trophy?")
        if (PASS !in access.inv && PASS !in access.bank && !fc.hasShownPass(player)) {
            chatPlayer(sad, "Not yet. I've lost the fishing pass you gave me.")
            givePass(first = false)
            return
        }
        val choice = choice2("Not yet. I'm working on it.", false, "I've lost my fishing pass.", true)
        if (choice) {
            chatPlayer(sad, "I've lost my fishing pass.")
            if (PASS in access.inv) {
                chatNpc(confused, "You mean the one in your hand? Perhaps hold on to it a bit more loosely.")
                return
            }
            givePass(first = false)
            return
        }
        chatPlayer(neutral, "Not yet. I'm working on it.")
        chatNpc(neutral, "Remember: Hemenster, south of McGrubor's Wood. The old fisherman who lives by the contest, Grandpa Jack, knows a trick or two.")
    }

    private suspend fun Dialogue.trophy() {
        chatNpc(quiz, "Back already? Have you won the trophy?")
        if (TROPHY !in access.inv) {
            chatPlayer(happy, "I won! But I don't have the trophy with me.")
            chatNpc(shocked, "You won it and then left it somewhere? Go and fetch it! Bonzo might know where it's got to.")
            return
        }
        chatPlayer(happy, "I won the fishing competition! Here's the trophy.")
        if (access.invDel(access.inv, TROPHY, 1).failure) {
            return
        }
        objbox(TROPHY, zoom = 400, "You hand over the fishing trophy.")
        chatNpc(happy, "Would you look at that! A real Hemenster fishing trophy, in dwarven hands at last!")
        chatNpc(laugh, "My clan will be talking about this for generations. Mostly to the humans. Loudly.")
        chatNpc(happy, "You're a true friend of the dwarves. Use the tunnel whenever you like; I'll tell my brother at the other end.")
        fc.advanceTo(access, STAGE_COMPLETE)
    }

    private suspend fun Dialogue.afterQuest(brother: String, end: String) {
        chatNpc(happy, "Welcome back, friend! The tunnel's yours to use. $brother will see you out at the $end end.")
        chatNpc(laugh, "The trophy's on the mantelpiece, by the way. We polish it twice a day.")
    }
}
