package org.rsmod.content.quest.area.tirannwn.templeoflight

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The crystal dispenser on the middle floor and the five chests behind the light doors.
 *
 * Collecting from the dispenser hands over whatever waits in its tray, as much as the pack holds;
 * the rest stays for later. With the tray empty it offers to reset the puzzle: every mirror and
 * crystal leaves the pillars for the tray, the preset mirrors turn back, and anything the player
 * is owed but holds nowhere is made again. A reset never touches the chests, the rope or the quest.
 *
 * A chest gives its contents once per player and only when they all fit; the chest lid opening is
 * shared scenery, its contents are not.
 */
class CrystalDispenser
@Inject
constructor(
    private val puzzle: TemplePuzzle,
    private val world: WorldRepository,
    private val locRepo: LocRepository,
) : PluginScript() {
    private val quest: MourningsEndPart2Quest
        get() = puzzle.quest

    override fun ScriptContext.startup() {
        onOpLoc1(LEVER) { collect(it.loc) }
        onOpLoc1(COLLECTOR) { collect(it.loc) }
        for (chest in TempleChest.entries) {
            onOpLoc1(chest.closed) { openChest(chest, it.loc) }
            onOpLoc1(chest.open) { searchChest(chest) }
            onOpLoc2(chest.open) { shutChest(chest, it.loc) }
        }
    }

    private suspend fun ProtectedAccess.collect(loc: BoundLocInfo) {
        arriveDelay()
        if (quest.guarded(player)) {
            mes("The Temple of Light's safeguards are working fine for now, so I'd best not meddle with them.")
            return
        }
        if (!quest.puzzleOpen(player)) {
            mes("Nothing happens.")
            return
        }
        if (puzzle.tray(player).values.any { it > 0 }) {
            takeFromTray()
            return
        }
        val reset = choice2("Yes, reset the light.", true, "No.", false, title = "Reset the mirrors and crystals?")
        if (!reset || !quest.puzzleOpen(player)) {
            return
        }
        anim(PULL_SEQ)
        locAnim(world, loc, LEVER_SEQ)
        val tray = puzzle.reset(player)
        mes("You pull the lever. The mirrors and crystals return to the dispenser.")
        if (tray.values.none { it > 0 }) {
            mes("You already have everything the dispenser holds for you.")
        }
    }

    private fun ProtectedAccess.takeFromTray() {
        var free = inv.freeSpace()
        var taken = 0
        for ((item, waiting) in puzzle.tray(player)) {
            val count = minOf(waiting, free)
            if (count <= 0) continue
            if (invAdd(inv, item.obj, count).failure) continue
            puzzle.setTray(player, item, waiting - count)
            free -= count
            taken += count
        }
        val left = puzzle.tray(player).values.sum()
        when {
            taken == 0 -> mes("You don't have enough room to take anything from the dispenser.")
            left > 0 -> mes("You take what you can carry from the dispenser. There is more waiting for you.")
            else -> mes("You collect the mirrors and crystals from the dispenser.")
        }
    }

    private suspend fun ProtectedAccess.openChest(chest: TempleChest, loc: BoundLocInfo) {
        arriveDelay()
        anim(CHEST_SEQ)
        locRepo.change(loc, chest.open, CHEST_TICKS)
        delay(1)
        searchChest(chest)
    }

    private suspend fun ProtectedAccess.shutChest(chest: TempleChest, loc: BoundLocInfo) {
        arriveDelay()
        anim(CHEST_SEQ)
        locRepo.change(loc, chest.closed, CHEST_TICKS)
    }

    private fun ProtectedAccess.searchChest(chest: TempleChest) {
        if (player.vars[chest.varbit] == 1 || !quest.puzzleOpen(player)) {
            mes("The chest is empty.")
            return
        }
        if (inv.freeSpace() < chest.slots) {
            mes("You need ${chest.slots} free inventory spaces to take what is in this chest.")
            return
        }
        for ((item, count) in chest.contents) {
            invAdd(inv, item.obj, count)
        }
        MourningsEndPart2Quest.setVarBit(player, chest.varbit, 1)
        midiJingle(CHEST_JINGLE)
        mes("Inside the chest you find ${describe(chest.contents)}.")
    }

    private fun describe(contents: Map<TempleItem, Int>): String =
        contents.entries.joinToString(" and ") { (item, count) ->
            when {
                item == TempleItem.MIRROR -> if (count == 1) "a mirror" else "${WORDS[count]} mirrors"
                item.fractured -> "a fractured crystal"
                else -> "a ${checkNotNull(item.colour).label} crystal"
            }
        }

    companion object {
        const val LEVER = "loc.mourning_temple_light_wall_lever"
        const val COLLECTOR = "loc.mourning_temple_light_wall_collector"
        const val PULL_SEQ = "seq.human_pull_lever"
        const val LEVER_SEQ = "seq.mourning_temple_lever"
        const val CHEST_SEQ = "seq.human_openchest"
        const val CHEST_TICKS = 100
        const val CHEST_JINGLE = "jingle.temple_of_light"

        private val WORDS = listOf("no", "one", "two", "three", "four")
    }
}
