# Gathering bots

Server-driven characters that gather a resource, bank it, and sell it on the
Grand Exchange ([grand-exchange.md](grand-exchange.md)). Each bot is a real
in-world player: it walks with the normal pathfinder, clicks trees, rocks and
altars through the same interaction objects a client packet creates, and is
subject to every level, tool, inventory, depletion and respawn rule the skill
scripts already enforce. Other players can see it and compete with it.

## Modules

| Path | What |
|---|---|
| `api/exchange` | Order book, tax, price guide, ordered DB writer, exchange tables |
| `content/other/grand-exchange` | `::ge` player commands |
| `content/other/bots` | Bot coordinator, state machine, roles, `::bot` commands |
| `content/other/bots/pack` | `bot_status` interface + init clientscript |
| `content/other/bots/src/main/resources/bot-sites.toml` | Work sites |

`core/` inside the bots module is pure logic (state machine, pricing, names,
persistence contract) and is what the unit tests exercise. `world/` adapts it
to the engine.

## Setup

1. Rebuild the cache so the status interface and its clientscript exist:
   `gradlew buildCache` (the pack registers `interface.bot_status = 1102` and
   `clientscript.bot_status_init = 45600`).
2. Start the server. Tables (`bots`, `bot_settings`, `exchange_offers`,
   `exchange_trades`) are created on first use; nothing to migrate by hand.
