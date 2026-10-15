package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.other.pets.PetMenagerie
import org.rsmod.content.other.pets.PetMenagerie.menagerieExtras
import org.rsmod.content.other.pets.Pets
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HousePets
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The menagerie's pet house, pet list, scratching post and arena.
 *
 * The owner stores a pet by using it on the pet house, up to the house's tier limit (3, 5, 7, 9, 12,
 * then all 71); [PetMenagerie] keeps it in the cache's menagerie varps, which also makes the pets
 * module count it as owned. Cats, dogs, pet rocks, pet fish and the wiki's other companions go in
 * as extras instead, twelve at most, in `inv.poh_menagerie_pets`. View opens the real
 * `poh_menagerie` interface, whose list the client draws from those varps and the extras, sent as
 * the owner's mirrored inventory; Take on a pet or extra gives it back, and the roaming toggle flips
 * `varbit.poh_menagerie_closed`. [HousePets] keeps the roaming npcs in step. The pet list names
 * every pet a menagerie can hold and which of them are stored.
 *
 * A pet used on the scratching post comes out to play with it for a while. Two pets used on the
 * arena come out and spar - nothing in it can be hurt - then go back. The pet feeder is
 * [DungeonScript]'s, which looks after everyone in a house every tick.
 */
class MenagerieScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val pets: HousePets,
    private val games: HouseGames,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((tier, house) in PET_HOUSES.withIndex()) {
            onOpLoc1(house) { view() }
            onOpLocU(house) { store(it.objType.id, it.invSlot, CAPACITY[tier]) }
        }
        onOpLoc1(PET_LIST_LOC) { readList() }
        for (post in SCRATCHING_POSTS) {
            onOpLocU(post) { scratch(it.loc.coords, it.objType.id) }
        }
        for (arena in ARENAS) {
            onOpLocU(arena) { enterArena(it.loc.coords, it.objType.id) }
        }
        onIfModalButton(LIST) { take(it) }
        onIfModalButton(ROAMING) { toggleRoaming() }
    }

    private fun ProtectedAccess.owner(): Player? = registry.houseAt(player.coords)?.owner

    private fun ProtectedAccess.store(obj: Int, slot: Int, capacity: Int) {
        if (owner() !== player) {
            mes("Only the owner of this house can keep pets here.")
            return
        }
        if (PetMenagerie.slotOf(obj) == null) {
            storeExtra(obj, slot)
            return
        }
        // Each pet has one place in the pet house; a second of it would be swallowed with nothing to show.
        val petSlot = PetMenagerie.slotOf(obj)
        if (petSlot != null && PetMenagerie.isStored(player, petSlot)) {
            mes("You already keep that pet in your pet house.")
            return
        }
        if (PetMenagerie.stored(player).size >= capacity) {
            mes("Your pet house is full.")
            return
        }
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj)
        if (invDel(inv, name, 1, slot = slot).failure) {
            return
        }
        if (!PetMenagerie.store(player, obj)) {
            invAdd(inv, name, 1)
            return
        }
        mes("You put your pet in the pet house.")
        pets.refresh(player)
    }

    private fun ProtectedAccess.storeExtra(obj: Int, slot: Int) {
        if (!PetMenagerie.isExtra(obj)) {
            mes("That can't live in a pet house.")
            return
        }
        val extras = player.menagerieExtras
        if (extras.freeSpace() <= 0) {
            mes("Your pet house has no room for any more extras.")
            return
        }
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj)
        if (invDel(inv, name, 1, slot = slot).failure) {
            return
        }
        invAdd(extras, name, 1)
        UpdateInventory.updateInvFullMirror(player, extras)
        mes("You put your pet in the pet house.")
        pets.refresh(player)
    }

    private fun ProtectedAccess.view() {
        ifOpenMainModal(MENAGERIE_INTERFACE)
        owner()?.let { UpdateInventory.updateInvFullMirror(player, it.menagerieExtras) }
        runClientScript(script("poh_menagerie_initlist"), LIST.asRSCM(RSCMType.COMPONENT), SCROLLBAR.asRSCM(RSCMType.COMPONENT))
        runClientScript(script("poh_menagerie_initroaming"), ROAMING.asRSCM(RSCMType.COMPONENT))
        ifSetEvents(LIST, 0..MAX_LIST_COMPONENTS, IfEvent.Op1, IfEvent.Op10)
        ifSetEvents(ROAMING, 0..0, IfEvent.Op1)
    }

    private fun ProtectedAccess.take(event: IfModalButton) {
        val obj = event.obj ?: return
        if (event.op != IfButtonOp.Op1) {
            return
        }
        if (owner() !== player) {
            mes("Only the owner of this house can take pets out.")
            return
        }
        if (inv.freeSpace() <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        val slot = PetMenagerie.slotOf(obj.id)
        if (slot == null) {
            takeExtra(obj.id)
            return
        }
        val taken = PetMenagerie.take(player, slot) ?: return
        invAdd(inv, RSCM.getReverseMapping(RSCMType.OBJ, taken), 1)
        pets.refresh(player)
    }

    private fun ProtectedAccess.takeExtra(obj: Int) {
        val extras = player.menagerieExtras
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj)
        if (invDel(extras, name, 1).failure) {
            return
        }
        invAdd(inv, name, 1)
        UpdateInventory.updateInvFullMirror(player, extras)
        pets.refresh(player)
    }

    private fun ProtectedAccess.toggleRoaming() {
        if (owner() !== player) {
            return
        }
        val closed = player.vars[CLOSED_VARBIT]
        VarPlayerIntMapSetter.set(player, CLOSED_VARBIT, 1 - closed)
        pets.refresh(player)
    }

    private fun ProtectedAccess.readList() {
        val owner = owner() ?: return
        val stored = PetMenagerie.stored(owner).toSet()
        val rows =
            (0 until PetMenagerie.size).joinToString("|") { slot ->
                val obj = PetMenagerie.shownObj(owner, slot)
                val name = obj?.let { ServerCacheManager.getItem(it)?.name }.orEmpty()
                if (slot in stored) "$name<br><col=00ff00>In the menagerie</col>" else name
            }
        ifOpenMainModal(PET_LIST_INTERFACE)
        runClientScript(script("poh_menagerie_petlist"), rows)
    }

    // ------------------------------------------------------------------------- playing

    /** The npc a pet obj comes out as, whether a menagerie pet or an extra. */
    private fun petNpc(obj: Int): String? = Pets.forObj(obj)?.second?.npc ?: PetMenagerie.extraNpc(obj)

    private fun ProtectedAccess.scratch(post: CoordGrid, obj: Int) {
        val house = registry.houseAt(player.coords) ?: return
        val npc = petNpc(obj)
        if (npc == null) {
            mes("Nothing happens.")
            return
        }
        val pet = games.spawn(npc, post.translate(1, 0, 0))
        if (Cats.forObj(obj) != null) {
            pet.say("Meow!")
            mes("Your cat has a good scratch at the post.")
        } else {
            mes("Your pet sniffs at the scratching post, unimpressed.")
        }
        games.removeAfter(house.owner, pet, PLAY_TICKS)
    }

    private suspend fun ProtectedAccess.enterArena(arena: CoordGrid, obj: Int) {
        val house = registry.houseAt(player.coords) ?: return
        val npc = petNpc(obj)
        if (npc == null) {
            mes("That can't go in the arena.")
            return
        }
        val fighters = games.of(house.owner).arena
        if (fighters.size >= ARENA_SIZE) {
            mes("There are already two pets in the arena.")
            return
        }
        val pet = games.spawn(npc, arena.translate(if (fighters.isEmpty()) -1 else 1, 0, 0))
        fighters += pet
        if (fighters.size < ARENA_SIZE) {
            // A pet left waiting goes home in the end, rather than holding the arena for good.
            games.removeAfter(house.owner, pet, ARENA_WAIT_TICKS)
            mes("Your pet waits in the arena for an opponent.")
            return
        }
        val (first, second) = fighters.toList()
        fighters.clear()
        // The fight is cosmetic; the pets leave on time even if this script is cut short.
        val fightTicks = (ARENA_ROUNDS + 1) * ROUND_TICKS
        games.removeAfter(house.owner, first, fightTicks)
        games.removeAfter(house.owner, second, fightTicks)
        for (round in 0 until ARENA_ROUNDS) {
            val attacker = if (round % 2 == 0) first else second
            val defender = if (round % 2 == 0) second else first
            attacker.facePathingEntitySquare(defender)
            attacker.say(TAUNTS[round % TAUNTS.size])
            delay(ROUND_TICKS)
        }
        first.say("Good fight!")
        mes("The pets have let off some steam.")
    }

    private fun script(name: String): Int = "clientscript.[clientscript,$name]".asRSCM(RSCMType.CLIENTSCRIPT)

    private companion object {
        val PET_HOUSES = (1..6).map { "loc.poh_menagerie_pethouse_$it" }
        val CAPACITY = listOf(3, 5, 7, 9, 12, 71)

        const val PET_LIST_LOC = "loc.poh_menagerie_petlist_1"
        const val MENAGERIE_INTERFACE = "interface.poh_menagerie"
        const val PET_LIST_INTERFACE = "interface.poh_petlist"
        const val LIST = "component.poh_menagerie:list"
        const val SCROLLBAR = "component.poh_menagerie:scrollbar"
        const val ROAMING = "component.poh_menagerie:roaming"
        const val CLOSED_VARBIT = "varbit.poh_menagerie_closed"

        val SCRATCHING_POSTS = (1..3).map { "loc.poh_menagerie_scratchingpost_$it" }
        val ARENAS = (1..3).map { "loc.poh_menagerie_combatring_$it" } + "loc.poh_menagerie_combatring_mat"
        const val PLAY_TICKS = 10
        const val ARENA_SIZE = 2
        const val ARENA_ROUNDS = 6
        const val ROUND_TICKS = 3
        const val ARENA_WAIT_TICKS = 100
        val TAUNTS = listOf("Grrr!", "Take that!", "Is that all you've got?", "Rawr!", "You'll pay for that!", "Hah!")

        /** The list draws one component per menagerie slot and one per extra slot. */
        const val MAX_LIST_COMPONENTS = 100
    }
}
