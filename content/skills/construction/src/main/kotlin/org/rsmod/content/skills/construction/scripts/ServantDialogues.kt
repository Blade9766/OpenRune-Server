package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.interfaces.bank.selectedTab
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.skills.construction.data.Kitchen
import org.rsmod.content.skills.construction.data.PlankType
import org.rsmod.content.skills.construction.data.Servant
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.HouseServants
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * Everything said to and done by servants: hiring one at the guild, dismissing one through the
 * chief servant, and the errands a hired servant runs from its owner's house.
 *
 * Errands are paid services: every [Servant.TRIPS_PER_WAGE] of them the servant wants its fee
 * again before it does anything else. An errand takes the servant out of the house for its trip
 * time; items only change hands when it gets back, so nothing is lost to a logout mid-trip -
 * except a deposit, which is made in the bank at once.
 */
@Singleton
class ServantDialogues
@Inject
constructor(
    private val servants: HouseServants,
    private val registry: HouseRegistry,
    private val store: HouseStore,
    private val players: PlayerList,
    private val bankInv: BankInvScript,
) {
    sealed class Errand {
        data class Fetch(val obj: String, val count: Int) : Errand()

        data class Deposit(val obj: String, val count: Int) : Errand()

        data class Unnote(val noted: String, val count: Int) : Errand()

        data class Sawmill(val plank: PlankType, val count: Int) : Errand()
    }

    private val lastErrand = HashMap<Long, Errand>()

    // -------------------------------------------------------------------------- the guild

    suspend fun ProtectedAccess.guildTalk(npc: Npc, servant: Servant) {
        startDialogue(npc) {
            chatNpc(happy, servant.greeting.address(player))
            when (choice3("What can you do?", 0, "Tell me about your previous jobs", 1, "You're hired!", 2)) {
                0 -> {
                    chatPlayer(quiz, "What can you do?")
                    servant.skills.forEach { chatNpc(neutral, it.address(player)) }
                }
                1 -> {
                    chatPlayer(quiz, "Tell me about your previous jobs.")
                    servant.history.forEach { chatNpc(neutral, it.address(player)) }
                }
                else -> hire(servant)
            }
        }
    }

    private suspend fun Dialogue.hire(servant: Servant) {
        if (player.constructionLvl < servant.level) {
            chatNpc(neutral, servant.tooLowLevel.address(player))
            return
        }
        if (servants.servantOf(player) != null) {
            mesbox("You already have a servant.")
            return
        }
        if (!servants.hasQuarters(store.state(player))) {
            mesbox("You need a second bedroom with a bed for your servant to sleep in.")
            return
        }
        if (access.invCoinTotal() < servant.cost) {
            chatPlayer(
                neutral,
                "You're hired... well, I don't have the money on me, but can I owe you? I'm good for " +
                    "it, honest.",
            )
            chatNpc(neutral, servant.noMoney.address(player))
            return
        }
        if (!access.invTakeFee(servant.cost)) {
            return
        }
        player.servantType = servant.type
        player.servantTrips = 0
        chatPlayer(happy, "You're hired!")
        chatNpc(happy, servant.hired.address(player))
    }

    suspend fun ProtectedAccess.chiefTalk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Welcome to the Ardougne Domestic Service Agency!")
            val hired = servants.servantOf(player) != null
            val about = if (hired) "I'd like to dismiss my servant, please." else "Tell me about servants."
            when (choice3(about, 0, "Can I hire a servant?", 1, "Thank you.", 2)) {
                0 -> if (hired) dismiss() else aboutServants()
                1 -> canHire()
                else -> chatPlayer(happy, "Thank you.")
            }
        }
    }

    private suspend fun Dialogue.aboutServants() {
        chatPlayer(quiz, "Tell me about servants.")
        chatNpc(neutral, "Do you need help around your house? Our trained, efficient workers are ready to serve you.")
        chatNpc(
            neutral,
            "They can cook meals for you and your friends, make tea, serve drinks, greet guests, and " +
                "even take items to and from the bank for you.",
        )
        chatNpc(
            neutral,
            "The more expensive servants will be able to make trips to the bank more quickly. Some " +
                "of them can also go to the sawmill to change logs to planks for you.",
        )
        chatNpc(
            neutral,
            "Remember, you'll need to keep paying your servant. You pay them once when you hire " +
                "them, and then they'll periodically ask for wages. You can dismiss your servant at " +
                "any time.",
        )
    }

    private suspend fun Dialogue.dismiss() {
        chatPlayer(neutral, "I'd like to dismiss my servant, please.")
        chatNpc(worried, "Oh dear, are you sure?")
        if (choice2("Yes, I want to dismiss my servant.", true, "No, I'll keep my servant for now.", false)) {
            chatPlayer(neutral, "Yes, I want to dismiss my servant.")
            fire(player)
            chatNpc(neutral, "Very well, you no longer have a servant.")
        } else {
            chatPlayer(neutral, "No, I'll keep my servant for now.")
        }
    }

    private suspend fun Dialogue.canHire() {
        chatPlayer(quiz, "Can I hire a servant?")
        val state = store.state(player)
        when {
            !state.owned ->
                chatNpc(
                    neutral,
                    "Do you even own a house? These are strictly domestic servants; they won't " +
                        "follow you around the world!",
                )
            !servants.hasQuarters(state) ->
                chatNpc(
                    neutral,
                    "First you'll need to make sure you have a servant's room. A servant will " +
                        "automatically take the second-best bedroom, leaving the best for yourself, " +
                        "of course! So you will need to build two bedrooms in your house.",
                )
            servants.servantOf(player) != null ->
                chatNpc(
                    neutral,
                    "According to our records, one of our people is already working for you. If you " +
                        "want to hire a different one you will have to dismiss them.",
                )
            else ->
                chatNpc(
                    happy,
                    "Certainly! We have a number of servants here now looking for work. Why not have " +
                        "a chat to them and see which one you would like to hire?",
                )
        }
    }

    // ---------------------------------------------------------------------- in the house

    /**
     * The bell-pull and the house options' Call Servant: the servant comes to its owner. The house
     * options button only works once a bell-pull is built somewhere in the house.
     */
    suspend fun ProtectedAccess.call(fromBell: Boolean) {
        val servant = servants.servantOf(player)
        if (servant == null) {
            mes("You don't have a servant.")
            return
        }
        if (registry.active(player) == null || !registry.isInside(player)) {
            mes("You can only call your servant in your own house.")
            return
        }
        val hasBell = store.state(player).rooms.values.any { BELL_PULL in it.furniture }
        if (!fromBell && !hasBell) {
            mes("You need a bell-pull in your dining room to call your servant from here.")
            return
        }
        if (servants.isAway(player)) {
            mes("Your servant is busy running an errand.")
            return
        }
        val npc = servants.bring(player, player.coords)
        if (npc == null) {
            mes("Your servant needs a second bedroom with a bed before they will live in your house.")
            return
        }
        command(npc, servant, rang = true)
    }

    suspend fun ProtectedAccess.houseTalk(npc: Npc) {
        val owner = servants.ownerOf(npc)
        val servant = servants.servantOf(player)
        if (owner !== player || servant == null) {
            return
        }
        command(npc, servant, rang = false)
    }

    private suspend fun ProtectedAccess.command(npc: Npc, servant: Servant, rang: Boolean) {
        startDialogue(npc) {
            if (player.servantTrips >= Servant.TRIPS_PER_WAGE && !payWages(servant)) {
                return@startDialogue
            }
            chatNpc(quiz, (if (rang) "You rang, %sir%?" else "Yes, %sir%?").address(player))
            val last = lastErrand[player.key()]
            if (last != null && choice2(last.label(), true, "Something else...", false, title = "Repeat last task?")) {
                access.startErrand(servant, last)
                return@startDialogue
            }
            val options =
                buildList {
                    add("Serve..." to 4)
                    add("Go to the bank..." to 0)
                    if (servant.sawmillTrips) add("Go to the sawmill..." to 1)
                    add("Greet guests" to 2)
                    add("You're fired" to 3)
                }
            when (menu(options)) {
                0 -> bank(servant)
                1 -> chatNpc(neutral, servant.sawmill.address(player))
                2 -> greetGuests()
                4 -> serve(servant)
                else -> fireFromHouse(servant)
            }
        }
    }

    private suspend fun Dialogue.greetGuests() {
        chatPlayer(neutral, "Stay at the entrance and greet guests.")
        chatNpc(neutral, "Very good, %sir%.".address(player))
        val house = registry.active(player) ?: return
        servants.stationAt(player, registry.entranceOf(house))
    }

    /**
     * A guest has just come into [owner]'s house. A servant left to greet guests welcomes them, and
     * leads them to the owner when the owner is in another room. Only the butler's words are in
     * the transcripts, so every servant uses them; the welcome when the owner is already at hand
     * was written for this.
     */
    suspend fun ProtectedAccess.greetGuest(owner: Player) {
        val npc = servants.greeterOf(owner) ?: return
        val house = registry.active(owner) ?: return
        val ownerRoom = registry.roomAt(house, owner.coords).takeIf { registry.isInside(owner) }
        val leads = ownerRoom != null && ownerRoom !== registry.roomAt(house, npc.coords)
        startDialogue(npc) {
            if (!leads) {
                chatNpc(happy, "Welcome, %sir%.".address(player))
                return@startDialogue
            }
            val room = ownerRoom!!.type.label.lowercase()
            chatNpc(neutral, "${owner.displayName} is entertaining in the $room. Would %sir% care to follow me?".address(player))
            val stillHome = registry.active(owner) === house && registry.isInside(owner)
            if (stillHome && registry.houseAt(player.coords) === house) {
                access.telejump(owner.coords)
            }
        }
    }

    /** A guest talking to the servant can be shown out; true when they ask to be. */
    suspend fun ProtectedAccess.askToLeave(npc: Npc): Boolean {
        var leave = false
        startDialogue(npc) {
            chatNpc(quiz, "Can I help %sir%?".address(player))
            leave = choice2("Please show me out.", true, "No, thank you.", false)
        }
        return leave
    }

    // --------------------------------------------------------------------------- serving

    private suspend fun Dialogue.serve(servant: Servant) {
        val state = store.state(player)
        when (choice3("Tea", 0, "Dinner", 1, "Drinks", 2)) {
            0 -> serveTea(servant, state)
            1 -> serveDinner(servant, state)
            else -> serveDrinks(servant, state)
        }
    }

    private suspend fun Dialogue.serveTea(servant: Servant, state: HouseState) {
        val cup = Kitchen.teaCup(state)
        if (cup == null) {
            chatNpc(neutral, servant.noKitchen.address(player))
            return
        }
        chatNpc(happy, servant.brewing.address(player))
        delay(SERVING_DELAY)
        chatNpc(quiz, servant.milk.address(player))
        val tea =
            when (choice3("Yes please", cup.milky, "No thanks", cup.tea, "Actually I don't want any", null)) {
                null -> return
                cup.milky -> cup.milky.also { chatPlayer(happy, "Yes please.") }
                else -> cup.tea.also { chatPlayer(neutral, "No thanks.") }
            }
        handOver(player, tea)
    }

    private suspend fun Dialogue.serveDinner(servant: Servant, state: HouseState) {
        if (!Kitchen.canServeDinner(state)) {
            chatNpc(neutral, servant.noTable.address(player))
            return
        }
        chatNpc(happy, servant.dinner.address(player))
        delay(SERVING_DELAY)
        val house = registry.active(player)
        val diners = listOf(player) + house?.let { registry.guests(it, players) }.orEmpty()
        diners.forEach { handOver(it, servant.dish) }
    }

    private suspend fun Dialogue.serveDrinks(servant: Servant, state: HouseState) {
        chatPlayer(neutral, "Serve drinks please.")
        val drink = Kitchen.drink(state)
        if (drink == null) {
            chatNpc(neutral, servant.noBarrel.address(player))
            return
        }
        chatNpc(quiz, servant.drinks.address(player))
        if (choice2("Yes please", true, "No thanks", false)) {
            handOver(player, drink)
        }
    }

    /** Gives [obj] to [diner], who has to have the room for it. */
    private fun handOver(diner: Player, obj: String) {
        if (diner.inv.isFull()) {
            diner.mes("You don't have enough room to take that.")
            return
        }
        diner.invAdd(diner.inv, obj)
    }

    private suspend fun Dialogue.bank(servant: Servant) {
        if (choice2("Take something to the bank", true, "Bring something from the bank", false)) {
            chatPlayer(quiz, "Can you take something to the bank for me?")
            chatNpc(neutral, servant.banking.address(player))
            return
        }
        chatPlayer(quiz, "Can you fetch something from the bank for me?")
        val obj = pickFetchable() ?: return
        val amount = access.countDialog("Enter amount:").coerceAtMost(servant.capacity)
        if (amount <= 0) {
            return
        }
        access.startErrand(servant, Errand.Fetch(obj, amount))
    }

    /** Eleven materials, four to a page, the fifth option turning the page. */
    private suspend fun Dialogue.pickFetchable(): String? {
        val pages = Servant.FETCHABLE.chunked(FETCH_PAGE)
        var page = 0
        while (true) {
            val items = pages[page]
            val options = items.map { it.first to it.second } + ("More..." to MORE)
            val picked = menu(options)
            if (picked != MORE) {
                return picked
            }
            page = (page + 1) % pages.size
        }
    }

    private suspend fun Dialogue.fireFromHouse(servant: Servant) {
        val sure = choice2("Yes", true, "No", false, title = "Do you really want to fire your servant?")
        if (!sure) {
            return
        }
        chatPlayer(angry, "You're fired!")
        chatNpc(neutral, servant.fired.address(player))
        fire(player)
    }

    /** True once the wages are paid; false if the player put them off or fired the servant. */
    private suspend fun Dialogue.payWages(servant: Servant): Boolean {
        chatNpc(neutral, servant.wages.address(player))
        val pay = "Okay, here's ${"%,d".format(servant.cost)} coins."
        val options =
            buildList {
                if (access.invCoinTotal() >= servant.cost) add(pay to 0)
                add("I'll pay you later." to 1)
                add("You're fired!" to 2)
            }
        when (menu(options)) {
            0 -> {
                if (!access.invTakeFee(servant.cost)) {
                    return false
                }
                player.servantTrips = 0
                chatPlayer(neutral, pay)
                return true
            }
            1 -> chatPlayer(neutral, "I'll pay you later.")
            else -> {
                chatPlayer(angry, "You're fired!")
                chatNpc(neutral, servant.fired.address(player))
                fire(player)
            }
        }
        return false
    }

    private fun fire(player: Player) {
        servants.despawn(player)
        player.servantType = 0
        player.servantTrips = 0
        lastErrand.remove(player.key())
    }

    // --------------------------------------------------------------------------- errands

    /** Something used on the servant: logs for the sawmill, notes to un-note, or a deposit. */
    suspend fun ProtectedAccess.useOn(npc: Npc, item: ItemServerType) {
        val servant = servants.servantOf(player)
        if (servants.ownerOf(npc) !== player || servant == null) {
            return
        }
        val name = RSCM.getReverseMapping(RSCMType.OBJ, item.id)
        val plank = PlankType.forLogs(name)
        val uncert = ocUncert(item)
        val errand =
            when {
                plank != null && servant.sawmillTrips ->
                    Errand.Sawmill(plank, minOf(inv.count(name), servant.capacity))
                uncert.id != item.id -> Errand.Unnote(name, minOf(inv.count(name), servant.capacity))
                else -> Errand.Deposit(name, minOf(inv.count(name), servant.capacity))
            }
        startErrand(servant, errand)
    }

    private suspend fun ProtectedAccess.startErrand(servant: Servant, errand: Errand) {
        if (player.servantTrips >= Servant.TRIPS_PER_WAGE) {
            val npc = servants.npcOf(player) ?: return
            startDialogue(npc) { payWages(servant) }
            return
        }
        if (errand is Errand.Deposit && !deposit(errand)) {
            return
        }
        lastErrand[player.key()] = errand
        player.servantTrips++
        servants.sendAway(player)
        queue(TRIP_QUEUE, servant.tripTicks, errand)
    }

    private fun ProtectedAccess.deposit(errand: Errand.Deposit): Boolean {
        val id = errand.obj.asRSCM(RSCMType.OBJ)
        val before = inv.physicalCount(errand.obj)
        var left = minOf(errand.count, before)
        intoMainTab {
            for (slot in inv.indices) {
                val obj = inv[slot]?.takeIf { it.id == id } ?: continue
                if (left <= 0) {
                    break
                }
                val take = minOf(left, obj.count)
                if (!with(bankInv) { invDeposit(slot, take, inv) }) {
                    break
                }
                left -= take
            }
        }
        return inv.physicalCount(errand.obj) < before
    }

    /** A servant banks into the main tab whatever tab the owner last had open. */
    private inline fun ProtectedAccess.intoMainTab(block: () -> Unit) {
        val previous = selectedTab
        selectedTab = BankTab.Main
        try {
            block()
        } finally {
            selectedTab = previous
        }
    }

    /** The servant is back from [errand]: settle it and hand over whatever it brought. */
    suspend fun ProtectedAccess.finishErrand(errand: Errand) {
        val servant = servants.servantOf(player) ?: return
        val home = registry.active(player) != null && registry.isInside(player)
        if (!home) {
            // Nothing changes hands with the owner away: what was fetched stays in the bank, the
            // notes and logs in the inventory. The servant just goes home.
            registry.standingHouseOf(player)?.let { servants.bring(player, registry.entranceOf(it)) }
            return
        }
        val npc = servants.bring(player, player.coords) ?: return
        val line = settle(errand) ?: servant.returned.address(player)
        startDialogue(npc) { chatNpc(neutral, line) }
    }

    /** Hands over what [errand] brought; returns what the servant says instead when it could not. */
    private fun ProtectedAccess.settle(errand: Errand): String? =
        when (errand) {
            is Errand.Deposit -> "I have deposited the items in your bank."
            is Errand.Fetch -> {
                val stackable = ocType(errand.obj).stackable
                val room = if (stackable) errand.count else inv.freeSpace()
                val count = minOf(errand.count, invTotal(bank, errand.obj), room)
                val id = errand.obj.asRSCM(RSCMType.OBJ)
                val slot = bank.indexOfFirst { it?.id == id }
                when {
                    invTotal(bank, errand.obj) <= 0 || slot < 0 -> "You do not have any of those items in your bank."
                    count <= 0 -> "You have no room for the items."
                    !with(bankInv) { invWithdraw(slot, minOf(count, bank[slot]?.count ?: 0), inv) } ->
                        "I could not fetch those items."
                    else -> null
                }
            }
            is Errand.Unnote -> {
                val unnoted = RSCM.getReverseMapping(RSCMType.OBJ, ocUncert(ocType(errand.noted)).id)
                val count = minOf(errand.count, inv.count(errand.noted), inv.freeSpace())
                if (count <= 0 || invDel(inv, errand.noted, count).failure) {
                    "You have no room for the items."
                } else {
                    invAdd(inv, unnoted, count)
                    null
                }
            }
            is Errand.Sawmill -> {
                val plank = errand.plank
                val count = minOf(errand.count, inv.count(plank.logs), invCoinTotal() / plank.cost)
                when {
                    count <= 0 -> "You need the logs and the sawmill's fee for me to make planks."
                    !invTakeFee(count * plank.cost) -> "I could not make the planks."
                    invDel(inv, plank.logs, count).failure -> {
                        invAdd(inv, "obj.coins", count * plank.cost)
                        "I could not make the planks."
                    }
                    else -> {
                        invAdd(inv, plank.plank, count)
                        null
                    }
                }
            }
        }

    private fun ProtectedAccess.ocType(obj: String): ItemServerType =
        requireNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { obj }

    /** How the "Repeat last task?" menu names [this] errand. */
    private fun Errand.label(): String =
        when (this) {
            is Errand.Fetch -> "Fetch $count x ${itemName(obj)}"
            is Errand.Deposit -> "Bank $count x ${itemName(obj)}"
            is Errand.Unnote -> "Un-note $count x ${itemName(noted)}"
            is Errand.Sawmill -> "Sawmill $count x ${plank.label.lowercase()}"
        }

    private fun itemName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj

    /** Fills in a transcript line's `%sir%`, `%he%` and `%level%` for [player]. */
    private fun String.address(player: Player): String {
        val female = player.appearance.bodyType != 0
        return replace("%Sir%", if (female) "Madam" else "Sir")
            .replace("%sir%", if (female) "madam" else "sir")
            .replace("%he%", if (female) "she" else "he")
            .replace("%level%", player.constructionLvl.toString())
    }

    private fun Player.key(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    companion object {
        const val TRIP_QUEUE = "queue.poh_servant_trip"
        private const val FETCH_PAGE = 4
        private const val MORE = "more"
        private const val BELL_PULL = "bell_pull"
        private const val SERVING_DELAY = 2

        var Player.servantType by intVarBit("varbit.poh_servant_type")
        var Player.servantTrips by intVarBit("varbit.poh_servant_pay")
    }
}
