# Arceuus runecrafting: dense essence, the Dark Altar, soul and blood runes

Soul runes and Kourend blood runes are bound from dark essence fragments, made in three steps
north of Arceuus. No Kourend favour is needed (OSRS removed it on 10 January 2024).

| Step | Where | Needs | Gives |
|---|---|---|---|
| Chip | Dense runestones (1762, 3856) and (1762, 3844) | Mining 38, Crafting 38 (both boostable), a pickaxe and a chisel | 1 dense essence block every 9 ticks: 12 Mining xp, 8 Crafting xp |
| Venerate | Dark Altar (1716, 3883) | dense essence blocks | every block becomes a dark essence block: 2.5 Runecraft xp and 1 prayer point each (blocks still convert at 0 prayer) |
| Chisel | anywhere | Crafting 38, a chisel | 4 fragments and 8 Crafting xp per dark block, at most 108 fragments carried |
| Bind | Soul Altar (1815, 3854) / Blood Altar (1717, 3829) | Runecraft 90 / 77 | 1 rune per fragment: 29.7 / 23.8 xp each |

## Code

- `content/skills/mining/.../dense/DenseRunestones.kt`: the "Chip" loop. Level, xp and product come
  from the cache's `dbrow.mining_denseessence` row. It lives in the mining module because
  runecrafting cannot depend on mining (mining -> quest -> runecrafting).
- `DenseRunestoneDepletion.kt`: each block leaves the runestone standing with 85.5% (Mining 38)
  to 90.2% (Mining 99) chance, linear in between.
- `content/skills/runecrafting/.../arceuus/DarkEssenceEvents.kt`: Venerate (op or using a block on
  the altar) and chisel-on-block, which keeps chiselling until the blocks run out or the fragment
  cap is reached. `DarkEssence.kt` holds the inventory moves (unit-tested in `DarkEssenceTest`).
- Binding is the existing altar code: `runecrafting_altar_soul` / `runecrafting_altar_blood_kourend`
  rows with `obj.bigblankrune` as their essence.

## Runestone depletion

Each player has their own pair of runestones: the multilocs `arceuus_runestone_base_1/_2` switch on
the cache varbits `arceuus_runestone_1/_2` (bits of the temporary varp `zeah_temp_1`, so both are
fresh after a relog). When a runestone depletes it shows as "Depleted runestone" and the other one
is woken up, so the player walks between them.

## Dark essence fragments

The Jagex obj is `stackable=never`, yet OSRS keeps the fragments in one slot with a count. The
server-side `items.toml` entry sets `stacks = "always"` for `obj.bigblankrune` so transactions merge
them into one stack (needs `buildCache`).

## Not done

- The Kourend & Kebos medium diary's 5% chance of a second block (that diary is not implemented).
- Re-randomising the runestones when the player re-enters the mine (they reset on relog instead).
- Bots: open-air altars with no bank in the loop need their own behaviour (see `docs/bots.md`).
- Exact OSRS chat messages for venerating, chipping and depletion are not documented on the wiki;
  the ones used are close paraphrases.
