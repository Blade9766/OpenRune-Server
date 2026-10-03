# Abyssal Sire

Module `content/bosses/abyssal-sire` (package `org.rsmod.content.bosses.abyssalsire`). All four
Abyssal Nexus chambers. The Sires, tentacles and respiratory systems are the existing map spawns.

## How it is built

- `AbyssalSire` registers every Sire form with the boss framework. It only claims the Sire's
  combat and hit events, so the generic npc melee AI stays out. The hit hooks, death queue and
  movement locks call into `SireFights`.
- `SireFights` runs each fight from its own per-tick loop, keyed on the stage held in `SireFight`.
  Using a loop rather than the DSL selectors keeps every timer and transition explicit and testable.
- `SireMinions` is a small boss spec for spawns and scions. A minion closes in to melee; now and
  then it switches to ranged (shooting from up to 7 tiles) and later back. Both styles hit through
  protection prayers. Like every npc, a minion drops its target once the player is more than 8 tiles
  (cache `maxrange` + `attackrange`) from where it landed.
- `SireAttackHook` (bound in `AbyssalSireModule`) requires 85 Slayer and an abyssal demon or Abyssal
  Sire boss task, not from Krystilia. It also stops a second player while the first is still fighting
  (they must have hit the Sire within the last 33 ticks).
- The engine teleports an npc home after 500 idle cycles off its spawn tile. The Sire stands still
  for whole phases, so the fight loop keeps its idle counter at zero; only a reset or its death
  returns it to the throne.
- `SireChamber` derives every position from the throne tile. The wiki's row 1/2/3 tiles fall out
  of it exactly.

## The fight

| Stage | Form | What happens |
|---|---|---|
| Asleep | `stasis_sleeping` | Woken by any ranged or magic hit. Melee does nothing. |
| Waking (10 ticks) | → `stasis_awake` | All 6 tentacles wake. A Shadow spell rolled now applies when it wakes. |
| Lungs | `stasis_awake` / `stasis_stunned` | Its HP heals to full after every hit. Ranged and magic damage counts toward disorientation (75), or a Shadow spell disorients it (25/50/75/100%). Disorientation lasts 50 ticks for the Sire and 45 for the tentacles. Respiratory systems take at most 3 per hit unless the tentacles are stunned; while they are, a landed hit deals at least half the attacker's max hit. They take melee only from a halberd, regain 1 HP every 8 ticks, and give 50 Slayer xp for each of the first four. Attacks are spawns or miasma, but only on a player within 10 tiles; with 15 minions alive it only pours miasma. |
| Walk to melee | `wandering` | Half damage. If reduced to 0 it restores to 170. |
| Melee | `puppet` | Every 7 ticks: if the player is within 2 tiles, an arm swipe or tendril flick (max 32, 6 under Protect from Melee) or a double flick (66/26). Otherwise a spawn (miasma instead with 15 minions alive), miasma, or it pulls the player to row 1 and blasts after 3 ticks (max 60). Resets after a minute without the player hitting it. |
| Walk to centre (at 212 HP or less) | `wandering` | Half damage. If reduced to 0 it restores to 85. |
| Panic | `panicking` | Summons 4 spawns. Tentacles wake. Miasma every 6 ticks. |
| Apocalypse (below 140 HP) | `apocalypse` (lower defence) | Pulls the player to row 2. 2 ticks later it explodes (max 96) on anyone within 2 tiles, then spawns 6 more. It keeps spawning up to 15 minions, and after 3 pools only pours miasma while there are fewer than 15. |
| Dying | — | Every minion dies in its dying form and pools stop. The drops roll (`NpcDeath.spawnDrops`, which also runs the Slayer and killcount hooks). The chamber resets and the Sire respawns on its throne the next tick. |

- **Miasma:** lands 2 ticks after the tell and burns for the next 6 ticks, so a player who moves as it appears takes nothing. The centre tile does 10–30, the
  ring around it 2–8, and it poisons (8).
- **Tentacles:** strike a player standing inside their 9×9 footprint every 4 ticks (max 30). The
  wiki's three-tile centre lane is outside every footprint, and the cache test checks this.
- **Spawns:** land on open ground in front of the Sire. While it is still enthroned that means
  just south of the alcove's lip, since the alcove itself is solid and walled off from the arena.
  They become scions after 20 ticks. A spawn or scion that cannot attack the player (out of
  its 7-tile reach, or the player has left) for 50 ticks (30 seconds) dies off, as Zulrah's
  snakelings do.
- **Leaving:** the chamber resets a minute after the player leaves it, logs out or dies.

## Unverified / deviations

- **Unprayed melee max:** the wiki doesn't give the unprayed max of the arm swipe and single flick
  ("moderately high"); 32 is my choice.
- **Tentacles and prayer:** the tentacles' "slight" Protect from Melee reduction isn't applied.
- **Messages:** the attack-gate messages and "The Sire pulls you towards it!" are written here, not
  taken from OSRS. The wiki documents none of them.
- **Form roles:** which of `puppet` and `wandering` is the phase 2 fighting form is a guess (the
  wiki only says "wandering" walks).
- **Walking:** no route leads out of the throne alcove for a 6×6 npc, so when the Sire leaves for
  phase 2 it is placed at its stop as soon as its route ends. The walk to the centre is a real walk.
- **Minion reach:** "unable to attack" means the player is more than 7 tiles from the minion or
  outside its leash; line of sight is not checked.
- **Phase 1 and 2 minion cap:** the wiki documents no limit before the explosion; the 15 it gives
  for after it is reused for both.
- **Minion style switching:** the wiki says minions only use ranged "if they decide to change
  styles" and gives no rate; here each minion switches with a 1 in 20 chance per tick.
- **Pool graphic:** the miasma graphic is re-sent every 2 ticks; its real duration is unknown.

## Tests

- `AbyssalSireCacheTest` (9 tests) checks:
  - forms, sizes and HP;
  - chamber layouts against the npc spawns;
  - the wiki's row tiles;
  - that the phase 3 stop and every row tile are open;
  - row 3 escaping the blast;
  - the tentacle-free lane;
  - that phase 1 spawn tiles are open and lead into the arena;
  - every animation, graphic and Shadow spell;
  - the Sire counting for abyssal demon tasks.
- `AbyssalSireFightTest` (22 tests) runs the real tick loop through every stage, the damage rules,
  the stun timings, spawns maturing, death, both resets and the attack gate.

## Walkthrough (client)

1. Get an abyssal demon task and 85 Slayer, then come through the Abyss tunnel or fairy ring DIP.
   Check that you can't attack without the task or the level.
2. Throw a ranged hit at the sleeping Sire. It wakes and the tentacles rise. Cast Shadow Barrage:
   the Sire and its tentacles go limp. While they're stunned, kill the four respiratory systems
   (outside a stun they take 0–3).
3. The Sire moves out. Melee it from row 1 and watch for the pull-in blast if you back off.
4. At 212 HP it moves to the centre and four spawns appear. Stay in the centre lane to avoid the
   tentacles. Below 140 you're pulled to row 2: run two tiles south before it explodes.
5. Kill it. Its minions die with it, drops appear, the vents revive and the Sire is back on its
   throne.
