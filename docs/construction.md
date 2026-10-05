# Construction

`content/skills/construction`: the sawmill, and the player-owned house.

## Sawmill

**Sawmill operators** (`npc.poh_sawmill_opp`, `npc.prif_sawmill_operator`,
`npc.auburn_sawmill_operator`). `Buy-plank` and using logs on the operator both open the same flow:
pick a plank, pick an amount, pay per plank. 100 / 250 / 500 / 1500 coins for normal, oak, teak and
mahogany.

## The house is an instanced region

A house is assembled at the moment its owner walks through a portal: one 8x8 zone copy per room into
a runtime small region (16x16 zones over four levels), followed by a pass that turns the raw template
into that owner's house. Nothing about a standing house is authoritative - `HouseState` is, and the
region is thrown away and rebuilt whenever the layout or the build mode changes. Rebuilding is also
what forces the client to receive a new scene, which it does because `Player.buildArea` is cleared
first.

The house grid is 13x13 cells, placed at region zones 1..13 so it never touches the region's border.
Floors map dungeon -> region level 0, ground -> 1, upper -> 2.

Every cell with no room in it is filled from the style block too, so a house stands on its plot
rather than floating in the void: each empty ground-floor cell in the owner's yard gets the block's
lawn (`HouseStyle.grassZone`, offset (1,880) - grass, sand for whitewashed stone, mud for Fremennik
and twisted, all 64 tiles walkable), and when the house has a dungeon, each empty dungeon cell gets the
block's solid rock (`rockZone`, offset (3,880), nothing walkable). Upper-floor cells stay empty.
`FillerTemplateTest` checks both for every style. A rebuild keeps a player standing on the lawn
where they are, as it does inside a room.

The yard is the building area (see "Rooms: levels and costs") with a ring of lawn one room wide
round it (`HouseLayout.inYard`): 5x5 at level 1, growing with the area to 9x9 at level 60, or 9x9
in free build. Beyond it there is nothing. The house is built for its owner's level when it is
opened (`ActiveHouse.size`), so a level-up shows the next time it is opened or rebuilt.

## Room templates

Templates live at `x 1792-2111, z 7040-7167`. Each of the four map squares holds a complete copy of
every room, and **the four styles that share a map square are stacked on its four levels** - so a
style is a template block plus a level, and picking one changes which zone every room is copied
from, walls, windows, doors and all.

| Style | Map square | Zone x | Level | Walls |
|---|---|---|---|---|
| Basic wood (Rimmington) | 29 | 232-239 | 0 | `loc.village_wall` |
| Basic stone (Lumbridge) | 29 | 232-239 | 1 | `loc.brickwall` |
| Whitewashed stone (Pollnivneach) | 29 | 232-239 | 2 | `loc.desertwall` |
| Fremennik (Rellekka) | 29 | 232-239 | 3 | `loc.viking_longhall_wall_inner` |
| Tropical wood (Brimhaven) | 30 | 240-247 | 0 | `loc.poh_timberwall` |
| Fancy stone (Yanille) | 30 | 240-247 | 1 | `loc.yanille_poh_wall` |
| Deathly mansion | 30 | 240-247 | 2 | `loc.deathly_poh_wall` |
| Twisted | 30 | 240-247 | 3 | `loc.twisted_poh_wall_plain` |
| Hosidius | 31 | 248-255 | 0 | `loc.hosidius_poh_wall` |
| Xmas 2020 | 31 | 248-255 | 1 | `loc.xmas2020_poh_wall` |
| Civitas illa Fortis | 31 | 248-255 | 2 | `loc.civitas_poh_wall_default` |
| Canifis | 31 | 248-255 | 3 | `loc.canifis_poh_wall_plain` |
| Deadman / wilderness | 32 | 256-263 | 0 | `loc.deadman_poh_wall` |

Each block also carries a **roof** zone, a whole 8x8 of roof pieces in that style. It is copied onto
the level above every indoor room with nothing built on top of it, which is what stops you seeing
into the top storey from the garden; the client lifts it again when you walk in under it. Gardens are
open to the sky and get none.

A hall is authored twice. The second copy - `stairs top` - is the same room with the floor cut away
over the stairwell, so you can see down into the room below, and with no rug authored across the
hole; its stair hotspot is `..._stairs_top` rather than `..._stairs_up`. A hall with a staircase
reaching up into it copies that one instead. Everything else about the two is identical, right down
to the rug hotspot ids, so nothing but the zone changes.

Room offsets are identical in every block. `doors` lists the edges the template has a door hotspot
on, before rotation. Rooms marked **shipped** are offered in the build menu.

| Zone | Room | Doors | Shipped |
|---|---|---|---|
| (232,881) | Garden | ENSW | yes, ground floor only |
| (232,883) | Dungeon cross | ENSW | yes, dungeon only |
| (232,885) | Workshop | NS | yes |
| (232,887) | Parlour | ESW | yes |
| (233,880) | Lawn filler | - | yes, every empty ground-floor cell |
| (233,882) | Roof | - | yes, over every top storey |
| (233,884) | Portal chamber | S | yes |
| (233,886) | Skill hall (stairs up) | ENSW | yes |
| (233,888) | Achievement gallery | NS | yes, one per house |
| (234,881) | Formal garden | ENSW | yes, ground floor only |
| (234,883) | Dungeon stairs | ENSW | yes, dungeon only |
| (234,885) | Chapel | SW | yes |
| (234,887) | Kitchen | SW | yes |
| (235,880) | Solid rock filler | - | yes, every empty dungeon cell |
| (235,884) | Combat room | ESW | yes |
| (235,886) | Skill hall (stairs top) | ENSW | yes, above a staircase |
| (235,888) | Portal nexus | ENSW | yes, one per house |
| (236,883) | Dungeon corridor | NS | yes, dungeon only |
| (236,885) | Study | ESW | yes |
| (236,887) | Dining room | ESW | yes |
| (237,880) | Superior garden | ENSW | yes, ground floor only |
| (237,884) | Games room | ESW | yes, one per house |
| (237,886) | Quest hall (stairs up) | ENSW | yes |
| (237,888) | League hall | ESW | yes, one per house |
| (238,881) | Costume room | S | yes, one per house |
| (238,883) | Oubliette | NESW | yes |
| (238,885) | Throne room | S | yes |
| (238,887) | Bedroom | SW | yes |
| (239,880) | Menagerie (outdoors) | ENSW | yes, built beside an outdoor room |
| (239,882) | Menagerie (indoors) | ENSW | yes |
| (239,884) | Treasure room | S | yes |
| (239,886) | Quest hall (stairs top) | ENSW | yes, above a staircase |

## Inside a room template

Local coordinates within the 8x8 zone are identical for every room:

Rooms come back out the way Old School takes them out: in building mode **every** doorway keeps its
hotspot, joined or not, and clicking one that already has a room behind it asks whether to remove
that room. Door hotspots do not block movement, so leaving them in place costs nothing. A staircase
also carries `Remove-room` on op four - the only op the cache gives room removal - and that always
means the room at the top of it: the one above when you are at its foot, the one you are standing in
when you are at its head. Without it the first upper-floor room could never be removed, having no
neighbour to click a door from. A room holding another one up cannot go first, and neither can the
garden the exit portal stands in.

A staircase's plain `Remove` takes the staircase out from its foot, and is refused while there is a
room above it or when taking it out would leave rooms with no way in. The head of a staircase
(`poh_stairstop_*`) always stands in such a room, so its `Remove` only says to remove the room first.

- Door hotspots sit at `(0,3)/(0,4)` west, `(3,0)/(4,0)` south, `(7,3)/(7,4)` east and `(3,7)/(4,7)`
  north, as a `doorl`/`doorr` pair. A doorway is a two-tile gap in the wall, so `HouseRegistry`
  either deletes the hotspots (the rooms on both sides have a door there) or replaces them with the
  style's plain wall. On the ground floor a doorway onto an empty cell leads out onto the lawn,
  so it gets a door like any other (or none, per the house options) instead of a wall; only
  doorways out of the yard, upstairs, or onto a room with no door on that side are walled up.
  A garden is the exception: it is open to the sky and its template has no wall anywhere, so a
  doorway leading nowhere is left open rather than filled with a slab.
  In build mode every hotspot is left alone - see the note on removing rooms above.
- `poh_dynamic_window` becomes the style's window only on a wall facing outside - an empty cell or
  an outdoor room. On a wall shared with another indoor room it becomes plain wall, so no room can
  be seen into from the next.
- A hung door is spawned over the template's door hotspot, and the engine puts a masked map loc
  back when the loc over it is deleted - so a door swung open would uncover the hotspot, a solid
  wall. Both sides of a doorway carry one, so closing a door uncovers the far side's in the same
  way. `HouseDoorScript` swings house doors through the shared `DoubleDoors` (generic-locs), takes
  the uncovered hotspot away again, and leaves the doors where they are put rather than on the
  generic doors' timer. The same doors outside a house still behave as ordinary double doors.
- Furniture hotspots are numbered per room - `loc.poh_parlour_1` ("Chair space"), `loc.poh_kitchen_5`
  ("Larder space") - and carry **Build** on op5; what they turn into carries **Remove** on op5.
- Windows are all `loc.poh_dynamic_window`, a placeholder with no style of its own, swapped for
  the style's window - which the cache names as the wall's sibling, usually the very next id
  (`loc.village_wall` 13098, `loc.village_wall_window` 13099). Tropical wood is the exception:
  there is no `poh_timberwall` window, so it borrows `loc.timberwall_with_window_2`, checked in
  game to match the timber wall's plaster and framing.

