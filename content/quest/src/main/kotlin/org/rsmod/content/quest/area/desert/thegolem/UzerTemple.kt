package org.rsmod.content.quest.area.desert.thegolem

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.desert.shadowofthestorm.DemonThroneRoom
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.CHISEL
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.DEMON_DOOR_SOUND
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.DOOR_ARRIVAL
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.HAMMER
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.MUSHROOM
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.RUINS_ARRIVAL
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_PORTAL_OPEN
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_SEEN_DEMON
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_STATUETTE_PLACED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STATUETTE
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.TELEPORT_SOUND
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.TEMPLE_ARRIVAL
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.THRONE_ROOM_ARRIVAL
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.TURN_STATUE_SOUND
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.setVarBit
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The temple of Uzer: the stairs down from the ruins, the black mushrooms that grow around it,
 * the four statuettes in their alcoves that unseal the demon's door, and Thammaron's throne room
 * beyond it.
 *
 * Each alcove is a multiloc on its own varbit. Alcoves `a` to `c` are 0 facing right and 1 facing
 * left; `d` is 0 while Varmen's statuette is away, then 1 facing left and 2 facing right. The door
 * opens when every statuette looks north at it: the west pair facing left, the east pair right.
 */
class UzerTemple
@Inject
constructor(
    private val golem: TheGolemQuest,
    private val objRepo: ObjRepository,
    private val collision: CollisionFlagMap,
    private val throneRoom: DemonThroneRoom,
    private val sots: ShadowOfTheStormQuest,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(STAIRS_DOWN) { descend() }
        onOpLoc1(STAIRS_UP) { ascend() }
        onOpLoc1(MUSHROOMS) { pickMushroom() }

        for (alcove in Alcove.entries) {
            onOpLoc1(alcove.loc) { turnStatuette(it.loc, alcove) }
        }
        onOpLocU(Alcove.D.loc, STATUETTE) { placeStatuette() }
        onOpLoc1(DEMON_DOOR) { demonDoor() }
        onOpLoc1(DEMON_PORTAL) { leaveThroneRoom() }
        onOpLocU(THRONE, HAMMER) { takeThroneGems() }
        onOpLocU(THRONE, CHISEL) { takeThroneGems() }
    }

    private suspend fun ProtectedAccess.descend() {
        arriveDelay()
        delay(1)
        telejump(TEMPLE_ARRIVAL)
        if (golem.isStarted(player)) {
            player.golemSeenUnderground = true
        }
    }

    private suspend fun ProtectedAccess.ascend() {
        arriveDelay()
        delay(1)
        telejump(RUINS_ARRIVAL)
        if (sots.stage(player) in ShadowOfTheStormQuest.STAGE_CHASE..ShadowOfTheStormQuest.STAGE_SECOND_RITUAL) {
            soundSynth(ShadowOfTheStormQuest.SOUND_SANDSTORM)
            mes("The sky over Uzer has gone the colour of a bruise, and the sand is moving in it.")
        }
    }

    private suspend fun ProtectedAccess.pickMushroom() {
        arriveDelay()
        if (inv.isFull()) {
            mes("You don't have enough inventory space to hold the mushroom.")
            return
        }
        anim(PICK_SEQ)
        soundSynth(PICK_SOUND)
        delay(1)
        invAdd(inv, MUSHROOM)
        mesbox("You pick a mushroom.")
    }

    private suspend fun ProtectedAccess.placeStatuette() {
        arriveDelay()
        if (vars[Alcove.D.varbit] != Alcove.EMPTY) {
            mes("There is already a statuette in the alcove.")
            return
        }
        if (invDel(inv, STATUETTE).failure) {
            return
        }
        anim(PLACE_SEQ)
        setVarBit(player, Alcove.D.varbit, Alcove.D.left)
        mes("You insert the statuette into the alcove.")
        golem.advanceTo(this, STAGE_STATUETTE_PLACED)
    }

    private suspend fun ProtectedAccess.turnStatuette(loc: BoundLocInfo, alcove: Alcove) {
        arriveDelay()
        faceSquare(loc.coords)
        val facing = vars[alcove.varbit]
        if (alcove == Alcove.D && facing == Alcove.EMPTY) {
            return
        }
        if (golem.stage(player) >= STAGE_PORTAL_OPEN) {
            mes("The statuette is now locked in place.")
            return
        }
        anim(PLACE_SEQ)
        soundSynth(TURN_STATUE_SOUND)
        setVarBit(player, alcove.varbit, if (facing == alcove.left) alcove.right else alcove.left)
        if (golem.stage(player) == STAGE_STATUETTE_PLACED && Alcove.entries.all { vars[it.varbit] == it.solved }) {
            delay(1)
            soundSynth(DEMON_DOOR_SOUND)
            golem.advanceTo(this, STAGE_PORTAL_OPEN)
            mes("The door grinds open.")
        }
    }

    private suspend fun ProtectedAccess.demonDoor() {
        arriveDelay()
        // Shadow of the Storm holds its own copy of the room on the far side of this door.
        if (sots.inProgress(player)) {
            if (sots.stage(player) < ShadowOfTheStormQuest.STAGE_INFILTRATED) {
                mes("Evil Dave is standing between you and the portal.")
                return
            }
            mes("You step into the portal.")
            soundSynth(TELEPORT_SOUND)
            delay(1)
            with(throneRoom) { enterThroneRoom() }
            return
        }
        if (golem.stage(player) < STAGE_PORTAL_OPEN) {
            mes("The door won't open. There must be some way to unlock it.")
            return
        }
        mes("You step into the portal.")
        soundSynth(TELEPORT_SOUND)
        delay(1)
        telejump(THRONE_ROOM_ARRIVAL)
        if (golem.stage(player) == STAGE_PORTAL_OPEN) {
            golem.advanceTo(this, STAGE_SEEN_DEMON)
            mes("This room is dominated by a colossal horned skeleton!")
        }
    }

    private suspend fun ProtectedAccess.leaveThroneRoom() {
        if (throneRoom.inside(player)) {
            with(throneRoom) { leave() }
            return
        }
        arriveDelay()
        mes("You step into the portal.")
        soundSynth(TELEPORT_SOUND)
        delay(1)
        telejump(collision.nearestFree(DOOR_ARRIVAL) ?: DOOR_ARRIVAL)
    }

    private suspend fun ProtectedAccess.takeThroneGems() {
        arriveDelay()
        if (player.golemThroneEmptied) {
            mes("You have already taken the gems from the throne.")
            return
        }
        if (HAMMER !in inv || CHISEL !in inv) {
            mes("You'll need a chisel as well as a hammer to get the gems.")
            return
        }
        anim(CHISEL_SEQ)
        soundSynth(CHISEL_SOUND)
        delay(2)
        player.golemThroneEmptied = true
        for (gem in THRONE_GEMS) {
            invAddOrDrop(objRepo, gem, 2)
        }
        mes("You prise the gems from the throne.")
    }

    private enum class Alcove(val loc: String, val varbit: String, val left: Int, val right: Int, val solved: Int) {
        A("loc.golem_statuettea", "varbit.golem_statuettestatusa", left = 1, right = 0, solved = 1),
        B("loc.golem_statuetteb", "varbit.golem_statuettestatusb", left = 1, right = 0, solved = 1),
        C("loc.golem_statuettec", "varbit.golem_statuettestatusc", left = 1, right = 0, solved = 0),
        D("loc.golem_statuetted", "varbit.golem_statuettestatusd", left = 1, right = 2, solved = 2);

        companion object {
            const val EMPTY = 0
        }
    }

    private companion object {
        const val STAIRS_DOWN = "loc.golem_insidestairs_top"
        const val STAIRS_UP = "loc.golem_insidestairs_base"
        const val MUSHROOMS = "loc.golem_black_mushrooms"
        const val DEMON_DOOR = "loc.golem_portal"
        const val DEMON_PORTAL = "loc.golem_demon_portal"
        const val THRONE = "loc.golem_demon_throne"

        const val PICK_SEQ = "seq.human_pickupfloor"
        const val PLACE_SEQ = "seq.human_pickuptable"
        const val CHISEL_SEQ = "seq.enakh_player_chisel"
        const val PICK_SOUND = "synth.pick"
        const val CHISEL_SOUND = "synth.chisel"

        val THRONE_GEMS = listOf("obj.ruby", "obj.emerald", "obj.sapphire")
    }
}
