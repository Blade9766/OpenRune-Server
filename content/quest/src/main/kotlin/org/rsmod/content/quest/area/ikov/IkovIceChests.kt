package org.rsmod.content.quest.area.ikov

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The six chests along the icy path south of the temple gates.
 *
 * Only one of them holds ice arrows at a time, and which one is remembered per player rather than
 * per world, so two people searching the path do not empty each other's chests. Taking the arrows
 * moves them to another chest, which may well be the same one again.
 */
class IkovIceChests
@Inject
constructor(private val locRepo: LocRepository, private val random: GameRandom) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CLOSED_CHEST) { openChest(it.loc) }
        onOpLoc1(OPEN_CHEST) { searchChest(it.loc) }
        onOpLoc2(OPEN_CHEST) { shutChest(it.loc) }
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(SEARCH_SEQ)
        soundSynth(OPEN_SOUND)
        locRepo.change(chest, OPEN_CHEST, CHEST_DURATION)
    }

    private suspend fun ProtectedAccess.shutChest(chest: BoundLocInfo) {
        arriveDelay()
        soundSynth(CLOSE_SOUND)
        locRepo.change(chest, CLOSED_CHEST, CHEST_DURATION)
    }

    private suspend fun ProtectedAccess.searchChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(SEARCH_SEQ)
        delay(1)
        val index = IkovCoords.ICE_CHESTS.indexOf(chest.coords)
        if (index < 0 || index != player.ikovArrowChest) {
            mes("You search the chest, but it is empty.")
            return
        }
        if (inv.isFull() && TempleOfIkovQuest.ICE_ARROWS !in player.inv) {
            mes("You don't have enough inventory space.")
            return
        }
        val arrows = random.of(MIN_ARROWS, MAX_ARROWS)
        invAdd(inv, TempleOfIkovQuest.ICE_ARROWS, arrows)
        soundSynth(TempleOfIkovQuest.SOUND_FOUND_ICE_ARROWS)
        player.ikovArrowChest = random.of(IkovCoords.ICE_CHESTS.size)
        objbox(
            TempleOfIkovQuest.ICE_ARROWS,
            "You find $arrows arrows of clear blue ice packed into the chest. They are ice cold " +
                "to the touch.",
        )
    }

    private companion object {
        const val CLOSED_CHEST = "loc.ikov_chestclosed"
        const val OPEN_CHEST = "loc.ikov_chestopen"

        const val MIN_ARROWS = 3
        const val MAX_ARROWS = 6

        /** An opened chest stands open until it reverts, the same as any other. */
        const val CHEST_DURATION = 100

        const val SEARCH_SEQ = "seq.human_pickupfloor"
        const val OPEN_SOUND = "synth.chest_open"
        const val CLOSE_SOUND = "synth.chest_close"
    }
}
