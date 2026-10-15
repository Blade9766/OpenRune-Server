package org.rsmod.content.quest.area.desert.icthlarin

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Icthlarin's Little Helper.
 *
 * The stage is `varbit.ics_little_var` (bits 0-4 of `varp.main_ics_var`, which also holds the 25
 * door-puzzle tiles and the flashback flag), endstate 26 from `dbrow.quest_icthlarinslittlehelper`.
 * The values are Jagex's own, pinned by the cache's multis: the Wanderer is there for 0-2, the
 * tunnel rock opens from 2, Raetul trades from 16, the town High Priest is away in the pyramid for
 * 16-24 and his ceremony self can be spoken to from 23, and the sarcophagi stop being searchable
 * from 19.
 *
 * Everything else the quest remembers is on its own cache varbits (`varp.ics_little_multi` and
 * `varp.ics_little_multi_extra`, both permanent): which canopic jar was stolen, the embalmer's and
 * carpenter's supplies, the four jar-shelf visibility flags and the sarcophagus loot count.
 * [syncVars] keeps the stage-derived ones right and clears the rest when the quest is reset.
 */
@Singleton
class IcthlarinsLittleHelperQuest : QuestScript(
    QUEST_KEY,
    "varp.main_ics_var",
    rewards {
        xp("stat.thieving", THIEVING_XP)
        xp("stat.agility", AGILITY_XP)
        xp("stat.woodcutting", WOODCUTTING_XP)
        item(CATSPEAK_AMULET)
        extra("Access to Sophanem")
    },
    ItemRewardDisplay(CATSPEAK_AMULET, zoom = 180),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    questVarbit = "varbit.ics_little_var",
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun canStart(player: Player): Boolean = QuestRequirements.hasCompleted(player, GERTRUDES_CAT)

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
     * Townsfolk treat the player as the grave robber from the moment they wake in Sophanem until
     * the jar is back in Klenter's tomb.
     */
    fun isAccused(player: Player): Boolean = stage(player) in STAGE_WOKE_IN_SOPHANEM until STAGE_JAR_RETURNED

    fun jar(player: Player): CanopicJar? = CanopicJar.byMulti(player.ilhJar)

    /**
     * Redraws what the stage decides: Klenter's shade haunts the city while his jar is missing, and
     * the stolen jar's place on the shelf is empty (outside a flashback, where the jar is back in
     * the past). A reset clears every sub-state varbit the quest uses.
     */
    fun syncVars(player: Player) {
        val stage = stage(player)
        if (stage == 0) {
            for (varbit in RESET_VARBITS) {
                setVarBit(player, varbit, 0)
            }
            for (tile in 1..TILE_COUNT) {
                setVarBit(player, "varbit.ics_tile$tile", 0)
            }
            return
        }
        player.ilhKlenterVisible = stage in STAGE_WOKE_IN_SOPHANEM until STAGE_JAR_RETURNED
        val stolen = jar(player)
        for (jar in CanopicJar.entries) {
            val hidden =
                when {
                    stage >= STAGE_JAR_RETURNED -> false
                    jar != stolen -> false
                    player.ilhInFlashback && stage in STAGE_SECOND_FLASHBACK until STAGE_SECOND_FLASHBACK_DONE ->
                        player.vars[jar.shelfVarbit] == 1
                    else -> stage >= STAGE_GAVE_SUPPLIES
                }
            setVarBit(player, jar.shelfVarbit, if (hidden) 1 else 0)
        }
    }

    private fun setVarBit(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != value) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Wanderer</col> in the desert, just west of the " +
            "<col=800000>Agility Pyramid</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            val jar = jar(access.player)
            objective(
                "A strange <red>Wanderer</red> in the desert will tell me of a secret way into " +
                    "<red>Sophanem</red> if I bring her a <red>full waterskin</red> and a " +
                    "<red>tinderbox</red>. She doesn't seem to like my <red>cat</red>.",
            ) {
                visibleWhen { stage in STAGE_STARTED until STAGE_WOKE_IN_SOPHANEM }
            }
            objective(
                "I woke up outside a pyramid in Sophanem with no memory of how I got there. The " +
                    "Wanderer must have hypnotised me. Perhaps something here will jog my memory.",
            ) {
                visibleWhen { stage == STAGE_WOKE_IN_SOPHANEM }
            }
            objective(
                "I remember being inside the pyramid, doing the bidding of my 'mistress'. " +
                    "I need to reach the heart of the pyramid.",
            ) {
                visibleWhen { stage == STAGE_FIRST_FLASHBACK }
            }
            objective(
                "The Wanderer hypnotised me, but I still don't know why. The <red>Sphinx</red> in " +
                    "the city might know something; she seems fond of cats.",
            ) {
                visibleWhen { stage == STAGE_FIRST_FLASHBACK_DONE }
            }
            objective(
                "The Sphinx gave me a token to show the <red>High Priest of Icthlarin</red> in the " +
                    "south-west of the city, so that he will speak with me.",
            ) {
                visibleWhen { stage == STAGE_SPHINX_TOKEN }
            }
            objective(
                "The High Priest wants the burial jar I stole returned to the pyramid. Only a " +
                    "<red>cat</red> can open the pyramid door.",
            ) {
                visibleWhen { stage in STAGE_RETURN_JAR until STAGE_SECOND_FLASHBACK_DONE }
            }
            if (jar != null) {
                objective("My jar has a lid shaped like ${jar.lid} and contains ${jar.organ}.") {
                    visibleWhen { stage in STAGE_RETURN_JAR until STAGE_JAR_RETURNED }
                }
            }
            objective(
                "I really did steal the jar. I need to get past the door puzzle into the " +
                    "<red>western chamber</red> and put it back where I found it.",
            ) {
                visibleWhen { stage in STAGE_SECOND_FLASHBACK_DONE until STAGE_JAR_RETURNED }
            }
            objective("I have returned the burial jar. I should tell the <red>High Priest</red>.") {
                visibleWhen { stage == STAGE_JAR_RETURNED }
            }
            objective(
                "The tomb must be reconsecrated. The <red>Embalmer</red>, south of the temple, " +
                    "needs <red>salt</red>, <red>tree sap</red> and <red>linen</red>, and the " +
                    "<red>Carpenter</red> in the east needs <red>willow logs</red> for a holy symbol.",
            ) {
                visibleWhen { stage == STAGE_PREPARING_CEREMONY }
            }
            objective(
                "The High Priest has begun the ceremony in the pyramid without me. I should take " +
                    "him the <red>holy symbol</red>.",
            ) {
                visibleWhen { stage == STAGE_CEREMONY_STARTED }
            }
            objective(
                "I remember hiding an unholy symbol somewhere in the <red>eastern chamber</red> " +
                    "for my mistress.",
            ) {
                visibleWhen { stage in STAGE_THIRD_FLASHBACK..STAGE_SYMBOL_HIDDEN }
            }
            objective(
                "The Wanderer means to attack the ceremony! I must warn the priests in the " +
                    "<red>eastern chamber</red>.",
            ) {
                visibleWhen { stage in STAGE_THIRD_FLASHBACK_DONE until STAGE_PRIEST_DEFEATED }
            }
            objective(
                "The Wanderer was the Devourer all along. The ceremony is saved; I should speak " +
                    "to the <red>High Priest</red>.",
            ) {
                visibleWhen { stage == STAGE_PRIEST_DEFEATED }
            }
            objective("The High Priest will meet me back in the city, above the pyramid.") {
                visibleWhen { stage in STAGE_CEREMONY_COMPLETE until STAGE_COMPLETE }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "A wanderer in the desert hypnotised me and sent me into Klenter's pyramid in " +
                    "Sophanem, where I stole one of the dead High Priest's canopic jars.",
            )
            line(
                "With the help of my cat and a Sphinx I won over the new High Priest of " +
                    "Icthlarin, returned the jar and helped prepare a ceremony to lay Klenter to rest.",
            )
            line(
                "The wanderer was Amascut, the Devourer, who used me to plant an unholy symbol in " +
                    "the tomb. I saved the priests from one of their own, possessed by her, and " +
                    "Icthlarin himself freed me from her control.",
            )
            line("The High Priest gave me an amulet that lets me understand cats.")
        }

    companion object {
        const val QUEST_KEY = "quest_icthlarinslittlehelper"
        const val GERTRUDES_CAT = "quest_gertrudescat"

        const val STAGE_STARTED = 1
        const val STAGE_GAVE_SUPPLIES = 2
        const val STAGE_WOKE_IN_SOPHANEM = 3
        const val STAGE_FIRST_FLASHBACK = 4
        const val STAGE_FIRST_FLASHBACK_DONE = 5
        const val STAGE_SPHINX_TOKEN = 6
        const val STAGE_RETURN_JAR = 7
        const val STAGE_SECOND_FLASHBACK = 8
        const val STAGE_GUARDIAN_SUMMONED = 9
        const val STAGE_GUARDIAN_DEFEATED = 11
        const val STAGE_SECOND_FLASHBACK_DONE = 12
        const val STAGE_JAR_ROOM_OPEN = 13
        const val STAGE_JAR_RETURNED = 14
        const val STAGE_PREPARING_CEREMONY = 15
        const val STAGE_CEREMONY_STARTED = 16
        const val STAGE_THIRD_FLASHBACK = 17
        const val STAGE_SYMBOL_HIDDEN = 18
        const val STAGE_THIRD_FLASHBACK_DONE = 19
        const val STAGE_PRIEST_POSSESSED = 20
        const val STAGE_PRIEST_DEFEATED = 23
        const val STAGE_CEREMONY_COMPLETE = 24
        const val STAGE_FREED = 25
        const val STAGE_COMPLETE = 26

        const val THIEVING_XP = 4500.0
        const val AGILITY_XP = 4000.0
        const val WOODCUTTING_XP = 4000.0

        const val CATSPEAK_AMULET = "obj.ics_little_amulet_of_catspeak"
        const val UNHOLY_SYMBOL = "obj.ics_little_unholy_symbol"
        const val HOLY_SYMBOL = "obj.ics_little_holy_symbol"
        const val SPHINX_TOKEN = "obj.ics_little_sphinxstatue"
        const val LINEN = "obj.ics_little_linen"
        const val SAP_BUCKET = "obj.ics_little_sap_bucket"
        const val PILE_OF_SALT = "obj.ics_little_pileofsalt"
        const val BAG_OF_SALT = "obj.slayer_bag_of_salt"
        const val SALTWATER_BUCKET = "obj.ics_little_saltwaterbucket"
        const val BUCKET = "obj.bucket_empty"
        const val KNIFE = "obj.knife"
        const val TINDERBOX = "obj.tinderbox"
        const val FULL_WATERSKIN = "obj.water_skin4"
        const val WILLOW_LOGS = "obj.willow_logs"
        const val COINS = "obj.coins"

        const val TILE_COUNT = 25

        const val KLENTER_HAUNT_TIMER = "timer.ilh_klenter_haunt"
        const val KLENTER_HAUNT_CYCLES = 150
        const val PYRAMID_TRAPS_TIMER = "timer.ilh_pyramid_traps"

        private val RESET_VARBITS =
            listOf(
                "varbit.ics_little_jar_multi",
                "varbit.ics_little_carpenter_multi",
                "varbit.ics_metembalmer",
                "varbit.ics_gotsalt",
                "varbit.ics_gotsap",
                "varbit.ics_gotlinen",
                "varbit.ics_liverpot_vis",
                "varbit.ics_lungpot_vis",
                "varbit.ics_stomachpot_vis",
                "varbit.ics_intestinespot_vis",
                "varbit.ics_little_tilecount",
                "varbit.ics_metsphinx",
                "varbit.ics_metcarpenter",
                "varbit.ics_sphinx_robbedcat",
                "varbit.ics_sarcophigi_gotstuffcounter",
                "varbit.ics_stateofmind",
                "varbit.ics_chestempty",
                "varbit.ics_specvis",
                "varbit.ics_givensphinxstatue",
                "varbit.ics_met_sphinx",
                "varbit.ics_given_token",
            )
    }
}

