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

Room offsets are identical in every block. `doors` lists the edges the template has a door hotspot
on, before rotation. Rooms marked **shipped** are offered in the build menu.

| Zone | Room | Doors | Shipped |
|---|---|---|---|
| (232,881) | Garden | ENSW | yes, ground floor only |
| (232,883) | Dungeon corridor | - | no |
| (232,885) | Workshop | NS | yes |
| (232,887) | Parlour | ESW | yes |
| (233,880) | Plain grass filler | - | no |
| (233,882) | Roof | - | no |
| (233,884) | Portal chamber | S | yes |
| (233,886) | Skill hall (stairs up) | ENSW | yes |
| (233,888) | Achievement gallery | NS | no |
| (234,881) | Formal garden | ENSW | no |
| (234,883) | Dungeon stairs | - | no |
| (234,885) | Chapel | SW | yes |
| (234,887) | Kitchen | SW | yes |
| (235,880) | Dungeon corridor | - | no |
| (235,884) | Combat room | ESW | no |
| (235,886) | Skill hall (stairs down) | ENSW | no |
| (235,888) | Portal nexus | ENSW | no |
| (236,883) | Dungeon junction | - | no |
| (236,885) | Study | ESW | yes |
| (236,887) | Dining room | ESW | yes |
| (237,880) | Superior garden | ENSW | no |
| (237,884) | Games room | ESW | no |
| (237,886) | Quest hall (stairs up) | ENSW | yes |
| (237,888) | League hall | ESW | no |
| (238,881) | Costume room | S | no |
| (238,883) | Oubliette | - | no |
| (238,885) | Throne room | S | no |
| (238,887) | Bedroom | SW | yes |
| (239,880) | Menagerie (outdoors) | ENSW | no |
| (239,882) | Menagerie (indoors) | ENSW | no |
| (239,884) | Treasure room | - | no |
| (239,886) | Quest hall (stairs down) | ENSW | no |

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

- Door hotspots sit at `(0,3)/(0,4)` west, `(3,0)/(4,0)` south, `(7,3)/(7,4)` east and `(3,7)/(4,7)`
  north, as a `doorl`/`doorr` pair. A doorway is a two-tile gap in the wall, so `HouseRegistry`
  either deletes the hotspots (the rooms on both sides have a door there) or replaces them with the
  style's plain wall. In build mode unconnected hotspots are left alone, because that is what the
  player clicks to add a room.
- Furniture hotspots are numbered per room - `loc.poh_parlour_1` ("Chair space"), `loc.poh_kitchen_5`
  ("Larder space") - and carry **Build** on op5; what they turn into carries **Remove** on op5.
- Windows are `loc.poh_dynamic_window`, left as authored.

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

## Rooms: levels and costs

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

- The rooms marked "no" above, and the dungeon floor with them.
- Hotspots whose data could not be sourced from the cache alone: trophy and quest-item spaces, the
  chapel statues, the workshop tool stores, the garden tip jar. They are deliberately absent rather
  than guessed at. A hotspot with no table is taken out of a finished house, but it is still there
  in building mode and clicking it does nothing.
- Directing a built portal frame at a destination, and the portal nexus.
- Servants, the house options interface, guests and the house advertisement board.
- Chapel altars always use the Saradomin models; live Old School picks the god from the icon built
  on the same wall.