**Angles matter.** The region registry translates a copied loc's coordinates but leaves its angle as
authored, so a loc read back out of a rotated zone still carries the *template* angle. Deleting one
needs that template angle; spawning a replacement over it needs the angle turned by the room's
rotation. `HouseRegistry.replace` is the only place that applies it.

## Vars, portals and NPCs

`varbit.poh_building_mode` (2176) is set on entry. `poh_house_location` (2187),
`poh_house_style` (2188), `poh_house_logo` (2189), `poh_house_locked` (2183), `poh_house_size`
(2285) and `poh_servant_type` (2190) are mapped but not yet driven - the server keeps the house in
its own save attribute rather than in vars.

Town portals carry `Enter / Home / Build mode / Friend's house`; op1 enters, op3 enters in build
mode. `loc.poh_exit_portal` is the portal standing in the garden.

The Teleport to House spell (`spell-teleports`) goes through `HouseAccess` too: Cast enters the house,
keeping build mode if you are already building, and Outside lands at the town portal, shutting the
house behind you. If the house can't be opened, Cast lands you outside instead.

A player who was saved inside a house - a crash, rather than a logout, which moves them out
first - is sent home by `queue.poh_evict` a cycle after login. It has to wait: the login scene is
composed from the saved coordinates before any script runs, and the engine skips the first
build-area rebuild, so moving them during the login event itself is never drawn.

| Town | Loc | Coords |
|---|---|---|
| Rimmington | `loc.poh_rimmington_portal` | 2951, 3222 |
| Taverley | `loc.poh_taverly_portal` | 2891, 3463 |
| Pollnivneach | `loc.poh_pollnivneach_portal` | 3338, 3001 |
| Hosidius | `loc.poh_kourend_portal` | 1740, 3515 |
| Rellekka | `loc.poh_rellekka_portal` | 2668, 3629 |
| Brimhaven | `loc.poh_brimhaven_portal` | 2755, 3176 |
| Yanille | `loc.poh_yanille_portal` | 2542, 3097 |
| Prifddinas | `loc.poh_prifddinas_portal` | 3237, 6077 |

Estate agents - `npc.poh_estate_agent`, `npc.prif_estate_agent`, `npc.fortis_estate_agent` - carry
`Talk-to / - / Relocate / Redecorate`.

## Study lectern

`LecternScript` opens the real `teletabs_craft_if`. Its clientscripts draw everything from two
vars: `varp.teletab_enum` holds the enum of tablets the lectern offers (one per lectern tier, named
`enum.poh_lectern_*` in the construction `gamevals.toml`), and `varbit.tablet_to_make` holds the
selected row. Each tablet obj points at its spell through `spell_parent_obj`, so the Magic level,
runes (staves and tomes included), quest, spellbook and experience all come from `MagicRuneManager`
and the spell itself. A tablet takes 4 ticks and one soft clay. `Create-last` repeats the last
tablet and amount, stored in `varp.teletab_last_crafted(_amount)`.

## Dungeon

The basement (region level 0) takes three rooms, all 70 Construction and 7,500 coins: the corridor
(doors north and south), the cross and the stairs room. Their templates were placed from the cache's
`poh_room` rows (`source_offset`), which is how the corridor and cross were found to be the other
way round from earlier notes; a test checks every room's doors against its template.

- Getting down: building a staircase in a ground-floor hall at 70 Construction asks up or down.
  Down puts the staircase in a dungeon stairs room beneath, laid down for free if the cell is
  empty, and the hall becomes the stairs-top hall above it with its Climb-down staircase - the same
  machinery as an upper floor. A garden's dungeon entrance (centrepiece, 70, one marble block) leads
  down when a stairs room with stairs sits beneath it; stairs under a garden come up through the
  entrance instead of planting a staircase in the lawn.
- Stairs in a dungeon stairs room need a hall or garden above them.
- Dungeon rooms use the style-free `poh_hotspot_door_dungeon` door hotspots. The walls are solid
  blocks rather than wall edges, so a doorway leading nowhere is filled with `dungeon_walltop`.
- Remove-room on dungeon stairs takes out the dungeon room, never the hall above, and a ground
  room cannot be removed while dungeon stairs come up into it.
- Hotspots built so far: lighting (candles, torches, skull torches), decoration (blood, pipe,
  skeleton), the stairs room's staircase and rug, doors, traps and guards.

Guards (`HouseGuards`): one guard space in the corridor and cross, two in the stairs room - a
skeleton (70) up to a hellhound (94), coins only, 50,000 to five million. Each is built as a statue
loc sharing its npc's name; outside building mode the statue goes and `HouseGuards` stands the
npc on its spot, despawning it with the house. Outside challenge mode they stand still and fight
back when attacked; in challenge mode a guest's trap timer sends any idle guard within four tiles
(a choice made here) after them. They give no combat experience (the multiplier is zeroed on spawn and respawn) and come back after death.

Doors and traps (`DungeonScript`, `Dungeon`):

- Every dungeon room has two door spaces, each a left and right panel: oak (74), steel-plated
  (84) and marble (94) doors. The owner just opens them; anyone else picks the lock (Thieving,
  better with a lockpick) or forces it (Strength) on the wiki's success charts, for 10/15/20 XP.
  Both panels swing open together, placed as the generic `DoubleDoorScript` places a double door,
  and shut again after 100 cycles or on Close.
