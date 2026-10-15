package org.rsmod.api.exchange

public enum class OwnerKind {
    Player,
    Bot,
}

public data class ExchangeOwner(val kind: OwnerKind, val id: Long) {
    override fun toString(): String = "${kind.name.lowercase()}:$id"

    public companion object {
        public fun player(characterId: Int): ExchangeOwner =
            ExchangeOwner(OwnerKind.Player, characterId.toLong())

        public fun bot(botId: Long): ExchangeOwner = ExchangeOwner(OwnerKind.Bot, botId)
    }
}

public enum class OfferSide {
    Buy,
    Sell,
}

public enum class OfferStatus {
    Active,
    Completed,
    Aborted,
    /** Finished and fully collected; no longer occupies a slot. */
    Closed,
}

/**
 * A single exchange offer. Buy offers escrow `quantity * price` coins on placement; sell offers
 * escrow the items. Everything owed back to the owner accumulates in [collectItems] /
 * [collectCoins] until collected, so the offer row alone is enough to settle it after a restart.
 */
public data class ExchangeOffer(
    val id: Long,
    val owner: ExchangeOwner,
    val slot: Int,
    val itemId: Int,
    val side: OfferSide,
    val quantity: Int,
    val price: Int,
    var filled: Int = 0,
    var coinsTraded: Long = 0,
    var taxPaid: Long = 0,
    var collectItems: Int = 0,
    var collectCoins: Long = 0,
    var status: OfferStatus = OfferStatus.Active,
    val clientRef: String? = null,
    val createdAt: Long,
    var updatedAt: Long = createdAt,
) {
    val remaining: Int
        get() = quantity - filled

    val isOpen: Boolean
        get() = status == OfferStatus.Active

    val hasCollectable: Boolean
        get() = collectItems > 0 || collectCoins > 0

    val occupiesSlot: Boolean
        get() = status != OfferStatus.Closed

    public fun snapshot(): ExchangeOffer = copy()
}

public data class ExchangeTrade(
    val id: Long,
    val itemId: Int,
    val quantity: Int,
    val price: Int,
    val buyOfferId: Long,
    val sellOfferId: Long,
    val executedAt: Long,
)

/** Every row a single exchange operation touched, persisted together in one transaction. */
public data class ExchangeMutation(
    val offers: List<ExchangeOffer>,
    val trades: List<ExchangeTrade>,
) {
    public fun isEmpty(): Boolean = offers.isEmpty() && trades.isEmpty()

    public operator fun plus(other: ExchangeMutation): ExchangeMutation =
        ExchangeMutation(offers + other.offers, trades + other.trades)

    public companion object {
        public val EMPTY: ExchangeMutation = ExchangeMutation(emptyList(), emptyList())
    }
}

public data class ExchangeLimits(
    val slotsPerOwner: Int = 8,
    val maxQuantity: Int = Int.MAX_VALUE,
    val maxOfferValue: Long = Int.MAX_VALUE.toLong(),
    val tradeHistoryPerItem: Int = 500,
)
