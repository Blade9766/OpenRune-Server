package org.rsmod.content.skills.magic.telegrab

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApObjT
import org.rsmod.api.script.onOpObjT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Telekinetic Grab: pulls a ground obj into the caster's inventory from up to ten tiles away.
 * The spell is cast as soon as the item is in range and in sight; the obj is only taken when the
 * projectile lands, so an item picked up by someone else in the meantime is simply missed.
 */
class TelekineticGrabScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val takeValidator: PlayerObjTakeValidator,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val spellObj = ServerCacheManager.getItem(SPELL_OBJ.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(spellObj) ?: return
        onApObjT(spell.component) { cast(it.obj, spell) }
        onOpObjT(spell.component) { cast(it.obj, spell) }
    }

    private suspend fun ProtectedAccess.cast(obj: Obj, spell: MagicSpell) {
        val type = ServerCacheManager.getItem(obj.type) ?: return
        val denial = takeValidator.validate(player, obj, type)
        if (denial != null) {
            mes(denial)
            return
        }
        if (!player.invAdd(inv, obj.type, obj.count, autoCommit = false).success) {
            mes(Constants.dm_take_invspace)
            return
        }
        if (!runes.canCastSpell(player, spell)) {
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }

        faceSquare(obj.coords)
        anim(CAST_SEQ)
        spotanim(CAST_SPOTANIM, height = CAST_HEIGHT)
        soundSynth(CAST_SOUND)
        worldRepo.projAnimSourced(player, obj.coords, travelSpotanim, PROJANIM)
        statAdvance("stat.magic", spell.castXp)

        delay(TRAVEL_TICKS)
        spotanimMap(worldRepo, IMPACT_SPOTANIM, obj.coords)
        if (!objRepo.del(obj, Int.MAX_VALUE)) {
            mes(Constants.dm_take_taken)
            return
        }
        val take = player.invAdd(inv, obj.type, obj.count, autoCommit = true)
        if (take.failure) {
            // The inventory filled up while the spell was in the air; put the item back.
            mes(Constants.dm_take_invspace)
            objRepo.add(obj, DROPPED_BACK_DURATION)
        }
    }

    private val travelSpotanim = SpotanimType(TRAVEL_SPOTANIM.asRSCM(RSCMType.SPOTANIM))

    private companion object {
        const val SPELL_OBJ = "obj.33_tele_grab"

        const val CAST_SEQ = "seq.human_casttelegrab"
        const val CAST_SPOTANIM = "spotanim.telegrab_casting"
        const val TRAVEL_SPOTANIM = "spotanim.telegrab_travel"
        const val IMPACT_SPOTANIM = "spotanim.telegrab_impact"
        const val PROJANIM = "projanim.magic_spell"
        const val CAST_SOUND = "synth.telegrab_all"
        const val CAST_HEIGHT = 92

        /** Cycles the projectile takes to reach the item. */
        const val TRAVEL_TICKS = 2

        /** How long an item stays on the floor when the grab has to give it back. */
        const val DROPPED_BACK_DURATION = 200
    }
}
