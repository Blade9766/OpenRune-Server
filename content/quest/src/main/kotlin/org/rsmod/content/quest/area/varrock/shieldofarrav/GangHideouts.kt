package org.rsmod.content.quest.area.varrock.shieldofarrav

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.CROSSBOW
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.WEAPON_STORE_KEY
import org.rsmod.content.quest.area.varrock.shieldofarrav.npcs.STRAVEN
import org.rsmod.content.quest.area.varrock.shieldofarrav.npcs.WEAPONSMASTER
import org.rsmod.content.quest.area.varrock.shieldofarrav.npcs.straven
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two gangs' hideouts: the three locked doors, the Phoenix Gang's chest and the Black Arm
 * Gang's cupboard that hide the halves of the shield, and the crossbows in the Phoenix weapon
 * store that the Weaponsmaster guards.
 *
 * Every door lets anyone inside back out; only gang members (or a weapon store key) get in, and
 * each door shuts behind the player so nobody can follow them through.
 */
class GangHideouts
@Inject
constructor(
    private val arrav: ShieldOfArravQuest,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val crossbowType by lazy {
        ServerCacheManager.getItem(CROSSBOW.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $CROSSBOW")
    }

    override fun ScriptContext.startup() {
        onOpLoc1(PHOENIX_DOOR) { phoenixDoor(it.vis, it.type) }
        onOpLoc1(WEAPON_STORE_DOOR) { weaponStoreDoor(it.vis, it.type, keyUsed = false) }
        onOpLocU(WEAPON_STORE_DOOR, WEAPON_STORE_KEY) { weaponStoreDoor(it.vis, it.type, keyUsed = true) }
        onOpLoc1(BLACKARM_DOOR) { blackArmDoor(it.vis, it.type) }
        onOpLoc1(PHOENIX_CHEST) { searchChest(it.loc) }
        onOpLoc1(BLACKARM_CUPBOARD) { searchCupboard(it.loc) }
        onOpObj3(crossbowType) { takeCrossbow(it.obj) }
    }

    /* Doors */

    private suspend fun ProtectedAccess.phoenixDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.z <= door.coords.z
        if (leaving || arrav.isPhoenix(player)) {
            if (!leaving) mes("The door automatically opens for you.")
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        val straven = npcFind(coords, STRAVEN, STRAVEN_RANGE, HuntVis.Off, search)
        if (straven == null) {
            mes("The door is securely locked.")
            return
        }
        startDialogue(straven) { straven(arrav, atDoor = true) }
    }

    private suspend fun ProtectedAccess.weaponStoreDoor(
        door: BoundLocInfo,
        type: ObjectServerType,
        keyUsed: Boolean,
    ) {
        val leaving = coords.z < door.coords.z
        if (leaving) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (!keyUsed && inv.count(WEAPON_STORE_KEY) == 0) {
            arriveDelay()
            mesbox("The door is securely locked.")
            return
        }
        soundSynth(UNLOCK_SOUND)
        mes("You unlock the door.")
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.blackArmDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.z >= door.coords.z
        if (leaving || arrav.isBlackArm(player)) {
            if (!leaving) mes("You hear heavy bolts being drawn back as the door is unlocked.")
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        soundSynth(LOCKED_SOUND)
        if (arrav.isPhoenix(player)) {
            mesbox("The door is securely locked.")
        } else {
            mes("This door seems to be locked from the inside.")
        }
    }

    /* The shield halves */

    private suspend fun ProtectedAccess.searchChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_CHEST_SEQ)
        soundSynth(CHEST_OPEN_SOUND)
        mesbox("You search the chest.")
        findShieldHalf(PHOENIX_SHIELD, "chest")
        locRepo.change(chest, PHOENIX_CHEST_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.searchCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_CUPBOARD_SEQ)
        soundSynth(CUPBOARD_OPEN_SOUND)
        findShieldHalf(BLACKARM_SHIELD, "cupboard")
        locRepo.change(cupboard, BLACKARM_CUPBOARD_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.findShieldHalf(half: String, container: String) {
        val taken = arrav.isComplete(player) || arrav.owns(this, half) || player.shieldGiven != 0
        if (taken || !arrav.isInProgress(player)) {
            mesbox("You open the $container, but don't find anything of interest inside.")
            return
        }
        if (inv.isFull()) {
            objbox(
                half,
                "You open the $container and find half of a shield inside, but you don't have " +
                    "enough room to take it.",
            )
            return
        }
        invAdd(inv, half)
        objbox(half, "You open the $container and find half of a shield inside.")
        if (half == BLACKARM_SHIELD) {
            startDialogue {
                chatPlayer(
                    happy,
                    "This must be the Shield of Arrav! Well... half of it, anyway. I wonder who " +
                        "has the rest.",
                )
            }
        }
    }

    /* The weapon store */

    private suspend fun ProtectedAccess.takeCrossbow(obj: Obj) {
        val guard =
            if (player.weaponsmasterDead) {
                null
            } else {
                npcFind(coords, WEAPONSMASTER, WEAPONSMASTER_RANGE, HuntVis.Off, search)
            }
        if (guard != null) {
            if (arrav.isPhoenix(player)) {
                startDialogue(guard) {
                    chatNpc(angry, "Oi! Those belong to Straven. He won't take kindly to you touching them!")
                }
                return
            }
            startDialogue(guard) { chatNpc(angry, "Stop! Thief!") }
            guard.opPlayer2(player, aiInteractions)
            return
        }
        if (inv.isFull()) {
            mes(constants.dm_take_invspace)
            return
        }
        if (coords != obj.coords) {
            delay(1)
            anim(PICKUP_SEQ)
        }
        soundSynth(PICKUP_SOUND)
        if (!objRepo.del(obj)) {
            return
        }
        invAdd(inv, CROSSBOW)
    }

    private companion object {
        const val PHOENIX_DOOR = "loc.phoenixdoor"
        const val WEAPON_STORE_DOOR = "loc.phoenixdoor2"
        const val BLACKARM_DOOR = "loc.blackarmdoor"
        const val PHOENIX_CHEST = "loc.phoenixshutchest"
        const val PHOENIX_CHEST_OPEN = "loc.phoenixopenchest"
        const val BLACKARM_CUPBOARD = "loc.blackarmcupboardshut"
        const val BLACKARM_CUPBOARD_OPEN = "loc.blackarmcupboardopen"

        const val STRAVEN_RANGE = 3
        const val WEAPONSMASTER_RANGE = 10
        const val OPEN_TICKS = 5

        const val OPEN_CHEST_SEQ = "seq.human_openchest"
        const val OPEN_CUPBOARD_SEQ = "seq.human_opencupboard"
        const val PICKUP_SEQ = "seq.human_pickuptable"

        const val UNLOCK_SOUND = "synth.unlock"
        const val LOCKED_SOUND = "synth.locked"
        const val CHEST_OPEN_SOUND = "synth.chest_open"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val PICKUP_SOUND = "synth.pick2"
    }
}
