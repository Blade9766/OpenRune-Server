package org.rsmod.content.quest.area.burthorpe.eadgarsruse

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Eadgar's Ruse.
 *
 * The stage lives in `varp.eadgar_quest` (335), 0..110 from `dbrow.quest_eadgarsruse`, using the
 * values RuneLite's quest helper reads: 10 started, 15 asked Eadgar, 20/25 met Burntmeat (before or
 * after Eadgar), 30 parrot wanted, 50 plan explained, 60 parrot hidden, 70 handing in the items, 80
 * truth potion wanted, 85 parrot to fetch, 86 parrot fetched, 87 fake man made, 90 Burntmeat fooled,
 * 100 storeroom unlocked and 110 complete. What has been handed to Eadgar and asked of Parroty Pete
 * sits in server-only varbits on `varp.eadgar_ruse_bits`.
 */
@Singleton
class EadgarsRuseQuest : QuestScript(
    QUEST_KEY,
    "varp.eadgar_quest",
    rewards {
        xp("stat.herblore", HERBLORE_XP)
        extra("Trollheim Teleport spell")
    },
    ItemRewardDisplay(GOUTWEED, zoom = 110),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::resetFlags)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** The goutweed and the storeroom stay open to a player who is done with the quest. */
    fun unlocked(player: Player): Boolean =
        isComplete(player) || (stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY))

    fun hasStoreroomAccess(player: Player): Boolean = stage(player) >= STAGE_STOREROOM || unlocked(player)

    fun meetsQuestRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, TrollStrongholdQuest.QUEST_KEY) &&
            QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL)

    fun meetsHerbloreRequirement(player: Player): Boolean = player.herbloreLvl >= HERBLORE_REQ

    fun stillNeeded(player: Player): Supplies =
        Supplies(
            logs = if (player.erLogsGiven) 0 else 1,
            chickens = CHICKENS_NEEDED - player.erChickensGiven,
            grain = GRAIN_NEEDED - player.erGrainGiven,
            robe = if (player.erRobeGiven) 0 else 1,
        )

    private fun resetFlags(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.erLogsGiven = false
        player.erChickensGiven = 0
        player.erGrainGiven = 0
        player.erRobeGiven = false
        player.erAskedPeteWater = false
        player.erAskedPeteFood = false
    }

    override fun subTitle(): String =
        "talking to <col=800000>Sanfew</col> upstairs in the <col=800000>Taverley</col> herb shop."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "Sanfew needs the herb the trolls call <red>goutweed</red> for the purification " +
                    "ritual. His friend <red>Eadgar</red>, who lives in a cave at the top of " +
                    "<red>Trollheim</red>, may be able to help.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }
            objective(
                "Eadgar says goutweed is used in troll cooking. I should ask one of the cooks in " +
                    "the <red>Troll Stronghold</red> kitchen.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ASKED_EADGAR }
            }
            objective(
                "<red>Burntmeat</red>, the troll cook, won't share his secret unless I bring him a " +
                    "tasty human for his stew. I should tell <red>Eadgar</red>.",
            ) {
                visibleWhen { stage(access.player) in STAGE_COOK_FIRST..STAGE_MET_COOK }
            }
            objective(
                "Eadgar has a plan, but first he needs a <red>parrot</red>. The zoo in " +
                    "<red>Ardougne</red> has an aviary.",
            ) {
                visibleWhen { stage(access.player) == STAGE_PARROT_WANTED }
            }
            objective(
                "Eadgar wants the parrot to learn what a human says to a troll. I should hide it " +
                    "somewhere in the <red>troll prison</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_PLAN }
            }
            objective(
                "The parrot is hidden under the rack in the troll prison. Eadgar needs " +
                    "<red>logs</red>, <red>${CHICKENS_NEEDED} raw chickens</red>, " +
                    "<red>${GRAIN_NEEDED} bundles of grain</red> and some <red>dirty clothes</red> " +
                    "to make a fake human.",
            ) {
                visibleWhen { stage(access.player) in STAGE_PARROT_HIDDEN..STAGE_HANDING_IN }
                custom(access.player.erLogsGiven, "I have given Eadgar the logs.").strike()
                custom(
                    access.player.erChickensGiven >= CHICKENS_NEEDED,
                    "I have given Eadgar the raw chickens.",
                ).strike()
                custom(access.player.erGrainGiven >= GRAIN_NEEDED, "I have given Eadgar the grain.").strike()
                custom(access.player.erRobeGiven, "I have given Eadgar the dirty clothes.").strike()
            }
            objective(
                "Eadgar wants a troll truth potion so Burntmeat will tell the truth. I must dry " +
                    "a <red>troll thistle</red> over a fire, grind it and add it to a " +
                    "<red>ranarr potion (unf)</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MAKE_POTION }
            }
            objective(
                "Eadgar has the potion. I should fetch the parrot back from under the rack in " +
                    "the troll prison.",
            ) {
                visibleWhen { stage(access.player) == STAGE_FETCH_PARROT }
            }
            objective("I have the parrot. I should take it back to Eadgar.") {
                visibleWhen { stage(access.player) == STAGE_PARROT_TRAINED }
            }
            objective("Eadgar has made a fake man. I should give it to <red>Burntmeat</red>.") {
                visibleWhen { stage(access.player) == STAGE_FAKE_MAN }
            }
            objective(
                "Burntmeat keeps the last of the goutweed in the <red>storeroom</red> under his " +
                    "kitchen. The key is hidden in a fake bottom of the kitchen drawers.",
            ) {
                visibleWhen { stage(access.player) == STAGE_COOK_FOOLED }
            }
            objective(
                "I can get into the storeroom. I need to sneak past the guards, take some " +
                    "<red>goutweed</red> and bring it to <red>Sanfew</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STOREROOM }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("Sanfew needed goutweed, a herb only the trolls still knew where to find.")
            line(
                "With Eadgar's help I fooled Burntmeat, the troll cook, with a fake man made " +
                    "from a scarecrow, a drunk parrot, dirty robes and raw chickens.",
            )
            line("I took goutweed from the troll storeroom and Sanfew taught me a new spell.")
        }

    data class Supplies(val logs: Int, val chickens: Int, val grain: Int, val robe: Int) {
        val isEmpty: Boolean
            get() = logs == 0 && chickens == 0 && grain == 0 && robe == 0
    }

    companion object {
        const val QUEST_KEY = "quest_eadgarsruse"
        const val DRUIDIC_RITUAL = "quest_druidicritual"

        const val STAGE_STARTED = 10
        const val STAGE_ASKED_EADGAR = 15
        const val STAGE_COOK_FIRST = 20
        const val STAGE_MET_COOK = 25
        const val STAGE_PARROT_WANTED = 30
        const val STAGE_PLAN = 50
        const val STAGE_PARROT_HIDDEN = 60
        const val STAGE_HANDING_IN = 70
        const val STAGE_MAKE_POTION = 80
        const val STAGE_FETCH_PARROT = 85
        const val STAGE_PARROT_TRAINED = 86
        const val STAGE_FAKE_MAN = 87
        const val STAGE_COOK_FOOLED = 90
        const val STAGE_STOREROOM = 100
        const val STAGE_COMPLETE = 110

        const val HERBLORE_REQ = 31
        const val HERBLORE_XP = 11_000.0
        const val CHICKENS_NEEDED = 5
        const val GRAIN_NEEDED = 10

        const val EADGAR = "npc.troll_eadgar"
        const val BURNTMEAT = "npc.eadgar_troll_chief_cook"
        const val PETE = "npc.eadgar_zoo_keeper_aviary"
        const val PARROT_NPC = "npc.eadgar_parrotts"
        const val FAKE_MAN_NPC = "npc.eadgar_fake_man"
        const val THISTLE_NPC = "npc.eadgar_troll_thistle"
        const val STOREROOM_GUARD = "npc.eadgar_storeroom_guard"
        const val TEGID = "npc.eadgar_druid_washing"
        const val SANFEW = "npc.sanfew"

        const val GOUTWEED = "obj.eadgar_goutweed_herb"
        const val THISTLE = "obj.eadgar_troll_thistle"
        const val DRIED_THISTLE = "obj.eadgar_dried_troll_thistle"
        const val GROUND_THISTLE = "obj.eadgar_ground_troll_thistle"
        const val TROLL_POTION = "obj.eadgar_ground_troll_thistle_potion"
        const val PARROT = "obj.eadgar_drunk_parrot"
        const val ROBE = "obj.eadgar_dirty_druid_robe"
        const val FAKE_MAN = "obj.eadgar_fake_man"
        const val STOREROOM_KEY = "obj.eadgar_troll_storeroom_key"
        const val ALCO_CHUNKS = "obj.eadgar_alco_chunks"
        const val VODKA = "obj.vodka"
        const val PINEAPPLE_CHUNKS = "obj.pineapple_chunks"
        const val RANARR_UNF = "obj.ranarrvial"
        const val PESTLE_AND_MORTAR = "obj.pestle_and_mortar"
        const val LOGS = "obj.logs"
        const val RAW_CHICKEN = "obj.raw_chicken"
        const val GRAIN = "obj.grain"
        const val BURNT_MEAT = "obj.burnt_meat"

        /**
         * Whether Tegid should be asked for a dirty robe: Eadgar has explained the plan, the robe
         * has not been handed over and the player has none. Static so Tegid's own script can ask
         * without depending on this quest's script.
         */
        fun needsRobe(player: Player): Boolean {
            val quest = Quest.get(QUEST_KEY) ?: return false
            val stage = quest.getQuestStage(player)
            return stage in STAGE_PLAN..STAGE_HANDING_IN && !player.erRobeGiven && !player.ownsAnywhere(ROBE)
        }
    }
}

internal fun Player.ownsAnywhere(obj: String): Boolean =
    inv.contains(obj) || worn.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

internal var Player.erLogsGiven by boolVarBit("varbit.eadgar_logs_given")
internal var Player.erChickensGiven by intVarBit("varbit.eadgar_chickens_given")
internal var Player.erGrainGiven by intVarBit("varbit.eadgar_grain_given")
internal var Player.erRobeGiven by boolVarBit("varbit.eadgar_robe_given")
internal var Player.erAskedPeteWater by boolVarBit("varbit.eadgar_asked_pete_water")
internal var Player.erAskedPeteFood by boolVarBit("varbit.eadgar_asked_pete_food")