- The corridor and cross have two trap spaces. Traps cost only coins: spike (3-5 damage), man
  (9-11), tangle vine (holds you), marble (drains Agility by a twentieth plus one) and teleport
  (the house's first oubliette pit, else a random dungeon room). Outside building mode they become their `_hidden`
  locs and are recorded on the house; a guest carries `timer.poh_dungeon_traps`, which checks the
  tile underfoot each cycle. Each trap is dodged on the wiki's Agility chart (12% at 1, 82% at 99)
  for 5-20 Agility XP, and rests ten cycles after it springs. Traps only fire in challenge mode,
  and the owner is never caught. The
  tangle vine's hold (8 ticks at level 1 down to 3), the rest time and the owner's immunity are
  choices made here, not wiki facts; the spike trap's knock-back and a dodge's jump aside are
  not modelled.

## Throne room

Ground floor only (60, 150,000, room type 15), one door on the south wall. Hotspots built:

- Throne (two spaces): oak (60) to demonic (99); the skeleton, crystal and demonic thrones take
  magic stones (`obj.poh_magic_crystal`).
- Seating along both side walls: carved teak, mahogany and gilded benches
  (`poh_throneroom_bench_1..3`).
- Decoration (two spaces): gilded decoration, and round, square and kite shields painted with
  the owner's family crest. The build menu names an Arrav shield (`Heraldry.crestDecor`); the
  house swaps in `poh_decor_<wood>_<crest>` for the owner's crest as it is put together. A shield
  needs a crest from Sir Renitee before it can be built.
- Floor: floor decoration, steel cage, trapdoor, lesser and greater magic cage. Every option is
  laid as the style's floor decoration (`poh_throne_room_3_<style>` -> `poh_floordecor_<style>`);
  the cages are only dropped over the mat (`poh_cage_throneroom`, `poh_magic_cage_lesser/greater`,
  at the mat's south-west tile) when the lever is pulled.
  - The cages play `cage_throneroom_fall`, `magic_cage_lesser` or `magic_cage_greater` as they come
    down. A steel cage plays `cage_throneroom_raise` before it is taken away.
  - A sprung trapdoor swaps every tile of the mat's floor decoration for
    `deserttreasure_pitfall_animated` for four cycles, then puts the decoration back. The wiki gives
    the sprung trapdoor's loc id as 6521, which is `deserttreasure_pitfall`; the pitfall's shape
    hasn't been checked against a spawn of it, so it is laid in the decoration's own shape.
  - Victims play `human_falling` (hanging in the air looking down) for the two cycles before the
    drop, and `human_falling_end` as they land in the pit.
- Trapdoor space: oak (68), teak (78), mahogany (88) trapdoors. Open and Close swap the loc in
  place; Go-down lands beside the oubliette's ladder below, or in its pit when it has no ladder.
- Lever: oak (68), teak (78), mahogany (88). Challenge-mode (owner only) asks for challenge mode
  or PvP challenge mode, or switches back to normal when either is on, and tells everyone in the
  house; switching off calls the guards off. The mode is `varbit.poh_house_mode` (0 off,
  1 challenge, 2 PvP) in the server-only temp varp `varp.poh_challenge_mode` on the owner.

Pulling the lever (`ThroneRoomScript`) works on whoever stands on the mat, owner included:

- Floor decoration: nothing happens.
- Trapdoor: everyone on the mat falls into the oubliette directly below, through
  `queue.poh_oubliette_drop` two cycles later. With no oubliette below, nothing happens.
- Steel cage: the cage comes down and holds them (a 2-tick freeze renewed every cycle while they
  stay on the mat); pulling again lifts it.
- Magic cages: as the steel cage, but pulling again asks the puller to Release or Drop into
  oubliette, and a greater cage also offers Expel from house. Its Teleport to... is not offered.

Either challenge mode turns the dungeon's traps and guards on against guests, and the oubliette's
pit on everyone in it. PvP challenge mode also lets players fight each other anywhere in the
dungeon: the hazard timer shows everyone on the dungeon level an Attack op (player op slot 1, as the
wilderness uses), noting it in `varbit.poh_house_attack_op` so it is only cleared when this plugin
set it, and takes it away again on leaving the dungeon, leaving the house or the mode ending.
`HousePvPHook` denies any attack from inside a house unless the house is in PvP mode and both
players are in its dungeon. Deaths stay safe, PvP ones included; nobody is skulled.

Dying anywhere in a house is always safe (`HouseDeathHooks`, bound in `ConstructionModule`):
everything is kept, the player respawns outside the portal of that house, and an owner who dies at
home closes it.

Challenge mode turns the dungeon's traps and guards on against guests. Dying anywhere in a house is
always safe (`HouseDeathHooks`, bound in `ConstructionModule`): everything is kept, the player
respawns outside the portal of that house, and an owner who dies at home closes it.

## Oubliette

A dungeon room (65, 150,000, room type 16, template (238,883)) with a doorway on every side. Its
walls are wall edges rather than the other dungeon rooms' solid blocks, so an unjoined doorway is
closed with `dungeon_outsidewall` instead of `dungeon_walltop`. Hotspots built:

- Floor, across the 4x4 pit: spikes (65), tentacle pool (71), flame pit (77), rocnar (83). The
  pit's ground hotspots (`poh_oubliette_1`, `_side`, `_corner`) take the pool or spikes, or an
  invisible floor under the flames and the rocnar; its object hotspots (`_type8`, `_type8_ogre`)
  take the fire and the rocnar.
- Prison: oak, oak and steel, steel, spiked and bone cages - `poh_cage_dungeon_<tier>` round the
  pit and its `_door` gate on the north side.
- Ladder (oak/teak/mahogany): climbs to beside the trapdoor of the room above, or its middle.
  In challenge mode only the owner may climb.
- Lighting and decoration as in the other dungeon rooms, and a guard space.

The house records each pit's tiles as it is put together (`ActiveHouse.pits`). In challenge mode
the hazard timer hurts anyone in a pit: tentacles 0-4 every 5 cycles, flames 1-3 every 3, the
rocnar 0-12 every 6, and spikes 3-5 once as a victim lands. The wiki gives the tentacle, flame and
rocnar damage; the intervals and the spike damage are choices made here, and the rocnar is a loc
whose hits are rolled without an accuracy check rather than an npc that fights.

The prison gate (`OublietteScript`) opens from anywhere for the owner. Anyone else must be inside
the pit to pick its lock (Thieving) or force it (Strength), for the wiki's 10/12.5/17.5/17.5/20 XP;
from outside it will not open. The wiki gives no success charts, so they run from the oak dungeon
door's (10-250) down towards the marble door's (2-50). The gate swings open as a single door and
shuts after 100 cycles.

## Treasure room

A dungeon room (75, 250,000, room type 20, template (239,884)) with one doorway, on the south.
Hotspots built:

- Treasure: wooden crate (75, holds 10,000 coins), oak (79, 20,000), teak (83, 50,000),
  mahogany (87, 75,000) and magic (91, 100,000) chests. The wiki's room tables give the magic
  chest 1,500 experience and its own page 1,000; 1,500 is used.
- Monster (`poh_dungeon_treasure_2`, 2x2): demon (75, 500,000) up to rune dragon (99, 25 million,
  needs Dragon Slayer II). Guardians are statues sharing their npc's name and join
  `Dungeon.GUARDS`, so `HouseGuards` stands them like the dungeon guards: no experience, back
  after dying, and only aggressive in challenge mode.
- Decoration: the dungeon decorations on `poh_dungeon_7`, and round, square and kite crest
  shields on `poh_dungeon_treasure_5`.
- Lighting, and a pair of dungeon door spaces (`poh_dungeon_4l/4r`).

The chest (`TreasureScript`): the owner uses coins on it to add to the hoard, up to that chest's
limit, at most once every five minutes. The hoard is one server-only perm varp
(`varp.poh_treasure`) shared by every chest; the cooldown is a temp varp
(`varp.poh_treasure_cooldown`, a map clock), so it resets on logout. A visitor can open the chest
only while every guardian of the owner's is dead; Search takes the whole hoard, and the owner and
everyone else in the house are told who won how many coins. An opened chest closes after 100
cycles. Coins stay in the varp if the chest is removed.

## Portal chamber

The centrepiece's Direct-portal (owner only, any build mode) asks which of the three portal
spaces to direct - 1 left of the door, 2 opposite, 3 right, each `poh_teleroom_1..3` - then offers
every destination as a paged chat menu, lowest Magic level first, with More... cycling the pages.
`Portals.Destination` lists 38 spell destinations (plus the two basalt ones below), each a teleport spell found in `MagicSpellRegistry`
by its cache name (and spellbook, for the two Ape Atoll spells). Directing costs 100 times the
spell's runes, taken loose from the inventory, and needs the spell's Magic level (boosts count)
and quest but not its spellbook; it gives five times the spell's experience. Redirecting costs the
full price again. Combination runes, which the wiki now allows, are not accepted.

Each space's destination is `varbit.poh_portal_1..3` (the destination's ordinal plus one, so the
enum is append-only) in the server-only perm varp `varp.poh_portals`, shared by every chamber in
the house. The house swaps an empty frame for `poh_portal_<frame>_<destination>` as it is put
together, and is rebuilt after directing so the new portal shows. The portals survive their frame
or the centrepiece being removed.

Anyone can step through a portal; it leaves the house as the exit portal does, landing on the
spell's coordinates. Two corrections: the standard Ape Atoll spell lands on level 1, and the
Watchtower spell on level 2 (its cache coordinates say level 0; the Watchtower spell itself still
uses them). Varrock, Camelot and Watchtower portals are two-way multilocs on the viewer's
`varrock_ge_teleport`, `seers_camelot_teleport` and `yanille_teleport_location` varbits: op 1 goes
wherever the varbit puts first, op 2 to the other place (Grand Exchange, Seers' Village, Yanille),
and Toggle swaps them. The wiki gates the toggle behind achievement diaries, which are not
tracked here, so anyone can toggle.

The Troll Stronghold and Weiss portals are the stony and icy basalt teleports, which have no spell
(`Destination.TROLL_STRONGHOLD` / `WEISS`, keys `stronghold` and `weiss`, appended to the enum):
- Directing one needs Making Friends with My Arm and no Magic level, and gives no experience.
- It costs a hundred times the basalt's ingredients, as the wiki's table gives: 100 basalt, 100 te
  salt and 300 urt salt for the Troll Stronghold; 100 basalt, 100 te salt and 300 efh salt for
  Weiss. The salts are `red_salt` (te), `green_salt` (urt) and `blue_salt` (efh) in the cache.
- Noted basalt counts, as the wiki says; loose basalt is taken first (`PortalCosts.kt`).
- The portals land where the basalts do, read off the wiki's maps: outside the stronghold's cave
  entrance (2845, 3694) and by Weiss's herb patches (2846, 3938). The stony basalt's roof landing
  needs the hard Fremennik diary and 73 Agility; diaries aren't tracked, so it is not offered.
- The portal nexus offers both too. Its cache entries name the basalt items as their "spell", and
  the nexus matches them to these destinations for the landing spot and the quest. Its
  thousandfold cost - 1,000 basalt - can be paid in banknotes.

## Jewellery box and mounted glory

The achievement gallery (80, 200,000, room type 27, doors north and south) can be built once per
house (`RoomType.unique`). Its jewellery box space takes basic (81), fancy (86) and ornate (91)
jewellery boxes, each built over the last with Upgrade, and its altar space the spellbook altars
below. The rest of the gallery is under "Achievement gallery displays" below.

Altar space (`SpellbookAltarScript`): ancient, lunar and dark altars (80; 10 limestone bricks, a
magic stone, the book's signet, and a pharaoh's sceptre, 10,000 astral runes or 5,000 blood and
5,000 soul runes). The occult altar (90) is built with Upgrade over whichever altar stands there,
for the other two's signets, sceptre and runes - the cache has one furniture row per altar it
replaces, so the group holds three occult options, each with `Buildable.upgradeFrom` naming the
altar it is built over (the build script's upgrade path now honours `upgradeFrom` as well as
"the option before"). Each single altar's Venerate swaps between its book and the standard one.
The occult altar is a multiloc on `varbit.spellbook`: ops 2-4 name the three books the player is
not on, in book order, and Venerate asks which of them to take. Switching goes through
`MagicSpellbookManager`, which also clears autocast. Ancient Magicks and the Lunar spellbook need
the quest of the book's lowest-level spell in the cache (Desert Treasure I, Lunar Diplomacy); the
Arceuus spellbook needs nothing. Guests can use the altars too. The switch messages are this
server's own.

`JewelleryBoxScript` handles both teleporters; both are free and unlimited, check
`JewelleryRequirements` and the teleport validator, play the jewellery teleport animation and then
leave the house as its exit portal does. Destinations come from the jewellery module
(`JewelleryTeleports`), so they match the real items.

- Jewellery box: Teleport Menu opens the real `poh_jewellery_box` interface through
  `poh_jewellery_box_init(tier, title, 0x1F)` - every unlock bit set, so nothing is struck
  through. Its buttons are numbered 0-26 across the dueling, games, combat, skills, wealth and glory
  panels; a click resumes a pause button on `poh_jewellery_box:universe` with that number. Basic
  boxes offer buttons 0-8, fancy 0-18, ornate all 27. The box is a multiloc on the cache varbit
  `poh_jewellerybox_multi` (last button plus one), set after each trip, which makes the client show
  the last destination as op 3; op 3 teleports straight there. Fortis Colosseum still has no
  coordinates in the jewellery module, so it says it has not been added yet.
- Mounted amulet of glory (quest hall guild trophy space): its ops 1-4 are Edgeville, Karamja,
  Draynor Village and Al Kharid.

## Costume room

One per house (42, 50,000, room type 23, one door on the south). Six stores, each tier built over
the last with Upgrade except the toy boxes, with the wiki's levels, materials and experience: the
treasure chest (oak, teak, mahogany), armour case (oak, teak, mahogany), fancy dress box (built as
the cache's "costume box"; oak, teak, mahogany), magic wardrobe (seven tiers, oak to marble), cape
rack (six tiers, oak to magical) and toy box (oak, teak, mahogany).

Storage (`CostumeScript`, `Costumes`): everything is kept in one persisted inventory,
`inv.poh_costumes` (cache inv 637, 1769 slots, server-side `stack = Always` so duplicates share a
slot). Open swaps a closed store for its open loc in place and Close swaps it back; Search (on the
open loc, or on the cape rack as it stands) opens the real `poh_costumes` interface through
`poh_costumes_init(enum, owner, true)` beside `poh_costumes_side`, which holds the inventory with a
Store op. The interface reads the store with `invother_total`, so the house owner's inventory is
sent as the mirrored copy of `inv.poh_costumes` whenever it changes. Which store, build option and
page are open are temp varbits `poh_costume_store/option/page`.

What a store shows and accepts comes from the cache: each store's enum (3289 magic wardrobe, 3290
armour case, 3291 fancy dress box, 3292 cape rack, 3299 toy box, 3293-3298 treasure chest beginner
to master) lists sets by a representative obj; enum 3077 maps a set to its members and enums 3304
and 3303 add their alternates. A store takes only pieces of its own sets, and only up to its tier's
set limit: magic wardrobe 7-42 (marble unlimited), armour case 25/50/unlimited, fancy dress box
2/4/all; the cape rack and toy box are unlimited. The oak treasure chest shows the beginner and
easy pages, teak adds medium, mahogany all six; `varp.if2` tells the client how many tier buttons
to offer, and tier ops 2-7 page between them.

Anyone can look; only the owner can Take (op 1 on an item takes one) or Store (all of that item
from the inventory), and Deposit inventory stores everything that fits. A store holding anything
cannot be removed, nor can the costume room while any store does. Not modelled: the cape rack's
cape-of-accomplishment limits, ultimate ironman rules, the bank PIN, the "Set" op on a set
heading, and the interface's client-side deposit mode.

## Superior garden

An outdoor room (65, 75,000, room type 26, doors on every side), ground floor only like the garden.
Levels and materials are the cache furniture rows', experience the wiki's:

- Fence (the edge's middle, post and post-m hotspots): redwood fence, marble wall
  (`poh_fencing7` on all three), obsidian fence.
- Pool: restoration (65), revitalisation (70), rejuvenation (80), fancy (85) and ornate (90)
  rejuvenation pools, each built over the last. Drink (`SuperiorGardenScript`) restores, in turn,
  special attack energy, run energy, prayer points, every other lowered stat, then hitpoints along
  with curing poison, venom and disease - each pool everything the one before does. Only lowered
  levels are raised, so boosts survive a drink.
- Teleport: spirit tree (75, Farming 83), obelisk (80), fairy ring (85), spirit tree and fairy ring
  (95, Farming 83). The fairy ring module runs the ring (`poh_fairy_ring`, and the combined loc's
  ring ops, which sit one along on ops 2-4). The spirit tree and obelisk are under "Spirit tree and
  obelisk" below.
- Theme: zen, otherworldly and volcanic, over eight hotspot variants (edge, feature, inner and
  outer corner, three paths, path corner). Zen and otherworldly lay one path on all three paths;
  volcanic has a path per variant and its fourth path on the corner.
- Topiary bush (65): `TopiaryScript`. The owner, holding secateurs (plain or magic), uses Clip to
  pick a boss shape from the wiki's nine: Kraken, Zulrah, Kalphite Queen, Cerberus, Abyssal Sire,
  Skotizo, Vorkath, Alchemical Hydra and the Nightmare. Clip can also put the bush back to plain.
  - Each shape needs that boss killed once, read from its cache kill count varp (e.g.
    `varp.total_kraken_boss_kills`). The killcount module increments those from each boss npc's
    `killcount_varp` param.
  - The shape is kept in `varp.poh_topiary` (index + 1), and the house is rebuilt showing
    `poh_topiary_<boss>`.
- Two seating spaces: teak garden, gnome, marble and obsidian benches.

## Menagerie

37, 30,000, doors on every side. The room menu answers with the indoor menagerie (type 24); built
beside an outdoor room, from a doorway or the viewer alike (`HouseLayout.menagerieFor`), it becomes
the outdoor one (type 25, ground floor only), whose
template adds the habitat space. Both have the pet house (oak 37 to nature 92, each built over the
last), pet feeder, pet list, scratching post and arena (its ring and mat hotspots), with the cache
rows' materials; the outdoor one adds grassland to volcanic habitats over its middle, side,
corner and feature hotspots.

Pets (`MenagerieScript`, `PetMenagerie` in the pets module, `HousePets`): the owner stores a pet by
using it on the pet house, up to 3, 5, 7, 9, 12 or 71 by tier. Storage is the cache's own: enum
985 lists the 71 menagerie pets by slot, one bit each across `varp.prayer20`,
`menagerie_contents2` and `menagerie_contents3`, and a multi-form pet keeps the form it went in
as its index in its form enum, in its `poh_menagerie_multiform_*` varbit. The pets module counts a
pet stored there as owned (`PetLocation.HOUSE`), so it is not dropped again. View opens the real
`poh_menagerie` interface (`poh_menagerie_initlist`, `poh_menagerie_initroaming`), which draws
from those varps; Take gives a pet back in its stored form, and "Allow pets to roam" flips
`varbit.poh_menagerie_closed`. While roaming is allowed, each stored pet stands in the house's
first menagerie as its own npc, on a free inner tile, whenever the house is out of building mode;
they move as their npc types allow. The pet list (Read) opens `poh_petlist` with every pet's name
and which are stored.

Extras (`PetMenagerie` extras, `MenagerieScript`):
- Cats and hellcats of every stage, dogs and puppies, pet rocks, pet fish (fishbowls with a fish),
  the toy cat, broav, spooky chair, Mayor of Catherby and Archibald go in as extras when used on
  the pet house.
- They are kept in the cache's 12-slot `inv.poh_menagerie_pets`, overridden in `inv.toml` to
  persist.
- View sends it to the client as the owner's mirrored inventory, so the real list draws them, and
  Take gives them back.
- Extras with an npc roam with the other pets.

Pet feeder: everyone in a house carries the dungeon timer, which now also calls `CatCare.tend`
(pets module) when the house has a menagerie with a feeder. A following kitten that drops to the
hungry or wants-attention warning is fed and fussed back to full.

Scratching post and arena (their locs have no ops; pets are used on them):
- A pet used on the post comes out beside it for ten ticks. A cat has a scratch; anything else
  sniffs at it.
- Two pets used on the arena come out side by side and trade six rounds of taunts, facing each
  other, then go back. Nothing can be hurt, and the items stay in the players' inventories.

Not modelled: the pets' own fight and scratching-post dialogue (generic lines stand in), Humphrey
Dumphrey (no obj found).

## Formal garden

55, 75,000, doors on every side, ground floor only (`RoomType.FORMAL_GARDEN`, type 21, template
zone offset 2 at z 881). Its hotspots are the `poh_posh_garden_*` locs:
- **Centrepiece:** exit portal, gazebo, dungeon entrance, then small, large and posh fountains.
- **Plants:** big and small sunflower, marigold and rose spaces (`flowera`), and big and small
  rosemary, daffodil and bluebell spaces (`flowerb`).
- **Fencing:** boundary stones to marble wall (`poh_fencing1`-`7`).
- **Hedging:** thorny to tall box hedge, built over its corner, middle and end hotspots
  (`poh_hedgecorner`/`middle`/`end` 1-7).

Levels, materials and experience are the cache rows' and the wiki's. Plants and hedges train
Farming as much as Construction.

An exit portal in a formal garden is a house entrance like the plain garden's (`HouseState.isEntrance`),
and its dungeon entrance is the same loc as the plain garden's, so the stairs below work as they do
there.

Not modelled: the tip jar (no tip jar hotspot in the template), and the event pumpkin, beehives and
greenman statue options.

## League hall

27, 15,000, doors east, south and west, one per house (`RoomType.LEAGUE_HALL`, type 29, template zone
offset 5 at z 888). Spaces:
- **Statue:** league statue, Trailblazer globe or ornate statue.
- **League accomplishments scroll.**
- **Banner stand:** plain or ornate.
- **Rug:** plain or opulent.
- **Three trophy pedestals:** plain or ornate.
- **Trophy case:** oak or mahogany.
- **Outfit stand:** oak or mahogany.

Levels and materials are the cache rows'; experience is the wiki's. The rug's level is the cache's
28, not the wiki's 13.

Displays (`Leagues`, `LeagueHallScript`) cover the six leagues the cache has pieces for: Twisted,
Trailblazer, Shattered Relics (`league_3`), Trailblazer Reloaded (`league_4`), Raging Echoes
(`league_5`) and the sixth (`league_6`).
- The owner uses a league trophy on an empty pedestal, a league banner on the banner stand, or a
  whole relic hunter outfit (head, top, legs, boots of one league and tier) on the outfit stand.
- The pieces are taken in and the house is rebuilt showing the cache's variant of the display, e.g.
  `poh_leaguehall_pedestal_2_decorative_twisted_dragon`.
- What is shown is kept in the cache's league hall varbits: each pedestal's league and trophy, the
  banner stand's league, and the outfit stand's league and tier, each stored as index + 1.
- Remove-trophy, -banner and -outfit hand the pieces back.
- The variants have no Remove op, so a display has to be emptied before it can be taken down.

No league runs on this server: the accomplishments scroll and an opened trophy case only say so, and
the statues are just looked at.

Not modelled: the Trailblazer rug (its 24 pieces are laid by tile position, not by hotspot) and the
league statistics, rankings and firsts.

## Portal nexus

72, 200,000, doors on every side, one per house (`RoomType.PORTAL_NEXUS`, type 28, template zone
offset 3 at z 888). Its spaces are the `poh_telenexus_*` and `poh_nexus_*_amulet` hotspots:
- **Nexus:** marble, upgraded to gilded and then crystalline.
- **Amulets:** a mounted Xeric's talisman and a mounted digsite pendant, one per amulet space.
- **Curtains and rug:** the shared sets.

Destinations come from the cache, not the wiki (`Nexus`):
- `enum.poh_nexus_destinations` (1377) maps ids 1-41, plus the two-way second places at id + 150, to
  structs.
- Each struct holds the name, up to four runes with their counts (already the thousandfold nexus
  cost), the Magic level and the teleport spell's obj.
- Those params are named `param.poh_nexus_dest_*` in construction's gamevals.
- Landing spots come from the spell obj's `spell_telecoord`, with the portal chamber's corrections
  (the Watchtower's level). The second places reuse the portal chamber's alternate coordinates.
- Respawn and Boat have no fixed landing spot and are not offered. The Troll Stronghold and Weiss
  are offered; see "Portal chamber".

The owner's teleports are kept in the cache's own varbits:
- `poh_nexus_tele_<slot>` holds a destination id per slot, in order.
- `poh_nexus_left_click` holds the left-click teleport.
- The marble, gilded and crystalline nexus hold 4, 8 and 41 teleports. Upgrading keeps them.

Using it (`NexusScript`):
- **Teleport** goes to the left-click destination. Anyone in the house can use it, and leaves the
  house the same way the exit portal does.
- **Teleport Menu** opens the real `telenexus_teleport` (interface 17). Its clientscript builds the
  rows from the viewer's own `poh_nexus_tele_<n>_temp` varbits and `varbit.poh_nexus_id` (the
  tier), so the owner's teleports are copied there first - which is how a guest sees the owner's.
  A row, clicked or picked by its number key, comes back as a pause button on `rows1` /
  `key_listeners` (the n-th held teleport) or `rows2` / `extra_key_listeners` (the n-th two-way
  teleport's second place).
- **Configuration (owner only)** opens the real `telenexus` (interface 19), a drag-and-drop editor
  over the same `_temp` varbits. The client changes its own copy as you drag, and the server gets
  each drag as a drag event and makes the change itself (`NexusConfig`):
  - an available teleport (rows numbered in `enum.poh_nexus_order`, 1375) dropped on the slots is
    added to the first empty slot;
  - a slot dropped on another swaps them;
  - a slot dropped back on the list is removed, leaving a gap;
  - a slot dropped on the left-click box becomes the left-click, and the radio buttons pick a
    two-way teleport's first or second place; Select on the box's text clears it.

  The drop targets (`scrolling1`, `scrolling2`, `list2`, `click_layer`) are static components the
  cache gives no drag-target flag, so the server sets one on each with `-1..-1`; the engine checks
  those server-set events for a static drop as well as the cache's (`InterfaceEvents`).
  After each change every `_temp` varbit is sent again, so whatever the client's own scripts did,
  it shows the server's copy. An added teleport needs the spell's Magic level unboosted, its quest
  done, and runes for it and everything else added so far. Those runes are set aside in
  `inv.telenexus_cost`, which the interface subtracts when it greys out what you can't afford.
  Save & Close asks the client to confirm; Confirm takes the runes and saves the copy into the
  real varbits. No experience; removing a teleport refunds nothing.

Scrying (`HouseAccess.scry`) is the scrying pool's Scry and the nexus menu's Scry mode:
- **Nexus:** the menu's Teleport Mode / Scry Mode buttons set `varbit.poh_nexus_tele_scry_mode`.
  In Scry mode a row is scried instead of gone to. Clicking the nexus icon (or the "Click here to
  scry house portal" line) scries the house's own town portal. The rows, mode buttons, icon and
  text carry no op names, so all come in as pause buttons with nothing waiting on them
  (`onIfModalPauseButton`). The client takes no further pause button until an interface opens or
  closes, so a mode change opens the menu afresh.
- **Pool:** Scry offers the chamber's directed portals, and shows where each leads as its Toggle
  is set.
- **How it works:** a look lasts ten seconds. The player is hidden, held still and moved there,
  since the client can only draw what is around its player. They then come back through the
  house's loading screen to where they stood - or to the entrance, if the house was rebuilt
  meanwhile.
- **Keeping the house up:** while someone scries, `HouseRegistry` counts them as still in the house,
  so a house its owner has left isn't taken down under them. Logging out mid-scry puts them back
  first, then out at the portal as usual.
- **Wilderness:** as the wiki has it, a Wilderness spot is never shown, and the chatbox gives how many
  players are within 16 tiles of it instead.
- **Not modelled:** Old School's own scry spots, which aren't in the cache, so the teleport's landing
  spot is shown. How long a look lasts is a guess, and the message wording is my own.

Mounted amulets teleport for free:
- The first op goes to the left-click place, which the amulet shows as its loc variant, and Teleport
  menu lists every place.
- The owner's Configuration sets the left-click (`poh_nexus_xeric` / `poh_nexus_digsite`) and
  rebuilds the house.
- Xeric's Lookout, Glade, Inferno, Heart and Honour, and the Digsite, Fossil Island and Lithkren,
  land on the wiki map's coordinates.

Not modelled:
- The amulets' unlocks (ancient tablet, strange machines).
- The Raging Echoes cosmetics.

## Watering can

Every planted piece needs a watering can with water in it in the inventory, as the wiki says
(`Buildable.wateringCan`):
- the outdoor menagerie's habitats;
- the garden's trees and plants;
- the formal garden's flowers and hedges;
- the superior garden's spirit tree, spirit tree and fairy ring, and topiary bush.

Without one, the build menu greys the piece out and building it says so. Any of
`obj.watering_can_1`-`8`, or Gricoller's can (`obj.zeah_wateringcan`), counts.
- No dose is used: the wiki only gives the can as a requirement.
- Gricoller's can is counted whatever its charges.
- No other tools are checked (hammer, saw), as before.

## Spirit tree and obelisk

The overworld spirit tree network moved out of the quest's script into `SpiritTreeNetwork` (quest
module), an injectable singleton.
- It holds the five trees and their travel rules: Tree Gnome Village done, and The Grand Tree for
  the stronghold tree.
- Other content can `register` a `Link`: an extra place every tree's menu offers.

`HouseTravelScript`:
- **Your house:** registers a link offered to any player whose house has a spirit tree or
  spiritual fairy tree in a superior garden. It plays the tree's teleport and takes them home.
- **House spirit tree:** Travel and Last-destination, and the spiritual fairy tree's Tree op, open
  the same network (without "Your house"). Anyone in the house can use it.

Wilderness obelisks (`WildernessObelisks`, wilderness module):
- The six sites (`loc.wilderness_portal_stone_0`-`5`) are told apart by their nearest centre.
- Touching any obelisk charges its site. Six ticks later everyone in its 3x3 square who isn't
  teleblocked goes to another site, keeping their place in the square.
- The site is random unless the toucher has the hard Wilderness diary
  (`varbit.wilderness_diary_hard_complete`). Then Teleport to Destination and Set Destination pick
  the site from a list. Both ops pick and go at once; the destination isn't remembered.

The house obelisk sends only the player who uses it, at once:
- Activate asks for confirmation first, then picks a random site.
- With the hard diary, the other two ops choose the site.
- Nothing teleports into the house.

Not modelled: the obelisks lighting up, the trees' Christmas and Raging Echoes variants, and landing
next to the house spirit tree (players arrive at the house entrance).

## Achievement gallery displays

The gallery's other four spaces (`GalleryScript`, `Gallery`):
- **Adventure log:** mahogany, gilded or marble.
- **Boss lair display.**
- **Display space:** mounted emblem (needs a tier 10 emblem), mounted coins (100,000,000 coins) or
  cape hanger.
- **Quest list.**

Levels, materials and experience are the cache rows' and the wiki's.

**Boss lair:** the owner puts in a boss's jar by using it on the display. The 16 bosses and their
jars are the wiki's list, Kraken (jar of dirt) through the Mad Angel (jar of light).
- Configure picks the lair to show from the jars held.
- Jars hands one back, emptying the display if that lair was on show.
- Jars held are a bit each in `varp.poh_gallery_lair`, and the lair on show is above bit 16.
- The house is rebuilt showing `poh_display_<boss>`.

**Cape hanger:** the owner uses a cape on the empty hanger and Take gives it back. The cape is kept
in `varp.poh_gallery_cape`.
- Takes every skillcape and its trimmed version, the quest, diary and music capes (plain and
  trimmed), the fire, champion's and mythical capes, the max cape and eleven of its variants.
- The mounted capes' own perk ops (teleports, max cape perks, spellbook) only say to wear the cape.

**Reading:** this server has no adventure log, so Read shows total level and quest points; the quest
list shows quest points.

Not modelled: the bank PIN on taking jars out, and the mounted capes' perks.

## Combat room

32, 25,000, doors east, south and west (`RoomType.COMBAT_ROOM`, type 22, template zone offset 3 at
z 884). Spaces: the combat ring, storage (`poh_combat_room_4`), decoration (`_5`, both of its
hotspots) and combat dummy (`_6`). Levels, materials and experience are the cache rows'; the
greenman carving is hung up as made and gives none.

The ring space is nineteen `poh_gr_1_*` hotspots, each named for the rings that build on it, and
`Combat.Ring` turns those names into pieces. The "combat" walls and the four corners make the
boxing, fencing and combat rings' perimeter (the boxing ring's red and blue corners on its
`redcorner`/`bluecorner`), with corner, side and middle mats over the 4x4 floor. The "ranging" walls
box in the floor's south-east and north-west tiles with magic barriers, and those two tiles get the
ranging spots. The "agility" walls rail off the floor's north row, which takes the beam's left end,
middle and right end. The template places these pieces; no wiki or dump states the mapping.

`HouseRegistry` records each ring's floor tiles (`ActiveHouse.rings`). Rings are `HousePvPHook`'s
other exception: two players standing on the same ring's floor may fight, in any house mode.
`HouseRingRules` (a `PvPAttackRestrictionHook`) holds them to the wiki's rules:
- Boxing: melee only, nothing worn, and boxing gloves or fists in hand.
- Fencing: melee with a weapon and shield, nothing else worn.
- Pedestals: ranged and magic only.
- Combat ring: anything.

Dying in a ring keeps everything, as anywhere in a house, and respawns the loser just outside the
ring's nearest edge instead of outside the house. Ring ropes are climbed over and barriers walked
through, both ways (`CombatRoomScript`).

On the balance beam a player stands on a piece (Get-down puts them back where they climbed up) and
uses a pugel on someone else on the beam. A roll of Strength against their Agility knocks them off
(50% at equal levels, 1% per level of difference, 10-90%) and wins the bout. That formula is ours.

The racks hand out what the wiki lists: boxing gloves (both colours), then the wooden sword and
shield, then the pugel. One of each at a time.

Combat dummy: Attach stands the dummy's npc in its place and Detach puts the dummy back. The
dummies are npc category 981, named `category.poh_combat_dummy` in combat-manager's gamevals.
`PlayerAttackManager` treats that category like the admin max-hit cheat: every melee, ranged,
spell and staff attack lands at its max hit. The npc gives no experience and is topped back up to
10,000 hitpoints after every hit. The undead and ornate dummies are upgrades, and each form's own
npc carries its undead, slayer, revenant, kalphite, kurask, vampyre or dragon attributes. Swap on
the ornate dummy picks another form; the paid ones cost their wiki items once (500 revenant ether,
10 ensouled kalphite heads, a kurask head, 20 vampyre dust, Vorkath's head). Bought forms and the
form on show are kept in `varp.poh_dummy_variants`, and the house shows that form when it is built.

Not modelled: the balance beam's real fall animation, ranged ammunition always being lost on a
dummy, and the black mask having to be uncharged.

## Games room

30, 25,000, doors east, south and west, one per house (`RoomType.GAMES_ROOM`, type 6, template zone
offset 5 at z 884). Its five spaces are `poh_games_room_2` to `_7`: the game space (jester, treasure
hunt, hangman), prize chest (oak, teak, mahogany), stone space (clay, limestone, marble attack
stones), elemental balance (lesser, medium, greater, built from air, water, earth and fire runes)
and ranging game (hoop and stick, dartboard, archery target). Levels, materials and experience are
the cache rows'.

`HouseGames` keeps each house's games while it stands; nothing is saved, and closing the house ends
every game. A game played by an npc - the attack stone, the balance and the hangman - takes its
built loc away for the length of the game and puts it back after. A win hands out a prize key when
someone else played too and the prize chest has coins in it.

- Ranging games (`RangingGameScript`): ten shots each, and the round ends once everyone in it has
  shot out; the highest score wins and a tie is a draw. A shot climbs the target's tiers one
  Ranged roll at a time - the dartboard scores 1, 2 or 3 and the archery target 1, 2, 3, 5 or 10,
  with the wiki's chances - and a score gives 2.5, 7.5 or 10 Ranged experience. The dartboard
  takes a wielded thrown weapon and the target a bow or crossbow with usable ammunition, one used
  up each shot; charge bows need none. The `poh_ranging` scoreboard shows the first four players.
- Prize chest (`PrizeChestScript`): the owner uses coins on it up to 20,000, 50,000 or 100,000
  (`varp.poh_prize_chest`) and opens it to take them back. A visitor opens it with a prize key,
  which is used up, and gets the whole prize.
- Attack stone (`GamesRoomScript`): Set-up stands the combat stone npc on its spot. Hit rolls up to
  the melee max hit from Strength and worn strength bonus, and the stone cracks through its 64 npc
  stages until it breaks at 100, 200 or 300 damage (our numbers; the wiki gives none). A chip
  sometimes hits the player for 1 to 3. The most damage wins. Hits train the attack style's stats
  at 2.5% of the usual four experience per damage, and never Hitpoints.
- Elemental balance (`GamesRoomScript`): Activate stands the balance npc on the orb's spot, tipped
  towards air or earth and some water or fire. Players take turns casting the 20 standard elemental
  combat spells on it, paying runes through `MagicRuneManager`; strike tips it by 1 up to surge
  by 5, and air cancels earth as water cancels fire. The npc shows the heavier pair's colour at
  that shade (up to 6) and whoever leaves it white wins. A bigger balance starts further out (3,
  5 or 7, our numbers). Banish puts the orb back.
- Jester (`PartyGamesScript`): Activate swaps in the playing jester and tells the player an emote.
  Copying it from the emotes tab (the emotes module's `PlayEmote` event) earns the next one; the
  first to copy ten wins.
- Treasure hunt: the fairy spawns on a clear inner tile of any room on any floor, and everyone in
  the house gets a treasure stone. Feel says hot, warm, cool or cold and whether that is warmer or
  colder than last time. Feeling it within a tile of the fairy wins, and every stone is taken back.
- Hangman: Activate stands the hangman npc with a word from Mod Ash's list; the npc says the
  blanks. Guess-letter takes one letter, or up to five missing ones as a guess at the word, where
  any wrong letter reveals none. Every miss adds armour, and the tenth piece ends the game. Filling
  in the last letter wins. Reset deals a new word and Banish puts the chest back.

Not modelled: projectiles and the hoop landing, Jacky Jester performing the emotes himself (he
names them instead), the `poh_hangman` letter interface (a text prompt stands in), staves not
counting towards balance runes, treasure stones disappearing when a player leaves, and the game
book.

## Chapel

`Chapel` (construction `data`) holds the altar and burner tables. The furniture table only names
`poh_altar_saradomin_<tier>`; `HouseRegistry` swaps in the god of the icon built in the same room
(Saradomin, Zamorak or Guthix symbols and icons; the Icon of Bob keeps Saradomin), and Remove
resolves any god variant back to its furniture entry.

Bones on any house altar (`AltarSacrificeEvents`, prayer module) give the wiki multiplier for the
altar's tier - 100% oak up to 250% gilded - plus 50% for every lit incense burner in the altar's
zone, which is the chapel itself. Every house altar also takes `Pray`.

`ChapelScript` lights a burner with a tinderbox and a clean marrentill (30 Firemaking). The lit
burner is a timed spawn whose despawn puts the unlit one back; re-lighting restarts the timer. It
burns for 200 ticks plus the Firemaking level plus up to that level again at random - RuneLite's
burner timer model, as the wiki gives no formula.

## Workshop

Pieces marked `upgrade` in `Furniture` are never offered in the build menu: op4 Upgrade builds them
over the piece before them, for the cache's upgrade cost - the bench with vice and lathe, crafting
tables 2-4 and tool stores 2-5. Tool stores fill one more of the five tool spaces per tier and keep
the ones before; an empty slot names its own hotspot loc and is taken out of a finished house.

`WorkshopScript`: a tool store's Search hands out its tools through the standard take chatbox; the
clockmaker's bench crafts everything its tier and the ones below allow (Crafting XP, recipes in
`Workshop`); a broken arrow, broken staff, rusty sword, damaged or broken armour used on a repair
bench good enough for it is repaired with the wiki's success chart for its skill, a failure
destroying it.

`WorkbenchScript`: Work-at lists the furniture categories (Old School's own category menu is drawn
by a clientscript missing from the dumps), then the regular creation menu for the pieces the bench
can pack - up to level 20, 40, 60, 80 and 99 by tier. A flatpack takes 8 ticks, a hammer and saw,
the full materials and level, and gives the XP; it installs at any level, in place of the materials
and without XP. `Flatpacks` matches a piece to its flatpack obj by name, as the cache's own link is
server-only.

## Heraldry

Sir Renitee (`npc.poh_herald_of_falador`, `SirReniteeScript`) reads out the player's family crest
once they have 16 Construction, assigning a random requirement-free crest the first time, and
changes it for 5,000 coins (the money bag for 500,000) when its requirement is met - a quest, 70
Prayer, a toy horsey or a skull. Dialogue follows the wiki transcript. The crest lives in the
server-only permanent `varp.poh_family_crest` (0 = none, otherwise `Crest.id`).

`HeraldryScript`: the pluming stand paints steel, adamant and rune full helms (38 Crafting), the
shield easel adds kiteshields (43) and the banner easel banners from a plank and a bolt of cloth
(48), each in the player's crest, through the make chatbox at 6 ticks apiece.

Sir Renitee also sells the quest hall's pictures (`Paintings`): portraits for 1,000 coins and
landscapes for 2,000, offered only once every quest behind them is done, and small, medium and
large maps for 1,000 at 51, 101 and 151 quest points. The quest hall frames them on its portrait,
landscape and map spaces with teak or mahogany planks. His line refusing a map without enough
quest points, and the one for having no portrait or landscape yet, are not in the wiki transcript
and were written for this.

The quest hall's guild trophy space mounts an anti-dragon shield, amulet of glory, Cape of Legends
or mythical cape (47 Construction, three teak planks), and its sword space Silverlight, Excalibur or
Darklight (42, two teak planks). A `Buildable.quest` - Dragon Slayer I and II, Legends', Demon
Slayer, Merlin's Crystal, Shadow of the Storm - greys the piece out in the build menu and blocks
building it until that quest is done.

## Skill hall

The armour spaces hold a mithril, adamant or rune suit (28 Construction plus 68/88/99 Smithing,
25 Smithing XP) or a Castle Wars suit; removing one hands the armour back (`Buildable.refund`).
The rune case space takes cases 1-3 (41 Construction plus 14/44/90 Runecraft, 25 Runecraft XP).
`Buildable.skill`/`skillLevel`/`skillXp` cover the second stat.

The head and fishing trophy spaces build empty displays - teak, mahogany, gilded for heads; oak,
teak, mahogany for fish - each buildable outright or upgraded from the tier below for the cache's
upgrade cost (`Buildable.upgradeMaterials`). `TrophyScript`:

- the Canifis taxidermist (`npc.poh_taxidermist`) stuffs a head or big fish for the wiki's price,
  with her transcript lines;
- a stuffed trophy used on a display in building mode is mounted for good: Construction XP plus
  Slayer or Fishing XP, or 200 XP in every combat skill for the boss heads if the player accepts;
- mounted trophies are account-wide bits in `varp.poh_skill_trophies`, which also holds the trophy
  each kind of display shows; Trophies picks it from a plain list (Old School uses its trophy menu)
  and `HouseRegistry` draws the display with it. A display only shows trophies of its tier or lower.

The trophy on show is kept per account, not per display, so a second skill hall shows the same
trophies - a simplification.

Mounted heads talk (`TalkingHeadsScript`): Talk-to runs each head's conversation from the wiki
transcript, spoken through the head's own chathead npc (`npc.poh_mounted_<head>_<wood>`, and the
three `poh_mounted_kbd_*` heads). Houses have no guests yet, so only the owner's conversations
are there. Fish don't talk.

## Guests

The town portal's Enter asks your house, your house in building mode, or a friend's house; Home
and Build mode go straight to your own; Friend's house asks for a name. `HouseAccess.visit` takes
the guest to the entrance of that player's house if it stands at this portal's town, its owner is
in it, and it is not in building mode ("They do not seem to be at home." / "That player is in
building mode." otherwise - both written for this rather than taken from Old School).

Who may come in is the owner's private chat setting, as the wiki describes (`HouseVisitors`):
- On lets anyone in, Friends only the owner's friends, and Off nobody.
- Nobody on the owner's ignore list gets in, whatever the setting.
- An ironman can't visit at all (`IronmanActivity.POH`, "As an Ironman, you cannot enter another
  player's house."). Group ironmen are treated like everyone else, as there are no groups to check.
- The exit portal's Lock shuts the house to everyone ("That player's house is locked."), until the
  owner picks it again. Only the owner can lock it, guests already inside stay, and it is kept in
  `varbit.poh_house_locked`, so it lasts across logins. A locked house counts as empty for the
  advertisement board, so its listing comes down after 15 minutes.

A refused guest gets the same "They do not seem to be at home." as when nobody is in, so nothing
gives the setting away. The setting, friends and ignores live in the central social service, so
`HouseAccess.visit` fetches the owner's snapshot (`CentralSocialService.socialSnapshot`) through
the database gateway and waits up to ten cycles for it. If the service can't be reached - no
central link, an owner with no character id, an error or no answer in time - the guest is let in,
as before the rule existed, rather than every house shutting. Names match whatever their case, and
a space, underscore or non-breaking space alike. Guests already inside stay when the owner changes
the setting; Expel Guests still puts them out.

A guest is anyone standing in another player's house region - nothing tracks them separately.
`HouseRegistry.houseAt` finds the house a tile belongs to, and `guests` lists who is in it. When the
owner rebuilds the house without building mode, guests are carried to the same tile of the new
region, and when the owner starts building they are put out at the house's town portal.

When the owner leaves - by the exit portal, by visiting someone else, by dying, by logging out, or
by any teleport (the dungeon timer notices them outside every house, `HouseAccess.leftWithoutPortal`)
- guests stay, as the wiki has it. `HouseRegistry.vacate` keeps the house standing with its servant,
guards and pets while anyone is in it, and takes it down once the last guest is out:
`HouseRegistry.sweep` runs when a guest uses the exit portal or logs out, and when the dungeon
timer every player in a house carries finds them in no house any more (a teleport or a death out).
Nobody new can come in while the owner is away, as visiting needs them home. An owner who comes
back takes the house over again (`reclaim`): it is reopened from their current state, as an
estate agent's changes may have been made meanwhile, and the guests are carried across to the same
spots. If they come back to build, the guests are put out instead. The exit portal puts a guest
outside without touching the owner's house, and logging out inside a friend's house lands you at
that house's portal. Stairs, the lectern, workshop and chapel work for guests; building and trophies stay with
the owner, and talking heads give guests their guest conversation.

## House advertisement boards

Each town portal has a House Advertisement board beside it (`HouseLocation.board`), run by
`AdvertBoardScript` with the listings kept server-wide in `HouseAdverts`. Following the wiki:
- Add-House, or the interface's Add/Remove House button, lists your house from level 50
  Construction. A listing lasts 30 minutes or until you log out, and a house can only be listed once
  every 30 minutes. While listed, `varbit.poh_board_advertising` is set.
- The exit portal's Remove board advert, or the button again, takes it down.
- A listing is checked each time a board is read, and taken down once nobody has been in the house
  for 15 minutes. The wiki's warnings three, two and one minutes before that aren't given.

View opens the real `poh_board` (interface 52):
- The server sets `varbit.poh_board_last_loc` to the board's town (the clientscripts' numbering:
  Rimmington 1, Taverley 2, Pollnivneach 3, Rellekka 4, Brimhaven 5, Yanille 6, Hosidius 8,
  Prifddinas 9). It then calls `poh_board_addline` for rows 0 to 200; the call for row 200 draws
  the board.
- Each listing's line is `name|town|level|altar|nexus|box|pool|spellbook|armour`
  (`HouseAdverts.line`). That is a Y or N for the gilded altar and the armour stand, the nexus and
  jewellery box tiers (1-3), the superior garden pool tier (1-5) and the spellbook altar (ancient 1,
  lunar 2, dark 3, occult 4), each the best built anywhere in the house (`HouseAdverts.features`).
- Sorting and the town filter are the client's own.
- A listing from the board's own town gets an Enter House arrow. Its clientscript answers a name
  dialog with the owner's name, so the board waits on one (`ProtectedAccess.nameDialogInput`) and
  then visits as the portal's Friend's house does, privacy and all. Add/Remove and Refresh cancel
  that wait, act, and show the board again.

Visit-Last visits the last name given to a portal or board this session, kept in an attribute as a
name can't go in a varp. Aldarin has a board in the cache but no house location here, so it is left
alone.

## Rotating rooms

Building a room (`HouseBuildScript.buildRoom`) offers every rotation that keeps one of its doors on
the doorway it is built from (`RoomType.rotationsFacing`).
- The house is shown with the room at each rotation in turn. The menu offers Rotate clockwise,
  Rotate anticlockwise, Build it here and Cancel.
- A room with only one way to face (the portal chamber, the throne room) skips the menu.
- The fee is taken only once it is built.

Clicking a doorway into a room that is already built offers Rotate, Remove or Cancel.
- Rotate previews the room's other rotations the same way, through that doorway, and keeps its
  furniture.
- A room a staircase runs through - its own, or the one rising into it from below - can't be
  turned, and no turn may cut rooms off (see `HouseLayout` under "House viewer").

The previews are built from a copy of the house state (`HouseAccess.rebuild(preview = ...)`,
`HouseRegistry.reopen(state = ...)`), which is never saved. Logging out or walking off mid-menu
leaves nothing behind; a `finally` puts the real house back however the menu ends.

## House viewer

The house options' Viewer opens the real `poh_viewer` (interface 422) in building mode, in your own
house (`HouseViewerScript`). The client draws it all:
- Each room is sent with `poh_viewer_setroom` (slot, `poh_room` row, then three packed words) and
  the map laid out by `script1382`. The room positions it keeps are client-only varcs
  (`poh_roompos_01..38`) that the setroom script fills in.
- The packed words hold the room's viewer cell, floor, rotation and `poh_room` type, then for each
  hotspot the 1-based place of what is built there in that hotspot's `builddata`, which is what the
  map's hover tooltip lists (`HouseViewer.pack`; bit 31 of the first word travels as bit 30 of the
  second).
- The side panel follows `varbit.poh_viewer_selectedroom`, `_destination`, `_rot`, `_enable_rot`,
  `_type`, `_selecteddoors` and `_adjacentdoors` (all on varp 780). The client flips them itself on
  each click, so the server makes the same change and keeps the true values. Door bits run north,
  east, south, west; ours are by `Side`, so `HouseViewer.viewerBit` converts.

The map is 9x9 per floor: the 7x7 building area (see "Rooms: levels and costs") with a free cell
all round, so the viewer's window is fixed one cell south-west of `HouseLayout.ORIGIN` on our 13x13
grid (`HouseViewer.layout`).

What it does:
- Selecting a room shows it with Move, Rotate and Delete.
- Move asks for an empty map cell on the same floor; it and Rotate end with the clockwise and
  anticlockwise arrows and Done, which saves the change, rebuilds the house and redraws the viewer.
  Cancel puts the selection back.
- Delete asks to confirm in the chatbox, then removes the room with the doorway's checks.
- An empty cell's Add room opens the room menu, then places the new room for turning; it is paid
  for, and built, on Done.
- Go to Portal puts you by the exit portal.
- The floor buttons are the client's own.

Every change goes through `HouseLayout`, which building, turning and removing through a doorway
also use:
- A room a staircase runs through can't be moved or turned, and one with a room above it can't be
  moved.
- An upstairs room needs a room under it, however it is built. Only the ground floor holds anything
  up: a dungeon room under a hall can come out (taking the hall's stairs with it).
- Only a hall can stand at the top of a staircase. Stairs can't be built on the first floor (they'd
  lead nowhere), and stairs up are refused when a room that isn't a hall stands above.
- Every room has to be reachable: door to door from a staircase, or on the ground floor from a
  doorway onto the yard's lawn, which leads outside. No change - build, move, turn or remove - may
  leave more rooms cut off than before (`HouseLayout.cutOff`). So nothing is ever stranded, and a
  new floor is only started with a staircase. On the ground floor a room needn't touch another, as
  long as it opens onto the lawn.

## Doors

Outside building mode a doorway between two rooms gets the style's double door
(`HouseStyle.doors`), closed or open by the owner's Doors option, or nothing for "no doors". Each
door hotspot ghosts its style's door - the models sit side by side in the cache - which is how the
style-to-door table was worked out; basic stone has no door of its own and borrows basic wood's.

Both rooms of a join author their doorway on the same wall line, so only one hangs the door: the
indoor room when the other side is a garden, otherwise the room west or south of the join. The
panels are placed to suit the generic `DoubleDoorScript` - a left panel's partner is at
`DoorTranslations.translateClose` - and the door locs are tagged in
`.data/raw-cache/server/loc/doors.toml`, so opening and closing them is that script's job. An
"open" house hangs the `_open` panels moved and turned just as the script opens them. Like every
door it handles, a door you open or close goes back after `DoorConstants.DURATION`.

## House options

`HouseOptionsScript` drives `poh_options`, which the settings tab already opens as a side panel. Its
radio buttons flip their own varbits on the client, so the server mirrors each click:

- Building Mode rebuilds your house in or out of building mode (guests are put out when it goes
  on); outside your own house it only says so.
- Teleport Inside (`varbit.poh_tele_toggle`, 1 = outside) and Default Building Mode
  (`varbit.poh_teleport_building_mode`) are read by Teleport to House.
- Doors stores `varbit.poh_doors_option` and redoes the house so the change shows.
- Expel Guests puts everyone else in your house out at its portal; Leave House works like the exit
  portal for owners and guests alike; Call Servant calls your servant once a bell-pull is built;
  Viewer opens the house viewer (see "House viewer").
- The room count line is filled in when the panel opens.

## Servants

Hired at the Servants' Guild in East Ardougne (`ServantScript`, `ServantDialogues`): each servant's
guild npc is a multinpc on `varbit.poh_servant_type` that hides once that servant is hired (Rick 1,
Maid 3, Cook 5, Butler 6, Demon butler 8). Hiring needs the servant's Construction level, no
servant already, two bedrooms with beds and the fee up front; the chief servant explains and
dismisses. Lines follow the wiki transcripts.

`HouseServants` stands the servant in its owner's house beside the entrance while the house is
open and has its two bedrooms. Talk-to, a bell-pull, or the house options' Call Servant (with a
bell-pull built) brings it and opens its orders: bank fetches of the eleven building materials,
deposits (use an item on it), un-noting (use notes), sawmill runs for the cook and up (use logs;
the sawmill's fee comes out of the inventory), repeat last task, or fire. An errand takes the
servant away for its trip time on `queue.poh_servant_trip`, up to its capacity; items only change
hands when it returns, apart from deposits, which go into the bank at once. Every eight errands
(`varbit.poh_servant_pay`) it asks for its fee again before doing anything else.

The orders follow the butler's transcript. With a last task on record the servant first asks
"Repeat last task?" (the task, or Something else...). Then come Serve..., Go to the bank..., Go to
the sawmill... (cook and up), Greet guests and You're fired.

Greet guests ("Stay at the entrance and greet guests." / "Very good, sir.") sends the servant to wait
by the entrance (`HouseServants.stationAt`) until it is next called or sent on an errand. While it
waits, every guest who comes in is greeted (`ServantDialogues.greetGuest`, from `HouseAccess.visit`):
- If the owner is in another room: "[owner] is entertaining in the [room]. Would sir care to follow
  me?", and the guest is taken to the owner. Only the butler's transcript has this line, so every
  servant uses it.
- Otherwise a plain "Welcome, sir." - written for this.

A guest talking to the servant is asked "Can I help sir?" and can say "Please show me out.", which
puts them out like the exit portal; both lines were written for this, as the transcripts have none.

Serve... (`Kitchen`): tea needs a kitchen with a stove that is not a firepit, a larder, a sink and
shelves - the best shelves pick the cup (clay +1, porcelain +2, trimmed +3 Construction when drunk,
`HouseDrinksScript`), with or without milk; dinner needs a dining table and a kitchen with a stove
and larder, and hands the servant's dish (shrimps, stew, pineapple pizza, chocolate cake, curry) to
everyone in the house; drinks need a barrel and pour its house drink. House teas, drinks and their
empty cups and glasses are taken off a player who leaves the house by the portal, Leave House,
being put out, or logging out. A barrel drink works as the ordinary drink it copies
(`obj.poh_beer` is `obj.beer`): the food table's healing and the consumables module's
`FoodEffectService` effect, except house cider, which boosts Farming by 2 rather than 1.

`KitchenScript`: Search on a set of shelves (either half) opens the take chatbox with that tier's
teapot, cup and kettle, plus the cookware each tier adds after wooden shelves 1 - beer glass, cake
tin, bowl, pie dish, pot, chef's hat. An empty beer glass (the house's or an ordinary one) used on
a barrel becomes its house drink.

Tea by hand: a larder's Search hands out its stock (tea leaves and milk; the oak larder adds eggs
and flour, the teak larder potatoes, garlic, onions and cheese). The kettle is filled by using it
on a sink and boiled on an oven or range - never a firepit - in three ticks; leaves go in a
teapot, and the boiled kettle poured on makes a pot of four cups (20 Cooking, 52 Cooking XP). Each
pour into a house cup makes that cup's tea, whatever the pot, and a bucket of milk makes it milky.
Teapots, kettles, house tea leaves, cups and pots of tea are all house-only items.