/**
 * The four canopic jars on the shelf in Klenter's western chamber. The one the player steals
 * decides which avatar's apparition guards it and which potion the possessed priest drops.
 * [multi] is the `varbit.ics_little_jar_multi` value Jagex stores for it.
 */
enum class CanopicJar(
    val multi: Int,
    val obj: String,
    val shelf: String,
    val shelfVarbit: String,
    val apparition: String,
    val potion: String,
    val lid: String,
    val organ: String,
) {
    Het(
        multi = 1,
        obj = "obj.ics_little_canopic_jar_liver",
        shelf = "loc.ics_little_pot_liver",
        shelfVarbit = "varbit.ics_liverpot_vis",
        apparition = "npc.ics_little_het",
        potion = "obj.4dose1defense",
        lid = "a man",
        organ = "a liver",
    ),
    Scabaras(
        multi = 2,
        obj = "obj.ics_little_canopic_jar_stomach",
        shelf = "loc.ics_little_pot_stomach",
        shelfVarbit = "varbit.ics_stomachpot_vis",
        apparition = "npc.ics_little_scabaras",
        potion = "obj.4dose1agility",
        lid = "a bug",
        organ = "a stomach",
    ),
    Apmeken(
        multi = 3,
        obj = "obj.ics_little_canopic_jar_intestines",
        shelf = "loc.ics_little_pot_intestines",
        shelfVarbit = "varbit.ics_intestinespot_vis",
        apparition = "npc.ics_little_apmeken",
        potion = "obj.4dose1attack",
        lid = "an ape",
        organ = "intestines",
    ),
    Crondis(
        multi = 4,
        obj = "obj.ics_little_canopic_jar_lungs",
        shelf = "loc.ics_little_pot_lungs",
        shelfVarbit = "varbit.ics_lungpot_vis",
        apparition = "npc.ics_little_crondis",
        potion = "obj.4dose1magic",
        lid = "a crocodile",
        organ = "lungs",
    );

    companion object {
        fun byMulti(multi: Int): CanopicJar? = entries.firstOrNull { it.multi == multi }

        fun byObj(obj: String): CanopicJar? = entries.firstOrNull { it.obj == obj }

        fun byShelf(loc: String): CanopicJar? = entries.firstOrNull { it.shelf == loc }

        val objs: List<String> = entries.map { it.obj }
    }
}
