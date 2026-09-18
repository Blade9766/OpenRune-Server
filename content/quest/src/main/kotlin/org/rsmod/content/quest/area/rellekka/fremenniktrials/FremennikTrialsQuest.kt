package org.rsmod.content.quest.area.rellekka.fremenniktrials

import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
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

/** The seven council members whose votes can be won, in the order the journal lists them. */
enum class Trial(val title: String, val councillor: String) {
    Bard("Bard's", "Olaf the Bard"),
    Reveller("Revellers'", "Manni the Reveller"),
    Hunter("Hunter's", "Sigli the Huntsman"),
    Merchant("Merchant's", "Sigmund the Merchant"),
    Warrior("warrior's", "Thorvald the Warrior"),
    Navigator("Navigator's", "Swensen the Navigator"),
    Seer("Seer's", "Peer the Seer"),
}

/**
 * The Fremennik Trials.
 *
 * The stage lives in `varp.viking` (347), which runs 0..10 from `dbrow.quest_fremenniktrials`.
 * RuneLite's quest helper reads it as one plus the number of council votes won, so 1 is started
 * with no votes, 8 is all seven, and 10 is complete once Brundt has welcomed the player.
 *
 * Which councillors have voted, and each trial's own progress, are varbits on the server-only
 * `varp.fremtrials_state` and `varp.fremtrials_progress`. Koschei's form and the letters dialled
 * into Peer's combination lock are on the Temp `varp.fremtrials_temp`, since both start over when
 * the player logs out.
 */
