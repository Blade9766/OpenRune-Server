package org.rsmod.content.quest.area.hemenster.fishingcontest

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fishing Contest.
 *
 * The stage is the cache varp `varp.fishingcompo`, endstate 5 from `dbrow.quest_fishingcontest`:
 * - [STAGE_STARTED]: Austri or Vestri has asked for the Hemenster trophy and handed over a pass.
 * - [STAGE_GROUNDS]: Morris has seen the pass and lets the player into the competition grounds.
 * - [STAGE_ENTERED]: the player has paid Bonzo for at least one round.
 * - [STAGE_TROPHY]: the player won a round with a giant carp and holds the trophy.
 * - [STAGE_COMPLETE]: a dwarf has the trophy, and both ends of the tunnel are open.
 *
 * The contest itself lives on the cache varp `varp.garlicpipe`: garlic in the pipe, the fee paid
 * for the current round, Morris's approval and the Stranger's move, plus a server-only bit for a
 * round that has ended and waits to be judged. The seconds left in a running round are on a Temp
 * server varp, so a round cut short by logging out is restarted free of charge.
 */
@Singleton
class FishingContestQuest :
    QuestScript(
        QUEST_KEY,
        "varp.fishingcompo",
        rewards {
            xp("stat.fishing", REWARD_XP)
            scroll("2,437 Fishing XP", "Use of the dwarven tunnel", "under White Wolf Mountain")
        },
        ItemRewardDisplay(TROPHY, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::normalise)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Austri</col> or <col=800000>Vestri</col> at either end of the " +
            "tunnel under <col=800000>White Wolf Mountain</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val start =
                "The dwarves guarding the tunnel under White Wolf Mountain only let their friends " +
                    "use it. To prove my friendship I must win the Hemenster fishing competition " +
                    "and bring back its trophy."
            if (stage == STAGE_STARTED) {
                line(start)
                if (PASS in p.inv) {
                    line(
                        "The dwarves gave me a fishing pass. I should show it at the gate of the " +
                            "competition grounds in Hemenster, south of McGrubor's Wood.",
                    )
                } else {
                    line("I've lost the dwarves' fishing pass. Austri or Vestri will give me another.")
                }
                return@questJournal
            }
            strike(start)
            strike("Morris let me into the Hemenster competition grounds once he had seen my pass.")
            if (stage >= STAGE_TROPHY) {
                strike("I won the Hemenster fishing competition with a giant carp!")
                if (TROPHY in p.inv) {
                    line("I should take the fishing trophy to Austri or Vestri.")
                } else {
                    line("I seem to have mislaid the trophy. Bonzo might have kept a spare.")
                }
                return@questJournal
            }
            line("Bonzo runs the competition. A round costs 5 coins and the biggest fish wins.")
            if (rodProblem(p) != null) {
                line("Contest rules say I need a plain fishing rod.")
            }
            val worms = p.inv.count(WORMS)
            if (worms > 0) {
                line("I have $worms red vine worm${if (worms == 1) "" else "s"} for bait.")
            } else {
                line("The local fish are fussy. Grandpa Jack, who lives nearby, knows these waters.")
            }
            line(
                "The best spot, under the wall pipes of the northern building, is taken by a " +
                    "sinister stranger who refuses to move.",
            )
            if (isGarlicPlaced(p)) {
                line(
                    "I stuffed garlic into the pipe above his spot. It should make itself felt " +
                        "once a round begins.",
                )
            }
            when {
                isRoundActive(p) -> line("A round is under way: about ${secondsLeft(p)} seconds left.")
                isAwaitingJudging(p) -> line("The round is over. I should take my catch to Bonzo.")
                isFeePaid(p) -> line("My round was cut short. Bonzo will restart it for free.")
            }
            if (CARP in p.inv) {
                line("I've caught a giant carp!")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "To earn the friendship of the dwarves of White Wolf Mountain I entered the " +
                    "Hemenster fishing competition, armed with red vine worms from McGrubor's Wood.",
            )
            line(
                "Garlic in the wall pipe drove the sinister stranger from the best spot, where I " +
                    "landed a giant carp and won the trophy. The dwarves now let me use their tunnel.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    fun isContestStage(player: Player): Boolean = stage(player) in STAGE_STARTED until STAGE_TROPHY

    fun isGarlicPlaced(player: Player): Boolean = player.garlicPlaced == 1

    fun placeGarlic(player: Player) {
        player.garlicPlaced = 1
    }

    fun hasShownPass(player: Player): Boolean = player.passShown == 1

    fun markPassShown(player: Player) {
        player.passShown = 1
    }

    fun isFeePaid(player: Player): Boolean = player.feePaid == 1

    fun isRoundActive(player: Player): Boolean = player.roundLeft > 0

    fun isAwaitingJudging(player: Player): Boolean = player.roundOver == 1

    fun isStrangerMoved(player: Player): Boolean = player.strangerMoved == 1

    fun secondsLeft(player: Player): Int = player.roundLeft * ROUND_STEP_TICKS * 6 / 10

    fun roundSteps(player: Player): Int = player.roundLeft

    /** Takes the fee as paid and starts a fresh round; the caller has already taken the coins. */
    fun startRound(player: Player) {
        player.feePaid = 1
        player.roundOver = 0
        player.roundLeft = ROUND_STEPS
    }

    fun setRoundSteps(player: Player, steps: Int) {
        player.roundLeft = steps
    }

    fun endRound(player: Player) {
        player.roundLeft = 0
        player.strangerMoved = 0
        if (player.feePaid == 1) {
            player.roundOver = 1
        }
    }

    /** Settles a judged round, win or lose: the next one has to be paid for again. */
    fun clearRound(player: Player) {
        player.roundLeft = 0
        player.roundOver = 0
        player.feePaid = 0
        player.strangerMoved = 0
    }

    fun markStrangerMoved(player: Player) {
        player.strangerMoved = 1
    }

    fun meetsFishingLevel(player: Player): Boolean = player.fishingLvl >= FISHING_LEVEL

    /** Why the player's rods can't be used in the contest, or null when a plain rod is carried. */
    fun rodProblem(player: Player): String? =
        when {
            ROD in player.inv -> null
            FLY_ROD in player.inv || OILY_ROD in player.inv ->
                "Contest rules: plain fishing rods only. Fly fishing and oily rods aren't allowed."
            else -> "You need a fishing rod to fish in the competition."
        }

    fun hint(player: Player): String {
        val stage = stage(player)
        return when {
            stage == 0 ->
                "Ask Austri or Vestri, at the ends of the White Wolf Mountain tunnel, about the " +
                    "way through the mountain."
            stage == STAGE_STARTED && PASS !in player.inv ->
                "You've lost your fishing pass. Austri or Vestri will give you another."
            stage == STAGE_STARTED ->
                "Show your fishing pass to Morris at the gate of the Hemenster competition " +
                    "grounds, south of McGrubor's Wood."
            stage >= STAGE_COMPLETE -> "You have completed this quest."
            stage == STAGE_TROPHY && TROPHY in player.inv -> "Take the trophy to Austri or Vestri."
            stage == STAGE_TROPHY -> "You've mislaid the trophy. Ask Bonzo about it."
            isAwaitingJudging(player) -> "Your round is over: talk to Bonzo to have your catch judged."
            isRoundActive(player) && CARP in player.inv ->
                "You've landed a giant carp. Hand it to Bonzo, or wait for the round to end."
            isRoundActive(player) && isStrangerMoved(player) ->
                "The stranger has fled the smell. Fish at his spot beside the wall pipes."
            isRoundActive(player) -> "Fish where you can before the round runs out."
            rodProblem(player) != null ->
                "You need a plain fishing rod. Grandpa Jack, north-east of the contest, sells " +
                    "them for 5 coins."
            WORMS !in player.inv ->
                "Grandpa Jack's favourite bait is red vine worms, dug from the vines in " +
                    "McGrubor's Wood with a spade. The gate is locked, but the northern railing is loose."
            !isGarlicPlaced(player) && GARLIC !in player.inv ->
                "The stranger by the pipes is awfully pale and shuns daylight. There's garlic on " +
                    "a table in a house in Seers' Village."
            !isGarlicPlaced(player) ->
                "Use the garlic on the wall pipe above the stranger's spot: the smell drains " +
                    "right out over him."
            player.inv.count(COINS) < ENTRY_FEE -> "Bonzo charges 5 coins for each round."
            else -> "Pay Bonzo 5 coins to start a round."
        }
    }

    private fun normalise(player: Player) {
        if (stage(player) == 0) {
            player.garlicPlaced = 0
            player.passShown = 0
            clearRound(player)
            return
        }
        if (player.roundLeft == 0) {
            player.strangerMoved = 0
        }
    }

    companion object {
        const val QUEST_KEY = "quest_fishingcontest"

        const val STAGE_STARTED = 1
        const val STAGE_GROUNDS = 2
        const val STAGE_ENTERED = 3
        const val STAGE_TROPHY = 4
        const val STAGE_COMPLETE = 5

        const val REWARD_XP = 2_437.0
        const val FISHING_LEVEL = 10
        const val ENTRY_FEE = 5
        const val ROD_PRICE = 5

        /** A round is [ROUND_STEPS] steps of [ROUND_STEP_TICKS] ticks: 90 seconds. */
        const val ROUND_STEP_TICKS = 5
        const val ROUND_STEPS = 30

        const val AUSTRI = "npc.tunnel_dwarf"
        const val VESTRI = "npc.tunnel_dwarf1"
        const val GRANDPA_JACK = "npc.grandpa_jack"
        const val MORRIS = "npc.morris"
        const val BONZO = "npc.bonzo"
        const val STRANGER = "npc.sinister_stranger"
        const val STRANGER_AT_SPOT = "npc.sinister_stranger0"
        const val STRANGER_MOVED = "npc.sinister_stranger1"
        const val BIG_DAVE = "npc.bigdave"
        const val JOSHUA = "npc.joshua"
        const val FORESTER = "npc.mcgruborforester"

        const val PASS = "obj.fishing_competition_pass"
        const val TROPHY = "obj.hemenster_fishing_trophy"
        const val WORMS = "obj.red_vine_worm"
        const val GARLIC = "obj.garlic"
        const val CARP = "obj.raw_giant_carp"
        const val ROD = "obj.fishing_rod"
        const val FLY_ROD = "obj.fly_fishing_rod"
        const val OILY_ROD = "obj.oily_fishing_rod"
        const val SPADE = "obj.spade"
        const val COINS = "obj.coins"
    }
}

private var Player.garlicPlaced: Int by intVarBit("varbit.fishingcompo_garlicpipe")
private var Player.feePaid: Int by intVarBit("varbit.fishingcompo_paid")
private var Player.passShown: Int by intVarBit("varbit.fishingcompo_passed")
private var Player.strangerMoved: Int by intVarBit("varbit.fishingcompo_stranger")
private var Player.roundOver: Int by intVarBit("varbit.fishingcompo_round_over")
private var Player.roundLeft: Int by intVarBit("varbit.fishingcontest_round_left")
