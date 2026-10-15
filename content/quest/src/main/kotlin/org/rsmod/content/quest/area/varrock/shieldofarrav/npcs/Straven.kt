package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.stravenArmband
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.INTEL_REPORT
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_JOINED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TASKED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TOLD_BY_BARAEK
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.WEAPON_STORE_KEY
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.stravenShieldChat
import org.rsmod.content.quest.area.varrock.shieldofarrav.stravenTrampChat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val STRAVEN = "npc.straven"

/**
 * Straven, one of the Phoenix Gang's leaders, who keeps the hideout door under the VTAM
 * Corporation building. He sets the Jonny the Beard task, swaps the intelligence report for gang
 * membership and the weapon store key, and replaces the key if it is lost. For the Heroes' Quest
 * he sends members after Scarface Pete's candlesticks and pays out the Master Thief armband.
 */
class Straven
@Inject
constructor(private val arrav: ShieldOfArravQuest, private val heroes: HeroesQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(STRAVEN) {
            startDialogue(it.npc) {
                if (!stravenArmband(heroes)) {
                    straven(arrav, atDoor = false)
                }
            }
        }
        onOpNpcU(STRAVEN) {
            if (it.objType.internalName != INTEL_REPORT) {
                mes("Nothing interesting happens.")
                return@onOpNpcU
            }
            startDialogue(it.npc) {
                if (arrav.isPhoenix(player)) {
                    chatNpc(
                        confused,
                        "You've already given me that report! Why are you showing me another " +
                            "copy of it?",
                    )
                } else {
                    straven(arrav, atDoor = false)
                }
            }
        }
    }
}

/** Also what the player gets when they try the hideout door with Straven standing guard. */
internal suspend fun Dialogue.straven(arrav: ShieldOfArravQuest, atDoor: Boolean) {
    when {
        arrav.isPhoenix(player) -> gangMember(arrav)
        arrav.isBlackArm(player) -> chatNpc(angry, "Keep away from here, you Black Arm dog!")
        player.phoenixGang == PHOENIX_TASKED -> mission(arrav)
        else -> {
            if (!atDoor) {
                chatPlayer(quiz, "What's through that door?")
            }
            cantEnter(arrav)
        }
    }
}

private suspend fun Dialogue.cantEnter(arrav: ShieldOfArravQuest) {
    chatNpc(
        neutral,
        "You can't go in there. Only authorised staff of the VTAM Corporation are allowed past " +
            "this point.",
    )
    val knows = player.phoenixGang == PHOENIX_TOLD_BY_BARAEK && arrav.isInProgress(player)
    val topic =
        if (knows) {
            choice3(
                "I know who you are!",
                1,
                "How do I get a job with the VTAM corporation?",
                2,
                "Fair enough.",
                3,
            )
        } else {
            choice2("How do I get a job with the VTAM corporation?", 2, "Fair enough.", 3)
        }
    when (topic) {
        1 -> knowWhoYouAre()
        2 -> {
            chatPlayer(quiz, "How do I get a job with the VTAM corporation?")
            chatNpc(
                neutral,
                "Pick up a copy of the Varrock Herald. If we're hiring, you'll find it advertised " +
                    "in there.",
            )
        }
        else -> chatPlayer(neutral, "Fair enough.")
    }
}

private suspend fun Dialogue.knowWhoYouAre() {
    chatPlayer(angry, "I know who you are!")
    chatNpc(neutral, "Do you now? Go on, then. Who are we?")
    chatPlayer(
        angry,
        "This is the headquarters of the most dangerous crime syndicate this city has ever " +
            "known! The Phoenix Gang!",
    )
    chatNpc(
        shifty,
        "Not at all. This is a legitimate business run by legitimate people. But... just " +
            "suppose we were this gang. What would you want with us?",
    )
    val offer =
        choice2(
            "I'd like to offer you my services.",
            true,
            "I want nothing. I was just making sure you were them.",
            false,
        )
    if (!offer) {
        chatPlayer(happy, "I want nothing. I was just making sure you were them.")
        chatNpc(angry, "Then clear off and stop wasting my time.")
        return
    }
    chatPlayer(happy, "I'd like to offer you my services.")
    chatNpc(
        neutral,
        "You mean you want to join the Phoenix Gang? Well, naturally I can't speak for them, " +
            "but I doubt they let people in just like that. You'd have to prove you're loyal.",
    )
    chatPlayer(quiz, "And how would I do that?")
    chatNpc(
        shifty,
        "I wouldn't know, of course. Though I did hear that a rival gang of ours... of theirs... " +
            "the Black Arm Gang, is meeting a contact from Port Sarim today.",
    )
    chatNpc(
        shifty,
        "The meeting is supposed to be in the Blue Moon Inn, and the contact is said to be " +
            "Jonny the Beard - a notorious killer turned pirate captain.",
    )
    player.phoenixGang = PHOENIX_TASKED
    chatNpc(
        shifty,
        "Not that I know anything about the Phoenix Gang's affairs, but if Jonny were to meet " +
            "with an accident and someone brought back his intelligence report, I'd bet the gang " +
            "would be very pleased.",
    )
    chatPlayer(shifty, "I see... I'm sure something can be arranged. I'll get right on it.")
}

