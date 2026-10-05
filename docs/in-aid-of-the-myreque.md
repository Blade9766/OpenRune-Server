# In Aid of the Myreque

Myreque series #2. Veliaf Hurtz sends the player to Burgh de Rott to find the Myreque a new
hideout; the player wins over the town, clears the inn cellar, repairs the store, bank and furnace,
defeats Gadderanks, escorts Ivan Strom to Paterdomus and remakes the Rod of Ivandis.

Code: `content/quest/src/main/kotlin/org/rsmod/content/quest/area/burghderott/inaid/`.
Tests: `content/quest/src/test/kotlin/.../burghderott/inaid/` (`InAidOfTheMyrequeCacheTest`,
`InAidOfTheMyrequeInteractionTest`).

Sources: the OSRS wiki quest page, `Transcript:In_Aid_of_the_Myreque`, and the item, npc and
location pages (Rubble, Bucket of rubble, Crate, Furnace (Burgh de Rott), Vampyre Juvinate,
Vampyre (attribute), Efaritay's aid, Temple library key, Bookcase (Paterdomus Library), Well
(Paterdomus), Rod mould, Silvthrill rod, Rod of Ivandis, Gadderhammer, Guthix balance, Aurel's
Supplies, Cornelius, Ivan Strom). Every varbit, multiloc, multinpc, combat level and map tile the
scripts use is read from the cache and pinned by `InAidOfTheMyrequeCacheTest`. Dialogue follows
the transcript's order and content in the server's own wording.

## Requirements and start

- In Search of the Myreque (and so Nature Spirit, Priest in Peril, The Restless Ghost), through
  `QuestRequirements`. The cache quest row lists Crafting 25, Mining 15 and Magic 7; its
  `requirementCheckSkillsOnStart` is false, so skills are checked where they are used:
  Mining 15 (base level, not boostable) on the rubble, Crafting 25 by the Crafting skill when the
  silvthrill rod is made, Magic 7 (current level, boostable) by Lvl-1 Enchant. Agility 25 is
  already needed to squeeze into the Myreque hideout (In Search of the Myreque).
- Veliaf Hurtz in the old hideout starts it once In Search of the Myreque is complete. The
  `MyrequeMembers` script hands Veliaf, Ivan, Polmafi and Radigad to `InAidHollows`; no second
  handler is registered on them.
- Stage: cache `varbit.myreque_2_quest` on `varp.myreque2_multivar`, endstate 430.

| Stage | Meaning |
|---|---|
| 10 | Accepted Veliaf's request |
| 20 | Admitted to Burgh de Rott (food left in the chest) |
| 30 | A townsperson suggested the inn's cellar |
| 40 | Cellar cleared, plaque found |
| 50 | Offered to help fix up the town |
| 60 | Aurel asked for the roof and wall |
| 70 | Aurel handed over the crate (request fixed) |
| 80 | Crate delivered, store stocked |
| 90 | Cornelius recruited as banker |
| 100 | Furnace lit, Gadderanks arrives |
| 110 | Spoke to Gadderanks, Wiskit and a juvinate; fight on |
| 120 | Gadderanks and both juvinates dealt with |
| 130 | Gadderanks' last words heard (Guthix balance known) |
| 140 | Veliaf reported; return to the Hollows |
| 150 | Veliaf's briefing on Ivan and Ivandis |
| 160 | Polmafi or Radigad told to move |
| 170 | Ivan delivered to Paterdomus |
| 180 | Drezel's library key |
| 190 | The sleeping seven read |
| 200 | Ivandis' coffin inspected |
| 210 | Rod mould made |
| 430 | Complete |

## Personal world state

Every change to Burgh de Rott is a cache varbit the client already reads, so one player's repairs
never show for another:

