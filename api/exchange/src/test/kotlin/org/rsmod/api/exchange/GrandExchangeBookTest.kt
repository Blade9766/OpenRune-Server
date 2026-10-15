package org.rsmod.api.exchange

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.exchange.persistence.InMemoryExchangeStore

class GrandExchangeBookTest {
    private val seller = ExchangeOwner.bot(1)
    private val buyer = ExchangeOwner.player(7)
    private var now = 1_000L
    private val book = GrandExchangeBook(clock = { now })

    private fun place(owner: ExchangeOwner, side: OfferSide, qty: Int, price: Int, ref: String? = null) =
        book.place(owner, side, LOGS, qty, price, ref) as PlaceResult.Placed

    @Test
    fun `trade executes at the resting price and refunds the buyer the difference`() {
        val sell = place(seller, OfferSide.Sell, 100, 200).offer
        val buy = place(buyer, OfferSide.Buy, 40, 250)

        assertEquals(1, buy.mutation.trades.size)
        assertEquals(200, buy.mutation.trades.single().price)
        assertEquals(OfferStatus.Completed, buy.offer.status)
        assertEquals(40, buy.offer.collectItems)
        assertEquals(40L * 50, buy.offer.collectCoins)

        assertEquals(40, sell.filled)
        assertEquals(40L * (200 - 4), sell.collectCoins)
        assertEquals(40L * 4, sell.taxPaid)
        assertTrue(sell.isOpen)
    }

    @Test
    fun `best price fills first, then oldest offer`() {
        val expensive = place(seller, OfferSide.Sell, 10, 300).offer
        val oldCheap = place(ExchangeOwner.bot(2), OfferSide.Sell, 10, 200).offer
        val newCheap = place(ExchangeOwner.bot(3), OfferSide.Sell, 10, 200).offer

        place(buyer, OfferSide.Buy, 15, 300)

        assertEquals(10, oldCheap.filled)
        assertEquals(5, newCheap.filled)
        assertEquals(0, expensive.filled)
    }

    @Test
    fun `an owner never trades with itself`() {
        place(seller, OfferSide.Sell, 10, 100)
        val own = place(seller, OfferSide.Buy, 10, 100)
        assertTrue(own.mutation.trades.isEmpty())
        assertEquals(0, own.offer.filled)
    }

    @Test
    fun `slots are limited per owner`() {
        repeat(8) { place(seller, OfferSide.Sell, 1, 1000 + it) }
        assertEquals(PlaceResult.NoFreeSlot, book.place(seller, OfferSide.Sell, LOGS, 1, 5000))
    }

    @Test
    fun `the same listing reference cannot be placed twice`() {
        place(seller, OfferSide.Sell, 10, 100, ref = "bot:1:sale:0")
        val again = book.place(seller, OfferSide.Sell, LOGS, 10, 100, "bot:1:sale:0")
        assertEquals(PlaceResult.DuplicateRef, again)
        assertEquals(1, book.offers(seller).size)
    }

    @Test
    fun `invalid offers are rejected`() {
        assertEquals(PlaceResult.InvalidQuantity, book.place(seller, OfferSide.Sell, LOGS, 0, 5))
        assertEquals(PlaceResult.InvalidPrice, book.place(seller, OfferSide.Sell, LOGS, 5, 0))
        assertEquals(
            PlaceResult.ValueTooHigh,
            book.place(buyer, OfferSide.Buy, LOGS, Int.MAX_VALUE, 2),
        )
    }

    @Test
    fun `abort returns the unfilled escrow exactly once`() {
        val sell = place(seller, OfferSide.Sell, 100, 200).offer
        place(buyer, OfferSide.Buy, 30, 200)

        assertNotNull(book.abort(seller, sell.id))
        assertNull(book.abort(seller, sell.id))
        assertEquals(70, sell.collectItems)
        assertEquals(30L * 196, sell.collectCoins)
    }

    @Test
    fun `collection cannot pay out twice or more than owed`() {
        val sell = place(seller, OfferSide.Sell, 10, 200).offer
        place(buyer, OfferSide.Buy, 10, 200)

        val owed = book.collectable(seller, sell.id)!!
        assertEquals(10L * 196, owed.coins)
        assertNotNull(book.confirmCollect(seller, owed))
        assertNull(book.confirmCollect(seller, owed))
        assertNull(book.offer(sell.id))

        val other = place(seller, OfferSide.Sell, 5, 200).offer
        place(buyer, OfferSide.Buy, 5, 200)
        val greedy = Collectable(other.id, 0, 5L * 196 + 1)
        assertNull(book.confirmCollect(seller, greedy))
        val stranger = book.collectable(seller, other.id)!!
        assertNull(book.confirmCollect(buyer, stranger))
    }

