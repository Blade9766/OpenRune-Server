package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Underground Pass.
 *
 * The stage is `varp.upass`, endstate 11 from `dbrow.quest_undergroundpass`. Two of the twelve
 * values are fixed by the cache, because the multinpcs of the pass index the varp directly:
 * - [STAGE_UNICORN]: `npc.unicorn_upass` lists five transforms, so the caged unicorn is drawn at
 *   stages 0-4 and gone from 5 on. Prying the boulder loose is what crushes it.
 * - [STAGE_IBAN_DEAD]: Iban, his disciples, the three demons, Kardia, the slaves and the soulless
 *   all list ten transforms, so every one of them leaves the pass at stage 10.
 *
 * Everything the pass remembers besides the stage lives on `varp.ibanmulti`, which carries thirty
 * cache varbits of its own: which orbs have been taken, which paladin has been killed, which
 * ingredient is on the doll, and which of the six Koftiks is standing in the tunnel. The flags
 * with no cache varbit (the orbs burnt in the furnace, the items handed over, the demon amulets)
 * live on `varp.upass_state`, and the player's own path across the grid is a seed in
 * `varp.upass_grid`.
 */
@Singleton
class UndergroundPassQuest : QuestScript(
    QUEST_KEY,
    "varp.upass",
    rewards {
        scroll(
            "3,000 Attack XP",
            "3,000 Agility XP",
            "Iban's staff",
            "Klank's gauntlets",
        )
    },
    ItemRewardDisplay(IBANS_STAFF, zoom = 135),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun inProgress(player: Player): Boolean = isStarted(player) && !isComplete(player)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    /**
     * Puts the six Koftiks where the stage says they should be, and clears the quest's own flags
     * when it is reset. Each Koftik is a multinpc on a varbit of his own whose first transform is
     * the visible tracker and whose second is nothing at all, so hiding the five that are not
     * wanted is a matter of setting their varbits to one.
     */
    fun syncVars(player: Player) {
        val stage = stage(player)
        if (stage == 0) {
            player.upassState = 0
            player.gridSeed = 0
            for (varbit in SUB_STATE_VARBITS) {
                setVarBit(player, varbit, 0)
            }
        }
        for ((varbit, stages) in KOFTIK_STAGES) {
            setVarBit(player, varbit, if (stage in stages) KOFTIK_HERE else KOFTIK_GONE)
        }
        // An orb taken and then lost before the furnace goes back in its cradle.
        for ((index, orb) in ORBS.withIndex()) {
            val lost = player.vars[orbBurntVarbit(index)] == 0 && !player.inv.contains(orb)
            if (player.vars[orbTakenVarbit(index)] == 1 && lost) {
                setVarBit(player, orbTakenVarbit(index), 0)
            }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>King Lathas</col>, in the throne room of " +
            "<col=800000>Ardougne Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            val p = player.player
            objective(
                "<red>King Lathas</red> wants the <red>Underground Pass</red> beneath the " +
                    "mountains opened, so his army can reach his brother <red>Tyras</red>. His " +
                    "tracker <red>Koftik</red> is waiting for me outside the cave in " +
                    "<red>West Ardougne</red>.",
            ) {
                visibleWhen { stage == STAGE_STARTED }
            }
            objective(
                "Koftik is camped by a fire inside the pass. The bridge ahead is raised: he gave " +
                    "me a <red>damp cloth</red> from some charred arrows. Wrapped round an arrow, " +
                    "lit and fired from a bow, it should burn through the <red>guide rope</red>.",
            ) {
                visibleWhen { stage == STAGE_ENTERED }
                hasItem(OILY_CLOTH, "I have the oily cloth.").strike()
                hasItem(LIT_ARROW, "I have an arrow wrapped in the cloth and lit at the fire.")
            }
            objective(
                "The bridge is down and the pass runs east. Past the <red>rope swing</red> and " +
                    "the collapsing <red>grid</red>, four <red>orbs of light</red> hold the " +
                    "darkness back. Randas's journal says they must go into the furnace.",
            ) {
                visibleWhen { stage == STAGE_BRIDGE }
                custom(p.orbsTaken == ORB_COUNT, "I have gathered all four orbs of light.").strike()
                custom(p.orbsBurnt == ORB_COUNT, "I have thrown all four orbs into the furnace.").strike()
            }
            objective(
                "Below the well the pass turns into a prison. Somewhere past the cells and the " +
                    "beacon maze is the caged <red>unicorn</red> whose horn I need.",
            ) {
                visibleWhen { stage == STAGE_WELL }
                hasItem(RAILING, "I pried a piece of railing out of one of the cages.").strike()
                custom(p.mudDug, "I dug through the loose mud at the back of the cage.").strike()
            }
            objective(
                "I have the <red>unicorn horn</red>. The three <red>paladins</red> camped north " +
                    "of here carry badges, and the well by the <red>Doors of Iban</red> wants " +
                    "all four before it will open them.",
            ) {
                visibleWhen { stage == STAGE_UNICORN }
                hasItem(UNICORN_HORN, "I have the unicorn horn.").strike()
                custom(p.badgesHeld == BADGE_COUNT, "I have all three paladin badges.").strike()
            }
            objective(
                "The Doors of Iban are open. Beyond them is Iban's own lair: broken bridges over " +
                    "a pit, and three dwarves who never got out.",
            ) {
                visibleWhen { stage == STAGE_DOORS }
            }
            objective(
                "<red>Niloof</red> says the witch <red>Kardia</red> knows how Iban can be killed. " +
                    "<red>Klank</red> gave me his gauntlets for the spiders.",
            ) {
                visibleWhen { stage == STAGE_DWARVES }
                hasItem(GAUNTLETS, "I have Klank's gauntlets.").strike()
                custom(p.gaveCat == 1, "I brought Kardia her cat back.").strike()
            }
            objective(
                "I took the <red>Doll of Iban</red> from Kardia's chest. Niloof says Iban's four " +
                    "elements are hidden in these caves: his flesh, his blood, his shadow and his " +
                    "conscience. Kardia's old journal should say where.",
            ) {
                visibleWhen { stage == STAGE_DOLL }
                custom(p.ashesOnDoll == 1, "His flesh: the ashes from his tomb, burnt with dwarf brew.").strike()
                custom(p.venomOnDoll == 1, "His blood: the poisoned blood of the spider Kalrag.").strike()
                custom(p.shadowOnDoll == 1, "His shadow: the dark liquid in the demons' chest.").strike()
                custom(p.doveOnDoll == 1, "His conscience: the bones of a dove from the cages.").strike()
            }
            objective(
                "Iban knows I am coming. The doll has to go into the <red>Well of the Damned</red> " +
                    "in his temple, and only followers of Zamorak in <red>monk robes</red> and " +
                    "nothing else may enter.",
            ) {
                visibleWhen { stage == STAGE_DOLL_READY }
                hasItem(DOLL, "I am carrying the doll of Iban.").strike()
            }
            objective(
                "Iban is gone and his temple with him. I should find my way out of the pass and " +
                    "tell <red>King Lathas</red>.",
            ) {
                visibleWhen { stage == STAGE_IBAN_DEAD }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Lathas sent me into the Underground Pass to open a road west, to the lands " +
                    "where his brother Tyras is gathering strength.",
            )
            line(
                "The pass belonged to Iban, son of Zamorak. I burned the bridge rope to get in, " +
                    "put out the four orbs that held the dark back, and cut my way down past the " +
                    "prison, the unicorn and the paladins to his lair.",
            )
            line(
                "Kardia the witch kept a doll in his likeness. With his ashes, his blood, his " +
                    "shadow and his dove in it I dropped it down the Well of the Damned, and the " +
                    "temple came down on top of him. His staff is mine.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_undergroundpass"

        const val BIOHAZARD_QUEST = "quest_biohazard"

        const val STAGE_STARTED = 1
        const val STAGE_ENTERED = 2
        const val STAGE_BRIDGE = 3
        const val STAGE_WELL = 4

        /** Fixed by `npc.unicorn_upass`, which has no transform for this value or above. */
        const val STAGE_UNICORN = 5

        const val STAGE_DOORS = 6
        const val STAGE_DWARVES = 7
        const val STAGE_DOLL = 8
        const val STAGE_DOLL_READY = 9

        /** Fixed by Iban, his disciples, the demons, Kardia and the soulless, who all end here. */
        const val STAGE_IBAN_DEAD = 10

        const val STAGE_COMPLETE = 11

        const val RANGED_REQ = 25
        const val PICKLOCK_THIEVING_REQ = 50
        const val ATTACK_XP_REWARD = 3_000.0
        const val AGILITY_XP_REWARD = 3_000.0

        const val ORB_COUNT = 4
        const val BADGE_COUNT = 3

        /** Values of each Koftik's own varbit: his first transform is the tracker, his second none. */
        const val KOFTIK_HERE = 0
        const val KOFTIK_GONE = 1

        /* Npcs. A multinpc's ops only ever arrive on the type placed on the map, never on the
         * form the player sees, so every hook below is registered on the spawn type. */
        const val KOFTIK_OUTSIDE = "npc.caveguide1"
        const val KOFTIK_BRIDGE = "npc.caveguide2"
        const val KOFTIK_GRID = "npc.caveguide3"
        const val KOFTIK_MAZE = "npc.caveguide4"
        const val KOFTIK_TEMPLE = "npc.caveguide5"
        const val KOFTIK_END = "npc.caveguide6"

        const val NILOOF = "npc.upassdwarf1"
        const val KLANK = "npc.upassdwarf2"
        const val KAMEN = "npc.upassdwarf3"

        const val KARDIA = "npc.cavewitch"
        const val KARDIA_CAT = "npc.cavewitchcat"
        const val UNICORN = "npc.unicorn_upass"
        const val BOULDER = "npc.boulder_upass"

        /**
         * Every npc of the pass is spawned twice by the map data: once as the multinpc that the
         * quest's varbits hide and show, and once as the plain "visible" type its first transform
         * points at. Ops and kills can land on either, so every hook in the quest is registered
         * against both names.
         */
        fun visibleTwin(npc: String): String = "${npc}_vis"

        const val PALADIN_JERRO = "npc.upass_paladin1"
        const val PALADIN_CARL = "npc.upass_paladin2"
        const val PALADIN_HARRY = "npc.upass_paladin3"

        const val KALRAG = "npc.kalrag"
        const val DOOMION = "npc.doomion"
        const val OTHAINIAN = "npc.othainian"
        const val HOLTHION = "npc.holthion"

        const val IBAN = "npc.iban"
        const val DISCIPLE = "npc.ibanmonk"
        const val HALF_SOULLESS = "npc.soulman"
        val SLAVES =
            arrayOf(
                "npc.cave_slave1",
                "npc.cave_slave2",
                "npc.cave_slave3",
                "npc.cave_slave4",
                "npc.cave_slave5",
                "npc.cave_slave6",
                "npc.cave_slave7",
            )

        /* Objs. */
        const val OILY_CLOTH = "obj.damp_cloth"
        const val LIT_ARROW = "obj.litarrow"
        const val UNLIT_ARROW = "obj.unlitarrow"
        const val PLANK = "obj.woodplank"
        const val RAILING = "obj.caverailing"
        const val UNICORN_HORN = "obj.cave_unicorn_horn"
        const val JOURNAL = "obj.upass_journal"
        const val IBAN_BOOK = "obj.old_journal"
        const val DOLL = "obj.ibandoll"
        const val GAUNTLETS = "obj.klanks_gauntlets"
        const val DWARF_BREW = "obj.upassdwarfbrew"
        const val ASHES = "obj.ibans_ashes"
        const val DOVE = "obj.ibansdove"
        const val SHADOW = "obj.ibansshadow"
        const val DEAD_ORB = "obj.caveorb4dot"
        const val IBANS_STAFF = "obj.ibanstaff"
        const val BROKEN_STAFF = "obj.brokenibanstaff"
        const val ROPE = "obj.rope"
        const val SPADE = "obj.spade"
        const val BUCKET = "obj.bucket_empty"
        const val TINDERBOX = "obj.tinderbox"
        const val ZAMORAK_TOP = "obj.zamrobetop"
        const val ZAMORAK_BOTTOM = "obj.zamrobebottom"

        val ORBS = arrayOf("obj.caveorb1", "obj.caveorb2", "obj.caveorb3", "obj.caveorb4")
        val BADGES = arrayOf("obj.paladinbadge1", "obj.paladinbadge2", "obj.paladinbadge3")
        val AMULETS =
            arrayOf("obj.doomion_amulet", "obj.othainian_amulet", "obj.holthion_amulet")

        /** Every arrow that can be wrapped in the oily cloth, paired with its lit form. */
        val ARROW_PAIRS =
            listOf(
                "obj.bronze_arrow" to ("obj.unlitarrow" to "obj.litarrow"),
                "obj.iron_arrow" to ("obj.iron_unlitarrow" to "obj.iron_litarrow"),
                "obj.steel_arrow" to ("obj.steel_unlitarrow" to "obj.steel_litarrow"),
                "obj.mithril_arrow" to ("obj.mithril_unlitarrow" to "obj.mithril_litarrow"),
                "obj.adamant_arrow" to ("obj.adamant_unlitarrow" to "obj.adamant_litarrow"),
                "obj.rune_arrow" to ("obj.rune_unlitarrow" to "obj.rune_litarrow"),
            )

        /* Sounds. */
        const val SOUND_BRIDGE_FALL = "synth.upass_bridgefall"
        const val SOUND_FIRE_ARROW = "synth.upass_fire_arrow"
        const val SOUND_ARROW_LAUNCH = "synth.upass_arrowlaunch"
        const val SOUND_SPRINGTRAP = "synth.upass_springtrap"
        const val SOUND_TRIPWIRE = "synth.upass_tripwire"
        const val SOUND_BIGFIRE = "synth.upass_bigfire"
        const val SOUND_IBAN_LIGHTNING = "synth.upass_iban_lightning"
        const val SOUND_LEVER = "synth.biglever"
        const val SOUND_PORTCULLIS = "synth.portcullis_open"
        const val SOUND_GATE_OPEN = "synth.picketgate_open"
        const val SOUND_LEDGE = "synth.balancing_ledge"
        const val SOUND_DISARM = "synth.upass_disarm_trap"
        const val SOUND_COLLAPSE = "synth.cave_collapse"
        const val SOUND_CAVEIN = "synth.cavein"
        const val SOUND_SWAMP_STEP = "synth.swamp_step"
        const val SOUND_RUMBLE = "synth.mine_rumbling"
        const val SOUND_UNICORN_DEATH = "synth.anger_unicorn_death"
        const val SOUND_DEMON_DEATH = "synth.demon_death"
        const val SOUND_STUNNED = "synth.thieving_stunned"
        const val SOUND_LOCKED_DOOR = "synth.ikov_lockeddoor"
        const val SOUND_FIRE_LIT = "synth.fire_lit"
        const val SOUND_TAP_FILL = "synth.tap_fill"
        const val SOUND_SQUEEZE = "synth.squeeze_in"
        const val SOUND_JUMP = "synth.jump_no_land"
        const val SOUND_GRATE_CLOSE = "synth.grate_close"
        const val SOUND_CHEST_OPEN = "synth.chest_open"
        const val SOUND_DOOR_OPEN = "synth.door_open"
        const val SOUND_PICK = "synth.pick2"
        const val SOUND_PUT_DOWN = "synth.put_down"

        /* Player animations. */
        const val SEQ_SEARCH = "seq.human_pickuptable"
        const val SEQ_PICKUP_FLOOR = "seq.human_pickupfloor"
        const val SEQ_DIG = "seq.human_dig"
        const val SEQ_LADDER = "seq.human_reachforladder"
        const val SEQ_PIPE_SQUEEZE = "seq.human_doublepipesqueeze"
        const val SEQ_ROPESWING = "seq.human_ropeswing_long"
        const val SEQ_THROW_ROPE = "seq.human_throwrope_up"
        const val SEQ_BALANCE = "seq.human_walk_logbalance"
        const val SEQ_BALANCE_STUMBLE = "seq.human_walk_logbalance_stumble"
        const val SEQ_STUMBLE_BACK = "seq.human_stumble_back"
        const val SEQ_CLIMB_DOWN = "seq.human_walk_style"
        const val SEQ_LONGJUMP = "seq.human_longjump"
        const val SEQ_DISARM = "seq.human_pickpocket"
        const val SEQ_BOX_LEVER = "seq.human_boxlever"
        const val SEQ_BOW = "seq.human_bow"
        const val SEQ_STUNNED = "seq.human_stunned"
        const val SEQ_BLOWN_BACK = "seq.human_blown_start"
        const val SEQ_DEATH = "seq.human_death"
        const val SEQ_OPEN_CHEST = "seq.human_openchest"
        const val SEQ_DROWNING = "seq.human_drowning"
        const val SEQ_PICKLOCK = "seq.human_pickpocket"

        /* Loc and npc animations. */
        const val SEQ_LOC_ROPESWING = "seq.ropeswing_long"
        const val SEQ_LOC_SPEARTRAP = "seq.speartrap_release"
        const val SEQ_LOC_SPRINGTRAP = "seq.double_springtrap_release"
        const val SEQ_LOC_SPRINGTRAP_RESET = "seq.double_springtrap_reset"
        const val SEQ_LOC_LOGTRAP = "seq.swinginglogtrap_release"
        const val SEQ_LOC_PORTCULLIS_OPEN = "seq.portcullisopen"
        const val SEQ_LOC_PORTCULLIS_CLOSE = "seq.portcullisclose"
        const val SEQ_IBAN_ATTACK = "seq.sitting_throne_attack"

        /* Spotanims. */
        const val SPOT_STUNNED = "spotanim.stunned"
        const val SPOT_IBAN_CLAW = "spotanim.upass_claw"
        const val SPOT_IBAN_BOLT = "spotanim.ibansbolt"
        const val SPOT_ROCKFALL = "spotanim.rockfall"

        /**
         * Which stages each Koftik is standing in the pass for. They are all spawned on the map,
         * so the five that are not wanted have to be hidden behind their own varbit.
         */
        val KOFTIK_STAGES: List<Pair<String, IntRange>> =
            listOf(
                "varbit.upass_koftik_outside" to 0..STAGE_COMPLETE,
                "varbit.upass_koftik_bridge" to STAGE_ENTERED..STAGE_COMPLETE,
                "varbit.upass_koftik_grid" to STAGE_BRIDGE..STAGE_COMPLETE,
                "varbit.upass_koftik_maze" to STAGE_WELL..STAGE_COMPLETE,
                "varbit.upass_koftik_temple" to STAGE_DOORS..STAGE_DOLL_READY,
                "varbit.upass_koftik_end" to STAGE_IBAN_DEAD..STAGE_COMPLETE,
            )

        /** The cache varbits on `varp.ibanmulti` that the quest owns and a reset has to clear. */
        val SUB_STATE_VARBITS =
            listOf(
                "varbit.upass_found_bridge",
                "varbit.upass_venom_on_doll",
                "varbit.upass_dove_on_doll",
                "varbit.upass_ashes_on_doll",
                "varbit.upass_shadow_on_doll",
                "varbit.upass_caveorb_1",
                "varbit.upass_caveorb_2",
                "varbit.upass_caveorb_3",
                "varbit.upass_caveorb_4",
                "varbit.upass_gavecat",
                "varbit.upass_seen_temple",
                "varbit.upass_lathas_met",
                "varbit.upass_crate_food",
                "varbit.upass_paladin_food",
                "varbit.upass_paladinbadge_1",
                "varbit.upass_paladinbadge_2",
                "varbit.upass_paladinbadge_3",
                "varbit.upass_read_journal",
                "varbit.upass_read_well",
                "varbit.upass_read_iban_book",
                "varbit.upass_brew_tomb",
                "varbit.upass_koftik_chat",
                "varbit.upass_cave_unicorn",
                "varbit.upass_dwarf_food",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }

        /** Landing tiles the quest teleports to, and the windows its area checks use. */
        val CAVE_ENTRANCE_INSIDE = CoordGrid(0, 39, 151, 0, 50)
        val CAVE_ENTRANCE_OUTSIDE = CoordGrid(0, 38, 51, 1, 51)
        val BRIDGE_NORTH = CoordGrid(0, 38, 151, 11, 55)
        val BRIDGE_SOUTH = CoordGrid(0, 38, 151, 11, 50)
        val WELL_LANDING = CoordGrid(0, 37, 150, 55, 61)
        val WELL_TOP = CoordGrid(0, 37, 151, 48, 12)
        val TEMPLE_INSIDE = CoordGrid(1, 33, 72, 34, 39)
        val TEMPLE_OUTSIDE = CoordGrid(1, 33, 72, 30, 39)
        val PASS_EXIT_TUNNEL = CoordGrid(0, 38, 150, 6, 9)
    }
}