| What | Var |
|---|---|
| Trapdoor rubble, trapdoor open | `burgh_inn_colapsed_wall`, `burgh_inn_trapdoor` |
| Rubble piles removed (count / which) | cache `burgh_inn_rubble_pile` / server `burgh_rubble_removed` (15-bit mask) |
| Store roof, wall, stocked (crates, shelves, fires) | `burgh_store_roof`, `burgh_store_wall`, `burgh_store_stocked` |
| Crate request and contents | `burgh_food_type`, `burgh_axes_crate`, `burgh_food_crate`, `burgh_tinderbox_crate` |
| Bank booth, wall, banker | `burgh_bank_booth_open`, `burgh_bank_wall`, `burgh_bank_teller` |
| Furnace 0 broken / 1 repaired / 2 fuelled / 3 lit | `burgh_furnace_fix` |
| Tithe npcs 0 none / 1 Gadderanks' party / 2 Veliaf / 3 gone | `blood_tithe_visible` |
| Tithe conversations | `gadderanks_blood_tithe_chat`, `villager_blood_tithe_chat`, `juve_blood_tithe_chat` |
| Gadderhammer handed over | `gadderanks_warhammer_give` |
| Ivan's armour, sickle, food count, food heal | `burgh_ivan_armour_give_*`, `ivan_sickle_give`, `burgh_ivan_food_give`, server `burgh_ivan_food_heal` |
| Route taken | `juvinate_ambush_routetaken` |
| Old hideout emptied | `route_hideout_npcs` |
| Library trapdoor, tomb boards | `burgh_temple_trapdoor`, `ivandis_tomb_boards` |
| Florin has turned the player away | server `burgh_florin_refused` |

Server-only varbits sit on free bits 11-31 of `varp.myreque2_extravar` (defined in
`.data/raw-cache/server/varbit.toml`, ids 65810/65811/65813). `InAidOfTheMyrequeQuest.syncWorld`
derives every var from the stage on login and on every stage change: below a step's window it is
cleared, past it it is set to the finished value, inside it is left alone. Jumps, resets and
reloads therefore never leave stale repairs.

Under the default `assume-completed` policy an unstarted player counts as complete
(`effectiveStage`): the town, bank, store, furnace and Temple Trekking check are open and the
visuals match. Starting the quest for real switches to real progress.

## The quest

