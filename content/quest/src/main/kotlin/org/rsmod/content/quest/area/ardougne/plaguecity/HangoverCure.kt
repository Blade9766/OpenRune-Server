package org.rsmod.content.quest.area.ardougne.plaguecity

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.HANGOVER_CURE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.SCRUFFY_NOTE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.TELEPORT_SCROLL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bravek's hangover cure (chocolate dust into a bucket of milk, then snape grass), the scruffy
 * recipe note he scribbled, and the Ardougne teleport scroll Edmond hands over at the end.
 */
class HangoverCure @Inject constructor(private val plagueCity: PlagueCityQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(CHOCOLATE_DUST, BUCKET_MILK) { mixChocolate() }
        onOpHeldU(SNAPE_GRASS, CHOCOLATY_MILK) { mixSnapeGrass() }
        onOpHeld1(SCRUFFY_NOTE) { readNote() }
        onOpHeld1(TELEPORT_SCROLL) { readScroll() }
    }

    private suspend fun ProtectedAccess.mixChocolate() {
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, CHOCOLATE_DUST)
        invReplace(inv, BUCKET_MILK, 1, CHOCOLATY_MILK)
        objbox(CHOCOLATY_MILK, "You mix the chocolate into the bucket.")
    }

    private suspend fun ProtectedAccess.mixSnapeGrass() {
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, SNAPE_GRASS)
        invReplace(inv, CHOCOLATY_MILK, 1, HANGOVER_CURE)
        objbox(HANGOVER_CURE, "You mix the snape grass into the bucket.")
    }

    /** Trudi's handwriting is as bad as Bravek's hangover. */
    private suspend fun ProtectedAccess.readNote() {
        mesbox("Got a bncket of nnilk. Tlen qrind sorne lcoculate vnith a pestal and rnortar.")
        mesbox("Ald the grourd dlocolate to tho milt. Fnales add 5cme snape gras5.")
    }

    /**
     * The first scroll teaches the spell; any further copies go up in smoke the way the real
     * one does once the spell is known.
     */
    private suspend fun ProtectedAccess.readScroll() {
        if (!plagueCity.readScroll.get(player)) {
            plagueCity.readScroll.set(player, true)
            invDel(inv, TELEPORT_SCROLL)
            objbox(TELEPORT_SCROLL, "You memorise what is written on the scroll. You can now use the Ardougne Teleport Spell.")
            return
        }
        invReplace(inv, TELEPORT_SCROLL, 1, ASHES)
        spotanim(PUFF)
        soundSynth(EXPLODE_SOUND)
        mes("The scroll bursts into flame as you unroll it, leaving only ashes.")
    }

    private companion object {
        const val CHOCOLATE_DUST = "obj.chocolate_dust"
        const val BUCKET_MILK = "obj.bucket_milk"
        const val CHOCOLATY_MILK = "obj.chocolaty_milk"
        const val SNAPE_GRASS = "obj.snape_grass"
        const val ASHES = "obj.ashes"

        const val MIX_SEQ = "seq.human_pickuptable"
        const val MIX_SOUND = "synth.vial_mix"
        const val PUFF = "spotanim.smokepuff"
        const val EXPLODE_SOUND = "synth.exploding_vial"
    }
}
