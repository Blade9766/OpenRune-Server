package org.rsmod.content.quest.area.ikov

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.inv.weight.InvWeight
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.feet
import org.rsmod.api.player.front
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The temple itself: the two gates near the entrance, the lever that opens the southern pair, the
 * stairs down to the webbed room, the lava bridge and the door into McGrubor's Wood.
 *
 * The bridge is the only piece with a mechanic of its own. The tiles spanning the lava are
 * authored a plane up with the bridge flag, so the routefinder walks anyone straight over them;
 * the check that they are light enough to survive it is here, on the step out over the lava.
 */
class IkovDungeon
@Inject
constructor(
    private val quest: TempleOfIkovQuest,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val bracketType: ObjectServerType =
        ServerCacheManager.getObject(LEVER_BRACKET.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $LEVER_BRACKET")

    private val mendedLeverType: ObjectServerType =
        ServerCacheManager.getObject(MENDED_LEVER.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $MENDED_LEVER")

    override fun ScriptContext.startup() {
        onOpLocU(LEVER_BRACKET, TempleOfIkovQuest.LEVER) { fitLever(it.loc) }
        onOpLoc1(MENDED_LEVER) { pullMendedLever() }

        for (gate in listOf(FEAR_GATE_LEFT, FEAR_GATE_RIGHT)) {
            onOpLoc1(gate) { chamberOfFear(it.loc, it.type) }
        }
        for (gate in listOf(LEVER_GATE_LEFT, LEVER_GATE_RIGHT)) {
            onOpLoc1(gate) { leverGate(it.loc, it.type) }
        }

        onOpLoc1(DARK_STAIRS_DOWN) { climbDarkStairs(down = true) }
        onOpLoc1(DARK_STAIRS_UP) { climbDarkStairs(down = false) }

        onOpLoc1(SHINY_KEY_DOOR) { shinyKeyDoor(it.loc, it.type) }

        onPlayerCoordsChanged {
            if (player.coords == lastKnownCoords) {
                return@onPlayerCoordsChanged
            }
            if (player.coords in IkovCoords.BRIDGE_TILES) {
                steppedOntoBridge(player)
            }
            if (player.ikovLeverFitted && player.coords.level == IkovCoords.LEVER_BRACKET.level &&
                player.coords.chebyshevDistance(IkovCoords.LEVER_BRACKET) <= BRACKET_SIGHT) {
                restoreMendedLever()
            }
        }
    }

    /* The lever and its bracket */

    private suspend fun ProtectedAccess.fitLever(bracket: BoundLocInfo) {
        arriveDelay()
        if (player.ikovLeverFitted) {
            mes("There is already a lever in the bracket.")
            return
        }
        if (invDel(inv, TempleOfIkovQuest.LEVER).failure) {
            return
        }
        anim(FIT_SEQ)
        soundSynth(LEVER_SOUND)
        player.ikovLeverFitted = true
        locRepo.add(bracket.coords, MENDED_LEVER, LEVER_DURATION, bracket.angle, bracket.shape)
        mes("You fit the lever into the bracket.")
    }

    private suspend fun ProtectedAccess.pullMendedLever() {
        arriveDelay()
        anim(PULL_SEQ)
        soundSynth(LEVER_SOUND)
        delay(1)
        if (player.ikovGateUnlocked) {
            mes("You pull the lever, but nothing else happens.")
            return
        }
        player.ikovGateUnlocked = true
        soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
        mes("You hear a heavy bolt slide back somewhere to the south.")
    }

    /**
     * Puts the lever back on the bracket if its spawn has run out.
     *
     * The bracket carries no ops of its own, so a player whose lever has expired would have
     * nothing left to click and no second lever to fetch; walking back into sight of it is enough
     * to hang the lever up again.
     */
    private fun restoreMendedLever() {
        if (locRepo.findExact(IkovCoords.LEVER_BRACKET, mendedLeverType) != null) {
            return
        }
        val bracket = locRepo.findExact(IkovCoords.LEVER_BRACKET, bracketType) ?: return
        locRepo.add(bracket.coords, MENDED_LEVER, LEVER_DURATION, bracket.angle, bracket.shape)
    }

    /* The two pairs of gates */

    /** The Chamber of Fear. Only the pendant of Lucien carries anyone through it. */
    private suspend fun ProtectedAccess.chamberOfFear(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.z >= gate.coords.z
        if (leaving || quest.isComplete(player) || player.wearingPendantOfLucien()) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
        if (TempleOfIkovQuest.PENDANT_OF_LUCIEN in player.inv) {
            mes("The gate will not budge. The pendant is in your pack, not around your neck.")
            return
        }
        mes("A cold dread rolls off the gate and your hands refuse to touch it.")
        mes("Lucien said his pendant would carry you through the Chamber of Fear.")
    }

    /** The southern gates, bolted until the lever in the bracket is pulled. */
    private suspend fun ProtectedAccess.leverGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.z < gate.coords.z
        if (leaving || quest.isComplete(player) || player.ikovGateUnlocked) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
        mes("The gate is bolted from the other side.")
        mes("There is an empty lever bracket back east; perhaps it works this gate.")
    }

    /* The stairs down to the unlit rooms */

    private suspend fun ProtectedAccess.climbDarkStairs(down: Boolean) {
        arriveDelay()
        delay(1)
        val dest = if (down) IkovCoords.DARK_ROOM_LANDING else IkovCoords.DARK_STAIRS_LANDING
        telejump(dest, TeleportType.Exempt)
        if (down) {
            mes("The stairs take you down into the dark.")
        }
    }

    /* McGrubor's Wood */

    /**
     * The door in McGrubor's Wood. The key is needed to get into the fenced corner it shuts off;
     * anyone already inside can walk back out of it.
     */
    private suspend fun ProtectedAccess.shinyKeyDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.z <= door.coords.z
        if (!leaving && TempleOfIkovQuest.SHINY_KEY !in player.inv) {
            soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
            mes("The door is locked.")
            return
        }
        if (!leaving) {
            mes("You unlock the door with the shiny key.")
        }
        with(passages) { walkThrough(door, type) }
    }

    /* The lava bridge */

    private fun steppedOntoBridge(player: Player) {
        if (player.effectiveWeightGrams() <= TempleOfIkovQuest.BRIDGE_WEIGHT_LIMIT_GRAMS) {
            return
        }
        player.clearInteractionRoute()
        launcher.launch(player) { fallOffBridge() }
    }

    private suspend fun ProtectedAccess.fallOffBridge() {
        val bank =
            if (coords.x <= IkovCoords.BRIDGE_WEST_BANK.x + 1) IkovCoords.BRIDGE_WEST_BANK
            else IkovCoords.BRIDGE_EAST_BANK
        mes("The bridge creaks under your weight and pitches you towards the lava!")
        anim(FALL_SEQ)
        soundSynth(TempleOfIkovQuest.SOUND_LAVA_BRIDGE)
        delay(1)
        telejump(bank, TeleportType.Exempt)
        queueHit(player, delay = 0, type = HitType.Typeless, damage = bridgeDamage())
        mes("You scramble back off the bridge, singed.")
        mes("Something far lighter than you could cross that.")
    }

    private fun ProtectedAccess.bridgeDamage(): Int =
        TempleOfIkovQuest.BRIDGE_DAMAGE.coerceAtMost(player.hitpoints - 1).coerceAtLeast(0)

    private companion object {
        const val LEVER_BRACKET = "loc.ikov_leverbracket"
        const val MENDED_LEVER = "loc.ikov_mendedlever"
        const val FEAR_GATE_LEFT = "loc.ikov_dooroffearl"
        const val FEAR_GATE_RIGHT = "loc.ikov_dooroffearr"
        const val LEVER_GATE_LEFT = "loc.ikov_mendedleverdoorl"
        const val LEVER_GATE_RIGHT = "loc.ikov_mendedleverdoorr"
        const val DARK_STAIRS_DOWN = "loc.ikov_darkstairsdown"
        const val DARK_STAIRS_UP = "loc.ikov_darkstairs"
        const val SHINY_KEY_DOOR = "loc.ikov_shinykeydoor"

        /** Long enough that the fitted lever is still there when the player comes back for it. */
        const val LEVER_DURATION = 2000

        /** How near the bracket a player has to be for an expired lever to be hung back up. */
        const val BRACKET_SIGHT = 12

        const val FIT_SEQ = "seq.human_leverup"
        const val PULL_SEQ = "seq.human_leverdown"
        const val FALL_SEQ = "seq.human_pickupfloor"
        const val LEVER_SOUND = "synth.lever"
    }
}

/**
 * The weight the lava bridge judges the player by, in grams.
 *
 * Worn boots of lightness weigh -4.5kg in OSRS. The cache stores that on the equipped variant of
 * the item, which nothing in the engine swaps to, so the boots the player is actually wearing
 * still carry their carried weight; the difference is applied here.
 */
internal fun Player.effectiveWeightGrams(): Int {
    val grams = InvWeight.calculateWeightInGrams(this)
    return if (wearingBootsOfLightness()) grams + BOOTS_WORN_WEIGHT - BOOTS_CARRIED_WEIGHT
    else grams
}

internal fun Player.wearingBootsOfLightness(): Boolean =
    feet?.isType(TempleOfIkovQuest.BOOTS_OF_LIGHTNESS) == true

internal fun Player.wearingPendantOfLucien(): Boolean =
    front?.isType(TempleOfIkovQuest.PENDANT_OF_LUCIEN) == true

internal fun Player.wearingArmadylPendant(): Boolean =
    front?.isType(TempleOfIkovQuest.ARMADYL_PENDANT) == true

private const val BOOTS_CARRIED_WEIGHT = 340
private const val BOOTS_WORN_WEIGHT = -4535
