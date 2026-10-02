# Mourning's End Part II (The Temple of Light)

Code: `content/quest/src/main/kotlin/org/rsmod/content/quest/area/tirannwn/templeoflight/`.
Tests: the same package under `content/quest/src/test/`.

| File | What it does |
|---|---|
| `MourningsEndPart2Quest.kt` | Quest script, stages, unlock rules, journal, rewards |
| `TempleGeometry.kt` | Pillars, beam paths, doors, shafts, cross locs, colour wheel |
| `LightNetwork.kt` | The light engine: puzzle state in, lit paths and open doors out |
| `TemplePuzzleStore.kt` | Saving pillar contents (versioned) |
| `TempleLights.kt` | Writing the player's beam, pillar, cross and door varbits |
| `TemplePuzzle.kt` | Commits, what the player is owed, the dispenser tray, the chests |
| `TemplePillars.kt` | Search / use-item on pillars: insert, rotate, remove, inspect |
| `CrystalDispenser.kt` | The dispenser (collect / reset) and the five chests |
| `LightDoors.kt` | Passing light doors; meeting Thorgel at the black door |
| `ThorgelList.kt`, `npcs/Thorgel.kt` | Thorgel's list and dialogue |
| `TempleObstacles.kt` | Stairs, ladders, low walls, rope, wall supports, blade traps, the doorway, the dwarves' tunnel |
| `TempleDiscoveries.kt` | The dig team, bodies, journal, notes, black crystal, altar charging, restoration |
| `TempleShadows.kt` | Shadow aggression and the trinket's truce |
| `npcs/TempleOfLightTalks.kt` | Arianwyn's and Essyllt's Part II dialogue |

Part I's `LletyaArianwyn`, `Essyllt` and `MournerHideout` hand over to these once Part I counts as
done (`MourningsEndQuest.unlocked`).

## Stages

`varbit.mourning_quest_main` (bits 0-7 of `varp.mourning_quest_part2`), endstate 60 from
`dbrow.quest_mourningsendpart2`. The client only distinguishes 0, 1-59 and 60.

| Stage | Meaning |
|---|---|
| 0 | Not started |
| 5 | Arianwyn sent the player to the mourners' mine |
| 10 | Essyllt handed over the new key |
| 20 | The player has seen the dead dig team outside the temple |
| 30 | Arianwyn asked for a sample of the black crystal |
| 40 | Eluned made the new crystal from the sample |
| 50 | The charged crystal restored the safeguards |
| 60 | Arianwyn's thanks: complete |

Every step only moves forward (`advanceTo`). Holding an item is never progress: the key (Essyllt
or his desk), sample (the black crystal), new crystal (Arianwyn), list (Thorgel) and trinket
(Arianwyn) are all replaceable.

## Rewards

From the dbrow and the quest script: 2 quest points, 60,000 Agility experience (the row stores
600,000 tenths; the old 20,000 is not used), the crystal trinket. The experience goes through
`statAdvance`, i.e. it is multiplied by the player's `xpRate` and the server's `globalXpRate`
(`xp-rates.yml`; 1.0 by default gives exactly 60,000). Completion runs once: the quest manager only
grants rewards when the stage first reaches 60, and Arianwyn refuses to complete with no room for
the trinket.

## The light model

Verified against the OSRS wiki walkthrough, the OSRS wiki pages for the light doors, the Final
Pillar, the colour wheel and the crystals, and the RS3 wiki's description of the same 2005 puzzle.

- Colours sit on a wheel: red, yellow, green, cyan, blue, magenta. White is the temple's own light.
- A crystal on a beam: white takes the crystal's colour; the crystal's own colour passes; colours
  two steps apart give the one between (cyan + yellow = green, cyan + magenta = blue, yellow +
  magenta = red, green + blue = cyan, red + green = yellow); adjacent or opposite colours are
  stopped. This is not RGB intersection: the fixed green crystal turns red light yellow, which an
  intersection model cannot do (RS3 wiki: the green crystal accepts "only white, red or blue light,
  turning it green, yellow or cyan").
- A door opens for a beam of only the colour opposite its own (yellow/blue, cyan/red,
  magenta/green); the black door needs white.
- White only comes back together at the Final Pillar: its north side wants blue, its east side red,
  its south side green, each alone. Then it sends white west at the black door. A single white beam
  into the Final Pillar does nothing (otherwise the final section would need four mirrors instead of
  thirteen).
- Mirrors send everything that reaches them out of the side they face (north, east, south, west,
  up, down). An empty pillar lets light straight through, vertically too. A pillar holds one item.
- Fractured crystals only fit the four-way pillars and take light only through the clear edge:
  horizontal (north edge) splits west/south/east, vertical (east edge) splits north/west/south.
- Beams of different colours share a path or a mirror without blending; a door hit by a mixed path
  stays shut.
- The source is the great crystal under the middle floor's pillar 9 (the "emitter" by the
  dispenser). Up from pillar 9 is the black crystal, down the source; both stop light.

