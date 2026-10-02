package org.rsmod.content.quest.area.tirannwn.mourningsend

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.hook.CombatStanceSelectHook
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BELLOWS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FIXED_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.RANGED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.RANGED_REQ
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_DEVICE_FIXED
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gnomish dye device and its ammunition.
 *
 * Dye on empty ogre bellows fills them (the dye is used up); the filled bellows inflate one swamp
 * toad of that colour (see the Big Chompy toad script). A coloured toad used on the device is
 * loaded into it: the loaded colour is the player's `varbit.mourning_gun_ammo`, which the aiming
 * interface's toad model reads, so one toad is loaded however many devices the player has. "Empty"
 * gives the toad back. The device needs 60 Ranged and a loaded toad to be wielded.
 *
 * Choosing "Aim and Fire" on the combat tab with the device wielded opens `interface.mourning_gun`
 * (five buttons and a close button; the client does no aiming of its own). The aim is a tile
 * offset from the player kept by this script, moved one tile per arrow and limited to
 * [MAX_RANGE]; the camera follows it. Firing is decided here, from the server's own aim, at the
 * moment the button is pressed: the toad is used up first, so a repeated fire packet only finds
 * an empty chamber, and whatever stands on the aimed tile is hit. A hit on one of Farmer Brumty's
 * flocks with the toad of its own colour, while the player is meant to be dyeing them, sets that
 * flock's flag for this player alone. Moving, logging out, death, or anything else that closes
 * the interface ends the aim; taking the device off or emptying it is caught on the next press.
 */
