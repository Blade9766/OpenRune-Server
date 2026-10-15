package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.construction.data.Chapel
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lighting the chapel's incense burners.
 *
 * A lit burner is a timed spawn over the unlit one, and its despawn puts the unlit burner back. The
 * timer belongs to the house region, so a house torn down or rebuilt simply comes back unlit.
 */
class ChapelScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (burner in Chapel.BURNERS.keys + Chapel.LIT_BURNERS.keys) {
            onOpLoc1(burner) { light(it.loc) }
        }
    }

    private fun ProtectedAccess.light(burner: BoundLocInfo) {
        val name = RSCM.getReverseMapping(RSCMType.LOC, burner.id)
        val unlit = Chapel.LIT_BURNERS[name] ?: name
        val lit = Chapel.BURNERS[unlit] ?: return
        if (player.firemakingLvl < FIREMAKING_LEVEL) {
            mes("You need a Firemaking level of $FIREMAKING_LEVEL to light the incense burner.")
            return
        }
        if (TINDERBOX !in inv) {
            mes("You need a tinderbox to light the incense burner.")
            return
        }
        if (MARRENTILL !in inv) {
            mes("You need a clean marrentill to burn in the incense burner.")
            return
        }
        if (invDel(inv, MARRENTILL).failure) {
            return
        }
        anim(ANIM)
        // Re-lighting has to replace the lit burner first: spawning over it is what drops its
        // pending despawn, so the old timer cannot put the burner out early.
        if (name != unlit) {
            locRepo.add(burner.coords, unlit, Int.MAX_VALUE, burner.angle, burner.shape)
        }
        val firemaking = player.firemakingLvl
        val duration = BURN_BASE + firemaking + random.of(0, firemaking)
        locRepo.add(burner.coords, lit, duration, burner.angle, burner.shape) {
            locRepo.add(burner.coords, unlit, Int.MAX_VALUE, burner.angle, burner.shape)
        }
        mes("You burn some marrentill in the incense burner.")
    }

    private companion object {
        const val TINDERBOX = "obj.tinderbox"
        const val MARRENTILL = "obj.marentill"
        const val ANIM = "seq.poh_fireplace_light"
        const val FIREMAKING_LEVEL = 30

        /**
         * Ticks a burner stays lit before the Firemaking bonus: it lasts this plus the player's
         * Firemaking level, plus up to that level again at random (RuneLite's burner timer model).
         */
        const val BURN_BASE = 200
    }
}
