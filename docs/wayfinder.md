# Wayfinder teleports

Two mages run a free teleport network:

| NPC | Gameval (id) | Spawn | Ops |
|---|---|---|---|
| Archmage Vaelor, the Edgeville Wayfinder | `npc.wayfinder_vaelor` (65526) | 3088,3495 against Edgeville bank's west wall, 4 tiles north of its entrance | Talk-to, Teleport |
| Arcanist Elowen, the Lumbridge Wayfinder | `npc.wayfinder_elowen` (65527) | 3239,3222 in the open ground north of Lumbridge church, clear of its doors | Talk-to, Teleport |

Talk-to explains the network and offers the destinations; Teleport opens the window straight
away. Both mages share the same destinations.

Each mage wears cache body parts plus four custom models (`[gamevals.models]` 64100-64107):

- **Archmage Vaelor**: long-bearded wizard body in purple robes with gold trim and grey hair; a
  wizard hat with a bent tip (worn and chathead versions); a crystal staff with a larger, two-tone
  faceted blue crystal, gold claws and a wooden shaft; and "regalia": layered purple pauldrons with
  gold edges, a leather rune pouch with a purple rune, and a lavender diamond on the robe front.
- **Arcanist Elowen**: auburn-haired body in teal and dark-blue robes with silver trim; a bent-tip
  blue hat with a silver band; a new staff with a wooden shaft, gold fittings, a silver crescent and
  a faceted cyan orb; and teal regalia with silver edges, a pouch with a cyan rune and a pale-blue
  diamond.

The custom models are generated from cache parts by `tools/wayfinder-models`
(`gradlew :tools:wayfinder-models:buildWayfinderModels`, which reads `.data/cache/LIVE`, so run
`buildCache` first). It writes `pack/models/wayfinder_*.dat` and preview renders in
`build/wayfinder-models/`. Every added vertex takes the animation label of the nearest body vertex,
so the pieces move with the shoulders, hips, legs and hand they sit on. Particle effects around the
staffs are not implemented.

## Modules

| Path | What |
|---|---|
| `content/travel/wayfinder` | Registry, `WayfinderTravel` service, NPC/interface script, favourites/recents |
| `content/travel/wayfinder/pack` | NPC configs, view varbits, `interface.wayfinder` (1108), CS2, the destination dbtable, sprites |
| `.data/raw-cache/map/npcs/{edgeville,lumbridge}.toml` | The two spawns |

## The teleport window

`interface.wayfinder` (512x334) follows the menu design: "WAYFINDER TELEPORTS" over "OpenRune" in
the steel frame, and three columns.

- **Categories**: Towns & Cities, Skilling, Dungeons, Slayer, Bosses, Minigames, Wilderness, each a
  stone button with an icon (custom castle and skull-and-crossbones sprites, otherwise Jagex map
  icons). The active one is amber with gold edges; Wilderness is red.
- **Destinations**: a search bar ("Search destinations..."; click it and type, Backspace deletes,
  Delete clears, Enter or Escape stops), a heading naming the view, and a scrolling list. Each row
  has an item icon, the name (Wilderness rows add the level in red) and a star. Clicking the star
  only toggles the favourite; it is a separate click target from the row.
- **Details**: name, location preview, description, activity tags, Arrival / Requirements / Danger
  / Cost, and the gold Teleport button.
- **Bottom bar**: All destinations (category lists), Favourites, Recent, the footer and the mage's
  chathead.

Search narrows whichever view is showing by name, key, tags and keywords; if the selected
destination drops out of view, the first visible one is selected instead. The client only ever
sends a row index, a star index or the search text; the server rebuilds the rows itself from the
registry, the view varbits (`varbit.wayfinder_mode`, `_category`, `_selected`, temp) and the search
text, and maps the index back.

Favourites and the last 20 completed journeys are saved with the account as lists of destination
keys (persistent attributes `wayfinder_favourites` / `wayfinder_recent`), so they survive logout
and restarts, and keys of removed or disabled destinations are skipped and dropped. A destination
enters Recent only after a successful arrival; travelling there again moves it to the top.

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
for confirmation in an overlay naming the place and the risk; cancelling leaves the player where they
are. The cast plays the standard teleport animation and graphic, and the mage faces the player and
speaks an incantation. Travel is free (`cost` 0 on every row).

## Adding a destination

1. Add a `destination(...)` row in `pack/.../WayfinderDestinationsTable.kt` where it should appear
   in its category: key, name, category, tile, description, arrival label, tags, icon obj, and
   optionally danger, warning, quest, bot policy and keywords.
2. Add `dbrow.wayfinder_<key>` to `src/main/resources/gamevals.toml` (next free id after 61205).
3. Preview: the picture is sprite `9100 + slot`, where slot is the row's position in the table.
   Save a 160x74 PNG (at most 255 colours) as `pack/sprites/wayfinder_<key>.png` and list it in
   `pack/sprites/manifest.toml` as `wayfinder_<key> = <9100 + slot>`. Inserting a row shifts the
   slots after it, so renumber those manifest lines too.
   `tools/wayfinder-previews/capture.py` regenerates previews (all, or the keys you pass) by
   driving a logged-in dev client to each tile through the developer-tools MCP. Without a
   preview the window shows the row's icon model instead.
4. Run `gradlew buildCache` and `:content:travel:wayfinder:test`. `WayfinderCacheTest` fails if the
   arrival tile is blocked or boxed in, a quest or icon doesn't exist, a preview is missing or its
   id doesn't match the manifest, or a Wilderness row lacks a warning or is open to bots.

Keys are what players' favourites and recents point at, so never rename one; set `enabled = false`
to retire a destination. No dialogue, interface or bot code changes are needed.

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
