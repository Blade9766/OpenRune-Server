package org.rsmod.content.other.bots.meister

/**
 * One settled fill of one of his offers: what the exchange traded between two looks at the offer.
 * Several trades at different prices can fall into one fill, so [gross] is exact and the unit
 * price is its average. [net] is the cash effect: negative for a purchase, proceeds after the fee
 * for a sale. For a sale, [knownQuantity] units had a known cost [costBasis] and produced
 * [realized]; [unknownQuantity] units came from stock of unknown cost (his starting allocation)
 * and are left out of the profit. A [reversal] undoes an earlier fill the exchange lost in a
 * crash; it is a new record, never an edit of the old one.
 */
data class MeisterFill(
    val id: String = "",
    val time: Long = 0,
    val itemId: Int = 0,
    val buy: Boolean = true,
    val quantity: Int = 0,
    val gross: Long = 0,
    val fee: Long = 0,
    val net: Long = 0,
    val knownQuantity: Int = 0,
    val unknownQuantity: Int = 0,
    val costBasis: Long? = null,
    val realized: Long? = null,
    val offerId: Long = 0,
    val slot: Int = 0,
    val offerPrice: Int = 0,
    val offerTotal: Int = 0,
    val filledAfter: Int = 0,
    val reversal: Boolean = false,
) {
    val unitPrice: Long
        get() = if (quantity == 0) 0 else gross / kotlin.math.abs(quantity)
}

/** Stock of one item split into units of known total cost and units of unknown cost. */
data class CostBasis(val knownQuantity: Long = 0, val knownCost: Long = 0, val unknownQuantity: Long = 0) {
    val averageCost: Long?
        get() = if (knownQuantity > 0) knownCost / knownQuantity else null
}

/** Where fills go; [record] returns false for an id it already holds. Writes are durable when it returns. */
interface MeisterHistorySink {
    fun record(fill: MeisterFill): Boolean

    fun all(): List<MeisterFill>

    val trackingSince: Long
}

class InMemoryHistorySink(override val trackingSince: Long = 0) : MeisterHistorySink {
    private val fills = LinkedHashMap<String, MeisterFill>()

    override fun record(fill: MeisterFill): Boolean = fills.putIfAbsent(fill.id, fill) == null

    override fun all(): List<MeisterFill> = fills.values.toList()
}

/**
 * Weighted-average cost basis per item. Purchases add units at their gross cost; a sale takes
 * known- and unknown-cost units in proportion to how many of each he holds, and the known part is
 * costed at the current average. Units from his starting allocation have no known cost, so their
 * sales never count as profit.
 */
object CostBasisBook {
    fun buy(basis: CostBasis, quantity: Int, gross: Long): CostBasis =
        basis.copy(knownQuantity = basis.knownQuantity + quantity, knownCost = Math.addExact(basis.knownCost, gross))

    class Sale(val basis: CostBasis, val knownQuantity: Int, val unknownQuantity: Int, val cost: Long?)

    fun sell(basis: CostBasis, quantity: Int): Sale {
        val pool = basis.knownQuantity + basis.unknownQuantity
        val unknown =
            when {
                pool <= 0 -> quantity.toLong()
                else -> (quantity.toLong() * basis.unknownQuantity / pool).coerceAtMost(quantity.toLong())
            }
        var known = quantity - unknown
        if (known > basis.knownQuantity) {
            known = basis.knownQuantity
        }
        val unknownTaken = quantity - known
        val cost = if (known > 0) basis.knownCost * known / basis.knownQuantity else 0L
        val after =
            CostBasis(
                knownQuantity = basis.knownQuantity - known,
                knownCost = basis.knownCost - cost,
                unknownQuantity = (basis.unknownQuantity - unknownTaken).coerceAtLeast(0),
            )
        return Sale(after, known.toInt(), unknownTaken.toInt(), if (known > 0) cost else null)
    }
}

enum class HistorySide(val label: String) {
    All("All trades"),
    Buys("Buys"),
    Sells("Sells"),
}

