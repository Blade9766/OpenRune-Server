package org.rsmod.content.quest.area.karamja.shilovillage

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.COINS
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The travel Shilo Village opens up: the jungle carts between Shilo Village (Vigroy) and Brimhaven
 * (Hajedy), and the Lady of the Waves, whose tickets Seravel sells in the village and whose captain,
 * Shanks, sails from Cairn Isle to Port Khazard and Port Sarim. None of them run until Rashiliyia
 * is at rest. The log across the river north of Ah Za Rhoon is here too.
 */
class ShiloTravel
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val search: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(VIGROY) { startDialogue(it.npc) { greetCartDriver(toBrimhaven = true) } }
        onOpNpc3(VIGROY) { startDialogue(it.npc) { offerCartRide(toBrimhaven = true) } }
        onOpNpc1(HAJEDY) { startDialogue(it.npc) { greetCartDriver(toBrimhaven = false) } }
        onOpNpc3(HAJEDY) { startDialogue(it.npc) { offerCartRide(toBrimhaven = false) } }
        onOpLoc1(SHILO_CART) { lookAtCart() }
        onOpLoc2(SHILO_CART) { boardCart(VIGROY, toBrimhaven = true) }
        onOpLoc3(SHILO_CART) { boardCart(VIGROY, toBrimhaven = true) }
        onOpLoc1(BRIMHAVEN_CART) { lookAtCart() }
        onOpLoc2(BRIMHAVEN_CART) { boardCart(HAJEDY, toBrimhaven = false) }
        onOpLoc3(BRIMHAVEN_CART) { boardCart(HAJEDY, toBrimhaven = false) }
        onOpNpc1(SERAVEL) { startDialogue(it.npc) { seravel() } }
        onOpNpc1(CAPTAIN_SHANKS) { startDialogue(it.npc) { captainShanks() } }
        onOpLoc1(LOG_BALANCE) { crossLog() }
    }

    /* The log over the river north of Ah Za Rhoon */

    private suspend fun ProtectedAccess.crossLog() {
        arriveDelay()
        val eastward = player.coords.x <= LOG_WEST_END
        val dest = if (eastward) LOG_EAST_BANK else LOG_WEST_BANK
        val start = if (eastward) LOG_WEST_BANK else LOG_EAST_BANK
        mes("You carefully balance across the log...")
        if (player.coords != start) {
            telejump(start, TeleportType.Exempt)
            delay(1)
        }
        anim(BALANCE_SEQ)
        exactMove(
            start = start,
            end = dest,
            delay1 = 0,
            delay2 = LOG_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (eastward) constants.em_face_east else constants.em_face_west,
            teleportType = TeleportType.Exempt,
        )
        delay(LOG_TICKS)
        resetAnim()
        mes("...and make it safely to the other side.")
    }

    /* The jungle carts */

    private suspend fun ProtectedAccess.lookAtCart() {
        mesbox("A sturdy travelling cart built for long trips through jungle areas.")
    }

    private suspend fun ProtectedAccess.boardCart(driver: String, toBrimhaven: Boolean) {
        mesbox("This looks like a sturdy travelling cart. A nearby man walks over to you.")
        val npc: Npc? = npcFind(player.coords, driver, DRIVER_RANGE, HuntVis.Off, search)
        if (npc != null) {
            startDialogue(npc) { offerCartRide(toBrimhaven) }
        } else {
            startDialogue { offerCartRide(toBrimhaven) }
        }
    }

    private suspend fun Dialogue.greetCartDriver(toBrimhaven: Boolean) {
        if (!toBrimhaven && !shilo.isComplete(player)) {
            closedRoute()
            return
        }
        chatPlayer(happy, "Hello!")
        chatNpc(happy, "Hello Bwana!")
        offerCartRide(toBrimhaven)
    }

    private suspend fun Dialogue.closedRoute() {
        chatNpc(
            sad,
            "We used to run cart trips down to Shilo Village in south Karamja. Since the troubles we had we've " +
                "had to stop them though, too many people got killed.",
        )
    }

    private suspend fun Dialogue.offerCartRide(toBrimhaven: Boolean) {
        if (!shilo.isComplete(player)) {
            closedRoute()
            return
        }
        val destination = if (toBrimhaven) "Brimhaven" else "Shilo Village"
        val driverName = if (toBrimhaven) "Vigroy" else "Hajedy"
        val fare = cartFare()
        chatNpc(neutral, "I am offering a cart ride to $destination if you're interested! It will cost $fare gold coins. Is that Ok?")
        if (!choice2("Yes please, I'd like to go to $destination.", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(neutral, "Ok Bwana, let me know if you change your mind.")
            return
        }
        chatPlayer(happy, "Yes please, I'd like to go to $destination.")
        if (player.inv.count(COINS) < fare) {
            chatNpc(
                sad,
                "Sorry, but it looks as if you don't have enough money. Come and see me when you have enough for the ride.",
            )
            return
        }
        chatNpc(happy, "Great! Just hop into the cart then and we'll go!")
        mesbox("You hop into the cart and the driver urges the horses on. You take a taxing journey through the jungle to $destination.")
        access.invDel(player.inv, COINS, fare)
        mesbox("You pay the fare and hand $fare gold coins to $driverName.")
        access.travel(if (toBrimhaven) ShiloCoords.BRIMHAVEN_CART_STOP else ShiloCoords.SHILO_CART_STOP)
        mesbox("You feel tired from the journey, but at least you didn't have to walk all that distance.")
    }

    /** Five percent of the coins carried, between 10 and 200. */
    private fun Dialogue.cartFare(): Int = (player.inv.count(COINS) * FARE_PERCENT / 100).coerceIn(MIN_FARE, MAX_FARE)

    /* The Lady of the Waves */

    private suspend fun Dialogue.seravel() {
        chatPlayer(neutral, "Hello")
        if (!shilo.isComplete(player)) {
            chatNpc(sad, "Hello Bwana. I'm afraid the 'Lady of the Waves' isn't sailing while Shilo Village is in such trouble.")
            return
        }
        chatNpc(quiz, "Hello Bwana. Are you interested in buying a ticket for the 'Lady of the Waves'?")
        chatNpc(
            neutral,
            "It's a ship that can take you to either Port Sarim or Khazard Port. The ship lies west of Shilo Village " +
                "and south of Cairn Island. The tickets cost $TICKET_PRICE Gold Pieces. Would you like to purchase a ticket Bwana?",
        )
        if (!choice2("Yes, that sounds great!", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(neutral, "Fair enough Bwana, let me know if you change your mind.")
            return
        }
        chatPlayer(happy, "Yes, that sounds great!")
        if (player.inv.count(COINS) < TICKET_PRICE) {
            chatNpc(sad, "Sorry Bwana, you don't have enough money. Come back when you have $TICKET_PRICE coins.")
            return
        }
        access.invDel(player.inv, COINS, TICKET_PRICE)
        access.invAdd(player.inv, SHIP_TICKET)
        chatNpc(happy, "Great, nice doing business with you.")
    }

    private suspend fun Dialogue.captainShanks() {
        if (!shilo.isComplete(player)) {
            chatNpc(
                sad,
                "Oh dear, this ship is in a terrible state. And I just can't get the items I need to repair it because " +
                    "Shilo village is overrun with zombies.",
            )
            return
        }
        chatNpc(happy, "Hello there shipmate! I sail to Khazard Port and to Port Sarim. Where are you bound?")
        if (!player.inv.contains(SHIP_TICKET)) {
            val price = access.random.of(SHANKS_PRICE_MIN, SHANKS_PRICE_MAX)
            chatNpc(
                happy,
                "I see you don't have a ticket for the ship, my colleague normally only sells them in Shilo village. " +
                    "But I could sell you one for a small additional charge. Shall we say $price gold pieces",
            )
            val buy =
                choice2(
                    "Yes, I'll buy a ticket for the ship.", true,
                    "No thanks, not just at the moment.", false,
                    title = "Buy a ticket for $price gold pieces.",
                )
            if (!buy) {
                chatPlayer(neutral, "No thanks, not just at the moment.")
                chatNpc(happy, "Very well me old shipmate, come back if you change your mind now.")
                return
            }
            chatPlayer(happy, "Yes, I'll buy a ticket for the ship.")
            if (player.inv.count(COINS) < price) {
                chatNpc(sad, "Sorry me old shipmate, but you seem to be financially challenged at the moment. Come back when your coffers are full!")
                return
            }
            chatNpc(happy, "It's a good deal and no mistake. Here you go me old shipmate, here's your ticket.")
            access.invDel(player.inv, COINS, price)
            access.invAdd(player.inv, SHIP_TICKET)
            chatNpc(happy, "Ok, now you have your ticket, do you want to sail anywhere?")
        }
        when (
            choice3(
                "Khazard Port please.", 1,
                "Port Sarim please.", 2,
                "Nowhere just at the moment thanks.", 3,
                title = "Captain Shanks asks, 'Where are you bound?'",
            )
        ) {
            1 -> sail("Khazard Port please.", ShiloCoords.PORT_KHAZARD, "You arrive safely in the quiet fishing port of Khazard.")
            2 -> sail("Port Sarim please.", ShiloCoords.PORT_SARIM, "The ship arrives in busy Port Sarim.")
            else -> {
                chatPlayer(neutral, "Nowhere just at the moment thanks.")
                chatNpc(happy, "Very well then me old shipmate, Just let me know if you change your mind.")
            }
        }
    }

    private suspend fun Dialogue.sail(request: String, dest: CoordGrid, arrival: String) {
        chatPlayer(happy, request)
        chatNpc(happy, "Very well then me old shipmate, I'll just take your ticket and then we'll set sail.")
        if (!player.inv.contains(SHIP_TICKET)) {
            return
        }
        access.invDel(player.inv, SHIP_TICKET)
        access.travel(dest)
        mesbox(arrival)
    }

    private suspend fun ProtectedAccess.travel(dest: CoordGrid) {
        fadeToBlack()
        delay(TRAVEL_TICKS)
        telejump(dest, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
    }

    private companion object {
        const val VIGROY = "npc.shilocartdriver"
        const val HAJEDY = "npc.brimhavencartdriver"
        const val SERAVEL = "npc.shiloshiptickets"
        const val CAPTAIN_SHANKS = "npc.captain_shanks"
        const val SHILO_CART = "loc.shilocart"
        const val BRIMHAVEN_CART = "loc.brimhavencart"
        const val SHIP_TICKET = "obj.shiloshipticket"
        const val LOG_BALANCE = "loc.zq_logbalance"
        const val LOG_WEST_END = 2907
        val LOG_WEST_BANK = CoordGrid(2906, 3049, 0)
        val LOG_EAST_BANK = CoordGrid(2910, 3049, 0)
        const val LOG_TICKS = 4
        const val CLIENT_CYCLES_PER_TICK = 30
        const val BALANCE_SEQ = "seq.human_walk_logbalance"

        const val DRIVER_RANGE = 7
        const val FARE_PERCENT = 5
        const val MIN_FARE = 10
        const val MAX_FARE = 200
        const val TICKET_PRICE = 25
        const val SHANKS_PRICE_MIN = 26
        const val SHANKS_PRICE_MAX = 45
        const val TRAVEL_TICKS = 3
    }
}
