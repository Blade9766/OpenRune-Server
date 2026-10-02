package org.rsmod.content.quest.area.hemenster.fishingcontest

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onNpcTimer
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.BIG_DAVE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.BONZO
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.CARP
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.COINS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.ENTRY_FEE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.FISHING_LEVEL
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.GARLIC
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.JOSHUA
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.ROUND_STEP_TICKS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_ENTERED
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_TROPHY
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STRANGER
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STRANGER_AT_SPOT
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.TROPHY
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.WORMS
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Hemenster fishing competition: Bonzo's entry fee and judging, the 90-second round, the
 * contest fishing spots, the wall pipes and the Sinister Stranger's flight from the garlic.
 *
 * A round runs on a soft timer that steps every [ROUND_STEP_TICKS] ticks, so it keeps counting
 * while the player is busy fishing or talking. Each cast is a weak queue, the same as ordinary
 * fishing, so walking off or clicking elsewhere stops it. Only the spot under the pipes holds the
 * giant carp, and the Stranger guards it until garlic in the pipe drives him off as a round begins.
 * He is one npc shared by everyone, so he only walks away for a while; whether the spot is free is
 * the player's own `varbit.fishingcompo_stranger`.
 */
@Singleton
class FishingCompetition
@Inject
constructor(
    private val fc: FishingContestQuest,
    private val npcList: NpcList,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BONZO) { startDialogue(it.npc) { bonzo() } }
        onOpNpc3(BONZO) { startDialogue(it.npc) { pay() } }
        onOpNpc1(PIPE_SPOT) { startFishing(Spot.PIPES) }
        onOpNpc1(OPEN_SPOT) { startFishing(Spot.OPEN) }
        onOpNpc1(BIG_DAVE_SPOT) { claimed(BIG_DAVE, "Big Dave", "Oi! This is my spot. Find your own.") }
        onOpNpc1(JOSHUA_SPOT) { claimed(JOSHUA, "Joshua", "Shh! Go and fish somewhere else, you'll scare them off.") }
        onPlayerQueueWithArgs<Spot>(CATCH_QUEUE) { cast(it.args) }
        onPlayerSoftTimer(ROUND_TIMER) { tick(player) }
        onOpLocU(PIPE, GARLIC) { placeGarlic(it.loc) }
        onOpLoc1(PIPE) { searchPipe() }
        onNpcTimer(STRANGER_RETURN_TIMER) { strangerReturns(npc) }
    }

    private suspend fun Dialogue.bonzo() {
        val stage = fc.stage(player)
        when {
            stage == 0 -> {
                chatNpc(happy, "Roll up, roll up! Enter the great Hemenster fishing competition! Only five coins!")
                chatNpc(confused, "Hang on, you're not on my list. How did you get past Morris? Competitors only, I'm afraid.")
            }
            stage >= STAGE_COMPLETE -> {
                chatNpc(happy, "Our champion! I hear that trophy is the talk of White Wolf Mountain.")
            }
            stage == STAGE_TROPHY -> trophyFollowUp()
            fc.isAwaitingJudging(player) -> judge()
            fc.isRoundActive(player) -> roundInProgress()
            fc.isFeePaid(player) -> restartRound()
            else -> introduce()
        }
    }

    private suspend fun Dialogue.pay() {
        if (fc.isContestStage(player) && !fc.isFeePaid(player)) {
            enter()
            return
        }
        bonzo()
    }

    private suspend fun Dialogue.introduce() {
        chatNpc(happy, "Roll up, roll up! Enter the great Hemenster fishing competition! Only $ENTRY_FEE coins a round!")
        val topic =
            menu(
                "I'd like to enter." to Topic.ENTER,
                "What are the rules?" to Topic.RULES,
                "No thanks, I'll just watch." to Topic.LEAVE,
            )
        when (topic) {
            Topic.ENTER -> enter()
            Topic.RULES -> rules()
            Topic.LEAVE -> {
                chatPlayer(neutral, "No thanks, I'll just watch.")
                chatNpc(neutral, "Suit yourself. Spectating is free, but no heckling the competitors.")
            }
        }
    }

    private suspend fun Dialogue.rules() {
        chatPlayer(quiz, "What are the rules?")
        chatNpc(neutral, "Simple. Pay $ENTRY_FEE coins and you get ninety seconds to fish. Plain fishing rods only, none of your fancy fly or oily rods.")
        chatNpc(neutral, "When the time's up, bring me your catch. The biggest fish caught wins the trophy.")
        chatNpc(laugh, "The trophy's been Big Dave's three years running, so no pressure.")
    }

    private suspend fun Dialogue.enter() {
        chatPlayer(happy, "I'd like to enter.")
        if (!fc.meetsFishingLevel(player)) {
            chatNpc(sad, "Sorry, it's Fishing level $FISHING_LEVEL or above. We had a beginner hook a judge last year.")
            return
        }
        val rod = fc.rodProblem(player)
        if (rod != null) {
            chatNpc(neutral, rod)
            return
        }
        if (WORMS !in access.inv) {
            chatNpc(confused, "And what are you going to use for bait, your fingers? Come back when you've got some worms.")
            return
        }
        if (access.inv.count(COINS) < ENTRY_FEE) {
            chatNpc(sad, "It's $ENTRY_FEE coins to enter, and you're a bit short.")
            return
        }
        if (!choice2("Pay $ENTRY_FEE coins.", true, "Not yet.", false, title = "Enter the competition?")) {
            chatPlayer(neutral, "Not just yet.")
            return
        }
        if (fc.isFeePaid(player) || access.invDel(access.inv, COINS, ENTRY_FEE).failure) {
            return
        }
        fc.startRound(player)
        fc.advanceTo(access, STAGE_ENTERED)
        beginRound(access)
        chatNpc(happy, "Marvellous! You've got ninety seconds, starting... now! Off you go!")
    }

    private suspend fun Dialogue.restartRound() {
        chatNpc(confused, "There you are! You paid, then wandered off before your round was done. No need to pay twice.")
        if (!choice2("Start my round again.", true, "Not yet.", false)) {
            chatPlayer(neutral, "Not yet. I'll be back.")
            return
        }
        fc.startRound(player)
        beginRound(access)
        chatNpc(happy, "Ninety seconds, starting... now!")
    }

    private suspend fun Dialogue.roundInProgress() {
        if (CARP !in access.inv) {
            chatNpc(neutral, "What are you doing chatting to me? You've about ${fc.secondsLeft(player)} seconds left. Get fishing!")
            return
        }
        chatNpc(shocked, "Is that a giant carp you've got there? Do you want to hand your catch in now? That'll be the end of your round.")
        if (!choice2("Yes, judge my catch.", true, "No, I'll keep fishing.", false)) {
            return
        }
        player.clearSoftTimer(ROUND_TIMER)
        fc.endRound(player)
        judge()
    }

    private suspend fun Dialogue.judge() {
        chatNpc(neutral, "Right then, let's have a look at what you've caught.")
        if (CARP !in access.inv) {
            val sardines = access.inv.count(SARDINE)
            fc.clearRound(player)
            if (sardines > 0) {
                chatNpc(neutral, "A sardine. Lovely. Very... sardine-sized.")
            } else {
                chatNpc(sad, "Nothing at all? Not even a tiddler?")
            }
            chatNpc(neutral, "I'm afraid Big Dave takes it again, with a trout as long as my arm. Better luck next time! Another $ENTRY_FEE coins and you can have another go.")
            return
        }
        if (access.invDel(access.inv, CARP, 1).failure) {
            return
        }
        if (access.invAdd(access.inv, TROPHY, 1).failure) {
            access.invAdd(access.inv, CARP, 1)
            return
        }
        fc.clearRound(player)
        fc.advanceTo(access, STAGE_TROPHY)
        access.mes("You hand Bonzo your raw giant carp.")
        chatNpc(shocked, "Great Saradomin's scales! A giant carp! That's the biggest fish anyone's caught in this lake for years!")
        chatNpc(happy, "Ladies and gentlemen, we have a new champion! Big Dave, put that trout away.")
        objbox(TROPHY, zoom = 400, "Bonzo hands you the Hemenster fishing trophy!")
        access.mes("Congratulations! You have won the Hemenster fishing competition.")
    }

    private suspend fun Dialogue.trophyFollowUp() {
        if (TROPHY in access.inv) {
            chatNpc(happy, "Our champion! Off you go and show that trophy to whoever you won it for.")
            return
        }
        if (TROPHY in access.bank) {
            chatNpc(confused, "Lost the trophy? I'd check your bank before you panic.")
            return
        }
        chatPlayer(sad, "I've lost the trophy.")
        chatNpc(shocked, "Lost it?! Lucky for you, I had a spare made in case Big Dave dropped the last one in the lake.")
        if (access.invAdd(access.inv, TROPHY, 1).failure) {
            chatNpc(neutral, "Make some room and I'll hand it over.")
            return
        }
        objbox(TROPHY, zoom = 400, "Bonzo hands you a replacement fishing trophy.")
    }

    /** Starts the countdown for the round [FishingContestQuest.startRound] just opened. */
    fun beginRound(access: ProtectedAccess) {
        val player = access.player
        player.softTimer(ROUND_TIMER, ROUND_STEP_TICKS)
        player.mes("The competition round has begun! You have ninety seconds.")
        if (fc.isGarlicPlaced(player)) {
            strangerFlees(player)
        }
    }

    /** One step of the round clock; ends the round when it reaches zero. */
    fun tick(player: Player) {
        val left = fc.roundSteps(player)
        if (left <= 0 || !fc.isFeePaid(player)) {
            player.clearSoftTimer(ROUND_TIMER)
            return
        }
        val now = left - 1
        fc.setRoundSteps(player, now)
        competitorsFish(now)
        when (now) {
            20 -> player.mes("<col=800000>Bonzo calls out: One minute left!</col>")
            10 -> player.mes("<col=800000>Bonzo calls out: Thirty seconds left!</col>")
            3 -> player.mes("<col=800000>Bonzo calls out: Nearly time! Reel them in!</col>")
            0 -> {
                player.clearSoftTimer(ROUND_TIMER)
                player.clearWeakQueue(CATCH_QUEUE)
                fc.endRound(player)
                player.mes("<col=800000>Bonzo calls out: Time's up! Bring me your catch for judging.</col>")
            }
        }
    }

    private suspend fun ProtectedAccess.startFishing(spot: Spot) {
        if (spot == Spot.PIPES && fc.isRoundActive(player) && !fc.isStrangerMoved(player)) {
            startDialogue {
                chatNpcSpecific("Sinister Stranger", STRANGER_AT_SPOT, angry, "I don't think so. This spot is mine.")
                chatNpcSpecific("Sinister Stranger", STRANGER_AT_SPOT, shifty, "I'm not moving for anyone. Not for anything. Well... almost anything.")
            }
            return
        }
        val problem = castProblem(spot)
        if (problem != null) {
            mes(problem)
            return
        }
        anim(CAST_SEQ)
        mes("You cast out your line...")
        clearWeakQueue(CATCH_QUEUE)
        weakQueue(CATCH_QUEUE, CAST_TICKS, spot)
    }

    /** Why a cast at [spot] can't happen now, or null when it can. */
    private fun ProtectedAccess.castProblem(spot: Spot): String? {
        val rod = fc.rodProblem(player)
        return when {
            fc.isAwaitingJudging(player) -> "The round is over. Take your catch to Bonzo for judging."
            !fc.isRoundActive(player) -> "You need to enter the competition first. Bonzo takes the $ENTRY_FEE coin entry fee."
            !fc.meetsFishingLevel(player) -> "You need a Fishing level of $FISHING_LEVEL to fish in the competition."
            rod != null -> rod
            WORMS !in inv -> "You have run out of red vine worms."
            spot == Spot.PIPES && !fc.isStrangerMoved(player) -> "The Sinister Stranger is still fishing here."
            spot == Spot.PIPES && CARP in inv -> "You've already landed a whopper. That should be plenty!"
            else -> null
        }
    }

    /** One cast of the line; recasts until the round ends, the bait runs out or the carp is landed. */
    suspend fun ProtectedAccess.cast(spot: Spot) {
        val problem = castProblem(spot)
        if (problem != null) {
            mes(problem)
            return
        }
        anim(CAST_SEQ)
        if (random.of(1, BITE_CHANCE) != 1) {
            weakQueue(CATCH_QUEUE, CAST_TICKS, spot)
            return
        }
        val fish = if (spot == Spot.PIPES) CARP else SARDINE
        if (invDel(inv, WORMS, 1).failure) {
            return
        }
        if (invAdd(inv, fish, 1).failure) {
            invAdd(inv, WORMS, 1)
            mes("You don't have enough inventory space to hold the fish.")
            return
        }
        if (spot == Spot.PIPES) {
            mes("Something huge takes the bait! After a long struggle you land a raw giant carp!")
            mes("That has to be a winner. Take it to Bonzo.")
            return
        }
        mes("You catch a sardine. Hardly a prize-winner.")
        weakQueue(CATCH_QUEUE, CAST_TICKS, spot)
    }

    private suspend fun ProtectedAccess.claimed(npc: String, name: String, line: String) {
        startDialogue { chatNpcSpecific(name, npc, angry, line) }
    }

    private suspend fun ProtectedAccess.placeGarlic(pipe: BoundLocInfo) {
        arriveDelay()
        faceLoc(pipe)
        if (!fc.isContestStage(player)) {
            mes("Why would you want to stuff garlic down a pipe?")
            return
        }
        if (fc.isGarlicPlaced(player)) {
            mes("There's already garlic in the pipe. Any more and someone will notice.")
            return
        }
        anim(PLACE_SEQ)
        delay(1)
        if (invDel(inv, GARLIC, 1).failure) {
            return
        }
        fc.placeGarlic(player)
        spotanimMap(worldRepo, SMELL_SPOTANIM, pipe.coords)
        mes("You stuff the garlic into the pipe. A pungent smell starts drifting out over the water below.")
        if (fc.isRoundActive(player)) {
            strangerFlees(player)
        }
    }

    private fun ProtectedAccess.searchPipe() {
        if (fc.isGarlicPlaced(player)) {
            mes("The pipe reeks of garlic.")
            return
        }
        mes("It's an outlet pipe. Whatever goes in here drains out over the water right by the fishing spot below.")
    }

    private fun strangerFlees(player: Player) {
        fc.markStrangerMoved(player)
        player.mes("A powerful whiff of garlic wafts out of the wall pipe...")
        player.mes("The Sinister Stranger gags, clutches his hood and storms off. The spot by the pipes is free!")
        val npc = stranger() ?: return
        worldRepo.spotanimMap(SpotanimType(SMELL_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), PIPE_SPOT_TILE)
        npc.say("Ugh! Garlic! Who would DO such a thing?")
        npc.anim(GAG_SEQ)
        npc.timer(STRANGER_RETURN_TIMER, STRANGER_AWAY_TICKS)
        if (npc.coords != STRANGER_AWAY_TILE) {
            npc.noneMode()
            npc.walk(STRANGER_AWAY_TILE)
        }
    }

    private fun strangerReturns(npc: Npc) {
        if (!npc.isType(STRANGER)) {
            return
        }
        npc.clearTimer(STRANGER_RETURN_TIMER)
        npc.say("Hmph. Is that smell finally gone?")
        if (npc.mode == NpcMode.None) {
            npc.defaultMode()
        }
        npc.walk(STRANGER_POST)
    }

    private fun competitorsFish(step: Int) {
        val dave = find(BIG_DAVE)
        val joshua = find(JOSHUA)
        val stranger = stranger()
        if (step % 2 == 0) {
            dave?.anim(CAST_SEQ)
            joshua?.anim(CAST_SEQ)
            stranger?.takeIf { it.mode != NpcMode.None }?.anim(CAST_SEQ)
        }
        if (step % CHATTER_STEPS == 0 && step > 0) {
            val line = (step / CHATTER_STEPS) % CHATTER.size
            when (line % COMPETITORS) {
                0 -> dave?.say(CHATTER[line])
                1 -> joshua?.say(CHATTER[line])
                else -> stranger?.takeIf { it.mode != NpcMode.None }?.say(CHATTER[line])
            }
        }
    }

    private fun find(type: String): Npc? = npcList.firstOrNull { it != null && it.isType(type) }

    private fun stranger(): Npc? = find(STRANGER)

    enum class Spot {
        PIPES,
        OPEN,
    }

    private enum class Topic {
        ENTER,
        RULES,
        LEAVE,
    }

    companion object {
        const val PIPE = "loc.garlicpipe"
        const val PIPE_SPOT = "npc.0_41_53_sinisterfishspot"
        const val OPEN_SPOT = "npc.0_41_53_compofishspot"
        const val BIG_DAVE_SPOT = "npc.0_41_53_bigdavefishspot"
        const val JOSHUA_SPOT = "npc.0_41_53_joshuafishspot"
        const val SARDINE = "obj.raw_sardine"

        const val ROUND_TIMER = "timer.fishingcontest_round"
        const val STRANGER_RETURN_TIMER = "timer.fishingcontest_stranger_return"
        const val CATCH_QUEUE = "queue.fishingcontest_catch"

        const val CAST_SEQ = "seq.human_fishing_casting"
        const val PLACE_SEQ = "seq.human_pickuptable"
        const val GAG_SEQ = "seq.human_wretch"
        const val SMELL_SPOTANIM = "spotanim.mortmyre_swampstench"

        const val CAST_TICKS = 5
        const val BITE_CHANCE = 2
        const val CHATTER_STEPS = 4
        const val COMPETITORS = 3
        const val STRANGER_AWAY_TICKS = 200

        val PIPE_SPOT_TILE = CoordGrid(2637, 3444, 0)
        val STRANGER_POST = CoordGrid(2637, 3440, 0)
        val STRANGER_AWAY_TILE = CoordGrid(2633, 3437, 0)

        val CHATTER =
            listOf(
                "Come on, come on... bite, you beauty!",
                "Shh! I think I've got one!",
                "The fish are... thirsty today.",
                "Three years champion, and counting!",
                "Mum always said I'd catch something big one day.",
                "Is it just me, or is it very sunny?",
            )
    }
}
