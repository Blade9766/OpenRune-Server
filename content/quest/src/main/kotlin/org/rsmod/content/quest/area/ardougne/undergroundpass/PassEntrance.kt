package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.OILY_CLOTH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ROPE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_ARROW_LAUNCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_BRIDGE_FALL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_FIRE_ARROW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEVER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_SWAMP_STEP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mouth of the pass in West Ardougne, the first cavern behind it, and the bridge Iban's people
 * cut when they sealed themselves in.
 *
 * The swamp filling the middle of the cavern is a trap: anything that walks into it is pulled
 * under and washed up back at the entrance. The way round is the line of rockslides to the north.
 * Past them Koftik keeps a fire going, and the only thing left of the party that came before him
 * is an oily cloth in the abandoned equipment. Wrapped round an arrow, lit at his fire and shot
 * into the guide rope, that cloth brings the bridge down.
 */
@Singleton
class PassEntrance
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
) : PluginScript() {

    private val guideRopeType by lazy { locType(GUIDE_ROPE) }
    private val cutRopeType by lazy { locType(ROPE_CUT) }
    private val bridgeType by lazy { locType(BRIDGE_UP) }

    override fun ScriptContext.startup() {
        onOpLoc1(CAVE_ENTRANCE) { enterPass() }
        onOpLoc1(CAVE_EXIT) { leavePass() }
        onOpLoc1(NOGO_CAVE) { mes("The tunnel is packed solid with fallen rock a few feet in.") }

        onOpLoc1(ABANDONED_GEAR) { searchGear() }
        onOpLoc1(ROPE_CRATE) { searchRopeCrate() }
        onOpLoc1(SWAMP) { crossSwamp() }

        for ((arrow, forms) in UndergroundPassQuest.ARROW_PAIRS) {
            val (unlit, lit) = forms
            onOpHeldU(OILY_CLOTH, arrow) { wrapArrow(arrow, unlit) }
            onOpLocU(FIRE, unlit) { lightArrow(it.loc.coords, unlit, lit) }
        }

        onOpLoc1(GUIDE_ROPE) { fireAtRope() }
        onOpLoc1(BRIDGE_LEVER) { pullBridgeLever() }

        onPlayerCoordsChanged { crossBridge(player) }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.enterPass() {
        arriveDelay()
        if (!quest.isStarted(player)) {
            mesbox(
                "A cold draught comes up out of that hole, and something in it smells like a " +
                    "battlefield. I'm not going down there without a reason.",
            )
            return
        }
        mes("You climb down into the darkness.")
        delay(1)
        telejump(UpassCoords.PASS_ARRIVAL)
        if (quest.stage(player) == STAGE_STARTED && player.koftikChat == 0) {
            mes("<col=800000>There is firelight somewhere away to the west.</col>")
        }
    }

    private suspend fun ProtectedAccess.leavePass() {
        arriveDelay()
        mes("You climb back up into the daylight.")
        delay(1)
        telejump(UpassCoords.CAVE_ENTRANCE_STEP)
    }

    private suspend fun ProtectedAccess.searchGear() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (inv.contains(OILY_CLOTH)) {
            mes("You already have an oily cloth.")
            return
        }
        if (invAdd(inv, OILY_CLOTH).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        player.clothTaken = true
        mesbox(
            "Among the rusted packs is a cloth, stiff with old lamp oil. Wrapped round an " +
                "arrowhead it would burn for a good while.",
        )
    }

    private suspend fun ProtectedAccess.searchRopeCrate() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (invAdd(inv, ROPE).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("You find a coil of rope in the crate.")
    }

    /**
     * The swamp is not a crossing. Wading in drags the player under and washes them up on the bank
     * by the cave mouth; the way past is the rockslides along the north wall.
     */
    private suspend fun ProtectedAccess.crossSwamp() {
        arriveDelay()
        soundSynth(SOUND_SWAMP_STEP)
        mes("You wade out into the swamp...")
        delay(1)
        takeInstantHit(HitType.Typeless, random.of(SWAMP_MIN_DAMAGE, SWAMP_MAX_DAMAGE))
        mes("Something under the surface takes hold of you and pulls you down.")
        delay(2)
        telejump(UpassCoords.SWAMP_SPIT_OUT, TeleportType.Exempt)
        mesbox(
            "You are spat out on the bank, coughing. Whatever lives in that swamp does not want " +
                "company; there must be a way round it.",
        )
    }

    private suspend fun ProtectedAccess.wrapArrow(arrow: String, unlit: String) {
        if (invDel(inv, arrow).failure) {
            return
        }
        invDel(inv, OILY_CLOTH)
        anim(SEQ_SEARCH)
        delay(1)
        invAdd(inv, unlit)
        mes("You wrap the oily cloth tightly round the arrow's head.")
    }

    private suspend fun ProtectedAccess.lightArrow(
        fire: org.rsmod.map.CoordGrid,
        unlit: String,
        lit: String,
    ) {
        if (fire != UpassCoords.KOFTIK_FIRE) {
            mes("This fire is nowhere near the bridge. The cloth would burn out long before.")
            return
        }
        arriveDelay()
        if (invDel(inv, unlit).failure) {
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(SOUND_FIRE_ARROW)
        delay(1)
        invAdd(inv, lit)
        mes("You hold the wrapped arrow in Koftik's fire until the cloth catches.")
    }

    private suspend fun ProtectedAccess.fireAtRope() {
        arriveDelay()
        val lit = UndergroundPassQuest.ARROW_PAIRS.map { it.second.second }
            .firstOrNull { inv.contains(it) }
        if (lit == null) {
            mesbox(
                "The rope is too far to reach and far too thick to cut. If it could be set " +
                    "alight from here it would part on its own.",
            )
            return
        }
        if (!hasBowEquipped()) {
            mes("I need a bow to shoot that far.")
            return
        }
        faceSquare(UpassCoords.GUIDE_ROPE)
        anim(BOW_SEQ)
        soundSynth(SOUND_ARROW_LAUNCH)
        invDel(inv, lit)
        delay(2)
        locRepo.findExact(UpassCoords.GUIDE_ROPE, guideRopeType)?.let {
            locRepo.change(it, cutRopeType, LOC_DURATION)
        }
        soundSynth(SOUND_FIRE_ARROW)
        mesbox("The arrow buries itself in the guide rope and the oily cloth takes hold.")
        delay(3)
        soundSynth(SOUND_BRIDGE_FALL)
        locRepo.findExact(UpassCoords.BRIDGE, bridgeType)?.let { locRepo.del(it, LOC_DURATION) }
        player.foundBridge = 1
        quest.advanceTo(this, STAGE_BRIDGE)
        mesbox(
            "The rope parts with a crack and the whole span comes down across the chasm. It will " +
                "hold long enough to walk over.",
        )
    }

    /**
     * The winch on the near bank pulls the span back up. It is the one thing in the cavern that
     * can undo the fire arrow, so it asks first.
     */
    private suspend fun ProtectedAccess.pullBridgeLever() {
        arriveDelay()
        if (player.foundBridge == 0) {
            soundSynth(SOUND_LEVER)
            anim(LEVER_SEQ)
            delay(1)
            mes("The winch turns, but there is nothing left on the other end of it to pull.")
            return
        }
        val choice = menu("Winch the bridge back up?", "Yes.", "No.", hotkeys = true)
        ifClose()
        if (choice != 1) {
            return
        }
        soundSynth(SOUND_LEVER)
        anim(LEVER_SEQ)
        delay(2)
        locRepo.findExact(UpassCoords.GUIDE_ROPE, cutRopeType)?.let {
            locRepo.change(it, guideRopeType, LOC_DURATION)
        }
        locRepo.add(UpassCoords.BRIDGE, bridgeType, LOC_DURATION, BRIDGE_ANGLE, BRIDGE_SHAPE)
        player.foundBridge = 0
        mesbox("The span grinds back up into the roof. You will have to burn the rope again.")
    }

    /**
     * With the span down the chasm is crossed by stepping onto the lip of it from either bank.
     * The fallen bridge is scenery with no option on it, so the tiles either side do the work.
     */
    private fun crossBridge(player: Player) {
        if (player.foundBridge == 0) {
            return
        }
        val dest =
            when (player.coords) {
                in UpassCoords.BRIDGE_WEST_EDGE -> UpassCoords.BRIDGE_EAST_LANDING
                in UpassCoords.BRIDGE_EAST_EDGE -> UpassCoords.BRIDGE_WEST_LANDING
                else -> return
            }
        launcher.launch(player) {
            mes("You pick your way across the fallen bridge.")
            climbOver(dest, WALK_SEQ, BRIDGE_CROSS_TICKS)
        }
    }

    private fun ProtectedAccess.hasBowEquipped(): Boolean {
        val weapon = player.worn[Wearpos.RightHand.slot] ?: return false
        val type = ServerCacheManager.getItem(weapon.id) ?: return false
        return type.weaponCategory == WeaponCategory.Bow
    }

    private companion object {
        const val CAVE_ENTRANCE = "loc.upass_caveentrance2"
        const val CAVE_EXIT = "loc.cave_exit_upass"
        const val NOGO_CAVE = "loc.cavewalltunnel_upass_nogo"
        const val ABANDONED_GEAR = "loc.upass_gear"
        const val ROPE_CRATE = "loc.upass_crate_rope"
        const val SWAMP = "loc.upass_swampbubbles1"
        const val GUIDE_ROPE = "loc.oldbridge_guiderope"
        const val ROPE_CUT = "loc.oldbridge_guiderope_cut"
        const val BRIDGE_UP = "loc.old_bridge_up"
        const val BRIDGE_LEVER = "loc.upass_lever_up"
        const val FIRE = "loc.fire"

        const val LOC_DURATION = Int.MAX_VALUE
        const val BRIDGE_CROSS_TICKS = 3
        const val SWAMP_MIN_DAMAGE = 3
        const val SWAMP_MAX_DAMAGE = 9

        val BRIDGE_ANGLE = LocAngle.South
        val BRIDGE_SHAPE = LocShape.CentrepieceStraight

        const val BOW_SEQ = "seq.human_bow"
        const val LEVER_SEQ = "seq.human_leverdown"
        const val WALK_SEQ = "seq.human_walk_f"
    }
}
