# Enter the Abyss (miniquest)

Current (post-March 2022) OSRS flow: meet the Mage of Zamorak in the Wilderness, take his scrying
orb in Varrock's Chaos Temple, be teleported into the Rune Essence Mine by three different
teleporters while carrying it, and hand it back. No quest points, no combat, no mining.

Package: `content/quest/.../area/wilderness/entertheabyss`.

## Cache definitions used

| What | Symbol | Notes |
|---|---|---|
| Quest row | `dbrow.miniquest_entertheabyss` | endstate 4, 0 QP, 1,000 Runecraft xp, requires `quest_runemysteries` |
| Stage | `varp.abyssal_miniquest` (492, perm) | 0 none, 1 sent to Varrock, 2 researching, 3 readings taken, 4 complete |
| Readings | `varbit.rcu_essencespot_{wizardstower,aubury,cromperty,brimstail,wizardsguild}` | one bit each on `varp.abyssal_warp` (491, perm) |
| Dialogue flags | `varbit.abyssal_miniquest_{intro,reconsider,orb,reward}` | on `varp.runemysteries_secondary` (3404, perm) |
| Wilderness mage | `npc.rcu_zammy_mage1` (multi on 492) | stages 0-3 → Talk/Trade, 4 → Talk/Trade/Teleport |
| Varrock mage | `npc.rcu_zammy_mage1_edge` (multi on 492) | hidden at stage 0 |
| Orb | `obj.scrying_orb_empty` / `obj.scrying_orb_full` | Destroy op handled by the generic destroy flow |
| Rewards | `obj.rcu_instruction_book` (Abyssal book), `obj.rcu_pouch_small` | |
| Shops | `inv.darkruneshop_crap` (before), `inv.darkruneshop_uber` (after) | "Battle Runes" |
| Abyssal bracelet | `obj.jewl_runerunning_bracelet_5..1` | |

Spawns: Wilderness mage (3106, 3558), Varrock mage (3260, 3383), Brimstail (2409, 9817), Distentor
(2594, 3089). **Wizard Cromperty had no spawn**; one was added at (2685, 3325) in
`map/npcs/east_ardougne.toml` (`buildCache` needed). `EnterTheAbyssCacheTest` pins all of the above.

## How it hooks in

- `RuneEssenceTeleports.teleportToMine` now runs every `EssenceMineArrivalHook` (Guice set, declared
  in `RunecraftingModule`) **only after the player has really landed in the mine**. A cancelled cast,
  a teleport block, an admin `telejump`, walking in, or any other teleport never reaches it. The
  hook gets the `EssenceMineTeleporter` as a stable source id.
- `ScryingOrbReadings` is that hook. At stage 2 with the empty orb **in the backpack** it sets the
  source's varbit. The count is always worked out from the set varbits. The third source swaps the
  orb to the full one and sets stage 3. Repeating a source records nothing.
- New teleporters: Wizard Cromperty (`areas/city/ardougne`), Wizard Distentor (`areas/guilds`),
  Brimstail (`quest/area/gnomestronghold`). Each uses its exit-portal value (3, 4, 7) and a return
  tile beside the npc. Distentor's 66 Magic gate is still just the existing Wizards' Guild door.
- `AbyssTeleport` reuses the essence teleport's cast, rolls one of the 12 obstacle layouts and lands
  the player in front of that layout's blockage (see below), drains
  prayer to 0 (the prayer drain timer then turns prayers off) and skulls through the new
  `applyAbyssSkull()` in the wilderness module (`SkullSource.ABYSS`, 10 minutes). A worn abyssal
  bracelet uses up a charge instead, and crumbles on its last one. An already-skulled player keeps
  both their skull and their charge. Teleport block / level checks go through the normal teleport
  validator.

## Rewards and the XP rate

The hand-in is one step that can't be interrupted: it deletes the full orb, sets the reward flag,
gives the book (into the orb's slot) and the small pouch (dropped at the player's feet if there is
no room), then sets stage 4. `Quest.completedMiniquest` grants the XP through `statAdvance`.
Under that existing policy, quest XP gets the player's `rate` × `global-rate` from `xp-rates.yml`,
like all other XP: **1,000 at the default 1.0**, 2,000 during a 2× global event. If the reward flag
is already set, completing again pays nothing (no items, no XP). The small pouch is skipped if the
player owns a colossal pouch (same rule as the Dark Mage). An existing small pouch and its stored
essence are left alone, and a second one is given, as in OSRS.