- **Gate** (`BurghGate`): the north gate is the only way in (verified by flood fill). It is
  locked to a player until they have spoken to Florin and then used a food item (anything in the
  server's food table) on the open chest; exactly one piece is taken, only on success. Admitted
  players pass through (`PaterdomusDoors.walkThrough`); anyone may leave.
- **Cellar** (`InnCellar`): climb the broken wall, use a pickaxe on the rubble over the trapdoor,
  open it and climb down. While rubble remains the player enters a private copy of the cellar's
  level-1 room with only their remaining piles. Each pile is mined three times
  (`burgh_rubble_a_1..3`) then removed (`a_4`) with a spade or pot into an empty or part-filled
  bucket (three loads per bucket). Only removal is permanent; a mined pile is whole again on the
  next visit, as are items dropped down there. Buckets can only be emptied on the rubble pile
  outside (or anywhere outside the town). Finds: 10 bronze nails and a rock (3rd pile), 5 iron
  nails and broken glass (6th), the dusty scroll (8th), 3 steel and 2 black nails (10th), the
  plaster fragment (12th), 1 mithril nail (14th). With a full pack the scroll/fragment go in the
  bucket and come back from searching the rubble pile. The 15th removal plays the plaque scene.
  After that the trapdoor leads to the cleared room (level 2), and once the Myreque have moved,
  to their base (level 0).
- **Store** (`GeneralStore`, `Repairs`, `BurghRepairs`): roof (up the ladder) and wall, each a
  hammer, 3 planks and 12 nails of any kind. Aurel picks mackerel or snails as he hands over the
  crate; the choice is saved with the crate in one step (leaving earlier rerolls, as in OSRS).
  The crate takes 10 bronze axes, 3 tinderboxes and 10 of the requested food (raw or cooked
  mackerel; thin/lean/fat snails raw or cooked), one or all at once, never notes, never more than
  asked. A lost crate is replaced with its contents. A full crate opens the shop
  (`inv.burgh_general_store`).
- **Bank**: booth = hammer, 2 planks, 8 nails, swamp paste; wall = hammer, 3 planks, 12 nails
  (transcript). Totals: 11 planks, 44 nails. Cornelius is recruited through "What should I do
  now?"; the booth (op1 Bank) and deposit box open from then on.
- **Furnace**: 2 steel bars + hammer, then coal, then a tinderbox. Lighting moves the stage on
  before the Castle Drakan scene, so an interrupted scene costs nothing. The lit furnace is in
  `category.furnace` (quest pack config), so smelting and silver crafting work there.
- **Blood tithe** (`BloodTithe`, `VampyreFights`): talking to all three of Gadderanks, Wiskit and a
  juvinate starts the fight in a private copy of the store: Gadderanks (35), juvinates of level 50
  and 54 (cache levels; the wiki lists two level 54s). Juvinates are never killed: at a quarter of
  their hitpoints, or at 0, they turn to mist. When the first does, Veliaf arrives and attacks the
  other juvinate, then Gadderanks. Defeat is credited to the fight's owner whoever lands the blow.
  Stepping out of the store, dying or logging out abandons the fight; talking again restarts it.
  Gadderanks' last words follow, then Aurel's Gadderhammer (kept by Aurel if there's no room).
  If that scene is cut short Veliaf replays it. Veliaf's report sends the player to the Hollows.
- **Relocation and escort** (`InAidHollows`, `IvanEscort`): Veliaf's briefing, then Polmafi or
  Radigad. Ivan takes a steel med helm, chainbody and platelegs (each once), a silver sickle and up
  to 15 of stew, salmon, cooked snails or cooked slimy eel; nothing comes back. He won't set off
  with a pet out or a pet item carried. Routes: short (through Canifis) = 2 level 75 juvinates,
  long (through the swamp) = 4 level 50s (OSRS wiki). The ambush is a private copy of a Temple
  Trekking swamp clearing. Ivan (40 hp) follows the player; juvinates attack him until the player
  hits them. He eats at half health, strikes back with the sickle, and teleports home at a fifth
  of his health with no food left; then the attempt has failed and the escape path is the way
  out. Dealing with every juvinate while Ivan is there finishes the escort and lands the player
  outside the little Morytanian mausoleum at Paterdomus.
- **Library** (`PaterdomusLibrary`): Drezel's own script hands him over while the quest needs
  him. Pressing him (after asking the other questions) gives the key; the key on the keyhole opens
  the library trapdoor (and closes it again). The middle west bookcase gives The sleeping seven;
  reading it (stage 190) is what unlocks the tomb, the boards and the mould.
- **Rod** (`RodOfIvandis`): hammer on the boards by the old hideout, enter, inspect the coffin,
  soft clay on it gives the rod mould (repeatable if lost). The silvthrill rod is the existing
  Crafting row `dbrow.crafting_silvthrill_rod` (silver bar, mithril bar, sapphire, mould kept,
  Crafting 25, 55 xp) at any furnace. Lvl-1 Enchant on it goes through `MagicRuneManager`
  (standard spellbook, Magic 7, cosmic + water rune or staff); 17.5 Magic xp. Tablets don't work.
  Using the enchanted rod on the Paterdomus well with a rope (kept) gives the Rod of Ivandis (10).
- **Finish** (`BurghHideout`): Veliaf in the new base takes the rod (any charge) from the pack -
  not while wielded - and completes the quest.

## Rewards

2 quest points; 2,000 Attack, Strength, Crafting and Defence xp (through `statAdvance`, i.e. the
player's xp rate times the global rate, as every quest reward on this server; 2,000 each at the
default 1.0); Temple Trekking access (`templeTrekkingUnlocked`); the ability to make the rod.
Rewards run once through `Quest.completeQuest`. Burgh de Rott's services, unlocked earlier, stay.
No extra Gadderhammer is given at completion.

## Integration

- `GadderhammerWearHook`: the Gadderhammer can't be wielded before Gadderanks is beaten. Aurel sells
  a replacement for 3,000 coins once one has been handed over and lost.
- Guthix balance: restore potion + garlic, then + silver dust (22 Herblore, 25 xp each), only after
  Gadderanks' last words.
- Myreque locations: the old hideout's Veliaf/Ivan/Polmafi/Radigad (and Curpile) hide on
  `route_hideout_npcs` once Ivan is delivered; the Burgh base's Myreque are on cellar level 0,
  which the trapdoor only leads to from then on.

## Known deviations

- The cellar, store fight and ambush are private instances (OSRS uses shared rooms or instances
  with personal varbits); the effect for players is the same.
- Which pile yields which find is not documented; see the find schedule above. The wiki rubble page
  lists 21 nails, so 23 must be brought (the quest page's "42 obtainable" is not supported).
- Rubble pile tiles are not in the cache; 15 tiles were chosen so no set of standing piles cuts
  the room off (`InAidOfTheMyrequeCacheTest`).
- The Castle Drakan scene is told over a black screen (the castle is far outside the loaded map).
- Ivan's numbers are invented: juvinates hit Ivan 1-10 (short) / 1-7 (long) at 80% accuracy,
  15% less per armour piece; Ivan's sickle hits 0-3; Veliaf hits 0-8 every 4 ticks.
- Route choice is a chat menu, not the OSRS map interface.
- Efaritay's aid has no charges here (the server has none for the ring).
- The bank booth's Collect option and using items on the deposit box are not gated.
- Invented messages: the chest before Florin, the crate refusals, "nobody running the bank", the
  rubble pile search, the bucket-room message, the escort escape messages.

## Missing dependencies

- No Museum / Historian Minas or kudos system: the 5 kudos (`MUSEUM_KUDOS`) are not claimable.
- Temple Trekking is not implemented; it should check `templeTrekkingUnlocked`.
- Darkness of Hallowvale is not implemented (its quest row requirement comes from the cache).
- No achievement diary framework: Morytania Medium/Hard tasks are not wired.
- No vampyre damage rules exist for the rest of the server: the tier 2 rules here apply only to
  the quest's juvinates. The Rod of Ivandis special attack (Retainer) and using Guthix balance on
  held vampyres are not implemented.
- Sailing (Oarswoman Olga) does not exist.

## Manual walkthrough (live client)

1. Finish In Search of the Myreque. In the old hideout talk to Veliaf, accept.
2. Burgh de Rott gate: Open -> locked, Florin shouts. Talk to Florin, use food on the open chest
   (one piece goes). Gate opens. Ask a citizen about "out of the way" places.
3. Inn: climb the broken wall, use a pickaxe on the rubble, open the trapdoor, climb down. Check
   the 15 piles; mine one three times, climb out and back in (it's whole), then clear all with a
   spade and 5 buckets, emptying them on the pile outside. Plaque scene at the end.
4. Citizen -> fix up the town; Aurel -> roof (ladder) and wall: check they change only for you
   (second account). Aurel -> crate; fill partly, relog, check request and contents; deliver.
5. Bank booth and wall, Cornelius -> banker, bank opens. Furnace: bars, coal, tinderbox, scene;
   then smelt there.
6. Store: talk to Gadderanks, Wiskit, a juvinate. Fight with a silver weapon or Efaritay's aid;
   watch a juvinate mist, Veliaf join, Gadderanks fall; walk out once to see the reset. Hammer,
   then Veliaf's report.
7. Old hideout: Veliaf, Polmafi; give Ivan armour/sickle/food; set off on each route (two
   accounts), let Ivan flee once, use the escape path, retry.
8. Drezel -> key -> keyhole -> trapdoor -> bookcase -> read. Boards, tomb, coffin, clay.
9. Furnace: silvthrill rod. Lvl-1 Enchant. Paterdomus well with rope. Veliaf in the inn cellar:
   quest scroll, 2 QP, xp; talk again for no second reward.
