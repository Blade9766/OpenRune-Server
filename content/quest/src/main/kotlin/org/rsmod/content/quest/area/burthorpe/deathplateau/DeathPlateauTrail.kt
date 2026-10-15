package org.rsmod.content.quest.area.burthorpe.deathplateau

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.CLIMBING_BOOTS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SECRET_WAY_MAP
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SPIKED_BOOTS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.TENZING
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way from Burthorpe to the foot of Death Plateau: Saba's cave, Tenzing's hut with its
 * doors and chicken pen, the danger sign on the main path, and the secret way behind the hut
 * that the player walks to check before reporting to Denulth. Also the climbing boots, which
 * only fit once Tenzing has taken the player's measure, and the spiked boots, which never do.
 */
class DeathPlateauTrail
@Inject
constructor(
    private val quest: DeathPlateauQuest,
    private val passages: GenericPassageScript,
    private val stairs: StairNavigator,
    private val search: NpcSearch,
    private val worldRepo: WorldRepository,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val frontDoor = locType(FRONT_DOOR)
    private val backDoor = locType(BACK_DOOR)
    private val penGates = PEN_GATES.associateWith(::locType)

    override fun ScriptContext.startup() {
        onOpLoc1(SABA_CAVE_ENTRANCE) { caveTo(SABA_CAVE_LANDING) }
        onOpLoc1(SABA_CAVE_EXIT) { caveTo(SABA_CAVE_OUTSIDE) }
        onOpLoc1(FRONT_DOOR) { tenzingsFrontDoor(it.loc) }
        onOpLoc1(BACK_DOOR) {
            arriveDelay()
            with(passages) { walkThrough(it.loc, backDoor) }
        }
        for ((gate, type) in penGates) {
            onOpLoc1(gate) { chickenPen(it.loc, type) }
        }
        onOpLoc1(DANGER_SIGN) { readDangerSign() }
        onOpHeld2(CLIMBING_BOOTS) {
            if (!QuestRequirements.hasCompleted(player, QUEST_KEY)) {
                mes("The sherpa's feet must be very small; I can't get them on.")
                return@onOpHeld2
            }
            invEquip(it.slot)
        }
        onOpHeld2(SPIKED_BOOTS) {
            mes("Trying to walk in these would be difficult. I'll carry them for now.")
        }
        onPlayerCoordsChanged {
            if (player.coords.isOnPathEnd() && isScouting(player)) {
                player.dpPathScouted = true
                launcher.launch(player) { farEnough() }
            }
        }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.caveTo(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(stairs.landing(dest) ?: dest, TeleportType.Exempt)
    }

    /* Tenzing's hut */

    private suspend fun ProtectedAccess.tenzingsFrontDoor(door: BoundLocInfo) {
        arriveDelay()
        val leaving = coords.x <= door.coords.x
        if (leaving || quest.isComplete(player) || player.dpTenzingAsked) {
            with(passages) { walkThrough(door, frontDoor) }
            return
        }
        soundSynth(KNOCK_SOUND)
        mesbox("You knock on the door.")
        val sabaSentMe = player.dpSabaAsked
        startDialogue {
            chatNpcSpecific("Tenzing", TENZING, neutral, "No milk today! Thank you!")
            if (sabaSentMe) {
                chatPlayer(neutral, "I'm not the milkman, I need your help!")
                chatNpcSpecific("Tenzing", TENZING, neutral, "Oh...OK. You'd better come in then.")
            }
        }
        if (sabaSentMe) {
            with(passages) { walkThrough(door, frontDoor) }
        }
    }

    private suspend fun ProtectedAccess.chickenPen(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.x > gate.coords.x
        if (leaving) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        startDialogue {
            chatNpcSpecific(
                "Tenzing",
                TENZING,
                angry,
                "Where do you think you're going? This is private property!",
            )
        }
    }

    /* The danger sign on the main path */

    /**
     * Reading the sign swings the camera round to the nearest thrower troll up the path, which
     * notices it and lobs a rock straight at it.
     */
    private suspend fun ProtectedAccess.readDangerSign() {
        arriveDelay()
        mes("Danger - Trolls!")
        val thrower = nearestThrower() ?: return
        val eye = coords.translate((thrower.coords.x - coords.x) / 3, (thrower.coords.z - coords.z) / 3)
        camMoveTo(eye, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE2)
        camLookAt(thrower.coords, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE2)
        delay(2)
        thrower.faceSquare(eye)
        thrower.anim(THROW_SEQ)
        thrower.spotanim(LAUNCH_SPOTANIM)
        soundSynth(THROW_SOUND)
        worldRepo.projAnim(
            ProjAnim(
                spotanim = ROCK_SPOTANIM.asRSCM(RSCMType.SPOTANIM),
                startHeight = ROCK_START_HEIGHT,
                endHeight = CAMERA_HEIGHT / 4,
                startTime = ROCK_START,
                endTime = ROCK_END,
                angle = ROCK_ANGLE,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = thrower.coords,
                endCoord = eye,
            ),
        )
        delay(2)
        soundSynth(IMPACT_SOUND)
        delay(1)
        camReset()
    }

    private fun ProtectedAccess.nearestThrower(): Npc? =
        THROWERS.flatMap { npcFindAll(coords, it, THROWER_SEARCH, HuntVis.Off, search).toList() }
            .minByOrNull { it.coords.chebyshevDistance(coords) }

    /* The secret way */

    private fun isScouting(player: Player): Boolean =
        !player.dpPathScouted &&
            player.dpMapDrawn &&
            quest.isStarted(player) &&
            !quest.isComplete(player) &&
            SECRET_WAY_MAP in player.inv

    private suspend fun ProtectedAccess.farEnough() {
        startDialogue {
            chatPlayer(
                neutral,
                "I think this is far enough, I can see Death Plateau and it looks like the " +
                    "trolls haven't found the path. I'd better go and tell Denulth.",
            )
        }
        mes("You should go and speak to Denulth.")
    }

    private fun CoordGrid.isOnPathEnd(): Boolean =
        level == 0 && x in PATH_END_X && z in PATH_END_Z

    private companion object {
        const val SABA_CAVE_ENTRANCE = "loc.death_hermitcave_entrance"
        const val SABA_CAVE_EXIT = "loc.death_hermitcave_exit"
        const val FRONT_DOOR = "loc.death_sherpa_door"
        const val BACK_DOOR = "loc.death_sherpa_backdoor"
        const val DANGER_SIGN = "loc.death_dangersign_trolls"
        val PEN_GATES = listOf("loc.death_fencegate_l", "loc.death_fencegate_r")

        val SABA_CAVE_LANDING = CoordGrid(2269, 4752, 0)
        val SABA_CAVE_OUTSIDE = CoordGrid(2858, 3577, 0)

        /** The eastern end of the secret way, past the second goat, in sight of the plateau. */
        val PATH_END_X = 2866..2877
        val PATH_END_Z = 3605..3612

        val THROWERS = (1..5).map { "npc.death_troll_thrower$it" }
        const val THROWER_SEARCH = 25

        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 4
        const val CAMERA_RATE2 = 10

        const val THROW_SEQ = "seq.troll_rock_throw"
        const val LAUNCH_SPOTANIM = "spotanim.troll_rock_launch"
        const val ROCK_SPOTANIM = "spotanim.troll_rock_travel"
        const val ROCK_START_HEIGHT = 90
        const val ROCK_START = 20
        const val ROCK_END = 60
        const val ROCK_ANGLE = 16

        const val KNOCK_SOUND = "synth.knock_knock"
        const val THROW_SOUND = "synth.troll_throw_rock"
        const val IMPACT_SOUND = "synth.rock_impact"
    }
}
