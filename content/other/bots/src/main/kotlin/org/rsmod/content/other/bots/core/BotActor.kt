package org.rsmod.content.other.bots.core

enum class BotDestination {
    Resource,
    SupplySource,
    Bank,
    Exchange,
}

sealed class TravelStatus {
    data object Arrived : TravelStatus()

    data object Moving : TravelStatus()

    data class Blocked(val reason: String) : TravelStatus()
}

sealed class GatherStatus {
    /** [progressed] marks work that yields nothing yet but is clearly advancing (a fight). */
    data class Working(val gained: Int, val progressed: Boolean = false) : GatherStatus()

    data object InventoryFull : GatherStatus()

    data object NoResource : GatherStatus()

    data object NeedsSupplies : GatherStatus()

    /** The work at this destination is finished (a slayer task is assigned or completed). */
    data object Done : GatherStatus()

    /** A bot that does not fight was hurt; it goes to the bank to rest. */
    data object Hurt : GatherStatus()

    data class Failed(val reason: String) : GatherStatus()
}

sealed class SupplyOutcome {
    data object NotNeeded : SupplyOutcome()

    data class Withdrawn(val count: Int) : SupplyOutcome()

    data object Unavailable : SupplyOutcome()

    data object NoSpace : SupplyOutcome()
}

sealed class DepositOutcome {
    data class Deposited(val items: Map<Int, Int>) : DepositOutcome()

    data object BankFull : DepositOutcome()
}

/**
 * The game-facing half of a bot. Every inventory/bank method is a single atomic transaction: it
 * either fully applies or changes nothing. Methods marked "at bank" are only called after
 * [travel] to [BotDestination.Bank] has returned [TravelStatus.Arrived].
 */
interface BotActor {
    val inventoryUsed: Int
    val inventoryCapacity: Int

    /** True while the character is mid-script (delayed or holding protected access). */
    val isBusy: Boolean

    /** Missing tool, equipment or level; the bot cannot work at all until fixed. */
    fun requirementProblem(): String?

    /** True while the character is carrying out its last click (chopping, walking to a fight). */
    val isActing: Boolean
        get() = false

    /** What the bot is working on right now and why it chose it, for debugging. */
    val focus: String?
        get() = null

    /** True when the bot must visit its site's supply source first (a slayer master for a task). */
    fun needsSourceVisit(): Boolean = false

    /** True when the bot can work on even though its supplies ran out (a rested fighter). */
    fun canWorkWithoutSupplies(): Boolean = false

    /** True when running out of supplies only means resting until [canWorkWithoutSupplies]. */
    fun restsWithoutSupplies(): Boolean = false

    /** True while a bot that does not fight is too hurt to go back to work. */
    fun needsRest(): Boolean = false

    /** True when the role consumes something (essence) the inventory has run out of. */
    fun needsSupplies(): Boolean

    fun travel(destination: BotDestination): TravelStatus

    fun gather(target: BotDestination): GatherStatus

    fun stopActivity()

    /** At bank: deposits everything except role tools and supplies. */
    fun depositGathered(): DepositOutcome

    /** At bank: tops up role supplies (essence). */
    fun withdrawSupplies(): SupplyOutcome

    fun bankCount(itemId: Int): Long

    /** At bank: withdraws up to [count] of [itemId] as notes; returns how many moved. */
    fun withdrawNoted(itemId: Int, count: Int): Int

    /** Noted plus unnoted [itemId] currently in the inventory. */
    fun held(itemId: Int): Int

    /** Noted [itemId] currently in the inventory. */
    fun heldNoted(itemId: Int): Int

    /** Removes exactly [count] of [itemId] from the inventory, or nothing. */
    fun takeHeld(itemId: Int, count: Int): Boolean

    /** Puts [count] of [itemId] back into the inventory as notes, or nothing. */
    fun returnHeld(itemId: Int, count: Int): Boolean

    /** At bank: credits items to the bank; false (and no change) when there is no room. */
    fun creditBank(itemId: Int, count: Int): Boolean

    /** At bank: credits up to [coins] to the bank and returns how many fit. */
    fun creditBankCoins(coins: Long): Long

    fun coins(): Long
}

interface BotLog {
    fun info(bot: BotRecord, message: String)

    fun warn(bot: BotRecord, message: String)

    fun error(message: String)
}
