package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ROBES
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Necrovarus's robing room upstairs in the temple of the Ectofuntus. Its door
 * (`loc.ahoy_harbour_door`, a door type the town shares) stays locked to everyone until they use
 * the bone key Necrovarus dropped for them on it, which unlocks it for that player alone
 * (`varbit.ahoy_templedoor_unlocked`). The door is only ever walked through, never left open, and
 * anyone inside can always leave. Every other door of the same type opens normally.
 *
 * The coffin inside holds the mystical robes; it is searched without being opened for anyone else.
 */
class TempleRobes
@Inject
constructor(private val ahoy: GhostsAhoyQuest, private val passages: GenericPassageScript) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(DOOR) { door(it.loc, it.type) }
        onOpLocU(DOOR, BONE_KEY) { unlock(it.loc) }
        onOpLoc1(COFFIN) { searchCoffin() }
        onOpLoc1(COFFIN_OPEN) { searchCoffin() }
    }

    private suspend fun ProtectedAccess.door(loc: BoundLocInfo, type: ObjectServerType) {
        if (loc.coords != TEMPLE_DOOR) {
            with(passages) { passage(loc, type, 0) }
            return
        }
        arriveDelay()
        val leaving = coords.x >= TEMPLE_DOOR.x
        if (!leaving && !ahoy.isTempleUnlocked(player)) {
            mes("The door is locked.")
            return
        }
        with(passages) { walkThrough(loc, type) }
    }

    private suspend fun ProtectedAccess.unlock(loc: BoundLocInfo) {
        arriveDelay()
        if (loc.coords != TEMPLE_DOOR) {
            mes("The key doesn't fit this lock.")
            return
        }
        if (ahoy.isTempleUnlocked(player)) {
            mes("You have already unlocked this door.")
            return
        }
        if (!ahoy.isPetitionPresented(player)) {
            mes("The key doesn't seem to turn in this lock.")
            return
        }
        if (invDel(inv, BONE_KEY).failure) {
            return
        }
        ahoy.setTempleUnlocked(player)
        soundSynth(UNLOCK_SOUND)
        mes("You unlock the door with the bone key. The key crumbles to dust.")
    }

    private suspend fun ProtectedAccess.searchCoffin() {
        arriveDelay()
        anim(OPEN_SEQ)
        if (!ahoy.needsRobes(player)) {
            mes("You search the coffin, but find nothing of interest.")
            return
        }
        if (invAdd(inv, ROBES).failure) {
            mes("You need a free inventory space to take anything from the coffin.")
            return
        }
        objbox(ROBES, "You open the coffin and find Necrovarus's mystical robes folded inside.")
    }

    companion object {
        const val DOOR = "loc.ahoy_harbour_door"
        const val COFFIN = "loc.ahoy_coffin"
        const val COFFIN_OPEN = "loc.ahoy_coffin_open"
        val TEMPLE_DOOR = CoordGrid(3656, 3514, 1)
        private const val UNLOCK_SOUND = "synth.unlock"
        private const val OPEN_SEQ = "seq.human_openchest"
    }
}
