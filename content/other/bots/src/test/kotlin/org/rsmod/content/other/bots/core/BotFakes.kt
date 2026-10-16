package org.rsmod.content.other.bots.core

const val LOGS = 1511
const val ORE = 440
const val AXE = 1351
const val COINS = 995
const val NOTE_OFFSET = 100_000

val TEST_ITEMS: Map<String, Int> = mapOf("obj.logs" to LOGS, "obj.iron_ore" to ORE)

/** A character with an exact item ledger, so tests can prove nothing is created or lost. */
class FakeActor(
    val gatheredItem: Int = LOGS,
    val tools: Set<Int> = setOf(AXE),
    val supplyItem: Int? = null,
    val stackable: Set<Int> = setOf(COINS),
    /** Slayer-like: work needs a task from the supply source, [taskSize] items per task. */
    val taskSize: Int = 0,
) : BotActor {
    var taskLeft = 0
    var rested = false
    var hitpointsPercent = 100
    var sourceVisits = 0

    val inv = LinkedHashMap<Int, Int>()
    val bank = LinkedHashMap<Int, Long>()
    var travelTicks = 2
    var blocked = emptySet<BotDestination>()
    var resourcesAvailable = true
    var busy = false
    var bankFull = false
    var missingTool = false
    var position: BotDestination? = null
    var stopped = 0
    var acting = false
    var gatherCalls = 0

    private var travelling: BotDestination? = null
    private var travelProgress = 0

    override val inventoryCapacity: Int = 28

    override val inventoryUsed: Int
        get() = inv.entries.sumOf { (id, count) -> if (id >= NOTE_OFFSET || id in stackable) 1 else count }

    override val isBusy: Boolean
        get() = busy

    override fun requirementProblem(): String? = if (missingTool) "missing a tool" else null

    override fun needsSupplies(): Boolean = supplyItem != null && (inv[supplyItem] ?: 0) == 0

    override fun needsSourceVisit(): Boolean = taskSize > 0 && taskLeft == 0

    override fun canWorkWithoutSupplies(): Boolean = rested

    override fun needsRest(): Boolean = hitpointsPercent < 90

    override fun travel(destination: BotDestination): TravelStatus {
        if (destination in blocked) {
            return TravelStatus.Blocked("path to ${destination.name} is blocked")
        }
        if (position == destination) {
            return TravelStatus.Arrived
        }
        if (travelling != destination) {
            travelling = destination
            travelProgress = 0
        }
        if (++travelProgress >= travelTicks) {
            position = destination
            travelling = null
            return TravelStatus.Arrived
        }
        position = null
        return TravelStatus.Moving
    }

    override val isActing: Boolean
        get() = acting

    override fun gather(target: BotDestination): GatherStatus {
        gatherCalls++
        if (missingTool) {
            return GatherStatus.Failed("missing a tool")
        }
        if (hitpointsPercent < 50) {
            return GatherStatus.Hurt
        }
        val full = inventoryUsed >= inventoryCapacity
        if (taskSize > 0 && target == BotDestination.SupplySource) {
            sourceVisits++
            taskLeft = taskSize
            return GatherStatus.Done
        }
        if (taskSize > 0 && taskLeft == 0) {
            return GatherStatus.Done
        }
        if (target == BotDestination.SupplySource) {
            if (full) {
                return GatherStatus.InventoryFull
            }
            add(supplyItem!!, 1)
            return GatherStatus.Working(1)
        }
        if (supplyItem != null && !rested) {
            if (needsSupplies()) {
                return GatherStatus.NeedsSupplies
            }
            val essence = inv.remove(supplyItem) ?: 0
            add(gatheredItem, essence)
            return GatherStatus.Working(essence)
        }
        if (full) {
            return GatherStatus.InventoryFull
        }
        if (!resourcesAvailable) {
            return GatherStatus.NoResource
        }
        add(gatheredItem, 1)
        if (taskLeft > 0) {
            taskLeft--
        }
        return GatherStatus.Working(1)
    }

    override fun stopActivity() {
        stopped++
    }

    override fun depositGathered(): DepositOutcome {
        if (bankFull) {
            return DepositOutcome.BankFull
        }
        val moved = LinkedHashMap<Int, Int>()
        for ((id, count) in inv.toMap()) {
            if (id in tools || id == supplyItem) {
                continue
            }
            val base = if (id >= NOTE_OFFSET) id - NOTE_OFFSET else id
            bank[base] = (bank[base] ?: 0) + count
            moved[base] = (moved[base] ?: 0) + count
            inv.remove(id)
        }
        return DepositOutcome.Deposited(moved)
    }

    override fun withdrawSupplies(): SupplyOutcome {
        val supply = supplyItem ?: return SupplyOutcome.NotNeeded
        val stock = bank[supply] ?: 0
        if (stock <= 0) {
            return SupplyOutcome.Unavailable
        }
        val space = inventoryCapacity - inventoryUsed
        if (space <= 0) {
            return SupplyOutcome.NoSpace
        }
        val count = minOf(stock, space.toLong()).toInt()
        bank[supply] = stock - count
        add(supply, count)
        return SupplyOutcome.Withdrawn(count)
    }

    override fun bankCount(itemId: Int): Long = bank[itemId] ?: 0

    override fun withdrawNoted(itemId: Int, count: Int): Int {
        val moved = minOf(count.toLong(), bankCount(itemId)).toInt()
        if (moved <= 0) {
            return 0
        }
        bank[itemId] = bankCount(itemId) - moved
        add(itemId + NOTE_OFFSET, moved)
        return moved
    }

    override fun held(itemId: Int): Int = (inv[itemId] ?: 0) + (inv[itemId + NOTE_OFFSET] ?: 0)

    override fun heldNoted(itemId: Int): Int = inv[itemId + NOTE_OFFSET] ?: 0

    override fun takeHeld(itemId: Int, count: Int): Boolean {
        if (held(itemId) < count) {
            return false
        }
        var left = count
        for (id in listOf(itemId + NOTE_OFFSET, itemId)) {
            val have = inv[id] ?: 0
            val take = minOf(have, left)
            if (take > 0) {
                if (have == take) inv.remove(id) else inv[id] = have - take
                left -= take
            }
        }
        return true
    }

    override fun returnHeld(itemId: Int, count: Int): Boolean {
        add(itemId + NOTE_OFFSET, count)
        return true
    }

    override fun creditBank(itemId: Int, count: Int): Boolean {
        if (bankFull) {
            return false
        }
        bank[itemId] = bankCount(itemId) + count
        return true
    }

    override fun creditBankCoins(coins: Long): Long {
        val room = Int.MAX_VALUE - bankCount(COINS)
        val credited = minOf(room, coins)
        bank[COINS] = bankCount(COINS) + credited
        return credited
    }

    override fun coins(): Long = bankCount(COINS) + (inv[COINS] ?: 0)

    fun add(id: Int, count: Int) {
        if (count > 0) {
            inv[id] = (inv[id] ?: 0) + count
        }
    }

    /** Every unit of [itemId] the bot owns, wherever it is. */
    fun total(itemId: Int): Long = bankCount(itemId) + held(itemId)

    fun toSave(): BotSave =
        BotSave(
            inv = inv.entries.mapIndexed { slot, (id, count) -> SavedObj(slot, id, count) },
            bank = bank.entries.mapIndexed { slot, (id, count) -> SavedObj(slot, id, count.toInt()) },
        )

    fun restore(save: BotSave) {
        inv.clear()
        bank.clear()
        save.inv.forEach { inv[it.id] = it.count }
        save.bank.forEach { bank[it.id] = it.count.toLong() }
    }
}

class FakeSpawner(private val factory: (BotRecord) -> FakeActor) : BotSpawner {
    val actors = LinkedHashMap<Long, FakeActor>()
    var slotsAvailable = Int.MAX_VALUE

    override fun spawn(record: BotRecord): BotActor? {
        if (actors.size >= slotsAvailable) {
            return null
        }
        val actor = factory(record)
        actor.restore(record.save)
        actors[record.id] = actor
        return actor
    }

    override fun snapshot(botId: Long): BotSave? = actors[botId]?.toSave()

    override fun despawn(botId: Long): BotSave? = actors.remove(botId)?.toSave()
}

class RecordingLog : BotLog {
    val lines = ArrayList<String>()

    override fun info(bot: BotRecord, message: String) {
        lines += "${bot.name}: $message"
    }

    override fun warn(bot: BotRecord, message: String) {
        lines += "WARN ${bot.name}: $message"
    }

    override fun error(message: String) {
        lines += "ERROR $message"
    }
}
