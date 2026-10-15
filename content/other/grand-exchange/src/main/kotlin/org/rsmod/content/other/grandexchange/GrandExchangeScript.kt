package org.rsmod.content.other.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.exchange.ExchangeMutation
import org.rsmod.api.exchange.ExchangeOffer
import org.rsmod.api.exchange.ExchangeOwner
import org.rsmod.api.exchange.ExchangePriceGuide
import org.rsmod.api.exchange.GrandExchange
import org.rsmod.api.exchange.OfferSide
import org.rsmod.api.exchange.OfferStatus
import org.rsmod.api.exchange.OwnerKind
import org.rsmod.api.exchange.PlaceResult
import org.rsmod.api.exchange.PriceGuideSettings
import org.rsmod.api.exchange.PriceSignal
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.ironman.IronmanActivity
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.output.mes
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.type.cert
import org.rsmod.game.type.uncert
import org.rsmod.game.world.WorldType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Player access to the Grand Exchange through `::ge`. Escrow is taken from the inventory in the
 * same tick the offer is placed and given back if the exchange rejects it; collections are
 * credited to the inventory before the exchange deducts them, and the exchange refuses to deduct
 * more than is owed, so a repeated command can never pay out twice.
 */
class GrandExchangeScript
@Inject
constructor(
    private val exchange: GrandExchange,
    private val playerRegistry: PlayerRegistry,
    private val mapClock: MapClock,
) : PluginScript() {
    private var nextLoadAttempt = 0

    override fun ScriptContext.startup() {
        exchange.addListener(::notifyOwners)
        onEvent<GameLifecycle.LateCycle> { tryLoad() }
        onEvent<GameLifecycle.Shutdown> { exchange.shutdown() }
        onCommand("ge") {
            desc = "Grand Exchange: buy, sell, offers, collect, abort, price"
            invalidArgs = USAGE
            cheat { handle() }
        }
    }

    private fun tryLoad() {
        if (exchange.loaded || mapClock.cycle < nextLoadAttempt) {
            return
        }
        if (!exchange.ensureLoaded()) {
            nextLoadAttempt = mapClock.cycle + LOAD_RETRY_TICKS
        }
    }

    private fun Cheat.handle() {
        if (args.isEmpty()) {
            player.mes(USAGE)
            return
        }
        if (!canUseExchange(player)) {
            return
        }
        when (args[0]) {
            "buy" -> place(OfferSide.Buy)
            "sell" -> place(OfferSide.Sell)
            "offers" -> listOffers(player)
            "collect" -> collectAll(player)
            "abort" -> abort(args[1].toInt())
            "price" -> price(args.drop(1))
            else -> player.mes(USAGE)
        }
    }

    private fun canUseExchange(player: Player): Boolean {
        if (player.worldType != WorldType.DEFAULT) {
            player.mes("The Grand Exchange is only available on main game worlds.")
            return false
        }
        if (IronmanRestrictions.block(player, IronmanActivity.GRAND_EXCHANGE)) {
            return false
        }
        if (!exchange.available) {
            player.mes("The Grand Exchange is temporarily unavailable. Please try again later.")
            return false
        }
        return true
    }

    private fun Cheat.place(side: OfferSide) {
        val quantity = args[1].toInt()
        val price = args[2].toInt()
        val type = resolveItem(args.drop(3)) ?: return player.mes("No such item.")
        val base = uncert(type)
        if (quantity <= 0 || price <= 0) {
            player.mes("Quantity and price must be positive.")
            return
        }
        val owner = ExchangeOwner.player(player.characterId)
        if (exchange.book.freeSlot(owner) == null) {
            player.mes("You have no free Grand Exchange slots.")
            return
        }
        when (side) {
            OfferSide.Buy -> placeBuy(player, owner, base, quantity, price)
            OfferSide.Sell -> placeSell(player, owner, base, quantity, price)
        }
    }

    private fun placeBuy(
        player: Player,
        owner: ExchangeOwner,
        base: ItemServerType,
        quantity: Int,
        price: Int,
    ) {
        val total = quantity.toLong() * price
        if (total > Int.MAX_VALUE) {
            player.mes("That offer is worth too much.")
            return
        }
        if (player.invDel(player.inv, COINS, total.toInt()).failure) {
            player.mes("You need ${total.formatAmount} coins in your inventory for that offer.")
            return
        }
        when (val result = exchange.book.place(owner, OfferSide.Buy, base.id, quantity, price)) {
            is PlaceResult.Placed -> {
                exchange.commit(result.mutation)
                player.mes("Offer placed: buy ${quantity.formatAmount} x ${base.name} at ${price.formatAmount} each.")
            }
            is PlaceResult.Rejected -> {
                check(player.invAdd(player.inv, COINS, total.toInt()).success) {
                    "Could not refund $total coins to $player after a rejected offer."
                }
                player.mes(result.message)
            }
        }
    }

    private fun placeSell(
        player: Player,
        owner: ExchangeOwner,
        base: ItemServerType,
        quantity: Int,
        price: Int,
    ) {
        val noteId = noteIdOf(base)
        val noted = if (noteId != null) countOf(player, noteId) else 0
        val unnoted = countOf(player, base.id)
        if (noted.toLong() + unnoted < quantity) {
            player.mes("You don't have ${quantity.formatAmount} x ${base.name} to sell.")
            return
        }
        val fromNoted = minOf(noted, quantity)
        val fromUnnoted = quantity - fromNoted
        val taken =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                if (fromNoted > 0 && noteId != null) {
                    delete(inv = inv, obj = noteId, count = fromNoted)
                }
                if (fromUnnoted > 0) {
                    delete(inv = inv, obj = base.id, count = fromUnnoted)
                }
            }
        if (taken.failure) {
            player.mes("You don't have ${quantity.formatAmount} x ${base.name} to sell.")
            return
        }
        when (val result = exchange.book.place(owner, OfferSide.Sell, base.id, quantity, price)) {
            is PlaceResult.Placed -> {
                exchange.commit(result.mutation)
                player.mes("Offer placed: sell ${quantity.formatAmount} x ${base.name} at ${price.formatAmount} each.")
            }
            is PlaceResult.Rejected -> {
                val refundId = noteId ?: base.id
                check(player.invAdd(player.inv, refundId, quantity, strict = true).success) {
                    "Could not return $quantity x ${base.internalName} to $player after a rejected offer."
                }
                player.mes(result.message)
            }
        }
    }

    private fun listOffers(player: Player) {
        val offers = exchange.book.offers(ExchangeOwner.player(player.characterId))
        if (offers.isEmpty()) {
            player.mes("You have no Grand Exchange offers.")
            return
        }
        for (offer in offers) {
            player.mes(describe(offer))
        }
    }

    private fun collectAll(player: Player) {
        val owner = ExchangeOwner.player(player.characterId)
        var collectedAny = false
        for (offer in exchange.book.offers(owner)) {
            if (!offer.hasCollectable) {
                continue
            }
            val owed = exchange.book.collectable(owner, offer.id) ?: continue
            if (owed.coins > Int.MAX_VALUE) {
                player.mes("Slot ${offer.slot + 1}: too many coins to collect at once.")
                continue
            }
            val itemId = if (owed.items > 0) noteIdOrBase(offer.itemId) else -1
            val credited =
                player.invTransaction(player.inv) {
                    val inv = select(player.inv)
                    if (owed.items > 0) {
                        add(inv = inv, obj = itemId, count = owed.items)
                    }
                    if (owed.coins > 0) {
                        add(inv = inv, obj = COINS.asRSCM(RSCMType.OBJ), count = owed.coins.toInt())
                    }
                }
            if (credited.failure) {
                player.mes("Slot ${offer.slot + 1}: you need more inventory space to collect.")
                continue
            }
            val mutation = exchange.book.confirmCollect(owner, owed)
            checkNotNull(mutation) { "Collection for offer ${offer.id} rejected after crediting $player." }
            exchange.commit(mutation)
            collectedAny = true
        }
        player.mes(if (collectedAny) "You collect your Grand Exchange items." else "There is nothing to collect.")
    }

    private fun Cheat.abort(slotNumber: Int) {
        val owner = ExchangeOwner.player(player.characterId)
        val offer = exchange.book.offers(owner).firstOrNull { it.slot == slotNumber - 1 }
        if (offer == null || !offer.isOpen) {
            player.mes("You have no open offer in slot $slotNumber.")
            return
        }
        val mutation = exchange.book.abort(owner, offer.id) ?: return
        exchange.commit(mutation)
        player.mes("Offer aborted. Use ::ge collect to collect what is left.")
    }

    private fun Cheat.price(name: List<String>) {
        val type = resolveItem(name) ?: return player.mes("No such item.")
        val base = uncert(type)
        val guide =
            ExchangePriceGuide.volumeWeighted(
                exchange.book.recentTrades(base.id),
                System.currentTimeMillis(),
                PriceGuideSettings(),
            )
        val average =
            when (guide) {
                is PriceSignal.Reliable -> "${guide.price.formatAmount} gp (${guide.volume.formatAmount} traded)"
                is PriceSignal.Unavailable -> guide.reason
            }
        val ask = exchange.book.bestAsk(base.id)?.formatAmount ?: "none"
        val bid = exchange.book.bestBid(base.id)?.formatAmount ?: "none"
        player.mes("${base.name}: average $average, lowest sell offer $ask, highest buy offer $bid.")
    }

    private fun notifyOwners(mutation: ExchangeMutation) {
        if (mutation.trades.isEmpty()) {
            return
        }
        val touched = mutation.offers.filter { it.owner.kind == OwnerKind.Player }
        for (offer in touched) {
            val player = playerRegistry.findOnlineByCharacterId(offer.owner.id.toInt()) ?: continue
            val verb = if (offer.side == OfferSide.Buy) "bought" else "sold"
            val done = if (offer.status == OfferStatus.Completed) " Offer complete." else ""
            player.mes(
                "Grand Exchange: you have $verb ${offer.filled.formatAmount}/${offer.quantity.formatAmount} " +
                    "x ${itemName(offer.itemId)}.$done"
            )
        }
    }

    private fun describe(offer: ExchangeOffer): String {
        val verb = if (offer.side == OfferSide.Buy) "Buying" else "Selling"
        val state =
            when (offer.status) {
                OfferStatus.Active -> "${offer.filled.formatAmount}/${offer.quantity.formatAmount}"
                OfferStatus.Completed -> "complete"
                OfferStatus.Aborted -> "aborted"
                OfferStatus.Closed -> "closed"
            }
        val collect =
            if (offer.hasCollectable) {
                " - collect ${offer.collectItems.formatAmount} items, ${offer.collectCoins.formatAmount} gp"
            } else {
                ""
            }
        return "Slot ${offer.slot + 1}: $verb ${offer.quantity.formatAmount} x ${itemName(offer.itemId)} " +
            "at ${offer.price.formatAmount} ($state)$collect"
    }

    private fun resolveItem(words: List<String>): ItemServerType? {
        if (words.isEmpty()) {
            return null
        }
        val joined = words.joinToString("_")
        joined.toIntOrNull()?.let { return ServerCacheManager.getItem(it) }
        val id = runCatching { "obj.$joined".asRSCM(RSCMType.OBJ) }.getOrNull() ?: return null
        return ServerCacheManager.getItem(id)
    }

    private fun noteIdOf(base: ItemServerType): Int? =
        if (base.canCert) cert(base.internalName).id else null

    private fun noteIdOrBase(itemId: Int): Int {
        val type = ServerCacheManager.getItem(itemId) ?: return itemId
        return noteIdOf(type) ?: itemId
    }

    private fun countOf(player: Player, itemId: Int): Int =
        player.inv.objs.sumOf { if (it != null && it.id == itemId) it.count else 0 }

    private fun itemName(itemId: Int): String = ServerCacheManager.getItem(itemId)?.name ?: "item $itemId"

    private companion object {
        private const val COINS = "obj.coins"
        private const val LOAD_RETRY_TICKS = 500
        private const val USAGE =
            "Use ::ge buy|sell <qty> <price> <item>, ::ge offers, ::ge collect, " +
                "::ge abort <slot>, ::ge price <item>"
    }
}
