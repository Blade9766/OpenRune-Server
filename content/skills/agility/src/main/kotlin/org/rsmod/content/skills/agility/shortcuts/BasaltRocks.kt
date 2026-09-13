package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.seqGlideTicks
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The basalt causeway between the Barbarian Outpost beach and the Lighthouse. The rocks form five
 * short stretches of stone separated by one-tile gaps of sea; each gap has a jumping spot loc on
 * both banks, and clicking one leaps the player to it from the spot across the gap. The beach and
 * shore jumps are safe and give nothing; the three rock-to-rock jumps give Agility experience, and
 * all but the middle one can be failed, dropping the player in the sea to wash up on the nearer
 * end of the causeway.
 */
class BasaltRocks : PluginScript() {

    override fun ScriptContext.startup() {
        for (jump in JUMPS) {
            for (target in listOf(jump.first, jump.second)) {
                onApLoc1(target.loc) { apJump(it.loc, target) }
                onOpLoc1(target.loc) { jump(it.loc, target) }
            }
        }
    }

    private suspend fun ProtectedAccess.apJump(loc: BoundLocInfo, target: Spot) {
        if (!isWithinApRange(loc, JUMP_REACH)) {
            return
        }
        jump(loc, target)
    }

    private suspend fun ProtectedAccess.jump(loc: BoundLocInfo, target: Spot) {
        val jump = JUMPS.first { it.first == target || it.second == target }
        val origin = if (jump.first == target) jump.second else jump.first
        val fromOrigin = coords.chebyshevDistance(origin.tile)
        if (coords.level != origin.tile.level || fromOrigin > JUMP_REACH || fromOrigin > coords.chebyshevDistance(loc.coords)) {
            return
        }
        if (coords != origin.tile) {
            teleport(origin.tile, TeleportType.Exempt)
            delay(1)
        }
        if (jump.xp > 0.0 && jump.failable && !statRandom("stat.agility", SUCCESS_LOW, SUCCESS_HIGH, 0)) {
            fall(target.tile)
            return
        }
        soundSynth(JUMP_SOUND)
        leapTo(target.tile, AgilityAnims.STEPPING_STONE, seqGlideTicks(AgilityAnims.STEPPING_STONE, fallback = 2))
        if (jump.xp > 0.0) {
            statAdvance("stat.agility", jump.xp)
        }
    }

    private suspend fun ProtectedAccess.fall(towards: CoordGrid) {
        faceSquare(towards)
        anim(FALL_SEQ)
        soundSynth(FALL_SOUND)
        mes("You slip and fall into the sea.")
        delay(2)
        spotanim(SPLASH_SPOTANIM)
        soundSynth(SPLASH_SOUND)
        val shore = if (coords.chebyshevDistance(BEACH) <= coords.chebyshevDistance(SHORE)) BEACH else SHORE
        telejump(shore, TeleportType.Exempt)
        resetAnim()
        statAdvance("stat.agility", FAIL_XP)
        val damage = random.of(1, MAX_FALL_DAMAGE).coerceAtMost(player.hitpoints)
        queueHit(delay = 0, type = HitType.Typeless, damage = damage)
        mes("The current washes you up on the shore.")
    }

    private data class Spot(val loc: String, val tile: CoordGrid)

    private data class Jump(val first: Spot, val second: Spot, val xp: Double, val failable: Boolean)

    private companion object {
        /** Every jump crosses a one-tile gap, so the spot being jumped from is two tiles away. */
        const val JUMP_REACH = 2
        const val ROCK_XP = 2.0
        const val FAIL_XP = 0.5
        const val MAX_FALL_DAMAGE = 10
        const val SUCCESS_LOW = 5
        const val SUCCESS_HIGH = 255

        const val FALL_SEQ = "seq.human_falling"
        const val SPLASH_SPOTANIM = "spotanim.watersplash"
        const val JUMP_SOUND = "synth.jump"
        const val FALL_SOUND = "synth.jump_and_fall"
        const val SPLASH_SOUND = "synth.splash_and_river"

        val BEACH = CoordGrid(2522, 3594, 0)
        val SHORE = CoordGrid(2514, 3620, 0)

        fun spot(index: Int, x: Int, z: Int) = Spot("loc.horror_jumping_spot$index", CoordGrid(x, z, 0))

        val JUMPS =
            listOf(
                Jump(spot(1, 2522, 3595), spot(2, 2522, 3597), xp = 0.0, failable = false),
                Jump(spot(3, 2522, 3600), spot(4, 2522, 3602), xp = ROCK_XP, failable = true),
                Jump(spot(5, 2518, 3611), spot(6, 2516, 3611), xp = ROCK_XP, failable = false),
                Jump(spot(7, 2514, 3613), spot(8, 2514, 3615), xp = ROCK_XP, failable = true),
                Jump(spot(9, 2514, 3617), spot(10, 2514, 3619), xp = 0.0, failable = false),
            )
    }
}