The transcripts lack most in-house lines, so servants other than the butlers say plain stand-ins
for coming back, banking, the sawmill and wages ("Here you are.", "My wages are due..."), and the
replies for empty banks, full inventories and missing bell-pulls were written for this.

## Rooms: levels and costs

A house holds at most `HouseLayout.maxRooms(level)` rooms, gardens and dungeon rooms included: 24
up to level 25, one more at 26 and every six levels to 92, and one each at 96 and 99, for 38 - the
wiki's table, and the 38 slots the house viewer has. Every way a room is made checks it: a doorway,
the viewer's Add room, stairs up (which raise a room above) and stairs down from a hall (which lay
down the dungeon stairs room). Free build skips the level but still stops at 38, all the viewer can
show. The limit and the building area below go by the base Construction level: a boost doesn't
count, so a house can't be built past what its yard shows once the boost wears off.

Rooms also have to stand inside the building area, a square that grows with Construction level:
3x3 rooms at level 1, 4x4 at 15, 5x5 at 30, 6x6 at 45 and 7x7 at 60 (`HouseLayout.maxSize`). Its
south-west corner is fixed at `HouseLayout.ORIGIN`, one cell south-west of the starter garden, so
the garden is the middle of the first 3x3. Each step adds a row to the north and a column to the
east, as the wiki describes. A doorway leading out of the area, and a move or Add room in the
viewer, say that a higher level is needed, or that you can't build any further at all past 7x7.
Free build gets the whole 7x7. Rooms already standing outside the area are left alone, but the
viewer won't open for such a house.

