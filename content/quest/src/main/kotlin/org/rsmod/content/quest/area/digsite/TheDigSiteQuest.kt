package org.rsmod.content.quest.area.digsite

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Dig Site.
 *
 * The stage lives in `varp.itexamlevel` (131), the varp the client's quest journal reads, and runs
 * 0..9 from `dbrow.quest_digsite`. Nothing in the cache pins the intermediate values, so they are
 * the exam the player has passed plus the two steps either side of the exams.
 *
 * Everything else is a varbit. The cache already carries the quest's own flags on
 * `varp.itdigsitemulti` - the two multilocs ([BARREL_VARBIT] picks the barrel's sealed/open/gone
 * form and [TABLET_VARBIT] the stone tablet's) plus the rope-on-winch, letter and tea flags - and
 * the rest sit on `varp.digsite_state`, a server-only varp added for this plugin.
 */
@Singleton
class TheDigSiteQuest : QuestScript(
    QUEST_KEY,
    "varp.itexamlevel",
    rewards {
        xp("stat.mining", MINING_XP)
        xp("stat.herblore", HERBLORE_XP)
        item("obj.gold_bar", GOLD_BARS)
    },
    ItemRewardDisplay(TALISMAN, zoom = 110),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearFlagsWhenReset)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** The highest exam the player has passed, 0 to 3. */
    fun examLevel(player: Player): Int =
        when {
            stage(player) >= STAGE_LEVEL3 -> 3
            stage(player) == STAGE_LEVEL2 -> 2
            stage(player) == STAGE_LEVEL1 -> 1
            else -> 0
        }

    /** Whether [student] has handed over their answer for exam [level] (1-3). */
    fun knowsAnswer(player: Player, level: Int, student: DigSiteStudent): Boolean =
        player.digsiteAnswers and answerBit(level, student) != 0

    fun learnAnswer(player: Player, level: Int, student: DigSiteStudent) {
        player.digsiteAnswers = player.digsiteAnswers or answerBit(level, student)
    }

    /** Whether [student] has already asked the player to fetch their lost keepsake. */
    fun askedFor(player: Player, student: DigSiteStudent): Boolean =
        player.digsiteAsked and (1 shl student.ordinal) != 0

    fun markAsked(player: Player, student: DigSiteStudent) {
        player.digsiteAsked = player.digsiteAsked or (1 shl student.ordinal)
    }

    /** `::resetquest` only clears the stage varp; the flag varbits are ours to put back. */
    private fun clearFlagsWhenReset(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.digsiteAnswers = 0
        player.digsiteAsked = 0
        player.digsiteOpalRequested = false
        player.digsiteBricksPrimed = false
        player.digsiteBricksBlown = false
        for (varbit in CACHE_FLAG_VARBITS) {
            setVarBit(player, varbit, 0)
        }
    }

    override fun subTitle(): String =
        "talking to an <col=800000>Examiner</col> in the <col=800000>Exam Centre</col>, south of " +
            "the <col=800000>Digsite</col> east of Varrock."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "An <red>Examiner</red> at the <red>Exam Centre</red> sets the Earth Sciences " +
                    "exams. Passing them is the only way onto the archaeological dig north of " +
                    "there.",
            ) {}

            objective(
                "She gave me an <red>unstamped letter</red>. <red>Curator Haig Halen</red> at the " +
                    "<red>Varrock Museum</red> has to stamp it before I may sit the exam.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LETTER }
            }

            objective("The letter is stamped. I should take it back to an <red>Examiner</red>.") {
                visibleWhen { stage(access.player) == STAGE_STAMPED }
            }

            objective(
                "I failed the first exam. The Examiner suggested I ask the <red>students</red> " +
                    "working on the digsite: each of them has lost something and will trade what " +
                    "they know for it.",
            ) {
                visibleWhen { stage(access.player) in STAGE_FAILED..STAGE_LEVEL2 }
                for (student in DigSiteStudent.entries) {
                    val level = (examLevel(access.player) + 1).coerceAtMost(3)
                    custom(
                        knowsAnswer(access.player, level, student),
                        "${student.journalName} told me their answer.",
                    )
                        .strike()
                }
            }

            objective("I have passed the <red>level 1</red> exam and been given a <red>trowel</red>.") {
                visibleWhen { stage(access.player) == STAGE_LEVEL1 }
            }

            objective("I have passed the <red>level 2</red> exam.") {
                visibleWhen { stage(access.player) == STAGE_LEVEL2 }
            }

            objective(
                "I have passed every Earth Sciences exam. The Examiner suggested I find something " +
                    "on the digsite worth showing the archaeological expert - a <red>level 3 " +
                    "dig</red> holds the best finds. I will need a <red>trowel</red>, a " +
                    "<red>specimen brush</red> and a <red>specimen jar</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LEVEL3 }
            }

            objective(
                "I dug up a <red>strange talisman</red>. <red>Terry Balando</red>, the " +
                    "archaeological expert in the Exam Centre, should be shown it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_TALISMAN }
            }

            objective(
                "The talisman belongs to a god named <red>Zaros</red>. Terry gave me an " +
                    "<red>invitation letter</red> for the private dig shafts; a <red>Digsite " +
                    "workman</red> needs to see it.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_INVITED &&
                        access.player.vars[INVITATION_VARBIT] == 0
                }
            }

            objective(
                "A workman let me use the dig shafts. A <red>rope</red> on a <red>winch</red> " +
                    "lets me climb down; there is a room beyond a pile of <red>bricks</red> that " +
                    "I cannot shift by hand.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_INVITED &&
                        access.player.vars[INVITATION_VARBIT] != 0 &&
                        !access.player.digsiteBricksBlown
                }
                custom(
                    access.player.digsiteBricksPrimed,
                    "I have poured the <red>chemical compound</red> over the bricks.",
                )
                    .strike()
            }

            objective(
                "The bricks are gone. Beyond them lies an altar to Zaros and a <red>stone " +
                    "tablet</red> that <red>Terry Balando</red> will want to see.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_INVITED && access.player.digsiteBricksBlown
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "An Examiner at the Exam Centre south of the Digsite set me the Earth Sciences " +
                    "exams. Curator Haig Halen of the Varrock Museum stamped my letter of " +
                    "recommendation, and three students traded their notes for a teddy bear, an " +
                    "animal skull and a special cup.",
            )
            line(
                "With all three certificates I dug the level 3 sites and turned up an ancient " +
                    "talisman. Terry Balando named the god on it as Zaros and let me into the " +
                    "private dig shafts.",
            )
            line(
                "Doug Deeping pointed me at a chemist's abandoned recipe. Ammonium nitrate, " +
                    "nitroglycerin, ground charcoal and arcenia root blew the bricks aside, and " +
                    "behind them stood an altar to Zaros and the stone tablet I carried back to " +
                    "Terry.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_digsite"

        const val STAGE_LETTER = 1
        const val STAGE_STAMPED = 2
        const val STAGE_FAILED = 3
        const val STAGE_LEVEL1 = 4
        const val STAGE_LEVEL2 = 5
        const val STAGE_LEVEL3 = 6
        const val STAGE_TALISMAN = 7
        const val STAGE_INVITED = 8
        const val STAGE_COMPLETE = 9

        const val MINING_XP = 15_300.0
        const val HERBLORE_XP = 2000.0
        const val GOLD_BARS = 2

        const val AGILITY_REQ = 10
        const val HERBLORE_REQ = 10
        const val THIEVING_REQ = 25

        /** Set once the barrel of nitroglycerin has been levered open; picks its multiloc form. */
        const val BARREL_VARBIT = "varbit.itdigsitebarrel"

        /** 0 shows the stone tablet, 1 the bare floor it lay on. */
        const val TABLET_VARBIT = "varbit.itdigsitetablet"

        /** Set once a workman has been shown Terry's invitation letter. */
        const val INVITATION_VARBIT = "varbit.itexpertletter"

        /** Set once the panning guide has had his cup of tea. */
        const val TEA_VARBIT = "varbit.itdigsitetea"

        const val EXAMINER = "npc.examiner"
        const val EXAMINER_2 = "npc.qip_digsite_examiner_02"
        const val EXAMINER_3 = "npc.qip_digsite_examiner_03"
        const val CURATOR = "npc.curator"
        const val TERRY = "npc.archaeological_expert"
        const val RESEARCHER = "npc.qip_digsite_researcher"
        const val PANNING_GUIDE = "npc.panning_guide"
        const val DOUG_DEEPING = "npc.digworkman2"

        val EXAMINERS = listOf(EXAMINER, EXAMINER_2, EXAMINER_3)

        /** The four workmen with a `Steal-from` op; Doug is one of them, down the west shaft. */
        val WORKMEN =
            listOf(
                "npc.digworkman1",
                DOUG_DEEPING,
                "npc.qip_digsite_digworkman_03",
                "npc.qip_digsite_digworkman_04",
            )

        const val PLAIN_LETTER = "obj.digplainletter"
        const val STAMPED_LETTER = "obj.recommendedletter"
        const val INVITATION = "obj.digexpertscroll"
        const val CERTIFICATE_1 = "obj.level1certificate"
        const val CERTIFICATE_2 = "obj.level2certificate"
        const val CERTIFICATE_3 = "obj.level3certificate"
        const val TROWEL = "obj.trowel"
        const val ROCK_PICK = "obj.rockpick"
        const val SPECIMEN_BRUSH = "obj.specimen_brush"
        const val SPECIMEN_JAR = "obj.specimen_jar"
        const val TALISMAN = "obj.digtalisman"
        const val STONE_TABLET = "obj.zarosstonetablet"
        const val CHEST_KEY = "obj.digchestkey"
        const val CHEMICAL_POWDER = "obj.unidentified_powder"
        const val AMMONIUM_NITRATE = "obj.ammonium_nitrate"
        const val UNIDENTIFIED_LIQUID = "obj.unidentified_liquid"
        const val NITROGLYCERIN = "obj.nitroglycerin"
        const val MIXED_CHEMICALS = "obj.precharcoalmixture"
        const val CHARCOAL_MIXTURE = "obj.postcharcoalmixture"
        const val CHEMICAL_COMPOUND = "obj.digcompound"
        const val GROUND_CHARCOAL = "obj.ground_charcoal"
        const val CHARCOAL = "obj.charcoal"
        const val ARCENIA_ROOT = "obj.arcenia_root"
        const val CHEMICAL_BOOK = "obj.digsitebook"
        const val ANIMAL_SKULL = "obj.rock_sample1"
        const val SPECIAL_CUP = "obj.rock_sample2"
        const val TEDDY = "obj.rock_sample3"
        const val PANNING_TRAY = "obj.tray_empty"
        const val PANNING_TRAY_MUD = "obj.tray_mud"
        const val PANNING_TRAY_GOLD = "obj.tray_gold"
        const val OPAL = "obj.opal"
        const val UNCUT_OPAL = "obj.uncut_opal"
        const val ROPE = "obj.rope"
        const val VIAL = "obj.vial_empty"
        const val PESTLE_AND_MORTAR = "obj.pestle_and_mortar"
        const val TINDERBOX = "obj.tinderbox"
        const val LEATHER_GLOVES = "obj.leather_gloves"
        const val LEATHER_BOOTS = "obj.leather_boots"
        const val CHOCOLATE_CAKE = "obj.chocolate_cake"
        const val FRUIT_BLAST = "obj.fruit_blast"

        /** Every cup of tea the panning guide will take, brewed or nettle, cup or bowl. */
        val TEA_CUPS =
            listOf(
                "obj.cup_of_tea",
                "obj.display_tea",
                "obj.cup_of_nettletea",
                "obj.cup_of_nettletea_milky",
                "obj.chinacup_of_nettletea",
                "obj.chinacup_of_nettletea_milky",
                "obj.poh_claycup_tea",
                "obj.poh_claycup_tea_milky",
                "obj.poh_chinacup_tea",
                "obj.poh_chinacup_tea_milky",
                "obj.poh_giltchinacup_tea",
                "obj.poh_giltchinacup_tea_milky",
            )

        /** The nettle brews the guide grumbles about before drinking. */
        val NETTLE_TEA_CUPS =
            listOf(
                "obj.cup_of_nettletea",
                "obj.cup_of_nettletea_milky",
                "obj.chinacup_of_nettletea",
                "obj.chinacup_of_nettletea_milky",
                "obj.poh_claycup_tea",
                "obj.poh_claycup_tea_milky",
                "obj.poh_chinacup_tea",
                "obj.poh_chinacup_tea_milky",
                "obj.poh_giltchinacup_tea",
                "obj.poh_giltchinacup_tea_milky",
            )

        private val CACHE_FLAG_VARBITS =
            listOf(
                BARREL_VARBIT,
                TABLET_VARBIT,
                INVITATION_VARBIT,
                TEA_VARBIT,
                "varbit.itexaminerletter",
                "varbit.itcuratorletter",
                "varbit.itdigsitewinch1",
                "varbit.itdigsitewinch2",
            )

        private fun answerBit(level: Int, student: DigSiteStudent): Int =
            1 shl ((level - 1) * DigSiteStudent.entries.size + student.ordinal)
    }
}

/** One bit per exam answer, three per exam level; see `TheDigSiteQuest.answerBit`. */
var Player.digsiteAnswers by intVarBit("varbit.digsite_answers")

/** One bit per student who has asked the player to find their lost keepsake. */
var Player.digsiteAsked by intVarBit("varbit.digsite_asked")

/** Set once the student in the purple skirt has asked for an opal. */
var Player.digsiteOpalRequested by boolVarBit("varbit.digsite_opal_requested")

/** Set once the chemical compound has been poured over the bricks. */
var Player.digsiteBricksPrimed by boolVarBit("varbit.digsite_bricks_primed")

/**
 * Set once the bricks have been blown apart. The dungeon is mapped twice, blocked and cleared;
 * see [DigSiteCoords.dungeonLevel].
 */
var Player.digsiteBricksBlown by boolVarBit("varbit.digsite_bricks_blown")

internal fun setVarBit(player: Player, varbit: String, value: Int) {
    if (player.vars[varbit] != value) {
        VarPlayerIntMapSetter.set(player, varbit, value)
    }
}