## Orb loss

Readings belong to the player, not the orb (they are player varbits). This matches the wiki: a
destroyed or lost orb is replaced **empty** at no cost to progress. With all three readings already
taken, one teleport from any teleporter refills it. A banked orb is neither replaced nor accepted.

## Deviations and unverified details

- Requirement mode: starting needs `QuestRequirements.hasCompleted(quest_runemysteries)`, so under
  the default `assume-completed` policy players can start it without having really done Rune
  Mysteries. The mine teleporters still check *real* Rune Mysteries completion (this was already
  the case, and their multinpcs only show Teleport at `varp.runemysteries` = 6). Abyss access needs
  *real* miniquest completion, which matches the client's Teleport op.
- Orb chat messages, the "remaining readings" line, the Rune Mysteries refusal lines, Cromperty's
  Teleport-op refusal and the bracelet crumble message are written here, not taken from OSRS.
- The exit-portal values for Cromperty, Brimstail and Distentor (3, 4, 7) are a choice among the
  values the cache shows a portal for. Only the portal's op label (Use/Exit) depends on it.
- The Abyss teleport reuses the essence-mine curse cast graphics. The obstacle messages, the
  missing-tool and rift refusal lines, and the eyes (`seq.sanctuary`) and gap
  (`seq.human_crawling`) animations are recalled from older RuneScape, not taken from OSRS.
- Monsters can still hit a player during an obstacle attempt (OSRS stops them).
- The inner side of each obstacle is unwalkable, so a successful pass moves the player to the
  nearest inner-ring tile. Two gaps (south-west, west-south) share one landing tile.
- The Mage refusing players who wear Saradomin/Guthix items is not implemented: the server has no
  god-item classification.

## The Abyss: obstacles and rifts

`AbyssObstacles` and `AbyssRifts`, in the same package.

- **Layouts.** All twelve gaps between the rings are multilocs (`loc.rcu_outer_multi1..12`) on one
  player varbit, `varbit.rcu_abyssal_generator` (varp 491). Values 0-11 are the twelve layouts.
  Layout *n* puts the blockage at gap *n+1* and the passage across the ring. Each trip in rolls a
  layout, so different players see different layouts at the same time.
- **Attempts.** Rocks (pickaxe, Mining), tendrils (axe, Woodcutting), boils (tinderbox, Firemaking),
  eyes (Thieving) and gaps (Agility) use the standard skilling roll: 0/255 at level 1 rising to
  255/255 at 99, matching the wiki's chart. Pickaxe and axe quality doesn't matter; any one the
  player can use works. Success gives 25 xp. Failing just shows the failure message, and the player
  can try again.
- **Clearing frames.** On a success the varbit plays the obstacle's own frames (12-19: teeth2/3,
  tendrils2/3, boil2/3, eyes2/3). Every obstacle briefly shows them, as the wiki describes for
  rocks. The player is then moved inside and the layout is put back. A `finally` restores the
  layout if the attempt is interrupted.
- **Passage and blockage.** The passage always lets the player through; the blockage does nothing.
- **Rifts.** Air, mind, water, earth, fire, body, cosmic, chaos, nature, law and death rifts land the
  player at the altar's ruins entrance from the `runecrafting_altars` table; no talisman or tiara
  is needed. Cosmic needs Lost City, law needs Troll Stronghold, death needs Mourning's End Part
  II (all through `QuestRequirements`). The law rift also refuses weapons and armour, using the
  same Entrana rule as the monks' ship, now in `lostcity/EntranaRules.kt`.
- **Blood rift.** `loc.abyss_exit_to_blood_parent` is a multiloc on
  `varbit.abyss_blood_rift_last_used`: op1 repeats the last destination and op2 takes the other.
  The true Blood Altar needs Sins of the Father. The `runecrafting_altar_blood` row now has its
  room entrance (3226, 4832), the room's `loc.bloodtemple_exit_portal`, and an exit outside the
  Meiyerditch ruins (3560, 9779). So the existing altar code makes the room's exit portals work.
