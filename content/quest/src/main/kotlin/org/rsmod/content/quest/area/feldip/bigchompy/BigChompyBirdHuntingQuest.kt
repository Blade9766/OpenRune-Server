package org.rsmod.content.quest.area.feldip.bigchompy

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Big Chompy Bird Hunting.
 *
 * The stage is the whole of `varp.chompybird`, endstate 65 from
 * `dbrow.quest_bigchompybirdhunting`. The values follow the cache in steps of five, because the
 * `npc.rantz` multinpc indexes the varp directly and only shows the post-quest Rantz at 65; nothing
 * else may share the varp. Everything the quest remembers besides the stage - which seasoning each
 * ogre asked for, whether the arrows handed in were the player's own work, and what the children
 * have already sold - lives on `varp.chompy_state`, and the lifetime chompy tally on
 * `varp.chompy_kills`.
 */
@Singleton
class BigChompyBirdHuntingQuest : QuestScript(
    QUEST_KEY,
    "varp.chompybird",
    rewards {
        xp("stat.fletching", FLETCHING_XP)
        xp("stat.cooking", COOKING_XP)
        xp("stat.ranged", RANGED_XP)
        item(OGRE_BOW)
        extra("The ability to hunt chompy birds")
    },
    ItemRewardDisplay(OGRE_BOW, zoom = 250),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

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
     * Mirrors the stage onto the varbit the ogre fletching recipes gate on, and clears the quest's
     * own flags when it is reset to not started.
     */
    private fun syncVars(player: Player) {
        if (stage(player) == 0) {
            player.chompyState = 0
            return
        }
        if (!player.chompyQuestStarted) {
            player.chompyQuestStarted = true
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Rantz</col>, on the south-east coast of the " +
            "<col=800000>Feldip Hills</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            objective(
                "<red>Rantz</red>, an ogre in the Feldip Hills, wants me to make him some " +
                    "'stabbers' so he can hunt the <red>chompy birds</red> that feed his family.",
            ) {
                visibleWhen { stage == STAGE_STARTED }
                custom(
                    true,
                    "I need <red>achey tree logs</red> for the shafts, <red>wolf bones</red> for " +
                        "the tips and <red>feathers</red> for the flights. He wants at least six " +
                        "<red>ogre arrows</red>, and they have to be my own work.",
                )
                hasItem(OGRE_ARROW, "I have made some ogre arrows.").strike()
            }
            objective(
                "Rantz has his arrows. Now he needs a <red>bloated toad</red> to lure a chompy " +
                    "bird into the clearing south of him.",
            ) {
                visibleWhen { stage in STAGE_GAVE_ARROWS until STAGE_SHOWN_TOAD }
                stageAtLeast(
                    STAGE_ASKED_ABOUT_TOADS,
                    "Fycie and Bugs inflate toads with a blower their father keeps locked away in " +
                        "the <red>cave</red> to the north.",
                ).strike()
                stageAtLeast(STAGE_OPENED_CHEST, "I have lifted the rock off the ogre chest.").strike()
                hasItem(BELLOWS_EMPTY, "I have a pair of ogre bellows.")
                hasItem(BLOATED_TOAD, "I have a bloated toad.").strike()
            }
            objective(
                "I should <red>drop a bloated toad</red> in the clearing south of Rantz and wait " +
                    "for a chompy bird to come down for it.",
            ) {
                visibleWhen { stage in STAGE_SHOWN_TOAD until STAGE_RANTZ_MISSED }
            }
            objective(
                "Rantz cannot hit a chompy bird to save his life, and blames my arrows. Perhaps " +
                    "he will let me <red>try the shot</red> myself.",
            ) {
                visibleWhen { stage == STAGE_RANTZ_MISSED }
            }
            objective(
                "Rantz lent me his <red>ogre bow</red>. I need to bait another chompy bird, shoot " +
                    "it and <red>pluck</red> the carcass.",
            ) {
                visibleWhen { stage in STAGE_GOT_BOW until STAGE_TOLD_TO_COOK }
                hasItem(RAW_CHOMPY, "I have a raw chompy.").strike()
            }
            objective(
                "The ogres want their chompy <red>seasoned</red> and cooked on the spit-roast " +
                    "north of Rantz. I should ask each of them what they want with theirs.",
            ) {
                visibleWhen { stage == STAGE_TOLD_TO_COOK }
                custom(
                    access.player.rantzFlavour == FLAVOUR_ONION,
                    "Rantz wants <red>onion</red> with his.",
                ).strike()
                custom(
                    access.player.rantzFlavour == FLAVOUR_POTATO,
                    "Rantz wants <red>potato</red> with his.",
                ).strike()
                custom(
                    access.player.bugsFlavour != FLAVOUR_UNSET,
                    "Bugs wants <red>${bugsFlavourName(access.player)}</red> with his.",
                ).strike()
                custom(
                    access.player.fycieFlavour != FLAVOUR_UNSET,
                    "Fycie wants <red>${fycieFlavourName(access.player)}</red> with hers.",
                ).strike()
            }
            objective(
                "The seasoned chompy is cooked. I should <red>hand it to Rantz</red>.",
            ) {
                visibleWhen { stage == STAGE_COOKED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Rantz, an ogre in the Feldip Hills, could not feed his family because he had " +
                    "nothing to shoot the chompy birds with, so I fletched him ogre arrows from " +
                    "achey logs, wolf bones and feathers.",
            )
            line(
                "His children showed me the bellows their father had locked in a chest, and I " +
                    "used them to inflate swamp toads into bait.",
            )
            line(
                "Rantz missed every shot, so he lent me his ogre bow. I shot a chompy myself, " +
                    "roasted it on the ogre spit with the seasonings the family asked for, and " +
                    "the three of them sat down to eat.",
            )
        }

    private fun bugsFlavourName(player: Player): String =
        if (player.bugsFlavour == FLAVOUR_CABBAGE) "cabbage" else "equa leaves"

    private fun fycieFlavourName(player: Player): String =
        if (player.fycieFlavour == FLAVOUR_DOOGLE) "doogle leaves" else "tomato"

    companion object {
        const val QUEST_KEY = "quest_bigchompybirdhunting"

        const val STAGE_STARTED = 5
        const val STAGE_GAVE_ARROWS = 10
        const val STAGE_ASKED_ABOUT_TOADS = 15
        const val STAGE_OPENED_CHEST = 20
        const val STAGE_SHOWN_TOAD = 25
        const val STAGE_DROPPED_TOAD = 30
        const val STAGE_CHOMPY_ATE_TOAD = 35
        const val STAGE_RANTZ_MISSED = 40
        const val STAGE_GOT_BOW = 45
        const val STAGE_KILLED_CHOMPY = 50
        const val STAGE_TOLD_TO_COOK = 55
        const val STAGE_COOKED = 60
        const val STAGE_COMPLETE = 65

        const val COOKING_REQ = 30

        const val FLETCHING_XP = 262.0
        const val COOKING_XP = 1470.0
        const val RANGED_XP = 735.0

        /** How many arrows Rantz takes before he is happy to start hunting. */
        const val ARROWS_WANTED = 6

        const val FEATHER_PRICE = 50
        const val FEATHERS_SOLD = 25
        const val TOOL_PRICE = 10

        /** The most bloated toads the player can carry at once. */
        const val MAX_BLOATED_TOADS = 3

        const val FLAVOUR_UNSET = 0
        const val FLAVOUR_POTATO = 0
        const val FLAVOUR_ONION = 1
        const val FLAVOUR_EQUA = 1
        const val FLAVOUR_CABBAGE = 2
        const val FLAVOUR_TOMATO = 1
        const val FLAVOUR_DOOGLE = 2

        const val RANTZ = "npc.rantz"
        const val FYCIE = "npc.fycie"
        const val BUGS = "npc.bugs"
        const val SWAMP_TOAD = "npc.toad"
        const val BLOATED_TOAD_NPC = "npc.bloated_toad"
        const val CHOMPY = "npc.chompybird"
        const val CHOMPY_DEAD = "npc.chompybird_dead"

        const val CAVE_ENTRANCE = "loc.rantzogrecaveentrance"
        const val CAVE_EXIT_LEFT = "loc.rantzogrecaveexitl"
        const val CAVE_EXIT_RIGHT = "loc.rantzogrecaveexitr"
        const val OGRE_CHEST = "loc.chompybird_chest"
        const val OGRE_CHEST_OPEN = "loc.chompybird_chest_open"
        const val SPIT_ROAST = "loc.multi_chompybird_spitroast_entity"
        const val SWAMP_BUBBLES = "loc.swampbubbles"
        const val SWAMP_BUBBLES_DARK = "loc.swampbubbles_swamp"

        const val OGRE_BOW = "obj.ogre_bow"
        const val OGRE_ARROW = "obj.ogre_arrow"
        const val OGRE_ARROW_STACK = "obj.ogre_arrow_5"
        const val BELLOWS_EMPTY = "obj.empty_ogre_bellows"
        const val BELLOWS_FULL = "obj.filled_ogre_bellow3"
        const val BELLOWS_TWO = "obj.filled_ogre_bellow2"
        const val BELLOWS_ONE = "obj.filled_ogre_bellow1"
        const val BLOATED_TOAD = "obj.bloated_toad"
        const val RAW_CHOMPY = "obj.raw_chompy"
        const val COOKED_CHOMPY = "obj.cooked_chompy"
        const val RUINED_CHOMPY = "obj.ruined_chompy"
        const val SEASONED_CHOMPY = "obj.cooked_s_chompy"
        const val CHOMPY_DISPLAY = "obj.chompy_bird_obj"
        const val COINS = "obj.coins"
        const val FEATHER = "obj.feather"
        const val KNIFE = "obj.knife"
        const val CHISEL = "obj.chisel"
        const val BONES = "obj.bones"

        const val ONION = "obj.onion"
        const val POTATO = "obj.potato"
        const val CABBAGE = "obj.cabbage"
        const val TOMATO = "obj.tomato"
        const val EQUA_LEAVES = "obj.equa_leaves"
        const val DOOGLE_LEAVES = "obj.doogleleaves"

        const val BELLOWS_SEQ = "seq.human_chompybird_ogrebellows"
        const val PICK_UP_SEQ = "seq.human_pickupfloor"
        const val TABLE_SEQ = "seq.human_pickuptable"
        const val COOK_SEQ = "seq.human_potterywheel"
        const val OGRE_BOW_SEQ = "seq.ogre_longbow"
        const val TOAD_INFLATE_SEQ = "seq.chompy_toad_inflate"
        const val CHOMPY_LAND_SEQ = "seq.chompy_update_fly_down"
        const val CHOMPY_EAT_SEQ = "seq.chompy_update_attack"
        const val ROAST_SEQ = "seq.roast"

        const val BELLOWS_SPOT = "spotanim.ogre_bellows_use"
        const val TOAD_BURST_SPOT = "spotanim.chompy_toad_exploding"
        const val ARROW_LAUNCH_SPOT = "spotanim.ogre_arrow_launch"
        const val ARROW_TRAVEL_SPOT = "spotanim.ogre_arrow_travel"

        const val BELLOWS_SOUND = "synth.ogre_bellows"
        const val BELLOWS_SUCK_SOUND = "synth.ogre_bellows_suck"
        const val OGRE_BOW_SOUND = "synth.ogre_bow"
        const val SPIT_ROAST_SOUND = "synth.spit_roast"
        const val TOAD_BURST_SOUND = "synth.toad_burst"
        const val TOAD_HISS_SOUND = "synth.toad_hiss"
        const val CHOMPY_SQUAWK_SOUND = "synth.chompy_bird_squak"

        /** Rantz's own patch of the hills; the only place he will shoot a baited chompy. */
        val BAIT_ZONE_SOUTH_WEST = CoordGrid(2631, 2962, 0)
        val BAIT_ZONE_NORTH_EAST = CoordGrid(2639, 2971, 0)

        /** The clearing Rantz points at, in the middle of the bait zone. */
        val BAIT_CLEARING = CoordGrid(2636, 2966, 0)

        /** Where the camera swings round to while Rantz points out the clearing. */
        val BAIT_CAMERA = CoordGrid(2639, 2956, 0)

        /** Inside Rantz's cave, at the foot of the passage up to the main chamber. */
        val CAVE_ARRIVAL = CoordGrid(2647, 9379, 0)

        /** Outside, just south of the cave mouth. */
        val CAVE_EXIT_ARRIVAL = CoordGrid(2630, 2997, 0)
    }
}