The engine (`LightNetwork`) is a worklist over pillar sides. A side only gains colours and there
are seven, so it settles on its own, loops included; `MAX_STEPS` guards against a geometry mistake.

## Geometry and cache mapping

Pillars are named after the beam varbits around them: `F_C` = floor F (1 ground, map level 0),
column C of the grid below. Every row is checked against the map in `MourningsEndPart2CacheTest`.

| Column | x, z | Floor 1 (level 0) | Floor 2 (level 1) | Floor 3 (level 2) |
|---|---|---|---|---|
| 1 | 1860, 4665 | `1_1` preset mirror (east) | open (cross `2_1`) | `3_1` |
| 2 | 1887, 4665 | open (cross `1_2`) | `2_2` | `3_2` fixed magenta, sealed cell |
| 3 | 1898, 4665 | `1_3` | `2_3` | `3_3` |
| 4 | 1915, 4665 | - | - | open (cross `3_4`) |
| 5 | 1887, 4650 | open (cross `1_5`) | `2_5` | `3_5` |
| 6 | 1898, 4650 | `1_6` | `2_6` | `3_6` |
| 7 | 1909, 4650 | `1_7` | `2_7` | open (cross `3_7`) |
| 8 | 1860, 4639 | - | open (shaft `2_8_up`) | `3_8` |
| 9 | 1909, 4639 | source crystal | `2_9` (emitter) | black crystal |
| 10 | 1887, 4628 | `1_10` | `2_10` fixed green | `3_10` |
| 11 | 1898, 4628 | `1_11` | `2_11` | `3_11` |
| 12 | 1909, 4628 | `1_12` | open (cross `2_12`) | `3_12` |
| 13 | 1860, 4613 | `1_13` preset mirror (north) | open (cross `2_13`) | `3_13` |
| 14 | 1887, 4613 | `1_14` | open (cross `2_14`) | open (cross `3_14`) |
| 15 | 1898, 4613 | open (cross `1_15`) | `2_15` | `3_15` |
| 16 | 1915, 4613 | `1_16` | `2_16` | `3_16` |

Off the grid: `1_b` (1881, 4639, 0), preset mirror "Mirror 14" facing east at the cyan door; the
Final Pillar (1869, 4639, 0). The three `loc.mourning_temple_pillar_3_a` locs on the top floor and
two on the ground floor are fixed bends: the paths `3_5_west`, `3_10_west` and `3_8_east` run
along the top floor, drop through them and continue on the ground floor to the Final Pillar or
`1_b`, each on one varbit.

A pillar's openings come from its model variant (`pillar_light` four-way, `_1` one, `_2` corner,
`_3` T, `_4` straight) rotated by its placement angle.

| What | Cache |
|---|---|
| Pillar models | `loc.mourning_temple_pillar_<id>`, multiloc on its column's `_up` varbit (0 dark, 1-7 colour) |
| Beam paths | `loc.mourning_light_temple_beam_<name>` on `varbit.mourning_light_temple_<name>` (exception: `beam_2_11_west` uses `2_10_11`) |
| Vertical beams | `varbit.mourning_light_temple_<F>_<C>_up`, beam above floor F |
| Cross locs | `loc.mourning_light_temple_beam_cross_<F>_<C>` on `varbit.mourning_pillar_light_cross_<F>_<C>` (0 off, 1-7 N-S, 8-14 E-W, 15-21 rising, 22-28 crossed) |
| Doors | `loc.mourning_door_<id>` on `varbit.mourning_door_<id>` (0 coloured, 1 white with Pass-through) |
| Colour order | red, yellow, green, cyan, blue, magenta, white = 1-7 |
| Black crystal | `loc.mourning_temple_obsidian_crystal` on `varbit.mourning_light_temple_safe_guards` |
| Chests | `loc.mourning_temple_light_parts_<n>_closed/open`, flags `varbit.mourning_temple_parts_<n>` |
| Dispenser | `loc.mourning_temple_light_wall_lever` and `_collector` (both "Collect") |
| Trays | `varbit.mourning_temple_mirrors_reset_tray`, `..._orange_..` (yellow), `..._red_..` (cyan), `..._blue_..`, `..._fractured_crystal_1/2_..` |
| Rope | `varbit.mourning_temple_rope` (`way_bitmulti`, `way_ropemulti`) |
| Thorgel | `npc.mourning_deathalter_dwarf` on `varbit.mourning_dwarf_vis`, task on `varbit.mourning_dwarf_startedtask` |
| Dwarves' tunnel | `loc.cavewalltunnel_to_temple` (2311, 9792, 0) on `varbit.mourning_light_door_1_c_first_time` |

