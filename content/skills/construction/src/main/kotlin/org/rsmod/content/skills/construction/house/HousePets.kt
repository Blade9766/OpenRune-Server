package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.other.pets.PetMenagerie
import org.rsmod.content.other.pets.PetMenagerie.menagerieExtras
import org.rsmod.content.other.pets.Pets
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The pets a menagerie's owner lets roam: each stored pet stands in the house's first menagerie as
 * its own npc while the house is out of building mode - its extras too, those that have an npc -
 * unless the pet house's "Allow pets to roam"
 * is off (`varbit.poh_menagerie_closed`). They go and come back with the house, and are put back
 * whenever a pet goes in or comes out.
 */
@Singleton
class HousePets @Inject constructor(private val npcRepo: NpcRepository) {
    private class Roaming(val tiles: List<CoordGrid>, val npcs: MutableList<Npc>)

    private val roaming = HashMap<Long, Roaming>()

    fun spawn(owner: Player, tiles: List<CoordGrid>) {
        despawn(owner)
        if (tiles.isEmpty()) {
            return
        }
        val npcs = ArrayList<Npc>()
        roaming[owner.key()] = Roaming(tiles, npcs)
        if (owner.vars[CLOSED_VARBIT] == 1) {
            return
        }
        val stored = PetMenagerie.stored(owner).mapNotNull { slot -> PetMenagerie.shownObj(owner, slot)?.let { Pets.forObj(it)?.second?.npc } }
        val extras = owner.menagerieExtras.mapNotNull { it?.id?.let(PetMenagerie::extraNpc) }
        for ((index, type) in (stored + extras).withIndex()) {
            val npc = Npc(type, tiles[index % tiles.size])
            npcRepo.add(npc, Int.MAX_VALUE)
            npc.respawns = false
            npcs += npc
        }
    }

    /** Puts the owner's pets back in line with what is stored and the roaming setting. */
    fun refresh(owner: Player) {
        val tiles = roaming[owner.key()]?.tiles ?: return
        spawn(owner, tiles)
    }

    fun despawn(owner: Player) {
        val entry = roaming.remove(owner.key()) ?: return
        for (npc in entry.npcs) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    private fun Player.key(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    private companion object {
        const val CLOSED_VARBIT = "varbit.poh_menagerie_closed"
    }
}
