package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarCoords
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ASKED_NAVIGATOR
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_AT_LUNAR_ISLE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_BLAMED_NAVIGATOR
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_JINX_LIFTED
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_SAILED_IN_CIRCLE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_SAILED_TO_COVE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarTravel
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Captain Bentley of the Lady Zay and his parrot. He is a multi-npc on the quest stage that gains
 * a "Travel" option once the ship has made it to Lunar Isle; after that he sails between the
 * Pirates' Cove and the island from whichever copy of the ship he is standing on.
 */
class CaptainBentley
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val travel: LunarTravel,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(CAPTAIN) { startDialogue(it.npc) { talk() } }
        onOpNpc3(CAPTAIN) { travelOn() }
        onOpNpc1(PARROT) { parrot(it.npc) }
    }

    private suspend fun Dialogue.talk() {
        val stage = lunar.stage(player)
        when {
            stage < STAGE_SAILED_TO_COVE -> stowaway()
            stage == STAGE_SAILED_TO_COVE -> welcome()
            stage == STAGE_SAILED_IN_CIRCLE -> {
                chatNpc(confused, "I don't understand it... the course was plotted, the wind was fair, and yet we haven't moved an inch!")
                chatPlayer(bored, "That was the most pointless voyage I've ever been on.")
                chatNpc(
                    neutral,
                    "Perhaps you should speak with Jack, our navigator. He's at the back of the " +
                        "ship, one deck below this one. Wears a brown hat.",
                )
                squawk("Below deck! Below deck!")
                if (choice2("Can we try again?", true, "I'll go and find him.", false)) {
                    sailAgain()
                }
            }
            stage == STAGE_ASKED_NAVIGATOR -> {
                chatNpc(neutral, "Hello again. What are you wanting now?")
                if (choice2("Can we try again?", true, "Perhaps it's the navigator's fault?", false)) {
                    sailAgain()
                    return
                }
                chatPlayer(quiz, "Perhaps the navigator made a mistake when he plotted the course?")
                chatNpc(confused, "You think so? His course looked true to me... but perhaps I'd better ask him.")
                chatPlayer(happy, "I'll go and ask him for you, Captain.")
                lunar.advanceTo(access, STAGE_BLAMED_NAVIGATOR)
                chatNpc(happy, "Would you? That's very helpful, for a stowaway. He's one deck down, at the back.")
                squawk("Stowaway! Stowaway!")
                chatPlayer(angry, "I'M NOT A STOWAWAY!")
            }
            stage in STAGE_BLAMED_NAVIGATOR until STAGE_JINX_LIFTED -> {
                chatNpc(quiz, "Have you found out what's wrong with my ship yet?")
                chatPlayer(neutral, "I'm working on it, Captain.")
                if (choice2("Can we try sailing again anyway?", true, "I'll keep looking.", false)) {
                    sailAgain()
                }
            }
            stage == STAGE_JINX_LIFTED -> jinxLifted()
            LunarCoords.onLunarShip(player.coords) -> atLunarIsle()
            else -> {
                chatPlayer(quiz, "Can we head to Lunar Isle?")
                if (!travel.hasSeal(access)) {
                    noSeal()
                    return
                }
                chatNpc(happy, "Sure thing, matey!")
                with(travel) { access.sailToLunarIsle() }
            }
        }
    }

    private suspend fun Dialogue.stowaway() {
        chatNpc(quiz, "Hmmm? I don't recognise you as one of my crew. What are you doing aboard my ship?")
        squawk("Stowaway! Stowaway!")
        chatPlayer(neutral, "Just looking around.")
        chatNpc(neutral, "Well, look around somewhere else.")
    }

    private suspend fun Dialogue.welcome() {
        chatPlayer(happy, "Aye-aye, cap'n!")
        chatNpc(quiz, "Hmmm? I don't recognise you as one of my men. What are you doing aboard my ship?")
        squawk("Stowaway! Stowaway!")
        chatPlayer(neutral, "Lokar offered me a lift. He said you could take me to the island of the Moon Clan.")
        chatNpc(
            sad,
            "Did he now? Sometimes I regret ever letting that vagabond join the crew. Terrible " +
                "work ethic, and a very confrontational attitude.",
        )
        squawk("Lokar's a loser!")
        chatPlayer(quiz, "So you won't take me to Lunar Isle?")
        chatNpc(
            neutral,
            "I didn't say that. We're heading back there soon anyway; our last visit was most " +
                "profitable. As captain I suppose I should play host, so is there anything you " +
                "wish to know?",
        )
        questions()
    }

    private suspend fun Dialogue.questions() {
        while (true) {
            val option =
                choice5(
                    "Can you tell me something about the Moon Clan?",
                    1,
                    "What were you doing at Lunar Isle?",
                    2,
                    "Can you tell me something about your ship?",
                    3,
                    "Can we sail to Lunar Isle now?",
                    4,
                    "Absolutely nothing at all.",
                    5,
                )
            when (option) {
                1 -> moonClan()
                2 -> whyLunarIsle()
                3 -> ship()
                4 -> {
                    sailNow()
                    return
                }
                else -> {
                    chatPlayer(bored, "No, actually. I've no idea why I started talking to you.")
                    chatNpc(
                        angry,
                        "You could be more grateful for free passage, but you are a friend of " +
                            "Lokar's and a Fremennik, so I suppose rudeness comes naturally. Be " +
                            "ready; we sail shortly.",
                    )
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.moonClan() {
        chatPlayer(quiz, "What can you tell me about this 'Moon Clan'? It all sounds very mysterious.")
        chatNpc(
            happy,
            "Very fine people. Very hospitable. We'd heard they don't care for strangers, but " +
                "they were delighted to see us - they even threw a festival in our honour!",
        )
        chatNpc(
            neutral,
            "They're masters of magic, and sold us rune-stones and a few good luck charms for " +
                "the voyage. A little secretive about their temple and that old lodge in town, " +
                "and they have the strangest house I've ever seen, but a pleasant trip all round.",
        )
        chatNpc(happy, "And it's not every port where a pirate can rest ashore without the guards coming for him.")
        squawk("Fight the law!")
    }

    private suspend fun Dialogue.whyLunarIsle() {
        chatPlayer(quiz, "What were you doing at Lunar Isle anyway? I thought pirates went pirating.")
        chatNpc(
            neutral,
            "We like to think of ourselves as unregulated merchants. What good is plunder if you've " +
                "nowhere to spend it? Naturally our first plan was to rob them blind, mind you.",
        )
        chatPlayer(quiz, "So what changed your mind?")
        chatNpc(
            worried,
            "First, the monsters. The island's swarming with the things, big as a wolf and twice " +
                "as smelly. You'd want to be handy with a blade out there.",
        )
        squawk("Run for your lives, lads!")
        chatNpc(
            worried,
            "Second, the Moon Clan are stupendously powerful magicians - and they read minds. Had " +
                "we turned up planning a robbery, they'd have killed us on the spot.",
        )
        chatNpc(happy, "So we traded instead. I do like their non-judgemental attitude.")
        chatPlayer(neutral, "Okay then...")
    }

    private suspend fun Dialogue.ship() {
        chatPlayer(happy, "I was hoping you'd tell me about your ship. She's very impressive.")
        chatNpc(
            happy,
            "Ah, a connoisseur! She's the Lady Zay, one of the finest ships ever to sail. A real " +
                "steal from the Karamjan shipyards - and I do mean we stole her.",
        )
        chatNpc(
            laugh,
            "Whoever they were building her for must have been awfully short. We had to raise all " +
                "the doors so we could stop crouching, but she's just how I want her now.",
        )
        squawk("I want guns, lots of guns!")
        chatPlayer(neutral, "I used to own a ship myself, you know. The Lady Lumbridge.")
        chatNpc(neutral, "You never struck me as captain material, but I suppose there's a ship out there for everyone.")
    }

    private suspend fun Dialogue.sailNow() {
        if (!travel.hasSeal(access)) {
            noSeal()
            return
        }
        chatPlayer(quiz, "Can we sail to Lunar Isle now? It all sounds very intriguing.")
        chatNpc(
            neutral,
            "Keep your Seal of Passage with you at all times on that island and you should be " +
                "fine, Fremennik or not. I'm not joking: without it they'd have your hide.",
        )
        chatPlayer(neutral, "Keep the seal with me at all times. Got it. Can we go now?")
        chatNpc(happy, "Indeed we can! The winds look good, so let's make a move!")
        squawk("She's blowing a storm, cap'n!")
        with(travel) { access.sailInCircle() }
        lunar.advanceTo(access, STAGE_SAILED_IN_CIRCLE)
        chatNpc(shocked, "Well... THAT was deuced strange!")
        chatPlayer(bored, "Um... way to sail a boat?")
        squawk("Way to sail a boat!")
        chatNpc(confused, "This is VERY strange indeed...")
    }

    private suspend fun Dialogue.noSeal() {
        chatNpc(
            worried,
            "I'd take you, but the Moon Clan are frankly very scary people, and they'd not take " +
                "kindly to us bringing a Fremennik without a Seal of Passage.",
        )
        chatPlayer(sad, "I probably should have kept hold of the seal Brundt gave me, shouldn't I?")
        chatNpc(neutral, "Yes, you should. I can't take you without it.")
        squawk("Don't drop quest items halfway through a quest!")
    }

    private suspend fun Dialogue.sailAgain() {
        chatNpc(neutral, "Yes, perhaps it was just a freak wind. Off we go!")
        with(travel) { access.sailInCircle() }
        chatNpc(sad, "No... we're right back where we started.")
    }

    private suspend fun Dialogue.jinxLifted() {
        chatPlayer(happy, "The jinx should be gone now!")
        chatNpc(happy, "Really? Good work, landlubber!")
        chatPlayer(neutral, "It was the cabin boy's doing, it seems.")
        chatNpc(neutral, "Pesky kid. I remember when I was a cabin boy, many years ago, I started off as...")
        chatPlayer(bored, "Spare me the life story. Can we go now?")
        chatNpc(angry, "No need to be rude.")
        if (!travel.hasSeal(access)) {
            noSeal()
            return
        }
        chatNpc(happy, "Very well, off we go!")
        with(travel) { access.sailToLunarIsle() }
        lunar.advanceTo(access, STAGE_AT_LUNAR_ISLE)
        chatNpc(happy, "Here we are! Lunar Isle!")
        chatPlayer(happy, "Thanks, cap'n.")
    }

    private suspend fun Dialogue.atLunarIsle() {
        chatPlayer(neutral, "Hi.")
        chatNpc(bored, "And what are you wanting now?")
        if (choice2("Can you take me back to the Pirates' Cove?", true, "So we're here?", false)) {
            chatPlayer(quiz, "Can you take me back to the Pirates' Cove, please?")
            chatNpc(neutral, "I'll take you as far as the Cove. You'll have to find your own way from there.")
            with(travel) { access.sailToPiratesCove() }
            return
        }
        chatPlayer(quiz, "So we're here?")
        chatNpc(
            neutral,
            "Yep. You're free to explore the island. Be careful, though: it wouldn't be wise to " +
                "wrong the Moon Clan.",
        )
        chatPlayer(neutral, "Thanks. I'll keep my seal close.")
    }

    private suspend fun ProtectedAccess.travelOn() {
        if (lunar.stage(player) < STAGE_AT_LUNAR_ISLE) {
            return
        }
        if (LunarCoords.onLunarShip(coords)) {
            with(travel) { sailToPiratesCove() }
            return
        }
        if (!travel.hasSeal(this)) {
            mes("Captain Bentley won't take you to Lunar Isle without your Seal of Passage.")
            return
        }
        with(travel) { sailToLunarIsle() }
    }

    private suspend fun ProtectedAccess.parrot(npc: Npc) {
        faceEntitySquare(npc)
        npc.say(PARROT_LINES.random())
    }

    private suspend fun Dialogue.squawk(text: String) {
        chatNpcSpecific("Parrot", PARROT, happy, "*Squawk* $text")
    }

    private companion object {
        const val CAPTAIN = "npc.lunar_pirate_captain"
        const val PARROT = "npc.lunar_captains_parrot"

        val PARROT_LINES =
            listOf(
                "*Squawk* Pieces of eight! Pieces of eight!",
                "*Brakawk* Stowaway! Stowaway!",
                "*Squawk* Polly wants a biscuit!",
                "*Squawk* Shiver me timbers!",
            )
    }
}
