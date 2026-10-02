# Recruitment Drive

Temple Knight series #1. Sir Amik Varze puts the player forward; Sir Tiffy Cashien takes them,
empty-handed, to the Temple Knight training grounds, where they must pass five tests in a row.

Code: `content/quest/src/main/kotlin/org/rsmod/content/quest/area/falador/recruitmentdrive/`.
Tests: `content/quest/src/test/kotlin/.../recruitmentdrive/` (`RecruitmentDriveCacheTest`,
`RecruitmentDriveInteractionTest`).

Sources: the OSRS wiki quest page, quick guide and `Transcript:Recruitment_Drive`, the item pages
for every Miss Cheevers item (`??? mixture`, `Tin (Recruitment Drive)`, ...), `Transcript:Sir_Tiffy_Cashien`,
`Spawning` (respawn tiles), the jingle pages (cache ids), and the RS3 transcript where the OSRS one
is incomplete (Miss Cheevers's searches and messages, Sir Tinley's nine seconds). Room geometry,
multilocs and varbits come from the cache and are pinned by `RecruitmentDriveCacheTest`.

## Requirements and start

- Black Knights' Fortress and Druidic Ritual, through `QuestRequirements` (so the server's quest
  requirement policy applies). No quest point requirement; no gender requirement or makeover.
- Sir Amik Varze ("Do you have any other quests for me to do?") starts it. Under the default
  `assume-completed` policy he still offers it, so it can be played for its rewards.
- Stage: `varbit.rd_main` on `varp.recruitmentdrive` (0, 1 started, 2 complete).

## The attempt

| What | Where |
|---|---|
| Room order (index into the 1,800 legal orders, +1) | `varbit.rd_order` on server varp `varp.rd_session` (Perm) |
| Passed rooms | cache `varbit.rd_room1..7_complete` (Perm) |
| Each room's answer (missing statue, Ren's riddle, Hynn's riddle) | `rd_statue_answer`, `rd_riddle`, `rd_logic_riddle` on `varp.rd_session` |
| Room puzzle state | cache multiloc varbits (`rd_foxleft`.., `rd_room_order`, `rd_room6_stone_door`, `rd_got_*`, ...) and `varp.rd_session` |
| Lock wheels, patience count | `varp.rd_lock` (Temp) |
| Chosen respawn | `varbit.respawn_point` on `varp.respawn_point_state` (Perm) |

- Every attempt is five different rooms, always including Sir Kuam Ferentse's, in a uniformly random
  order (`RoomOrder`). Accepting the quest rolls nothing; Sir Tiffy's teleport rolls the order.
- The grounds (map square 38_77, all seven rooms) are a private instance per visit
  (`TestingGrounds`, via `QuestInstances`); scripts work in world tiles and translate.
