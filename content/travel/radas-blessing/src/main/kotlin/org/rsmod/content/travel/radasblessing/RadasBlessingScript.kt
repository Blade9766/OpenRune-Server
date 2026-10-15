package org.rsmod.content.travel.radasblessing

import jakarta.inject.Inject
import java.time.LocalDate
import java.time.ZoneOffset
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rada's blessings, the Kourend & Kebos Achievement Diary rewards.
 *
 * Every tier can teleport to the Kourend Woodland; tiers 3 and 4 also reach the summit of Mount
 * Karuulm. The lower tiers ration their teleports per day (resetting at 00:00 UTC):
 *
 * | Tier | Kourend Woodland | Mount Karuulm |
 * |------|------------------|---------------|
 * | 1    | 3 per day        | -             |
 * | 2    | 5 per day        | -             |
 * | 3    | unlimited        | 3 per day     |
 * | 4    | unlimited        | unlimited     |
 *
 * The held ops are `Equip, Kourend Woodland, Mount Karuulm` (op 3 and op 4) and the worn ops come
 * from the `wear_op1`/`wear_op2` params (worn op 2 and op 3). The prayer bonus is a cache stat and
 * the extra-fish chance is handled by the Fishing skill script.
 *
 * Daily usage is packed into `varp.radas_blessing_teleports`: bits 0-15 hold the UTC day the counts
 * belong to, bits 16-19 the Woodland teleports used and bits 20-23 the Karuulm teleports used.
 */
class RadasBlessingScript
@Inject
constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in TIERS) {
            onOpHeld3(tier.obj) { teleport(tier, WOODLAND) }
            onOpWorn2(tier.obj) { teleport(tier, WOODLAND) }
            if (tier.karuulmLimit != NONE) {
                onOpHeld4(tier.obj) { teleport(tier, KARUULM) }
                onOpWorn3(tier.obj) { teleport(tier, KARUULM) }
            }
        }
    }

    private suspend fun ProtectedAccess.teleport(tier: Blessing, destination: Destination) {
        if (actionDelay > mapClock) {
            return
        }
        val limit = tier.limit(destination)
        val used = usesToday(destination)
        if (limit != UNLIMITED && used >= limit) {
            mes("You have used all of your ${destination.name} teleports for today.")
            return
        }
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return
        }
        if (limit != UNLIMITED) {
            recordUse(destination)
        }
        actionDelay = mapClock + ACTION_DELAY
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_DELAY)
        // Validated before the animation began; a teleblock landing during the cast must not stop
        // a teleport that has already started.
        telejump(destination.landing(random), TeleportType.Exempt)
        anim(TELEPORT_END_ANIM)
        if (limit != UNLIMITED) {
            val left = limit - used - 1
            val plural = if (left == 1) "teleport" else "teleports"
            mes("You have $left ${destination.name} $plural left today.")
        }
    }

    private fun ProtectedAccess.usesToday(destination: Destination): Int {
        val packed = vars[USAGE_VARP]
        if ((packed and DAY_MASK) != today()) {
            return 0
        }
        return (packed ushr destination.shift) and USES_MASK
    }

    private fun ProtectedAccess.recordUse(destination: Destination) {
        val today = today()
        var packed = vars[USAGE_VARP]
        if ((packed and DAY_MASK) != today) {
            packed = today
        }
        val uses = ((packed ushr destination.shift) and USES_MASK) + 1
        val cleared = packed and (USES_MASK shl destination.shift).inv()
        vars[USAGE_VARP] = cleared or (uses.coerceAtMost(USES_MASK) shl destination.shift)
    }

    /** The UTC day, truncated to the 16 bits reserved for it. */
    private fun today(): Int = (LocalDate.now(ZoneOffset.UTC).toEpochDay() and DAY_MASK.toLong()).toInt()

    private data class Blessing(val obj: String, val woodlandLimit: Int, val karuulmLimit: Int) {
        fun limit(destination: Destination): Int =
            if (destination === WOODLAND) woodlandLimit else karuulmLimit
    }

    /**
     * A landing zone: the player arrives on a random tile inside it, matching the small patch the
     * official teleport spreads arrivals over.
     */
    private class Destination(
        val name: String,
        private val xRange: IntRange,
        private val zRange: IntRange,
        val shift: Int,
    ) {
        fun landing(random: GameRandom): CoordGrid =
            CoordGrid(random.of(xRange), random.of(zRange), 0)
    }

    private companion object {
        private const val UNLIMITED = -1
        private const val NONE = 0

        private const val USAGE_VARP = "varp.radas_blessing_teleports"
        private const val DAY_MASK = 0xFFFF
        private const val USES_MASK = 0xF

        private val WOODLAND = Destination("Kourend Woodland", 1549..1558, 3454..3460, shift = 16)
        private val KARUULM = Destination("Mount Karuulm", 1311..1312, 3797..3805, shift = 20)

        private val TIERS =
            listOf(
                Blessing("obj.zeah_blessing_easy", woodlandLimit = 3, karuulmLimit = NONE),
                Blessing("obj.zeah_blessing_medium", woodlandLimit = 5, karuulmLimit = NONE),
                Blessing("obj.zeah_blessing_hard", woodlandLimit = UNLIMITED, karuulmLimit = 3),
                Blessing("obj.zeah_blessing_elite", woodlandLimit = UNLIMITED, karuulmLimit = UNLIMITED),
            )

        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.teleport_all"
        private const val TELEPORT_DELAY = 3
        private const val ACTION_DELAY = 4
    }
}
