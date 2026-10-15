package org.rsmod.content.travel.ardougnecloak

import jakarta.inject.Inject
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
 * Ardougne cloaks, the Ardougne Achievement Diary rewards. Every tier teleports to the Kandarin
 * Monastery without limit; only the elite cloak reaches the Ardougne farming patch, though the
 * cache gives its op to the medium and hard cloaks as well.
 *
 * The held ops are `Equip, Monastery Teleport, Farm Teleport` (op 3 and op 4) and the worn ops come
 * from the `wear_op1`/`wear_op2` params (worn op 2 and op 3).
 */
class ArdougneCloakScript
@Inject
constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (cloak in CLOAKS) {
            onOpHeld3(cloak.obj) { teleport(MONASTERY) }
            onOpWorn2(cloak.obj) { teleport(MONASTERY) }
            if (!cloak.hasFarmOp) {
                continue
            }
            if (cloak.reachesFarm) {
                onOpHeld4(cloak.obj) { teleport(FARM) }
                onOpWorn3(cloak.obj) { teleport(FARM) }
            } else {
                onOpHeld4(cloak.obj) { mes(FARM_DENIAL) }
                onOpWorn3(cloak.obj) { mes(FARM_DENIAL) }
            }
        }
    }

    private suspend fun ProtectedAccess.teleport(destination: Destination) {
        if (actionDelay > mapClock) {
            return
        }
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return
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
    }

    private data class Cloak(val obj: String, val hasFarmOp: Boolean, val reachesFarm: Boolean)

    /** A landing zone: the player arrives on a random tile inside it. */
    private class Destination(private val xRange: IntRange, private val zRange: IntRange) {
        fun landing(random: GameRandom): CoordGrid =
            CoordGrid(random.of(xRange), random.of(zRange), 0)
    }

    private companion object {
        private const val FARM_DENIAL =
            "Only the elite Ardougne cloak can teleport to the Ardougne farm."

        private val MONASTERY = Destination(2606..2608, 3220..3222)
        private val FARM = Destination(2664..2666, 3375..3377)

        private val CLOAKS =
            listOf(
                Cloak("obj.ardy_cape_easy", hasFarmOp = false, reachesFarm = false),
                Cloak("obj.ardy_cape_medium", hasFarmOp = true, reachesFarm = false),
                Cloak("obj.ardy_cape_hard", hasFarmOp = true, reachesFarm = false),
                Cloak("obj.ardy_cape_elite", hasFarmOp = true, reachesFarm = true),
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
