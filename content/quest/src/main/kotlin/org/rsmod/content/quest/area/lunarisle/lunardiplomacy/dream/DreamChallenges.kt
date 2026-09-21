package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.DreamChallenge
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarSpokenCentre
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The six islands of the Dream World. Their state is kept on the quest's cache varbits, several
 * of which overlap (the numbers game's sequence shares bits with the mimic's and the woodcutting
 * game's flags), so [clearScratch] wipes every island's working state whenever the player lands on
 * or leaves an island, and only the progress varbits survive.
 *
 * - Chance: six dice whose opposite faces sum to seven; flipping one swaps it between its two
 *   faces, and the Fluke's number must be made five times.
 * - Numbers: the Numerator reads out one of sixteen sequences; the next two numbers must be pressed.
 * - Mimic: copy five of the Mimic's emotes.
 * - Woodcutting: deposit twenty dream logs before the Perceptive, who adds two every few ticks.
 * - Race: beat the Expert, who walks a straight lane, over the course with four hurdles.
 * - Memory: cross a four-by-eight field of dream puffs; only the puffs on one hidden path hold.
 */
@Singleton
class DreamChallenges
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val dream: DreamWorld,
    private val launcher: ProtectedAccessLauncher,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
) {
    fun clearScratch(player: Player) {
        for (varbit in SCRATCH_VARBITS) {
            LunarDiplomacyQuest.setVarBit(player, varbit, 0)
        }
        player.clearSoftTimer(RIVAL_TIMER)
        raceTicks.remove(player.uid)
    }

    fun ProtectedAccess.leaveIsland(challenge: DreamChallenge) {
        if (challenge == DreamChallenge.Trees) {
            val logs = inv.count(DREAM_LOGS)
            if (logs > 0) {
                invDel(inv, DREAM_LOGS, logs)
            }
        }
        dream.npc(player, DreamWorld.EXPERT)?.teleport(collision, dream.at(player, EXPERT_HOME))
        clearScratch(player)
    }

    fun arrive(access: ProtectedAccess, challenge: DreamChallenge) {
        if (challenge == DreamChallenge.Memory) {
            generatePath(access.player)
        }
    }

    private suspend fun ProtectedAccess.finish(challenge: DreamChallenge) {
        challenge.setProgress(player, challenge.needed)
        player.lunarSpokenCentre = true
        lunar.pendingLesson.set(player, challenge.ordinal + 1)
        leaveIsland(challenge)
        player.midiJingle(REFLECTION_JINGLE)
        with(dream) { throwTo(DreamWorld.CENTRE) }
    }

    /* A game of chance */

    suspend fun ProtectedAccess.fluke() {
        val npc = dream.npc(player, DreamWorld.FLUKE)
        startDialogue(npc ?: return) {
            if (player.diceTarget == 0) {
                chatPlayer(neutral, "Hello.")
                chatNpc(laugh, "Let's have a game, ${player.displayName}! A game of luck! Or is it... Hahaha!")
                chatPlayer(worried, "Erm... I'm scared. What?")
                explainDice()
                resetDice(player)
                player.diceTarget = nextTarget(DICE_START_SUM)
                chatNpc(neutral, "The number you want is: ${player.diceTarget}")
                access.mes("The number you want is: ${player.diceTarget}")
                return@startDialogue
            }
            chatPlayer(neutral, "Hello.")
            chatNpc(quiz, "What lucky question will I get?")
            when (choice3("What number am I trying to make up?", 1, "What am I supposed to be doing?", 2, "Nothing, never mind.", 3)) {
                1 -> {
                    chatPlayer(quiz, "What number am I trying to make up?")
                    chatNpc(neutral, "The number ${player.diceTarget}!")
                }
                2 -> {
                    chatPlayer(quiz, "What am I supposed to be doing?")
                    explainDice()
                }
                else -> chatPlayer(neutral, "Nothing, never mind.")
            }
        }
    }

    private suspend fun Dialogue.explainDice() {
        chatNpc(
            neutral,
            "Look around and you'll see six dice. I call out a number, and you roll the dice over " +
                "until their top faces add up to it. There are many ways to make most numbers, but " +
                "the dice are perhaps not all that random!",
        )
    }

    suspend fun ProtectedAccess.rollDie(die: Die) {
        if (player.diceTarget == 0) {
            mesbox("You should probably talk to that nearby character first.")
            return
        }
        anim(ROLL_SEQ)
        soundSynth(DICE_SOUND)
        val flipped = if (die.state(player) == 0) die.flipped else 0
        LunarDiplomacyQuest.setVarBit(player, die.varbit, DICE_SHAKE)
        delay(DICE_SHAKE_TICKS)
        LunarDiplomacyQuest.setVarBit(player, die.varbit, flipped)
        val sum = Die.entries.sumOf { it.face(player) }
        if (sum != player.diceTarget) {
            return
        }
        val progress = DreamChallenge.Dice.progress(player) + 1
        DreamChallenge.Dice.setProgress(player, progress)
        val fluke = dream.npc(player, DreamWorld.FLUKE)
        if (progress < DreamChallenge.Dice.needed) {
            player.diceTarget = nextTarget(sum)
            fluke?.say("Well done! The next number is: ${player.diceTarget}")
            mes("The next number is: ${player.diceTarget}")
            return
        }
        if (fluke != null) {
            startDialogue(fluke) {
                chatNpc(
                    happy,
                    "You've done it! Good work. Remember: luck plays its part in things, but that " +
                        "doesn't mean there is no reason behind your surroundings. Look hard enough " +
                        "and you'll see it.",
                )
                chatPlayer(happy, "Ok. Thanks!")
                chatNpc(happy, "Ok, back you go!")
            }
        }
        finish(DreamChallenge.Dice)
    }

    private fun resetDice(player: Player) {
        for (die in Die.entries) {
            LunarDiplomacyQuest.setVarBit(player, die.varbit, 0)
        }
    }

    private fun nextTarget(current: Int): Int {
        var target: Int
        do {
            target = random.of(DICE_MIN_SUM, DICE_MAX_SUM)
        } while (target == current)
        return target
    }

    /* Communicating in numbers */

    suspend fun ProtectedAccess.numerator() {
        val npc = dream.npc(player, DreamWorld.NUMERATOR) ?: return
        startDialogue(npc) {
            if (!player.numbersIntro) {
                chatPlayer(quiz, "Hello there. What's going on here?")
                chatNpc(happy, "127369186!")
                chatPlayer(confused, "Huh?")
                chatNpc(quiz, "879732749 31764 762 908723089?")
                chatPlayer(confused, "I... caaannooot... uuunnnderstaaand... youuu!")
                chatNpc(
                    neutral,
                    "Oh, sorry. I'm not 7676908 used to 768 speaking like 1324719 this. Fancy a " +
                        "4354353 game? Complete 353 my sequences 43553 using the numbers 56367 " +
                        "around you. Tell me 24757 the next two numbers!",
                )
                chatPlayer(neutral, "Erm, ok. I can't really say no in this place.")
                player.numbersIntro = true
                newSequence(player, exclude = -1)
                chatNpc(neutral, currentSequence(player))
                return@startDialogue
            }
            chatPlayer(neutral, "Hello.")
            chatNpc(neutral, "127369186.")
            when (choice3("What was the sequence again?", 1, "What am I supposed to be doing?", 2, "Nothing, never mind.", 3)) {
                1 -> {
                    chatPlayer(quiz, "What was the sequence again?")
                    chatNpc(neutral, currentSequence(player))
                }
                2 -> {
                    chatPlayer(quiz, "What am I supposed to be doing?")
                    chatNpc(neutral, "Complete the 295621 sequences I tell 2428428 you, using the numbers 792682 around you!")
                    chatNpc(neutral, currentSequence(player))
                }
                else -> chatPlayer(neutral, "Nothing, never mind.")
            }
        }
    }

    suspend fun ProtectedAccess.pressNumber(number: Int) {
        if (!player.numbersIntro) {
            mesbox("It would be best to talk to that interesting fellow first.")
            return
        }
        val word = NUMBER_WORDS[number]
        anim(PRESS_SEQ)
        soundSynth(PRESS_SOUND)
        say("$word!")
        mes("$word!")
        delay(1)
        val sequence = SEQUENCES[player.numbersSequence.coerceIn(SEQUENCES.indices)]
        val position = player.numbersAnswered
        val npc = dream.npc(player, DreamWorld.NUMERATOR)
        if (number != sequence.answers[position]) {
            newSequence(player, exclude = player.numbersSequence)
            npc?.say("That's not it! Try a new sequence!")
            mes(currentSequence(player))
            return
        }
        if (position == 0) {
            player.numbersAnswered = 1
            npc?.say("That's correct! Onto the next 112908 number!")
            mes("So far we have the sequence: ${sequence.text}, ${sequence.answers[0]}")
            return
        }
        val done = DreamChallenge.Numbers.progress(player) + 1
        DreamChallenge.Numbers.setProgress(player, done)
        if (done < DreamChallenge.Numbers.needed) {
            val left = DreamChallenge.Numbers.needed - done
            newSequence(player, exclude = player.numbersSequence)
            npc?.say("That's it! $left more to go!")
            mes("That's it! You've completed $done ${if (done == 1) "sequence" else "sequences"}. ${currentSequence(player)}")
            return
        }
        if (npc != null) {
            startDialogue(npc) {
                chatNpc(happy, "That's it! I think 34132421 you've got the hang 124151 of this now!")
                chatPlayer(happy, "Phew!")
                chatNpc(
                    neutral,
                    "Please remember 23235 this: you must be able to 26243 communicate with and " +
                        "understand others. They have 4363472 wisdom and experience to share!",
                )
                chatPlayer(bored, "Yeah, yeah. What now?")
                chatNpc(happy, "Off you go!")
            }
        }
        finish(DreamChallenge.Numbers)
    }

    private fun newSequence(player: Player, exclude: Int) {
        var next: Int
        do {
            next = random.of(0, SEQUENCES.lastIndex)
        } while (next == exclude)
        player.numbersSequence = next
        player.numbersAnswered = 0
    }

    private fun currentSequence(player: Player): String =
        SEQUENCES[player.numbersSequence.coerceIn(SEQUENCES.indices)].text

    /* Anything you can do... */

    suspend fun ProtectedAccess.mimic() {
        val npc = dream.npc(player, DreamWorld.MIMIC) ?: return
        if (player.mimicPlaying) {
            startDialogue(npc) { chatNpc(happy, "Follow my lead! Tee hee hee.") }
            performNext(player, repeat = true)
            return
        }
        var playing = false
        startDialogue(npc) {
            chatPlayer(neutral, "Hello there. What's going on?")
            chatNpc(happy, "Follow my lead!")
            chatPlayer(confused, "Huh, wait a second, who are...")
            chatNpc(laugh, "See what you will learn! Tee hee hee.")
            chatPlayer(bored, "You're not all there, are you?")
            chatNpc(happy, "Hey! This is your dream! So what do you say?")
            playing = choice2("Suppose I may as well have a go.", true, "I think I'll pass.", false)
            if (playing) {
                chatPlayer(neutral, "Suppose I may as well have a go.")
                chatNpc(happy, "Follow!")
            } else {
                chatPlayer(neutral, "I think I'll pass.")
                chatNpc(sad, "Oh, that's no fun.")
            }
        }
        ifClose()
        if (!playing) {
            return
        }
        player.mimicPlaying = true
        player.mimicEmote = 0
        performNext(player, repeat = false)
    }

    private fun performNext(player: Player, repeat: Boolean) {
        val npc = dream.npc(player, DreamWorld.MIMIC) ?: return
        val emote =
            if (repeat && player.mimicEmote != 0) {
                MimicEmote.entries[player.mimicEmote - 1]
            } else {
                MimicEmote.entries.filter { it.ordinal + 1 != player.mimicEmote }.random()
            }
        player.mimicEmote = emote.ordinal + 1
        npc.teleport(collision, dream.at(player, emote.tile))
        npc.facePlayer(player)
        npc.anim(emote.seq)
    }

    /** Called for every emote the player plays; only matters while they are copying the Mimic. */
    fun emotePlayed(player: Player, seqId: Int) {
        if (!player.mimicPlaying || player.mimicEmote == 0 || !dream.isDreaming(player)) {
            return
        }
        val npc = dream.npc(player, DreamWorld.MIMIC) ?: return
        val expected = MimicEmote.entries[player.mimicEmote - 1]
        if (seqId != expected.seq.asRSCM(RSCMType.SEQ)) {
            npc.say("NO no no!")
            npc.anim(expected.seq)
            return
        }
        val progress = DreamChallenge.Mimic.progress(player) + 1
        DreamChallenge.Mimic.setProgress(player, progress)
        npc.say(MIMIC_CHEERS[(progress - 1).coerceIn(MIMIC_CHEERS.indices)])
        if (progress < DreamChallenge.Mimic.needed) {
            performNext(player, repeat = false)
            return
        }
        player.mimicPlaying = false
        launcher.launch(player) {
            startDialogue(npc) {
                chatNpc(
                    happy,
                    "You soon picked that up! Can you see how important it is to be able to relate " +
                        "to others? You can learn a great deal from it.",
                )
                chatPlayer(neutral, "I see. There's a lot more to communication than words. Thank you.")
                chatNpc(happy, "Ok, back you go!")
            }
            finish(DreamChallenge.Mimic)
        }
    }

    /* Chop, chop, chop away */

    suspend fun ProtectedAccess.perceptive() {
        val npc = dream.npc(player, DreamWorld.PERCEPTIVE) ?: return
        var start = false
        startDialogue(npc) {
            if (player.treePlaying) {
                chatPlayer(neutral, "Hello.")
                chatNpc(angry, "Stop distractin' me, go chop dem troys!")
                return@startDialogue
            }
            if (!player.treeIntro) {
                chatPlayer(neutral, "Hello.")
                chatNpc(happy, "Why 'ello there. Do yow like me troys?")
                chatPlayer(quiz, "Troys? Oh, trees?")
                chatNpc(happy, "Ya, troys. Bet you canna chop dem troys as fast as me.")
                chatPlayer(neutral, "I dunno.")
                chatNpc(
                    neutral,
                    "Ya dunno? Yow gotta know wot yow can do, or 'ow can yow judge wot yow need " +
                        "ta learn? Chop logs an' pile 'em in the centre. First ta twenty wins.",
                )
                player.treeIntro = true
            } else {
                chatPlayer(neutral, "Hello.")
                chatNpc(quiz, "Wot do yow want now?")
            }
            start = choice2("Ok, let's go!", true, "Not just yet.", false)
            if (!start) {
                chatPlayer(neutral, "Not just yet.")
                chatNpc(neutral, "'Av it your way!")
                return@startDialogue
            }
            chatPlayer(happy, "Ok, let's go!")
            if (findAxe(access) == null) {
                chatNpc(quiz, "You got an axe?")
                chatPlayer(sad, "Not one I can use, no.")
                if (player.inv.freeSpace() == 0) {
                    chatPlayer(sad, "And I don't have any space for one.")
                    start = false
                    return@startDialogue
                }
                chatNpc(neutral, "I can give yow a bronze axe, but nowt more.")
                access.invAdd(access.inv, BRONZE_AXE)
                chatPlayer(happy, "Thanks.")
            }
        }
        ifClose()
        if (!start) {
            return
        }
        player.treePlaying = true
        player.playerLogs = 0
        player.rivalLogs = 0
        player.playerLogPile = 0
        player.rivalLogPile = 0
        softTimer(RIVAL_TIMER, TREE_RIVAL_TICKS)
        mes("Go, go, go!")
    }

    suspend fun ProtectedAccess.chopDreamTree(loc: BoundLocInfo) {
        if (!player.treePlaying && !DreamWorld.inArena(dream.toWorld(player, loc.coords))) {
            mesbox("You should probably talk to that nearby character first.")
            return
        }
        val world = dream.toWorld(player, loc.coords)
        if (DreamWorld.inArena(world)) {
            mesbox("Now is not the time!")
            return
        }
        if (world.x >= RIVAL_TREES_X) {
            dream.npc(player, DreamWorld.PERCEPTIVE)?.say("Oi! Get off moi troys!")
            mesbox("You can't chop from his trees. Yours are at the other end of the island.")
            return
        }
        val axe = findAxe(this)
        if (axe == null) {
            mes("You need an axe to chop down this tree.")
            return
        }
        while (player.treePlaying) {
            if (inv.freeSpace() == 0) {
                mes("Your inventory is too full to hold any more logs.")
                return
            }
            faceLoc(loc)
            anim(axe)
            soundSynth(CHOP_SOUND)
            delay(CHOP_TICKS)
            if (random.of(0, CHOP_ROLL) < chopChance(statBase(WOODCUTTING))) {
                invAdd(inv, DREAM_LOGS)
                mes("You get a dream log.")
            }
        }
        resetAnim()
    }

    private fun chopChance(level: Int): Int = (CHOP_BASE_CHANCE + level).coerceAtMost(CHOP_ROLL)

    suspend fun ProtectedAccess.depositLogs() {
        if (!player.treePlaying) {
            mesbox("You should probably talk to that nearby character first.")
            return
        }
        val logs = inv.count(DREAM_LOGS)
        if (logs == 0) {
            mes("You have no logs.")
            return
        }
        invDel(inv, DREAM_LOGS, logs)
        anim(DEPOSIT_SEQ)
        val total = player.playerLogs + logs
        player.playerLogs = total.coerceAtMost(MAX_LOG_COUNT)
        player.playerLogPile = pileSize(total)
        mes("You deposited $logs logs, giving you $total in total.")
        if (total < LOGS_TO_WIN) {
            return
        }
        clearSoftTimer(RIVAL_TIMER)
        player.treePlaying = false
        val npc = dream.npc(player, DreamWorld.PERCEPTIVE)
        if (npc != null) {
            startDialogue(npc) {
                chatNpc(happy, "Well done. I see yow do 'ave a good command of your abilitoys.")
                chatPlayer(happy, "Thanks.")
                chatNpc(neutral, "Remember this: yow need ta know wot yow can do, so yow know wot ta learn. Now off yow go!")
            }
        }
        finish(DreamChallenge.Trees)
    }

    private fun pileSize(logs: Int): Int = (logs / 2).coerceAtMost(MAX_PILE)

    private fun rivalTick(player: Player) {
        if (!player.treePlaying) {
            player.clearSoftTimer(RIVAL_TIMER)
            return
        }
        val logs = player.rivalLogs + RIVAL_LOGS_PER_TRIP
        player.rivalLogs = logs.coerceAtMost(MAX_LOG_COUNT)
        player.rivalLogPile = pileSize(logs)
        dream.npc(player, DreamWorld.PERCEPTIVE)?.say("Dat's two more logs from dem troys.")
        player.mes("He's collected: $logs logs so far!")
        if (logs < LOGS_TO_WIN) {
            return
        }
        player.treePlaying = false
        player.clearSoftTimer(RIVAL_TIMER)
        player.playerLogs = 0
        player.rivalLogs = 0
        player.playerLogPile = 0
        player.rivalLogPile = 0
        dream.npc(player, DreamWorld.PERCEPTIVE)?.say("Har har, I did beat ya! Yow ain't got no skiws.")
        player.mes("The Ethereal Perceptive beat you to twenty logs. Talk to him to try again.")
    }

    private fun findAxe(access: ProtectedAccess): String? {
        for ((axe, seq) in AXES) {
            if (axe in access.player.inv || axe in access.player.worn) {
                return seq
            }
        }
        return null
    }

    /* The race is on! */

    suspend fun ProtectedAccess.expert() {
        val npc = dream.npc(player, DreamWorld.EXPERT) ?: return
        var race = false
        startDialogue(npc) {
            if (player.raceRunning) {
                chatNpc(quiz, "Oh, hello? Looks like you'll want to start again.")
                race = choice2("Ok.", true, "No thanks.", false)
                if (!race) {
                    chatPlayer(neutral, "No thanks.")
                    chatNpc(neutral, "Have it your way.")
                    player.raceRunning = false
                    player.clearSoftTimer(RIVAL_TIMER)
                }
                return@startDialogue
            }
            chatPlayer(neutral, "This looks like an interesting island.")
            chatNpc(happy, "Oh, it is. Fancy a race?!")
            chatPlayer(confused, "That's a bit sudden. What kind of race?")
            chatNpc(
                happy,
                "I bet I can beat you to the far end of this island. I'll take the left route, " +
                    "and you take the right.",
            )
            chatPlayer(angry, "That's hardly fair. Mine's all curvy and yours is straight!")
            chatNpc(neutral, "That's life, I'm afraid. It's never as easy as you'd like. You take the path you're dealt!")
            race = choice2("Ok.", true, "No thanks.", false)
            if (!race) {
                chatPlayer(neutral, "No thanks.")
                chatNpc(neutral, "Have it your way.")
            }
        }
        ifClose()
        if (!race) {
            return
        }
        player.raceRunning = false
        clearSoftTimer(RIVAL_TIMER)
        telejump(dream.at(player, RACE_START), TeleportType.Exempt)
        npc.teleport(collision, dream.at(player, EXPERT_LANE_START))
        npc.lockFacingDirection(Direction.North)
        npc.say("On your marks!")
        delay(RACE_COUNT_TICKS)
        npc.say("Get set!")
        delay(RACE_COUNT_TICKS)
        npc.say("Go!!!")
        player.raceRunning = true
        raceTicks[player.uid] = 0
        softTimer(RIVAL_TIMER, RACE_TICK)
    }

    suspend fun ProtectedAccess.jumpHurdle(loc: BoundLocInfo) {
        if (!player.raceRunning && !DreamChallenge.Race.isComplete(player)) {
            mesbox("You should probably talk to that nearby character first.")
            return
        }
        val north = coords.z < loc.coords.z
        val dest = CoordGrid(coords.x, if (north) loc.coords.z + 1 else loc.coords.z - 1, coords.level)
        val failChance = (HURDLE_BASE_FAIL - statBase(AGILITY) / HURDLE_AGILITY_DIVISOR).coerceAtLeast(HURDLE_MIN_FAIL)
        if (random.of(0, PERCENT) < failChance) {
            anim(HURDLE_FAIL_SEQ)
            spotanim(HURDLE_FAIL_SPOT)
            soundSynth(SLIP_SOUND)
            delay(2)
            takeInstantHit(HitType.Typeless, HURDLE_DAMAGE)
            mes("You clip the hurdle and are thrown back by a jolt of energy!")
            return
        }
        anim(HURDLE_SEQ)
        soundSynth(HURDLE_SOUND)
        exactMove(
            start = coords,
            end = dest,
            delay1 = 0,
            delay2 = HURDLE_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (north) constants.em_face_north else constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(HURDLE_TICKS)
    }

    private val raceTicks = HashMap<PlayerUid, Int>()

    private fun raceTick(player: Player) {
        if (!player.raceRunning) {
            player.clearSoftTimer(RIVAL_TIMER)
            return
        }
        val npc = dream.npc(player, DreamWorld.EXPERT)
        val world = dream.worldCoords(player)
        if (world.z >= RACE_FINISH_Z) {
            player.raceRunning = false
            player.clearSoftTimer(RIVAL_TIMER)
            launcher.launch(player) { wonRace() }
            return
        }
        val ticks = (raceTicks[player.uid] ?: 0) + 1
        raceTicks[player.uid] = ticks
        if (npc == null || ticks % EXPERT_STEP_EVERY == 0) {
            return
        }
        val npcWorld = dream.toWorld(player, npc.coords)
        val next = CoordGrid(EXPERT_LANE_START.x, npcWorld.z + 1, npcWorld.level)
        npc.teleport(collision, dream.at(player, next))
        if (next.z < RACE_FINISH_Z) {
            return
        }
        player.raceRunning = false
        player.clearSoftTimer(RIVAL_TIMER)
        npc.say("Woo hoo! Ha ha! You'll never beat me, I'm far too fast!")
        player.mes("The Ethereal Expert beat you to the end. Talk to him to race again.")
        npc.teleport(collision, dream.at(player, EXPERT_HOME))
    }

    private suspend fun ProtectedAccess.wonRace() {
        val npc = dream.npc(player, DreamWorld.EXPERT)
        if (npc != null) {
            npc.teleport(collision, dream.at(player, RACE_FINISH_EXPERT))
            startDialogue(npc) {
                chatPlayer(happy, "I won!")
                chatNpc(shocked, "Noooo! How could you beat me?")
                chatPlayer(happy, "I'm just making the most of the abilities I've worked hard for, that's all!")
                chatNpc(neutral, "True. A very important thing to remember! Now be gone!")
            }
        }
        finish(DreamChallenge.Race)
    }

    /* Where am I? */

    suspend fun ProtectedAccess.guide() {
        val npc = dream.npc(player, DreamWorld.GUIDE) ?: return
        startDialogue(npc) {
            chatPlayer(neutral, "Hey there.")
            chatNpc(happy, "You know, everything is a journey!")
            chatPlayer(bored, "Don't talk to me about journeys! I'm amazed I have any feet left.")
            chatNpc(
                neutral,
                "I know, I know. But at every step it mattered that you knew where you'd been, " +
                    "where you were, and where you were going. Get across to the other side and " +
                    "you'll see what I mean.",
            )
        }
    }

    private fun generatePath(player: Player) {
        val columns = IntArray(PUFF_COLUMNS)
        var column = random.of(0, PUFF_COLUMNS - 1)
        for (row in PUFF_ROWS - 1 downTo 0) {
            columns[column] = columns[column] or (1 shl row)
            if (row == 0) {
                break
            }
            val target = random.of(0, PUFF_COLUMNS - 1)
            val step = if (target > column) 1 else -1
            while (column != target) {
                column += step
                columns[column] = columns[column] or (1 shl row)
            }
        }
        for ((i, varbit) in FLOOR_COLUMNS.withIndex()) {
            LunarDiplomacyQuest.setVarBit(player, varbit, columns[i])
        }
    }

    private fun isRealPuff(player: Player, column: Int, row: Int): Boolean =
        (player.vars[FLOOR_COLUMNS[column]] shr row) and 1 == 1

    suspend fun ProtectedAccess.jumpToPuff(loc: BoundLocInfo) {
        val from = dream.worldCoords(player)
        val to = dream.toWorld(player, loc.coords)
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (dx != 0 && dz != 0) {
            mes("You can't jump diagonally!")
            return
        }
        if (abs(dx) + abs(dz) != PUFF_SPACING) {
            mes("That's too far to jump from here.")
            return
        }
        if (player.vars[FLOOR_COLUMNS[0]] == 0 && player.vars[FLOOR_COLUMNS[1]] == 0) {
            generatePath(player)
        }
        anim(JUMP_SEQ)
        soundSynth(JUMP_SOUND)
        exactMove(
            start = coords,
            end = loc.coords,
            delay1 = 0,
            delay2 = PUFF_JUMP_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = direction(dx, dz),
            teleportType = TeleportType.Exempt,
        )
        delay(PUFF_JUMP_TICKS)
        if (to.z <= MEMORY_END_Z) {
            reachedEnd()
            return
        }
        if (to.z >= MEMORY_START_Z) {
            return
        }
        val column = (to.x - PUFF_FIRST_X) / PUFF_SPACING
        val row = (to.z - PUFF_FIRST_Z) / PUFF_SPACING
        if (isRealPuff(player, column, row)) {
            return
        }
        locAnim(worldRepo, loc, PUFF_FALL_SEQ)
        anim(PUFF_FALL_PLAYER_SEQ)
        soundSynth(SLIP_SOUND)
        mes("The dream puff dissolves beneath your feet!")
        delay(2)
        telejump(dream.at(player, MEMORY_START), TeleportType.Exempt)
        anim(DreamWorld.LANDING_SEQ)
        soundSynth(DreamWorld.LAND_SOUND)
        delay(2)
        anim(DreamWorld.STAND_SEQ)
    }

    private suspend fun ProtectedAccess.reachedEnd() {
        startDialogue {
            chatNpcSpecific("Ethereal Guide", DreamWorld.GUIDE, happy, "Well done! So tell me, what have you learned?")
            chatPlayer(
                neutral,
                "That it helps to remember where you've been, where you are and where you're " +
                    "headed. I needed all three to get across, and some trial and error for the " +
                    "parts nobody can know in advance.",
            )
            chatNpcSpecific("Ethereal Guide", DreamWorld.GUIDE, happy, "Very deep! Excellent. I shall send you back!")
        }
        finish(DreamChallenge.Memory)
    }

    private fun direction(dx: Int, dz: Int): Int =
        when {
            dz > 0 -> constants.em_face_north
            dz < 0 -> constants.em_face_south
            dx > 0 -> constants.em_face_east
            else -> constants.em_face_west
        }

    fun onRivalTimer(player: Player) {
        when {
            player.treePlaying -> rivalTick(player)
            player.raceRunning -> raceTick(player)
            else -> player.clearSoftTimer(RIVAL_TIMER)
        }
    }

    /** One of the six dice: the varbit behind it and the value that shows its other face. */
    enum class Die(val loc: String, val varbit: String, val up: Int, val flipped: Int) {
        One("loc.quest_lunar_dice_1up_multi", "varbit.lunar_quest_dicepos1", 1, 5),
        Two("loc.quest_lunar_dice_2up_multi", "varbit.lunar_quest_dicepos2", 2, 3),
        Three("loc.quest_lunar_dice_3up_multi", "varbit.lunar_quest_dicepos3", 3, 1),
        Four("loc.quest_lunar_dice_4up_multi", "varbit.lunar_quest_dicepos4", 4, 5),
        Five("loc.quest_lunar_dice_5up_multi", "varbit.lunar_quest_dicepos5", 5, 3),
        Six("loc.quest_lunar_dice_6up_multi", "varbit.lunar_quest_dicepos6", 6, 1);

        fun state(player: Player): Int = player.vars[varbit]

        fun face(player: Player): Int = if (state(player) == 0) up else DICE_OPPOSITE - up
    }

    private enum class MimicEmote(val seq: String, val tile: CoordGrid) {
        Cry("seq.emote_cry", CoordGrid(1769, 5058, 2)),
        Bow("seq.emote_bow", CoordGrid(1770, 5063, 2)),
        Dance("seq.emote_dance", CoordGrid(1772, 5070, 2)),
        Wave("seq.emote_wave", CoordGrid(1767, 5061, 2)),
        Think("seq.emote_think", CoordGrid(1771, 5069, 2)),
    }

    private class NumberSequence(val text: String, val answers: List<Int>)

    companion object {
        const val RIVAL_TIMER = "timer.lunar_dream_rival"
        const val REFLECTION_JINGLE = 95

        private var Player.diceTarget by intVarBit("varbit.lunar_dice_curnum")
        private var Player.numbersIntro by boolVarBit("varbit.lunar_num_intro")
        private var Player.numbersSequence by intVarBit("varbit.lunar_num_curseq")
        private var Player.numbersAnswered by intVarBit("varbit.lunar_pt3_num_seq_n")
        private var Player.treeIntro by boolVarBit("varbit.lunar_tree_intro")
        private var Player.treePlaying by boolVarBit("varbit.lunar_tree_playing")
        private var Player.playerLogs by intVarBit("varbit.lunar_logcol")
        private var Player.rivalLogs by intVarBit("varbit.lunar_pt3_tree_npc_col")
        private var Player.playerLogPile by intVarBit("varbit.lunar_logmulti_pl")
        private var Player.rivalLogPile by intVarBit("varbit.lunar_logmulti_npc")
        private var Player.mimicPlaying by boolVarBit("varbit.lunar_emote_canmimic")
        private var Player.mimicEmote by intVarBit("varbit.lunar_emote_loc")
        private var Player.raceRunning by boolVarBit("varbit.lunar_skill_intro")

        val SCRATCH_VARBITS =
            listOf(
                "varbit.lunar_dice_curnum",
                "varbit.lunar_quest_dicepos1",
                "varbit.lunar_quest_dicepos2",
                "varbit.lunar_quest_dicepos3",
                "varbit.lunar_quest_dicepos4",
                "varbit.lunar_quest_dicepos5",
                "varbit.lunar_quest_dicepos6",
                "varbit.lunar_num_intro",
                "varbit.lunar_num_curseq",
                "varbit.lunar_pt3_num_seq_n",
                "varbit.lunar_tree_intro",
                "varbit.lunar_emote_canmimic",
                "varbit.lunar_emote_loc",
                "varbit.lunar_pt3_tree_npc_col",
                "varbit.lunar_skill_intro",
                "varbit.lunar_logmulti_npc",
                "varbit.lunar_logmulti_pl",
                "varbit.lunar_logcol",
                "varbit.lunar_tree_playing",
                "varbit.lunar_floor_col_a",
                "varbit.lunar_floor_col_b",
                "varbit.lunar_floor_col_c",
                "varbit.lunar_floor_col_d",
                "varbit.lunar_battle_pos",
            )

        private const val DICE_START_SUM = 21
        private const val DICE_MIN_SUM = 12
        private const val DICE_MAX_SUM = 30
        private const val DICE_OPPOSITE = 7
        private const val DICE_SHAKE = 6
        private const val DICE_SHAKE_TICKS = 1
        private const val ROLL_SEQ = "seq.human_pickuptable"
        private const val DICE_SOUND = "synth.moon_dice_roll"

        private val NUMBER_WORDS = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
        private const val PRESS_SEQ = "seq.human_pickuptable"
        private const val PRESS_SOUND = "synth.moon_press_number"

        /** Indexed by `varbit.lunar_num_curseq`, the same order the client's quest helper reads. */
        private val SEQUENCES =
            listOf(
                NumberSequence("1, 3, 5", listOf(7, 9)),
                NumberSequence("1, 9, 2, 8", listOf(3, 7)),
                NumberSequence("0, 1, 3, 4", listOf(6, 7)),
                NumberSequence("1, 1, 1, 2, 1, 3, 1, 4", listOf(1, 5)),
                NumberSequence("8, 6, 4", listOf(2, 0)),
                NumberSequence("1, 2, 3", listOf(4, 5)),
                NumberSequence("3, 4, 2, 5", listOf(1, 6)),
                NumberSequence("1, 1, 2, 2, 3", listOf(3, 4)),
                NumberSequence("1, 6, 2, 5", listOf(3, 4)),
                NumberSequence("2, 3, 5, 6", listOf(8, 9)),
                NumberSequence("9, 7, 5", listOf(3, 1)),
                NumberSequence("1, 4, 2, 5", listOf(3, 6)),
                NumberSequence("2, 6, 3, 7", listOf(4, 8)),
                NumberSequence("7, 3, 6, 2", listOf(5, 1)),
                NumberSequence("9, 8, 7, 6", listOf(5, 4)),
                NumberSequence("1, 1, 2, 3, 1, 1, 4", listOf(5, 1)),
            )

        private val MIMIC_CHEERS = listOf("Well done!", "Woo hoo!", "That's it!", "Nice one!", "Yep, you've got it!")

        private const val DREAM_LOGS = "obj.lunar_dream_logs"
        private const val BRONZE_AXE = "obj.bronze_axe"
        private const val WOODCUTTING = "stat.woodcutting"
        private const val RIVAL_TREES_X = 1760
        private const val TREE_RIVAL_TICKS = 12
        private const val RIVAL_LOGS_PER_TRIP = 2
        private const val LOGS_TO_WIN = 20
        private const val MAX_LOG_COUNT = 63
        private const val MAX_PILE = 9
        private const val CHOP_TICKS = 3
        private const val CHOP_ROLL = 100
        private const val CHOP_BASE_CHANCE = 30
        private const val CHOP_SOUND = "synth.moon_woodchop"
        private const val DEPOSIT_SEQ = "seq.human_pickuptable"

        private val AXES =
            listOf(
                "obj.dragon_axe" to "seq.human_woodcutting_dragon_axe",
                "obj.rune_axe" to "seq.human_woodcutting_rune_axe",
                "obj.adamant_axe" to "seq.human_woodcutting_adamant_axe",
                "obj.mithril_axe" to "seq.human_woodcutting_mithril_axe",
                "obj.black_axe" to "seq.human_woodcutting_black_axe",
                "obj.steel_axe" to "seq.human_woodcutting_steel_axe",
                "obj.iron_axe" to "seq.human_woodcutting_iron_axe",
                "obj.bronze_axe" to "seq.human_woodcutting_bronze_axe",
            )

        private val RACE_START = CoordGrid(1786, 5080, 2)
        private val EXPERT_HOME = CoordGrid(1787, 5079, 2)
        private val EXPERT_LANE_START = CoordGrid(1782, 5080, 2)
        private val RACE_FINISH_EXPERT = CoordGrid(1782, 5108, 2)
        private const val RACE_FINISH_Z = 5107
        private const val RACE_COUNT_TICKS = 2
        private const val RACE_TICK = 1
        private const val EXPERT_STEP_EVERY = 3
        private const val AGILITY = "stat.agility"
        private const val HURDLE_BASE_FAIL = 40
        private const val HURDLE_AGILITY_DIVISOR = 3
        private const val HURDLE_MIN_FAIL = 5
        private const val PERCENT = 100
        private const val HURDLE_DAMAGE = 8
        private const val HURDLE_TICKS = 2
        private const val HURDLE_SEQ = "seq.quest_lunar_dreamland_jump_hurdle"
        private const val HURDLE_FAIL_SEQ = "seq.quest_lunar_dreamland_jump_hurdle_shock"
        private const val HURDLE_FAIL_SPOT = "spotanim.quest_lunar_dreamland_hurdle_shock_spotanim"
        private const val HURDLE_SOUND = "synth.moon_hurdle"
        private const val SLIP_SOUND = "synth.moon_slip"

        private val FLOOR_COLUMNS =
            listOf(
                "varbit.lunar_floor_col_a",
                "varbit.lunar_floor_col_b",
                "varbit.lunar_floor_col_c",
                "varbit.lunar_floor_col_d",
            )
        private const val PUFF_COLUMNS = 4
        private const val PUFF_ROWS = 8
        private const val PUFF_FIRST_X = 1731
        private const val PUFF_FIRST_Z = 5085
        private const val PUFF_SPACING = 3
        private const val PUFF_JUMP_TICKS = 2
        private const val MEMORY_START_Z = 5108
        private const val MEMORY_END_Z = 5083
        private val MEMORY_START = CoordGrid(1736, 5110, 2)
        private const val JUMP_SEQ = "seq.human_jump_stones"
        private const val JUMP_SOUND = "synth.moon_jump"
        private const val PUFF_FALL_SEQ = "seq.quest_lunar_puff_platform_fall"
        private const val PUFF_FALL_PLAYER_SEQ = "seq.human_jump_hurdle"

        private const val CLIENT_CYCLES_PER_TICK = 30
    }
}
