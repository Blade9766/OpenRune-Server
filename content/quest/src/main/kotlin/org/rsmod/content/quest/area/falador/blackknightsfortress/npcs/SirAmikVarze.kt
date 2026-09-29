package org.rsmod.content.quest.area.falador.blackknightsfortress.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.DOSSIER
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.REQUIRED_QUEST_POINTS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.REWARD_COINS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.SIR_AMIK
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_OVERHEARD
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_SABOTAGED
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.falador.blackknightsfortress.dossierCountdown
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sir Amik Varze, leader of the White Knights. Starts and finishes Black Knights' Fortress. */
class SirAmikVarze @Inject constructor(private val bkf: BlackKnightsFortressQuest) : PluginScript() {

    private val quest
        get() = bkf.quest

    override fun ScriptContext.startup() {
        onOpNpc1(SIR_AMIK) { startDialogue(it.npc) { sirAmik() } }
    }

    private suspend fun Dialogue.sirAmik() {
        when (bkf.stage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> searching()
            STAGE_OVERHEARD -> foundTheWeapon()
            STAGE_SABOTAGED -> reward()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(quiz, "I am the leader of the White Knights of Falador. Why do you seek my audience?")
        when (choice2("I seek a quest!", 1, "I don't, I'm just looking around.", 2)) {
            1 -> {
                chatPlayer(neutral, "I seek a quest.")
                if (!bkf.hasQuestPoints(player)) {
                    chatNpc(neutral, "Well, I do have a task, but it is very dangerous and it's critical to us that no mistakes are made. I couldn't possibly let an inexperienced quester like yourself go.")
                    access.mes("You need at least $REQUIRED_QUEST_POINTS quest points before you may attempt this quest.")
                    return
                }
                offerQuest()
            }
            2 -> {
                chatPlayer(neutral, "I don't, I'm just looking around.")
                chatNpc(neutral, "Ok. Please don't break anything.")
            }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatNpc(neutral, "Well, I need some spy work doing but it's quite dangerous. It will involve going into the Black Knights' fortress.")
        when (
            choice2(
                "I laugh in the face of danger!", 1,
                "I go and cower in a corner at the first sign of danger!", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I laugh in the face of danger!")
                chatNpc(neutral, "Well that's good. Don't get too overconfident though.")
                briefing()
            }
            2 -> {
                chatPlayer(worried, "I go and cower in a corner at the first sign of danger!")
                chatNpc(shocked, "Err....")
                chatNpc(confused, "Well.")
                chatNpc(quiz, "I... suppose spy work DOES involve a little hiding in corners.")
                when (
                    choice2(
                        "Oh. I suppose I'll give it a go then.", 1,
                        "No, I'm not ready to do that.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(neutral, "Oh. I suppose I'll give it a go then.")
                        briefing()
                    }
                    2 -> declined()
                }
            }
        }
    }

    private suspend fun Dialogue.briefing() {
        chatNpc(neutral, "You've come along at just the right time actually. All of my knights are already known to the Black Knights.")
        chatNpc(sad, "Subtlety isn't exactly our strong point.")
        chatPlayer(quiz, "Can't you just take your White Knights' armour off? They wouldn't recognise you then!")
        chatNpc(neutral, "I am afraid our charter prevents us using espionage in any form, that is the domain of the Temple Knights.")
        chatPlayer(quiz, "Temple Knights? Who are they?")
        chatNpc(neutral, "That information is classified. I am forbidden to share it with outsiders.")
        chatPlayer(quiz, "So... what do you need doing?")
        chatNpc(angry, "Well, the Black Knights have started making strange threats to us; demanding large amounts of money and land, and threatening to invade Falador if we don't pay them.")
        chatNpc(neutral, "Now, NORMALLY this wouldn't be a problem...")
        chatNpc(angry, "But they claim to have a powerful new secret weapon.")
        chatNpc(neutral, "Your mission, should you decide to accept it, is to infiltrate their fortress, find out what their secret weapon is, and then sabotage it.")
        if (!startQuestPrompt(quest)) {
            declined()
            return
        }
        chatPlayer(happy, "Ok, I'll do my best.")
        bkf.advance(access)
        chatNpc(happy, "Good luck! Let me know how you get on. Here's the dossier for the case, I've already given you the details.")
        if (player.inv.isFull()) {
            chatNpc(confused, "Oh. You don't appear to have any room for the dossier in your inventory. Come back when you do.")
            return
        }
        access.invAdd(access.inv, DOSSIER)
    }

    private suspend fun Dialogue.declined() {
        chatPlayer(neutral, "No, I'm not ready to do that.")
        chatNpc(neutral, "Come see me again if you change your mind.")
    }

    private suspend fun Dialogue.searching() {
        chatNpc(quiz, "How's the mission going?")
        chatPlayer(sad, "I haven't managed to find what the secret weapon is yet...")
        chatNpc(neutral, "Well, keep at it! Falador's future is at stake!")
        if (player.inv.contains(DOSSIER)) {
            return
        }
        chatNpc(neutral, "Here's the dossier on the case.")
        if (player.inv.isFull()) {
            dossierCountdown()
            return
        }
        access.invAdd(access.inv, DOSSIER)
    }

    private suspend fun Dialogue.foundTheWeapon() {
        chatNpc(quiz, "How's the mission going?")
        chatPlayer(neutral, "I've found out what the Black Knight's secret weapon is. It's a potion of invincibility.")
        chatNpc(worried, "A potion of invincibility? That is grave news indeed. If you can sabotage it somehow, you will be very well paid.")
        chatPlayer(quiz, "So when I finish this mission for you can I be a white knight? Can I wear your armour too?")
        chatNpc(neutral, "I am afraid I cannot authorise you to become a white knight unless under a situation of dire circumstance. Assisting us on a freelance basis will be well worth your time however and you will have my personal gratitude.")
        chatPlayer(sad, "I can't buy stuff with personal gratitude though...")
        chatNpc(neutral, "There is of course also the financial recompense as I said.")
        chatPlayer(happy, "Ok! I'll get on to sabotaging that potion.")
    }

    private suspend fun Dialogue.reward() {
        chatPlayer(happy, "I have ruined the Black Knights' invincibility potion. That should put a stop to your problem and an end to their little schemes.")
        chatNpc(neutral, "Yes, we have just received a message from the Black Knights saying they withdraw their demands, which would seem to confirm your story.")
        chatPlayer(quiz, "Now I believe there was some talk of a cash reward...")
        chatNpc(happy, "Absolutely right. Please accept this reward.")
        mesbox("Sir Amik hands you ${"%,d".format(REWARD_COINS)} coins.")
        bkf.advance(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello Sir Amik.")
        chatNpc(happy, "Hello, friend!")
    }
}
