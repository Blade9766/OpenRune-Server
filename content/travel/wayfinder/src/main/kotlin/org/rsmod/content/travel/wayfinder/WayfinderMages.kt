package org.rsmod.content.travel.wayfinder

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.entity.Npc

/** The npcs that run the wayfinder network, each with the words they cast it with. */
object WayfinderMages {
    data class Mage(val npc: String, val name: String, val incantation: String)

    val ALL: List<Mage> =
        listOf(
            Mage("npc.wayfinder_vaelor", "Archmage Vaelor", "Viam aperio, Vaelor iubet!"),
            Mage("npc.wayfinder_elowen", "Arcanist Elowen", "Lumen, duc viatorem!"),
        )

    private val byType: Map<Int, Mage> by lazy { ALL.associateBy { it.npc.asRSCM(RSCMType.NPC) } }

    val typeIds: Set<Int>
        get() = byType.keys

    fun isMage(npc: Npc): Boolean = npc.type.id in byType

    fun name(npc: Npc): String = byType[npc.type.id]?.name ?: npc.type.name

    fun incantation(npc: Npc): String = byType[npc.type.id]?.incantation ?: "Abracadabra!"
}