private suspend fun Dialogue.mission(arrav: ShieldOfArravQuest) {
    chatNpc(quiz, "How's your little errand going?")
    if (access.inv.count(INTEL_REPORT) == 0) {
        if (arrav.owns(access, INTEL_REPORT)) {
            chatPlayer(happy, "I have the intelligence report!")
            chatNpc(neutral, "Let's see it, then.")
            chatPlayer(neutral, "Ah... I'll just go and get it from the bank.")
            chatNpc(bored, "I'll wait here.")
            return
        }
        chatPlayer(sad, "I haven't got it yet...")
        chatNpc(
            shifty,
            "Jonny the Beard's the one who has it. I'd imagine he's in the Blue Moon Inn... not " +
                "that I'd know, what with me definitely not being in the Phoenix Gang.",
        )
        return
    }
    chatPlayer(happy, "I have the intelligence report!")
    chatNpc(neutral, "Let's see it, then.")
    objbox(INTEL_REPORT, "You show the report to Straven.")
    access.invDel(access.inv, INTEL_REPORT)
    chatNpc(
        happy,
        "Very good... very good indeed. All right, I'm convinced. Welcome to the Phoenix Gang! " +
            "The name's Straven; I'm one of the gang's leaders.",
    )
    chatPlayer(happy, "Pleased to meet you. I'm ${player.displayName}.")
    chatNpc(
        neutral,
        "As one of us, you're free to use the hideout and the weapons supply depot. The " +
            "hideout's through this door, and the depot is two doors along. You'll need this key " +
            "for it.",
    )
    player.phoenixGang = PHOENIX_JOINED
    access.invAdd(access.inv, WEAPON_STORE_KEY)
    objbox(WEAPON_STORE_KEY, "Straven hands you a key.")
    chatNpc(happy, "Welcome aboard!")
}

private suspend fun Dialogue.gangMember(arrav: ShieldOfArravQuest) {
    chatNpc(neutral, "Greetings, fellow gang member.")
    if (!arrav.owns(access, WEAPON_STORE_KEY)) {
        chatPlayer(sad, "I'm afraid I've lost the key you gave me...")
        chatNpc(
            angry,
            "You need to be more careful. We don't want that key ending up in the wrong hands. " +
                "Here's a spare - and don't lose this one!",
        )
        access.invAdd(access.inv, WEAPON_STORE_KEY)
        objbox(WEAPON_STORE_KEY, "Straven hands you a key.")
        return
    }
    while (true) {
        val topic =
            choice4(
                "I've heard you've got some cool treasures in this place.",
                1,
                "Any suggestions for where I can go thieving?",
                2,
                "Where's the Black Arm Gang hideout?",
                3,
                "I'd better get going.",
                4,
            )
        when (topic) {
            1 -> treasures(arrav)
            2 -> {
                chatPlayer(quiz, "Any suggestions for where I can go thieving?")
                chatNpc(
                    neutral,
                    "You could always try the market over in Ardougne. Plenty of opportunities " +
                        "there!",
                )
            }
            3 -> {
                chatPlayer(quiz, "Where's the Black Arm Gang hideout?")
                chatNpc(
                    shifty,
                    "Fancy spying on the enemy, do you? It won't be easy. Their security's " +
                        "good. Not as good as ours, obviously, but good.",
                )
                chatNpc(
                    neutral,
                    "If you're set on it, it's down an alley near the south gate. One of our " +
                        "contacts, a tramp called Charlie, keeps an eye on the place. He might " +
                        "help you.",
                )
                player.stravenTrampChat = true
                chatPlayer(happy, "I see. Thanks for the help!")
            }
            else -> {
                chatPlayer(neutral, "I'd better get going.")
                return
            }
        }
    }
}

private suspend fun Dialogue.treasures(arrav: ShieldOfArravQuest) {
    chatPlayer(quiz, "I've heard you've got some cool treasures in this place.")
    chatNpc(
        happy,
        "Oh, we've all nicked a thing or two in our time. Those candlesticks down here, for " +
            "instance, took some doing to get out of the palace.",
    )
    chatPlayer(quiz, "And the Shield of Arrav?")
    player.stravenShieldChat = true
    if (arrav.isComplete(player)) {
        chatNpc(neutral, "Once the pride of our collection.")
        chatPlayer(quiz, "Once?")
        chatNpc(
            angry,
            "Seems the Black Arm Gang weren't happy with just the one half. They've gone and " +
                "stolen our half as well!",
        )
        chatPlayer(
            shifty,
            "Oh no! I'll be sure to watch out for any of their lot sneaking about.",
        )
        return
    }
    chatNpc(shocked, "Now there's a blast from the past! We pinched that years and years ago!")
    chatNpc(
        neutral,
        "Truth is, we don't even have all of it any more. A while back there was a big fight " +
            "in the gang, and the shield got broken in two.",
    )
    chatNpc(
        neutral,
        "After that, some of the gang walked out and started their own outfit. They took one " +
            "half of the shield with them.",
    )
    chatPlayer(quiz, "This other outfit - the Black Arm Gang?")
    chatNpc(angry, "The very same.")
}