3. Walk each site you intend to use (see [Sites](#sites)) and fix its
   coordinates if your map differs.
4. Create bots, give them their kit, enable them, then turn the master toggle
   on.

All `::bot` commands need `ADMINISTRATOR` rights. Arguments are lower-cased by
the client, so names are matched case-insensitively.

## Commands

```
::bot                                   open the status interface
::bot list                              one line per bot
::bot status <bot>                      details (config, totals, last error)
::bot create <role> <site> [name]       role: woodcutting | mining | runecrafting
::bot enable|disable <bot>
::bot remove <bot> [force]              force also cancels open offers (forfeits them)
::bot clear <bot>                       clear an ERROR so it can run again
::bot master on|off                     master toggle for every bot
::bot max <n>                           maximum bots in the world at once
::bot set <bot> <key> <value>           see the table below
::bot give <bot> <item> [n] [inv|worn|bank]   bot must be disabled
::bot take <bot> <item> [n] [inv|worn|bank]   bot must be disabled
::bot level <bot> <stat> <level>        bot must be disabled
::bot sites [reload]                    list sites / re-read the override file
::bot here                              print your tile, for writing routes
```

`<bot>` is the numeric id or the bot's name (spaces written as `_`). Without a name, `create` picks a
random silly one ("Soggy Waffle", "Captn Turnip", ...); names never end in
"bot".

`::bot set` keys:

| Key | Meaning | Default |
|---|---|---|
| `threshold` | Banked units that trigger a sale (1000-2000) | 1000 |
| `quantity` | Units listed per sale (1..threshold) | 1000 |
| `selling` | `on` / `off` | on |
| `item` | Item to sell; must be gathered at the bot's site and allowed | first site product |
| `allow` / `disallow` | Explicit sale allow-list | the sale item |
| `refprice` | Configured reference price (`0` = use the market-price service) | unset |
| `adjust` | % applied to the base price (-50..50) | -1.0 |
| `minprice` | Minimum acceptable price, **net of tax** | 1 |
| `deviation` | Max % a trade average may differ from the reference before it is rejected | 50 |
| `window` / `mintrades` / `minvolume` | What counts as reliable trade data | 360 min / 3 / 100 |
| `timeout` | Minutes before an unsold listing is aborted | 60 |
| `retries` / `step` | Re-list attempts and % price step per attempt | 3 / 2.0 |
| `cooldown` | Minutes selling pauses after giving up or lacking a safe price | 30 |
| `interval` | Ticks between bot decisions (1-100) | 1 |
| `hourlimit` | Max items gathered per rolling hour (0 = none) | 0 |
| `essence` | Runecrafting essence source: `bank` or `mine` | bank |

## Configuration examples

Woodcutter at Varrock west, selling oak logs:

```
::bot create woodcutting varrock_west_trees
::bot give 1 mithril_axe
::bot level 1 woodcutting 30
::bot set 1 allow oak_logs
::bot set 1 item oak_logs
::bot set 1 disallow logs
::bot set 1 threshold 1500
::bot set 1 quantity 1000
::bot set 1 refprice 60
::bot set 1 minprice 45
::bot enable 1
::bot master on
```

Miner at the south-east Varrock mine, selling iron ore only:

```
::bot create mining varrock_east_mine Rusty Spork
::bot give rusty_spork rune_pickaxe
::bot level rusty_spork mining 41
::bot set rusty_spork allow iron_ore
::bot set rusty_spork item iron_ore
::bot set rusty_spork disallow copper_ore
::bot set rusty_spork threshold 2000
::bot set rusty_spork adjust -2
::bot enable rusty_spork
```

Earth runecrafter mining its own essence:

```
::bot create runecrafting earth_altar
::bot give 3 tiara_earth 1 worn
::bot give 3 rune_pickaxe
::bot level 3 runecrafting 9
::bot level 3 mining 30
::bot set 3 essence mine
::bot set 3 threshold 1000
::bot enable 3
```

With `essence bank` instead, stock its bank: `::bot give 3 blankrune_high 5000 bank`.
It pauses in `WAITING_FOR_SUPPLIES` when the bank runs dry and resumes when
essence appears.

A bot's stored configuration is JSON in `bots.record`, for example:

```json
{
  "role": "Woodcutting",
  "site": "varrock_west_trees",
  "sale": { "enabled": true, "item": "obj.oak_logs", "threshold": 1500,
            "quantity": 1000, "allowedItems": ["obj.oak_logs"] },
  "pricing": { "referencePrice": 60, "adjustmentPercent": -1.0, "minPrice": 45,
               "maxDeviationPercent": 50.0, "windowMinutes": 360, "minTrades": 3,
               "minVolume": 100, "cooldownMinutes": 30, "listingTimeoutMinutes": 60,
               "maxRetries": 3, "retryStepPercent": 2.0 },
  "essenceSource": "Bank", "actionIntervalTicks": 1, "maxGatheredPerHour": 0
}
```

## Sites

Sites are in `bot-sites.toml`. Copy it to `.data/bots/bot-sites.toml` to edit
without rebuilding; sites there replace bundled ones with the same key, and
`::bot sites reload` re-reads it. A site has a resource area, a bank area, an
exchange area, the products that may be gathered, and waypoint routes that
start next to the bank (`resource_route`, `exchange_route`, and for
runecrafting with mined essence `supply_route` + `supply_npc` + `supply_area`).
The pathfinder walks between waypoints, so keep them within ~40 tiles of each
other and off closed doors. **The bundled coordinates are a starting point and
must be walked in-game before use.**

## States

`DISABLED, IDLE, TRAVEL_TO_RESOURCE, GATHERING, TRAVEL_TO_BANK, BANKING,
PREPARE_SALE, TRAVEL_TO_EXCHANGE, LISTING, WAITING_FOR_SUPPLIES, ERROR`.

Every state has a budget (travel 500 ticks, no gathering progress 200 ticks,
no resources 300 ticks, stuck mid-action 100 ticks). A blown budget is retried
three times, then the bot goes to `ERROR`, recovers automatically up to three
times five minutes apart, and then waits for `::bot clear`. Missing tools,
levels, equipment, a full bank or no inventory space for supplies go straight
to `ERROR` without auto-recovery.

## Selling and pricing

At the bank, once the sale item's banked count reaches the threshold, the bot
withdraws `quantity` as notes, walks to the exchange and lists them. Price:

1. Volume-weighted average of recent exchange trades, if reliable (enough
   trades and volume in the window, outliers more than 50% from the median
   discarded) and within `deviation` of the reference.
2. Otherwise the reference: `refprice`, else the server's `MarketPrices`
   service (cache value).
3. Otherwise selling pauses for `cooldown` minutes.

Then `adjust` is applied, minus `step`% per retry, never below the price whose
after-tax proceeds reach `minprice`, and never below another seller's current
lowest ask (bots join the queue instead of undercutting). Unsold listings are
aborted after `timeout` and re-listed lower, at most `retries` times, then
selling pauses for `cooldown`. A bot keeps at most one open listing per item.
Proceeds and returned items are collected straight into the bot's bank on its
next bank visit. Only players buy from bots; there are no artificial buyers.

## Persistence and safety

- `bots` (one JSON row per bot, including inventory, equipment, bank, xp,
  totals and sale state) and `bot_settings` (master toggle, max active).
- Every bot write carries the bot's full item snapshot **and** the exchange
  rows the same action changed, in one transaction, through a single ordered
  writer shared with the exchange. Listing, collection and abort can therefore
  never be persisted half-done.
- Each listing carries an idempotency reference (`bot:<id>:sale:<seq>`); the
  exchange refuses a second offer with the same reference.
- Collections credit the bank first; the exchange then refuses to deduct more
  than is owed, so a repeated collection pays nothing.
- On restart, bots come back `DISABLED`/`ERROR` with their saved items and are
  respawned (if enabled and the master toggle is on) from `IDLE`. A bot still
  carrying noted sale items goes back to the exchange or the bank.
- Disabling never drops items: the bot finishes its current script step,
  stops, is saved, and leaves the world.
- Bots have negative account ids, so they never touch the account save path
  (`AccountRegistry` skips them) or the central session tables.

Logs: logger `bots` (gathering totals with each deposit, deposits, listings,
aborts, sales, errors, spawns) and `bots.admin` (who changed what).

## Limitations

- The game-facing code (`world/`, scripts, interface) could not be compiled or
  run in the environment it was written in; only `core/` and `api/exchange`
  were compiled and unit-tested. Expect a first in-game shakedown.
- Site coordinates and routes are unverified. Routes cannot cross closed doors.
- Bots are visible through a headless player-info avatar (`HeadlessSessions`);
  if RSProt rejects an avatar without a session the bot still works but is
  invisible (a warning is logged).
- Bot items from a crash between saves: up to ~100 ticks of freshly gathered
  items can be lost (never duplicated), since bots save on every bank, listing
  and collection, and otherwise every 100 ticks.
- The Grand Exchange has no client interface yet; players use `::ge`.
- Kit, levels and site changes require the bot to be disabled.
