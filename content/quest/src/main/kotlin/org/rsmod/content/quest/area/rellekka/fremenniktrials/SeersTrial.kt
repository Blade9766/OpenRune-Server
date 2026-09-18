package org.rsmod.content.quest.area.rellekka.fremenniktrials

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.interfaces.bank.selectedTab
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.PEER
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.outerlanderRebuff
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Peer the Seer's trial: enter his house with nothing at all, and leave by the other door.
 *
 * The front door has a combination lock whose four letters answer one of six riddles. Inside, the
 * way out needs the Seer's key, frozen into ice inside a sealed vase. The vase is locked in a chest
 * balanced by exactly four litres of water (measured with the five-litre bucket and three-litre
 * jug), and its lid is the centre of the mural in the downstairs room, which falls out once both
 * red disks are in place; the second disk is the wooden one, coloured with the goop from the red
 * herring cooked on the range. The filled, sealed vase freezes on the icy table and shatters, and
 * the range thaws the key.
 */
class SeersTrial
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val bankInv: BankInvScript,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(PEER) { startDialogue(it.npc) { peer() } }
        onOpNpc3(PEER) { startDialogue(it.npc) { depositOption() } }

        onOpLoc1(FRONT_DOOR) { frontDoor(it.loc, it.type) }
        onOpLoc1(BACK_DOOR) { backDoor(it.loc, it.type) }
        for (button in LOCK_BUTTONS) {
            onIfModalButton(button.component) { turnWheel(button.wheel, button.step) }
        }
        onIfModalButton(LOCK_ENTER) { tryCombination() }

        onOpLoc1(ENTRANCE_LADDER) { climb(UPSTAIRS_WEST) }
        onOpLoc1(EXIT_LADDER) { climb(UPSTAIRS_EAST) }
        onOpLoc1(TRAPDOOR_OPEN) { trapdoorDown(it.loc) }
        onOpLoc2(TRAPDOOR_OPEN) { closeTrapdoor(it.loc) }
        onOpLoc1(TRAPDOOR_CLOSED) { openTrapdoor(it.loc) }

        onOpLoc1(BOOKCASE) { searchBookcase(it.loc) }
        onOpLoc1(UNICORN_HEAD) { unicornHead() }
        onOpLoc1(BULL_HEAD) { bullHead() }
        onOpLoc1(CUPBOARD) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { shutCupboard(it.loc) }
        onOpLoc2(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc1(CHEST) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { closeChest(it.loc) }
        onOpLoc2(CHEST_OPEN) { searchChest(it.loc) }
        onOpLoc1(SCALES_CHEST) {
            mesbox(
                "This chest is securely locked shut. There is some kind of balance attached to the " +
                    "lock, and a number four is painted just above it.",
            )
        }
        onOpLoc1(BOXES) { searchBoxes(it.loc, MAGNET, SW_BOXES, "a magnet") }
        onOpLoc1(EAST_BOXES) { searchBoxes(it.loc, THREAD, it.loc.coords, "some thread") }
        onOpLoc1(CRATE) { searchBoxes(it.loc, PICK, EAST_CRATE, "a small pick") }
        onOpLoc1(CRATE_SOUTH_EAST) { searchBoxes(it.loc, TOY_SHIP, it.loc.coords, "a toy ship") }
        onOpLoc1(MURAL) { mes("You study the mural... it appears to be missing something...") }

        onOpLocU(TAP) { useOnTap(it.objType) }
        onOpLocU(DRAIN) { useOnDrain(it.objType) }
        onOpLocU(RANGE) { useOnRange(it.objType) }
        onOpLocU(FROZEN_TABLE) { useOnTable(it.objType) }
        onOpLocU(SCALES_CHEST) { useOnScales(it.objType) }
        onOpLocU(MURAL) { useOnMural(it.objType) }

        registerPouring()
        onOpHeldU(STICKY_GOOP, WOODEN_DISK) { colourDisk() }
        onOpHeldU(VASE_LID, VASE) { sealVase(VASE, SEALED_VASE) }
        onOpHeldU(VASE_LID, VASE_OF_WATER) { sealVase(VASE_OF_WATER, SEALED_VASE_OF_WATER) }
        onOpHeldU(VASE_LID, FROZEN_VASE) { mes("The ice prevents you screwing on the lid.") }
        onOpHeldU(FULL_JUG, VASE) { fillVaseFrom(FULL_JUG, EMPTY_JUG) }
        onOpHeldU(FULL_BUCKET, VASE) { fillVaseFrom(FULL_BUCKET, EMPTY_BUCKET) }
        onOpHeldU(MAGNET, VASE) {
            mes("You use the magnet on the vase. The metallic object inside moves.")
            mes("The neck of the vase is too thin for the object to come out of the vase.")
        }
        onOpHeldU(PICK, VASE) { mes("This pick wouldn't be strong enough to break the vase open.") }
        onOpHeldU(PICK, FROZEN_VASE) { mes("This pick wouldn't be strong enough to break the vase open.") }
        onOpHeldU(TOY_SHIP, VASE) { mes("It wouldn't fit in there!") }
        onOpHeldU(PICK, FROZEN_KEY) { mes("Nothing interesting happens.") }
        onOpHeld1(VASE) {
            mesbox(
                "You shake the strangely shaped vase. From the sound of it there is something metallic " +
                    "inside, but the neck of the vase is too narrow for it to come out.",
            )
        }
        onOpHeld1(VASE_OF_WATER) {
            mesbox(
                "You shake the strangely shaped vase. The water inside it sloshes around a little. Some " +
                    "spills out of the neck of the vase.",
            )
        }
        onOpHeld1(FROZEN_VASE) {
            mesbox(
                "You shake the strangely shaped vase. Whatever is at the bottom of it is packed with ice " +
                    "and does not rattle.",
            )
        }
        onOpHeld1(SEALED_VASE) { removeLid(SEALED_VASE, VASE) }
        onOpHeld1(SEALED_VASE_OF_WATER) { removeLid(SEALED_VASE_OF_WATER, VASE_OF_WATER) }
        onOpHeld1(SEALED_FROZEN_VASE) { removeLid(SEALED_FROZEN_VASE, FROZEN_VASE) }
    }

    /* Peer */

    private suspend fun Dialogue.peer() {
        when {
            !quest.isStarted(player) -> outerlanderRebuff()
            quest.hasVote(player, Trial.Seer) && !merchant.isActive(player) -> peerVoted()
            merchant.isActive(player) -> {
                val topic =
                    choice3(
                        "Ask about the Merchant's trial",
                        1,
                        "Ask about becoming a Fremennik",
                        2,
                        "Ask about depositing your equipment",
                        3,
                    )
                when (topic) {
                    1 -> with(merchant) { merchantTalk(MerchantContact.Peer) }
                    2 -> if (quest.hasVote(player, Trial.Seer)) peerVoted() else seersTrial()
                    else -> depositOption()
                }
            }
            player.ftThorvaldStarted -> {
                val deposit =
                    choice2(
                        "Ask about becoming a Fremennik",
                        false,
                        "Ask about depositing your equipment",
                        true,
                    )
                if (deposit) depositOption() else seersTrial()
            }
            else -> seersTrial()
        }
    }

    private suspend fun Dialogue.peerVoted() {
        chatPlayer(quiz, "So you will vote for me at the council?")
        chatNpc(
            happy,
            "Absolutely, outerlander. Your wisdom in passing my test marks you as worthy in my eyes.",
        )
    }

    private suspend fun Dialogue.seersTrial() {
        if (!player.ftPeerStarted) {
            challenge()
            return
        }
        if (carriesAnything(player)) {
            chatPlayer(quiz, "So I can bring nothing with me when I enter your house?")
            chatNpc(
                neutral,
                "That is correct outerlander, but as I say, I can use my small skill in magic to send " +
                    "your items directly into your bank account from here.",
            )
            chatNpc(
                neutral,
                "You will need to manually go to the bank to withdraw them again however. Would you " +
                    "like me to perform this small spell upon you, outerlander?",
            )
            offerDeposit()
            return
        }
        chatPlayer(quiz, "So I just have to enter by one door of your house, and leave by the other?")
        chatNpc(
            neutral,
            "That is correct outerlander. Be warned it is not as easy as it may at first sound...",
        )
    }

    private suspend fun Dialogue.challenge() {
        chatNpc(neutral, "Hello outerlander. What do you want?")
        chatPlayer(
            neutral,
            "Hello. I'm looking for members of the council of elders to vote for me to become a " +
                "Fremennik.",
        )
        chatNpc(
            neutral,
            "Are you now? Well that is interesting. Usually outerlanders do not concern themselves " +
                "with our ways like that.",
        )
        chatNpc(
            neutral,
            "I am one of the members of the council of elders, and should you be able to prove to me " +
                "that you have something to offer my clan,",
        )
        chatNpc(neutral, "I will vote in your favour at the next meeting.")
        chatPlayer(quiz, "How can I prove that to you?")
        chatNpc(
            neutral,
            "Well, I have but a simple test. This building behind me is my house. Inside I have " +
                "constructed a puzzle.",
        )
        chatNpc(
            neutral,
            "As a Seer to the clan, I value intelligence very highly, so you may think of it as an " +
                "intelligence test of sorts.",
        )
        chatPlayer(shifty, "An intelligence test? I thought barbarians were stupid!")
        chatNpc(
            neutral,
            "That is the opinion that outerlanders usually hold of my people, it is true. But that is " +
                "because people often confuse knowledge with wisdom.",
        )
        chatNpc(
            neutral,
            "My puzzle tests not what you know, but what you can work out. All members of our clan " +
                "have been tested when they took their trials.",
        )
        chatPlayer(quiz, "So what exactly does this puzzle consist of, then?")
        chatNpc(
            neutral,
            "Well, firstly you must enter my house with no items, weapons or armour. Then it is a " +
                "simple matter of entering through one door and leaving by the other.",
        )
        chatPlayer(shocked, "I can't take anything in there with me?")
        chatNpc(
            neutral,
            "That is correct outerlander. Everything you need to complete the puzzle you will find " +
                "inside the building. Nothing more.",
        )
        chatNpc(quiz, "So what say you outerlander? You think you have the wit to earn yourself my vote?")
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(
                neutral,
                "No, thinking about stuff isn't really my 'thing'. I'd rather go kill something. I'll " +
                    "find someone else to vote for me.",
            )
            chatNpc(neutral, "As you wish, outerlander.")
            return
        }
        chatPlayer(neutral, "Yes, I accept your challenge. I have one small question, however...")
        player.ftPeerStarted = true
        chatNpc(neutral, "Yes, outerlander?")
        chatPlayer(neutral, "Well... you say I can bring nothing with me when I enter your house...")
        chatNpc(neutral, "Yes, outerlander?")
        chatPlayer(neutral, "Well.....")
        chatNpc(neutral, "Yes, outerlander?")
        chatPlayer(quiz, "Where is the nearest bank?")
        chatNpc(
            neutral,
            "Ah, I see your problem outerlander. The nearest bank to here is the place known to " +
                "outerlanders as the Seers Village. It is some way South.",
        )
        chatNpc(neutral, "I do however have an alternative, should you wish to take it.")
        chatPlayer(quiz, "And what is that?")
        chatNpc(
            neutral,
            "I can store all the weapons, armour and items that you have upon you directly into your " +
                "bank account.",
        )
        chatNpc(
            neutral,
            "This will tax what little magic I possess however, so you will have to manually travel to " +
                "the bank to withdraw them again.",
        )
        chatNpc(quiz, "What say you outerlander? you wish me to do this for you?")
        offerDeposit()
    }

    private suspend fun Dialogue.offerDeposit() {
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(angry, "No thanks. Nobody touches my stuff but me!")
            chatNpc(neutral, "As you wish, outerlander.")
            chatNpc(
                neutral,
                "You may attempt my little task when you have deposited your equipment in the bank.",
            )
            return
        }
        chatPlayer(happy, "Yes, thank you!")
        depositEverything()
    }

    private suspend fun Dialogue.depositOption() {
        if (!quest.isStarted(player)) {
            outerlanderRebuff()
            return
        }
        chatPlayer(
            quiz,
            "Hello there! Thorvald tells me you can deposit my equipment directly into my bank account " +
                "for me...?",
        )
        chatNpc(
            neutral,
            "That is correct outerlander, but you will need to go and retrieve your equipment from " +
                "your bank account yourself, manually.",
        )
        chatNpc(
            neutral,
            "Alas, my spell is not very accurate, so it will deposit everything you have upon you into " +
                "your bank, not just your armour and weapons.",
        )
        if (!choice2("Bank your equipment", true, "Don't bank your equipment", false)) {
            chatPlayer(neutral, "No thanks, I'll just go to the bank and sort my inventory out myself.")
            return
        }
        chatPlayer(neutral, "Yes, please deposit my equipment.")
        depositEverything()
    }

    private suspend fun Dialogue.depositEverything() {
        val previous = access.selectedTab
        if (previous != BankTab.Main) {
            access.selectedTab = BankTab.Main
        }
        try {
            with(bankInv) {
                if (!player.inv.isEmpty()) access.depositInv()
                if (!player.worn.isEmpty()) access.depositWorn()
            }
        } finally {
            if (previous != BankTab.Main) {
                access.selectedTab = previous
            }
        }
        if (carriesAnything(player)) {
            chatNpc(sad, "I am sorry outerlander, the spell is not working.")
            return
        }
        chatNpc(neutral, "The task is done. I wish you luck with your test, outerlander.")
    }

    private fun carriesAnything(player: Player): Boolean = !player.inv.isEmpty() || !player.worn.isEmpty()

    /* The doors and the combination lock */

    private fun isRunning(player: Player): Boolean =
        quest.isInProgress(player) && player.ftPeerStarted && !player.voted(Trial.Seer)

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val inside = coords.z < door.coords.z
        if (inside) {
            leaveByFrontDoor(door, type)
            return
        }
        if (!isRunning(player)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (carriesAnything(player)) {
            startDialogue {
                chatNpcSpecific(
                    "Peer the Seer",
                    PEER,
                    neutral,
                    "You may not take anything into my house, outerlander! Speak to me, and I will send " +
                        "your belongings to your bank.",
                )
            }
            return
        }
        if (player.ftPeerDoorSolved) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (player.ftPeerRiddle == 0) {
            player.ftPeerRiddle = random.of(1, RIDDLES.size)
        }
        startDialogue {
            mesbox(
                "There is a combination lock on this door. Above the lock you can see that there is a " +
                    "metal plaque with a riddle on it.",
            )
            val action =
                choice3(
                    "Read the riddle",
                    1,
                    "Solve the riddle",
                    2,
                    "Forget it",
                    3,
                    title = "What would you like to do?",
                )
            when (action) {
                1 -> {
                    val riddle = RIDDLES[player.ftPeerRiddle - 1]
                    mesbox(riddle.clues)
                    mesbox(riddle.whole)
                }
                2 -> access.openLock()
                else -> access.mes("You leave the riddle for later.")
            }
        }
    }

    private suspend fun ProtectedAccess.leaveByFrontDoor(door: BoundLocInfo, type: ObjectServerType) {
        if (isRunning(player)) {
            mesbox(
                "If you leave by this door you will fail this trial. Press continue to go through the " +
                    "door anyway.",
            )
            clearPuzzle()
        }
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.backDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        if (!isRunning(player)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        val inside = coords.z < door.coords.z
        if (!inside || SEERS_KEY !in player.inv) {
            mes("This door is locked tightly shut.")
            return
        }
        soundSynth(UNLOCK_SOUND)
        mes("You unlock the door with your key.")
        with(passages) { walkThrough(door, type) }
        clearPuzzle()
        if (!isRunning(player)) {
            return
        }
        startDialogue {
            chatNpcSpecific(
                "Peer the Seer",
                PEER,
                shocked,
                "Incredible! To have solved my puzzle so quickly! I have no choice but to vote in your " +
                    "favour!",
            )
        }
        quest.grantVote(this, Trial.Seer)
    }

    private fun ProtectedAccess.openLock() {
        for (wheel in 0 until WHEELS) {
            setWheel(wheel, 0)
        }
        ifOpenMainModal(LOCK_INTERFACE)
        showWheels()
        for (button in LOCK_BUTTONS) {
            ifSetEvents(button.component, 0..0, IfEvent.Op1)
        }
        ifSetEvents(LOCK_ENTER, 0..0, IfEvent.Op1)
    }

    private fun ProtectedAccess.showWheels() {
        for (wheel in 0 until WHEELS) {
            ifSetText(LOCK_LETTERS[wheel], ('A' + wheelOf(wheel)).toString())
        }
    }

    private fun ProtectedAccess.wheelOf(wheel: Int): Int = player.vars[LOCK_VARBITS[wheel]]

    private fun ProtectedAccess.setWheel(wheel: Int, letter: Int) {
        VarPlayerIntMapSetter.set(player, LOCK_VARBITS[wheel], letter)
    }

    private fun ProtectedAccess.turnWheel(wheel: Int, step: Int) {
        setWheel(wheel, Math.floorMod(wheelOf(wheel) + step, ALPHABET))
        showWheels()
    }

    private fun ProtectedAccess.tryCombination() {
        val word = (0 until WHEELS).map { 'A' + wheelOf(it) }.joinToString("")
        ifClose()
        val riddle = RIDDLES.getOrNull(player.ftPeerRiddle - 1)
        if (riddle == null || word != riddle.answer) {
            mes("You have failed to solve the riddle.")
            return
        }
        soundSynth(UNLOCK_SOUND)
        player.ftPeerDoorSolved = true
        mes("You have solved the riddle!")
    }

    /* Moving around the house */

    private suspend fun ProtectedAccess.climb(landing: CoordGrid) {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(1)
        telejump(landing, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.trapdoorDown(trapdoor: BoundLocInfo) {
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        val landing = if (trapdoor.coords.x <= WEST_TRAPDOOR_X) DOWNSTAIRS_WEST else DOWNSTAIRS_EAST
        telejump(landing, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.openTrapdoor(trapdoor: BoundLocInfo) {
        arriveDelay()
        anim(PICKUP_SEQ)
        soundSynth(TRAPDOOR_OPEN_SOUND)
        locRepo.change(trapdoor, TRAPDOOR_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.closeTrapdoor(trapdoor: BoundLocInfo) {
        arriveDelay()
        if (trapdoor.coords.x <= WEST_TRAPDOOR_X) {
            mes("Nothing interesting happens.")
            return
        }
        soundSynth(TRAPDOOR_CLOSE_SOUND)
        locRepo.change(trapdoor, TRAPDOOR_CLOSED, OPEN_TICKS)
    }

    /* Searching the upstairs room */

    private suspend fun ProtectedAccess.searchBookcase(bookcase: BoundLocInfo) {
        arriveDelay()
        mes("You search the bookcase...")
        if (bookcase.coords == HERRING_BOOKCASE && findOnce(PuzzleFind.Herring, RED_HERRING)) {
            mes("Hidden behind some old books, you find a red herring.")
            return
        }
        mes("You find nothing of interest.")
    }

    private suspend fun ProtectedAccess.unicornHead() {
        arriveDelay()
        mesbox("You notice there is something unusual about the left eye of this unicorn head...")
        if (findOnce(PuzzleFind.UnicornDisk, OLD_RED_DISK)) {
            mesbox("It is not an eye at all, but some kind of red coloured disk. You take it from the head.")
            return
        }
        mes("It's unusual because you've removed it already!")
    }

    private suspend fun ProtectedAccess.bullHead() {
        arriveDelay()
        mesbox("You notice there is something unusual about the right eye of this bulls' head...")
        if (findOnce(PuzzleFind.BullDisk, WOODEN_DISK)) {
            mesbox("It is not an eye at all, but some kind of disk made of wood. You take it from the head.")
            return
        }
        mes("It's unusual because you've already taken it!")
    }

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        soundSynth(CUPBOARD_OPEN_SOUND)
        locRepo.change(cupboard, CUPBOARD_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.shutCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        soundSynth(CUPBOARD_CLOSE_SOUND)
        locRepo.change(cupboard, CUPBOARD, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        mes("You search the cupboard...")
        if (findOnce(PuzzleFind.Bucket, EMPTY_BUCKET)) {
            mes("You find a bucket with a number five painted on it.")
            return
        }
        mes("You find nothing of interest.")
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        soundSynth(CHEST_OPEN_SOUND)
        locRepo.change(chest, CHEST_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        arriveDelay()
        soundSynth(CHEST_CLOSE_SOUND)
        locRepo.change(chest, CHEST, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.searchChest(chest: BoundLocInfo) {
        arriveDelay()
        mes("You search the chest...")
        if (chest.coords == JUG_CHEST && findOnce(PuzzleFind.Jug, EMPTY_JUG)) {
            mes("You find a jug with a number three painted on it.")
            return
        }
        mes("You find nothing of interest.")
    }

    private suspend fun ProtectedAccess.searchBoxes(
        boxes: BoundLocInfo,
        obj: String,
        holder: CoordGrid,
        found: String,
    ) {
        arriveDelay()
        mes("You search the boxes...")
        val find = PuzzleFind.entries.first { it.obj == obj }
        if (boxes.coords == holder && findOnce(find, obj)) {
            mes("You find $found hidden inside.")
            return
        }
        mes("You find nothing of interest.")
    }

    /** Hands over [obj] the first time the player finds it; false once it has been taken. */
    private fun ProtectedAccess.findOnce(find: PuzzleFind, obj: String): Boolean {
        if (player.vars[find.varbit] != 0) {
            return false
        }
        if (player.inv.isFull()) {
            mes("You don't have enough inventory space.")
            return false
        }
        VarPlayerIntMapSetter.set(player, find.varbit, 1)
        invAdd(inv, obj)
        return true
    }

    /* The water puzzle */

    private fun ScriptContext.registerPouring() {
        for (bucket in BUCKETS.indices) {
            for (jug in JUGS.indices) {
                onOpHeldU(BUCKETS[bucket], JUGS[jug]) { pour(bucketPoursIntoJug(bucket, jug), bucket, jug) }
            }
        }
    }

    /**
     * The engine does not say which of the two was used on the other, so water goes the only way
     * it can: an empty jug or full bucket is poured into, a full jug or empty bucket poured out of,
     * and two part-filled containers empty the jug into the bucket.
     */
    private fun bucketPoursIntoJug(bucket: Int, jug: Int): Boolean =
        when {
            jug == JUG_LITRES || bucket == 0 -> false
            jug == 0 || bucket == BUCKET_LITRES -> true
            else -> false
        }

    private fun ProtectedAccess.pour(fromBucket: Boolean, bucket: Int, jug: Int) {
        val source = if (fromBucket) bucket else jug
        val room = if (fromBucket) JUG_LITRES - jug else BUCKET_LITRES - bucket
        val poured = minOf(source, room)
        if (poured == 0) {
            mes("Nothing interesting happens.")
            return
        }
        val newBucket = if (fromBucket) bucket - poured else bucket + poured
        val newJug = if (fromBucket) jug + poured else jug - poured
        invReplace(inv, BUCKETS[bucket], 1, BUCKETS[newBucket])
        invReplace(inv, JUGS[jug], 1, JUGS[newJug])
        soundSynth(POUR_SOUND)
        mes(
            when {
                fromBucket && newJug == JUG_LITRES -> "You fill the jug to the brim."
                fromBucket -> "You empty the bucket into the jug."
                newBucket == BUCKET_LITRES -> "You fill the bucket to the brim."
                else -> "You empty the jug into the bucket."
            },
        )
    }

    private suspend fun ProtectedAccess.useOnTap(obj: ItemServerType) {
        arriveDelay()
        when (obj.id) {
            EMPTY_BUCKET.id() -> fillFromTap(EMPTY_BUCKET, FULL_BUCKET, "You fill the bucket from the tap.")
            EMPTY_JUG.id() -> fillFromTap(EMPTY_JUG, FULL_JUG, "You fill the jug from the tap.")
            VASE.id() -> fillFromTap(VASE, VASE_OF_WATER, "You fill the strange looking vase with water.")
            MAGNET.id() -> mes("You use the magnet on the tap. It gets a bit wet.")
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.fillFromTap(empty: String, full: String, message: String) {
        soundSynth(POUR_SOUND)
        invReplace(inv, empty, 1, full)
        mes(message)
    }

    private suspend fun ProtectedAccess.useOnDrain(obj: ItemServerType) {
        arriveDelay()
        when {
            obj.id == VASE_OF_WATER.id() -> {
                invReplace(inv, VASE_OF_WATER, 1, VASE)
                mes("You empty the strange looking vase of water.")
            }
            obj.id == VASE.id() -> mes("The vase is already empty.")
            BUCKETS.drop(1).any { it.id() == obj.id } -> {
                invReplace(inv, obj.internalName, 1, EMPTY_BUCKET)
                soundSynth(POUR_SOUND)
                mes("You empty the bucket down the drain.")
            }
            JUGS.drop(1).any { it.id() == obj.id } -> {
                invReplace(inv, obj.internalName, 1, EMPTY_JUG)
                soundSynth(POUR_SOUND)
                mes("You empty the jug down the drain.")
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.useOnRange(obj: ItemServerType) {
        arriveDelay()
        when (obj.id) {
            RED_HERRING.id() -> {
                anim(COOK_SEQ)
                soundSynth(FRY_SOUND)
                delay(2)
                invReplace(inv, RED_HERRING, 1, HERRING)
                invAdd(inv, STICKY_GOOP)
                mesbox(
                    "As you cook the herring on the stove, the colouring on it peels off separately as a " +
                        "red sticky goop...",
                )
            }
            FROZEN_KEY.id() -> {
                anim(COOK_SEQ)
                delay(2)
                invReplace(inv, FROZEN_KEY, 1, SEERS_KEY)
                mes("The heat of the range melts the ice around the key.")
            }
            FROZEN_JUG.id() -> thaw(FROZEN_JUG, EMPTY_JUG, "jug")
            FROZEN_BUCKET.id() -> thaw(FROZEN_BUCKET, EMPTY_BUCKET, "bucket")
            FROZEN_VASE.id() -> thaw(FROZEN_VASE, VASE, "vase")
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.thaw(frozen: String, empty: String, name: String) {
        invReplace(inv, frozen, 1, empty)
        mes("You place the frozen $name on the range. The ice turns to steam.")
    }

    private suspend fun ProtectedAccess.useOnTable(obj: ItemServerType) {
        arriveDelay()
        when {
            obj.id == SEALED_VASE_OF_WATER.id() -> {
                invReplace(inv, SEALED_VASE_OF_WATER, 1, FROZEN_KEY)
                soundSynth(SHATTER_SOUND)
                mes("The water expands as it freezes, and shatters the vase.")
                mes("You are left with a key encased in ice.")
            }
            obj.id == VASE_OF_WATER.id() -> {
                invReplace(inv, VASE_OF_WATER, 1, FROZEN_VASE)
                mes("The icy table immediately freezes the water in your vase.")
            }
            obj.id == VASE.id() -> mes("Your empty vase gets very cold on the icy table.")
            obj.id == EMPTY_JUG.id() -> mes("Your empty jug gets very cold on the icy table.")
            obj.id == EMPTY_BUCKET.id() -> mes("Your empty bucket gets very cold on the icy table.")
            BUCKETS.drop(1).any { it.id() == obj.id } -> {
                invReplace(inv, obj.internalName, 1, FROZEN_BUCKET)
                mes("The icy table immediately freezes the water in your bucket.")
            }
            JUGS.drop(1).any { it.id() == obj.id } -> {
                invReplace(inv, obj.internalName, 1, FROZEN_JUG)
                mes("The icy table immediately freezes the water in your jug.")
            }
            obj.id == MAGNET.id() -> mes("You use the magnet on the table. It gets slightly colder.")
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.useOnScales(obj: ItemServerType) {
        arriveDelay()
        when {
            obj.id == BUCKETS[FOUR_LITRES].id() -> {
                mes("You place the bucket on the scale.")
                if (!findOnce(PuzzleFind.Vase, VASE)) {
                    mes("The chest is empty.")
                    return
                }
                soundSynth(CHEST_OPEN_SOUND)
                mes("It is a perfect counterweight and balances precisely.")
                mes("You take a strange looking vase out of the chest.")
            }
            obj.id == FULL_BUCKET.id() -> {
                mes("You place the bucket on the scale.")
                mes("It is too heavy to balance it properly.")
            }
            BUCKETS.any { it.id() == obj.id } -> {
                mes("You place the bucket on the scale.")
                mes("It is too light to balance it properly.")
            }
            JUGS.any { it.id() == obj.id } -> {
                mes("You place the jug on the scale.")
                mes("It is too light to balance it properly.")
            }
            obj.id == MAGNET.id() -> mes("You use the magnet on the chest. The scale wobbles a bit.")
            obj.id == PICK.id() -> {
                mes("You might be able to break the chest open using this pick...")
                mes("...if you stood here tapping at it for twenty years or so.")
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    /* The mural and the vase */

    private suspend fun ProtectedAccess.useOnMural(obj: ItemServerType) {
        arriveDelay()
        when (obj.id) {
            WOODEN_DISK.id() -> {
                mes("You put the wooden disk into the empty hole in the mural.")
                mes("It is slightly too small, and falls back out.")
            }
            OLD_RED_DISK.id() -> placeDisk(OLD_RED_DISK, "varbit.fremtrials_peer_mural_old")
            RED_DISK.id() -> placeDisk(RED_DISK, "varbit.fremtrials_peer_mural_new")
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.placeDisk(disk: String, varbit: String) {
        invDel(inv, disk)
        VarPlayerIntMapSetter.set(player, varbit, 1)
        mes("You put the red disk into the empty hole on the mural.")
        mes("It is a perfect fit!")
        if (player.ftMuralOld && player.ftMuralNew) {
            mes("The centre of the mural falls out!")
            invAdd(inv, VASE_LID)
        }
    }

    private fun ProtectedAccess.colourDisk() {
        invDel(inv, STICKY_GOOP)
        invReplace(inv, WOODEN_DISK, 1, RED_DISK)
        mes("You coat the wooden coin with the sticky red goop.")
    }

    private fun ProtectedAccess.sealVase(from: String, into: String) {
        invDel(inv, VASE_LID)
        invReplace(inv, from, 1, into)
        mes("You screw the lid on tightly.")
    }

    private fun ProtectedAccess.removeLid(sealed: String, into: String) {
        invReplace(inv, sealed, 1, into)
        invAdd(inv, VASE_LID)
        mes("You unscrew the lid from the vase.")
    }

    private fun ProtectedAccess.fillVaseFrom(container: String, empty: String) {
        invReplace(inv, VASE, 1, VASE_OF_WATER)
        invReplace(inv, container, 1, empty)
        soundSynth(POUR_SOUND)
        mes("You fill the strange looking vase with water.")
    }

    /** Everything found in the house stays in the house, and the searches start over. */
    private fun ProtectedAccess.clearPuzzle() {
        for (obj in HOUSE_ITEMS) {
            val count = inv.count(obj)
            if (count > 0) {
                invDel(inv, obj, count)
            }
        }
        for (find in PuzzleFind.entries) {
            VarPlayerIntMapSetter.set(player, find.varbit, 0)
        }
        player.ftMuralOld = false
        player.ftMuralNew = false
    }

    private fun String.id(): Int = asRSCM(RSCMType.OBJ)

    private class Riddle(val clues: String, val whole: String, val answer: String)

    private class LockButton(val component: String, val wheel: Int, val step: Int)

    private enum class PuzzleFind(val varbit: String, val obj: String) {
        Jug("varbit.fremtrials_peer_jug_found", EMPTY_JUG),
        Bucket("varbit.fremtrials_peer_bucket_found", EMPTY_BUCKET),
        BullDisk("varbit.fremtrials_peer_bull_disk", WOODEN_DISK),
        UnicornDisk("varbit.fremtrials_peer_unicorn_disk", OLD_RED_DISK),
        Herring("varbit.fremtrials_peer_herring_found", RED_HERRING),
        Magnet("varbit.fremtrials_peer_magnet_found", MAGNET),
        Ship("varbit.fremtrials_peer_ship_found", TOY_SHIP),
        Pick("varbit.fremtrials_peer_pick_found", PICK),
        Thread("varbit.fremtrials_peer_thread_found", THREAD),
        Vase("varbit.fremtrials_peer_vase_found", VASE),
    }

    private companion object {
        const val FRONT_DOOR = "loc.viking_seers_door1"
        const val BACK_DOOR = "loc.viking_seers_door2"
        const val ENTRANCE_LADDER = "loc.viking_seer_up_ladder"
        const val EXIT_LADDER = "loc.viking_seer_down_ladder"
        const val TRAPDOOR_OPEN = "loc.viking_seer_trapdoor_open"
        const val TRAPDOOR_CLOSED = "loc.viking_seer_trapdoor_closed"
        const val BOOKCASE = "loc.viking_seer_bookcase"
        const val UNICORN_HEAD = "loc.viking_unimountedhead"
        const val BULL_HEAD = "loc.viking_bullmountedhead"
        const val CUPBOARD = "loc.viking_cupboardhigh"
        const val CUPBOARD_OPEN = "loc.viking_cupboardopen_high"
        const val CHEST = "loc.viking_seer_chest_closed"
        const val CHEST_OPEN = "loc.viking_seer_chest_open"
        const val SCALES_CHEST = "loc.viking_seer_chest_closed_scales"
        const val BOXES = "loc.viking_seerboxes_1"
        const val EAST_BOXES = "loc.viking_seerboxes_3"
        const val CRATE = "loc.viking_seercrate"
        const val CRATE_SOUTH_EAST = "loc.viking_seercrate_2"
        const val MURAL = "loc.viking_seers_mural"
        const val TAP = "loc.viking_seers_tap"
        const val DRAIN = "loc.viking_seers_drain"
        const val RANGE = "loc.viking_seer_range"
        const val FROZEN_TABLE = "loc.viking_small_table_frozen"

        const val LOCK_INTERFACE = "interface.seer_combolock"
        const val LOCK_ENTER = "component.seer_combolock:seerenter"
        val LOCK_LETTERS =
            listOf(
                "component.seer_combolock:seera",
                "component.seer_combolock:seerb",
                "component.seer_combolock:seerc",
                "component.seer_combolock:seerd",
            )
        val LOCK_VARBITS =
            listOf(
                "varbit.fremtrials_lock_a",
                "varbit.fremtrials_lock_b",
                "varbit.fremtrials_lock_c",
                "varbit.fremtrials_lock_d",
            )
        val LOCK_BUTTONS =
            listOf("a", "b", "c", "d").flatMapIndexed { wheel, letter ->
                listOf(
                    LockButton("component.seer_combolock:seer${letter}_left", wheel, -1),
                    LockButton("component.seer_combolock:seer${letter}_right", wheel, 1),
                )
            }
        const val WHEELS = 4
        const val ALPHABET = 26

        val RIDDLES =
            listOf(
                Riddle(
                    "My first is in the well, but not at sea.<br>My second in 'I', but not in 'me'.<br>" +
                        "My third is in flies, but insects not found.<br>My last is in earth, but not in " +
                        "the ground.",
                    "My whole when stolen from you, causes you death.<br>What am I?",
                    "LIFE",
                ),
                Riddle(
                    "My first is in water, and also in tea.<br>My second in fish, but not in the sea." +
                        "<br>My third in mountains, but not underground.<br>My last is in strike, but not " +
                        "in pound.",
                    "My whole crushes mountains, drains rivers, and destroys civilisations.<br>All that " +
                        "live fear my passing.<br>What am I?",
                    "TIME",
                ),
                Riddle(
                    "My first is in mage, but not in wizard.<br>My second in goblin, and also in lizard." +
                        "<br>My third in night, but not in the day.<br>My last is in fields, but not in " +
                        "the hay.",
                    "My whole is the most powerful tool you will possess.<br>What am I?",
                    "MIND",
                ),
                Riddle(
                    "My first is in tar, but not in a swamp.<br>My second in fire, but not in a camp." +
                        "<br>My third is in eagle, but never in air.<br>My last is in hate, but also in " +
                        "care.",
                    "My whole wears rings the older I get.<br>What am I?",
                    "TREE",
                ),
                Riddle(
                    "My first is in fish, but not in the sea.<br>My second in birds, but not in a tree." +
                        "<br>My third is in doors, but not in a hall.<br>My last is everything, but " +
                        "nothing at all.",
                    "My whole cannot die as long as it has food.<br>What am I?",
                    "FIRE",
                ),
                Riddle(
                    "My first is in wizard, but not in a mage.<br>My second in jail, but not in a cage." +
                        "<br>My third is in anger, but not in a rage.<br>My last in a drawing, but not on " +
                        "a page.",
                    "My whole helps to make bread, let birds fly and boats sail.<br>What am I?",
                    "WIND",
                ),
            )

        const val EMPTY_BUCKET = "obj.viking_bucket_empty"
        const val FULL_BUCKET = "obj.viking_bucket_5"
        const val FROZEN_BUCKET = "obj.viking_bucket_frozen"
        const val EMPTY_JUG = "obj.viking_jug_empty"
        const val FULL_JUG = "obj.viking_jug_3"
        const val FROZEN_JUG = "obj.viking_jug_frozen"
        const val BUCKET_LITRES = 5
        const val JUG_LITRES = 3
        const val FOUR_LITRES = 4

        /** Buckets and jugs by how many litres they hold. */
        val BUCKETS =
            listOf(
                EMPTY_BUCKET,
                "obj.viking_bucket_1",
                "obj.viking_bucket_2",
                "obj.viking_bucket_3",
                "obj.viking_bucket_4",
                FULL_BUCKET,
            )
        val JUGS = listOf(EMPTY_JUG, "obj.viking_jug_1", "obj.viking_jug_2", FULL_JUG)

        const val VASE = "obj.viking_airtight_vase"
        const val VASE_OF_WATER = "obj.viking_airtight_vase_water"
        const val FROZEN_VASE = "obj.viking_airtight_vase_frozen"
        const val VASE_LID = "obj.viking_vase_lid"
        const val SEALED_VASE = "obj.viking_airtight_vase_with_lid"
        const val SEALED_FROZEN_VASE = "obj.viking_airtight_vase_with_lid_frozen"
        const val SEALED_VASE_OF_WATER = "obj.viking_airtight_vase_with_lid_water"
        const val FROZEN_KEY = "obj.viking_key_in_ice"
        const val SEERS_KEY = "obj.viking_key"
        const val RED_HERRING = "obj.viking_red_herring"
        const val HERRING = "obj.herring"
        const val STICKY_GOOP = "obj.viking_red_splat"
        const val WOODEN_DISK = "obj.viking_uncoloured_wooden_coin"
        const val RED_DISK = "obj.viking_red_wooden_coin"
        const val OLD_RED_DISK = "obj.viking_red_wooden_coin_old"
        const val MAGNET = "obj.viking_pointless_magnet"
        const val THREAD = "obj.viking_pointless_thread"
        const val PICK = "obj.viking_pointless_pick"
        const val TOY_SHIP = "obj.viking_pointless_shiptoy"

        val HOUSE_ITEMS =
            BUCKETS + JUGS +
                listOf(
                    FROZEN_BUCKET,
                    FROZEN_JUG,
                    VASE,
                    VASE_OF_WATER,
                    FROZEN_VASE,
                    VASE_LID,
                    SEALED_VASE,
                    SEALED_FROZEN_VASE,
                    SEALED_VASE_OF_WATER,
                    FROZEN_KEY,
                    SEERS_KEY,
                    RED_HERRING,
                    STICKY_GOOP,
                    WOODEN_DISK,
                    RED_DISK,
                    OLD_RED_DISK,
                    MAGNET,
                    THREAD,
                    PICK,
                    TOY_SHIP,
                )

        val UPSTAIRS_WEST = CoordGrid(2632, 3663, 2)
        val UPSTAIRS_EAST = CoordGrid(2637, 3663, 2)
        val DOWNSTAIRS_WEST = CoordGrid(2632, 3663, 0)
        val DOWNSTAIRS_EAST = CoordGrid(2637, 3663, 0)
        const val WEST_TRAPDOOR_X = 2631

        val HERRING_BOOKCASE = CoordGrid(2634, 3665, 2)
        val JUG_CHEST = CoordGrid(2638, 3662, 2)
        val SW_BOXES = CoordGrid(2633, 3660, 2)
        val EAST_CRATE = CoordGrid(2638, 3661, 2)

        /** How long an opened cupboard, chest or trapdoor stays open before it swings shut. */
        const val OPEN_TICKS = 100

        const val CLIMB_UP_SEQ = "seq.human_climbing"
        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val PICKUP_SEQ = "seq.human_pickupfloor"
        const val COOK_SEQ = "seq.human_cooking"

        const val UNLOCK_SOUND = "synth.unlock"
        const val TRAPDOOR_OPEN_SOUND = "synth.trapdoor_open"
        const val TRAPDOOR_CLOSE_SOUND = "synth.trapdoor_close"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
        const val CHEST_OPEN_SOUND = "synth.chest_open"
        const val CHEST_CLOSE_SOUND = "synth.chest_close"
        const val POUR_SOUND = "synth.liquid"
        const val FRY_SOUND = "synth.fry"
        const val SHATTER_SOUND = "synth.glass_chink_1"
    }
}

private var Player.ftMuralOld by boolVarBit("varbit.fremtrials_peer_mural_old")
private var Player.ftMuralNew by boolVarBit("varbit.fremtrials_peer_mural_new")