Doors:

| Door | Tiles | Colour | Opened by |
|---|---|---|---|
| `1_1_east`, `1_1_south` | (1863,4665,0), (1860,4662,0) | yellow | blue |
| `1_13_east`, `1_13_north` | (1863,4613,0), (1860,4616,0) | cyan | red |
| `1_16_west` | (1912,4613,0) | magenta | green |
| `1_16_north` | (1915,4616,0) | yellow | blue |
| `2_4_west`, `2_4_south` | (1912,4665,1), (1915,4662,1) | magenta | green |
| `2_16_west`, `2_16_north` | (1912,4613,1), (1915,4616,1) | blue | yellow |
| `1_b` | (1885,4639,0) | cyan | red |
| `1_c` | (1865,4638-4640,0) | black | white |

Chests (contents from the OSRS wiki loot tables):

| Chest | Loc | Tile | Contents |
|---|---|---|---|
| 1 | `parts_2` | (1917,4613,1) | 2 mirrors, cyan crystal |
| 2 | `parts_3` | (1917,4665,1) | 2 mirrors |
| 3 | `parts_5` | (1880,4659,0) | 2 mirrors, horizontal fractured crystal |
| 4 | `parts_4` | (1858,4613,0) | blue crystal |
| 5 | `parts_6` | (1910,4622,0) | 3 mirrors, vertical fractured crystal |

The dispenser's starting set is 4 mirrors and the yellow crystal (`parts_1` records it was handed
out); 13 mirrors and 5 crystals in all.

## Reference solutions

`TempleSolutions` (test sources) holds the six sections as pillar placements, translated from the
OSRS wiki walkthrough. `TempleLightNetworkTest` checks each opens exactly its doors with only the
pieces the walkthrough has given by then, that the light takes the colours the walkthrough
describes, and that near misses (no crystal, one crystal, a wrong turn, white instead of blue,
mirror 14 facing the wrong way, a single white beam into the Final Pillar) open nothing.

## Persistence

| Var | Kind | Holds |
|---|---|---|
| `varp.mourning2_pillars_1..5` (65464-65468) | server, Perm | Pillar contents, 4 bits each, 7 per varp, `TempleGeometry.adjustable` order |
| `varbit.mourning2_puzzle_version` (65806) | server, Perm | Schema version (1); 0 = never saved |
| `varp.mourning2_thorgel_1..2` (65469-65470) | server, Perm | Delivered list items, one bit each |
| `varbit.mourning2_thorgel_ticket/book/key` (65807-65809) | server, Perm | The list's random picks, 1-3 |
| `varp.mourning2_shadow_peace` (65472) | server, Temp | 1 while done and carrying the trinket; read by `stalk.mourning_shadow` (25) |

Pillar nibbles: 0 empty (or a preset mirror at its start), 1-6 mirror N/E/S/W/up/down, 7-11
yellow/cyan/blue/horizontal fractured/vertical fractured. An all-zero record is the untouched
temple, so existing characters need no migration; an unknown nibble reads as the default. Beam,
pillar and door varbits are derived and recomputed from the saved pillars after every change, on
login and on entering the temple; they are never read back.

## Item conservation

The pieces exist in the pillars, the dispenser tray and the player's inventory, equipment or bank.
Only chests (once each) and the dispenser create them. A reset empties the pillars into the tray and
tops it up to what the player is owed (starting set plus opened chests) minus what they still hold
anywhere, so a destroyed piece comes back and nothing can be duplicated. Collecting hands over as
much as fits and leaves the rest. Pillar changes delete or add the item and save the pillar in the
same tick, after re-checking everything once the dialogue ends. Mirrors, yellow, cyan and both
fractured crystals carry the cache's no-bank param; the blue crystal does not.

