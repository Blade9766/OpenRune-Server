# Wayfinder teleports

Two mages run a free teleport network:

| NPC | Gameval (id) | Spawn | Ops |
|---|---|---|---|
| Archmage Vaelor, the Edgeville Wayfinder | `npc.wayfinder_vaelor` (65526) | 3088,3495 against Edgeville bank's west wall, 4 tiles north of its entrance | Talk-to, Teleport |
| Arcanist Elowen, the Lumbridge Wayfinder | `npc.wayfinder_elowen` (65527) | 3239,3222 in the open ground north of Lumbridge church, clear of its doors | Talk-to, Teleport |

Talk-to explains the network and offers the destinations; Teleport opens the window straight
away. Both mages share the same destinations.

## Modules

| Path | What |
|---|---|
| `content/travel/wayfinder` | Registry, `WayfinderTravel` service, NPC/interface script, favourites/recents |
| `content/travel/wayfinder/pack` | NPC configs, varps, `interface.wayfinder` (1108), CS2, the destination dbtable |
| `.data/raw-cache/map/npcs/{edgeville,lumbridge}.toml` | The two spawns |

## The teleport window

`interface.wayfinder` (512x334, steel border): categories on the left (Favourites, Recent, Towns
& Cities, Skilling, Dungeons, Slayer, Bosses, Minigames & Activities, Wilderness, and Search results
after a search), a scrolling destination list, and a details panel with the description, danger
level, Wilderness level, quest requirement (green when met, red when not), an Add/Remove favourite
button and the Teleport button. Search opens a chatbox prompt over the window and matches names,
keys and keywords.

The client only ever sends a row index; the server rebuilds the rows from the registry and the
player's view varbits (`varbit.wayfinder_category`, `varbit.wayfinder_selected`, temp) and maps the
index back itself. Favourites (`varp.wayfinder_favourites_1..4`, one bit per destination slot) and
the last four destinations (`varp.wayfinder_recent`) are permanent varps.

## Travel rules

Everything goes through `WayfinderTravel.travel`, used by both dialogue paths, the window and bots.
A request is checked when it is made and again after the 3-tick cast, right before the player moves:

- the key must name an enabled destination (unknown or disabled keys are refused);
- a wayfinder mage must be within 3 tiles on the same level;
- the player must not be dying or already mid-teleport (`actionDelay`, so button spam does nothing);
- quest requirements (under the server's quest requirement mode). Skill levels and membership
  are not checked: guild doors, course obstacles and dungeon gates apply their own on arrival;
- `PlayerTeleportValidator` with `TeleportType.Standard`: teleblock, the level 20 Wilderness limit and
  every other registered teleport hook.

Arrival tiles sit outside dungeon entrances, guild doors and boss lairs, so those gates still apply
on arrival. Any destination in the Wilderness, in the Wilderness category, or with `danger` 2+ asks
for confirmation in an overlay first; cancelling leaves the player where they are. The cast plays
the standard teleport animation and graphic, and the mage faces the player and speaks an incantation.
Logging out mid-cast cancels it before the player moves. Travel is free.

## Adding a destination

Add a row in `pack/.../WayfinderDestinationsTable.kt` with `destination(...)`, add its
`dbrow.wayfinder_<key>` to `src/main/resources/gamevals.toml` (next free id after 61205), then run
`gradlew buildCache` and `:content:travel:wayfinder:test`. `WayfinderCacheTest` fails if the
arrival tile is blocked or boxed in, if a quest name doesn't exist, or if a Wilderness row
lacks a warning or is open to bots. Give the row a new `slot` (1-127); never reuse or renumber one,
since favourites and recents are saved by slot. To retire a destination, set `enabled = false`.
No dialogue, interface or bot code changes are needed.

## Bots

A bot more than 64 tiles from its site (after a death respawn in Lumbridge, say) used to telejump
straight to its bank. Now it first walks to the nearest wayfinder within 64 tiles, asks for a
destination by key through the same `WayfinderTravel` service (bots never click the interface),
and walks its normal route from where it lands. The destination is the site's `wayfinder` key in
`bot-sites.toml` if set, else the bot-allowed destination nearest the site's bank that lands within
40 tiles of one of its routes.

Bots only use rows with `bot_allowed = true`; the table default is off for anything dangerous or
in the Wilderness. Each trip is bounded: 3 requests, 120 ticks to reach a mage, 20 ticks for a cast,
and a mage it can't reach or a destination that is refused is not retried on that trip. When the
budget runs out the bot falls back to the old telejump to its bank (logged under `bots`).

## Omitted candidates

Left out because their entrance or content doesn't work on this server yet: God Wars Dungeon (no
Trollheim entrance handler), Fremennik Slayer Dungeon and Brimhaven Dungeon (entrance passages land
on the wrong tile; no Saniboch fee), Kalphite Lair, Catacombs of Kourend, Smoke Dungeon, Revenant
Caves, Karamja Volcano (no entrance handlers), Duke Sucellus, Phantom Muspah, the Whisperer and the
Leviathan (no route to their instances), Vardorvis (Stranglewood not implemented), demonic gorillas
and tormented demons (no route), Chaeldar and the Cosmic altar (inside quest-locked Zanaris), and
minigames with no implementation (Pest Control, Barbarian Assault, Wintertodt, Tempoross, NMZ and
others). 
