package org.rsmod.content.quest.area.desert.icthlarin

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.PYRAMID_TRAPS_TIMER
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_CEREMONY_COMPLETE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_CEREMONY_STARTED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FIRST_FLASHBACK
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FIRST_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FREED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_GUARDIAN_DEFEATED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_GUARDIAN_SUMMONED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_JAR_RETURNED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_JAR_ROOM_OPEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PRIEST_DEFEATED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PRIEST_POSSESSED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_RETURN_JAR
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SECOND_FLASHBACK
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SECOND_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SYMBOL_HIDDEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_THIRD_FLASHBACK
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_THIRD_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_WOKE_IN_SOPHANEM
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.UNHOLY_SYMBOL
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Klenter's pyramid in Sophanem. Only a cat can open its door. Inside, the way to the burial
 * chambers runs past two sets of wall crushers and a hallway whose floor hides pit traps, then
 * across a great pit that takes a running jump. North of the pit a door puzzle guards the western
 * chamber, where the canopic jars are kept, and the eastern chamber holds the ceremonial table.
 *
 * The three flashbacks start here - touching the door, and jumping the pit on the way to the jar
 * and to the ceremony - and play out in the pyramid itself. Logging out inside puts the player
 * back outside the door, as in the original.
 */
class KlentersPyramid
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val cats: IcthlarinCats,
    private val flashbacks: Flashbacks,
    private val fights: PyramidFights,
    private val ceremony: Ceremony,
    private val puzzle: TilePuzzle,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val crusherType: ObjectServerType by lazy { locType(CRUSHER) }
    private val secretDoorType: ObjectServerType by lazy { locType(SECRET_DOOR) }

    override fun ScriptContext.startup() {
        onOpLoc1(PYRAMID_DOOR) { openPyramidDoor(it.loc) }
        onOpLoc1(LADDER) { climbOut() }
        onOpLoc2(PIT_SOUTH_EDGE) { jumpNorth(it.loc) }
        onOpLoc2(PIT_NORTH_EDGE) { jumpSouth(it.loc) }
        onOpLoc1(WEST_DOOR) { westDoor(it.loc, it.type) }
        onOpLoc1(EAST_DOOR) { eastDoor(it.loc, it.type) }
        for (jar in CanopicJar.entries) {
            onOpLoc1(jar.shelf + MULTI_SUFFIX) { takeJar(jar) }
            onOpHeld5(jar.obj) { dropJar(jar, it.slot) }
        }
        onOpLoc1(CHEST_CLOSED) { useChest(it.loc, CHEST_OPEN) }
        onOpLoc2(CHEST_OPEN) { mes("You search the chest but find nothing of interest.") }
        onOpLoc3(CHEST_OPEN) { useChest(it.loc, CHEST_CLOSED) }
        onOpLoc1(SARCOPHAGUS) { searchSarcophagus() }
        onOpLocU(SARCOPHAGUS, UNHOLY_SYMBOL) { hideSymbol() }
        onOpLocU(CEREMONIAL_TABLE, UNHOLY_SYMBOL) {
            if (quest.stage(player) == STAGE_THIRD_FLASHBACK) {
                startDialogue { chatPlayer(neutral, "No... It must be somewhere more secret...") }
            } else {
                mes("Nothing interesting happens.")
            }
        }
        for (tile in 1..IcthlarinsLittleHelperQuest.TILE_COUNT) {
            onIfModalButton(TilePuzzle.tileComponent(tile)) { pressTile(tile) }
        }
        onIfModalButton(TilePuzzle.RESET_COMPONENT) { puzzle.reset(this) }
        onPlayerSoftTimer(PYRAMID_TRAPS_TIMER) { trapsTick(player) }
        onPlayerSoftQueue(PIT_TRAP_QUEUE) { landFromTrap(player) }
        onPlayerSoftQueue(FLY_OUT_QUEUE) { flyOut(player) }
        onPlayerLogin {
            if (SophanemCoords.inPyramid(player.coords)) {
                player.softQueue(LOGIN_EVICT_QUEUE, 1)
            }
        }
        onPlayerSoftQueue(LOGIN_EVICT_QUEUE) { thrownOut(player) }
        onPlayerLogout {
            fights.cleanup(player)
            flashbacks.end(player)
        }
    }

    /* The cat door */

    private suspend fun ProtectedAccess.openPyramidDoor(door: BoundLocInfo) {
        val stage = quest.stage(player)
        when {
            stage == STAGE_WOKE_IN_SOPHANEM || stage == STAGE_FIRST_FLASHBACK -> firstFlashback(door)
            stage < STAGE_RETURN_JAR -> startDialogue { chatPlayer(sad, "Drat! It's locked.") }
            stage >= STAGE_FREED ->
                startDialogue {
                    chatPlayer(
                        neutral,
                        "I think it would be best if I stayed out of this tomb after what happened last time.",
                    )
                }
            else -> catOpensDoor(door, stage)
        }
    }

    private suspend fun ProtectedAccess.firstFlashback(door: BoundLocInfo) {
        startDialogue { mesbox("As you touch the pyramid door, a sense of deja-vu sweeps over you...") }
        ifClose()
        quest.advanceTo(this, STAGE_FIRST_FLASHBACK)
        fadeToBlack()
        locAnim(worldRepo, door, DOOR_OPEN_SEQ)
        flashbacks.begin(player, Flashback.BreakIn)
        enterPyramid(SophanemCoords.PYRAMID_LANDING)
        fadeBackIn()
        startDialogue { chatPlayer(neutral, "I must go to the heart of the pyramid... The mistress commands it...") }
    }

    private suspend fun ProtectedAccess.catOpensDoor(door: BoundLocInfo, stage: Int) {
        val cat = cats.anyPet(player)
        if (cat == null) {
            startDialogue {
                chatPlayer(quiz, "I think the High Priest mentioned something about needing a cat to enter the pyramid.")
            }
            return
        }
        startDialogue {
            val jar = quest.jar(player)
            if (stage < STAGE_JAR_RETURNED && jar != null && jar.obj !in player.inv && jar.obj !in access.bank) {
                access.invAdd(access.inv, jar.obj)
                objbox(jar.obj, "You notice an oddly familiar decorative jar lying nearby and pick it up.")
            }
            if (stage == STAGE_RETURN_JAR) {
                chatPlayer(happy, "Hey puss! What do you reckon about that cat on the door? Do you think you can open it?")
            } else {
                chatPlayer(happy, "Hey puss! Think you can open this door again?")
            }
            access.soundSynth(MEEOOW)
            chatNpcSpecific("Cat", cat.npc, happy, "Meeeow.")
        }
        ifClose()
        locAnim(worldRepo, door, DOOR_OPEN_SEQ)
        soundSynth(DOOR_SOUND)
        delay(1)
        when (stage) {
            in STAGE_SECOND_FLASHBACK until STAGE_SECOND_FLASHBACK_DONE -> {
                flashbacks.begin(player, Flashback.TheftOfTheJar)
                enterPyramid(SophanemCoords.WEST_DOOR_OUTSIDE)
                startDialogue { chatPlayer(neutral, "I need to get the burial jar for the mistress...") }
            }
            STAGE_THIRD_FLASHBACK, STAGE_SYMBOL_HIDDEN -> {
                flashbacks.begin(player, Flashback.HidingTheSymbol)
                enterPyramid(SophanemCoords.PIT_NORTH)
                startDialogue { chatPlayer(neutral, "I need to hide the mistress's symbol in the ceremonial room...") }
            }
            else -> enterPyramid(SophanemCoords.PYRAMID_LANDING)
        }
    }

    private fun ProtectedAccess.enterPyramid(dest: CoordGrid) {
        telejump(dest, TeleportType.Exempt)
        softTimer(PYRAMID_TRAPS_TIMER, 1)
    }

    /* Leaving */

    private suspend fun ProtectedAccess.climbOut() {
        if (flashbacks.active(player) != null) {
            val leave =
                startDialogueResult {
                    chatPlayer(neutral, "I must get to the heart of the pyramid...")
                    choice2("Yes.", true, "No.", false, title = "Do you want to leave?")
                }
            if (!leave) {
                return
            }
            ifClose()
            fights.cleanup(player)
            flashbacks.end(player)
        }
        if (quest.stage(player) == STAGE_CEREMONY_COMPLETE) {
            with(ceremony) { icthlarinIntervenes() }
            return
        }
        anim(CLIMB_SEQ)
        delay(2)
        fights.cleanup(player)
        telejump(SophanemCoords.PYRAMID_DOORSTEP, TeleportType.Exempt)
    }

    /** A player who logs in inside the pyramid finds themselves outside its door again. */
    /**
     * Runs a cycle after login: moving the player while the login map is still being built leaves
     * the client drawing the old map around a player it cannot find.
     */
    private fun thrownOut(player: Player) {
        if (!SophanemCoords.inPyramid(player.coords)) {
            return
        }
        fights.cleanup(player)
        flashbacks.end(player)
        PathingEntityCommon.telejump(player, collision, SophanemCoords.PYRAMID_DOORSTEP)
    }

    /* The great pit */

    private suspend fun ProtectedAccess.jumpNorth(edge: BoundLocInfo) {
        val stage = quest.stage(player)
        val dest = CoordGrid(edge.coords.x, edge.coords.z + PIT_WIDTH, edge.coords.level)
        if (flashbacks.active(player) == null) {
            if (stage in STAGE_RETURN_JAR until STAGE_SECOND_FLASHBACK_DONE) {
                relive(Flashback.TheftOfTheJar, STAGE_SECOND_FLASHBACK, "I need to get the burial jar for the mistress...")
                return
            }
            if (stage in STAGE_CEREMONY_STARTED until STAGE_THIRD_FLASHBACK_DONE) {
                relive(
                    Flashback.HidingTheSymbol,
                    STAGE_THIRD_FLASHBACK,
                    "I need to hide the mistress's symbol in the ceremonial room...",
                )
                return
            }
        }
        jump(edge, dest)
    }

    private suspend fun ProtectedAccess.relive(flashback: Flashback, stage: Int, thought: String) {
        startDialogue { mesbox("As you go to make the jump, another sense of deja-vu sweeps over you...") }
        ifClose()
        fadeToBlack()
        quest.advanceTo(this, stage)
        flashbacks.begin(player, flashback)
        telejump(SophanemCoords.PIT_NORTH, TeleportType.Exempt)
        delay(1)
        fadeBackIn()
        startDialogue { chatPlayer(neutral, thought) }
    }

    private suspend fun ProtectedAccess.jumpSouth(edge: BoundLocInfo) {
        val dest = CoordGrid(edge.coords.x, edge.coords.z - PIT_WIDTH, edge.coords.level)
        when (flashbacks.active(player)) {
            Flashback.TheftOfTheJar -> {
                val jar = quest.jar(player)
                if (quest.stage(player) != STAGE_GUARDIAN_DEFEATED || jar == null || jar.obj !in player.inv) {
                    startDialogue { chatPlayer(neutral, "I need to get the burial jar for the mistress...") }
                    return
                }
                rememberTheTheft()
                return
            }
            Flashback.HidingTheSymbol -> {
                if (quest.stage(player) != STAGE_SYMBOL_HIDDEN) {
                    startDialogue { chatPlayer(neutral, "I need to hide the mistress's symbol in the ceremonial room...") }
                    return
                }
                rememberTheSymbol()
                return
            }
            else -> jump(edge, dest)
        }
    }

    /**
     * A running jump over the pit. It takes a fifth of the player's run energy, and the higher
     * their Agility the better their chance of clearing it; a fall leaves them further back.
     */
    private suspend fun ProtectedAccess.jump(edge: BoundLocInfo, dest: CoordGrid) {
        val inMemory = flashbacks.active(player) != null
        if (!inMemory && player.runEnergy < JUMP_ENERGY) {
            mes("You need at least 20% run energy to jump across the pit.")
            return
        }
        telejump(edge.coords, TeleportType.Exempt)
        delay(1)
        if (!inMemory) {
            player.runEnergy = (player.runEnergy - JUMP_ENERGY).coerceAtLeast(0)
            UpdateRun.energy(player, player.runEnergy)
        }
        val agility = statBase(AGILITY)
        val cleared = inMemory || random.of(100) < (JUMP_BASE_CHANCE + agility).coerceAtMost(JUMP_MAX_CHANCE)
        if (!cleared) {
            anim(PIT_FALL_SEQ)
            soundSynth(PIT_JUMP_FALL)
            delay(2)
            telejump(SophanemCoords.PIT_FAIL_LANDING, TeleportType.Exempt)
            anim(GET_UP_SEQ)
            mes("You fall into the pit, and scramble back out further along the tomb.")
            return
        }
        anim(PIT_JUMP_SEQ)
        soundSynth(PIT_JUMP)
        val north = dest.z > edge.coords.z
        exactMove(
            start = edge.coords,
            end = dest,
            delay1 = 0,
            delay2 = JUMP_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (north) constants.em_face_north else constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(JUMP_TICKS)
        soundSynth(PIT_LAND)
    }

    /* The western chamber */

    private suspend fun ProtectedAccess.westDoor(door: BoundLocInfo, type: ObjectServerType) {
        if (coords.z < door.coords.z + 1) {
            with(passages) { walkThrough(door, type) }
            return
        }
        val stage = quest.stage(player)
        when (flashbacks.active(player)) {
            Flashback.BreakIn -> puzzle.open(this)
            Flashback.TheftOfTheJar -> with(passages) { walkThrough(door, type) }
            Flashback.HidingTheSymbol ->
                startDialogue { chatPlayer(neutral, "I need to hide the mistress's symbol in the ceremonial room...") }
            null ->
                when {
                    stage == STAGE_SECOND_FLASHBACK_DONE -> puzzle.open(this)
                    stage >= STAGE_JAR_ROOM_OPEN -> with(passages) { walkThrough(door, type) }
                    else -> mes("The door is firmly shut.")
                }
        }
    }

    private suspend fun ProtectedAccess.pressTile(tile: Int) {
        if (!puzzle.press(this, tile)) {
            return
        }
        player.midiJingle(PUZZLE_JINGLE)
        delay(SOLVED_PICTURE_CYCLES)
        ifClose()
        when {
            flashbacks.active(player) == Flashback.BreakIn -> rememberTheBreakIn()
            quest.stage(player) == STAGE_SECOND_FLASHBACK_DONE -> {
                quest.advanceTo(this, STAGE_JAR_ROOM_OPEN)
                soundSynth(DOOR_SOUND)
                telejump(SophanemCoords.WEST_DOOR, TeleportType.Exempt)
                mes("The door swings open.")
            }
        }
    }

    /** The door opens on the first memory: the wanderer's hypnosis. The player comes to outside. */
    private suspend fun ProtectedAccess.rememberTheBreakIn() {
        delay(1)
        fadeToBlack()
        fights.cleanup(player)
        flashbacks.end(player)
        statHeal(HITPOINTS, 0, 100)
        telejump(SophanemCoords.PYRAMID_DOORSTEP, TeleportType.Exempt)
        quest.advanceTo(this, STAGE_FIRST_FLASHBACK_DONE)
        delay(1)
        fadeBackIn()
        startDialogue {
            chatPlayer(shocked, "I remember! That wanderer, she hypnotised me!")
            chatPlayer(confused, "But... I can't remember why. I needed to get... something. This is so confusing...")
            chatPlayer(neutral, "Maybe someone around here can tell me what on Gielinor is going on.")
        }
    }

    /** Back over the pit with the jar: the second memory ends where it began. */
    private suspend fun ProtectedAccess.rememberTheTheft() {
        fadeToBlack()
        fights.cleanup(player)
        flashbacks.end(player)
        quest.advanceTo(this, STAGE_SECOND_FLASHBACK_DONE)
        telejump(SophanemCoords.PIT_SOUTH, TeleportType.Exempt)
        delay(1)
        fadeBackIn()
        startDialogue { chatPlayer(sad, "So I really did steal the jar. I need to return it as fast as I can.") }
    }

    private suspend fun ProtectedAccess.rememberTheSymbol() {
        fadeToBlack()
        flashbacks.end(player)
        quest.advanceTo(this, STAGE_THIRD_FLASHBACK_DONE)
        telejump(SophanemCoords.PIT_SOUTH, TeleportType.Exempt)
        delay(1)
        fadeBackIn()
        startDialogue { chatPlayer(shocked, "The wanderer! She's going to attack the ceremony! I need to warn them!") }
    }

    private suspend fun ProtectedAccess.takeJar(shelf: CanopicJar) {
        if (flashbacks.active(player) != Flashback.TheftOfTheJar) {
            startDialogue {
                chatPlayer(neutral, "I've already caused enough trouble over these jars. I'll just leave them alone.")
            }
            return
        }
        val jar = quest.jar(player)
        if (shelf != jar) {
            startDialogue { chatPlayer(neutral, "I don't want this jar.") }
            return
        }
        when (quest.stage(player)) {
            STAGE_SECOND_FLASHBACK -> {
                quest.advanceTo(this, STAGE_GUARDIAN_SUMMONED)
                soundSynth(SPECTRE_APPEAR)
                fights.summonGuardian(player, jar)
            }
            STAGE_GUARDIAN_SUMMONED -> {
                if (fights.guardianOf(player) == null) {
                    soundSynth(SPECTRE_APPEAR)
                    fights.summonGuardian(player, jar)
                }
            }
            STAGE_GUARDIAN_DEFEATED -> {
                if (jar.obj in player.inv) {
                    return
                }
                if (invAdd(inv, jar.obj).failure) {
                    mes("You don't have enough room in your inventory.")
                    return
                }
                anim(PICKUP_SEQ)
                setShelfEmpty(jar, true)
                startDialogue { objbox(jar.obj, "You take the burial jar.") }
            }
        }
    }

    /** Putting the jar back is a matter of setting it down in its old place on the shelf. */
    private suspend fun ProtectedAccess.dropJar(jar: CanopicJar, slot: Int) {
        val returning =
            quest.stage(player) == STAGE_JAR_ROOM_OPEN &&
                quest.jar(player) == jar &&
                SophanemCoords.inWestChamber(coords)
        if (!returning) {
            invDrop(slot)
            return
        }
        if (invDel(inv, jar.obj, slot = slot).failure) {
            return
        }
        anim(PICKUP_SEQ)
        setShelfEmpty(jar, false)
        quest.advanceTo(this, STAGE_JAR_RETURNED)
        startDialogue { objbox(jar.obj, "You return the burial jar.") }
    }

    private fun ProtectedAccess.setShelfEmpty(jar: CanopicJar, empty: Boolean) {
        VarPlayerIntMapSetter.set(player, jar.shelfVarbit, if (empty) 1 else 0)
    }

    /* The eastern chamber */

    private suspend fun ProtectedAccess.eastDoor(door: BoundLocInfo, type: ObjectServerType) {
        val stage = quest.stage(player)
        val inside = coords.z < door.coords.z + 1
        if (inside) {
            when {
                flashbacks.active(player) == Flashback.HidingTheSymbol && stage == STAGE_SYMBOL_HIDDEN -> {
                    rememberTheSymbol()
                    return
                }
                stage == STAGE_CEREMONY_COMPLETE -> {
                    with(ceremony) { icthlarinIntervenes() }
                    return
                }
                stage in STAGE_PRIEST_POSSESSED until STAGE_PRIEST_DEFEATED -> fights.cleanup(player)
            }
            with(passages) { walkThrough(door, type) }
            return
        }
        when {
            flashbacks.active(player) == Flashback.HidingTheSymbol -> with(passages) { walkThrough(door, type) }
            stage == STAGE_THIRD_FLASHBACK_DONE -> {
                with(passages) { walkThrough(door, type) }
                delay(1)
                with(ceremony) { devourerRevealed() }
            }
            stage in STAGE_PRIEST_POSSESSED until STAGE_PRIEST_DEFEATED -> {
                with(passages) { walkThrough(door, type) }
                if (fights.priestOf(player) == null) {
                    delay(1)
                    fights.summonPriest(player)
                }
            }
            stage >= STAGE_PRIEST_DEFEATED -> with(passages) { walkThrough(door, type) }
            else -> mes("The door is firmly shut.")
        }
    }

    private suspend fun ProtectedAccess.useChest(chest: BoundLocInfo, into: String) {
        arriveDelay()
        anim(CHEST_SEQ)
        soundSynth(CHEST_SOUND)
        locRepo.change(chest, into, CHEST_TICKS)
    }

    private suspend fun ProtectedAccess.searchSarcophagus() {
        if (flashbacks.active(player) == null) {
            mes("You search the sarcophagus but find nothing of interest.")
            return
        }
        anim(SEARCH_SEQ)
        delay(1)
        val found = player.ilhSarcophagusLoot
        if (found >= SARCOPHAGUS_LOOT.size) {
            mes("You search the sarcophagus but find nothing of interest.")
            return
        }
        when (random.of(SARCOPHAGUS_RISK_ROLLS)) {
            0 -> {
                spotanimMap(worldRepo, SCARAB_CRACK_SPOT, coords)
                soundSynth(SCARABS_APPEAR)
                fights.summonSwarm(player, coords)
                mes("A swarm of scarabs pours out of the sarcophagus!")
                return
            }
            1 -> {
                takeInstantHit(HitType.Typeless, SARCOPHAGUS_TRAP_DAMAGE)
                mes("Something inside the sarcophagus bites you!")
                return
            }
        }
        val (obj, count) = SARCOPHAGUS_LOOT[found]
        if (invAdd(inv, obj, count).failure) {
            mes("You don't have enough room in your inventory.")
            return
        }
        player.ilhSarcophagusLoot = found + 1
        mes("You search the sarcophagus and find something.")
    }

    private suspend fun ProtectedAccess.hideSymbol() {
        if (quest.stage(player) != STAGE_THIRD_FLASHBACK || !SophanemCoords.inEastChamber(coords)) {
            mes("Nothing interesting happens.")
            return
        }
        if (invDel(inv, UNHOLY_SYMBOL).failure) {
            return
        }
        anim(SEARCH_SEQ)
        quest.advanceTo(this, STAGE_SYMBOL_HIDDEN)
        startDialogue {
            objbox(UNHOLY_SYMBOL, "You hide the unholy symbol in the sarcophagus.")
            chatPlayer(neutral, "Yes... Now the mistress can enter the pyramid whenever she pleases...")
        }
    }

    /* Traps */

    /**
     * Runs every tick while the player is in the pyramid: the wall crushers slam down on anyone
     * standing beneath them, the hallway's hidden pits send the unwary back to the entrance, and
     * scarabs sometimes burst out of the floor of the long corridors.
     */
    private fun trapsTick(player: Player) {
        if (!SophanemCoords.inPyramid(player.coords)) {
            player.clearSoftTimer(PYRAMID_TRAPS_TIMER)
            fights.cleanup(player)
            return
        }
        fights.tick(player)
        if (fights.priestRecovered(player)) {
            player.mes("The priest seems to have recovered from the Devourer's spell.")
        }
        val coords = player.coords
        if (coords in SophanemCoords.PIT_TRAPS) {
            if (PIT_TRAP_QUEUE !in player.queueList) {
                fallIntoTrap(player)
            }
            return
        }
        if (coords in SophanemCoords.CRUSHERS && player.hasMovedPreviousCycle && random.of(CRUSH_ONE_IN) == 0) {
            locRepo.findExact(coords, crusherType)?.let { worldRepo.locAnim(it, CRUSH_SEQ) }
            worldRepo.soundArea(coords, WALL_CRUSHER)
            launcher.launch(player) { crushed() }
            return
        }
        if (SophanemCoords.scarabGround(coords) && fights.swarmOf(player) == null && random.of(SCARAB_ONE_IN) == 0) {
            launcher.launch(player) { scarabsBurstOut() }
        }
    }

    /**
     * A soft queue lands the fall: it needs no protected access, so neither a click nor a mummy's
     * attack can cut it short.
     */
    private fun fallIntoTrap(player: Player) {
        player.abortRoute()
        player.anim(TRAP_FALL_SEQ)
        player.spotanim(TRAP_SMOKE_SPOT)
        player.soundSynth(PIT_FALL)
        player.mes("You fall through a trap in the floor!")
        player.softQueue(PIT_TRAP_QUEUE, TRAP_FALL_CYCLES)
    }

    private fun landFromTrap(player: Player) {
        PathingEntityCommon.telejump(player, collision, SophanemCoords.SECRET_DOOR_EXIT)
        locRepo.findExact(SophanemCoords.SECRET_DOOR, secretDoorType)?.let { worldRepo.locAnim(it, SECRET_DOOR_SEQ) }
        player.resetAnim()
        player.softQueue(FLY_OUT_QUEUE, 1)
    }

    /** A cycle after landing, once the client has dropped the fall, which outranks the fly-out. */
    private fun flyOut(player: Player) {
        player.anim(FLY_OUT_SEQ)
        player.soundSynth(PITFALL)
    }

    private fun ProtectedAccess.scarabsBurstOut() {
        spotanimMap(worldRepo, SCARAB_CRACK_SPOT, coords)
        soundSynth(SCARABS_APPEAR)
        fights.summonSwarm(player, coords)
    }

    private fun ProtectedAccess.crushed() {
        soundSynth(WALL_CRUSHED)
        takeInstantHit(HitType.Typeless, random.of(CRUSH_MIN_DAMAGE, CRUSH_MAX_DAMAGE))
        mes("The wall crushes you as it slams shut!")
    }

    private suspend fun ProtectedAccess.startDialogueResult(block: suspend Dialogue.() -> Boolean): Boolean {
        var result = false
        startDialogue { result = block() }
        return result
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private companion object {
        const val PYRAMID_DOOR = "loc.icthalarins_temple_door"
        const val LADDER = "loc.ics_ladder"
        const val PIT_SOUTH_EDGE = "loc.ics_little_pit_to"
        const val PIT_NORTH_EDGE = "loc.ics_little_pit_from"
        const val WEST_DOOR = "loc.icthalarins_ancient_temple_door_1"
        const val EAST_DOOR = "loc.icthalarins_ancient_temple_door_2"
        const val SARCOPHAGUS = "loc.ics_sarcophigi_door_2"
        const val CEREMONIAL_TABLE = "loc.ics_ceremtable"
        const val CHEST_CLOSED = "loc.ics_chestclosed"
        const val CHEST_OPEN = "loc.ics_chestopen"
        const val CHEST_SEQ = "seq.human_openchest"
        const val CHEST_SOUND = "synth.chest_open"
        const val CHEST_TICKS = 500
        const val CRUSHER = "loc.deserttreasure_wall_crusher"
        const val SECRET_DOOR = "loc.icthalarins_tomb_secretdoor"
        const val MULTI_SUFFIX = "_multi"

        const val HITPOINTS = "stat.hitpoints"
        const val AGILITY = "stat.agility"

        /** From the pit's lip on one side to the lip on the other. */
        const val PIT_WIDTH = 2
        const val JUMP_ENERGY = 2000
        const val JUMP_BASE_CHANCE = 40
        const val JUMP_MAX_CHANCE = 95
        const val JUMP_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30

        /** Js5 group of "Icthlarin's Little Puzzle", played when the door puzzle is solved. */
        const val PUZZLE_JINGLE = 192

        const val CRUSH_ONE_IN = 3
        const val CRUSH_MIN_DAMAGE = 2
        const val CRUSH_MAX_DAMAGE = 5
        const val SCARAB_ONE_IN = 60
        const val SARCOPHAGUS_RISK_ROLLS = 4
        const val SARCOPHAGUS_TRAP_DAMAGE = 3

        val SARCOPHAGUS_LOOT =
            listOf(
                "obj.cert_gold_bar" to 10,
                "obj.unstrung_emerald_amulet" to 1,
                "obj.sapphire_necklace" to 1,
            )

        const val DOOR_OPEN_SEQ = "seq.icthalarins_door_opens"
        const val DOOR_SOUND = "synth.door_open"
        const val CLIMB_SEQ = "seq.human_reachforladder"
        const val PICKUP_SEQ = "seq.human_pickuptable"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val GET_UP_SEQ = "seq.human_getup"
        const val PIT_JUMP_SEQ = "seq.ic_pit_jump"
        const val PIT_FALL_SEQ = "seq.ic_pit_fall"
        const val TRAP_FALL_SEQ = "seq.ics_comedy_fall"

        /** Long enough for the last tiles to finish turning, so the golden bird is seen whole. */
        const val SOLVED_PICTURE_CYCLES = 4
        const val PIT_TRAP_QUEUE = "queue.ilh_pit_trap_fall"
        const val FLY_OUT_QUEUE = "queue.ilh_pit_trap_flyout"
        const val LOGIN_EVICT_QUEUE = "queue.ilh_login_evict"
        const val TRAP_FALL_CYCLES = 2
        const val FLY_OUT_SEQ = "seq.ic_flyout"
        const val CRUSH_SEQ = "seq.tomb_wall_crushing"
        const val SECRET_DOOR_SEQ = "seq.ics_secret_door"
        const val TRAP_SMOKE_SPOT = "spotanim.comedy_smoke_fall"
        const val SCARAB_CRACK_SPOT = "spotanim.deserttreasure_cracks_floor_spotanim"

        const val MEEOOW = "synth.meeoow"
        const val SPECTRE_APPEAR = "synth.ics_spectre_appear"
        const val PIT_JUMP = "synth.pit_jump"
        const val PIT_JUMP_FALL = "synth.pit_jump_fall"
        const val PIT_LAND = "synth.pit_land"
        const val PIT_FALL = "synth.pit_fall"
        const val PITFALL = "synth.pitfall"
        const val WALL_CRUSHER = "synth.wall_crusher"
        const val WALL_CRUSHED = "synth.wom_recycle_remove"
        const val SCARABS_APPEAR = "synth.scarabs_appear"
    }
}