    @Test
    fun `partial collection keeps the remainder owed`() {
        val sell = place(seller, OfferSide.Sell, 10, 200).offer
        place(buyer, OfferSide.Buy, 10, 200)
        val part = book.collectable(seller, sell.id, maxCoins = 500)!!
        assertEquals(500, part.coins)
        book.confirmCollect(seller, part)
        assertEquals(10L * 196 - 500, book.offer(sell.id)!!.collectCoins)
    }

    @Test
    fun `coins and items are conserved across random trading`() {
        val random = Random(42)
        val owners = (1L..6L).map { ExchangeOwner(if (it % 2 == 0L) OwnerKind.Bot else OwnerKind.Player, it) }
        var coinsEscrowed = 0L
        var itemsEscrowed = 0L
        repeat(500) {
            now += 1000
            val owner = owners.random(random)
            val side = if (random.nextBoolean()) OfferSide.Buy else OfferSide.Sell
            val qty = random.nextInt(1, 50)
            val price = random.nextInt(40, 120)
            when (val result = book.place(owner, side, LOGS, qty, price)) {
                is PlaceResult.Placed ->
                    if (side == OfferSide.Buy) coinsEscrowed += qty.toLong() * price else itemsEscrowed += qty
                is PlaceResult.Rejected -> {}
            }
            if (random.nextInt(5) == 0) {
                book.offers(owner).firstOrNull { it.isOpen }?.let { book.abort(owner, it.id) }
            }
        }
        val offers = book.allOffers()
        val tax = offers.sumOf { it.taxPaid }
        val coinsOwed = offers.sumOf { it.collectCoins }
        val coinsStillEscrowed =
            offers.filter { it.isOpen && it.side == OfferSide.Buy }.sumOf { it.remaining.toLong() * it.price }
        assertEquals(coinsEscrowed, coinsOwed + coinsStillEscrowed + tax)

        val itemsOwed = offers.sumOf { it.collectItems.toLong() }
        val itemsStillEscrowed = offers.filter { it.isOpen && it.side == OfferSide.Sell }.sumOf { it.remaining.toLong() }
        assertEquals(itemsEscrowed, itemsOwed + itemsStillEscrowed)
    }

    @Test
    fun `restart restores open offers, owed collections and id sequence`() {
        val store = InMemoryExchangeStore()
        val sell = place(seller, OfferSide.Sell, 100, 200)
        store.persist(sell.mutation)
        val buy = place(buyer, OfferSide.Buy, 25, 210)
        store.persist(buy.mutation)

        val snapshot = store.load(0)
        val restored = GrandExchangeBook(clock = { now })
        restored.load(snapshot.offers, snapshot.trades)
        restored.reserveIds(snapshot.nextOfferId, snapshot.nextTradeId)

        val restoredSell = restored.offer(sell.offer.id)!!
        assertEquals(25, restoredSell.filled)
        assertEquals(25L * 196, restoredSell.collectCoins)
        assertEquals(25, restored.offer(buy.offer.id)!!.collectItems)
        assertEquals(1, restored.recentTrades(LOGS).size)

        val next = restored.place(seller, OfferSide.Sell, LOGS, 1, 500) as PlaceResult.Placed
        assertTrue(next.offer.id > buy.offer.id)
    }

    @Test
    fun `closed offers are not reloaded and free their slot`() {
        val store = InMemoryExchangeStore()
        val sell = place(seller, OfferSide.Sell, 10, 200)
        store.persist(sell.mutation)
        store.persist(place(buyer, OfferSide.Buy, 10, 200).mutation)
        store.persist(book.confirmCollect(seller, book.collectable(seller, sell.offer.id)!!)!!)

        val restored = GrandExchangeBook()
        restored.load(store.load(0).offers, emptyList())
        assertFalse(restored.offers(seller).any { it.id == sell.offer.id })
        assertEquals(0, restored.freeSlot(seller))
    }

    @Test
    fun `tax follows the exchange rules`() {
        assertEquals(0, ExchangeTax.perItem(49))
        assertEquals(1, ExchangeTax.perItem(50))
        assertEquals(1, ExchangeTax.perItem(99))
        assertEquals(20, ExchangeTax.perItem(1000))
        assertEquals(5_000_000, ExchangeTax.perItem(Int.MAX_VALUE))
    }

    private companion object {
        const val LOGS = 1511
    }
}
