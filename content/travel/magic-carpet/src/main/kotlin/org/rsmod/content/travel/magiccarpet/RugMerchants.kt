package org.rsmod.content.travel.magiccarpet

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.travel.magiccarpet.CarpetRide.fly
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rug merchants of Ali Morrisane's flying carpet fleet. The Shantay Pass and south Pollnivneach
 * are hubs; every other station only flies back to its hub.
 */
class RugMerchants : PluginScript() {
    override fun ScriptContext.startup() {
        for (station in CarpetStation.entries) {
            onOpNpc1(station.merchant) { converse(it.npc) { talkTo(station) } }
            onOpNpc3(station.merchant) { converse(it.npc) { chooseDestination(station) } }
        }
    }

    /** Runs [conversation] and flies the route it settles on once the dialogue has closed. */
    private suspend fun ProtectedAccess.converse(
        npc: Npc,
        conversation: suspend Dialogue.() -> CarpetRoute?,
    ) {
        var route: CarpetRoute? = null
        startDialogue(npc) { route = conversation() }
        val chosen = route ?: return
        ifClose()
        fly(chosen)
    }

    private suspend fun Dialogue.talkTo(station: CarpetStation): CarpetRoute? {
        chatPlayer(neutral, "Hello.")
        chatNpc(
            neutral,
            "Greetings, desert traveller. Do you require the services of Ali Morrisane's flying " +
                "carpet fleet?",
        )
        when (
            choice5(
                "Yes please.", TRAVEL,
                "Tell me about Ali Morrisane.", ALI,
                "Tell me about this magic carpet fleet.", FLEET,
                "I have some questions.", QUESTIONS,
                "No thanks.", NO_THANKS,
            )
        ) {
            TRAVEL -> {
                chatPlayer(neutral, "Yes please.")
                return chooseDestination(station)
            }
            ALI -> aboutAli()
            FLEET -> aboutFleet()
            QUESTIONS -> questions(station)
            else -> chatNpc(neutral, "Come back anytime.")
        }
        return null
    }

