package org.rsmod.api.exchange

/** Grand Exchange sale tax: 2% per item, rounded down, capped at 5m, nothing under 50gp. */
public object ExchangeTax {
    public const val RATE_PERCENT: Int = 2
    public const val EXEMPT_BELOW: Int = 50
    public const val CAP_PER_ITEM: Long = 5_000_000

    public fun perItem(price: Int): Long {
        if (price < EXEMPT_BELOW) {
            return 0
        }
        return minOf(price.toLong() * RATE_PERCENT / 100, CAP_PER_ITEM)
    }

    public fun total(price: Int, quantity: Int): Long = perItem(price) * quantity

    public fun netPerItem(price: Int): Long = price - perItem(price)
}
