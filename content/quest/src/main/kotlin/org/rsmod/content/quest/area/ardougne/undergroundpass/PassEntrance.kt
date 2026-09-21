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
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.OILY_CLOTH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ROPE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BOW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BOX_LEVER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_DROWNING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_ARROW_LAUNCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_BRIDGE_FALL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_FIRE_ARROW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEVER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_SWAMP_STEP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_ENTERED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.TINDERBOX
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mouth of the pass in West Ardougne, the first cavern behind it, and the bridge Iban's people
 * raised when they sealed themselves in.
 *
 * The swamp in the middle of the cavern drags anyone who wades in down into the crevasse below.
 * Koftik keeps a fire going by the bridge and gives the player a damp cloth he found among some
 * charred arrows: wrapped round an arrow, lit and fired from a bow, it burns through the guide
 * rope holding the bridge up. The lever on the far bank lowers it again for the way back.
 */
@Singleton
class PassEntrance
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val koftik: Koftik,
) : PluginScript() {

    private val bridgeType by lazy { locType(BRIDGE_UP) }
    private val fallingBridgeType by lazy { locType(BRIDGE_FALLING) }
    private val leverDownType by lazy { locType(LEVER_DOWN) }

    override fun ScriptContext.startup() {
        onOpLoc1(CAVE_ENTRANCE) { enterPass() }
        onOpLoc1(CAVE_EXIT) { leavePass() }
        onOpLoc1(NOGO_CAVE) { mes("The tunnel is packed solid with fallen rock a few feet in.") }

        onOpLoc1(ABANDONED_GEAR) { searchGear() }
        onOpLoc1(ROPE_CRATE) { searchRopeCrate() }
        onOpLoc1(SWAMP) { crossSwamp() }
        onOpLoc1(MUD_PILE) { climbMudPile() }

        for ((arrow, forms) in UndergroundPassQuest.ARROW_PAIRS) {
            val (unlit, lit) = forms
            onOpHeldU(OILY_CLOTH, arrow) { wrapArrow(arrow, unlit) }
            onOpHeldU(TINDERBOX, unlit) { lightArrow(unlit, lit) }
            onOpLocU(FIRE, unlit) { lightArrow(unlit, lit) }
        }

        onApLoc1(GUIDE_ROPE) {
            if (isWithinApRange(it.loc, ROPE_SHOT_RANGE)) {
                fireAtRope(it.loc)
            }
        }
        onOpLoc1(GUIDE_ROPE) { fireAtRope(it.loc) }
        onOpLoc1(BRIDGE_LEVER) { pullBridgeLever(it.loc) }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.enterPass() {
        arriveDelay()
        if (!quest.isStarted(player)) {
            mes("You must talk to King Lathas before you can enter.")
            return
        }
        if (quest.stage(player) == STAGE_STARTED) {
            with(koftik) { meetOutside() }
            return
        }
        mes("You cautiously enter the cave...")
        delay(3)
        telejump(UpassCoords.PASS_ARRIVAL)
    }

    private suspend fun ProtectedAccess.leavePass() {
        arriveDelay()
        mes("You leave the underground pass.")
        delay(3)
        telejump(UpassCoords.CAVE_EXIT_LANDING)
    }

    private suspend fun ProtectedAccess.searchGear() {
        arriveDelay()
        anim(SEQ_SEARCH)
        mes("You search the abandoned equipment...")
        delay(2)
        if (inv.contains(OILY_CLOTH) || quest.stage(player) >= STAGE_BRIDGE) {
            mes("...but find nothing of use.")
            return
        }
        if (invAdd(inv, OILY_CLOTH).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("...and find a damp cloth among some charred arrows.")
    }

    private suspend fun ProtectedAccess.searchRopeCrate() {
        arriveDelay()
        anim(SEQ_SEARCH)
        mes("You search the crate...")
        delay(2)
        if (invAdd(inv, ROPE).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("...and find a coil of rope.")
    }

    private suspend fun ProtectedAccess.crossSwamp() {
        arriveDelay()
        soundSynth(SOUND_SWAMP_STEP)
        mes("You try to cross the swamp...")
        delay(1)
        mes("The swamp seems to cling to your legs.")
        delay(1)
        mes("You feel yourself being slowly dragged below...")
        say("Gulp!")
        setWalkStyle(SEQ_DROWNING)
        try {
            delay(2)
        } finally {
            clearWalkStyle()
        }
        mes("You tumble deep into the crevasse.")
        mes("You land battered and bruised at the base.")
        telejump(UpassCoords.CREVASSE_FLOOR, TeleportType.Exempt)
        say("Aargh!")
        takeInstantHit(HitType.Typeless, stat("stat.hitpoints") * SWAMP_DAMAGE_PERCENT / 100)
    }

    private suspend fun ProtectedAccess.climbMudPile() {
        arriveDelay()
        mes("You climb up the pile of mud...")
        delay(3)
        telejump(UpassCoords.ROCKPILE_TOP)
        mes("It leads into darkness, the stench is unbearable.")
        mes("You surface by the swamp, covered in muck.")
    }

    private fun ProtectedAccess.wrapArrow(arrow: String, unlit: String) {
        if (inv.count(arrow) > 1 && inv.freeSpace() == 0 && !inv.contains(unlit)) {
            mes("You don't have space to do that.")
            return
        }
        if (invDel(inv, OILY_CLOTH).failure || invDel(inv, arrow).failure) {
            return
        }
        invAdd(inv, unlit)
        mes("You wrap the damp cloth around the arrow head.")
    }

    private fun ProtectedAccess.lightArrow(unlit: String, lit: String) {
        if (inv.count(unlit) > 1 && inv.freeSpace() == 0 && !inv.contains(lit)) {
            return
        }
        if (invDel(inv, unlit).failure) {
            return
        }
        invAdd(inv, lit)
        soundSynth(SOUND_FIRE_ARROW)
        mes("You light the cloth wrapped arrow head.")
    }

    /**
     * The rope has to be shot from the far bank, north of the chasm, with a lit arrow loaded in
     * the quiver. When it parts the bridge swings down and the player runs round to it and over.
     */
    private suspend fun ProtectedAccess.fireAtRope(rope: BoundLocInfo) {
        if (coords.x < rope.coords.x) {
            mes("You don't need to shoot the bridge from this side.")
            return
        }
        if (coords.z < UpassCoords.ROPE_SHOT_MIN_Z) {
            mes("You can't get a clear shot from here.")
            return
        }
        val weapon = player.worn[Wearpos.RightHand.slot]
        val weaponType = weapon?.let { ServerCacheManager.getItem(it.id) }
        if (weaponType?.weaponCategory != WeaponCategory.Bow) {
            mes("You'll need to equip a bow before you can fire arrows at the rope.")
            return
        }
        val ammo = player.worn[Wearpos.Quiver.slot]
        val lit = ammo?.let { obj -> LIT_ARROWS.firstOrNull { it.asRSCM(RSCMType.OBJ) == obj.id } }
        if (lit == null) {
            mes("You need something to fire that will make the bridge drop.")
            return
        }
        invDel(worn, lit)
        faceSquare(rope.coords)
        mes("You fire your arrow at the rope supporting the bridge...")
        anim(SEQ_BOW)
        soundSynth(SOUND_ARROW_LAUNCH)
        delay(1)
        if (!statRandom("stat.ranged", RANGED_LOW, RANGED_HIGH, 0)) {
            mes("The arrow just misses the rope.")
            return
        }
        soundSynth(SOUND_FIRE_ARROW)
        mes("The arrow impales the rope support.")
        for (tile in UpassCoords.ROPE_SHOT_WALK) {
            playerRun(tile)
        }
        delay(1)
        mes("The bridge falls.")
        mes("You rush across the bridge.")
        soundSynth(SOUND_BRIDGE_FALL)
        lowerBridge(ROPE_BRIDGE_TICKS)
        delay(3)
        telejump(UpassCoords.BRIDGE_WEST, TeleportType.Exempt)
        player.foundBridge = 1
        if (quest.stage(player) == STAGE_ENTERED) {
            quest.advanceTo(this, STAGE_BRIDGE)
        }
    }

    private suspend fun ProtectedAccess.pullBridgeLever(lever: BoundLocInfo) {
        arriveDelay()
        mes("You pull the old lever...")
        anim(SEQ_BOX_LEVER)
        soundSynth(SOUND_LEVER)
        locRepo.change(lever, leverDownType, LEVER_TICKS)
        stepThrough(lineTo(UpassCoords.BRIDGE_LEVER_STAND))
        delay(2)
        stepThrough(listOf(UpassCoords.BRIDGE_LEVER_STAND.translate(1, 1)))
        lowerBridge(LEVER_BRIDGE_TICKS)
        stepThrough(lineTo(UpassCoords.BRIDGE_WEST))
        delay(1)
        mes("You cross the lowered bridge.")
        telejump(UpassCoords.BRIDGE_EAST, TeleportType.Exempt)
    }

    private fun lowerBridge(ticks: Int) {
        locRepo.findExact(UpassCoords.BRIDGE, bridgeType)?.let {
            locRepo.change(it, fallingBridgeType, ticks)
        }
    }

    private companion object {
        const val CAVE_ENTRANCE = "loc.upass_caveentrance2"
        const val CAVE_EXIT = "loc.cave_exit_upass"
        const val NOGO_CAVE = "loc.cavewalltunnel_upass_nogo"
        const val ABANDONED_GEAR = "loc.upass_gear"
        const val ROPE_CRATE = "loc.upass_crate_rope"
        const val SWAMP = "loc.upass_swampbubbles1"
        const val MUD_PILE = "loc.caverockpile"
        const val GUIDE_ROPE = "loc.oldbridge_guiderope"
        const val BRIDGE_UP = "loc.old_bridge_up"
        const val BRIDGE_FALLING = "loc.old_bridge_animated"
        const val BRIDGE_LEVER = "loc.upass_lever_up"
        const val LEVER_DOWN = "loc.upass_lever_down"
        const val FIRE = "loc.fire"

        const val ROPE_SHOT_RANGE = 10
        const val RANGED_LOW = 90
        const val RANGED_HIGH = 300
        const val ROPE_BRIDGE_TICKS = 8
        const val LEVER_BRIDGE_TICKS = 5
        const val LEVER_TICKS = 4
        const val SWAMP_DAMAGE_PERCENT = 15

        val LIT_ARROWS = UndergroundPassQuest.ARROW_PAIRS.map { it.second.second }
    }
}