@Singleton
class FremennikTrialsQuest : QuestScript(
    QUEST_KEY,
    "varp.viking",
    rewards {
        for (stat in REWARD_STATS) {
            xp(stat, REWARD_XP)
        }
        scroll(
            "2,812 XP in each of:",
            "Agility, Attack, Crafting,",
            "Defence, Fishing, Fletching,",
            "Hitpoints, Strength,",
            "Thieving and Woodcutting",
            "Membership of the Fremennik",
        )
    },
    ItemRewardDisplay(FREMENNIK_HELM, zoom = 110),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncFlags)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isInProgress(player: Player): Boolean = isStarted(player) && !isComplete(player)

    fun hasVote(player: Player, trial: Trial): Boolean = isComplete(player) || player.voted(trial)

    fun votes(player: Player): Int = Trial.entries.count { player.voted(it) }

    fun start(access: ProtectedAccess) {
        if (stage(access.player) == 0) {
            quest.advanceQuestStage(access, STAGE_STARTED)
        }
    }

    /** Records [trial]'s vote and moves the stage up to match the number of votes won. */
    fun grantVote(access: ProtectedAccess, trial: Trial) {
        val player = access.player
        if (player.voted(trial) || !isInProgress(player)) {
            return
        }
        player.setVoted(trial)
        access.mes("Congratulations! You have completed the ${trial.title} trial!")
        val target = STAGE_STARTED + votes(player)
        val remaining = target - stage(player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        val player = access.player
        player.fremennikPrefix = access.random.of(0, NAME_PREFIXES.lastIndex)
        player.fremennikSuffix = access.random.of(0, NAME_SUFFIXES.lastIndex)
        quest.completeQuest(access)
    }

    fun fremennikName(player: Player): String =
        NAME_PREFIXES[player.fremennikPrefix.coerceIn(NAME_PREFIXES.indices)] +
            NAME_SUFFIXES[player.fremennikSuffix.coerceIn(NAME_SUFFIXES.indices)]

    private fun syncFlags(player: Player) {
        if (stage(player) != 0) {
            return
        }
        VarPlayerIntMapSetter.set(player, STATE_VARP, 0)
        VarPlayerIntMapSetter.set(player, PROGRESS_VARP, 0)
        VarPlayerIntMapSetter.set(player, TEMP_VARP, 0)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Brundt the Chieftain</col> in the <col=800000>longhall</col> of " +
            "<col=800000>Rellekka</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Brundt the Chieftain</red> told me that I can become a Fremennik if I win " +
                    "the votes of seven of the twelve members of the <red>council of elders</red>.",
            ) {
                visibleWhen { isInProgress(access.player) }
            }
            for (trial in Trial.entries) {
                objective("<red>${trial.councillor}</red> will vote for me: ${trialHint(trial)}") {
                    visibleWhen { isInProgress(access.player) && trialStarted(access.player, trial) }
                    custom(access.player.voted(trial), "I have passed the ${trial.title} trial.")
                        .strikeObjective()
                }
            }
            objective(
                "I have seven votes. I should go back to <red>Brundt</red> in the longhall.",
            ) {
                visibleWhen { stage(access.player) >= STAGE_ALL_VOTES && !isComplete(access.player) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "The council of elders of Rellekka voted to accept me into the Fremennik after I " +
                    "passed seven of their trials.",
            )
            line(
                "I drank Manni under the table, played an epic on Olaf's stage, hunted the " +
                    "Draugen for Sigli and found Sigmund his exotic flower.",
            )
            line(
                "I fought Koschei the deathless for Thorvald, walked Swensen's maze of portals " +
                    "and solved the puzzle in Peer the Seer's house.",
            )
            line("Brundt the Chieftain gave me a new Fremennik name: ${fremennikName(player.player)}.")
        }

    private fun trialStarted(player: Player, trial: Trial): Boolean =
        player.voted(trial) ||
            when (trial) {
                Trial.Bard -> player.ftOlafStarted
                Trial.Reveller -> player.ftManniChallenged
                Trial.Hunter -> player.ftSigliStarted
                Trial.Merchant -> player.ftSigmundStep > 0
                Trial.Warrior -> player.ftThorvaldStarted
                Trial.Navigator -> player.ftSwensenStarted
                Trial.Seer -> player.ftPeerStarted
            }

    private fun trialHint(trial: Trial): String =
        when (trial) {
            Trial.Bard ->
                "if I make a <red>lyre</red>, have it blessed by the <red>Fossegrimen</red> and " +
                    "perform an epic on the <red>longhall</red> stage."
            Trial.Reveller ->
                "if I can beat him in a <red>drinking contest</red> with a keg of beer from the " +
                    "longhall."
            Trial.Hunter ->
                "if I track and defeat the <red>Draugen</red> with his <red>hunters' talisman</red>."
            Trial.Merchant -> "if I persuade someone in Rellekka to part with a rare <red>flower</red>."
            Trial.Warrior ->
                "if I fight <red>Koschei the deathless</red> beneath his house, without any armour " +
                    "or weapons."
            Trial.Navigator -> "if I find my way through the <red>maze</red> beneath his house."
            Trial.Seer ->
                "if I enter his house by one door and leave by the other, taking <red>no items</red> " +
                    "in with me."
        }

    companion object {
        const val QUEST_KEY = "quest_fremenniktrials"

        const val STAGE_STARTED = 1
        const val STAGE_ALL_VOTES = 8

        const val STATE_VARP = "varp.fremtrials_state"
        const val PROGRESS_VARP = "varp.fremtrials_progress"
        const val TEMP_VARP = "varp.fremtrials_temp"

        const val FREMENNIK_HELM = "obj.viking_helmet"
        const val REWARD_XP = 2812.4

        val REWARD_STATS =
            listOf(
                "stat.agility",
                "stat.attack",
                "stat.crafting",
                "stat.defence",
                "stat.fishing",
                "stat.fletching",
                "stat.hitpoints",
                "stat.strength",
                "stat.thieving",
                "stat.woodcutting",
            )

        val NAME_PREFIXES =
            listOf(
                "Bal", "Bar", "Dal", "Dar", "Den", "Dok", "Jar", "Jik",
                "Lar", "Rak", "Ral", "Ril", "Sig", "Tal", "Thor", "Ton",
            )
        val NAME_SUFFIXES =
            listOf(
                "dar", "dor", "dur", "kal", "kar", "kir", "kur", "lah",
                "lak", "lim", "lor", "rak", "tin", "ton", "tor", "vald",
            )

        const val BRUNDT = "npc.viking_brundt"
        const val OLAF = "npc.viking_olaf"
        const val MANNI = "npc.viking_reveller_3"
        const val SIGLI = "npc.viking_sigli"
        const val SIGMUND = "npc.viking_sigmund"
        const val THORVALD = "npc.viking_thorvald"
        const val SWENSEN = "npc.viking_hallifred"
        const val PEER = "npc.viking_peer"
        const val ASKELADDEN = "npc.viking_askelapen"
        const val THORA = "npc.viking_longhall_barkeep"
        const val YRSA = "npc.viking_clothing_shopkeeper"
        const val SKULGRIMEN = "npc.viking_weapons_salesman"
        const val FISHERMAN = "npc.viking_fisherman1"
        const val SAILOR = "npc.viking_sailor"
        const val LALLI = "npc.viking_lalli_troll"
        const val FOSSEGRIMEN = "npc.viking_lake_spirit"
        const val BOUNCER = "npc.viking_longhall_bouncer"

        const val COINS = "obj.coins"
    }
}