@Singleton
class FixedDevice
@Inject
constructor(
    private val mourning: MourningsEndQuest,
    private val worldRepo: WorldRepository,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    private val aims = HashMap<PlayerUid, Aim>()

    data class Aim(val dx: Int, val dz: Int)

    enum class Shot { EMPTY, MISS, WRONG_COLOUR, ALREADY_DYED, REDYED, SPLATTERED }

    override fun ScriptContext.startup() {
        for (flock in Flock.entries) {
            onOpHeldU(flock.dye, BELLOWS) { fillBellows(flock) }
            onOpHeldU(flock.toad, FIXED_DEVICE) { load(flock) }
        }
        onOpHeld3(FIXED_DEVICE) { empty() }
        onOpWorn2(FIXED_DEVICE) { empty() }
        onIfModalButton(LEFT) { move(-1, 0) }
        onIfModalButton(RIGHT) { move(1, 0) }
        onIfModalButton(UP) { move(0, 1) }
        onIfModalButton(DOWN) { move(0, -1) }
        onIfModalButton(FIRE) { fire() }
        onIfModalButton(CLOSE) { ifClose() }
        onIfClose(INTERFACE) { stop(player) }
        onPlayerLogout { aims.remove(player.uid) }
    }

    fun aimOf(player: Player): Aim? = aims[player.uid]

    fun aimTile(player: Player): CoordGrid? = aims[player.uid]?.let { player.coords.translate(it.dx, it.dz) }

    private suspend fun ProtectedAccess.fillBellows(flock: Flock) {
        if (!swap(listOf(flock.dye to 1, BELLOWS to 1), listOf(flock.bellows to 1))) {
            return
        }
        mes("You fill the ogre bellows with ${flock.label} dye.")
    }

    private fun ProtectedAccess.load(flock: Flock) {
        if (player.gunAmmo != 0) {
            mes("There is already a toad in the firing chamber.")
            return
        }
        if (invDel(inv, flock.toad, 1).failure) {
            return
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_gun_ammo", flock.ammo)
        mes("You put the dye-filled toad in to the firing chamber. To use the device, select the Aim and Fire mode from your Combat Options.")
    }

    private fun ProtectedAccess.empty() {
        val flock = Flock.ofAmmo(player.gunAmmo)
        if (flock == null) {
            mes("The device is empty.")
            return
        }
        if (inv.freeSpace() < 1 || invAdd(inv, flock.toad).failure) {
            mes("You don't have enough room to take the toad out.")
            return
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_gun_ammo", 0)
        mes("You take the ${flock.label} toad out of the device.")
    }

    fun ProtectedAccess.openAim() {
        if (!wieldsDevice(player)) {
            return
        }
        if (Flock.ofAmmo(player.gunAmmo) == null) {
            mes("The device is empty. Load it with a dye-filled toad first.")
            return
        }
        aims[player.uid] = Aim(0, START_RANGE)
        ifOpenMainModal(INTERFACE)
        for (button in listOf(LEFT, RIGHT, UP, DOWN, FIRE, CLOSE)) {
            ifSetEvents(button, 0..0, IfEvent.Op1)
        }
        point()
        mes("Use the arrows to aim at your target. Once you have it lined up, press the red 'Fire' button.")
    }

    fun ProtectedAccess.move(dx: Int, dz: Int) {
        val aim = aims[player.uid] ?: return
        if (!wieldsDevice(player)) {
            ifClose()
            return
        }
        val next = Aim((aim.dx + dx).coerceIn(-MAX_RANGE, MAX_RANGE), (aim.dz + dz).coerceIn(-MAX_RANGE, MAX_RANGE))
        aims[player.uid] = next
        point()
    }

    private fun ProtectedAccess.point() {
        val tile = aimTile(player) ?: return
        camMoveTo(tile.translate(0, -CAMERA_BACK), CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
        camLookAt(tile, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
    }

    suspend fun ProtectedAccess.fire(): Shot? {
        val target = aimTile(player) ?: return null
        if (!wieldsDevice(player)) {
            ifClose()
            return null
        }
        val loaded = Flock.ofAmmo(player.gunAmmo)
        if (loaded == null) {
            mes("The device is empty.")
            return Shot.EMPTY
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_gun_ammo", 0)
        val shot = resolve(player, loaded, target)
        anim(FIRE_SEQ)
        val flight = worldRepo.projAnim(player, target, SpotanimType(loaded.travel.asRSCM(RSCMType.SPOTANIM)), PROJANIM)
        ifClose()
        report(shot, loaded)
        delay((flight.serverCycles).coerceAtLeast(1))
        worldRepo.spotanimMap(SpotanimType(loaded.impact.asRSCM(RSCMType.SPOTANIM)), target)
        return shot
    }

    /** What the toad fired at [target] does; flags are set here, in the tick of the shot. */
    fun resolve(player: Player, loaded: Flock, target: CoordGrid): Shot {
        val npc = npcRepo.findAll(target).firstOrNull { Flock.ofField(it.id) != null }
        val flock = npc?.let { Flock.ofField(it.id) } ?: return if (npcRepo.findAll(target).any()) Shot.SPLATTERED else Shot.MISS
        if (mourning.stage(player) != STAGE_DEVICE_FIXED) {
            return Shot.SPLATTERED
        }
        if (mourning.sheepDone(player, flock)) {
            return Shot.ALREADY_DYED
        }
        if (flock != loaded) {
            return Shot.WRONG_COLOUR
        }
        MourningsEndQuest.setVarBit(player, flock.varbit, 1)
        return Shot.REDYED
    }

    private fun ProtectedAccess.report(shot: Shot, loaded: Flock) {
        when (shot) {
            Shot.REDYED -> mes("You re-dye the ${loaded.label} sheep.")
            Shot.ALREADY_DYED -> mes("Those sheep have already been re-dyed.")
            Shot.WRONG_COLOUR -> mes("The ${loaded.label} dye won't stay on sheep of a different colour. You'll need a toad of the right colour.")
            Shot.SPLATTERED -> mes("The toad bursts, splattering ${loaded.label} dye everywhere.")
            Shot.MISS -> mes("You miss, and the toad bursts on the ground.")
            Shot.EMPTY -> Unit
        }
    }

    fun stop(player: Player) {
        if (aims.remove(player.uid) != null) {
            Camera.camReset(player)
        }
    }

    private fun wieldsDevice(player: Player): Boolean =
        player.worn[Wearpos.RightHand.slot]?.id == FIXED_DEVICE.asRSCM(RSCMType.OBJ)

    companion object {
        const val INTERFACE = "interface.mourning_gun"
        const val LEFT = "component.mourning_gun:left"
        const val RIGHT = "component.mourning_gun:right"
        const val UP = "component.mourning_gun:up"
        const val DOWN = "component.mourning_gun:down"
        const val FIRE = "component.mourning_gun:fire"
        const val CLOSE = "component.mourning_gun:close"

        const val START_RANGE = 4
        const val MAX_RANGE = 10
        const val CAMERA_BACK = 6
        const val CAMERA_HEIGHT = 900
        const val LOOK_HEIGHT = 0
        const val CAMERA_RATE = 100

        const val FIRE_SEQ = "seq.human_toadcannon_fire"
        const val PROJANIM = "projanim.thrown"
    }
}

/** "Aim and Fire" is the device's first style; picking it opens the aiming interface. */
class FixedDeviceStanceHook
@Inject
constructor(private val device: FixedDevice, private val launcher: ProtectedAccessLauncher) : CombatStanceSelectHook {
    private val deviceId by lazy { FIXED_DEVICE.asRSCM(RSCMType.OBJ) }

    override fun onSelect(player: Player, weapon: ItemServerType?, stance: CombatStance) {
        if (weapon?.id != deviceId || stance != CombatStance.Stance1) {
            return
        }
        launcher.launch(player) { with(device) { openAim() } }
    }
}

/** The device rests on the shoulder only with a toad in it, and needs 60 Ranged. */
class FixedDeviceWearHook @Inject constructor() : PlayerRestrictionHook {
    private val deviceId by lazy { FIXED_DEVICE.asRSCM(RSCMType.OBJ) }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id != deviceId) {
            return null
        }
        if (player.stat(RANGED) < RANGED_REQ) {
            return "You need a Ranged level of $RANGED_REQ to wield this."
        }
        if (player.gunAmmo == 0) {
            return "You need to load the device with a dye-filled toad before you can wield it."
        }
        return null
    }
}
