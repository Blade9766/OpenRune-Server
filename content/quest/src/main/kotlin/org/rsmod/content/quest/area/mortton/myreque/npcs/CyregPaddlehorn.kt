package org.rsmod.content.quest.area.mortton.myreque.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.BOAT_FARE
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.COINS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.CYREG
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.HAMMER
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS_NEEDED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANK
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANKS_FOR_BOAT
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANKS_NEEDED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH_CHARGES_NEEDED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH_EMPTY
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BOATMAN_CONVINCED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_REACHED_HOLLOWS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.mortton.myreque.SwampBoat
import org.rsmod.content.quest.area.mortton.myreque.missingWeapons
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cyreg Paddlehorn, the Mort'ton boatman who knows where the Myreque hide.
 *
 * He has to be talked round first, and while he talks he gives away everything Curpile Fyod
 * will later quiz the player on. The first crossing is checked in full before anything is taken:
 * the weapons, a druid pouch with [POUCH_CHARGES_NEEDED] charges, the bridge supplies and the
 * fare. Only then does he keep three planks and the coins. Once the player has reached the
 * Hollows, crossings are free and unchecked, so a failed visit never strands anyone.
 */
class CyregPaddlehorn
@Inject
constructor(
    private val myq: InSearchOfTheMyrequeQuest,
    private val boat: SwampBoat,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CYREG) { talk(it.npc) }
        onOpLoc1(MORTTON_BOAT) { board() }
        onOpLoc2(MORTTON_BOAT) { board() }
        onOpLoc1(HOLLOWS_BOAT) { with(boat) { rowToMortton() } }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        var depart = false
        startDialogue(npc) { depart = cyreg() }
        if (depart) {
            with(boat) { rowToHollows() }
        }
    }

    private suspend fun ProtectedAccess.board() {
        if (myq.stage(player) >= STAGE_REACHED_HOLLOWS) {
            with(boat) { rowToHollows() }
            return
        }
        mes("This is Cyreg Paddlehorn's boat. You should speak to him before taking it.")
    }

    private suspend fun Dialogue.cyreg(): Boolean {
        val stage = myq.stage(player)
        return when {
            stage < STAGE_STARTED -> {
                chatNpc(neutral, "Boat's not for hire. Nothing out there but swamp and things you don't want to meet.")
                false
            }
            stage < STAGE_BOATMAN_CONVINCED -> persuade()
            stage < STAGE_REACHED_HOLLOWS -> firstCrossing()
            else -> regular()
        }
    }

    private suspend fun Dialogue.persuade(): Boolean {
        chatNpc(neutral, "What do you want? If it's the boat, it's not for hire.")
        chatPlayer(neutral, "I'm looking for the Myreque. I was told you know where they are.")
        chatNpc(shifty, "Myreque? Never heard of them. Whoever told you that was having a laugh.")
        when (
            choice3(
                "Tell me where they are, or else.", 1,
                "I'm no friend of the Drakans. I've brought them weapons.", 2,
                "Fine. I'll find them myself.", 3,
            )
        ) {
            1 -> {
                chatPlayer(angry, "Tell me where they are, or else.")
                chatNpc(angry, "Or else what? Vampyres have threatened me with worse. Clear off.")
                return false
            }
            2 -> chatPlayer(neutral, "I'm no friend of the Drakans. I've brought them weapons, and I'm not asking for anything in return.")
            3 -> {
                chatPlayer(neutral, "Fine. I'll find them myself.")
                chatNpc(neutral, "You do that. The ghasts could use the company.")
                return false
            }
        }
        chatNpc(quiz, "Weapons, eh? ...Say I did know where these people were. Why should I trust you?")
        when (
            choice2(
                "Without steel they'll be slaughtered. Do you want that on your conscience?", 1,
                "You shouldn't. But you'll be rowing, so you can watch me the whole way.", 2,
            )
        ) {
            1 -> chatPlayer(neutral, "Without steel they'll be slaughtered. Do you want that on your conscience?")
            2 -> chatPlayer(happy, "You shouldn't. But you'll be rowing, so you can keep an eye on me the whole way.")
        }
        chatNpc(sad, "...They've lost good people already. Fine. I'll take you, and may Saradomin help me if I'm wrong about you.")
        chatNpc(neutral, "But you'll need to know who you're dealing with. The guard at their door doesn't let in anyone who can't answer his questions.")
        myq.advanceTo(access, STAGE_BOATMAN_CONVINCED)
        askAbout()
        return firstCrossing()
    }

    private suspend fun Dialogue.askAbout() {
        while (true) {
            val topic =
                menu(
                    "Who leads them?" to Topic.Leader,
                    "Who else is in the group?" to Topic.Members,
                    "What does 'Myreque' mean?" to Topic.Meaning,
                    "Who are they fighting?" to Topic.Enemy,
                    "That's all I need to know." to Topic.Done,
                )
            when (topic) {
                Topic.Leader -> {
                    chatPlayer(quiz, "Who leads them?")
                    chatNpc(neutral, "Veliaf Hurtz. Stubborn as a mule and twice as hard to kill, which is what you want in a leader round here.")
                }
                Topic.Members -> {
                    chatPlayer(quiz, "Who else is in the group?")
                    chatNpc(neutral, "Sani Piliu, the only woman among them and the best shot. Harold Evans, an old soldier with a temper. Ivan Strom, the youngest, barely shaving.")
                    chatNpc(neutral, "Polmafi Ferdygris was a scholar before he picked up a sword, and Radigad Ponfit keeps them all fed. Oh, and I'm Cyreg Paddlehorn, in case the guard asks who rowed you.")
                }
                Topic.Meaning -> {
                    chatPlayer(quiz, "What does 'Myreque' mean?")
                    chatNpc(neutral, "Hidden in Myre. Which is why I'd rather you didn't go shouting it around Mort'ton.")
                }
                Topic.Enemy -> {
                    chatPlayer(quiz, "Who are they fighting?")
                    chatNpc(angry, "The Drakans. Lord Drakan and his family rule all of Morytania from Castle Drakan, and they treat the rest of us like cattle.")
                }
                Topic.Done -> {
                    chatPlayer(neutral, "That's all I need to know.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.firstCrossing(): Boolean {
        when (
            choice3(
                "I'm ready to go to the Myreque.", 1,
                "Tell me about the Myreque again.", 2,
                "I'll come back when I'm ready.", 3,
            )
        ) {
            1 -> chatPlayer(neutral, "I'm ready to go to the Myreque.")
            2 -> {
                askAbout()
                return false
            }
            3 -> {
                chatPlayer(neutral, "I'll come back when I'm ready.")
                chatNpc(neutral, "Bring the weapons, six planks, 225 steel nails, a hammer, ten coins and a druid pouch with five charges in it.")
                return false
            }
        }
        val missing = missingSupplies()
        if (missing.isNotEmpty()) {
            chatNpc(neutral, "Not so fast. I'm not rowing anyone out there half-prepared.")
            for (line in missing) {
                chatNpc(neutral, line)
            }
            return false
        }
        chatNpc(neutral, "Right. My boat's taking on water, so three of those planks are mine. The rest you'll want for the bridge on the other side; it's in bits.")
        chatNpc(neutral, "And it's ten coins for the trip. Ghasts don't scare off for free.")
        access.invDel(access.inv, PLANK, PLANKS_FOR_BOAT)
        access.invDel(access.inv, COINS, BOAT_FARE)
        myq.advanceTo(access, STAGE_REACHED_HOLLOWS)
        mesbox("You hand over three planks and ten coins. Cyreg patches his boat.")
        return true
    }

    /** Everything the first crossing needs, as lines Cyreg can say, so nothing is taken early. */
    private fun Dialogue.missingSupplies(): List<String> {
        val inv = player.inv
        val lines = mutableListOf<String>()
        val weapons = missingWeapons(inv)
        if (weapons.isNotEmpty()) {
            lines += "Where are the weapons? You're still short of ${weapons.joinToString()}. And they need to be in your pack, not in your hands."
        }
        val charges = inv.count(POUCH)
        if (charges < POUCH_CHARGES_NEEDED) {
            lines +=
                if (charges == 0 && POUCH_EMPTY !in inv) {
                    "Where's your druid pouch? Without one the ghasts will have you before we're halfway."
                } else {
                    "Your druid pouch needs at least $POUCH_CHARGES_NEEDED charges. Cast Bloom with a blessed sickle near something rotten and fill it with what grows."
                }
        }
        if (myq.isBridgeRepaired(player)) {
            if (inv.count(PLANK) < PLANKS_FOR_BOAT) {
                lines += "I'll need $PLANKS_FOR_BOAT planks to patch my boat."
            }
        } else {
            if (inv.count(PLANK) < PLANKS_NEEDED) {
                lines += "You'll need $PLANKS_NEEDED planks: three for my boat and three for the bridge."
            }
            if (inv.count(NAILS) < NAILS_NEEDED) {
                lines += "Bring $NAILS_NEEDED steel nails for that bridge."
            }
            if (HAMMER !in inv) {
                lines += "No hammer? How did you plan to mend a bridge, with your teeth?"
            }
        }
        if (inv.count(COINS) < BOAT_FARE) {
            lines += "And it's $BOAT_FARE coins for the trip."
        }
        return lines
    }

    private suspend fun Dialogue.regular(): Boolean {
        chatNpc(neutral, "Back again? I suppose you want rowing out to the Hollows.")
        return when (
            choice3(
                "Yes please.", 1,
                "Tell me about the Myreque again.", 2,
                "No thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                true
            }
            2 -> {
                askAbout()
                false
            }
            else -> {
                chatPlayer(neutral, "No thanks.")
                false
            }
        }
    }

    private enum class Topic { Leader, Members, Meaning, Enemy, Done }

    private companion object {
        const val MORTTON_BOAT = "loc.route_rowboat_mortton"
        const val HOLLOWS_BOAT = "loc.route_rowboat_hollows"
    }
}