    /** Offers the routes out of [station]; returns the chosen, paid-for route or null. */
    private suspend fun Dialogue.chooseDestination(station: CarpetStation): CarpetRoute? {
        val route =
            when (station) {
                CarpetStation.ShantayPass -> shantayDestinations()
                CarpetStation.PollnivneachSouth -> pollnivneachDestinations()
                else -> returnFlight(station)
            } ?: return null
        val quest = route.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            chatNpc(
                neutral,
                "I'm sorry, but the carpets aren't flying to ${route.to.place} for you just yet. " +
                    "Come back when you've got some business there.",
            )
            return null
        }
        return if (payFare()) route else null
    }

    private suspend fun Dialogue.shantayDestinations(): CarpetRoute? {
        chatNpc(
            neutral,
            "From here you can travel to Uzer, to the Bedabin camp or to the North of Pollnivneach.",
        )
        chatNpc(
            neutral,
            "The second major carpet hub station, to the south of Pollnivneach is in easy walking " +
                "distance from there.",
        )
        val destination =
            choice4(
                "I want to travel to Uzer.", CarpetStation.Uzer,
                "I want to travel to the Bedabin Camp.", CarpetStation.BedabinCamp,
                "I want to travel to Pollnivneach.", CarpetStation.PollnivneachNorth,
                "I don't want to travel to any of those places.", null,
            )
        return pickRoute(CarpetStation.ShantayPass, destination, anyOfThose = true)
    }

    private suspend fun Dialogue.pollnivneachDestinations(): CarpetRoute? {
        chatNpc(
            neutral,
            "From here you can travel to Nardah and the Menaphite cities of Sophanem and Menaphos.",
        )
        val destination =
            choice4(
                "I want to travel to Nardah.", CarpetStation.Nardah,
                "I want to travel to Menaphos.", CarpetStation.Menaphos,
                "I want to travel to Sophanem.", CarpetStation.Sophanem,
                "I don't want to travel to any of those places.", null,
            )
        return pickRoute(CarpetStation.PollnivneachSouth, destination, anyOfThose = true)
    }

    private suspend fun Dialogue.returnFlight(station: CarpetStation): CarpetRoute? {
        val (offer, accept) =
            when (station) {
                CarpetStation.Uzer ->
                    "You can travel from here back to the Shantay Pass." to
                        "That sounds good, take me there."
                CarpetStation.BedabinCamp ->
                    "From here you can travel to the Shantay Pass." to "Take me there."
                CarpetStation.PollnivneachNorth ->
                    "From here you can travel to the Shantay Pass - the Southern gate of Al " +
                        "Kharid." to "Take me to the Pass then."
                CarpetStation.Sophanem ->
                    "The carpets here will take you to the south of Pollnivneach. Do you want to " +
                        "take a lift?" to "Pollnivneach will do."
                else ->
                    "The carpets here will take you to the south of Pollnivneach." to
                        "Let's go then."
            }
        chatNpc(neutral, offer)
        val route = CarpetRoutes.from(station).single()
        val destination = choice2(accept, route.to, "I don't want to travel there.", null)
        return pickRoute(station, destination, anyOfThose = false, accept = accept)
    }

    private suspend fun Dialogue.pickRoute(
        station: CarpetStation,
        destination: CarpetStation?,
        anyOfThose: Boolean,
        accept: String? = null,
    ): CarpetRoute? {
        if (destination == null) {
            val refusal =
                if (anyOfThose) {
                    "I don't want to travel to any of those places."
                } else {
                    "I don't want to travel there."
                }
            chatPlayer(neutral, refusal)
            chatNpc(
                neutral,
                "Fair enough, magic carpet travel isn't for everyone. Enjoy the walk.",
            )
            return null
        }
        chatPlayer(neutral, accept ?: "I want to travel to ${travelName(destination)}.")
        return CarpetRoutes.from(station).first { it.to == destination }
    }

    private fun travelName(station: CarpetStation): String =
        when (station) {
            CarpetStation.BedabinCamp -> "the Bedabin Camp"
            else -> station.place
        }

    private suspend fun Dialogue.payFare(): Boolean {
        val fare = CarpetRide.fare(access)
        if (fare == 0) {
            return true
        }
        if (access.inv.count("obj.coins") < fare) {
            chatNpc(neutral, "It costs $fare gold coins to fly. Come back when you can afford it.")
            return false
        }
        if (access.invDel(access.inv, "obj.coins", fare).failure) {
            return false
        }
        access.mes("You pay $fare gold coins to the rug merchant.")
        return true
    }

    private suspend fun Dialogue.aboutAli() {
        chatPlayer(neutral, "Tell me about Ali Morrisane.")
        chatNpc(
            neutral,
            "What, you haven't heard of Ali M? Possibly the greatest salesman of the Kharidian " +
                "empire if not all Gielinor?",
        )
        if (QuestRequirements.hasCompleted(player, THE_FEUD)) {
            chatPlayer(
                neutral,
                "Ah yes I remember him now, I went on a wild goose chase looking for his nephew.",
            )
            chatNpc(laugh, "Ha! No doubt old Ali M instigated the whole thing.")
            chatPlayer(neutral, "I had a bit of fun though, The whole job was quite diverting.")
            chatNpc(
                neutral,
                "There's never a dull moment around that man, he's always looking for a way to " +
                    "make a quick coin or two.",
            )
            return
        }
        chatPlayer(
            neutral,
            "I can't say that I have, but he must be the ambitious type to try and set up his own " +
                "airline.",
        )
        chatNpc(
            neutral,
            "You know something, I reckon that he's trying to take on those gnomes at their own " +
                "game and I'd bet good money that he'll probably win.",
        )
        chatPlayer(confused, "Hah? I think you've gone and lost me now.")
        chatNpc(neutral, "You know those small little guys, not the dwarves now mind.")
        chatPlayer(neutral, "Ya... gnomes, I'm with you that far.")
        chatNpc(neutral, "Well they have already established an Airline, Gnome Air...")
        chatPlayer(neutral, "Go on...")
        chatNpc(
            neutral,
            "Anyway I think that Ali M's setup here will prove really successful and maybe once " +
                "we're properly established we could try compete with those gnomes.",
        )
        chatPlayer(neutral, "I'll watch this space.")
    }

    private suspend fun Dialogue.aboutFleet() {
        chatPlayer(neutral, "Tell me about this Magic Carpet fleet.")
        chatNpc(
            neutral,
            "The latest idea from the great Ali Morrisane. Desert travel will never be the same " +
                "again.",
        )
        chatPlayer(quiz, "So how does it work?")
        chatNpc(neutral, "The carpet or the whole enterprise?")
        val carpet =
            choice2(
                "Tell me about how the carpet works.", true,
                "Tell me about the enterprise then.", false,
            )
        if (carpet) {
            aboutCarpet()
        } else {
            aboutEnterprise()
        }
    }

    private suspend fun Dialogue.aboutCarpet() {
        chatPlayer(neutral, "Tell me about how the carpet works.")
        chatNpc(
            neutral,
            "I'm not really too sure, it's just an enchanted rug really, made out of special " +
                "Ugthanki hair. It flies to whatever destination its owner commands.",
        )
        chatPlayer(quiz, "Are they for sale then?")
        chatNpc(
            angry,
            "Do you think I'm mad? Do you think that Ali Morrisane would throw his magic carpet " +
                "monopoly away?",
        )
        chatPlayer(neutral, "Well perhaps if I offered the right price?")
        chatNpc(
            neutral,
            "Not a hope. Could you imagine the mess there'd be if people were constantly zooming " +
                "through Al Kharid and Pollnivneach? It would be chaos. This way, we can keep " +
                "the carpet traffic outside towns and other busy places.",
        )
        chatPlayer(
            neutral,
            "I suppose getting stuck in a carpet jam could get a bit tiresome.",
        )
        chatNpc(
            neutral,
            "Just think of the friction burns you would get if you were in a carpet crash.",
        )
    }

    private suspend fun Dialogue.aboutEnterprise() {
        chatPlayer(neutral, "Tell me about the enterprise then.")
        chatNpc(
            neutral,
            "It's quite simple really, Ali Morrisane has hired myself and a few others to set up " +
                "carpet stations at some of the desert's more populated places and run flights " +
                "between the stations.",
        )
        chatPlayer(quiz, "So why has he limited the service to just the desert?")
        chatNpc(
            neutral,
            "I don't think Ali is prepared to take on Gnome Air just yet, their gliders are much " +
                "faster than our carpets, besides that I think we are in the short haul " +
                "business, something that would only work in harsh conditions like the desert.",
        )
        chatPlayer(quiz, "Why is that?")
        chatNpc(
            neutral,
            "I suppose because people would just walk. Getting lost isn't too much of a problem " +
                "generally, but it's a different matter when you're in the middle of the " +
                "Kharidian desert with a dry waterskin and no idea which direction to go in.",
        )
        chatPlayer(neutral, "You're right I guess. How's the business going then?")
        chatNpc(
            neutral,
            "Not too bad, the hubs are generally quite busy. But the stations in Uzer and the " +
                "Bedabin camp could do with a bit more traffic.",
        )
        chatPlayer(neutral, "A growth market I guess.")
    }

    private suspend fun Dialogue.questions(station: CarpetStation) {
        chatPlayer(neutral, "I have some questions.")
        chatNpc(neutral, "I'll try to help you as much as I can.")
        when (
            choice3(
                "What are you doing here?", 1,
                "Is that your pet monkey nearby?", 2,
                "Where did you get that hat?", 3,
            )
        ) {
            1 -> whatAreYouDoing(station)
            2 -> aboutMonkey()
            else -> aboutHat()
        }
    }

    private suspend fun Dialogue.whatAreYouDoing(station: CarpetStation) {
        chatPlayer(quiz, "What are you doing here?")
        when (station) {
            CarpetStation.ShantayPass ->
                chatNpc(
                    neutral,
                    "Well this is a good position for desert traffic. Shantay seems to have a " +
                        "nice little money spinner setup, but I reckon, this could turn out even " +
                        "better.",
                )
            CarpetStation.Uzer -> {
                chatNpc(neutral, "You mightn't realise it, but this is quite a busy station.")
                chatPlayer(quiz, "Who would want to come here?")
                chatNpc(
                    neutral,
                    "Well you for one, and besides that we get quite a few archaeologists from " +
                        "the dig site passing through to examine the golem.",
                )
            }
            CarpetStation.BedabinCamp ->
                chatNpc(
                    neutral,
                    "Well besides the obvious - looking after this station, I'm trying to figure " +
                        "out how these Tentis manage to cultivate such delicious pineapples.",
                )
            CarpetStation.PollnivneachNorth -> {
                chatNpc(neutral, "Well Pollnivneach is the ideal location for setting up a carpet station.")
                chatPlayer(quiz, "Why's that?")
                halfwayStation()
            }
            CarpetStation.PollnivneachSouth -> {
                chatNpc(
                    neutral,
                    "I work here renting out magic carpets. I'm from Pollnivneach so it is a " +
                        "handy job, I don't have to commute too far to work every day.",
                )
                chatPlayer(quiz, "Why's that?")
                halfwayStation()
                chatPlayer(neutral, "So I suppose you're called Ali then.")
                chatNpc(
                    neutral,
                    "Not the most remarkable of names, not that it matters, you see everyone in " +
                        "town knows me as Flash.",
                )
                chatPlayer(quiz, "Really?")
                chatNpc(neutral, "No.")
                chatPlayer(neutral, "........")
                chatNpc(neutral, "........")
                chatPlayer(neutral, "Oh right.")
            }
            CarpetStation.Nardah ->
                chatNpc(
                    neutral,
                    "Well I'd preferred to have been running one of the carpet stations at a hub " +
                        "such as Pollnivneach. I was a bit slow off the mark to get that gig " +
                        "though. Still business in Nardah isn't bad for a terminal. At least " +
                        "people come here for the bank and to see the herbalist.",
                )
            CarpetStation.Sophanem -> {
                chatNpc(
                    neutral,
                    "I look after the carpet station here. The place is a bit dead though. Ha! " +
                        "I'm just too much.",
                )
                chatPlayer(confused, "What?")
                chatNpc(laugh, "You know, Sophanem, city of the dead and all that?")
                chatPlayer(neutral, "...")
                chatNpc(neutral, "Aw come on, the joke wasn't that bad.")
                chatPlayer(neutral, "...")
            }
            CarpetStation.Menaphos -> {
                chatNpc(
                    neutral,
                    "Until recently I was looking after one of the busiest carpet stations. But " +
                        "that all changed since Menaphos closed its gates.",
                )
                chatNpc(
                    neutral,
                    "Right now I have to fill my day trying to come up with reasons why Ali M " +
                        "should keep this place open.",
                )
                chatPlayer(quiz, "So have you come up with any good ideas then?")
                chatNpc(
                    neutral,
                    "Not really. The only reason I have come up with to date is that by keeping " +
                        "the station open, people become familiar with it.",
                )
                chatNpc(
                    neutral,
                    "Maybe once Menaphos opens her gates again, the station will make a fortune " +
                        "once more.",
                )
            }
        }
    }

    private suspend fun Dialogue.halfwayStation() {
        chatNpc(
            neutral,
            "You see it's located halfway between Al Kharid, and the Menaphite cities and close " +
                "enough to Nardah too, so we get more than enough traffic to keep the business " +
                "running.",
        )
    }

    private suspend fun Dialogue.aboutMonkey() {
        chatPlayer(quiz, "Is that your pet monkey nearby?")
        chatNpc(neutral, "He's his own monkey, he does whatever suits him, a total nuisance.")
        chatPlayer(neutral, "I detect a degree of hostility being directed towards the monkey.")
        chatNpc(
            neutral,
            "I shouldn't say this really, but sometimes I begin to question some of Ali " +
                "Morrisane's ideas, he says that associating a monkey with any product will " +
                "increase sales. I just don't know, what will be next?",
        )
        chatPlayer(quiz, "Frogs?")
        chatNpc(neutral, "I doubt it, amphibians don't have the same cutesy factor as monkeys.")
        chatPlayer(confused, "I'm confused. I thought you didn't like monkeys.")
        chatNpc(
            neutral,
            "I don't dislike monkeys, it's just that monkey. I don't know, I might just be " +
                "paranoid but I think he's... well... evil.",
        )
        chatPlayer(neutral, "Hmmm... Interesting.")
    }

    private suspend fun Dialogue.aboutHat() {
        chatPlayer(quiz, "Where did you get that hat?")
        chatNpc(
            neutral,
            "My fez? I got it from Ali Morrisane, it's a uniform of sorts, apparently it makes us " +
                "more visible, but I'm not too sure about it.",
        )
        chatPlayer(neutral, "Well it is quite distinctive.")
        chatNpc(
            neutral,
            "Do you like it? I haven't really made my mind up about it yet. You see it's not all " +
                "that practical for desert conditions.",
        )
        chatPlayer(quiz, "How so?")
        chatNpc(
            neutral,
            "Well it doesn't keep the sun out of my eyes and after a while sitting out in the " +
                "desert they really begin to burn.",
        )
    }

    private companion object {
        const val TRAVEL = 1
        const val ALI = 2
        const val FLEET = 3
        const val QUESTIONS = 4
        const val NO_THANKS = 5

        const val THE_FEUD = "quest_feud"
    }
}
