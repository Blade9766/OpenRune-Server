package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ORBS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ORB_COUNT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PLANK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BLOWN_BACK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_DISARM
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LADDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_LOGTRAP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_SPEARTRAP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_SPRINGTRAP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_SPRINGTRAP_RESET
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_PICKUP_FLOOR
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_STUNNED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_BIGFIRE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LOCKED_DOOR
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_PICK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_PUT_DOWN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_STUNNED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPOT_STUNNED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_WELL
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The four orbs of light, the traps set round them, the furnace they are put out in and the well
 * that will not let anyone down while one of them still burns.
 *
 * Randas left the orbs in the tunnels as beacons against Iban. Three lie in the open at the end of
 * trapped passages; the fourth sits on a log trap and has to be lifted with the trap disarmed. The
 * spear traps and the spring traps under the flat rocks go off on anyone who walks over them.
 */
@Singleton
class OrbsOfLight
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val launcher: ProtectedAccessLauncher,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val spearTrapType by lazy { locType(SPEAR_TRAP) }
    private val springTriggerType by lazy { locType(SPRING_TRIGGER) }
    private val springTrapType by lazy { locType(SPRING_TRAP) }
    private val logTrapType by lazy { locType(LOG_TRAP) }

    override fun ScriptContext.startup() {
        onOpLoc1(ORB) { orbTrap(it.loc.coords) }
        for ((index, orb) in ORB_LOCS.withIndex()) {
            if (index > 0) {
                onOpLoc1(orb) { takeOrb(index) }
            }
        }
        onOpLoc1(LOG_TRIGGER) { disarmLogTrap(it.loc) }

        onOpLoc2(FURNACE) { burnHeldOrbs() }
        for ((index, orb) in ORBS.withIndex()) {
            onOpLocU(FURNACE, orb) { burnOrb(index) }
            onOpLocU(WELL, orb) { orbInWell() }
        }
        onOpLoc1(WELL) { climbDownWell() }

        onOpLoc1(SPEAR_TRAP) { disarmSpearTrap(it.loc) }
        onOpLoc1(SPRING_TRIGGER) {
            mesbox(
                "The rock appears to move slightly as you touch it...<br>It's a trap!<br>You " +
                    "don't see any way to disarm it,<br>maybe you can find a way to cross over it...",
            )
        }
        onOpLocU(SPRING_TRIGGER, PLANK) { plankOverTrap(it.loc) }
        onPlayerCoordsChanged {
            if (lastKnownCoords != player.coords) {
                checkTraps(player)
            }
        }

        onOpHeld1(JOURNAL) { readJournal() }
        for ((tablet, text) in TABLET_TEXT) {
            onOpLoc1(tablet) { readTablet(text) }
        }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    /* The orbs */

    /** The cradle is a multiloc on the orb's own varbit, so setting it is what empties it. */
    private suspend fun ProtectedAccess.takeOrb(index: Int) {
        arriveDelay()
        if (inv.contains(ORBS[index])) {
            mes("You are already carrying this orb.")
            return
        }
        if (invAdd(inv, ORBS[index]).failure) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(SOUND_PICK)
        UndergroundPassQuest.setVarBit(player, orbTakenVarbit(index), 1)
    }

    /**
     * The first orb sits on the trigger of a swinging log. Reaching for it sets the trap off: the
     * log comes down and throws the player back across the passage.
     */
    private suspend fun ProtectedAccess.orbTrap(orb: CoordGrid) {
        arriveDelay()
        stepThrough(lineTo(orb.translate(1, 0)))
        locRepo.findExact(UpassCoords.ORB_LOG_TRAP, logTrapType)?.let {
            locAnim(worldRepo, it, SEQ_LOC_LOGTRAP)
        }
        val landing =
            collision.nearestFree(coords.translate(-BLOWN_BACK_TILES, 0), BLOWN_LANDING_RADIUS)
                ?: coords
        climbOver(landing, SEQ_BLOWN_BACK, ticks = 2, startDelay = BLOWN_START_CYCLES)
        stun()
        takeInstantHit(HitType.Typeless, minOf(ORB_TRAP_MAX, stat("stat.hitpoints") * 20 / 100))
    }

    private suspend fun ProtectedAccess.disarmLogTrap(trigger: BoundLocInfo) {
        arriveDelay()
        mesbox("The rock appears to move slightly as you touch it... It's a trap!")
        if (!wantsToDisarm()) {
            return
        }
        mes("You try to disarm the trap...")
        anim(SEQ_DISARM)
        soundSynth(SOUND_LOCKED_DOOR)
        delay(2)
        if (!statRandom("stat.thieving", DISARM_LOW, DISARM_HIGH, 0)) {
            mes("...and fail, activating the trap!")
            orbTrap(trigger.coords)
            return
        }
        val orb = ORBS[0]
        if (inv.freeSpace() == 0 || inv.contains(orb) || player.vars[orbBurntVarbit(0)] == 1) {
            mes("...and succeed, you hear it resetting after a few seconds.")
            return
        }
        invAdd(inv, orb)
        UndergroundPassQuest.setVarBit(player, orbTakenVarbit(0), 1)
        mes("...and succeed long enough to take the Orb.")
    }

    private suspend fun ProtectedAccess.burnOrb(index: Int) {
        arriveDelay()
        mes("You throw the glowing orb into the furnace...")
        delay(2)
        if (invDel(inv, ORBS[index]).failure) {
            return
        }
        soundSynth(SOUND_BIGFIRE)
        UndergroundPassQuest.setVarBit(player, orbBurntVarbit(index), 1)
        mes("Its light quickly dims and then dies.")
        delay(2)
        mes("You feel a cold shudder run down your spine.")
    }

    private suspend fun ProtectedAccess.burnHeldOrbs() {
        arriveDelay()
        val held = ORBS.indices.filter { inv.contains(ORBS[it]) }
        if (held.isEmpty()) {
            mes("Nothing interesting happens.")
            return
        }
        for (index in held) {
            burnOrb(index)
        }
    }

    private suspend fun ProtectedAccess.orbInWell() {
        arriveDelay()
        mes("You place the orb in the well...")
        delay(2)
        mes("A mystical force blasts the orb back out.")
        takeInstantHit(HitType.Typeless, ORB_WELL_DAMAGE)
    }

    /** The well only takes anyone down once all four orbs are out. */
    private suspend fun ProtectedAccess.climbDownWell() {
        arriveDelay()
        anim(SEQ_LADDER)
        mes("You climb into the well...")
        if (player.orbsBurnt == ORB_COUNT) {
            delay(1)
            mes("You feel the grip of icy hands all around you...")
            delay(1)
            telejump(UpassCoords.WELL_BOTTOM)
            if (quest.stage(player) == STAGE_BRIDGE) {
                quest.advanceTo(this, STAGE_WELL)
            }
            mes("... slowly dragging you further down into the caverns.")
            return
        }
        delay(1)
        mes("... A mystical force seems to blast you out of the well!")
        delay(1)
        mes("... There must be some positive force nearby.")
        delay(1)
        takeInstantHit(HitType.Typeless, minOf(stat("stat.hitpoints") - 1, WELL_BLAST_DAMAGE))
    }

    /* The traps */

    private suspend fun ProtectedAccess.wantsToDisarm(): Boolean =
        choice2(
            "Yes, I'll give it a go.",
            true,
            "No thanks, I'll leave it alone.",
            false,
            title = "Do you want to try and disarm it?",
        )

    private suspend fun ProtectedAccess.disarmSpearTrap(trap: BoundLocInfo) {
        arriveDelay()
        mesbox("The markings appear to be holes in the wall... It's a trap!")
        if (!wantsToDisarm()) {
            return
        }
        mes("You try to disarm the trap...")
        anim(SEQ_DISARM)
        soundSynth(SOUND_LOCKED_DOOR)
        delay(2)
        if (!statRandom("stat.thieving", DISARM_LOW, DISARM_HIGH, 0)) {
            mes("...and fail, activating the trap!")
            locAnim(worldRepo, trap, SEQ_LOC_SPEARTRAP)
            say("Ouch!")
            takeInstantHit(HitType.Typeless, stat("stat.hitpoints") * 10 / 100 + 1)
            return
        }
        mes("...and succeed, you quickly walk past.")
        val dx = if (coords.x > trap.coords.x) -SPEAR_TRAP_SKIP else SPEAR_TRAP_SKIP
        climbOver(coords.translate(dx, 0), SEQ_WALK, ticks = 1)
    }

    /**
     * The plank bridges the flat rock without touching it. It is laid down, walked over, and
     * picked back up on the far side.
     */
    private suspend fun ProtectedAccess.plankOverTrap(trigger: BoundLocInfo) {
        arriveDelay()
        mes("You place the plank across the flat rock...")
        anim(SEQ_PICKUP_FLOOR)
        soundSynth(SOUND_PUT_DOWN)
        delay(1)
        mes("...and quickly walk over.")
        val t = trigger.coords
        val (dest, angle) =
            when {
                coords.z > t.z -> t.translate(0, -1) to LocAngle.West
                coords.z < t.z -> t.translate(0, 1) to LocAngle.West
                coords.x > t.x -> t.translate(-1, 0) to LocAngle.North
                else -> t.translate(1, 0) to LocAngle.North
            }
        climbOver(dest, SEQ_WALK, ticks = 1)
        locRepo.del(trigger, PLANK_TICKS)
        locRepo.add(t, PLANK_LOC, PLANK_TICKS, angle, LocShape.GroundDecor)
    }

    /** Runs on every step: the spear traps and spring traps go off under anyone who walks on them. */
    private fun checkTraps(player: Player) {
        val coords = player.coords
        if (coords.level != 0 || coords.x !in TRAP_MIN_X..TRAP_MAX_X || coords.z !in TRAP_MIN_Z..TRAP_MAX_Z) {
            return
        }
        val spear =
            locRepo.findExact(coords, spearTrapType)
                ?: locRepo.findExact(coords.translate(0, -1), spearTrapType)
        if (spear != null) {
            launcher.launch(player) {
                locAnim(worldRepo, spear, SEQ_LOC_SPEARTRAP)
                say("Ouch!")
                takeInstantHit(HitType.Typeless, stat("stat.hitpoints") * 10 / 100 + 1)
            }
            return
        }
        if (locRepo.findExact(coords, springTriggerType) == null) {
            return
        }
        val trap =
            locRepo.findExact(coords.translate(-1, 0), springTrapType)
                ?: locRepo.findExact(coords.translate(0, -1), springTrapType)
        launcher.launch(player) {
            trap?.let { locAnim(worldRepo, it, SEQ_LOC_SPRINGTRAP) }
            say("Ouch!")
            takeInstantHit(HitType.Typeless, statBase("stat.hitpoints") * 8 / 100 + 1)
            delay(1)
            trap?.let { locAnim(worldRepo, it, SEQ_LOC_SPRINGTRAP_RESET) }
        }
    }

    private fun ProtectedAccess.stun() {
        anim(SEQ_STUNNED)
        spotanim(SPOT_STUNNED, height = STUN_HEIGHT)
        soundSynth(SOUND_STUNNED)
    }

    /* Writing */

    private suspend fun ProtectedAccess.readJournal() {
        player.readJournal = 1
        mes("The journal is old and worn.")
        delay(2)
        mes("It reads...")
        delay(2)
        mesbox(
            "<col=000080>The Journal of Randas</col><br>I came to cleanse these mountain passes " +
                "of the dark forces that dwell here. I knew my journey would be treacherous, so I " +
                "deposited Spheres of Light in some of the tunnels.",
        )
        mesbox(
            "These spheres are a beacon of safety for all who come. The spheres were created by " +
                "Saradominist mages. When held they boost our faith and courage. I still feel... " +
                "Iban relentlessly tugging... at my weak soul...",
        )
        mesbox(
            "...bringing out any innate goodness to ones heart, illuminating the dark caverns " +
                "with the light of Saradomin, bringing fear and pain to all who embrace the dark " +
                "side.",
        )
        mesbox(
            "My men are still repelled by 'Iban's will' - it seems as if their pure hearts bar " +
                "them from entering Iban's realm. My turn has come. I dare not admit it to my " +
                "loyal men, but I fear for the welfare of my soul.",
        )
    }

    private suspend fun ProtectedAccess.readTablet(text: String) {
        arriveDelay()
        mes("The writing seems to have been scratched")
        mes("into the rock with bare hands, it reads...")
        delay(1)
        mesbox("<col=8B0000>$text</col>")
    }

    private companion object {
        const val ORB = "loc.caveorb"
        val ORB_LOCS = arrayOf(ORB, "loc.caveorb2", "loc.caveorb3", "loc.caveorb4")
        const val LOG_TRIGGER = "loc.upass_logtrap_trigger"
        const val LOG_TRAP = "loc.upass_logtrap"
        const val FURNACE = "loc.furnace_upass"
        const val WELL = "loc.cave_well"
        const val SPEAR_TRAP = "loc.upass_speartrap"
        const val SPRING_TRIGGER = "loc.upass_double_springtrap_trigger"
        const val SPRING_TRAP = "loc.upass_double_springtrap"
        const val PLANK_LOC = "loc.upass_crossing_plank"
        const val SEQ_WALK = "seq.human_walk_f"

        /** The corridors of the orb caverns, the only place the spear and spring traps stand. */
        const val TRAP_MIN_X = 2368
        const val TRAP_MAX_X = 2447
        const val TRAP_MIN_Z = 9664
        const val TRAP_MAX_Z = 9727

        const val DISARM_LOW = 110
        const val DISARM_HIGH = 320
        const val SPEAR_TRAP_SKIP = 2
        const val PLANK_TICKS = 5
        const val BLOWN_BACK_TILES = 5
        const val BLOWN_LANDING_RADIUS = 2
        const val BLOWN_START_CYCLES = 20
        const val STUN_HEIGHT = 124
        const val ORB_TRAP_MAX = 5
        const val ORB_WELL_DAMAGE = 3
        const val WELL_BLAST_DAMAGE = 10

        val TABLET_TEXT =
            mapOf(
                "loc.stone_tablet1_upass" to
                    "All those who thirst for knowledge,<br>Bow down to the lord.<br>All you " +
                        "that crave eternal life,<br>Come and meet your god.<br>For no man nor " +
                        "beast<br>can cast a spell<br>against the wake of eternal hell.",
                "loc.stone_tablet2_upass" to
                    "Most men do live in fear of death<br>that it might steal their soul.<br>" +
                        "Some work and pray, to shield their life<br>from the ravages of the " +
                        "cold.<br>But only those who embrace the end<br>can truly make their " +
                        "life extend.<br>And when all hope begins to fade<br>look above and use " +
                        "nature as your aid.",
                "loc.stone_tablet3_upass" to
                    "And now our God has given us<br>one who is from our own.<br>A saviour who " +
                        "once sat upon<br>his father's glorious throne.<br>It is in your name " +
                        "that we<br>will lead the attack,<br>Iban, Son of Zamorak!",
                "loc.stone_tablet4_upass" to
                    "Here lies the sacred well,<br>entrance to Iban's hell.<br>He blesses all " +
                        "his disciples keen.<br>'We bathe in pure evil' they yell.<br>The force " +
                        "of darkness is strong,<br>the wait for morning forever long.<br>If a " +
                        "light should break the night<br>the dark will rise to win the fight.",
                "loc.stone_tablet5_upass" to
                    "Leave this battered corpse be,<br>for now he lives as spirit alone.<br>" +
                        "Turn and leave, run and flee<br>before the Soulless writhe and moan." +
                        "<br>It is the soil that shall rise<br>to turn away the mites and flies." +
                        "<br>Only as flesh becomes ash,<br>and wood becomes dust,<br>will Iban's " +
                        "corpse embrace<br>nature's eternal lust.",
                "loc.stone_tablet7_upass" to
                    "The doors of Iban will not open<br>while a beating, good heart is present.",
                "loc.stone_tablet8_upass" to
                    "Blessed be the servants<br>of the one true master of this place.",
            )
    }
}
