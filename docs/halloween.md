# Halloween event (Trick-or-Treating)

`content/events/halloween` rebuilds the 2022/2023 OSRS Halloween event ("Trick-or-treating
Reloaded") from the assets still in the cache.

## When it runs

The event is live during October, by the server clock (`HalloweenSeason`). Admins can force it:

```
::halloween on     force the event on
::halloween off    force it off
::halloween auto   back to the October calendar
```

While it is live, `HalloweenWorld` puts the Great cauldron and three costumed children outside
Varrock west bank and sends a Generous Treater to one of five Varrock spots every 5–12 minutes,
for about 3 minutes at a time. Turning the event off removes them again.

It also decorates 16 towns (Lumbridge, Varrock, Falador, Draynor, Edgeville, Al Kharid, Port Sarim,
Rimmington, Barbarian Village, Taverley, Burthorpe, Catherby, Seers', Camelot, Ardougne, Yanille)
with Jagex's 2022 decorations: jack-o'-lanterns, turnip lanterns, candy bowls, scarecrows,
gravestones and hay bales. The tiles live in `dbtable.halloween_decorations` and were picked from
the map collision by `DecorationPlacementGenerator` (a test that runs only with
`HALLOWEEN_DECOR_OUT=<file>` set). Placement rules: outdoors, clear of doors, stairs, banks and
anvils, five tiles apart, and solid pieces only against a wall where every neighbouring path stays
connected. To re-pick, edit the town list in the generator, run it, and paste the output.

## Music

In October, area music has a 1-in-3 chance of playing one of the OSRS Halloween tracks (the cache's
`dbtable.music` rows with `holiday = 2`: Grimly Fiendish, Dies Irae, Nox Irae, High Spirits,
Eve's Epinette, Sea Minor Shanty) each time a new area track starts. Within 20 tiles of the Great
cauldron it always plays one. This goes through `MusicAreaTrackHook` in `api/player`, which any
content can bind to swap area tracks.

## How it plays

- **Costumed children**: Talk-to (Jagex dialogue), Holiday-items and Check-costume. They hand out
  the Treat cauldron and unlock every older Halloween reward in Diango's Holiday Item Retrieval,
  so new players can put a costume together.
- **Trick-or-treat**: needs a Treat cauldron and at least three pieces from
  `enum.halloween_costume_items` worn. Men, women and guards in Misthalin/Asgarnia
  (`enum.halloween_trick_or_treaters`) give 3–5 treats, or trick you: a junk item, a
  spider/rat/bat transform (Protect from Magic stops 85%), or a ghost. Each player's last three
  treaters refuse a repeat visit.
- **Ghosts**: talking to one while wearing a ghostspeak amulet grants a ghostly presence, which
  guarantees 5 treats on the next trick-or-treat.
- **Terrifying charm**: made from the four junk items and worn in the ammo slot. It guarantees 5
  treats three times, then crumbles. The spooky cauldron behind Zaff's hands out junk outside the
  event only, as in OSRS.
- **Key treaters**: King Roald, Aggie, Hetty, Duke Horacio, Party Pete (2022) and Ned, Elsie,
  Iffie, the Falador Town Crier and the Lumbridge Guide (2023) give 100 treats once each, with
  their wiki dialogue. Each points you to one you haven't visited yet.
- **Generous Treater**: 100 treats once per visit.
- **Great cauldron**: Deposit, Check and Claim. A gift unlocks every 80 treats deposited
  (`dbtable.halloween_treat_rewards`): cobweb cape, spider hat, the Trick emote, four spider hat
  styles, two Halloween mask sets and two pumpkins. With a full pack, the gift waits until you
  Claim.
- **Treat cauldron**: Check, Change and Emote. It holds at most 8,000 treats.

## State

| What | Where |
|---|---|
| Treats carried / deposited | `varp.halloween_treats`, `varp.halloween_treats_deposited` (perm) |
| Key treaters visited, gifts received | cache varbits `hw22_treated_*`, `hw23_treated_*`, `hw23_reward_received_*` on `varp.hw22_int_2` (perm) |
| Last three treaters, ghostly presence, Generous Treater visit | `varp.halloween_recent_npc_*`, `varp.halloween_temp` (temp) |

## Deviations from OSRS

- The NPC "Trick-or-treat" option sits in the cache all year, because the client cache can't
  change by date. Outside October it only answers that the season is over.
- There are no global community goals. Treat cauldron styles 2–5 unlock at 2,000, 4,000, 6,000
  and 8,000 treats deposited by the player instead (`enum.halloween_cauldron_styles`).
- Great cauldron Check shows a message, not the "~ The Great Cauldron ~" interface.
- Player-to-player trick-or-treating (interface `hw22_trickortreat`) isn't implemented.
- "Change" cycles to the next unlocked style; there is no style picker interface.

## Adding the option to an NPC

NPC config entries merge field by field, but the option list is one field, and the server
config packer keeps only the last entry per id. So an NPC that already has an entry somewhere
(e.g. `.data/raw-cache/server/npcs.toml`) gets `option4 = "Trick-or-treat"` added to that
entry, together with its existing `option1..3`. Only NPCs with no other entry go in
`halloween_npcs.toml`.
