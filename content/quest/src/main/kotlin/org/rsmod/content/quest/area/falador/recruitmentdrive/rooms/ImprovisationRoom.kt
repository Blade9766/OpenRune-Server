package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestingGrounds
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdCheeversTalks
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Alchemy.Layer
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Miss Cheevers's test of improvisation: out through two doors with what the room's shelves,
 * bookcases, crates and chest hold.
 *
 * - First door (`loc.rd_stone_door`, a multiloc on `varbit.rd_room6_stone_door`): burn the metal
 *   spade's handle off at the Bunsen burner, slide the head into the door's hole, pour cupric sulfate
 *   then a vial of liquid onto it so it heats and jams, and pull it as a handle.
 * - Second door (`loc.rd_room6_exitdoor`): a bronze key cast from the chained key (see [Alchemy]).
 *
 * The limited chemicals are tracked on the cache's own `varbit.rd_got_*` and `varbit.rd_spare_water`,
 * so each vial can be taken once per visit; the tools, notes and tin come back from their bookcase,
 * crate or chest whenever the player no longer carries one. Every use checks the room, the attempt
 * and the exact inventory slots before anything is consumed.
 */
@Singleton
class ImprovisationRoom
@Inject
constructor(
    private val testing: RecruitmentTesting,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript(), TestRoomScript {
    override val room = TestRoom.IMPROVISATION

    /** A shelf of chemicals: the vials on it, and the var counting what has been taken. */
    enum class Shelf(val loc: String, val vials: List<Pair<String, String>>) {
        NORTH_1("loc.rd_shelves_chemicals_1", listOf(Alchemy.ACETIC_ACID to "varbit.rd_got_vinegar", Alchemy.LIQUID to "varbit.rd_got_water")),
        NORTH_2("loc.rd_shelves_chemicals_2", listOf(Alchemy.CUPRIC_SULFATE to "varbit.rd_got_copsulph")),
        NORTH_3("loc.rd_shelves_chemicals_3", listOf(Alchemy.GYPSUM to "varbit.rd_got_gypsum")),
        NORTH_4("loc.rd_shelves_chemicals_4", listOf(Alchemy.SALT to "varbit.rd_got_salt")),
        SOUTH_1("loc.rd_shelves_chemicals_5", emptyList()),
        SOUTH_2("loc.rd_shelves_chemicals_6", listOf(Alchemy.COPPER_POWDER to "varbit.rd_got_copore")),
        SOUTH_3("loc.rd_shelves_chemicals_7", listOf(Alchemy.TIN_POWDER to "varbit.rd_got_tin")),
        SOUTH_4("loc.rd_shelves_chemicals_8", listOf(Alchemy.NITROUS_OXIDE to "varbit.rd_got_n2o")),
    }

    override fun ScriptContext.startup() {
        testing.register(this@ImprovisationRoom)
        onOpNpc1(CHEEVERS) { startDialogue(it.npc) { talk() } }
        for (shelf in Shelf.entries) {
            onOpLoc1(shelf.loc) { if (allowed(it.loc)) searchShelf(shelf) }
        }
        for ((bookcase, item) in BOOKCASES) {
            onOpLoc1(bookcase) { if (allowed(it.loc)) searchBookcase(item) }
        }
        for (crate in CRATES) {
            onOpLoc1(crate) { if (allowed(it.loc)) searchCrate(it.loc) }
        }
        onOpLoc1(CHEST_CLOSED) { if (allowed(it.loc)) openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { if (allowed(it.loc)) searchChest() }
        onOpLoc2(CHEST_OPEN) { if (allowed(it.loc)) locRepo.change(it.loc, CHEST_CLOSED, TestingGrounds.OBJ_DURATION) }
        onOpLoc1(STONE_DOOR_BASIC) { if (allowed(it.loc)) studyDoor() }
        onOpLoc1(STONE_DOOR_SPADE) { if (allowed(it.loc)) pullSpade() }
        onOpLoc1(STONE_DOOR_OPEN) { if (allowed(it.loc)) walkThroughStoneDoor() }
        onOpLocU(STONE_DOOR_BASIC) { if (allowed(it.loc)) useOnStoneDoor(it.objType.id, it.invSlot) }
        onOpLocU(STONE_DOOR_SPADE) { if (allowed(it.loc)) useOnStoneDoor(it.objType.id, it.invSlot) }
        onOpLocU(BUNSEN_BURNER) { if (allowed(it.loc)) heat(it.objType.id, it.invSlot) }
        onOpLocU(CHAINED_KEY) { if (allowed(it.loc)) takeImpression(it.objType.id, it.invSlot) }
        onOpLoc1(room.exitDoor) { openExitDoor() }
        onOpLocU(room.exitDoor) { openExitDoor() }
        for (item in COMBINABLE) {
            onOpHeldU(item) { combine(it.first.id, it.firstSlot, it.second.id, it.secondSlot) }
        }
        onOpHeld1(Alchemy.NITROUS_OXIDE) { uncork(it.slot) }
        onOpHeld1(NOTES) { readNotes() }
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        testing.grounds.spawnObj(player, SPADE, SPADE_TILE)
        testing.grounds.spawnObj(player, Alchemy.EMPTY_VIAL, VIAL_TILE)
        val cheevers = testing.grounds.observer(player, room) ?: return
        startDialogue(cheevers) {
            chatNpc(happy, "Greetings, ${player.displayName}. Welcome to my challenge.")
            chatNpc(neutral, "All you need to do is leave from the opposite door to where you came in by.")
            chatNpc(neutral, "I will warn you that this is more complicated than it may at first appear.")
            chatNpc(neutral, "I should also warn you that there are limited supplies of the items in this room, so think carefully before using them, or you may find yourself stuck, and have to leave to start again!")
            chatNpc(happy, "Best of luck!")
        }
    }

    private fun ProtectedAccess.allowed(loc: BoundLocInfo): Boolean =
        testing.testing(player, room) && testing.inRoom(player, loc.coords, room)

    private fun has(player: Player, obj: String): Boolean = player.inv.contains(obj)

    private suspend fun <T> ProtectedAccess.ask(question: suspend Dialogue.() -> T): T? {
        var answer: T? = null
        startDialogue { answer = question() }
        return answer
    }

    private fun ProtectedAccess.give(obj: String): Boolean {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return false
        }
        return invAdd(inv, obj).success
    }

    /* Miss Cheevers */

    private suspend fun Dialogue.talk() {
        if (!testing.testing(player, room)) {
            return
        }
        if (player.rdCheeversTalks == 0) {
            chatPlayer(quiz, "Can you give me any help?")
            chatNpc(neutral, "No, I am sorry, but that is forbidden by our rules.")
            chatNpc(neutral, "If you are having a particularly tough time of it, I suggest you leave and come back later when you are in a more receptive frame of mind.")
            chatNpc(neutral, "Sometimes a break from concentration will yield fresh insight. Our aim is to test you, but not to the point of frustration!")
            chatPlayer(happy, "Okay, thanks!")
            player.rdCheeversTalks = 1
            return
        }
        chatPlayer(worried, "Please... I am REALLY stuck... Isn't there ANYTHING you can do to help me...?")
        chatNpc(neutral, "Well... Look, I really shouldn't say anything about this room, but...")
        chatNpc(neutral, "When I was attempting to join the Temple Knights I myself had to do this puzzle myself.")
        chatNpc(neutral, "It was slightly different, but the idea behind it was the same, and I left the notes I had made while doing it hidden in one of the bookcases.")
        chatNpc(neutral, "If you look carefully you may find them, and they may be of some use to you.")
        chatNpc(neutral, "I really can't be any more help than that I'm afraid, it is more than my job's worth to have given you the help I already have.")
        RecruitmentState.set(player, HINT_VARBIT, 1)
        chatPlayer(happy, "Okay, thanks a lot, you've been very helpful!")
        chatNpc(happy, "Best of luck with the test ${player.displayName}, I hope your application is successful.")
    }

    /* Searching */

    private suspend fun ProtectedAccess.searchShelf(shelf: Shelf) {
        if (shelf == Shelf.SOUTH_1) {
            searchWaterShelf()
            return
        }
        val left = shelf.vials.filter { (_, taken) -> player.vars[taken] == 0 }
        if (left.isEmpty()) {
            startDialogue { mesbox("There is nothing of interest on these shelves.") }
            return
        }
        if (left.size == 1) {
            val (vial, taken) = left.single()
            val take =
                ask {
                    mesbox("There is a vial on this shelf.")
                    choice2("YES", true, "NO", false, title = "Take the vial?")
                }
            if (take == true && testing.testing(player, room) && player.vars[taken] == 0 && give(vial)) {
                RecruitmentState.set(player, taken, 1)
            }
            return
        }
        val (first, firstTaken) = left[0]
        val (second, secondTaken) = left[1]
        val pick =
            ask {
                mesbox("There are two vials on this shelf.")
                choice3("Take the first vial.", 1, "Take the second vial.", 2, "Take both vials.", 3, title = "Take the vials?")
            } ?: return
        if (!testing.testing(player, room)) return
        if ((pick == 1 || pick == 3) && player.vars[firstTaken] == 0 && give(first)) RecruitmentState.set(player, firstTaken, 1)
        if ((pick == 2 || pick == 3) && player.vars[secondTaken] == 0 && give(second)) RecruitmentState.set(player, secondTaken, 1)
    }

    private suspend fun ProtectedAccess.searchWaterShelf() {
        val remaining = WATER_ON_SHELF - player.vars[SPARE_WATER]
        if (remaining <= 0) {
            startDialogue { mesbox("There is nothing of interest on these shelves.") }
            return
        }
        val count =
            ask {
                when (remaining) {
                    1 -> {
                        mesbox("There is a vial on this shelf.")
                        if (choice2("YES", true, "NO", false, title = "Take the vial?")) 1 else 0
                    }
                    2 -> {
                        mesbox("There are two vials on this shelf.")
                        choice3("Take one vial", 1, "Take both vials", 2, "Don't take a vial.", 0, title = "Take the vials?")
                    }
                    else -> {
                        mesbox("There are three vials on this shelf.")
                        choice4("Take one vial", 1, "Take two vials", 2, "Take all three vials", 3, "Don't take a vial.", 0, title = "Take the vials?")
                    }
                }
            } ?: return
        if (!testing.testing(player, room)) return
        repeat(count.coerceAtMost(WATER_ON_SHELF - player.vars[SPARE_WATER])) {
            if (!give(Alchemy.LIQUID)) return
            RecruitmentState.set(player, SPARE_WATER, player.vars[SPARE_WATER] + 1)
        }
    }

    private fun ProtectedAccess.searchBookcase(item: String?) {
        mes("You search the bookshelves...")
        val found = item?.takeIf { it != NOTES || player.vars[HINT_VARBIT] == 1 }
        if (found == null || has(player, found)) {
            mes("You don't find anything interesting.")
            return
        }
        if (!give(found)) return
        mes(
            when (found) {
                NOTES -> "You find a book that looks like it might be helpful."
                Alchemy.MAGNET -> "Hidden amongst the books you find a magnet."
                else -> "Hidden amongst the books you find a knife."
            },
        )
    }

    private fun ProtectedAccess.searchCrate(crate: BoundLocInfo) {
        mes("You search the crate...")
        val world = testing.worldOf(player, crate.coords)
        val item = CRATE_CONTENTS[world]
        val carrying = item != null && (if (item == Alchemy.TIN) Alchemy.TINS.any { has(player, it) } else has(player, item))
        if (item == null || carrying) {
            mes("You don't find anything interesting.")
            return
        }
        if (!give(item)) return
        if (item == Alchemy.TIN) {
            setLayer(player, Layer.NONE)
        }
        mes(
            when (item) {
                Alchemy.TIN -> "Inside the crate you find a tin."
                Alchemy.WIRE -> "Inside the crate you find some wire."
                else -> "Inside the crate you find a chisel."
            },
        )
    }

    private fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        locRepo.change(chest, CHEST_OPEN, TestingGrounds.OBJ_DURATION)
    }

    private fun ProtectedAccess.searchChest() {
        mes("You search the chest...")
        if (has(player, SHEARS)) {
            mes("You don't find anything interesting.")
            return
        }
        if (give(SHEARS)) mes("Inside the chest you find some shears.")
    }

    /* The stone door */

    private fun stoneDoor(player: Player): Int = player.vars[STONE_DOOR_VARBIT]

    private suspend fun ProtectedAccess.studyDoor() {
        startDialogue { mesbox("There is a stone slab here obstructing the door. There is a small hole in the slab that looks like it might be for a handle.") }
    }

    private suspend fun ProtectedAccess.useOnStoneDoor(obj: Int, slot: Int) {
        if (inv[slot]?.id != obj) return
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj)
        val state = stoneDoor(player)
        when {
            name == SPADE -> startDialogue { chatPlayer(confused, "I don't think I'll be able to smash my way through the stone with a spade...") }
            name == SPADE_HEAD && state == DOOR_EMPTY -> {
                if (invDel(inv, SPADE_HEAD, slot = slot).failure) return
                RecruitmentState.set(player, STONE_DOOR_VARBIT, DOOR_SPADE)
                RecruitmentState.set(player, REACT_VARBIT, 0)
                mes("You slide the spade into the hole in the stone...")
                mes("It's nearly a perfect fit!")
            }
            name == Alchemy.CUPRIC_SULFATE && state == DOOR_SPADE -> {
                if (invDel(inv, Alchemy.CUPRIC_SULFATE, slot = slot).failure) return
                RecruitmentState.set(player, REACT_VARBIT, 1)
                mes("You pour the vial onto the flat part of the spade.")
            }
            name == Alchemy.LIQUID && state == DOOR_SPADE -> {
                if (invReplace(inv, Alchemy.LIQUID, 1, Alchemy.EMPTY_VIAL).failure) return
                mes("You pour the vial onto the flat part of the spade.")
                if (player.vars[REACT_VARBIT] == 1) {
                    RecruitmentState.set(player, STONE_DOOR_VARBIT, DOOR_EXPANDED)
                    mes("Something caused a reaction when mixed!")
                    mes("The spade gets hotter, and expands slightly.")
                }
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.pullSpade() {
        mes("You pull on the spade...")
        when (stoneDoor(player)) {
            DOOR_SPADE -> {
                if (!give(SPADE_HEAD)) return
                RecruitmentState.set(player, STONE_DOOR_VARBIT, DOOR_EMPTY)
                RecruitmentState.set(player, REACT_VARBIT, 0)
                mes("It comes loose, and slides out of the hole in the stone.")
            }
            DOOR_EXPANDED -> {
                RecruitmentState.set(player, STONE_DOOR_VARBIT, DOOR_OPEN)
                mes("It works as a handle, and you swing the stone door open.")
            }
        }
    }

    private fun ProtectedAccess.walkThroughStoneDoor() {
        if (stoneDoor(player) != DOOR_OPEN) return
        val world = testing.worldOf(player, player.coords) ?: return
        val target = if (world.x < STONE_DOOR_X) BETWEEN_DOORS else BEFORE_STONE_DOOR
        with(testing.grounds) { moveTo(target) }
    }

    /* The second door */

    private suspend fun ProtectedAccess.openExitDoor() {
        if (testing.passedHere(player, room)) {
            with(testing) { proceed(room) }
            return
        }
        if (!testing.testing(player, room)) {
            return
        }
        val world = testing.worldOf(player, player.coords) ?: return
        if (stoneDoor(player) != DOOR_OPEN || world.x <= STONE_DOOR_X || !has(player, Alchemy.BRONZE_KEY)) {
            mes("This door is locked.")
            return
        }
        val attempt = testing.attempt(player)
        mes("You use the duplicate key you made to unlock the door.")
        with(testing) { passRoom(room, attempt) }
    }

    /* The Bunsen burner and the chained key */

    private fun ProtectedAccess.heat(obj: Int, slot: Int) {
        if (inv[slot]?.id != obj) return
        when (RSCM.getReverseMapping(RSCMType.OBJ, obj)) {
            SPADE -> {
                if (invReplace(inv, slot, 1, type(SPADE_HEAD)).failure) return
                mes("You burn the wooden handle away from the spade...")
                invAddOrDrop(objRepo, ASHES)
                mes("...and are left with a metal spade with no handle.")
            }
            Alchemy.TIN_UNHEATED -> {
                if (invReplace(inv, slot, 1, type(Alchemy.TIN_KEY)).failure) return
                mes("You heat the two powdered ores together in the tin.")
                mes("You make a duplicate of the key in bronze.")
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.takeImpression(obj: Int, slot: Int) {
        if (inv[slot]?.id != obj) return
        if (RSCM.getReverseMapping(RSCMType.OBJ, obj) != Alchemy.TIN_HARDENING) {
            mes("Nothing interesting happens.")
            return
        }
        if (invReplace(inv, slot, 1, type(Alchemy.TIN_IMPRESSION)).failure) return
        mes("You make an impression of the key as the white mixture hardens.")
    }

    /* Using items together */

    private suspend fun ProtectedAccess.combine(first: Int, firstSlot: Int, second: Int, secondSlot: Int) {
        if (!testing.testing(player, room) || inv[firstSlot]?.id != first || inv[secondSlot]?.id != second || firstSlot == secondSlot) {
            mes("Nothing interesting happens.")
            return
        }
        val a = RSCM.getReverseMapping(RSCMType.OBJ, first)
        val b = RSCM.getReverseMapping(RSCMType.OBJ, second)
        when (val result = Alchemy.combine(a, b, layer(player))) {
            is Alchemy.Result.Change -> apply(result, mapOf(a to firstSlot, b to secondSlot))
            is Alchemy.Result.Say -> startDialogue { chatPlayer(confused, result.text) }
            is Alchemy.Result.Message -> mes(result.text)
            Alchemy.Result.Laugh -> laugh()
            Alchemy.Result.Nothing -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.apply(change: Alchemy.Result.Change, slots: Map<String, Int>) {
        if (change.adds != null && inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        for ((item, becomes) in change.into) {
            val slot = slots.getValue(item)
            when {
                becomes == null -> if (invDel(inv, item, slot = slot).failure) return
                becomes != item -> if (invReplace(inv, slot, 1, type(becomes)).failure) return
            }
        }
        change.adds?.let { invAdd(inv, it) }
        change.layer?.let { setLayer(player, it) }
        change.messages.forEach { mes(it) }
    }

    private fun ProtectedAccess.uncork(slot: Int) {
        if (!testing.testing(player, room) || inv[slot]?.id != Alchemy.NITROUS_OXIDE.asRSCM(RSCMType.OBJ)) return
        if (invReplace(inv, slot, 1, type(Alchemy.EMPTY_VIAL)).failure) return
        mes("You uncork the vial...")
        mes("You smell a strange gas as it escapes from inside the vial.")
        laugh()
    }

    private fun ProtectedAccess.laugh() {
        anim(LAUGH_SEQ)
        say("hahahahahahaha!")
    }

    private suspend fun ProtectedAccess.readNotes() {
        startDialogue {
            for (page in NOTE_PAGES) {
                mesbox(page)
            }
        }
    }

    companion object {
        const val CHEEVERS = "npc.rd_observer_room_6"

        const val SPADE = "obj.rd_metal_spade"
        const val SPADE_HEAD = "obj.rd_metal_spade_no_handle"
        const val NOTES = "obj.rd_chem_book"
        const val SHEARS = "obj.rd_shears"
        const val ASHES = "obj.ashes"

        const val STONE_DOOR_BASIC = "loc.rd_stone_door_basic"
        const val STONE_DOOR_SPADE = "loc.rd_stone_door_spade"
        const val STONE_DOOR_OPEN = "loc.rd_stone_door_opened"
        const val BUNSEN_BURNER = "loc.rd_wooden_table_bunsen_burner"
        const val CHAINED_KEY = "loc.rd_key_chained"
        const val CHEST_CLOSED = "loc.rd_chest_closed"
        const val CHEST_OPEN = "loc.rd_chest_open"

        const val STONE_DOOR_VARBIT = "varbit.rd_room6_stone_door"
        const val REACT_VARBIT = "varbit.rd_react_on_spade"
        const val HINT_VARBIT = "varbit.rd_room6_hint1"
        const val SPARE_WATER = "varbit.rd_spare_water"
        const val GYPSUM_IN_TIN = "varbit.rd_gypsum_in_tin"
        const val WATER_IN_TIN = "varbit.rd_water_in_tin"

        /** `varbit.rd_room6_stone_door`: the hole, the spade in it, the spade jammed, the door open. */
        const val DOOR_EMPTY = 0
        const val DOOR_SPADE = 1
        const val DOOR_EXPANDED = 2
        const val DOOR_OPEN = 3

        const val WATER_ON_SHELF = 3
        const val LAUGH_SEQ = "seq.emote_laugh"

        /** The stone door fills x 2477; the second door is the wall east of x 2478. */
        const val STONE_DOOR_X = 2477
        val BEFORE_STONE_DOOR = CoordGrid(2476, 4940, 0)
        val BETWEEN_DOORS = CoordGrid(2478, 4940, 0)

        /** Where the cache map spawns the spade and the empty vial on the burner's table. */
        val SPADE_TILE = CoordGrid(2473, 4941, 0)
        val VIAL_TILE = CoordGrid(2472, 4941, 0)

        /**
         * The four old bookcases: the north-west one hides a magnet, the one directly north of Miss
         * Cheevers (with the toy horse) her notes once she has mentioned them, the south-west one a
         * knife, and the last nothing.
         */
        val BOOKCASES =
            listOf(
                "loc.rd_bookshelf_old_tall" to Alchemy.MAGNET,
                "loc.rd_bookshelf_old_tall2" to NOTES,
                "loc.rd_bookshelf_old_tall3" to Alchemy.KNIFE,
                "loc.rd_bookshelf_old_tall4" to null,
            )

        val CRATES = listOf("loc.rd_large_crate", "loc.rd_large_crates", "loc.rd_small_crates")

        /**
         * What the crates hold, by tile: the cake tin in the middle-sized crate beside the chest in the
         * north-east corner, the wire in the crates next to it and the chisel in the southernmost
         * crate. The other crates are empty.
         */
        val CRATE_CONTENTS =
            mapOf(
                CoordGrid(2476, 4943, 0) to Alchemy.TIN,
                CoordGrid(2475, 4943, 0) to Alchemy.WIRE,
                CoordGrid(2476, 4937, 0) to Alchemy.CHISEL,
            )

        /** Every item the room hands out that can be used on another. */
        val COMBINABLE =
            (Alchemy.FILLED_VIALS + Alchemy.TINS + Alchemy.KEY_TOOLS + Alchemy.MAGNET + SPADE + SPADE_HEAD + NOTES + SHEARS + Alchemy.BRONZE_KEY).toList()

        /** Miss Cheevers's notes, in short: which pairs gave off heat, and the two that made something. */
        val NOTE_PAGES =
            listOf(
                "Most pairs of these chemicals did nothing at all at room temperature: acetic acid, sodium chloride and the ore powders with each other or with anything else.",
                "Cupric Sulfate and Dihydrogen Monoxide: exothermic. A blue compound, and a good deal of heat.",
                "Gypsum and Dihydrogen Monoxide: exothermic. A white liquid that soon set into a hard, heat resistant solid, much like plaster.",
                "Cupric Ore Powder and Tin Ore Powder: heated together over my burner, the two powders made bronze far more easily than ore in a furnace.",
                "Nitrous Oxide: no results; the gas always escaped as soon as the vial was opened.",
            )

        fun layer(player: Player): Layer =
            when {
                player.vars[GYPSUM_IN_TIN] == 1 -> Layer.GYPSUM
                player.vars[WATER_IN_TIN] == 1 -> Layer.LIQUID
                else -> Layer.NONE
            }

        fun setLayer(player: Player, layer: Layer) {
            RecruitmentState.set(player, GYPSUM_IN_TIN, if (layer == Layer.GYPSUM) 1 else 0)
            RecruitmentState.set(player, WATER_IN_TIN, if (layer == Layer.LIQUID) 1 else 0)
        }

        private fun type(obj: String) = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { obj }
    }
}
