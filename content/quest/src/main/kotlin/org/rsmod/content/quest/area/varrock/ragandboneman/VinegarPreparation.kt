package org.rsmod.content.quest.area.varrock.ragandboneman

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.JUG
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT_OF_VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.VINEGAR
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting a specimen ready to boil: vinegar into an empty pot, then the raw specimen into the pot
 * of vinegar, each specimen keeping its own obj. Anything else offered to the pot of vinegar is
 * turned away with a reason. Emptying a jug or pot of vinegar is free; tipping out a soaking
 * specimen asks first, since it throws the specimen away (the right creature drops another).
 *
 * The pot of vinegar takes everything through its default use handler rather than a pair per
 * specimen, because a default on the pot fires before the dispatcher tries the reverse order of a
 * pair.
 */
class VinegarPreparation : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(VINEGAR, POT) { pourVinegar(it.firstSlot, it.secondSlot) }
        onOpHeldU(POT_OF_VINEGAR) { soak(it.firstSlot, it.second, it.secondSlot) }
        for (specimen in Specimen.entries) {
            onOpHeldU(specimen.raw, POT) { mes("You need to fill the pot with vinegar before the ${name(specimen)} will soak.") }
            onOpHeldU(specimen.raw, VINEGAR) { mes("Pour the vinegar into an empty pot first, then add the ${name(specimen)}.") }
            onOpHeld4(specimen.inVinegar) { emptySpecimen(specimen, it.slot) }
        }
        onOpHeld4(VINEGAR) { emptyVinegar(it.slot, JUG, "You pour the vinegar away.") }
        onOpHeld4(POT_OF_VINEGAR) { emptyVinegar(it.slot, POT, "You tip the vinegar out of the pot.") }
    }

    private fun ProtectedAccess.pourVinegar(jugSlot: Int, potSlot: Int) {
        val poured =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = VINEGAR.asRSCM()
                    strictCount = 1
                    strictSlot = jugSlot
                }
                delete {
                    from = pack
                    obj = POT.asRSCM()
                    strictCount = 1
                    strictSlot = potSlot
                }
                insert {
                    into = pack
                    obj = JUG.asRSCM()
                    strictCount = 1
                    strictSlot = jugSlot
                }
                insert {
                    into = pack
                    obj = POT_OF_VINEGAR.asRSCM()
                    strictCount = 1
                    strictSlot = potSlot
                }
            }
        if (poured.success) {
            anim(POUR_SEQ)
            soundSynth(POUR_SOUND)
            mes("You pour the vinegar into the pot.")
        }
    }

    private fun ProtectedAccess.soak(potSlot: Int, other: ItemServerType, otherSlot: Int) {
        val raw = Specimen.byRaw(other.internalName)
        if (raw == null) {
            refuse(other.internalName, other)
            return
        }
        val soaked =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = raw.raw.asRSCM()
                    strictCount = 1
                    strictSlot = otherSlot
                }
                delete {
                    from = pack
                    obj = POT_OF_VINEGAR.asRSCM()
                    strictCount = 1
                    strictSlot = potSlot
                }
                insert {
                    into = pack
                    obj = raw.inVinegar.asRSCM()
                    strictCount = 1
                    strictSlot = potSlot
                }
            }
        if (soaked.success) {
            soundSynth(POUR_SOUND)
            mes("You put the ${name(raw)} into the pot of vinegar to soak.")
        }
    }

    private fun ProtectedAccess.refuse(obj: String, other: ItemServerType) {
        val polished = Specimen.byPolished(obj)
        val soaking = Specimen.byInVinegar(obj)
        when {
            polished != null ->
                mes("The ${name(polished)} is already polished. Another soak would only dull the shine.")
            soaking != null -> mes("That pot is already full: the ${name(soaking)} is soaking in it.")
            obj == POT_OF_VINEGAR -> mes("Both pots are already full of vinegar.")
            "bone" in other.name.lowercase() ->
                mes("The Odd Old Man wants his eight particular specimens, not just any old bones.")
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.emptySpecimen(specimen: Specimen, slot: Int) {
        val confirmed =
            choice2(
                "Yes, tip it all out.",
                true,
                "No, keep it soaking.",
                false,
                title = "Empty the pot? The ${name(specimen)} will be thrown away too.",
            )
        if (!confirmed) {
            return
        }
        if (invReplaceSlot(inv, slot, 1, objType(POT)).success) {
            mes("You tip out the vinegar and the ${name(specimen)} along with it. You'll need to find another.")
        }
    }

    private fun ProtectedAccess.emptyVinegar(slot: Int, emptied: String, message: String) {
        if (invReplaceSlot(inv, slot, 1, objType(emptied)).success) {
            mes(message)
        }
    }

    private fun name(specimen: Specimen): String = specimen.label.lowercase()

    private fun objType(obj: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(obj.asRSCM())) { obj }

    companion object {
        const val POUR_SEQ = "seq.human_pour_pot"
        const val POUR_SOUND = "synth.liquid"
    }
}
