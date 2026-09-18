package org.rsmod.content.quest.area.burthorpe.trollstronghold

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.BERRY
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.BERRY_AWAKE
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.EADGAR
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.EADGAR_KEY
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.GODRIC
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.GODRIC_KEY
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.PRISON_KEY
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_DAD_BEATEN
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_GODRIC_FREED
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_PRISON_OPEN
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.THIEVING_REQ
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.TWIG
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.TWIG_AWAKE
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Troll Stronghold under Trollheim: the front door and its three exits, the stone
 * staircases between its three floors, the locked prison door, the two sleeping guards with the
 * cell keys, the cells of Godric and Eadgar and the secret exit out of the back of the prison.
 *
 * The staircases are "Stone Staircase" locs, which the generic passage script does not
 * recognise, so each flight is paired here with the tile its other end lands on.
 */
class TrollStrongholdDungeon
@Inject
constructor(
    private val quest: TrollStrongholdQuest,
    private val passages: GenericPassageScript,
    private val stairs: StairNavigator,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val prisonDoor = locType(PRISON_DOOR)
    private val twigAwake = npcType(TWIG_AWAKE)
    private val berryAwake = npcType(BERRY_AWAKE)

    override fun ScriptContext.startup() {
        onOpLoc1(FRONT_DOOR) { travel(INSIDE_FRONT_DOOR, entering = true) }
        for (exit in TOP_EXITS) {
            onOpLoc1(exit) { travel(OUTSIDE_FRONT_DOOR, entering = false) }
        }
        onOpLoc1(STAIRS_UP) { climb(it.loc, STAIRS_UP_LANDINGS) }
        onOpLoc1(STAIRS_DOWN) { climb(it.loc, STAIRS_DOWN_LANDINGS) }
        onOpLoc1(PRISON_DOOR) { unlockPrisonDoor(it.loc) }
        onOpLocU(PRISON_DOOR, PRISON_KEY) { unlockPrisonDoor(it.loc) }
        onOpLoc1(GODRIC_CELL) { unlockCell(it.loc, godric = true, used = null) }
        onOpLoc1(EADGAR_CELL) { unlockCell(it.loc, godric = false, used = null) }
        for (key in listOf(GODRIC_KEY, EADGAR_KEY)) {
            onOpLocU(GODRIC_CELL, key) { unlockCell(it.loc, godric = true, used = key) }
            onOpLocU(EADGAR_CELL, key) { unlockCell(it.loc, godric = false, used = key) }
        }
        onOpNpc3(TWIG) { pickpocket(it.npc, GODRIC_KEY, twigAwake) }
        onOpNpc3(BERRY) { pickpocket(it.npc, EADGAR_KEY, berryAwake) }
        onOpLoc1(PRISON_EXIT) {
            arriveDelay()
            player.tsOpenedBackExit = true
            travel(OUTSIDE_SECRET_DOOR, entering = false)
        }
        onOpLoc1(SECRET_DOOR) { secretDoor() }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private suspend fun ProtectedAccess.travel(dest: CoordGrid, entering: Boolean) {
        arriveDelay()
        if (entering) {
            player.tsEnteredStronghold = true
        }
        delay(1)
        telejump(stairs.landing(dest) ?: dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climb(flight: BoundLocInfo, landings: Map<CoordGrid, CoordGrid>) {
        arriveDelay()
        val dest = landings[flight.coords]
        if (dest == null) {
            mes("You cannot see a way through.")
            return
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    /* The prison */

    private suspend fun ProtectedAccess.unlockPrisonDoor(door: BoundLocInfo) {
        arriveDelay()
        val leaving = coords.x >= door.coords.x
        val unlocked = quest.isComplete(player) || quest.stage(player) >= STAGE_PRISON_OPEN
        if (leaving || unlocked) {
            with(passages) { walkThrough(door, prisonDoor) }
            return
        }
        if (PRISON_KEY !in player.inv) {
            soundSynth(LOCKED_SOUND)
            mes("This door is locked.")
            return
        }
        if (quest.stage(player) >= STAGE_DAD_BEATEN) {
            quest.advanceTo(this, STAGE_PRISON_OPEN)
        }
        soundSynth(UNLOCK_SOUND)
        mes("You unlock the prison door.")
        with(passages) { walkThrough(door, prisonDoor) }
    }

    private suspend fun ProtectedAccess.unlockCell(door: BoundLocInfo, godric: Boolean, used: String?) {
        arriveDelay()
        val rightKey = if (godric) GODRIC_KEY else EADGAR_KEY
        val alreadyFree =
            quest.isComplete(player) ||
                if (godric) quest.stage(player) >= STAGE_GODRIC_FREED else player.tsFreedEadgar
        if (alreadyFree) {
            mes("You have no need to do this.")
            return
        }
        if (used != null && used != rightKey) {
            mes("This key doesn't open this door.")
            return
        }
        if (rightKey !in player.inv) {
            if (GODRIC_KEY in player.inv || EADGAR_KEY in player.inv) {
                mes("This key doesn't open this door.")
            } else {
                mes("You need a key to unlock this door.")
            }
            return
        }
        invDel(inv, rightKey)
        faceLoc(door)
        soundSynth(UNLOCK_SOUND)
        if (godric) {
            quest.advanceTo(this, STAGE_GODRIC_FREED)
            startDialogue { chatNpcSpecific("Godric", GODRIC, happy, "Thank you, my friend.") }
        } else {
            player.tsFreedEadgar = true
            startDialogue { chatNpcSpecific("Eadgar", EADGAR, happy, "Thanks! I'm off back home!") }
        }
    }

    /**
     * Twig carries Godric's cell key and Berry Eadgar's. A slip wakes the guard, who turns on the
     * player; a sleeping guard with nothing left to take is picked for nothing.
     */
    private suspend fun ProtectedAccess.pickpocket(guard: Npc, key: String, awake: NpcServerType) {
        if (player.thievingLvl < THIEVING_REQ) {
            mes("You need to be at least level $THIEVING_REQ Thieving to pick the guard's pocket.")
            return
        }
        arriveDelay()
        faceEntitySquare(guard)
        anim(PICKPOCKET_SEQ)
        mes("You attempt to pick the guard's pocket.")
        delay(PICKPOCKET_TICKS)
        if (!guard.isSlotAssigned) {
            return
        }
        val needsKey =
            quest.isStarted(player) &&
                !quest.isComplete(player) &&
                key !in player.inv &&
                if (key == GODRIC_KEY) quest.stage(player) < STAGE_GODRIC_FREED else !player.tsFreedEadgar
        if (random.of(SUCCESS_ROLL) >= successChance()) {
            npcChangeType(guard, awake, WAKE_DURATION)
            guard.say("What do you think you're doing?")
            soundSynth(SHOUT_SOUND)
            guard.opPlayer2(player, aiInteractions)
            return
        }
        soundSynth(STEAL_SOUND)
        if (!needsKey) {
            mes("You pick the guard's pocket but find nothing.")
            return
        }
        invAdd(inv, key)
        mes("You pick the guard's pocket and find a key.")
    }

    private fun ProtectedAccess.successChance(): Int =
        (BASE_SUCCESS + (player.thievingLvl - THIEVING_REQ) * SUCCESS_PER_LEVEL).coerceAtMost(MAX_SUCCESS)

    /* The secret door on the mountain */

    private suspend fun ProtectedAccess.secretDoor() {
        arriveDelay()
        if (!player.tsOpenedBackExit) {
            mes("The door won't open from this side.")
            return
        }
        travel(INSIDE_PRISON_EXIT, entering = true)
    }

    private companion object {
        const val FRONT_DOOR = "loc.troll_stronghold_door"
        val TOP_EXITS =
            listOf(
                "loc.troll_stronghold_top_exit_left",
                "loc.troll_stronghold_top_exit_mid",
                "loc.troll_stronghold_top_exit_right",
            )
        const val STAIRS_UP = "loc.troll_stronghold_stairs"
        const val STAIRS_DOWN = "loc.troll_stronghold_stairstop"
        const val PRISON_DOOR = "loc.troll_stronghold_prison_door_closed"
        const val GODRIC_CELL = "loc.troll_celldoor_godric"
        const val EADGAR_CELL = "loc.troll_celldoor_eadgar"
        const val PRISON_EXIT = "loc.troll_stronghold_exit"
        const val SECRET_DOOR = "loc.troll_stronghold_entrance"

        val OUTSIDE_FRONT_DOOR = CoordGrid(2840, 3690, 0)
        val INSIDE_FRONT_DOOR = CoordGrid(2837, 10090, 2)
        val OUTSIDE_SECRET_DOOR = CoordGrid(2827, 3646, 0)
        val INSIDE_PRISON_EXIT = CoordGrid(2824, 10050, 0)

        /** Each lower flight by its origin, and the tile beside the top of the flight above. */
        val STAIRS_UP_LANDINGS =
            mapOf(
                CoordGrid(2842, 10108, 1) to CoordGrid(2845, 10108, 2),
                CoordGrid(2842, 10051, 1) to CoordGrid(2845, 10052, 2),
                CoordGrid(2852, 10106, 0) to CoordGrid(2851, 10107, 1),
                CoordGrid(2852, 10061, 0) to CoordGrid(2852, 10060, 1),
            )

        /** Each upper flight by its origin, and the tile at the foot of the flight below. */
        val STAIRS_DOWN_LANDINGS =
            mapOf(
                CoordGrid(2843, 10108, 2) to CoordGrid(2841, 10108, 1),
                CoordGrid(2843, 10051, 2) to CoordGrid(2841, 10052, 1),
                CoordGrid(2852, 10107, 1) to CoordGrid(2851, 10107, 0),
                CoordGrid(2852, 10061, 1) to CoordGrid(2852, 10064, 0),
            )

        const val PICKPOCKET_SEQ = "seq.human_pickpocket"
        const val PICKPOCKET_TICKS = 2
        const val SUCCESS_ROLL = 100
        const val BASE_SUCCESS = 60
        const val SUCCESS_PER_LEVEL = 2
        const val MAX_SUCCESS = 95
        const val WAKE_DURATION = 500

        const val LOCKED_SOUND = "synth.locked"
        const val UNLOCK_SOUND = "synth.unlock"
        const val STEAL_SOUND = "synth.pick"
        const val SHOUT_SOUND = "synth.trolls_shout"
    }
}