- `RecruitmentTesting` runs the visit: enter room, pass (marks the room, plays its jingle, the
  observer congratulates; the fourth and fifth passes have Sir Kuam's special lines), fail (observer
  line, back to Falador Park, attempt cleared, Sir Tiffy's "jolly bad luck"), quit (a room's entrance
  portal: attempt cleared), proceed (a passed room's exit door or portal).
- Every room action checks: inside the player's own copy, the room is the current one, the player
  and the loc are in that room, and the *attempt number* is current (it changes on every room
  load, exit, death and logout), so stale dialogue continuations and repeated clicks do nothing.
- Leaving a room deletes only the grounds' own items (`SessionItems`) from inventory and worn slots.
- After the fifth pass the player is put back in Falador Park; Sir Tiffy completes the quest.

## The tests

- **Sir Kuam Ferentse / Sir Leye** (`CombatRoom`): the four steel weapons are spawned on the room's
  tables; Sir Leye (`npc.rd_combat_npc_room_3`, level 20) is spawned for, owned by and attacking only
  the player (`SirLeyeAttackHook`). Any weapon hurts him; when he dies the finishing blow's weapon
  (recorded from the hit) and the weapon held now must both be the steel warhammer or nothing, else
  the test fails. Prayer allowed. Death is a normal unsafe death: Hardcore Ironmen lose their
  status through the existing `HardcoreIronmanDeathHook`.
- **Sir Spishyus** (`CrossingRoom`, `RiverCrossing`): pieces are auto-worn when picked up (fox in the
  weapon slot, chicken shield, grain cape — the cache's own wearpos), removed to set down on the
  current bank. Crossing with more than one is refused; walking away from fox+chicken or
  chicken+grain fails (the fat fox / empty sack is shown). Solved = all three set down on the far
  bank, judged from the vars, so any legal sequence passes.
- **Lady Table** (`StatueRoom`): the 12 statue multilocs on `varbit.rd_room_order` hold 12 layouts,
  each missing a different statue (read from the cache), plus the full set (value 0). One layout is
  picked per attempt; after 17 cycles (10 s) the full set returns, but not while the player is in a
  dialogue (OSRS wiki). Touching early only messages; the right statue passes, any other fails.
- **Sir Ren Itchood** (`AcrosticRoom`): the six transcript riddles (BITE, TIME, FISH, MEAT, LAST,
  RAIN), each with first/different/final clue, shown with `<br>` line breaks. The door opens
  `interface.rd_combolock`; the word is checked against this player's riddle; one wrong word fails.
- **Ms. Hynn Terprett** (`LogicRoom`): five riddles worded as the transcript; fingers (count 0),
  daughter (count 10), false statements (option 3), fate (wolves), buckets (bucket A).
- **Sir Tinley** (`PatienceRoom`): after his clue, 15 cycles (9 s) with no action. A soft timer fails
  the test on a step, a route, an interaction (taking the hourglass included) or anything that runs
  `ifClose` (item/equipment ops, `stopAction` emotes — seen as the `queue.rd_patience_watch` marker
  vanishing). Keepalives, chat, camera and interface redraws don't touch any of these.
- **Miss Cheevers** (`ImprovisationRoom`, `Alchemy`): shelves (each vial once per attempt, on the
  cache's `rd_got_*`/`rd_spare_water`), bookcases (magnet, knife, notes after begging twice), crates
  (tin, wire, chisel — re-searchable when not carried), chest (shears).
  First door: spade on Bunsen burner (head + ashes), head into the door, cupric sulfate, vial of
  liquid (reaction), pull. Second door: gypsum and liquid into the tin in either order, impression on
  the chained key, tin and cupric ore powder in either order, Bunsen burner, knife/chisel/wire, key on
  the door. Wrong mixtures follow the wiki (hot/warm/horrible mixtures, strange tin, laughing gas).
  Every use validates the exact slots before consuming anything.

## Rewards and unlocks

- 1 QP; 1,000.5 Prayer, Herblore and Agility XP (stored as 10,005 tenths each, × the player's xp
  rate × the global rate — 1,000.5 at the default 1.0); 3,000 coins and an initiate sallet (given
  with `invAddOrDrop`, never twice: only at stage 1 with all five passed).
- Initiate armour (`InitiateArmourWearHook`) needs the quest; 20 Defence / 10 Prayer come from the
  cache's stat-requirement params. The harness unpacks into the three pieces.
- Sir Tiffy afterwards: shop (`inv.templeknight_armoury1`, cache prices 6,000 / 10,000 / 8,000 /
  20,000), Gaze of Saradomin explanation, switching respawn Lumbridge <-> Falador (2970, 3342). The
  quest never changes the respawn itself. `PlayerRespawnHook` gained a `respawnPriority`; the chosen
  point answers after every activity's own respawn hook.

## Logout, death, teleports

- Logout or death in the grounds ends the visit: Sir Leye and the copy go, the grounds' items and the
  room's state go, the order, passed rooms and the room's answer stay. Talking to Sir Tiffy resumes at
  the first room not passed (RS3 wiki behaviour for teleporting/dying out; the OSRS pages are silent).
  The player logs back in at Falador Park (instance exit coord); `varbit.rd_roomlogout` marks a visit
  that ended without an exit so the login cleans up even after a crash.
- Teleports out of the grounds are refused (only Exempt moves — the portals and Sir Tiffy — work),
  so no room item can leave.

## Known deviations / unverified

- The grounds are instanced; in OSRS they are shared static rooms with per-player varbits.
- Teleporting out is refused (RS3 allowed home teleport; OSRS unverified).
- Not checked in a live client: bridge walk (one `exactMove` per tile, no special animation), the
  statue swap visuals, the lock interface layout, the chest swap, Sir Leye's behaviour.
- Tiles chosen (walkability verified from the cache, positions not sourced): weapon order on the two
  tables, Sir Leye's spawn (2460, 4962), the Falador Park return tile (2997, 3374).
- Crate-to-item mapping by tile is a best reading of contradictory guides (tin: middle crate beside
  the chest; wire: the crates next to it; chisel: southernmost crate).
- Messages not found in any transcript: "This door is locked.", "You mix the two vials together.",
  "You empty the vial into the mixture.", inventory-space messages, the teleport refusal and the
  Sir Leye attack refusal. Miss Cheevers's notes are summarised, not quoted.
- Undocumented mixture edge cases: warm mixture into the tin makes the strange tin; the same layer
  twice, or anything on the finished key tin, does nothing.
- Sir Tiffy's "Do you have any jobs for me yet?" uses his later "still organising" line because
  Wanted! is not implemented.

## Missing dependencies

- Wanted! is not implemented (its requirement check can use `RecruitmentDriveQuest.unlocked`).
- No achievement diary framework: the Medium Falador Diary tasks are not wired.
- No title system: the Initiate rank exists only in the journal.
- No other respawn points exist on the server (Camelot, Kourend, Ferox...): `varbit.respawn_point`
  holds Lumbridge (0) or Falador (1).

## Manual walkthrough (live client)

1. `::queststage quest_blackknightsfortress 4` and complete Druidic Ritual (or rely on the policy).
   Talk to Sir Amik Varze (2nd floor, west tower) -> "Do you have any other quests..." -> Yes.
2. With items: Sir Tiffy (Falador Park bench) refuses. Bank everything, "Yes, let's go!": the
   teleport puts you in the first room of your order, in your own instance; the observer speaks.
3. Per room, check: portal at the start quits to Falador (attempt cleared); exit door says locked
   until passed, then leads to the next room; jingle on pass; items vanish between rooms.
   - Kuam: take the warhammer from the table, kill Sir Leye (overhead "No blade may defeat me!");
     repeat with the sword equipped for the kill -> fail. Check another player can't attack him.
   - Spishyus: pick up the chicken (appears worn), Cross, Remove it, Cross back... (7 moves); try
     crossing with two -> refused; leave fox+chicken -> fat fox, fail.
   - Lady Table: screenshot; after ~10 s the lights dim line; touch the newcomer. Talk to her during
     the 10 s and check the swap waits until the chat closes.
   - Ren: ask clues, read the first letters, open the door, dial the word, Enter.
   - Hynn: answer by count box or option.
   - Tinley: talk, then don't touch anything for 9 s; retry and click the floor -> fail.
   - Cheevers: follow the two door sequences above; read the notes after asking her twice.
4. After the fifth pass you're back in Falador Park; talk to Sir Tiffy: quest scroll, 3,000 coins,
   sallet, the respawn message. Talk again: no second reward. Open the shop; switch respawn to
   Falador, die somewhere, confirm (2970, 3342); switch back.
5. Log out mid-room and back in: Falador Park, room items gone, passed rooms kept; Sir Tiffy resumes.
