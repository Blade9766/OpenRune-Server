# Gathering bots

Server-driven characters that gather a resource, bank it, and sell it on the
Grand Exchange (`content/other/grand-exchange`, the same book players trade on
through the GE interface). Each bot is a real
in-world player: it walks with the normal pathfinder, clicks trees, rocks and
altars through the same interaction objects a client packet creates, and is
subject to every level, tool, inventory, depletion and respawn rule the skill
scripts already enforce. Other players can see it and compete with it.

## Modules

| Path | What |
|---|---|
| `content/other/grand-exchange` | The Grand Exchange: order book, tax, guide prices, `.data/grand-exchange.json` |
| `content/other/bots` | Bot coordinator, state machine, roles, `::bot` commands |
| `content/other/bots/pack` | `bot_status` interface + init clientscript |
| `content/other/bots/src/main/resources/bot-sites.toml` | Work sites |

`core/` inside the bots module is pure logic (state machine, pricing, names,
persistence contract) and is what the unit tests exercise. `world/` adapts it
to the engine.

## Setup

1. Rebuild the cache so the status interface and its clientscript exist:
   `gradlew buildCache` (the pack registers `interface.bot_status = 1102` and
   `clientscript.bot_status_init = 45800`).
2. Start the server. Bots are kept in `.data/bots.json`, created on first use.
3. Walk each site you intend to use (see [Sites](#sites)) and fix its
   coordinates if your map differs.
4. Create bots, give them their kit, enable them, then turn the master toggle
   on.

All `::bot` commands need `ADMINISTRATOR` rights. Arguments are lower-cased by
the client, so names are matched case-insensitively.

## Bot Manager window

`::bot` opens the Bot Manager (administrators only; every click re-checks rights, and
every change is validated by the server exactly as the commands are). A navigation
column on the left switches between **Overview**, **Bot Roster**, **Master Meister**
(Buy Rules, Sell Rules, Schedule, Active Offers, Trade History) and **Settings**; the
Master Meister tabs above his tables are the same sections, so the two always agree.
The window keeps your section, filters, pages and selections while it refreshes and
when you reopen it. The layout is generated: edit
`content/other/bots/pack/tools/gen_bot_manager.py` and run it, never `bot_manager.if3`.

**Overview** - bot counts (total, in world, queued, errors), Master Meister's status,
cash, open offers and realized profit, and quick switches (bots master, trading,
pause buying, pause selling, his items).

**Bot Roster** - one row per bot: name, role, state, difficulty, inventory, open
offers and coins. A bot that is enabled but not yet in the world shows as *Queued*.
Search by name and filter by state, role and difficulty; Bulk acts on every bot the
filters show (enable, disable, difficulty, each asking first). Select a bot for its
details and actions: **Configure** (the bot's page, below), Enable/Disable,
Send home, Go to bot, Items, Remove (out of the world only, asks first). **Create**
opens the create page in the window: choose a role (each shows its sites and bots),
then a site (with the levels its starter kit sets and the bots already there), then
a name (a random one, **Reroll**, or **Name...** to type one) and **Create**. The bot
gets the site's starter kit, starts disabled and is selected in the roster. The
footer holds the bots master switch and **Max - / +** for how many bots may be in the
world at once.

**Settings** - the bots master switch, bots in world and every bot's difficulty (these
three apply at once), and Master Meister's whole-market safeguards (rare items,
refills, price margins, spending and quantity caps), which wait for Save Changes.

**Bot page** - Configure opens the selected bot's page inside the Bot Manager. Its
header shows its state, inventory, open offers and coins; **Roster** (the last tab) or
**Back to roster** in the footer returns. The footer also has Enable/Disable and Send
home (an admin tool: teleports a stuck bot to its bank and has it start afresh). Four tabs:

- **Behaviour**:

| Setting | Effect |
|---|---|
| Difficulty | Easy (4-10 tick reactions, decides every 2 ticks), Normal (1-4, every tick) or Hard (0-2, every tick). Changing reaction or speed by hand makes it Custom |
| Reaction | Random pause before each new click |
| Speed | How often it decides what to do; higher slows everything, walking too |
| Hourly limit | Most items gathered per hour (steps of 250; none = no limit) |
| Selling / Sell at | Whether it sells on the Grand Exchange, and the banked amount that triggers a trip |
| Restock | Buy its supplies on the exchange when the bank runs out |

- **Selling**: which product it sells, batch size, reference price (market or a fixed
  price), price adjustment, minimum price, max deviation, listing timeout, re-list
  retries and price cut, the pause after giving up, the trade data window, minimum
  trades and volume, and the restock batch.
- **Tools**: **Appearance** (the appearance editor, below; the bot must be in the
  world), and, out of the world only, set a skill level,
  **Give an item** (the item picker, below), apply the starter kit, clear an error,
  copy its behaviour settings to every other bot of its role, reset its settings
  (keeps what it sells) and view its items. Copy and Reset ask first.
- **Activity**: its latest decisions with when and why, and its last target.

Click a setting's name for an explanation. Greyed-out settings and buttons do not
apply to that bot (for example selling at a site with no exchange route) and say
why when clicked. Every change is saved at once and applies on the bot's next
decision; nothing waits for a respawn.

**Appearance editor** - Tools > Appearance opens the game's character designer for
the bot, titled "Bot Appearance: <name>". The bot is held still while it is open, and
every arrow, gender and pronoun change shows on the bot in the world at once. When the
bot is within 14 tiles (the client only draws players that close; use **Go to bot**
first) the camera swings round so the bot stands beside the window, and resets when it
closes. In fixed mode the window covers the whole view, so use resizable mode. **Randomize** picks a random look for the current
gender, **Reset** puts back the look it had when the designer opened, **Apply** saves
the look with the bot so it survives restarts and respawns, and **Cancel** or closing
the window undoes every change. Only the designer's own style lists are offered, and
saved looks are checked against them again when the bot spawns. Each opening and
change is logged on `bots.admin`. A bot nobody has styled keeps its random look
generated from its id.

**Item picker** - Give an item and Master Meister's **+ Add item** open it inside the
window. Click the search box and type: matches with icons appear as you type, eight
to a page. Click one to choose it; for Give, set the amount with - / + or Set... and
where it goes (Inventory, Worn or Bank), then **Give**. **Back** leaves without
changes.

**View items** shows the bot's inventory, worn items and bank, read-only. It is
live while the bot is in the world and shows its last save otherwise.

Bots have no teams, and there is no accuracy or aggression setting: they do skilling
work through the same clicks a player makes, so difficulty is how quickly and how
often they act.

## Commands

```
::bot                                   open the status interface
::bot list                              one line per bot
::bot status <bot>                      details (config, totals, last error)
::bot create <role> <site> [name]       role: woodcutting | mining | runecrafting | fishing | thieving | combat | slayer | smithing | smelting
::bot enable|disable <bot>
::bot remove <bot> [force]              force also cancels open offers (forfeits them)
::bot clear <bot>                       clear an ERROR so it can run again
::bot kit <bot>                         top the bot up to its site's starter kit (bot must be disabled)
::bot master on|off                     master toggle for every bot
::bot max <n>                           maximum bots in the world at once
::bot set <bot> <key> <value>           see the table below
::bot give <bot> <item> [n] [inv|worn|bank]   bot must be disabled
::bot take <bot> <item> [n] [inv|worn|bank]   bot must be disabled
::bot level <bot> <stat> <level>        bot must be disabled
::bot trace <bot>                       current focus and the last decisions with their reasons
::bot tele <bot> <x> <z> [level]        move a bot (to test how it finds its way back)
::bot die <bot>                         kill a bot (to test what it does after a death)
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
| `item` | Main item to sell, checked first; must be gathered at the bot's site and allowed | first site product |
| `allow` / `disallow` | Explicit sale allow-list; `allow all` adds every product of the site | every site product |
| `refprice` | Configured reference price (`0` = use the market-price service) | unset |
| `adjust` | % applied to the base price (-50..50) | -1.0 |
| `minprice` | Minimum acceptable price, **net of tax** | 1 |
| `deviation` | Max % a trade average may differ from the reference before it is rejected | 50 |
| `window` / `mintrades` / `minvolume` | What counts as reliable trade data | 360 min / 3 / 100 |
| `timeout` | Minutes before an unsold listing is aborted | 60 |
| `retries` / `step` | Re-list attempts and % price step per attempt | 3 / 2.0 |
| `cooldown` | Minutes selling pauses after giving up or lacking a safe price | 30 |
| `interval` | Ticks between bot decisions (1-100) | 1 |
| `reaction` | Random pause before each new click, as `min-max` ticks (0-20) | 1-4 |
| `hourlimit` | Max items gathered per rolling hour (0 = none) | 0 |
| `essence` | Runecrafting essence source: `bank` or `mine` | bank |
| `restock` | Buy supplies on the exchange when the bank runs out: `on` / `off` | on |
| `restockqty` | Supplies bought per restock (1-100000) | 500 |

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
::bot give 3 mithril_pickaxe
::bot level 3 runecrafting 9
::bot level 3 mining 30
::bot set 3 essence mine
::bot set 3 threshold 1000
::bot enable 3
```

`essence mine` uses Aubury's teleport, which needs Rune Mysteries; a bot set to mine its
own essence has the quest marked complete each time it spawns, whatever the quest policy.

With `essence bank` instead, stock its bank: `::bot give 3 blankrune_high 5000 bank`,
or let it buy essence itself (see [Restocking supplies](#restocking-supplies)).

A bot's stored configuration is the `config` of its entry in `.data/bots.json`, for example:

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
  "essenceSource": "Bank", "restock": { "enabled": true, "quantity": 500 },
  "actionIntervalTicks": 1, "maxGatheredPerHour": 0
}
```

## Sites

Sites are in `bot-sites.toml`. Copy it to `.data/bots/bot-sites.toml` to edit
without rebuilding; sites there replace bundled ones with the same key, and
`::bot sites reload` re-reads it. A site has a resource area, a bank area, an
exchange area, the products that may be gathered, and waypoint routes that
start next to the bank (`resource_route`, `exchange_route`, and for
runecrafting with mined essence `supply_route` + `supply_npc` + `supply_area`).
A `mining` site with a `supply_npc` mines the rune essence mine instead of rocks: its
`resource` is the area around that npc (Aubury's shop), and the bot teleports in
through the npc, mines until full, and leaves through the mine's exit portal.
The pathfinder walks between waypoints, so keep them within ~40 tiles of each
other and off closed doors. **The bundled coordinates are a starting point and
must be walked in-game before use.**

Sites with an `exchange_route` sell by default: bots created there start with
selling on. A site with no `exchange_route` does not sell: bots created there
start with selling off and keep their loot in the bank.

Bots never use free or admin teleports at work. Sites too far to walk to the
exchange (Catherby, Ardougne, Al Kharid, Zanaris, Shilo Village, Taverley Dungeon)
set `exchange_by_wayfinder` and travel like a player: with no wayfinder mage in
reach the bot casts the normal Home Teleport (the spellbook button, so the spell's
own checks, 30-minute cooldown and interruptible 24-tick cast apply) to Lumbridge, asks Arcanist Elowen for the
Grand Exchange, lists its items, walks to Archmage Vaelor at Edgeville and asks for
the site's `wayfinder` destination, then walks `return_route` (a bank-relative chain
ending where that destination lands) home. Their `exchange_route` is just the
exchange tile, which also marks the site as one that sells. The cosmic bots wear a
dramen staff and come home through the Lumbridge Swamp shed door.

```toml
exchange_route = [{ x = 3164, z = 3487 }]
exchange_by_wayfinder = true
wayfinder = "catherby"
return_route = []
```

### Bundled sites

| Site | Role | Bank | Gives | Sells by |
|---|---|---|---|---|
| `varrock_west_trees` | woodcutting | Varrock West | logs, oak logs | walking |
| `draynor_willows` | woodcutting (30) | Draynor | willow logs | walking |
| `edgeville_yews` | woodcutting (60) | Edgeville | yew logs | walking |
| `varrock_east_mine` | mining | Varrock East | copper, tin, iron ore | walking |
| `barbarian_village_coal` | mining (30) | Edgeville | coal | walking |
| `varrock_essence_mine` | mining (30) | Varrock East | pure essence | walking |
| `mining_guild_coal` | mining (60) | Mining Guild chest | coal | walking |
| `mining_guild_mithril` | mining (60) | Mining Guild chest | mithril ore | walking |
| `mining_guild_adamantite` | mining (70) | Mining Guild chest | adamantite ore | walking |
| `mining_guild_runite` | mining (85) | Mining Guild chest | runite ore | walking |
| `earth_altar` | runecrafting (9) | Varrock East | earth runes | walking |
| `air_altar` | runecrafting (1) | Falador East | air runes | walking |
| `mind_altar` | runecrafting (2) | Edgeville | mind runes | walking |
| `water_altar` | runecrafting (5) | Draynor | water runes | walking |
| `body_altar` | runecrafting (20) | Edgeville | body runes | walking |
| `fire_altar` | runecrafting (14) | Al Kharid | fire runes | wayfinder |
| `cosmic_altar` | runecrafting (27) | Zanaris | cosmic runes | wayfinder |
| `law_altar` | runecrafting (54) | Draynor | law runes | walking |
| `barbarian_village_fly` | fishing | Edgeville | trout, salmon | walking |
| `draynor_net` | fishing | Draynor | shrimps, anchovies | walking |
| `catherby_lobsters` | fishing (40) | Catherby | lobsters | wayfinder |
| `catherby_swordfish` | fishing (50) | Catherby | tuna, swordfish | wayfinder |
| `catherby_sharks` | fishing (76) | Catherby | sharks | wayfinder |
| `varrock_tea_stall` | thieving | Varrock East | cups of tea | walking |
| `ardougne_silk` | thieving (20) | Ardougne South | silk | wayfinder |
| `ardougne_gems` | thieving (75) | Ardougne South | uncut gems | wayfinder |
| `barbarian_village_fighters` | combat | Edgeville | bones | walking |
| `falador_cows` | combat | Falador East | cowhides | walking |
| `falador_chickens` | combat | Falador East | feathers, raw chicken | walking |
| `draynor_slayer` | slayer (Spria) | Draynor | bones, cowhides, feathers, furs | walking |
| `edgeville_dungeon_slayer` | slayer (Vannaka) | Edgeville | big bones, limpwurt roots | walking |
| `chaos_altar` | runecrafting (35) | Edgeville | chaos runes | walking |
| `nature_altar` | runecrafting (44, Agility 79) | Shilo Village | nature runes | wayfinder |
| `taverley_blue_dragons` | combat (75, Agility 70) | Falador West | dragon bones, ensouled dragon heads | wayfinder |
| `wilderness_green_dragons` | combat (70) | Edgeville | dragon bones, ensouled dragon heads | walking |
| `edgeville_dungeon_druids` | combat (40) | Edgeville | ensouled chaos druid heads, bones | walking |
| `catacombs_dagannoths` | combat (80) | Arceuus | ensouled dagannoth heads, bones | wayfinder |
| `catacombs_moss_giants` | combat (45) | Arceuus | ensouled giant heads, big bones | wayfinder |
| `blood_altar` | runecrafting (77, Mining 41, Crafting 38) | Arceuus | blood runes | wayfinder |
| `soul_altar` | runecrafting (90, Mining 41, Crafting 38) | Arceuus | soul runes | wayfinder |
| `varrock_bronze_arrowtips` | smithing (5) | Varrock West | bronze arrowtips | walking |
| `varrock_iron_arrowtips` | smithing (20) | Varrock West | iron arrowtips | walking |
| `varrock_steel_arrowtips` | smithing (35) | Varrock West | steel arrowtips | walking |
| `varrock_mithril_arrowtips` | smithing (55) | Varrock West | mithril arrowtips | walking |
| `varrock_adamant_arrowtips` | smithing (75) | Varrock West | adamant arrowtips | walking |
| `varrock_rune_arrowtips` | smithing (90) | Varrock West | rune arrowtips | walking |
| `edgeville_bronze_bars` | smelting (1) | Edgeville | bronze bars | walking |
| `edgeville_iron_bars` | smelting (15) | Edgeville | iron bars | walking |
| `edgeville_steel_bars` | smelting (30) | Edgeville | steel bars | walking |
| `edgeville_mithril_bars` | smelting (50) | Edgeville | mithril bars | walking |
| `edgeville_adamant_bars` | smelting (70) | Edgeville | adamantite bars | walking |
| `edgeville_rune_bars` | smelting (85) | Edgeville | runite bars | walking |

Runecrafting sites other than `earth_altar` start with 1,000 pure essence in the
bank and then buy essence on the exchange; run a `varrock_essence_mine` bot to
keep essence listed for them. `chaos_altar` walks from Edgeville over the
Wilderness ditch to the ruins in the level 9 Wilderness, where other players can
attack it. `nature_altar` banks in Shilo Village, walks out of the village's
open north-west side and climbs the Agility 79 rocks shortcut up to the ruins
(its kit gives the Agility). `law_altar` banks at Draynor, walks to the Port
Sarim jetty and sails to Entrana by talking to the monks, carrying nothing but
essence and the law tiara so the search lets it aboard; it sails back with the
Entrana monks.

`blood_altar` and `soul_altar` make their own essence, as players do: the bot chips
dense essence at the Arceuus runestones (the site's `supply_area`) until its inventory
is full, walks `craft_route` from there past the Dark Altar, venerates the blocks and
chisels them into fragments on the spot, then binds the fragments at the open-air
altar. Their kits give a worn rune pickaxe, a chisel and the levels. It banks the
runes at Arceuus between trips (keeping the pickaxe, chisel, blocks and fragments) and
never takes essence from the bank, whatever its essence setting. The clicks are the
player's own: chipping, venerating and chiselling run the dense-essence scripts with
their levels, depletion and fragment cap.

These altars have no site:

- **Death**: the only way in is the Temple of Light puzzle under the Mourner
  tunnels.
- **Wrath**: the ruins are in the Myths' Guild basement; Dragon Slayer II and the
  guild are not implemented.
- **Astral**: Lunar Isle is not reachable for bots.

Every site can carry a starter kit, applied when a bot is created there and by
`::bot kit` / Manage > Apply starter kit. It only tops up: items already there
are not duplicated and higher levels are not lowered.

```toml
kit = { inv = [{ item = "obj.fly_fishing_rod" }], bank = [{ item = "obj.feather", count = 1000 }], levels = { fishing = 30 } }
```

Fishing, thieving and combat sites also use:

| Field | Meaning |
|---|---|
| `targets` | What the bot clicks: `npc.x` / `loc.x` types, `content.x` groups, or `name:Display name` |
| `target_op` | Which op of a target (1-5): fishing spot op, `2` for a stall's Steal-from or an npc's Attack |
| `tools` | The bot needs one of these carried or worn; never banked |
| `supplies` | Withdrawn from the bank before a trip, best first (bait, feathers, food) |
| `supply_amount` | How many supplies to carry; `0` fills the free inventory |

Roles:

- **fishing**: clicks the nearest target spot; bait/feathers come from `supplies`.
  `barbarian_village_fly` (fly fishing, Edgeville bank) gives trout and salmon but
  uses feathers, so it pauses when the bank runs out. `draynor_net` (small net,
  Draynor bank) gives shrimps and anchovies and needs no supplies at all.
- **thieving**: steals from the nearest stocked stall and waits out emptied ones.
  `varrock_tea_stall` (Varrock East bank) gives cups of tea.
- **combat**: attacks the nearest target not already in combat, eats `supplies`
  below 50% Hitpoints, heads to the bank to restock when out of food, and picks
  up the site's `products` between fights, before fighting back against
  aggressive monsters (otherwise a constant stream of attackers would stop it ever looting). Food is optional: with none left in
  the bank the bot rests there ("Resting") until it is back to 90% Hitpoints and
  then fights on. A drop or monster it fails to reach three times is skipped. Use cheap gear: a bot that dies keeps
  only what the normal death rules allow. `barbarian_village_fighters` (Edgeville
  bank) collects bones.

- **slayer**: combat on tasks from the site's slayer master (`supply_npc`, reached
  through `supply_area`/`supply_route`). With no task, or a finished one, the bot
  walks to the master and is given a task. The site lists a `hunts` entry per
  task it can work: the task's lowercase name, an area, a route from the bank and
  what to loot. A task without a hunt, or whose hunt has had nothing to fight for
  250 looks, is cancelled at the usual slayer point cost (30, 100 with Mortimer); a
  bot that cannot afford that goes to `ERROR` rather than taking a free new task.
  The bot attacks whatever the slayer module counts for its task,
  so every kill counts. Slayer bots get the master's unlock quest (A Porcine of
  Interest for Spria) marked complete when they spawn. `draynor_slayer` uses
  Spria, who awards no points, so a task without a hunt parks the bot in `ERROR`.
  It hunts goblins and cows near Lumbridge, rats and spiders west of Lumbridge
  Castle, birds in Fred's chicken pen, zombies and skeletons in the Draynor Sewers
  (trapdoor north of the bank), scorpions in the Dwarven Mine (stairs east of
  Falador), sourhogs down Spria's Strange hole, ghosts upstairs in Draynor Manor
  (in and out through the back door, since the front doors only open from
  outside), the dog in the farm south of Falador, and dwarves, icefiends and dark
  bears around Ice Mountain (by the Monastery path west of Edgeville). Spria's
  other tasks have no hunt within walking reach: bats (Silvarea and dungeons),
  wolves (White Wolf Mountain, the desert past Shantay Pass), monkeys (Karamja),
  kalphites and lizards (the desert), minotaurs (Stronghold of Security, behind
  its question doors), crawling hands and banshees (Morytania), and cave bugs,
  cave crawlers and cave slimes (Lumbridge Swamp Caves, which need a light
  source). A bot given one of those parks until `::bot clear`. Details
  shows the current task. It sells at the Grand Exchange, walking north between
  Falador's east wall and the Draynor Manor fence to Barbarian Village and on
  past Edgeville (the River Lum blocks the direct way to Varrock). Each hunt's
  route also counts as a way home, so a bot can walk back to the master or bank
  from any hunt area.
  `edgeville_dungeon_slayer` takes tasks from Vannaka in Edgeville Dungeon (down the
  trapdoor south of Edgeville bank, north past the giant spiders and through the
  gate at 3103,9909) and hunts hill giants, reached through the brass-key house
  north of Barbarian Village and its ladder; its kit carries the brass key as a
  tool. The hill giants hunt also loots ensouled giant heads. Moss giants are only reachable through the pipe shortcut, so that task is
  re-rolled.

- **smithing**: smiths the site's product at the nearest anvil (`category.anvil`) in
  its resource area. It clicks the anvil with a full inventory of the product's bar
  and presses the product's button on the smithing interface, so the anvil script
  makes as many as the bars allow with the usual levels, timing and xp. The bar
  (taken from the cache recipe, listed in `supplies` for the Bot Manager) is
  withdrawn at the bank and bought on the exchange once the bank runs out; the
  hammer in `tools` is never banked. The `varrock_*_arrowtips` sites use the
  anvils south of Varrock West bank; their kits give a hammer, 270 bars and the
  Smithing level for the tier.

- **smelting**: smelts the site's product, a bar, at the nearest furnace
  (`category.furnace`) in its resource area, closing the loop between the mining
  bots that sell ore and the smithing bots that buy bars. The ores and coal come
  from the bar's `smithing_bars` cache row and are withdrawn as whole sets (9 iron
  ore and 18 coal for steel), so a trip never carries ore without its coal. The bot
  clicks Smelt on the furnace and picks its bar on the make menu (which also lists
  iron beside steel), so the furnace script runs with the usual levels, timing, xp
  and iron's 50% failure. Everything is banked; once the bank runs short the bot
  buys whichever part it lacks on the exchange. The `edgeville_*_bars` sites use
  the furnace east of Edgeville bank; their kits give the Smithing level and some
  starting ore.

Bones and ensouled heads come from these combat sites:

- `taverley_blue_dragons` leaves Falador through the north-west city gate, takes
  the Taverley gate and the ladder south of Taverley, and squeezes through the
  Agility 70 pipe to the blue dragons (dragon bones, ensouled dragon heads).
  Its kit is rune armour, an anti-dragon shield and sharks.
- `wilderness_green_dragons` crosses the ditch north of Edgeville to the green
  dragons in the level 13 Wilderness, with the same kit.
- `edgeville_dungeon_druids` fights the chaos druids north of Vannaka for
  ensouled chaos druid heads.

- `catacombs_dagannoths` and `catacombs_moss_giants` bank at Arceuus (the castle has
  no bank), walk south to King Rada's statue in the Kourend Castle courtyard and
  Investigate it to climb into the Catacombs of Kourend (1666,10050); they climb back
  out by the vine ladder. The dagannoths (levels 74 and 92) stand in the south pit,
  reached by the loop east past the jellies; the moss giants are just south of the
  arrival tile. Catacomb kills also roll ancient shards and dark totem pieces. The
  side holes on the surface only open once their vine has been climbed from inside
  (`varbit.cata_hole*`), so the bots use the statue. They sell through the wayfinder
  (`great_kourend` lands west of the castle).

Superior dragon bones have no site: they only drop from Vorkath (only his sleeping
form is spawned; Dragon Slayer II is not implemented) and frost dragons (Grimstone
dungeon has no entrance).

Dagannoth bones have no site either. They only drop from the Dagannoth Kings (one
per kill); ordinary dagannoths drop plain bones and ensouled dagannoth heads, which
`catacombs_dagannoths` covers. Waterbirth Island Dungeon is implemented
(`content/areas/misc/waterbirth-island`), and the way in is within a bot's reach: a
`talk` step to Jarvald at the Rellekka pier, the cave mouth at 2523,3740, the
Agility 85 rocks and mountain cave that skip the sublevel 1 doors, and the ladder
chain to the Kings' ladder at 1911,4367. The fight is not. The three Kings share
one multi-combat lair and attack together, and each takes damage from one style
only (Rex magic, Prime ranged, Supreme melee), so a melee bot can only kill Supreme
while Rex and Prime keep hitting it at level 303 each. Players handle this with
protection prayers, switches and safespots; bots do not pray, switch styles or use
safespots, so a bot would burn its food and die every trip. A Kings site needs those
first (a protection-prayer and style-switch combat behaviour).

Bots play with auto-retaliate off: the engine's auto-retaliate turns a player back to
fight even while it walks, which kept bots from leaving aggressive monsters. Combat
bots fight back on their own (see **combat** above); the rest walk away.

Bots that do not fight (woodcutting, mining, runecrafting, fishing, thieving, smithing, smelting) run
to the bank when something drops them below 50% Hitpoints and rest there until
they are back to 90%. A bot that dies keeps only what the death rules allow; if
that loses its tool it stops with an error naming the tool, and Manage > Apply
starter kit (or `::bot kit`) puts it back. `draynor_net` has aggressive young dark
wizards (level 7) next to it, so its kit gives combat level 15: monsters ignore
players over twice their level.

A bot that is stuck on its route clicks "Open" on a closed door or gate within
two tiles of the straight line to its next waypoint (and at most 16 tiles away),
so it never wanders off to open doors that are not in its way. Each door it opens
counts as one of its five reroutes until it reaches the next waypoint, so a door
that keeps shutting in its face ends in a "stuck" failure instead of a loop. A route into a fenced area needs a waypoint on each
side of the gate and a resource area that lies wholly inside the fence. A waypoint
counts as reached two tiles away, but only when the bot can walk to it, so one just
past a closed gate is not ticked off through the fence. When the next waypoint cannot be
reached with doors as they are, the bot opens the door on the line to it before it
walks, rather than letting the pathfinder lead it to the nearest tile on the wrong side. A bot heading home retraces
the route it arrived by from the nearest waypoint it can walk to (ties go to the one
nearer the bank), so it leaves through the same gate, and a bot that has
drifted outside a pen does not walk back in first.

Routes can go up and down ladders and trapdoors. A waypoint with `climb` (the loc's
name) and `op` (its option) is used whenever the next waypoint is on another level
or more than 64 tiles away: the bot clicks the nearest such loc within three tiles,
clicking "Open" first on a closed trapdoor. A climb loc that is missing for now (a
door someone left open) is waited for, up to 600 ticks. Put a climb on the tile on each side,
so a bot walking the route in either direction climbs back the way it came:

```toml
resource_route = [{ x = 3094, z = 3478 }, { x = 3096, z = 3470, climb = "Trapdoor", op = "Climb-down" }, { x = 3096, z = 9868, climb = "Ladder", op = "Climb-up" }, { x = 3100, z = 9852 }]
```

Obstacles you cross on the same level (the Wilderness ditch, agility pipes and rocks)
work
the same way: two consecutive waypoints with the same `climb` name, one on each
side, make the bot click it instead of walking (names and options are those the
player sees, so multilocs work), and it carries on once it is
closer to the far tile than the near one:

```toml
{ x = 3087, z = 3519, climb = "Wilderness Ditch", op = "Cross" }, { x = 3087, z = 3523, climb = "Wilderness Ditch", op = "Cross" }
```

Boats and other travel that goes through a conversation use a `talk` waypoint: the
npc (a gameval or `name:`), its option (`op`, `Talk-to` by default) and the chat
menu lines to answer (`options`). It is used, like a climb, when the next waypoint
is on another level or more than 64 tiles away. The bot clicks the nearest such npc
within eight tiles and answers the conversation the way a client does, through the
same resume-pause-button input: it continues every chat and message page and, on
each chat menu, picks the first `options` line the menu offers (case, colour tags,
spacing and end punctuation ignored; a line may be the start of a longer option).
A menu offering none of them, or a conversation of more than 40 screens, closes the
dialogue and blocks the bot with the menu's lines in its error, so it never clicks
something it was not told to. Once the conversation has carried it away, the bot
carries on from the route waypoint nearest to where it landed, so each side of a
boat trip lists its own deck and the far side's deck is skipped:

```toml
{ x = 3048, z = 3234, talk = "npc.shipmonk", op = "Talk-to", options = ["Yes, okay, I'm ready to go."] },
{ x = 3048, z = 3231, level = 1, climb = "Gangplank", op = "Cross" },
{ x = 2834, z = 3331, level = 1, climb = "Gangplank", op = "Cross" },
{ x = 2834, z = 3335, talk = "npc.shipmonk2", op = "Talk-to", options = ["Yes, I'm ready to go."] }
```

Outside a `talk` step bots still close any dialogue that opens on them. The chat
menu's lines only ever go to the client (`chatbox_multi_init`), so every bot's client
is wrapped in a tap that keeps the last menu it was sent.

Each bot gets a random body type, clothing and colours, seeded by its id so it
keeps the same look across restarts. Styles come only from the original
character-creator ranges; the later ranges hold special models (a mine cart in the
leg slot).

Bots spawn with full run energy, switch run on at 50% energy and back to walking
at 15%, so they jog between sites and recover while they work.

## How bots play

- **Reaction time.** When a click finishes (a tree falls, a fight ends) the bot
  waits a random `reaction` delay before the next one, like a person noticing.
  `0-0` gives instant, robotic reactions; larger ranges make a bot slower.
- **Sharing resources.** A bot sees what other players near it are clicking.
  It picks the tree, rock or stall with the lowest walking distance plus 10
  tiles for each player already on it, so bots spread out and leave a rock
  a real player is mining alone. Equal choices are broken at random.
- **Fighting.** Fighters never click an npc someone else is fighting or has
  just clicked. A fighter that is hit fights back against its attacker before
  looting or walking to a new target, since anyone can see what is hitting them.
  Bots only know what a player standing there could see: nearby npcs, ground
  items and the clicks of other players in view.
- **Debugging.** `::bot trace <bot>` shows what the bot is focused on and why
  ("Oak at 3170,3416: nobody else on it, best of 6"), plus its last decisions
  with their reasons.

## States

`DISABLED, IDLE, TRAVEL_TO_RESOURCE, GATHERING, TRAVEL_TO_BANK, BANKING,
PREPARE_SALE, TRAVEL_TO_EXCHANGE, LISTING, BUYING_SUPPLIES, WAITING_FOR_SUPPLIES, ERROR`.

Every state has a budget (travel 1000 ticks, no gathering progress 200 ticks,
no resources 300 ticks, stuck mid-action 100 ticks). A blown budget is retried
three times, then the bot goes to `ERROR`, recovers automatically up to three
times five minutes apart, and then waits for `::bot clear`. Missing tools,
levels, equipment, a full bank or no inventory space for supplies go straight
to `ERROR` without auto-recovery.

## Selling and pricing

At the bank, every allowed item whose banked count has reached the threshold
(1,000 by default) and has no listing open yet is due: the bot withdraws
`quantity` of each as notes (as many items as it has free exchange slots), walks
to the exchange and lists them one after another. A bot at a site with several
products (trout and salmon, tuna and swordfish, gems) therefore sells each of
them once it has 1,000 banked. Bots created before this change only allow their
first product; `::bot set <bot> allow all` lets them sell the rest. Price:

1. Volume-weighted average of recent exchange trades, if reliable (enough
   trades and volume in the window, outliers more than 50% from the median
   discarded) and within `deviation` of the reference.
2. Otherwise the reference: `refprice`, else the Grand Exchange guide price
   (the wiki price shipped in `ge-items.tsv`; items without one have no
   reference).
3. Otherwise selling pauses for `cooldown` minutes.

Then `adjust` is applied, minus `step`% per retry, never below the price whose
after-tax proceeds reach `minprice`, and never below another seller's current
lowest ask (bots join the queue instead of undercutting). Unsold listings are
aborted after `timeout` and re-listed lower, at most `retries` times, then
selling pauses for `cooldown`. A bot keeps at most one open listing per item.
Proceeds and returned items are collected straight into the bot's bank on its
next bank visit. Only players buy from bots; there are no artificial buyers.
Bots trade under their negative account id, so they show up in no player's
offer screens and the exchange never tries to message them.

## Restocking supplies

When a bot's bank runs out of its site's `supplies` (feathers, bait, food, essence) it
restocks on the exchange itself instead of waiting for `::bot give`:

1. It buys `restockqty` (capped by the exchange buy limit) of the first supply in
   the site's list that another seller offers at a fair price (no more than
   `deviation`% over the guide price). The offer trades at the sellers' prices.
   When nobody offers any of its supplies fairly, it places a buy offer for the
   first one at the guide price and leaves it resting until a seller fills it or it
   times out (then it is aborted, refunded and placed again).
2. Without enough coins it first withdraws just enough of its sale item to pay for
   them, even below the sale threshold, and lists it like any sale (same pricing).
   It waits at the bank (`WAITING_FOR_SUPPLIES`, "Waiting for sales to afford
   supplies") and buys once the proceeds come in. With some coins but nothing more
   to sell it buys as many as it can afford.
3. Back at the bank it collects the supplies and goes back to work. A buy offer
   still unfilled after `timeout` is aborted and its coins refunded.

It keeps waiting in `WAITING_FOR_SUPPLIES`, rechecking every minute, with the reason
as its activity, while its buy offer rests, when its supplies have no guide price and
nobody sells them, it has no coins and nothing to sell, selling is paused or off, or
the exchange is unavailable. A fighter out of food rests while it waits. A bot whose
bank has had no supplies for an hour is parked: it aborts its supply buy offer, is
disabled with the activity "Parked: no supplies for 60 minutes; tries again in 2
hours" and a warning in the log, and frees its active slot for a bot that can work.
Two hours later it re-enables itself to try buying again (the wake time survives
restarts); disabling or enabling it by hand cancels the wake-up. `::bot set <bot> restock off`
restores the old behaviour of waiting for an administrator.

### Supply chain

Smiths, smelters and essence runecrafters buy their supplies on the exchange, so
they only work while other bots sell them: essence miners (`varrock_essence_mine`)
feed the runecrafters, ore and coal miners (`varrock_east_mine`,
`barbarian_village_coal` and the four Mining Guild sites) feed the smelters, and the
smelters feed the arrowtip smiths. Keep a miner or two per consumer; when a link is
missing the consumers park and retry every two hours. The Mining Guild sites bank at
the chest by the south rocks: coal and mithril sit north of the guild's inner door,
which their routes cross, and every guild trip to the exchange leaves by the ladder.
`essence mine` only works for runecrafting sites with a route to Aubury
(`earth_altar`); the others buy essence.

## Persistence and safety

Bots live in `.data/bots.json` (one entry per bot with inventory, equipment,
bank, xp, permanent varps (quest stages, slayer task), totals and sale state, plus the master toggle and max active),
rewritten through a temporary file and an atomic move. The exchange saves its
own book to `.data/grand-exchange.json` at the end of each cycle in which it
changed. Because the two files are written separately, every item movement
between a bot and the exchange is done in steps, each saved before the next:

- **Listing**: the notes leave the bot's inventory (for a supply buy offer, its
  coins leave the bank) and the listing is written to the bot file as pending. Next tick the offer is placed. Once the exchange
  file has been saved with the offer in it, the pending entry is cleared.
- **Collecting**: a collect box is only paid out once the exchange file has
  saved what is in it: with unsaved exchange changes the bot notes the box's
  contents when it first sees them and credits at most that much after the
  exchange's next save, so a fill or cancellation lost in a crash is never paid. Proceeds, bought supplies and
  returned items are then credited to the bot's bank
  and written as pending, with the offer's running collected totals as the
  target. Next tick the exchange is emptied down to that target, and the
  pending entry is cleared once the exchange file has saved that too.

On restart the exchange file decides what happened to anything still pending:
a listing whose offer is not in the book is rolled back into the bot's
inventory (a buy offer's coins into its bank); a collection takes from the exchange only what its target says is
still owed. A crash at any point therefore neither duplicates nor loses items
or coins. While the bot file cannot be written, bots leave the exchange alone
(the error is logged once) and resume when writes succeed again, which is logged too.
Writes to the bot file are serialized (the shutdown hook saves off the game thread),
each goes through its own temporary file, a move Windows refuses because another
program has the file open is retried, and a failed write changes nothing in memory.
A listing that cannot be rolled back (the bank has no room for the refund) keeps its
pending entry and puts that bot in `ERROR`, retrying every tick; other bots carry on.
Death and logout do not heal a bot: its current levels (Hitpoints, Prayer, boosts and
drains) are saved and restored as they were.

- On restart, bots come back `DISABLED`/`ERROR` with their saved items and are
  respawned (if enabled and the master toggle is on) from `IDLE`. A bot still
  carrying noted sale items goes back to the exchange or the bank.
- Disabling never drops items: the bot finishes its current script step,
  settles pending exchange work (up to 50 ticks), is saved, and leaves the
  world.
- Bots have negative account ids, so they never touch the account save path
  (`AccountRegistry` skips them) or the central session tables.

Logs: logger `bots` (gathering totals with each deposit, deposits, listings,
aborts, sales, errors, spawns) and `bots.admin` (who changed what).

## Master Meister

Master Meister is a single server-run player bot who lives around the Grand Exchange and trades on
the same book players use. Buying is his main activity; now and then he sells a little of
what he holds. Code: `content/other/bots/.../meister/` (pure trading logic, unit-tested) and
`meister/world/` (his player body, ledger file, commands).

**Wealth.** On his first start he is given an explicit starting allocation: 2,147,483,647 coins
(the largest coin stack, which is also the most any exchange offer can hold) and a stock of
every tradeable item - every entry of the exchange's item list with a guide price that the
cache marks tradeable and exchange-tradeable, minus coins, notes, placeholders, dummy and
transformation items, bonds and anything the config excludes (about 4,000 kinds). How many
of each depends on the guide price (2,000 of items up to 100 gp down to 1 of items over
10M). The allocation is written in the same save that marks him initialized, so a restart
never adds to it. After that, purchases spend his real cash and sales consume his real
items; nothing is topped up unless the `Refill cash` / `Refill items` switches are on (once
per 24 hours, logged as an allocation; coins and items sitting in his offers and collect
boxes count as held, so a refill never doubles them).

**Where his things live.** His cash and items are a ledger in `.data/master-meister.json`
(written like the bot file: temporary file and atomic move), not a player inventory - a bank
holds 1,410 slots and he holds thousands of kinds. His offers live in the exchange book under
owner id -1,000,000,000.

**Trading.** Buy checks and sell checks run on their own timers, every 90 seconds (+/- 50%)
each. A buy check places an offer 55% of the time, a sell check 10% of the time; otherwise
he does nothing that time. This section describes whole-market mode, the default; see
**Rule mode** below for trading only listed items.

- *Buying*: he looks at every open sell offer from other traders. A listing is eligible when
  its price is at most 5% over the item's reference price; he prefers cheaper listings,
  items that trade often, and items he holds fewer of than his starting stock, and picks one
  of the ten best at random. If nothing is listed at a fair price he may leave a standing
  bid 6% under the reference on a random item instead, which players can sell into. The
  quantity is random up to the smallest of: his cash, 5M per offer, 20M per item per day,
  100M per day (counting coins tied up in open bids), the exchange buy limit and 500 items.
- *Selling*: he prefers items someone is bidding for at no less than 98% of the reference,
  then items he holds most of. He sells at that bid, or else asks 5% over the reference;
  never below 98%. At most 10% of his stock of the item and 100 items per offer. Rare items
  (guide price 50M or more, or listed as `sensitive`) are never sold unless `Sell rare
  items` is on.
- *Reference price*: the exchange guide price, or the volume-weighted price of recent trades
  when that is within 25% of the guide price - the lower of the two when buying, the higher
  when selling, so trades between other players cannot pump what he pays or dump what he
  asks. Items with no guide price are never traded.
- *Safeguards*: at most 4 open offers and one offer per item (buy or sell), so his offers
  never meet each other (the exchange also refuses to match any server-run trader against
  itself). After an offer for an item finishes, fails or is cancelled he leaves that item
  alone for 30 minutes; an item he bought is not sold for 24 hours and the other way round.
  Buy offers unfilled after 30 minutes and sell offers unsold after 60 minutes are
  cancelled. Proceeds that would take his cash past the cap stay in the collect box until he
  has room.

Every trade goes through the exchange's normal matching, tax, buy limits and collection.
Item and coin moves use the gathering bots' two-step protocol: the ledger change and a
pending record are saved first, the exchange is changed on a later tick, and the pending
record is cleared once the exchange has saved too. He only collects what the exchange has
already saved, so a fill or cancellation the exchange loses in a crash is never paid out.

**In the world.** He is a player bot like the gathering bots: a player character named
Master Meister with no network session (others see him through the same bot avatar), spawned
at 3164,3480 and kept within 14 tiles of the exchange's centre. He carries nothing - his wealth
is the ledger - so leaving the world loses nothing. He walks with the normal pathfinder,
strolls a few tiles at a time, thinks, nods and calls out now and then, and when he places an
offer walks to the nearest Exchange booth, faces it and reaches over the counter (he does not
open the exchange interface; his offers go straight into the book like every bot's). A walk
that stalls is abandoned; three in a row, or leaving his area, have him walk back to his spawn
tile. Only if that walk fails too is he placed there, which is logged as a warning.
If the engine removes him he comes back within 100 ticks, at his last position.

**Bot Manager.** His sections in `::bot` share a header with his portrait, trading
status (Trading Active, Buying paused, Trading Paused...), mode (rule lists or whole
market), cash, open offers and realized profit. The footer shows whether buying and
selling are active, the next buy check, unsaved changes, and **Pause buying**,
**Discard** and **Save Changes**.

- **Buy Rules / Sell Rules** - his rule lists as searchable tables with item icons:
  max buy price or min sell price, offer quantity, holdings limit or reserve, cooldown
  and an Enabled tick. **+ Add item** searches every tradeable item in the item
  picker; **Bulk...**
  enables, disables or removes the rules shown (removing and clearing ask first).
  Selecting a rule opens its editor below the table: price, quantity, holdings limit
  (buy) or amount to keep (sell), cooldown, daily spend (buy) and price mode, with the
  largest offer's cost or proceeds and warnings (over the guide price, a buy ceiling at
  or over a sell floor that could loop at a loss, rules not in use because rule mode is
  off). Right-click > History opens that item's trade history.
- **Schedule** - trading, buying and selling switches (applied at once), rule mode,
  separate buy and sell check intervals with the time to the next check, variation,
  buy and sell chances, offer caps (total, buys, sells), item cooldown, no-flip window
  and offer timeouts.
- **Active Offers** - every exchange offer: item, side, price, filled / total and status.
  **Cancel** cancels it through the exchange (fills stay his, the rest comes back);
  **Cancel and replace** also re-offers the item on the saved rule once the box is
  collected; **Cancel all** asks first. Pausing only stops new offers.
- **Trade History** - see below.

Edits to rules and settings are a draft until **Save Changes** (Discard drops them;
Settings has **Reset defaults**). Saved rules apply to new offers; open offers keep
their terms. The trading, buying, selling and bot switches marked * apply at once.
If another administrator saved in the meantime, your edits are applied on top of
theirs; when both of you changed the same rule (or both changed settings) Save
Changes refuses, so neither edit is silently lost.
Every change is logged to `bots.admin`.

**Rule mode.** With **Rule lists only** on (Schedule) he trades only the enabled items on
his lists: a buy check picks a ready buy rule (enabled, not cooling down, no open offer
for the item, under its holdings limit counting open bids, not sold within the no-flip
window) and bids its ceiling (**Fixed ceiling**; the exchange fills at sellers' lower
prices and refunds the difference) or buys at the cheapest listing / bids under the
market, never over the ceiling (**Market, capped**). A sell check sells at most the
rule's quantity of what he holds above the reserve, asking the floor (**Fixed floor**)
or over the market (**Market, floored**). An empty or fully disabled list means no
trading in that direction. The global spending budgets, buy limits, offer caps, cash
cap and no-self-trading rules still apply; a rule's daily spend adds its own cap.
Editing rules never adds cash or items.

**Trade history.** Every settled fill is recorded when he counts it on the exchange:
time, item, buy or sell, quantity, unit price (the average when one look at the offer
covers several trades), gross, the exchange's fee, net cash and, for sales, the cost
basis and realized profit. It is appended to `.data/master-meister-history.jsonl` and
forced to disk; each fill's id comes from the exchange offer id and its filled count,
so a fill counted again after a crash is recognised and not written twice. A fill the
exchange loses in a crash gets a correction record (FIX) instead of an edit. Tracking
starts when the history file is created; earlier trades are not backfilled.

Cost basis is a weighted average per item. Purchases add units at what he paid; a sale
takes known-cost and unknown-cost units in proportion to how many of each he holds and
costs the known part at the current average. His starting allocation (and refills) has
no known cost: sales of it show **Unknown** and are left out of realized profit, with
the excluded quantity shown. Buying is never a loss: net cash flow (net sale proceeds
minus spending) is shown separately from realized profit. The tab shows Spent, Net
sales, Net cash flow and Realized P/L for every fill matching the filters (item search,
all/buys/sells, today / 7 days / 30 days / all time) - not just the page - and says so.
Sort by time, quantity, unit price, net total or realized profit. **Export CSV** writes
the filtered fills to `.data/exports/master-meister-trades-<time>.csv`.

**Commands** (administrators):

```
::meister [status]                status, budgets and lifetime totals
::meister offers | trace | items [n]
::meister keys                    every setting name
::meister set <key> <value>       e.g. ::meister set dayspend 50m, ::meister set sellchance 5
::meister trading|buying|selling on|off
::meister cancel                  cancel every open offer (contents are collected)
::meister home | reset
```

| Key | Default | Meaning |
|---|---|---|
| `interval` / `sellinterval` / `jitter` | 90 s / 90 s / 50% | Time between buy checks, between sell checks, and their random variation |
| `buychance` / `sellchance` | 55% / 10% | Chance a buy / sell check places an offer |
| `maxoffers` / `maxbuys` / `maxsells` | 4 / 3 / 2 | Open offers at once: total (1-8), buys, sells |
| `rules` | off | Trade only the enabled items on the rule lists |
| `premium` | 5% | Most he pays over the reference |
| `biddiscount` | 6% | Standing bids sit this far under the reference |
| `markup` / `minsell` | 5% / 98% | Asking price over the reference / lowest sale price |
| `deviation` | 25% | How far recent trades may differ from the guide price and still count |
| `offerspend` / `itemspend` / `dayspend` | 5M / 20M / 100M | Spending caps per offer, per item per day, per day |
| `buyqty` / `sellqty` / `sellshare` | 500 / 100 / 10% | Quantity caps |
| `cooldown` / `flip` | 30 min / 24 h | Item cooldown / no buy-then-sell window |
| `buytimeout` / `selltimeout` | 30 / 60 min | When unfilled offers are cancelled |
| `trading`, `buying`, `selling` | on | Switches |
| `buysensitive` / `sellsensitive` | on / off | Rare items |
| `replenishcash` / `replenishitems` | off | Daily refills |

**World config** (`content/other/bots/src/main/resources/master-meister.toml`; copy it to
`.data/bots/master-meister.toml` to change it without rebuilding, read at startup): `enabled`,
spawn tile, area, stroll radius, booth tiles and what each faces, animations and chat lines,
`starting_cash`, the allocation tiers and per-item `allocation_overrides`, `sensitive_price`,
`sensitive`, `exclude`, `no_buy` and `no_sell`. The allocation settings only matter before
his first save exists; delete `.data/master-meister.json` (with the server stopped, and after
cancelling his offers) to start him afresh.

Logs: logger `bots.meister` - every decision with its reason, every offer placed, confirmed,
filled, cancelled, collected or rolled back, and every allocation.

## Limitations

- The game-facing code (`world/` engine adapters, scripts, interface) could not
  be compiled or run in the environment it was written in; `core/`, the file
  store, the exchange adapter and the Grand Exchange book were compiled and
  unit-tested. Expect a first in-game shakedown.
- Every bundled site ran in game on 2026-10-15, and the sites added on 2026-10-16
  each completed a gather and bank trip in game; walk any new site first. Doors and
  gates are only opened when the bot gets stuck right next to one; this was seen
  working on the Lumbridge cow field gate (`draynor_slayer` cows) on 2026-10-15,
  as was the `draynor_slayer` exchange route.
- A bot more than 64 tiles from its bank and every route point (after a death respawn
  or any teleport) rides a wayfinder mage back towards its site (see `wayfinder.md`): it
  walks to the nearest Vaelor or Elowen within 96 tiles (far enough to walk from the Grand
  Exchange to Edgeville) and asks for the site's `wayfinder`
  destination key, or the bot-allowed destination nearest its bank. With no mage in reach it
  casts Home Teleport to Lumbridge and tries again. If that does not get it to a mage either,
  travel fails ("stranded") and the bot ends in `ERROR`; it is never placed anywhere.
- A bot that dies keeps what the death rules allow; kit gear it kept (a tiara, a weapon)
  lands in its inventory, and it puts it back on before going back to work. Worn kit gear
  it lost (an anti-dragon shield) is fetched from its bank if it has a spare there;
  otherwise the bot goes to `ERROR` instead of going back to die again.
- Home Teleport works as in OSRS for bots and players alike: a 24-tick cast that any click
  cancels, then a 30-minute cooldown shared by every spellbook's home teleport
  (`varp.aide_tele_timer`). A bot does nothing else while casting. While it recharges, a far
  site's bot with no mage in reach keeps gathering and banking its loot ("Home Teleport
  recharges in N min"), and starts the exchange trip once it is ready; a bot that already
  needs it (stranded, or mid-trip) waits where it is without counting it as a travel failure.
- Bots are restored with at least 10 Hitpoints.
- Bots are visible through the session-less player-info avatar
  (`BotSessions`) that castle-wars bots also use.
- Bot items from a crash between saves: up to ~100 ticks of freshly gathered
  items can be lost (never duplicated), since bots save on every bank, listing
  and collection, and otherwise every 100 ticks.
- Kit, levels and site changes require the bot to be disabled.
