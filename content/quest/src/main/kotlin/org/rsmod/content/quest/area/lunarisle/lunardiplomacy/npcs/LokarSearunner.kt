package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarCoords
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_GOT_SEAL
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_SAILED_TO_COVE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarTravel
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lokar Searunner, a Fremennik who ran away to be a pirate. On the Rellekka docks he is a
 * multi-npc on the quest stage that gains his "Pirate's Cove" option once he has taken the player
 * there; at the Cove he offers the trip back to "Rellekka".
 */
class LokarSearunner
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val trials: FremennikTrialsQuest,
    private val travel: LunarTravel,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(RELLEKKA_LOKAR) { startDialogue(it.npc) { rellekka() } }
        onOpNpc3(RELLEKKA_LOKAR) { toPiratesCove() }
        onOpNpc1(COVE_LOKAR) { startDialogue(it.npc) { piratesCove() } }
        onOpNpc3(COVE_LOKAR) { toRellekka() }
    }

    private suspend fun Dialogue.rellekka() {
        val stage = lunar.stage(player)
        when {
            !trials.isComplete(player) -> outerlander()
            stage == 0 -> introduction()
            stage == STAGE_STARTED -> {
                chatNpc(quiz, "Hey there, ${name()}. Did Brundt the Chieftain give you that seal thingy yet?")
                chatPlayer(sad, "No... not yet.")
                chatNpc(angry, "Well get a move on! I've had my fill of this dump of a town and I want to get back to the ship.")
            }
            stage == STAGE_GOT_SEAL -> readyToLeave()
            !travel.hasSeal(access) && !lunar.isComplete(player) -> {
                chatNpc(neutral, "You won't make many friends on Lunar Isle without your Seal of Passage.")
                if (choice2("That's fine, I'm only going to the Pirates' Cove.", true, "Oh, I'd better go and fetch it.", false)) {
                    chatPlayer(neutral, "That's fine, I'm only going to the Pirates' Cove.")
                    offerTrip()
                } else {
                    chatPlayer(neutral, "Oh, I'd better go and fetch it.")
                }
            }
            else -> {
                chatPlayer(quiz, "Lokar, could you take me back to your ship?")
                chatNpc(bored, "Make your mind up, pal. I'm a pirate, not a ferryman!")
                offerTrip()
            }
        }
    }

    private suspend fun Dialogue.offerTrip() {
        if (choice2("Go now.", true, "Don't go.", false)) {
            access.toPiratesCove()
            return
        }
        chatPlayer(shifty, "Actually, I've changed my mind again. I'll stay here.")
        chatNpc(bored, "You must be the most indecisive person I have ever met...")
    }

    private suspend fun Dialogue.outerlander() {
        chatPlayer(happy, "Hello there.")
        chatNpc(angry, "Clear off, outerlander! I've no time for the likes of you!")
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(happy, "Hello there.")
        chatNpc(angry, "Don't bother me, outerlander. I haven't got time for your sort!")
        chatPlayer(angry, "Outerlander? I'll have you know I'm the mighty ${name()}!")
        chatNpc(
            confused,
            "Never heard of you. They're letting anyone into the clan these days, are they? Things " +
                "have really changed since I left...",
        )
        when (choice3("You don't look like a Fremennik yourself...", 1, "You've been away from these parts a while?", 2, "Well, 'bye then.", 3)) {
            1 -> {
                chatPlayer(quiz, "You don't look much like a Fremennik yourself...")
                chatNpc(sad, "No, I suppose I don't any more. I've been away a very long time.")
            }
            2 -> awayAWhile()
            else -> chatPlayer(neutral, "Well, 'bye then.")
        }
    }

    private suspend fun Dialogue.awayAWhile() {
        chatPlayer(quiz, "You've been away from these parts a while?")
        chatNpc(shocked, "You mean you've never heard of Lokar, terror of the northern seas?")
        chatPlayer(confused, "Erm... no. I take it that's you?")
        chatNpc(angry, "Forgotten me already! Well, I don't regret leaving one bit.")
        if (!choice2("Why did you leave?", true, "Well, 'bye then.", false)) {
            chatPlayer(neutral, "Well, 'bye then.")
            return
        }
        chatPlayer(quiz, "Why did you leave?")
        chatNpc(
            neutral,
            "When I was young I found the Fremennik life far too slow. Farming, fishing, milking " +
                "cows... the only fun was the odd raid on the islands around here.",
        )
        chatNpc(
            happy,
            "So I ran away to be a PIRATE! Robbing ships, spending it all on wine and song, and " +
                "seeing the world. It beats milking cows, I can tell you.",
        )
        chatPlayer(quiz, "Then why come back to Rellekka at all?")
        chatNpc(neutral, "Supplies. The rest of the crew are over at Lunar Isle, where the Moon Clan live.")
        chatPlayer(neutral, "The Moon Clan... I've heard of them.")
        chatNpc(
            neutral,
            "I'm not surprised. They've been at war with the Fremennik for hundreds of years. " +
                "Hmm, that might be a problem for you, actually.",
        )
        chatNpc(
            worried,
            "They're powerful mages. They'd read your mind, see you're a Fremennik and probably " +
                "turn you inside out.",
        )
        chatPlayer(quiz, "But they let you visit?")
        chatNpc(
            laugh,
            "I think the Fremennik are a bunch of losers, and I reckon they picked up on that. " +
                "Still, I know a way you could visit without getting killed, if you fancy the trip.",
        )
        val start = choice2("Yes.", true, "No.", false, title = "Start the Lunar Diplomacy quest?")
        if (!start) {
            chatPlayer(worried, "No thanks, I'd quite like to live.")
            chatNpc(bored, "So you're a loser like the rest of them. See you around.")
            return
        }
        val missing = lunar.missingRequirements(access)
        if (missing.isNotEmpty()) {
            chatNpc(
                shifty,
                "Actually, on second thoughts, you don't look up to it. Come back when you're a bit " +
                    "more experienced.",
            )
            mesbox("You need: ${missing.joinToString(", ")}.")
            return
        }
        chatPlayer(happy, "Why not? I've always wondered what my innards look like!")
        lunar.advanceTo(access, STAGE_STARTED)
        chatNpc(
            happy,
            "That's the spirit! Go and ask Brundt the Chieftain for a Seal of Passage. It marks " +
                "you out as a diplomat, and nobody attacks a diplomat.",
        )
        chatPlayer(shocked, "A diplomat? So I'm meant to make peace between two clans who've fought for centuries?")
        chatNpc(
            laugh,
            "I never said you had to actually try. Just look like you might. If those losers want " +
                "to get themselves slaughtered, that's more loot for me.",
        )
        chatPlayer(neutral, "Well, I'll do my best. I am the mighty ${name()}, after all.")
        chatNpc(neutral, "Right... Come back once you've got the seal. My supplies are loaded and I'm ready to go.")
    }

    private suspend fun Dialogue.readyToLeave() {
        chatNpc(quiz, "So have you got that seal from Brundt the Chieftain? Can we leave this dump yet?")
        if (!travel.hasSeal(access)) {
            chatPlayer(sad, "Well, he gave me one... but I think I've lost it somewhere.")
            chatNpc(angry, "Useless! Go and get another one, and be quick about it.")
            return
        }
        chatPlayer(happy, "Yes, I've got it right here!")
        chatNpc(happy, "Great! Then let's head back to my ship. Ready?")
        if (!choice2("Arrr! Let's be on our way, yarr!", true, "Not just yet...", false)) {
            chatPlayer(neutral, "Not just yet...")
            chatNpc(angry, "Well hurry up. I'm a bloodthirsty pirate, not your personal travel agent.")
            return
        }
        chatPlayer(happy, "Arrr! Let's be on our way, yarr!")
        chatNpc(confused, "...Why are you talking like that?")
        chatPlayer(quiz, "Isn't that how pirates talk?")
        chatNpc(
            angry,
            "Only the really stupid ones. Don't talk like that to my crew, they'll think you're " +
                "mocking them and gut you.",
        )
        chatPlayer(happy, "Right you are. Tally-ho!")
        chatNpc(bored, "I suppose that'll have to do.")
        lunar.advanceTo(access, STAGE_SAILED_TO_COVE)
        access.toPiratesCove()
        chatNpc(neutral, "Here we are at the Pirates' Cove. The captain's already aboard the Lady Zay.")
    }

    private suspend fun Dialogue.piratesCove() {
        chatPlayer(happy, "Hello again, Lokar.")
        chatNpc(happy, "Hi again, ${name()}! What can I do for you?")
        if (!travel.hasSeal(access) && !lunar.isComplete(player)) {
            chatPlayer(quiz, "Do I still need the Seal of Passage?")
            chatNpc(neutral, "Yeah, why?")
            chatPlayer(sad, "I've lost it.")
            chatNpc(laugh, "Silly you. You'll have to get another from Brundt the Chieftain before you go on to Lunar Isle.")
        }
        if (choice2("Can you take me back to Rellekka?", true, "Nothing, thanks.", false)) {
            chatPlayer(quiz, "Can you take me back to Rellekka, please?")
            chatNpc(bored, "If you want to go back to loserville, who am I to stop you?")
            access.toRellekka()
            return
        }
        chatPlayer(happy, "Nothing, thanks. I just saw you and thought I'd say hello!")
        chatNpc(happy, "I knew you seemed alright when I met you, ${name()}!")
    }

    private suspend fun ProtectedAccess.toPiratesCove() {
        with(travel) { rowWithLokar(LunarCoords.COVE_DOCK) }
    }

    private suspend fun ProtectedAccess.toRellekka() {
        with(travel) { rowWithLokar(LunarCoords.RELLEKKA_DOCK) }
    }

    private fun Dialogue.name(): String = trials.fremennikName(player)

    private companion object {
        const val RELLEKKA_LOKAR = "npc.lunar_fremennik_pirate"
        const val COVE_LOKAR = "npc.lunar_fremennik_pirate_piratecove"
    }
}