| Level | Room | Cost | | Level | Room | Cost |
|---|---|---|---|---|---|---|
| 1 | Parlour | 1,000 | | 45 | Chapel | 50,000 |
| 1 | Garden | 1,000 | | 50 | Portal chamber | 100,000 |
| 5 | Kitchen | 5,000 | | 55 | Formal garden | 75,000 |
| 10 | Dining room | 5,000 | | 60 | Throne room | 150,000 |
| 15 | Workshop | 10,000 | | 65 | Superior garden | 75,000 |
| 20 | Bedroom | 10,000 | | 65 | Oubliette | 150,000 |
| 25 | Skill hall | 15,000 | | 70 | Dungeon | 7,500 |
| 27 | League hall | 15,000 | | 72 | Portal nexus | 200,000 |
| 30 | Games room | 25,000 | | 75 | Treasure room | 250,000 |
| 32 | Combat room | 25,000 | | 80 | Achievement gallery | 200,000 |
| 35 | Quest hall | 25,000 | | | | |
| 37 | Menagerie | 30,000 | | | | |
| 40 | Study | 50,000 | | | | |
| 42 | Costume room | 50,000 | | | | |

Locations: Rimmington 1 / 5,000 - Taverley 10 / 5,000 - Pollnivneach 20 / 7,500 - Hosidius 25 /
8,750 - Rellekka 30 / 10,000 - Aldarin 35 / 12,500 - Brimhaven 40 / 15,000 - Yanille 50 / 25,000 -
Prifddinas 70 / 50,000.

## Not yet built

- In the throne room: the greater magic cage's Teleport to... The back decoration hotspot
  (`poh_throne_room_3`) is not in the template and is left out.
- Hotspots whose data could not be sourced from the cache alone: the chapel statues, the garden tip
  jar. They are deliberately absent rather
  than guessed at. A hotspot with no table is taken out of a finished house, but it is still there
  in building mode and clicking it does nothing.
- The Respawn and boat portals (no fixed landing spot).
- Barrows repair on the armour stand: nothing degrades Barrows equipment yet.
- Group ironmen visiting only their own group's houses (there is no group membership to check).