private val VOTE_VARBITS =
    mapOf(
        Trial.Bard to "varbit.fremtrials_vote_olaf",
        Trial.Reveller to "varbit.fremtrials_vote_manni",
        Trial.Hunter to "varbit.fremtrials_vote_sigli",
        Trial.Merchant to "varbit.fremtrials_vote_sigmund",
        Trial.Warrior to "varbit.fremtrials_vote_thorvald",
        Trial.Navigator to "varbit.fremtrials_vote_swensen",
        Trial.Seer to "varbit.fremtrials_vote_peer",
    )

fun Player.voted(trial: Trial): Boolean = vars[VOTE_VARBITS.getValue(trial)] != 0

private fun Player.setVoted(trial: Trial) {
    VarPlayerIntMapSetter.set(this, VOTE_VARBITS.getValue(trial), 1)
}

var Player.ftOlafStarted by boolVarBit("varbit.fremtrials_olaf_started")
var Player.ftLalliTalked by boolVarBit("varbit.fremtrials_lalli_talked")

/** Lalli has refused the pet rock and the player has thought of rock soup. */
var Player.ftStewPlanned by boolVarBit("varbit.fremtrials_stew_planned")
var Player.ftStewOnion by boolVarBit("varbit.fremtrials_stew_onion")
var Player.ftStewCabbage by boolVarBit("varbit.fremtrials_stew_cabbage")
var Player.ftStewPotato by boolVarBit("varbit.fremtrials_stew_potato")
var Player.ftStewRock by boolVarBit("varbit.fremtrials_stew_rock")
var Player.ftStewTasted by boolVarBit("varbit.fremtrials_stew_tasted")

var Player.ftManniChallenged by boolVarBit("varbit.fremtrials_manni_challenged")
var Player.ftManniLost by boolVarBit("varbit.fremtrials_manni_lost")

/** A lit strange object is fizzing in the longhall drain pipe. */
var Player.ftPipePrimed by boolVarBit("varbit.fremtrials_pipe_primed")

/** The keg of beer the player carries now holds low alcohol beer. */
var Player.ftKegSwitched by boolVarBit("varbit.fremtrials_keg_switched")

/** The poison salesman has given his sales pitch for low alcohol beer. */
var Player.ftSalesmanPitched by boolVarBit("varbit.fremtrials_salesman_pitched")

var Player.ftSigliStarted by boolVarBit("varbit.fremtrials_sigli_started")
var Player.ftThorvaldStarted by boolVarBit("varbit.fremtrials_thorvald_started")
var Player.ftSwensenStarted by boolVarBit("varbit.fremtrials_swensen_started")
var Player.ftPeerStarted by boolVarBit("varbit.fremtrials_peer_started")

/** How far along the chain of favours for Sigmund's flower the player is; see [MerchantStep]. */
var Player.ftSigmundStep by intVarBit("varbit.fremtrials_sigmund_step")

var Player.fremennikPrefix by intVarBit("varbit.fremtrials_name_prefix")
var Player.fremennikSuffix by intVarBit("varbit.fremtrials_name_suffix")

/** Which of Peer's six riddles is on his door, 1-based; 0 until the player first reads it. */
var Player.ftPeerRiddle by intVarBit("varbit.fremtrials_peer_riddle")
var Player.ftPeerDoorSolved by boolVarBit("varbit.fremtrials_peer_door_solved")

/** Which hiding place in the Fremennik province the Draugen is haunting; see [DraugenHunt]. */
var Player.ftDraugenSpot by intVarBit("varbit.fremtrials_draugen_spot")

var Player.ftKoscheiForm by intVarBit("varbit.fremtrials_koschei_form")

/** Whether the player carries, wears or has banked [obj]. */
internal fun ProtectedAccess.ownsAnywhere(obj: String): Boolean =
    obj in player.inv || obj in player.worn || obj in bank

internal fun ProtectedAccess.carries(obj: String): Boolean = obj in player.inv

internal fun Player.mesAll(vararg lines: String) {
    for (line in lines) {
        mes(line)
    }
}
