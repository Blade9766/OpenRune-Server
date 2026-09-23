package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onOpObj3
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CRYSTAL_CHUNK
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CRYSTAL_HUNK
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CRYSTAL_LUMP
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.DARK_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.ENCHANTED_VIAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GLOWING_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HEART_CRYSTAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HEART_CRYSTAL_GLOWING
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_FORCE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_CRYSTAL_SMELTED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ENTERED_LOWER_DUNGEON
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_HEART_IN_RECESS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_PUSHED_BOULDER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RECEIVED_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SACRED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.VIYELDI_HAT
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Viyeldi caves under the winch, where the source of the sacred water rises.
 *
 * The climb down lands on a ledge above a maze of narrow paths and loose rocks. The three dead
 * heroes each drop a piece of crystal that the ancient furnace fuses into a heart; the dragon's
 * eye rock brings it to life, and the glowing heart opens the barrier to the source. There a
 * boulder stoppers the stream and the spirit of "Echned Zekin" demands the death of Viyeldi, the
 * sorcerer whose empty clothes lie by the rope, in exchange for the water. Echned is Nezikchened
 * in disguise: giving him the blooded dagger, or casting Ungadulu's Holy Force spell on him, makes
 * him show his true form and fight.
 */
class ViyeldiCaves
@Inject
constructor(
    private val legends: LegendsQuest,
    private val support: LegendsSupport,
    private val nezikchened: Nezikchened,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val world: WorldRepository,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    private val watersplash = SpotanimType(WATERSPLASH.asRSCM(RSCMType.SPOTANIM))
    private val boulderHomes = HashMap<Npc, CoordGrid>()

    override fun ScriptContext.startup() {
        onOpLoc1(CLIMBING_ROPE) { climbRope() }
        onOpLoc1(LEDGE_WEST) { ledgeWest(it.vis) }
        onOpLoc1(LEDGE_NORTH) { ledgeNorth(it.vis) }
        onOpLoc1(LEDGE_EAST) { ledgeEast(it.vis) }
        onOpLoc1(ROCKS_ONE) { rocksOne(it.vis) }
        onOpLoc1(ROCKS_TWO) { rocksTwo(it.vis) }
        onOpLoc1(ROCKS_THREE) { rocksThree(it.vis) }

        onOpLoc1(FURNACE) {
            arriveDelay()
            mesbox("This is an ancient looking furnace, it is partially molten and buried by years of rock debris. Inside the furnace there is a compartment with strangely shaped sections.")
            mesbox("You search the lava furnace. You find a small compartment that you may be able to use. Strangely, it looks as if it is designed for a specific purpose... to fuse things together at very high temperatures...")
            furnacePieces(searching = true)
        }
        onOpLocU(FURNACE) { crystalInFurnace(it.objType.internalName) }
        onOpLoc1(DRAGONS_EYE) { mes("These rocks look somehow manufactured.") }
        onOpLocU(DRAGONS_EYE) { onDragonsEye(it.objType.internalName) }
        onOpLoc1(RECESS) {
            mesbox("You see a heart-shaped depression in the wall. A message reads... <col=0000ff>All ye who stand 'ere the dragons teeth, Place your full true heart within, and proceed...</col>")
        }
        onOpLocU(RECESS) { heartInRecess(it.vis, it.objType.internalName) }
        onOpLoc1(RECESS_FULL) { mesbox("You see a magical, glowing crystal shape in the wall. It grants access to the cavern.") }
        onOpLoc1(BARRIER) { walkIntoBarrier() }
        onOpLoc2(BARRIER) {
            mesbox("You see a shimmering field right in front of your eyes. It seems to float in the air. You touch it with your hand and it seems to resist your movements. It will definitely bar you from getting into the next cave.")
        }

        onOpNpc1(BOULDER) { pushBoulder(it.npc) }
        onOpLoc1(SOURCE_POOL) { mesbox("You see some sparkling effervescent water, it looks somehow similar to the water above ground.") }
        onOpLocU(SOURCE_POOL) { fillFromSource(it.objType.internalName) }

        onOpNpc1(ECHNED) { talkToEchned(it.npc) }
        onOpNpcU(ECHNED) { showEchned(it.npc, it.objType.internalName) }
        onOpHeld1(HOLY_FORCE) { castHolyForce() }

        onOpObj3(objType(VIYELDI_HAT)) { touchHat(it.obj) }
        onOpNpc1(VIYELDI) {
            if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER) {
                mes("Your eyes must be playing tricks on you. There is nothing interesting there!")
                return@onOpNpc1
            }
            viyeldiSpeaks(it.npc)
        }
        onOpNpcU(VIYELDI) {
            if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER) {
                mes("Your eyes must be playing tricks on you. There is nothing interesting there!")
                return@onOpNpcU
            }
            if (it.objType.internalName == DARK_DAGGER && inv.count(DARK_DAGGER) > 0) {
                invDel(inv, DARK_DAGGER, 1)
                invAdd(inv, GLOWING_DAGGER, 1)
                stabViyeldi(it.npc)
            } else {
                mes("Nothing interesting happens.")
            }
        }
        onOpNpc2(VIYELDI) { attackViyeldi(it.npc) }
    }

    private suspend fun ProtectedAccess.climbRope() {
        arriveDelay()
        mes("You climb back up the rope to the Shaman Caves..")
        anim(CLIMB_SEQ)
        delay(1)
        teleport(LegendsCoords.WINCH_TOP)
    }

    private suspend fun ProtectedAccess.dangerousPath(prompt: String = "Walk along dangerous path?"): Boolean {
        mesbox("This looks like quite a dangerous walk way, are you sure you want to take this path?")
        val go = choice2("Yes, I can think of nothing more exciting!", true, "No, I'm having second thoughts.", false, title = prompt)
        if (!go) {
            mes("You decide to stay where you are.")
            return false
        }
        ifClose()
        return true
    }

    private suspend fun ProtectedAccess.slipAndHang(hang: CoordGrid, recover: CoordGrid, dir: Int, agilityLoss: Int) {
        mes("You slip and fall!")
        say("Arrgggghhhhhhhhhhhhhhh...........!!!")
        anim(HANG_SEQ)
        exactMove(coords, hang, delay1 = 37, delay2 = 70, dir = dir, teleportType = TeleportType.Exempt)
        delay(5)
        mes("You feel a little nervous from your near fall...")
        anim(HANG_CLIMB_SEQ)
        exactMove(coords, recover, delay1 = 37, delay2 = 70, dir = dir, teleportType = TeleportType.Exempt)
        delay(2)
        mes("You lose some Agility!")
        mes("You wrench your arm as you grab for a handhold.")
        hurt(random.of(1, 10))
        statSub(AGILITY, agilityLoss, 0)
    }

    private suspend fun ProtectedAccess.ledgeWest(ledge: BoundLocInfo) {
        arriveDelay()
        val loc = ledge.coords
        if (coords.x < loc.x) {
            teleport(loc, TeleportType.Exempt)
            delay(1)
            mes("You climb confidently over the rocks and hold your balance well.")
            teleport(loc.translateX(1), TeleportType.Exempt)
            return
        }
        if (!dangerousPath(" Walk along dangerous path?")) return
        mes("You step forward carefully onto the pathway.")
        teleport(loc, TeleportType.Exempt)
        delay(1)
        if (!statRandom(AGILITY, LEDGE_LOW, LEDGE_HIGH, 0)) {
            slipAndHang(loc.translateZ(1), loc.translateX(-1), constants.em_face_east, 1)
            return
        }
        teleport(loc.translateX(-1), TeleportType.Exempt)
        mes("You climb confidently over the rocks and hold your balance well.")
    }

    private suspend fun ProtectedAccess.ledgeNorth(ledge: BoundLocInfo) {
        arriveDelay()
        val loc = ledge.coords
        if (coords.x > loc.x) {
            mes("You step confidently onto the rocky ledge.")
            teleport(loc.translateZ(-1), TeleportType.Exempt)
            return
        }
        if (!dangerousPath()) return
        mes("You start to climb the precarious rocks.")
        teleport(loc, TeleportType.Exempt)
        delay(1)
        if (!statRandom(AGILITY, LEDGE_LOW, LEDGE_HIGH, 0)) {
            slipAndHang(loc.translate(1, -1), loc.translateX(1), constants.em_face_west, 1)
            return
        }
        mes("You climb confidently over the rocks and hold your balance well.")
        delay(1)
        teleport(loc.translateX(1), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.ledgeEast(ledge: BoundLocInfo) {
        arriveDelay()
        val loc = ledge.coords
        if (coords.z < loc.z) {
            mes("You step confidently onto the rocky ledge.")
            teleport(loc.translateZ(1), TeleportType.Exempt)
            return
        }
        if (!dangerousPath()) return
        mes("You step forward carefully onto the pathway.")
        teleport(loc, TeleportType.Exempt)
        delay(1)
        if (!statRandom(AGILITY, LEDGE_LOW, LEDGE_HIGH, 0)) {
            slipAndHang(loc.translateX(-1), loc.translateZ(-1), constants.em_face_north, random.of(1, 10))
            return
        }
        mes("You climb confidently over the rocks and hold your balance well.")
        delay(1)
        teleport(loc.translateZ(-1), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.riskyRocks(): Boolean {
        mesbox("This rock looks pretty risky to climb over, especially with such a drop on each side of the path. Are you sure you want to risk climbing this rock?")
        val go = choice2("Yes, I want to climb over the rocks.", true, "No thanks, I've changed my mind.", false, title = "Climb over rocks?")
        if (!go) {
            mes("You decide not to climb the rocks.")
            return false
        }
        ifClose()
        mes("You start to prepare to climb over the rocks.")
        return true
    }

    private suspend fun ProtectedAccess.fallOffCliff(dest: CoordGrid, dir: Int) {
        mes("You slip and fall!")
        anim(WOBBLE_SEQ, delay = 30)
        exactMove(coords, dest, delay1 = 52, delay2 = 100, dir = dir, teleportType = TeleportType.Exempt)
        delay(3)
        anim(FALL_END_SEQ)
        mes("You take a nasty fall down the side of the cliff...")
        when (random.of(6)) {
            0 -> {
                mes("...and take major damage.")
                hurt(random.of(1, 30))
            }
            1 -> {
                mes("...and take severe damage.")
                hurt(random.of(1, 25))
            }
            2 -> {
                mes("...and take hard damage.")
                hurt(random.of(1, 20))
            }
            3 -> {
                mes("...and take medium damage.")
                hurt(random.of(1, 15))
            }
            4 -> {
                mes("...and take light damage.")
                hurt(random.of(1, 10))
            }
            else -> mes("...but you use your Agility to avoid any damage.")
        }
    }

    private suspend fun ProtectedAccess.rocksOne(rock: BoundLocInfo) {
        arriveDelay()
        val loc = rock.coords
        if (coords.x > loc.x) {
            mes("You easily climb over the rocks.")
            teleport(loc, TeleportType.Exempt)
            delay(1)
            teleport(loc.translateZ(-1), TeleportType.Exempt)
            return
        }
        if (!riskyRocks()) return
        teleport(loc, TeleportType.Exempt)
        delay(1)
        if (!statRandom(AGILITY, LEDGE_LOW, LEDGE_HIGH, 0)) {
            fallOffCliff(loc.translate(-2, 2), constants.em_face_east)
            return
        }
        mes("You climb over the rocks quite easily.")
        teleport(loc.translateX(1), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.rocksTwo(rock: BoundLocInfo) {
        arriveDelay()
        val loc = rock.coords
        if (coords.z < loc.z) {
            mes("You easily climb over the rocks.")
            teleport(loc, TeleportType.Exempt)
            delay(1)
            teleport(loc.translateZ(1), TeleportType.Exempt)
            return
        }
        if (!riskyRocks()) return
        teleport(loc, TeleportType.Exempt)
        delay(1)
        if (!statRandom(AGILITY, LEDGE_LOW, LEDGE_HIGH, 0)) {
            fallOffCliff(loc.translateX(3), constants.em_face_south)
            return
        }
        mes("You climb over the rocks quite easily.")
        teleport(loc.translateZ(-1), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.rocksThree(rock: BoundLocInfo) {
        arriveDelay()
        val loc = rock.coords
        if (coords.z < loc.z) {
            mes("You easily climb over the rocks.")
            teleport(loc, TeleportType.Exempt)
            delay(1)
            teleport(loc.translateZ(1), TeleportType.Exempt)
            return
        }
        if (!riskyRocks()) return
        teleport(loc.translate(1, 1), TeleportType.Exempt)
        delay(1)
        if (!statRandom(AGILITY, LEDGE_LOW, LEDGE_HIGH, 0)) {
            fallOffCliff(coords.translate(2, -2), constants.em_face_south)
            return
        }
        mes("You climb over the rocks quite easily.")
        teleport(loc.translateZ(-1), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.crystalInFurnace(obj: String) {
        arriveDelay()
        if (player.craftingLvl < REQUIRED_CRAFTING) {
            mes("You need a Crafting level of 50 to use this furnace.")
            return
        }
        val bit = CRYSTAL_BITS[obj]
        if (bit == null) {
            mes("The furnace doesn't seem to be working.")
            return
        }
        anim(SEARCH_SEQ)
        if (player.legendsCrystals and bit != 0) {
            mesbox("You have already placed that chunk of crystal into the furnace.")
            return
        }
        objbox(obj, "You place the piece of crystal into a specially shaped compartment of the furnace.")
        invDel(inv, obj, 1)
        player.legendsCrystals = player.legendsCrystals or bit
        furnacePieces(searching = false)
    }

    private suspend fun ProtectedAccess.furnacePieces(searching: Boolean) {
        when (Integer.bitCount(player.legendsCrystals)) {
            0 -> mesbox("The compartment in the furnace looks like it should hold something. It's divided into three seperate sections.")
            1 -> mesbox("The compartment in the furnace looks like it should hold something. It looks like it needs two more pieces.")
            2 -> mesbox("The compartment in the furnace looks like it should hold something. It looks like it needs one more piece.")
            else -> {
                anim(FURNACE_SEQ)
                if (searching) {
                    mesbox("You find the pieces of crystal fit perfectly into the strangely shaped compartments. You use your crafting skill to control the furnace.")
                } else {
                    mesbox("You place the final segment of the crystal into the strangely shaped compartment, all the pieces seem to fit. You use your crafting skill to control the furnace.")
                }
                anim(FURNACE_SEQ)
                mesbox("The heat in the furnace slowly rises and soon fuses the parts together. As soon as the item cools, you pick it up.")
                legends.advanceFrom(this, STAGE_ENTERED_LOWER_DUNGEON, STAGE_CRYSTAL_SMELTED)
                player.legendsCrystals = 0
                invAdd(inv, HEART_CRYSTAL, 1)
                objbox(HEART_CRYSTAL, "As the crystal touches your hands a voice inside of your head says.. <col=ff0000>Bring life to the dragons eye.</col>")
            }
        }
    }

    private suspend fun ProtectedAccess.onDragonsEye(obj: String) {
        arriveDelay()
        if (obj != HEART_CRYSTAL) {
            mes("Nothing interesting happens.")
            return
        }
        anim(SEARCH_SEQ)
        invDel(inv, HEART_CRYSTAL, 1)
        invAdd(inv, HEART_CRYSTAL_GLOWING, 1)
        objbox(
            HEART_CRYSTAL_GLOWING,
            "You carefully place the dragon crystal on the rock. The rocks seem to vibrate and hum and " +
                "the crystal starts to glow. The vibration in the area diminishes, but the crystal " +
                "continues to glow.",
        )
    }

    private suspend fun ProtectedAccess.heartInRecess(recess: BoundLocInfo, obj: String) {
        arriveDelay()
        when (obj) {
            HEART_CRYSTAL -> mesbox("The crystal seems to fit into the recess perfectly, but nothing interesting happens.")
            HEART_CRYSTAL_GLOWING -> {
                if (legends.stage(player) >= STAGE_HEART_IN_RECESS) {
                    mes("The cave is already open to you.")
                    return
                }
                mesbox(
                    "You carefully place the glowing heart shaped crystal into the depression, it slots " +
                        "in perfectly and glows even brighter. You hear a snapping sound coming from in " +
                        "front of the cave.",
                )
                legends.setStage(this, STAGE_HEART_IN_RECESS)
                anim(SEARCH_SEQ)
                world.spotanimMap(watersplash, coords, height = SPLASH_HEIGHT)
                invDel(inv, HEART_CRYSTAL_GLOWING, 1)
                locRepo.change(recess, RECESS_FULL, RECESS_TICKS)
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.walkIntoBarrier() {
        arriveDelay()
        if (legends.stage(player) >= STAGE_HEART_IN_RECESS) {
            if (coords.z > LegendsCoords.BARRIER_SOUTH.z) {
                mes("You walk carefully through the magical barrier and")
                teleport(LegendsCoords.BARRIER_SOUTH, TeleportType.Exempt)
                delay(1)
                mes("into the darkness of the cavern.")
            } else {
                teleport(LegendsCoords.BARRIER_NORTH, TeleportType.Exempt)
            }
            return
        }
        anim(STUMBLE_SEQ)
        delay(1)
        mesbox("You walk into an invisible barrier... Some kind of magical force will not allow you to pass into the cavern. You notice something interesting on the wall to the left.")
    }

    private suspend fun ProtectedAccess.pushBoulder(boulder: Npc) {
        val demon = support.findNpc(coords, Nezikchened.DEMON, 5)
        if (demon != null && nezikchened.ownerOf(demon) == player.uid) {
            mes("The rock is motionless and impossible to budge.")
            return
        }
        anim(PUSH_SEQ)
        mes("You attempt to push the boulder out of the way.")
        val home = boulderHomes.getOrPut(boulder) { boulder.coords }
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER) {
            if (boulder.coords != home) {
                mes("This boulder has already")
                mes("been pushed as far as it can go.")
                return
            }
            if (coords.x <= boulder.coords.x + 1) {
                mes("You don't seem to be able to push it from this angle.")
                return
            }
            mes("You push the boulder out of the way.")
            locRepo.add(boulder.coords, SOURCE_POOL, POOL_TICKS, LocAngle.West, LocShape.CentrepieceStraight)
            soundSynth(GRATE_SOUND)
            boulder.walk(boulder.coords.translateX(-2))
            worldQueues.add(POOL_TICKS) {
                if (boulder.isSlotAssigned) {
                    boulder.walk(home)
                }
            }
            return
        }
        mesbox("A thick green mist seems to emanate from the water... It slowly congeals into the shape of a body.")
        ifClose()
        val echned = support.findNpc(coords, ECHNED, 7) ?: spawnEchned() ?: return
        delay(1)
        mes("The shapeless form slowly floats towards you.")
        startDialogue(echned) { echned() }
    }

    private fun ProtectedAccess.spawnEchned(): Npc? {
        val type = ServerCacheManager.getNpc(ECHNED.asRSCM(RSCMType.NPC)) ?: return null
        val spot = mapFindSquareNone(coords, 1, 3) ?: return null
        val spirit = Npc(type, spot)
        npcRepo.add(spirit, SPIRIT_TICKS)
        spirit.facePlayer(player)
        return spirit
    }

    private suspend fun ProtectedAccess.fillFromSource(obj: String) {
        arriveDelay()
        if (legends.stage(player) < STAGE_DEFEATED_NEZIKCHENED_WATER) {
            mes("Some magical force keeps you away from the water.")
            return
        }
        val fill = SOURCE_FILLS[obj]
        if (fill == null) {
            mes("Nothing interesting happens.")
            return
        }
        invDel(inv, obj, 1)
        invAdd(inv, fill.first, 1)
        if (fill.first == BLESSED_BOWL_PURE) {
            player.legendsBowlUses = 0
            legends.advanceFrom(this, STAGE_DEFEATED_NEZIKCHENED_WATER, STAGE_SACRED_WATER)
        }
        objbox(fill.first, fill.second)
    }

    private suspend fun ProtectedAccess.talkToEchned(echned: Npc) {
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER) {
            mes("You have no further business with the spirit.")
            return
        }
        startDialogue(echned) { echned() }
    }

    private suspend fun ProtectedAccess.showEchned(echned: Npc, obj: String) {
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER) {
            mes("You have no further business with the spirit.")
            return
        }
        when (obj) {
            DARK_DAGGER ->
                startDialogue(echned) {
                    mesbox("You hand the dark dagger to the spirit.")
                    chatNpc(neutral, "Should I take from this that you do not wish to help me gain vengeance on Viyeldi?")
                    if (choice2("Yes, take the dagger back.", true, "No, I'll complete the task.", false)) {
                        chatPlayer(neutral, "Yes, take the dagger back.")
                        access.invDel(access.inv, DARK_DAGGER, 1)
                        mesbox("You feel a chill touch as the spirit removes the dagger from your hand.")
                        chatNpc(neutral, "Very well, if that is your decision... However, you will not get access to the water...")
                    } else {
                        chatPlayer(neutral, "No, I'll complete the task.")
                        chatNpc(neutral, "Very well, return to me when it is done.")
                    }
                }
            GLOWING_DAGGER -> startDialogue(echned) { glowingDagger() }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.echned() {
        if (player.legendsGaveDagger) {
            unmask()
            return
        }
        when {
            access.inv.count(HOLY_FORCE) > 0 -> {
                chatNpc(neutral, "Something seems different about you... Your sense of purpose seems not bent to my will... Give me the dagger that you used to slay Viyeldi or taste my wrath!")
                somethingDifferent()
                return
            }
            access.carries(DARK_DAGGER) -> {
                mesbox("The shapeless entity of Echned Zekin appears in front of you.")
                chatNpc(angry, "Why do you return when your task is still incomplete?")
                taskIncomplete()
                return
            }
            access.carries(GLOWING_DAGGER) -> {
                glowingDagger()
                return
            }
        }
        legends.advanceFrom(access, STAGE_HEART_IN_RECESS, STAGE_PUSHED_BOULDER)
        mesbox("In a rasping, barely audible voice you hear the entity speak.")
        chatNpc(angry, "Who disturbs the rocks of Zekin?")
        mesbox("There seems to be something slightly familiar about this presence.")
        if (choice2("Er... me?", true, "Who's asking?", false)) {
            chatPlayer(neutral, "Er... me?")
            chatNpc(neutral, "So, you desire the water that flows here?")
            if (choice2("Yes, I need it for my quest.", true, "Not really, I just wondered if I could push that big rock.", false)) {
                chatPlayer(neutral, "Yes, I need it for my quest.")
                chatNpc(neutral, "The water babbles so loudly and I am already so tortured. I cannot abide the sound so I have stoppered the streams. Care you not for my torment and pain?")
            } else {
                chatPlayer(neutral, "Not really, I just wondered if I could push that big rock.")
                chatNpc(neutral, "The rock must remain, it stoppers the waters that babble. The noise troubles my soul and I seek some rest... rest from this terrible torture...")
            }
            if (choice2("Why are you tortured?", true, "What can I do about that?", false)) whyTortured() else whatCanIDo()
            return
        }
        chatPlayer(neutral, "Who's asking?")
        mesbox("The hooded, headless figure turns to face you... It's quite unnerving.")
        chatNpc(neutral, "I am Echned Zekin, and I seek peace from my eternal torture...")
        when (choice3("What can I do about that?", 1, "Do I know you?", 2, "Why are you tortured?", 3)) {
            1 -> whatCanIDo()
            2 -> {
                chatPlayer(neutral, "Do I know you?")
                chatNpc(neutral, "I am long since dead and buried, lost in the passages of time. Long since have my kin departed and I been forgotten... It is unlikely that you know me... I am a poor tortured soul looking for rest and eternal peace...")
                if (choice2("Why are you tortured?", true, "What can I do about that?", false)) whyTortured() else whatCanIDo()
            }
            else -> whyTortured()
        }
    }

    private suspend fun Dialogue.whyTortured() {
        chatPlayer(neutral, "Why are you tortured?")
        chatNpc(neutral, "I was robbed of my life by a cruel man called Viyeldi and I hunger for revenge upon him. It is long since I have walked this world looking for him to haunt him and raise terror in his life.")
        chatNpc(neutral, "But tragedy of tragedies, his spirit is neither living or dead. He serves the needs of the source. He died trying to collect the water from this stream, and now I hang in torment for eternity.")
        if (choice2("What can I do about that?", true, "Can't I just get some water?", false)) {
            whatCanIDo()
            return
        }
        chatPlayer(neutral, "Can't I just get some water?")
        chatNpc(neutral, "Yes, you may get some water, but first you must help me. Revenge is the only thing that keeps my spirit in this place.")
        chatNpc(neutral, "Help me take vengeance on Viyeldi and I will gladly remove the rocks and allow you access to the water. What say you?")
        vengeanceChoice()
    }

    private suspend fun Dialogue.whatCanIDo() {
        chatPlayer(neutral, "What can I do about that?")
        chatNpc(neutral, "I was brutally murdered by a vicious man called Viyeldi. I sense his presence near by, but I know that he is no longer living.")
        chatNpc(neutral, "My spirit burns with the need for revenge, I shall not rest while I sense his spirit still. If you seek the pure water, you must ensure he meets his end.")
        chatNpc(neutral, "If not, you will never see the source and your journey back must ye start. What is your answer? Will ye put an end to Viyeldi for me?")
        vengeanceChoice()
    }

    private suspend fun Dialogue.vengeanceChoice() {
        while (true) {
            if (!choice2("I'll do what I must to get the water.", true, "No, I won't take someone's life for you.", false)) {
                chatPlayer(neutral, "No, I won't take someone's life for you.")
                chatNpc(neutral, "Such noble thoughts, but Viyeldi is not alive. He is merely a vessel by which the power of the source protects itself.")
                chatNpc(neutral, "If that is your decision, so be it, but expect not to gain the water from this stream.")
                return
            }
            chatPlayer(neutral, "I'll do what I must to get the water.")
            mesbox("The shapeless spirit seems to crackle with energy.")
            if (legends.owns(access, DARK_DAGGER)) {
                chatNpc(neutral, "Use the dagger I have provided for you to complete this task and then bring it to me when Viyeldi is dead.")
            } else {
                chatNpc(neutral, "You would release me from my torment and the source would be available to you. However, you must realise that this will be no easy task.")
                chatNpc(neutral, "I will furnish you with a weapon which will help you to achieve your aims... Here, take this...")
                objbox(DARK_DAGGER, "The spirit waves an arm and in front of you appears a dark black dagger made of pure obsidian.")
                chatNpc(neutral, "To complete your task you must use this weapon on Viyeldi.")
                access.mes("You take the dagger and place it in your backpack.")
                access.invAddOrDrop(objRepo, DARK_DAGGER)
                legends.raiseTo(access, STAGE_RECEIVED_DAGGER)
            }
            if (choice2("Ok, I'll do it.", true, "I've changed my mind, I can't do it.", false)) {
                chatPlayer(neutral, "Ok, I'll do it.")
                mesbox("The formless shape shimmers brightly...")
                chatNpc(neutral, "You will benefit from this decision, the source will be opened to you. Bring the dagger back to me when you have completed this task.")
                vanishSpirit()
                return
            }
            chatPlayer(neutral, "I've changed my mind, I can't do it.")
            chatNpc(neutral, "The decision is yours but you will have no other way to get the source. The pure water you seek will forever be out of your reach.")
        }
    }

    private suspend fun Dialogue.taskIncomplete() {
        while (true) {
            when (choice3("Who am I supposed to kill again?", 1, "Er I've had second thoughts.", 2, "I have to be going...", 3)) {
                1 -> {
                    chatPlayer(neutral, "Who am I supposed to kill again?")
                    chatNpc(neutral, "Avenge upon me the death of Viyeldi, the cruel. And I will give you access to source...")
                }
                2 -> {
                    chatPlayer(neutral, "Er I've had second thoughts.")
                    chatNpc(neutral, "It is too late for second thoughts... Do as you have agreed and return to me in all haste... His presence tortures me so...")
                }
                else -> {
                    haveToGo()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.somethingDifferent() {
        while (true) {
            when (
                choice4(
                    "I don't have the dagger.", 1,
                    "I haven't slayed Viyeldi yet.", 2,
                    "I have something else in mind!", 3,
                    "I have to be going...", 4,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "I don't have the dagger.")
                    mesbox("The spirit seems to shake with anger...")
                    chatNpc(neutral, "Bring it to me with all haste. Or torment and pain will I bring to you...")
                    mesbox("The spirit extends a wraithlike finger towards you. You feel a searing pain jolt through your body...")
                    access.hurt(5)
                    return
                }
                2 -> {
                    chatPlayer(neutral, "I haven't slayed Viyeldi yet.")
                    chatNpc(neutral, "Go now and slay him, as you agreed. If you are forfeit on this I will take you as a replacement for Viyeldi!")
                }
                3 -> {
                    chatPlayer(neutral, "I have something else in mind!")
                    chatNpc(neutral, "You worthless Vacu, how dare you seek to trick me. Go and slay Viyeldi as you promised or I will layer upon you all the pain and torment I have endured all these long years!")
                }
                else -> {
                    haveToGo()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.haveToGo() {
        chatPlayer(neutral, "I have to be going...")
        chatNpc(neutral, "Return swiftly with the weapon as soon as your task is complete.")
        access.mes("The spirit slowly fades and then disappears.")
        vanishSpirit()
    }

    private fun Dialogue.vanishSpirit() {
        val spirit = npc ?: return
        if (spirit.isSlotAssigned) {
            npcRepo.del(spirit, Int.MAX_VALUE)
        }
    }

    private suspend fun Dialogue.glowingDagger() {
        chatNpc(neutral, "Aha, I see you have completed your task. I'll take that dagger from you now.")
        mesbox(
            "The formless shape of Echned Zekin takes the dagger from you. As a ghostly hand envelopes " +
                "the dagger, something seems to move from the black weapon into the floating figure of the spirit.",
        )
        chatNpc(neutral, "Aahhhhhhhhhh! As I take the spirit of one departed, I will now reveal myself and spell out your doom.")
        if (access.inv.count(GLOWING_DAGGER) > 0) {
            access.invDel(access.inv, GLOWING_DAGGER, 1)
        } else if (access.worn.count(GLOWING_DAGGER) > 0) {
            access.invDel(access.worn, GLOWING_DAGGER, 1)
            access.rebuildAppearance()
        }
        player.legendsGaveDagger = true
        unmask()
    }

    private fun Dialogue.unmask() {
        access.ifClose()
        access.mes("A terrible fear comes over you.")
        access.mes("You feel a terrible sense of loss...")
        access.mes("A sense of loss and dread comes over you.")
        access.statSub("stat.prayer", PRAYER_LOSS, 0)
        vanishSpirit()
        with(nezikchened) { access.summonDemon(Nezikchened.Fight.Water, 3, "Now I am revealed to you, Vacu, so shall ye perish.") }
    }

    private suspend fun ProtectedAccess.castHolyForce() {
        val echned = support.findNpc(coords, ECHNED, HOLY_FORCE_RANGE)
        if (echned == null) {
            mes("There is no suitable candidate to cast this spell on.")
            return
        }
        echned.say("What's this we have here?")
        delay(1)
        mes("You thrust the Holy Force spell in front of the spirit.")
        anim(CAST_SEQ)
        spotanim(HOLY_SPOTANIM, height = HOLY_HEIGHT)
        delay(1)
        mes("A bright, holy light streams out from the page.")
        mes("Thou have returned, but I am ready for thee...")
        mes("Look upon my true face, Vacu, and know thy doom!")
        mes("A terrible fear comes over you... ")
        mes("You feel a terrible sense of loss...")
        mes("The Holy Force spell seems to weaken the demon.")
        mes("A sense of loss and dread comes over you.")
        statSub("stat.prayer", PRAYER_LOSS, 0)
        npcRepo.del(echned, Int.MAX_VALUE)
        with(nezikchened) { summonDemon(Nezikchened.Fight.Water, 3, "Perish, Vacu!") }
    }

    private suspend fun ProtectedAccess.touchHat(hat: Obj) {
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER || player.legendsKilledViyeldi) {
            mesbox("Your hand passes straight through the hat as though it wasn't there at all. Nothing else seems to happen though.")
            return
        }
        if (support.findNpc(hat.coords, VIYELDI, 5) != null) {
            mesbox("Your hand passes through the hat as though it wasn't there at all.")
            return
        }
        val type = ServerCacheManager.getNpc(VIYELDI.asRSCM(RSCMType.NPC)) ?: return
        val spirit = Npc(type, hat.coords)
        npcRepo.add(spirit, VIYELDI_TICKS)
        teleport(hat.coords.translateZ(1))
        delay(1)
        spirit.facePlayer(player)
        mesbox("Your hand passes through the hat as though it wasn't there at all. Instantly, the other clothes begin to animate and walk towards you!")
        viyeldiSpeaks(spirit)
    }

    private suspend fun ProtectedAccess.viyeldiSpeaks(spirit: Npc) {
        startDialogue(spirit) {
            chatNpc(neutral, "Beware adventurer lest thee lose thy head in search of source. Thee hast been tested for bravery and have not been found wanting.")
            mesbox("The spirit wavers slightly and then stands proud.")
            chatNpc(neutral, "Perilous dangers await for thee, Tojalon, Senay and Devere make three, None hold malice but will test your might, pray you do not lose these fights.")
            chatNpc(neutral, "If, however, you win this day, Take heart that see the source you may, Through Dragon's eye will you gain new heart, To see the source and then depart.")
        }
        mes("The clothes slump to the floor again after he has finished.")
        if (spirit.isSlotAssigned) {
            npcRepo.del(spirit, Int.MAX_VALUE)
        }
    }

    private suspend fun ProtectedAccess.attackViyeldi(spirit: Npc) {
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_WATER) {
            mes("You have no further business with him.")
            return
        }
        if (worn.count(DARK_DAGGER) > 0) {
            invDel(worn, DARK_DAGGER, 1)
            invAdd(worn, GLOWING_DAGGER, 1)
            rebuildAppearance()
            stabViyeldi(spirit)
            return
        }
        startDialogue(spirit) {
            chatNpc(neutral, "Adventurer, I am already deceased, and therefore impervious to your attacks. Still, I can tell when I'm not wanted...")
        }
        npcRepo.del(spirit, Int.MAX_VALUE)
        mes("The spirit of Viyeldi crumples back into a pile of clothes. You may have hurt his")
        mes("feelings.")
    }

    private suspend fun ProtectedAccess.stabViyeldi(spirit: Npc) {
        player.legendsKilledViyeldi = true
        mes("You thrust the Dark Dagger at Viyeldi...")
        anim(STAB_SEQ)
        delay(1)
        mes("You hit Viyeldi squarely with the dagger.")
        delay(2)
        spirit.say("So, you have fallen for the foul one's trick...")
        delay(2)
        spirit.say("Aaaaahhhhhhhhh the pain!")
        delay(2)
        mes("You see a flash as something travels from Viyeldi into the dagger.")
        delay(2)
        mes("The dagger glows brightly as Viyeldi crumples to the floor.")
        if (spirit.isSlotAssigned) {
            npcRepo.del(spirit, Int.MAX_VALUE)
        }
    }

    private companion object {
        const val CLIMBING_ROPE = "loc.lgclimbrope_viyeldicaves"
        const val LEDGE_WEST = "loc.rocky_ledge"
        const val LEDGE_NORTH = "loc.rocky_ledge1"
        const val LEDGE_EAST = "loc.rocky_ledge2"
        const val ROCKS_ONE = "loc.viycaves_climbrock1"
        const val ROCKS_TWO = "loc.viycaves_climbrock2"
        const val ROCKS_THREE = "loc.viycaves_climbrock3"
        const val FURNACE = "loc.furnace_legendsquest"
        const val DRAGONS_EYE = "loc.dragons_eye_rock"
        const val RECESS = "loc.heart_recess_empty"
        const val RECESS_FULL = "loc.heart_recess_full"
        const val BARRIER = "loc.legendsquest_force_barrier"
        const val SOURCE_POOL = "loc.lgwaterpool"
        const val BOULDER = "npc.boulder_legends"
        const val ECHNED = "npc.echned_zekin"
        const val VIYELDI = "npc.viyeldi"

        val CRYSTAL_BITS =
            mapOf(
                CRYSTAL_CHUNK to Nezikchened.CHUNK_BIT,
                CRYSTAL_HUNK to Nezikchened.HUNK_BIT,
                CRYSTAL_LUMP to Nezikchened.LUMP_BIT,
            )

        val SOURCE_FILLS =
            mapOf(
                BLESSED_BOWL to (BLESSED_BOWL_PURE to "You get some sacred water in the golden blessed bowl."),
                ENCHANTED_VIAL to (HOLY_WATER to "You get some sacred water into the enchanted vial."),
                GOLD_BOWL to (GOLD_BOWL_PURE to "You get some sacred water into the enchanted golden bowl."),
                "obj.bowl_empty" to ("obj.bowl_water" to "You get some water into your bowl. The water doesn't bubble and sparkle as much as it did in the pool."),
                "obj.bucket_empty" to ("obj.bucket_water" to "You get some water in your bucket. The water doesn't bubble and sparkle as much as it did in the pool."),
                "obj.jug_empty" to ("obj.jug_water" to "You get some water in your jug. The water doesn't bubble and sparkle as much as it did in the pool."),
                "obj.vial_empty" to ("obj.vial_water" to "You put some water in your vial. The water doesn't bubble and sparkle as much as it did in the pool."),
            )

        const val AGILITY = "stat.agility"
        const val REQUIRED_CRAFTING = 50
        const val LEDGE_LOW = 110
        const val LEDGE_HIGH = 250
        const val PRAYER_LOSS = 18
        const val HOLY_FORCE_RANGE = 5
        const val SPIRIT_TICKS = 500
        const val VIYELDI_TICKS = 100
        const val POOL_TICKS = 30
        const val RECESS_TICKS = 15
        const val SPLASH_HEIGHT = 128
        const val HOLY_HEIGHT = 92

        const val CLIMB_SEQ = "seq.human_reachforladder"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val FURNACE_SEQ = "seq.human_furnace"
        const val PUSH_SEQ = "seq.human_push"
        const val STUMBLE_SEQ = "seq.human_stumble_back"
        const val HANG_SEQ = "seq.human_stumble_and_hang"
        const val HANG_CLIMB_SEQ = "seq.human_stumble_and_hang_climbup"
        const val WOBBLE_SEQ = "seq.human_wobbleandfall_l"
        const val FALL_END_SEQ = "seq.human_falling_end"
        const val CAST_SEQ = "seq.human_casting"
        const val STAB_SEQ = "seq.human_sword_stab"
        const val WATERSPLASH = "spotanim.watersplash"
        const val HOLY_SPOTANIM = "spotanim.bookofbinding_effect"
        const val GRATE_SOUND = "synth.grate_open"
    }
}