- **Kourend's blood altar and the soul rift.** Each is locked until the player has crafted runes at
  that Kourend altar (new server-only `varbit.rc_kourend_{blood,soul}_crafted`, set in
  `AltarEvents` when a craft there produces runes). Then the player uses a dark essence block or
  fragments on the rift once; that sets the cache's `varbit.zeah_{blood,soul}_altar_unlocked` and
  doesn't use up the essence. The messages are the official ones from the wiki transcript. Landings
  are beside each altar: blood (1716, 3827), soul (1814, 3852).

- **Abyssal Nexus.** `AbyssNexusPassage`: the outer ring's south tunnel (`loc.rcu_abyss_to_overseer`,
  3039, 4804) moves the player to (3039, 4800) in the Nexus. There is no walking route back. The
  Nexus end (`loc.rcu_overseer_to_abyss`) only answers with its own examine text, "It looks
  impossible to pass through from this side." (my wording for the op, not confirmed OSRS). The
  Abyss's skull and prayer drain already apply.
- **Appendage.** `AbyssNexusAppendage`: "Operate" on `loc.abyssalsire_exit_lever` (3032, 4793) works
  like the Wilderness levers. It plays the lever pull, swaps briefly to its down form
  (`abyssalsire_exit_lever_inactive`), then gives the standard teleport cast and moves the player to
  Lumbridge (3222, 3218). It is the Nexus's way out. The two chat lines are written here, not
  taken from OSRS.

## Not implemented (separate Abyss / follow-on content)

- The true Blood Altar's own ruins entrance (blood talisman/tiara) outside the Abyss.
- The Abyssal Sire fight lives in its own module; see `docs/abyssal-sire.md`.
- Temple of the Eye, Devious Minds, Wanted! and the Wilderness Diary don't exist in this repo. Their
  requirement on this miniquest will come from `QuestRequirements` / the quest dbrows. The Slayer
  code doesn't use this miniquest for Abyssal demon tasks.
- Cromperty's Ardougne Diary "Claim" op.

## Manual client walkthrough

1. `::queststage quest_runemysteries 6` (or finish Rune Mysteries). Walk north of Edgeville to
   the mage (≈3106, 3558). Talk-to: he sends you to Varrock and the journal entry appears.
   Trade shows Battle Runes (basic stock).
2. Chaos Temple, south-east Varrock (≈3260, 3383): a second mage is now there. Choose "Where do you
   get your runes from?", then "Maybe I could make it worth your while?", then "I did it so that I
   could then steal their secrets.", then "Deal." You get one empty orb. With a full backpack you
   get no orb and stay at stage 1.
3. With the orb in your backpack, teleport from Aubury (Teleport op or dialogue). You should see
   "absorbs a reading ... 2 more locations". Repeat with Aubury: "already holds a reading". Exit
   through the portal. Do Sedridor (Wizards' Tower basement), Cromperty (East Ardougne, ≈2685,
   3325), Brimstail (Gnome Stronghold cave) and Distentor (Wizards' Guild, 66 Magic). Any third
   distinct one turns the orb full.
4. Destroy the orb and ask the Varrock mage: "I lost it" gives an empty orb. One teleport refills it.
5. Hand it in. Check for 1,000 Runecraft xp, the book and the small pouch, the miniquest
   completion message, and 0 QP.
6. Wilderness mage: right-click Teleport (or "Could you teleport me to the Abyss?"). You land in the
   outer ring, facing the blockage, with 0 prayer and a skull. Try again wearing an abyssal
   bracelet(5): no skull, and it becomes (4). Trade now shows the bigger stock including blood runes.
7. Walk round the ring. Try a rock with and without a pickaxe, and a low-level and high-level skill
   obstacle. A success briefly turns every obstacle into its broken frames, moves you inside and
   gives 25 xp. Go through the passage too.
8. Inner ring: enter the air rift (you should arrive in the Air Altar). Try the law rift wearing a
   platebody (refused), then without it.
9. Blood rift: op1 goes to the true Blood Altar (with Sins of the Father). Its exit portal returns
   you outside the Meiyerditch ruins. Craft at Kourend's blood altar, use dark essence fragments on
   the rift, then "Exit-through (Kourend)". The rift's first option then switches to Kourend. Do
   the same for the soul rift with soul runes.
10. Outer ring, south (≈3039, 4805): Enter the passage. You land in the Abyssal Nexus. The passage's
   Nexus end refuses you. Pull the appendage on the central room's north-west wall to reach
   Lumbridge.
