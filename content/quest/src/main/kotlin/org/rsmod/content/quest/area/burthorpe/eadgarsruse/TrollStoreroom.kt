package org.rsmod.content.quest.area.burthorpe.eadgarsruse

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GOUTWEED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COOK_FOOLED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_FETCH_PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_HIDDEN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_TRAINED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PLAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STOREROOM
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STOREROOM_GUARD
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STOREROOM_KEY
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The troll prison's torture rack, where the parrot learns to scream, and Burntmeat's kitchen
 * drawers, storeroom door and goutweed crates.
 *
 * The guards' patrol through the storeroom is not modelled tile by tile: each search of a crate is
 * a roll against being spotted. A spotted player takes a thrown rock and is put back outside the
 * door; an unspotted one comes away with a goutweed.
 */
class TrollStoreroom
@Inject
constructor(
    private val eadgarsRuse: EadgarsRuseQuest,
    private val passages: GenericPassageScript,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val storeroomDoor: ObjectServerType by lazy {
        ServerCacheManager.getObject(STOREROOM_DOOR.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $STOREROOM_DOOR")
    }

    override fun ScriptContext.startup() {
        onOpLoc1(RACK) { searchRack() }
        onOpLocU(RACK, PARROT) { hideParrot() }
        onOpLoc1(DRAWERS) { searchDrawers() }
        onOpLoc1(STOREROOM_DOOR) { storeroomDoor(it.loc) }
        onOpLocU(STOREROOM_DOOR, STOREROOM_KEY) { storeroomDoor(it.loc) }
        onOpLoc1(GOUTWEED_CRATE) { searchCrate() }
    }

    /* The rack */

    internal suspend fun ProtectedAccess.hideParrot() {
        arriveDelay()
        if (eadgarsRuse.stage(player) != STAGE_PLAN) {
            mes("Why would you want to do that?")
            return
        }
        invDel(inv, PARROT)
        eadgarsRuse.advanceTo(this, STAGE_PARROT_HIDDEN)
        mes("You hide the parrot under the torture rack.")
    }

    internal suspend fun ProtectedAccess.searchRack() {
        arriveDelay()
        val stage = eadgarsRuse.stage(player)
        when {
            stage in STAGE_PARROT_HIDDEN until STAGE_FETCH_PARROT -> {
                mesbox("You look under the rack and find the drunk parrot.")
                startDialogue {
                    chatNpcSpecific("Parrot", PARROT_NPC, drunk, PARROT_LINES[access.random.of(PARROT_LINES.size)])
                    chatPlayer(neutral, "I don't think it's done yet.")
                }
            }
            stage == STAGE_FETCH_PARROT -> {
                mesbox("You look under the rack and find the drunk parrot.")
                startDialogue {
                    chatNpcSpecific(
                        "Parrot",
                        PARROT_NPC,
                        drunk,
                        "Ah, hello Sir. Could you please free me? I seem to have ... OW! What are you " +
                            "doing? That's my spleen!",
                    )
                    chatPlayer(happy, "I think it's probably heard enough.")
                }
                invAddOrDrop(objRepo, PARROT)
                eadgarsRuse.advanceTo(this, STAGE_PARROT_TRAINED)
            }
            else -> mesbox("You look under the rack but find nothing.")
        }
    }

    /* The kitchen */

    internal suspend fun ProtectedAccess.searchDrawers() {
        arriveDelay()
        mes("You search the drawers...")
        val knowsOfKey = eadgarsRuse.stage(player) >= STAGE_COOK_FOOLED || eadgarsRuse.unlocked(player)
        if (!knowsOfKey || player.ownsAnywhere(STOREROOM_KEY)) {
            mes("You don't find anything.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You open the fake bottom of the drawer and see a key, but don't have space to take it.")
            return
        }
        invAdd(inv, STOREROOM_KEY)
        mes("You open the fake bottom of the drawer and find the storeroom key.")
    }

    /* The storeroom */

    private suspend fun ProtectedAccess.storeroomDoor(door: BoundLocInfo) {
        arriveDelay()
        if (!unlockStoreroom(door)) {
            return
        }
        with(passages) { walkThrough(door, storeroomDoor) }
    }

    /** Whether the player may pass [door]: always on the way out, otherwise only with the key. */
    internal fun ProtectedAccess.unlockStoreroom(door: BoundLocInfo): Boolean {
        val leaving = coords.z >= door.coords.z
        if (leaving) {
            return true
        }
        if (STOREROOM_KEY !in inv) {
            soundSynth(LOCKED_SOUND)
            mes("This door is locked.")
            return false
        }
        if (eadgarsRuse.stage(player) == STAGE_COOK_FOOLED) {
            eadgarsRuse.advanceTo(this, STAGE_STOREROOM)
        }
        soundSynth(UNLOCK_SOUND)
        mes("You unlock the door.")
        return true
    }

    internal suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()
        if (!eadgarsRuse.hasStoreroomAccess(player)) {
            mes("You search the crate but find nothing of interest.")
            return
        }
        val guard = nearestGuard()
        if (random.of(SPOT_ROLL) < SPOT_CHANCE) {
            guard?.say("!")
            takeInstantHit(HitType.Typeless, random.of(0, ROCK_MAX_DAMAGE))
            telejump(OUTSIDE_DOOR, TeleportType.Exempt)
            return
        }
        invAddOrDrop(objRepo, GOUTWEED)
        objbox(GOUTWEED, "You've found some goutweed!")
        guard?.say("Hm?")
    }

    private fun ProtectedAccess.nearestGuard(): Npc? {
        val guard = STOREROOM_GUARD.asRSCM(RSCMType.NPC)
        return npcRepo.findAll(ZoneKey.from(coords), zoneRadius = 2)
            .filter { it.id == guard }
            .minByOrNull { it.coords.chebyshevDistance(coords) }
    }

    private companion object {
        const val RACK = "loc.eadgar_rack"
        const val DRAWERS = "loc.eadgar_kitchen_drawers"
        const val STOREROOM_DOOR = "loc.eadgar_storeroomdoor"
        const val GOUTWEED_CRATE = "loc.eadgar_crate_goutweed"

        val OUTSIDE_DOOR = CoordGrid(2869, 10084, 0)

        const val SPOT_ROLL = 100
        const val SPOT_CHANCE = 50
        const val ROCK_MAX_DAMAGE = 6

        const val LOCKED_SOUND = "synth.locked"
        const val UNLOCK_SOUND = "synth.unlock"

        val PARROT_LINES = listOf("Polly wanna cracker!", "Pieces of eight! Pieces of eight!", "Who's a pretty boy then?")
    }
}
