package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_BRIEFED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_DOOR
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_PAPERS
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_REPORTED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.ID_PAPERS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val GRUBOR = "npc.grubor"
private const val TROBERT = "npc.trobert"

/**
 * The Black Arm Gang's Brimhaven hideout on Palm Street: Grubor, who minds the door, and Trobert,
 * who runs the place and hands out Hartigen's stolen identity papers.
 */
class BlackArmBrimhaven @Inject constructor(private val heroes: HeroesQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(GRUBOR) {
            startDialogue(it.npc) {
                chatPlayer(neutral, "Hi.")
                chatNpc(neutral, "Hi. I'm a little busy right now.")
            }
        }
        onOpNpc1(TROBERT) { startDialogue(it.npc) { trobert() } }
    }

    private suspend fun Dialogue.trobert() {
        val hasPapers = heroes.owns(access, ID_PAPERS)
        val stage = heroes.stage(player)
        if (heroes.isBlackArm(player) && stage in BLACKARM_PAPERS until BLACKARM_REPORTED && !hasPapers) {
            chatPlayer(sad, "I have lost Hartigen's ID papers...")
            chatNpc(
                neutral,
                "Well that was careless of you, wasn't it? Fortunately for you, he had a spare " +
                    "set. Take this one, but please try to be more careful with it.",
            )
            access.invAdd(access.inv, ID_PAPERS)
            return
        }
        if (hasPapers || heroes.blackArmAt(player, BLACKARM_PAPERS)) {
            chatNpc(quiz, "How's it going?")
            chatPlayer(neutral, "Fine, thanks.")
            return
        }
        chatNpc(
            neutral,
            "Hi. Welcome to our Brimhaven headquarters. I'm Trobert and I'm in charge here.",
        )
        val onQuest = heroes.isBlackArm(player) && stage == BLACKARM_DOOR
        val help =
            if (onQuest) {
                choice2(
                    "So can you help me get Scarface Pete's candlesticks?",
                    true,
                    "Pleased to meet you.",
                    false,
                )
            } else {
                false
            }
        if (!help) {
            chatPlayer(neutral, "Pleased to meet you.")
            chatNpc(neutral, "Likewise.")
            return
        }
        chatPlayer(quiz, "So can you help me get Scarface Pete's candlesticks?")
        chatNpc(
            neutral,
            "Well, we have made some progress there. We know that one of the only keys to Pete's " +
                "treasure room is carried by Grip, the head guard, so we thought it might be good " +
                "to get close to him somehow.",
        )
        chatNpc(
            neutral,
            "Grip was taking on a new deputy called Hartigen, an Asgarnian Black Knight who was " +
                "deserting the Black Knight Fortress and seeking new employment here on " +
                "Brimhaven.",
        )
        chatNpc(
            neutral,
            "We managed to waylay him on the journey here, and steal his I.D. papers. Now all we " +
                "need is to find somebody willing to impersonate him and take the deputy role to " +
                "get that key for us.",
        )
        val volunteer =
            choice2("I volunteer to undertake that mission!", true, "Well, good luck then.", false)
        if (!volunteer) {
            chatPlayer(neutral, "Well, good luck then.")
            chatNpc(neutral, "Someone will show up eventually.")
            return
        }
        chatPlayer(happy, "I volunteer to undertake that mission!")
        access.invAdd(access.inv, ID_PAPERS)
        heroes.setStage(access, BLACKARM_PAPERS)
        chatNpc(
            happy,
            "Good good. Here's the I.D. papers. Take them and introduce yourself to the guards at " +
                "Scarface Pete's mansion. We'll have that treasure in no time.",
        )
    }
}

/**
 * Grubor answering the hideout door. Only a Black Arm player Katrine has briefed is asked for the
 * password; anyone else gets the brush-off.
 */
internal suspend fun Dialogue.gruborAtDoor(heroes: HeroesQuest) {
    chatNpc(quiz, "Yes? What do you want?")
    val briefed = heroes.isBlackArm(player) && heroes.stage(player) == BLACKARM_BRIEFED
    if (!briefed) {
        when (choice3("Would you like your hedges trimming?", 1, "I want to come in.", 2, "Do you want to trade?", 3)) {
            1 -> {
                chatPlayer(quiz, "Would you like your hedges trimming?")
                chatNpc(confused, "Eh? Don't be daft! We don't even HAVE any hedges!")
            }
            2 -> {
                chatPlayer(angry, "I want to come in.")
                chatNpc(neutral, "No, go away.")
            }
            else -> {
                chatPlayer(quiz, "Do you want to trade?")
                chatNpc(neutral, "No, I'm busy.")
            }
        }
        return
    }
    val password =
        choice4("Rabbit's foot.", 1, "Four leaved clover.", 2, "Lucky horseshoe.", 3, "Black cat.", 4)
    when (password) {
        2 -> {
            chatPlayer(neutral, "Four leaved clover.")
            chatNpc(
                neutral,
                "Oh, you're one of the gang are you? Ok, hold up a second, I'll just let you in " +
                    "through here.",
            )
            heroes.setStage(access, BLACKARM_DOOR)
            mesbox("You hear the door being unbarred from inside.")
            return
        }
        1 -> chatPlayer(neutral, "Rabbit's foot.")
        3 -> chatPlayer(neutral, "Lucky horseshoe.")
        else -> chatPlayer(neutral, "Black cat.")
    }
    chatNpc(angry, "Eh? What are you on about? Go away!")
}
