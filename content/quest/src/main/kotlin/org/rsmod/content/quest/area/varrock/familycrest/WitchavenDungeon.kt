package org.rsmod.content.quest.area.varrock.familycrest

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The lever puzzle under Witchaven, which shuts the hellhounds - and the last seam of 'perfect'
 * gold - away from the rest of the dungeon.
 *
 * Three levers stand in the dungeon, each with a raised and a lowered form in the cache
 * (`loc.leverg` / `loc.leverg2` and so on). Every door is named after the lever positions that
 * open it: `famcrest_doorg2h1` opens with G raised and H lowered, `famcrest_doori2h1` with I
 * raised and H lowered. Since a lever can only be reached through a door that some *other*
 * combination opens, the only way to end up with G raised, H lowered and I raised is:
 *
 * G up, H up, G down, I up, G up, H down.
 *
 * Raised levers fall back on their own after [LEVER_RESET_TICKS], which is what makes a player
 * who wanders off - or logs out - start the sequence again. Once a player has been through the
 * hellhound door the puzzle stays solved for them and the door opens on its own.
 */
class WitchavenDungeon
@Inject
constructor(
    private val locRepo: LocRepository,
    private val passages: GenericPassageScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (lever in Lever.entries) {
            onOpLoc1(lever.lowered) { pull(lever, it.vis, raise = true) }
            onOpLoc1(lever.raised) { pull(lever, it.vis, raise = false) }
        }
        for (door in PuzzleDoor.entries) {
            onOpLoc1(door.loc) { open(door, it.vis, it.type) }
        }
    }

    private fun raised(lever: Lever): Boolean = locRepo.findLoc(lever.coords, lever.raised)

    private suspend fun ProtectedAccess.pull(lever: Lever, loc: BoundLocInfo, raise: Boolean) {
        arriveDelay()
        anim(if (raise) LEVER_UP_SEQ else LEVER_DOWN_SEQ)
        soundSynth(LEVER_SOUND)
        val into = if (raise) lever.raised else lever.lowered
        locRepo.change(loc, into, LEVER_RESET_TICKS)
        mes(if (raise) "You pull the lever up." else "You push the lever down.")
    }

    private suspend fun ProtectedAccess.open(
        door: PuzzleDoor,
        loc: BoundLocInfo,
        type: ObjectServerType,
    ) {
        val unlocked = door.isGoldRoom && player.witchavenPuzzleSolved
        if (!unlocked && !door.opensNow(::raised)) {
            arriveDelay()
            soundSynth(LOCKED_SOUND)
            mes("The door is held shut by something you cannot see.")
            return
        }
        if (door.isGoldRoom && !player.witchavenPuzzleSolved) {
            player.witchavenPuzzleSolved = true
            mes("The levers grind into place and the door gives way.")
        }
        with(passages) { walkThrough(loc, type) }
    }

    /** The three levers, named after the letters the cache gives their doors. */
    enum class Lever(val lowered: String, val raised: String, val coords: CoordGrid) {
        G("loc.leverg", "loc.leverg2", CoordGrid(2722, 9710, 0)),
        H("loc.leverh", "loc.leverh2", CoordGrid(2724, 9669, 0)),
        I("loc.leveri", "loc.leveri2", CoordGrid(2722, 9718, 0))
    }

    /**
     * A door and the lever positions that open it. [raisedLevers] must all be up and
     * [loweredLevers] all down.
     */
    enum class PuzzleDoor(
        val loc: String,
        val raisedLevers: List<Lever>,
        val loweredLevers: List<Lever> = emptyList(),
        val isGoldRoom: Boolean = false,
    ) {
        /** West doorway out of the entrance hall. */
        H2("loc.famcrest_doorh2", listOf(Lever.H)),

        /** East doorway out of the entrance hall. */
        G2H1("loc.famcrest_doorg2h1", listOf(Lever.G), listOf(Lever.H)),

        /** Into the north room, where lever I stands. */
        H2G1("loc.famcrest_doorh2g1", listOf(Lever.H), listOf(Lever.G)),

        /** Into the hellhound room and the 'perfect' gold. */
        I2H1("loc.famcrest_doori2h1", listOf(Lever.I), listOf(Lever.H), isGoldRoom = true);

        fun opensNow(raised: (Lever) -> Boolean): Boolean =
            raisedLevers.all(raised) && loweredLevers.none(raised)
    }

    private companion object {
        /** Five minutes, the same as an ordinary door's swing, before a lever falls back. */
        const val LEVER_RESET_TICKS = 500

        const val LEVER_UP_SEQ = "seq.human_leverup"
        const val LEVER_DOWN_SEQ = "seq.human_leverdown"
        const val LEVER_SOUND = "synth.lever"
        const val LOCKED_SOUND = "synth.locked"
    }
}