## Known deviations and missing dependencies

- The Abyss, Guardians of the Rift and the achievement diaries are not on this server. The Death
  Altar's ruins are only reachable through the temple or the dwarves' tunnel, and death runes can be
  crafted there before completion, as in OSRS. `MourningsEndPart2Quest.unlocked` is the hook for
  the Abyss's death rift when it exists.
- Song of the Elves is not implemented; its quest row already lists this quest as a requirement.
- Shadows stop hunting a trinket carrier after the quest; that they also never retaliate is not
  modelled (no npc retaliation hook).
- Dialogue, the scrawled notes and Edern's journal are in this server's words, following the OSRS
  transcript's content but not its text. The journal is a summary of its nine entries.
- Messages with no OSRS transcript (empty pillar, mirror placement and rotation, dispenser, rope,
  low wall, trap, slip, chest contents) are this server's.
- Animations without a verified OSRS source: the wall-support jumps reuse the Brimhaven agility
  arena handhold sequences, the traps the `human_dodge_rf` sequences, the dispenser the generic
  lever pull with `seq.mourning_temple_lever` on the lever itself, the chisel `human_crafting`.
- The temple doorway is sealed in the map by `loc.inviswall`; the server moves the player across it
  when they walk up to it, so they can enter until the safeguards are restored, and afterwards only
  with the trinket ("A strange force blocks your path."). The blade traps are handled the same way
  across their `inviswall_serverside` walls.
- Fractured crystals fitting only four-way pillars is this server's reading of "doesn't seem to fit
  right in this pillar"; both reference solutions use four-way pillars.
- The north straight staircase on the middle floor lands in a one-tile pocket walled off by loose
  bricks in the map; it only leads back up.
- `HuntCodec` read a hunt condition's varp as a signed short, so any custom varp above 32767 (this
  quest's and Monkey Madness's) decoded negative; it now reads it unsigned.

## Manual walkthrough (live client)

Prerequisites: Part I complete (or `assume-completed`), the disguise, a chisel, a rope, food or
Protect from Melee. `::queststage` and `::item` speed things up.

1. Lletya, Arianwyn: accept. Journal mentions the Headquarters.
2. Headquarters basement in disguise, Essyllt: the new key. The west door says it is locked without
   it and opens with it.
3. Walk west through the mines; the dark beasts attack. At the bodies (x 1918-1934) the player
   remarks on the dig team. Search the guard by the north wall for Edern's journal; read it.
4. Walk into the temple doorway (1917, 4638-4640); the player glides inside. East stairs up, south
   ladder up, middle stairs down and up to the north, the black crystal at (1908, 4638, 2): Search,
   then chisel.
5. Arianwyn: Eluned turns the sample into the new crystal (watch her chathead).
6. Middle floor, dispenser at (1913, 4639): Collect, reset, Collect again: 4 mirrors and yellow.
   Search the guard beside it for the colour wheel and notes.
7. Chest 1: mirrors at 2_9 north, 2_7 west, 2_6 south, yellow in 2_11, mirror 2_15 east. Watch the
   beams turn yellow at 2_11 and the blue door turn white. Cross the wall supports (eight jumps; a
   slip drops to the ground floor for 5 damage), pass the door, open the chest.
8. Continue with the walkthrough's chests 2-5 (or `TempleSolutions`). Check the fixed magenta turns
   cyan light blue, the fixed green turns white green, the rope shortcut, the blade traps (dodge or
   5 damage and a step back).
9. Final arrangement: the cyan door by the middle stairs opens with mirror 14 facing east; inside,
   turn it west and the black door turns white. Step through: Thorgel's scene, the list (if no
   talisman/tiara/cape). Hand items in over several trips; read the list in between.
10. Enter the ruins with a death or catalytic talisman or tiara, use the new crystal on the altar
    (no Runecraft level needed), exit, turn mirror 14 back east before leaving.
11. Use the charged crystal on the black crystal: it turns into the repowered crystal for this
    player only.
12. Arianwyn: completion scroll, 2 QP, 60,000 Agility XP, the trinket. Without the trinket the
    doorway now blocks; with it the shadows leave the player alone. The slayer ring's Dark Beasts
    teleport now works.
13. Log out mid-puzzle and back in: the beams and doors come back as they were. A second account in
    the temple sees its own beams.
