# Grand Exchange

A server-wide order book (`api/exchange`) shared by players and bots
([bots.md](bots.md)). There is no client interface yet; players trade with
`::ge` (main game worlds only; ironmen are blocked).

```
::ge buy <qty> <price> <item>     coins are taken from your inventory now
::ge sell <qty> <price> <item>    noted or unnoted items from your inventory
::ge offers                       your slots and what you can collect
::ge collect                      collect items and coins into your inventory
::ge abort <slot>                 cancel an offer; collect the remainder
::ge price <item>                 recent average, lowest ask, highest bid
```

Item names are gameval names without the prefix (`oak_logs`, `iron_ore`) or a
numeric id.

## Rules

- 8 offer slots per owner. A buy offer escrows `qty * price` coins (max
  2,147,483,647); a sell offer escrows the items.
- Price-time priority: the best price fills first, then the oldest offer.
  Trades execute at the resting offer's price; a buyer who bid more is refunded
  the difference.
- Tax: 2% of the sale price per item, rounded down, capped at 5,000,000 per
  item, nothing under 50 gp. Taken from the seller's proceeds.
- An owner never trades with itself.
- The volume-weighted price guide uses the last 6 hours of trades, needs at
  least 3 trades and 100 items, and discards trades more than 50% from the
  median.

## Persistence

`exchange_offers` and `exchange_trades`, created on first use. Writes go
through one ordered writer (`SerialDbWriter`) and are retried until they
succeed; while a write is failing the exchange reports itself unavailable and
no new offers can be placed. Trade history older than 7 days is not loaded.

Player inventories are saved by the account pipeline, which runs at the end of
the tick when an inventory changes. A crash in the moment between an offer
being written and that save could leave a player with both the escrow and the
offer; bots do not have this window because their items and their offers are
written in one transaction.