enum class HistoryRange(val label: String, val millis: Long?) {
    Today("Today", 24 * 60 * 60_000L),
    Week("Last 7 days", 7 * 24 * 60 * 60_000L),
    Month("Last 30 days", 30 * 24 * 60 * 60_000L),
    All("All time", null),
}

enum class HistorySort(val label: String) {
    Time("Time"),
    Quantity("Quantity"),
    UnitPrice("Unit price"),
    Total("Net total"),
    Profit("Realized P/L"),
}

data class HistoryQuery(
    val search: String = "",
    val itemId: Int? = null,
    val side: HistorySide = HistorySide.All,
    val range: HistoryRange = HistoryRange.All,
    val sort: HistorySort = HistorySort.Time,
    val descending: Boolean = true,
)

data class HistorySummary(
    val fills: Int,
    val boughtQuantity: Long,
    val soldQuantity: Long,
    val spent: Long,
    val grossSales: Long,
    val fees: Long,
    val netProceeds: Long,
    val netCashFlow: Long,
    val realized: Long,
    val unknownBasisQuantity: Long,
)

object MeisterHistoryView {
    fun filter(fills: List<MeisterFill>, query: HistoryQuery, now: Long, name: (Int) -> String): List<MeisterFill> {
        val needle = query.search.trim().lowercase()
        val since = query.range.millis?.let { now - it }
        val matching =
            fills.filter { fill ->
                (query.itemId == null || fill.itemId == query.itemId) &&
                    (needle.isEmpty() || name(fill.itemId).lowercase().contains(needle)) &&
                    (since == null || fill.time >= since) &&
                    when (query.side) {
                        HistorySide.All -> true
                        HistorySide.Buys -> fill.buy
                        HistorySide.Sells -> !fill.buy
                    }
            }
        val comparator: Comparator<MeisterFill> =
            when (query.sort) {
                HistorySort.Time -> compareBy { it.time }
                HistorySort.Quantity -> compareBy { it.quantity }
                HistorySort.UnitPrice -> compareBy { it.unitPrice }
                HistorySort.Total -> compareBy { it.net }
                HistorySort.Profit -> compareBy(nullsFirst()) { it.realized }
            }
        val ordered = matching.sortedWith(comparator.thenBy { it.id })
        return if (query.descending) ordered.reversed() else ordered
    }

    fun summarize(fills: List<MeisterFill>): HistorySummary {
        var spent = 0L
        var gross = 0L
        var fees = 0L
        var net = 0L
        var realized = 0L
        var bought = 0L
        var sold = 0L
        var unknown = 0L
        for (fill in fills) {
            if (fill.buy) {
                spent += fill.gross
                bought += fill.quantity
            } else {
                gross += fill.gross
                fees += fill.fee
                net += fill.net
                sold += fill.quantity
                unknown += fill.unknownQuantity
            }
            realized += fill.realized ?: 0L
        }
        return HistorySummary(fills.size, bought, sold, spent, gross, fees, net, net - spent, realized, unknown)
    }

    private val CSV_HEADER =
        listOf(
            "time_utc", "fill_id", "offer_id", "item_id", "item", "type", "quantity", "unit_price", "gross", "fee",
            "net_cash", "known_cost_quantity", "unknown_cost_quantity", "cost_basis", "realized_pl", "reversal",
        )

    fun csv(fills: List<MeisterFill>, name: (Int) -> String): String {
        val lines = ArrayList<String>(fills.size + 1)
        lines += CSV_HEADER.joinToString(",")
        for (fill in fills) {
            lines +=
                listOf(
                    java.time.Instant.ofEpochMilli(fill.time).toString(),
                    fill.id,
                    fill.offerId.toString(),
                    fill.itemId.toString(),
                    quote(name(fill.itemId)),
                    if (fill.buy) "BUY" else "SELL",
                    fill.quantity.toString(),
                    fill.unitPrice.toString(),
                    fill.gross.toString(),
                    fill.fee.toString(),
                    fill.net.toString(),
                    fill.knownQuantity.toString(),
                    fill.unknownQuantity.toString(),
                    fill.costBasis?.toString() ?: "",
                    fill.realized?.toString() ?: "",
                    fill.reversal.toString(),
                ).joinToString(",")
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun quote(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""
}
