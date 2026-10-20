package org.rsmod.content.areas.misc.waterbirth

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Jarvald sails between the Rellekka docks and Waterbirth Island. Outerlanders pay 1,000 coins a
 * trip; Fremenniks (per the quest requirement mode) sail free. Once the player has made the trip,
 * the Rellekka Jarvald gains his "Waterbirth Island" quick-travel option.
 */
class Jarvald @Inject constructor(private val trials: FremennikTrialsQuest) : PluginScript() {
    private var Player.quickTravel by boolVarBit("varbit.dag_island_quick_travel")

    override fun ScriptContext.startup() {
        onOpNpc1(RELLEKKA_TALK_ONLY) { startDialogue(it.npc) { rellekka() } }
        onOpNpc1(RELLEKKA_QUICK) { startDialogue(it.npc) { rellekka() } }
        onOpNpc3(RELLEKKA_QUICK) { quickTravelToIsland() }
        onOpNpc1(ISLAND) { startDialogue(it.npc) { island() } }
        onOpNpc3(ISLAND) { sail(WaterbirthCoords.RELLEKKA_PIER, "Rellekka") }
    }

    private fun isFremennik(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, FremennikTrialsQuest.QUEST_KEY)

    private fun Dialogue.name(): String =
        if (trials.isComplete(player)) trials.fremennikName(player) else player.displayName

    private suspend fun Dialogue.rellekka() {
        if (!isFremennik(player)) {
            outerlander()
            return
        }
        chatNpc(happy, "Greetings, ${name()}!")
        chatNpc(
            happy,
            "So what brings you back to fair Rellekka? It has been too long since you have drunk " +
                "in the longhall with us and sang of your battles!",
        )
        val choice =
            choice3(
                "What Jarvald is doing.",
                1,
                "Can I come?",
                2,
                "Nothing.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "So what are you doing here?")
                chatNpc(happy, "You have not heard, ${name()}? I am leading an expedition to Waterbirth Island!")
                fremennikTrip()
            }
            2 -> fremennikTrip()
            else -> chatPlayer(neutral, "Actually, I don't think I have anything to speak to you about...")
        }
    }

    private suspend fun Dialogue.fremennikTrip() {
        chatPlayer(quiz, "Can I come?")
        chatNpc(happy, "Of course, ${name()}! Your presence is more than welcome on this cull! You wish to leave now?")
        if (choice2("YES", true, "NO", false)) {
            access.sail(WaterbirthCoords.ISLAND_SHORE, "Waterbirth Island")
            return
        }
        notNow()
    }

    private suspend fun Dialogue.outerlander() {
        chatNpc(
            neutral,
            "What do you want from me outerlander? It is our policy not to associate with those " +
                "not of our tribe.",
        )
        if (!choice2("What Jarvald is doing.", true, "Nothing.", false)) {
            chatPlayer(neutral, "Actually, I don't think I have anything to speak to you about...")
            return
        }
        chatPlayer(quiz, "So what are you doing here?")
        chatNpc(
            neutral,
            "This should not concern you, outerlander. I am awaiting other Fremenniks to join me " +
                "on an expedition to Waterbirth Island.",
        )
        chatPlayer(quiz, "Can I come?")
        chatNpc(shocked, "An outerlander join us on an honoured hunt???")
        chatNpc(
            neutral,
            "I will allow you to escort us, but you must pay me a sum of money first. Let us " +
                "say... 1,000 coins. Payable in advance, of course.",
        )
        chatNpc(
            neutral,
            "So do you have the 1,000 coins for my service, and are you ready to leave now?",
        )
        if (!choice2("YES", true, "NO", false)) {
            notNow()
            return
        }
        if (!access.payFare()) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        access.sail(WaterbirthCoords.ISLAND_SHORE, "Waterbirth Island")
        chatNpc(
            neutral,
            "I suggest you head to the cave with some urgency outerlander, the cold air out here " +
                "might be too much for the likes of you...",
        )
    }

    private suspend fun Dialogue.notNow() {
        chatPlayer(neutral, "No, actually I have some stuff to do here first.")
        chatNpc(neutral, "As you wish. Come and see me when your bloodlust needs sating.")
    }

    private suspend fun Dialogue.island() {
        if (isFremennik(player)) {
            chatNpc(happy, "Ah, ${name()}! Such glorious battle makes you feel glad of life, does it not?")
            chatNpc(
                quiz,
                "So what say you, stay here for the hunt, or return home to sweet Rellekka to feast " +
                    "and drink with your tribe?",
            )
        } else {
            chatNpc(happy, "Ah, you live yet, outerlander!")
            chatNpc(
                quiz,
                "Have you had your fill of the hunt and wish to return, or are you still feeling the " +
                    "joy of the cull?",
            )
        }
        if (choice2("I wish to return to Rellekka.", true, "I want to stay here.", false)) {
            chatPlayer(neutral, "I wish to return to Rellekka.")
            chatNpc(happy, "Then let us away; There will be death to bring here another day!")
            access.sail(WaterbirthCoords.RELLEKKA_PIER, "Rellekka")
            return
        }
        chatPlayer(neutral, "I want to stay here.")
        chatNpc(laugh, "Ha Ha Ha! A true huntsman at heart!")
        chatNpc(happy, "I myself have killed over a hundred of the dagger- mouths, and did not think it too many!")
    }

    private suspend fun ProtectedAccess.quickTravelToIsland() {
        if (!isFremennik(player) && !payFare()) {
            mes("You need 1,000 coins to pay for the trip to Waterbirth Island.")
            return
        }
        sail(WaterbirthCoords.ISLAND_SHORE, "Waterbirth Island")
    }

    private fun ProtectedAccess.payFare(): Boolean {
        if (isFremennik(player)) {
            return true
        }
        if (inv.count(COINS) < FARE || !invDel(inv, COINS, FARE).success) {
            return false
        }
        mes("You pay Jarvald $FARE coins.")
        return true
    }

    private suspend fun ProtectedAccess.sail(dest: CoordGrid, destName: String) {
        ifClose()
        mes("You board the longship and sail to $destName.")
        midiJingle(VOYAGE_JINGLE)
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FADE_CYCLES,
        )
        delay(SAIL_TICKS)
        telejump(dest)
        player.quickTravel = true
        delay(1)
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FADE_CYCLES,
        )
        closeFadeOverlay(cycles = 2)
    }

    private companion object {
        const val RELLEKKA_TALK_ONLY = "npc.viking_dagganoth_cave_ferryman_1op"
        const val RELLEKKA_QUICK = "npc.viking_dagganoth_cave_ferryman_island"
        const val ISLAND = "npc.viking_dagganoth_cave_waterbirth_island"
        const val COINS = "obj.coins"
        const val FARE = 1000
        const val VOYAGE_JINGLE = "jingle.sailing_journey_short"
        const val FADE_CYCLES = 50
        const val SAIL_TICKS = 4
    }
}
