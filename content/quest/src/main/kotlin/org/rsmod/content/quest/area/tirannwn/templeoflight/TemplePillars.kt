package org.rsmod.content.quest.area.tirannwn.templeoflight

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The pillars of light, through their chatbox interface: Search shows what a pillar holds and
 * offers to turn or take a mirror or take a crystal; using a mirror or crystal on an empty pillar
 * puts it in, a mirror after asking which way it should face.
 *
 * Nothing changes until the player's last answer, and then everything is checked again against
 * the saved pillars and the inventory before the item and the pillar change together in one tick,
 * so a dialogue cut short by a shadow, a second click or a repeated packet can neither lose nor
 * copy a piece. The preset mirrors turn but stay put; the fused crystals and the Final Pillar
 * cannot be touched; and once the safeguards are restored nothing in the temple may be moved.
 */
class TemplePillars @Inject constructor(private val puzzle: TemplePuzzle) : PluginScript() {
    private val quest: MourningsEndPart2Quest
        get() = puzzle.quest

    override fun ScriptContext.startup() {
        for (pillar in TempleGeometry.pillars) {
            if (pillar.kind == PillarKind.FINAL) continue
            onOpLoc1(pillar.loc) { search(pillar) }
            for (item in TempleItem.entries) {
                onOpLocU(pillar.loc, item.obj) { insert(pillar, item) }
            }
        }
        onOpLoc1(BEND) { mes("A mirror is fixed inside the pillar, angled to carry the light between floors.") }
    }

    private suspend fun ProtectedAccess.search(pillar: Pillar) {
        arriveDelay()
        if (pillar.kind == PillarKind.FIXED_CRYSTAL) {
            val colour = checkNotNull(pillar.fixedColour)
            mes("There is a ${colour.label} crystal fused into the pillar.")
            return
        }
        when (val content = puzzle.state(player).contentOf(pillar)) {
            PillarContent.Empty -> mes("There is nothing inside the pillar.")
            is PillarContent.Mirror -> mirrorOptions(pillar, content)
            is PillarContent.Crystal -> crystalOptions(pillar, content)
        }
    }

    private suspend fun ProtectedAccess.mirrorOptions(pillar: Pillar, mirror: PillarContent.Mirror) {
        mes("There is a mirror in the pillar, facing ${mirror.facing.label}.")
        if (!mayChange()) {
            return
        }
        val preset = pillar.kind == PillarKind.PRESET_MIRROR
        val choice =
            if (preset) {
                choice2("Rotate the mirror.", ROTATE, "Leave it.", LEAVE)
            } else {
                choice3("Rotate the mirror.", ROTATE, "Take the mirror.", TAKE, "Leave it.", LEAVE)
            }
        when (choice) {
            ROTATE -> {
                val facing = askFacing() ?: return
                rotate(pillar, mirror, facing)
            }
            TAKE -> take(pillar, mirror)
        }
    }

    private suspend fun ProtectedAccess.crystalOptions(pillar: Pillar, crystal: PillarContent.Crystal) {
        mes("There is a ${itemName(crystal.item)} in the pillar.")
        if (!mayChange()) {
            return
        }
        if (choice2("Take the crystal.", TAKE, "Leave it.", LEAVE) == TAKE) {
            take(pillar, crystal)
        }
    }

    private suspend fun ProtectedAccess.insert(pillar: Pillar, item: TempleItem) {
        arriveDelay()
        when (pillar.kind) {
            PillarKind.FIXED_CRYSTAL -> {
                mes("There is already a crystal fused into this pillar.")
                return
            }
            PillarKind.PRESET_MIRROR -> {
                mes("There is already a mirror fixed in this pillar.")
                return
            }
            else -> Unit
        }
        if (!mayChange()) {
            return
        }
        if (puzzle.state(player).contentOf(pillar) != PillarContent.Empty) {
            mes("There is already something in this pillar.")
            return
        }
        if (item.fractured && !pillar.crossShaped) {
            mes("This fractured crystal doesn't seem to fit right in this pillar.")
            return
        }
        val content =
            if (item == TempleItem.MIRROR) {
                PillarContent.Mirror(askFacing() ?: return)
            } else {
                PillarContent.Crystal(item)
            }
        place(pillar, item, content)
    }

    /** The answer to "which way?", over two chatbox menus as there are six ways to face. */
    private suspend fun ProtectedAccess.askFacing(): Facing? {
        val picked =
            choice5(
                "North.", Facing.NORTH,
                "East.", Facing.EAST,
                "South.", Facing.SOUTH,
                "West.", Facing.WEST,
                "Up or down.", null,
                title = FACING_TITLE,
            )
        return picked ?: choice3("Up.", Facing.UP, "Down.", Facing.DOWN, "Never mind.", null, title = FACING_TITLE)
    }

    private fun ProtectedAccess.mayChange(): Boolean {
        if (quest.guarded(player)) {
            mes("The Temple of Light's safeguards are working fine for now, so I'd best not meddle with them.")
            return false
        }
        if (!quest.puzzleOpen(player)) {
            mes("You have no reason to meddle with the temple's light.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.place(pillar: Pillar, item: TempleItem, content: PillarContent) {
        val state = puzzle.state(player)
        if (!mayChangeQuietly() || state.contentOf(pillar) != PillarContent.Empty || !nearby(pillar)) {
            return
        }
        if (invDel(inv, item.obj, 1).failure) {
            return
        }
        puzzle.commit(player, state.with(pillar.id, content))
        when {
            content is PillarContent.Mirror -> mes("You place the mirror in the pillar, facing ${content.facing.label}.")
            item.fractured -> mes("The crystal only fits the pillar with the clear side facing ${checkNotNull(item.clearSide).label}.")
            else -> mes("You place the ${itemName(item)} in the pillar.")
        }
    }

    private fun ProtectedAccess.rotate(pillar: Pillar, mirror: PillarContent.Mirror, facing: Facing) {
        val state = puzzle.state(player)
        if (!mayChangeQuietly() || state.contentOf(pillar) != mirror || !nearby(pillar)) {
            return
        }
        puzzle.commit(player, state.with(pillar.id, PillarContent.Mirror(facing)))
        mes("You turn the mirror to face ${facing.label}.")
    }

    private fun ProtectedAccess.take(pillar: Pillar, content: PillarContent) {
        val state = puzzle.state(player)
        if (!mayChangeQuietly() || state.contentOf(pillar) != content || !nearby(pillar)) {
            return
        }
        val item =
            when (content) {
                is PillarContent.Mirror -> TempleItem.MIRROR
                is PillarContent.Crystal -> content.item
                PillarContent.Empty -> return
            }
        if (inv.freeSpace() < 1 || invAdd(inv, item.obj, 1).failure) {
            mes("You don't have enough room to take it.")
            return
        }
        puzzle.commit(player, state.with(pillar.id, PillarContent.Empty))
        mes(if (item == TempleItem.MIRROR) "You take the mirror from the pillar." else "You take the ${itemName(item)} from the pillar.")
    }

    private fun ProtectedAccess.mayChangeQuietly(): Boolean = quest.puzzleOpen(player)

    private fun ProtectedAccess.nearby(pillar: Pillar): Boolean =
        coords.level == pillar.coords.level && coords.chebyshevDistance(pillar.coords) <= REACH

    private fun itemName(item: TempleItem): String =
        when (item) {
            TempleItem.FRACTURED_HORIZONTAL, TempleItem.FRACTURED_VERTICAL -> "fractured crystal"
            TempleItem.MIRROR -> "mirror"
            else -> "${checkNotNull(item.colour).label} crystal"
        }

    companion object {
        /** The fixed bends on the top floor and ground floor that carry three beams to the Final Pillar. */
        const val BEND = "loc.mourning_temple_pillar_3_a"
        const val FACING_TITLE = "Which way should the mirror face?"
        const val REACH = 1

        private const val ROTATE = 1
        private const val TAKE = 2
        private const val LEAVE = 3
    }
}
