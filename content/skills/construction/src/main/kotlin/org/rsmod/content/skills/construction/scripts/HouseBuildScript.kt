package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Buildable
import org.rsmod.content.skills.construction.data.Chapel
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.content.skills.construction.data.Costumes
import org.rsmod.content.skills.construction.data.Costumes.costumeStorage
import org.rsmod.content.skills.construction.data.Flatpacks
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Furniture
import org.rsmod.content.skills.construction.data.FurnitureRows
import org.rsmod.content.skills.construction.data.Gallery
import org.rsmod.content.skills.construction.data.Heraldry
import org.rsmod.content.skills.construction.data.HotspotGroup
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.Leagues
import org.rsmod.content.skills.construction.data.Material
import org.rsmod.content.skills.construction.data.Nexus
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.content.skills.construction.data.Topiary
import org.rsmod.content.skills.construction.data.Trophies
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseLayout
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.content.skills.construction.house.Room
import org.rsmod.content.skills.construction.house.freeBuild
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Building and removing, in build mode.
 *
 * Both halves hang off op five, because that is where the cache puts "Build" on a hotspot and
 * "Remove" on the thing it turns into. A door hotspot builds a whole room into the empty cell on
 * the other side of it; every other hotspot builds furniture where it stands.
 */
class HouseBuildScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val store: HouseStore,
    private val access: HouseAccess,
    private val xpMods: XpModifiers,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (style in HouseStyle.entries) {
            onOpLoc5(style.doorLeft) { buildRoom(it.loc) }
            onOpLoc5(style.doorRight) { buildRoom(it.loc) }
        }
        onOpLoc5(DUNGEON_DOOR) { buildRoom(it.loc) }
        for (loc in hotspotLocs()) {
            onOpLoc5(loc) { buildFurniture(it.loc, loc) }
        }
        val shownForms =
            Combat.Dummy.ORNATE_FORMS.map { it.loc } +
                Nexus.Amulet.entries.flatMap { it.locs } +
                Leagues.DISPLAYS.values.flatten() +
                Gallery.SHOWN_FORMS +
                Topiary.Shape.entries.map { it.loc }
        for (loc in builtLocs() + Chapel.ALTARS.keys + Trophies.DISPLAYS.keys + shownForms) {
            onOpLoc5(loc) { removeFurniture(it.loc, canonical(loc)) }
        }
        for (loc in stairLocs()) {
            onOpLoc4(loc) { removeStairRoom(it.loc) }
        }
        for (loc in Furniture.STAIRS_DOWN.values - Furniture.STAIRS_DOWN.keys) {
            onOpLoc5(loc) { removeStairsTop() }
        }
        val trophyDisplays = Trophies.DISPLAYS.filterValues { (_, tier) -> tier < 3 }.keys
        for (loc in upgradableLocs() + trophyDisplays) {
            onOpLoc4(loc) { upgradeFurniture(it.loc, canonical(loc)) }
        }
        for (interf in PohInterfaces.interfaces) {
            onIfClose(interf) { ClientScripts.chatDefaultRestoreInput(player) }
        }
    }

    /**
     * The furniture table's name for a built [loc] that the house shows in another form: an altar
     * dedicated to another god, a trophy display with a trophy on it, an ornate combat dummy
     * swapped to another form, a mounted amulet showing its left-click teleport, or a league or
     * achievement gallery display with something on it, or a topiary bush clipped into a shape.
     */
    private fun canonical(loc: String): String =
        Trophies.blankOf(loc)
            ?: Combat.Dummy.ofLoc(loc)?.takeIf { it.ornate }?.let { Combat.Dummy.ORNATE_UNDEAD.loc }
            ?: Nexus.Amulet.ofLoc(loc)?.loc
            ?: Leagues.baseOf(loc)
            ?: Gallery.baseOf(loc)
            ?: Topiary.BUSH.takeIf { Topiary.isShape(loc) }
            ?: Chapel.undedicated(loc)

    private fun hotspotLocs(): Set<String> =
        RoomType.entries.flatMapTo(LinkedHashSet()) { room -> room.hotspots.flatMap { it.locs } }

    /** Both halves of every staircase pair; the spiral ones are their own top and bottom. */
    private fun stairLocs(): Set<String> =
        Furniture.STAIRS_DOWN.keys + Furniture.STAIRS_DOWN.values

    /** Every built loc of a piece that has an upgrade after it in its group. */
    private fun upgradableLocs(): Set<String> {
        val hotspots = hotspotLocs()
        return RoomType.entries
            .flatMap { room -> room.hotspots }
            .flatMap { group ->
                val chained =
                    group.options.zipWithNext()
                        .filter { (_, next) -> next.upgradable && next.upgradeFrom == null }
                        .flatMap { it.first.built }
                val branched = group.options.mapNotNull { it.upgradeFrom }.flatMap { group.options[it].built }
                chained + branched
            }
            .filterTo(LinkedHashSet()) { it !in hotspots }
    }

    private fun builtLocs(): Set<String> {
        val hotspots = hotspotLocs()
        return RoomType.entries
            .flatMap { room -> room.hotspots.flatMap { group -> group.options.flatMap { it.built } } }
            .filterTo(LinkedHashSet()) { it !in hotspots }
    }

    // ------------------------------------------------------------------------- rooms

    private suspend fun ProtectedAccess.buildRoom(loc: BoundLocInfo) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only build while your house is in building mode.")
            return
        }
        val cell = registry.cellOf(house, loc.coords) ?: return
        val (floor, gx, gz) = cell
        val side = loc.angleId
        val targetX = gx + Side.deltaX(side)
        val targetZ = gz + Side.deltaZ(side)
        val facing = Side.opposite(side)
        val existing = house.state[floor, targetX, targetZ]
        val size = HouseLayout.sizeOf(player)
        val outside = HouseLayout.areaProblem(targetX, targetZ, size)
        if (existing == null && outside != null) {
            mes(outside)
            return
        }
        if (existing != null) {
            val label = existing.type.label.lowercase()
            when (choice3("Rotate the $label.", ROTATE, "Remove the $label.", REMOVE, "Cancel.", CANCEL)) {
                ROTATE -> rotateRoom(house, floor, targetX, targetZ, existing, facing)
                REMOVE -> removeRoom(house, floor, targetX, targetZ, existing)
            }
            return
        }

        if (!roomToSpare(house)) {
            return
        }
        val selected = PohInterfaces.selectRoom(this) ?: return
        val room = HouseLayout.menagerieFor(house.state, floor, targetX, targetZ, selected)
        // Any rotation offered keeps a door on this doorway, so the first one stands for them all.
        val problem = HouseLayout.addProblem(house.state, floor, targetX, targetZ, room, room.rotationsFacing(facing).first(), size)
        if (problem != null) {
            mes(problem)
            return
        }
        val free = player.freeBuild
        if (!free && player.constructionLvl < room.level) {
            mes("You need a Construction level of ${room.level} to build a ${room.label.lowercase()}.")
            return
        }
        if (!free && invCoinTotal() < room.cost) {
            mes("You need ${room.cost} coins to build a ${room.label.lowercase()}.")
            return
        }
        val rotation =
            chooseRotation(room.rotationsFacing(facing), room.rotationsFacing(facing).first(), CONFIRM_BUILD) { state, turn ->
                state[floor, targetX, targetZ] = Room(room, turn)
            } ?: return
        // The preview is still standing; whatever happens next, the real house goes back up.
        try {
            if (!free && invCoinTotal() < room.cost) {
                mes("You need ${room.cost} coins to build a ${room.label.lowercase()}.")
                return
            }

            anim(Construction.BUILD_ANIM)
            soundSynth(Construction.BUILD_WOOD_SOUND)
            delay(Construction.BUILD_CYCLE)
            resetAnim()

            if (!free && !invTakeFee(room.cost)) {
                return
            }
            store.update(player) { it[floor, targetX, targetZ] = Room(room, rotation) }
            mes("You build a ${room.label.lowercase()}.")
        } finally {
            access.rebuild(this)
        }
    }

    /**
     * Turns a built room in place, keeping its furniture, to any rotation that still leaves a door
     * on the doorway it was reached through, within [HouseLayout]'s rules.
     */
    private suspend fun ProtectedAccess.rotateRoom(house: ActiveHouse, floor: Floor, gx: Int, gz: Int, room: Room, facing: Int) {
        if (HouseLayout.stairsLinked(house.state, floor, gx, gz)) {
            mes("You can't turn a room that a staircase runs through.")
            return
        }
        val rotations = room.type.rotationsFacing(facing)
        if (rotations.size <= 1) {
            mes("That room can only face this way.")
            return
        }
        val rotation =
            chooseRotation(rotations, room.rotation, CONFIRM_ROTATE) { state, turn ->
                state[floor, gx, gz]?.rotation = turn
            } ?: return
        if (rotation == room.rotation) {
            access.rebuild(this)
            return
        }
        val problem = HouseLayout.rotateProblem(house.state, floor, gx, gz, rotation, HouseLayout.sizeOf(player))
        if (problem != null) {
            mes(problem)
            access.rebuild(this)
            return
        }
        store.update(player) { it[floor, gx, gz]?.rotation = rotation }
        mes("You turn the ${room.type.label.lowercase()}.")
        access.rebuild(this)
    }

    /**
     * Shows the house with a room at each of [rotations] in turn, starting from [start], until the
     * player confirms one or backs out. [place] puts the room, at a rotation, into a copy of the
     * house state; the copy is never saved, so a preview left half way - by logging
     * out, say - leaves nothing behind. Returns the chosen rotation, or null when cancelled, in which
     * case the real house has already been put back.
     */
    private suspend fun ProtectedAccess.chooseRotation(
        rotations: List<Int>,
        start: Int,
        confirm: String,
        place: (HouseState, Int) -> Unit,
    ): Int? {
        if (rotations.size == 1) {
            return rotations.single()
        }
        var index = rotations.indexOf(start).coerceAtLeast(0)
        var chosen: Int? = null
        try {
            while (chosen == null) {
                val preview = HouseState.decode(store.state(player).encode())
                place(preview, rotations[index])
                access.rebuild(this, preview = preview)
                when (choice4("Rotate clockwise.", CLOCKWISE, "Rotate anticlockwise.", ANTICLOCKWISE, confirm, CONFIRM, "Cancel.", CANCEL)) {
                    CLOCKWISE -> index = (index + 1) % rotations.size
                    ANTICLOCKWISE -> index = (index - 1 + rotations.size) % rotations.size
                    CONFIRM -> chosen = rotations[index]
                    else -> return null
                }
            }
            return chosen
        } finally {
            // A confirmed choice is saved and rebuilt by the caller; anything else puts the real house back.
            if (chosen == null) {
                access.rebuild(this)
            }
        }
    }

    /**
     * Takes a room back out. Old School offers this on the door leading into it, and on a
     * staircase, which always means the room at the top of that staircase - the one above when you
     * are standing at its foot, and the one you are standing in when you are at its head.
     */
    private suspend fun ProtectedAccess.removeRoom(
        house: ActiveHouse,
        floor: Floor,
        gx: Int,
        gz: Int,
        room: Room,
    ) {
        val costumes = Costumes.Store.entries.any { Costumes.setsStored(it, player.costumeStorage) > 0 }
        val problem = HouseLayout.removalProblem(house.state, floor, gx, gz, costumes, HouseLayout.sizeOf(player))
        if (problem != null) {
            mes(problem)
            return
        }

        val label = room.type.label.lowercase()
        val confirm = choice2("Yes, remove the $label.", true, "No.", false)
        if (!confirm) {
            return
        }

        anim(Construction.BUILD_ANIM)
        soundSynth(Construction.BUILD_WOOD_SOUND)
        delay(Construction.BUILD_CYCLE)
        resetAnim()

        store.update(player) { it[floor, gx, gz] = null }
        mes("You remove the $label.")
        access.rebuild(this)
    }

    private suspend fun ProtectedAccess.removeStairRoom(loc: BoundLocInfo) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only remove rooms while your house is in building mode.")
            return
        }
        val (floor, gx, gz) = registry.cellOf(house, loc.coords) ?: return
        val above = Floor.entries.getOrNull(floor.ordinal + 1)
        // At the head of a staircase - the room has stairs rising into it - the top is the room you
        // stand in; at its foot it is the room above. A dungeon's stairs lead up into the house
        // proper, which is never what they take out.
        val atHead = house.state.hasStairsBelow(floor, gx, gz)
        val target =
            if (floor != Floor.DUNGEON && !atHead && above != null && house.state[above, gx, gz] != null) above
            else floor
        val room = house.state[target, gx, gz] ?: return
        removeRoom(house, target, gx, gz, room)
    }

    // --------------------------------------------------------------------- furniture

    private suspend fun ProtectedAccess.buildFurniture(loc: BoundLocInfo, hotspot: String) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only build while your house is in building mode.")
            return
        }
        val room = roomAt(house, loc) ?: return
        val group = room.type.hotspots.firstOrNull { hotspot in it.locs } ?: return
        if (group.key in room.furniture) {
            mes("Upgrade the ${group.label.removeSuffix(" space").lowercase()} to fill this space.")
            return
        }

        val option = selectFurniture(group, group.options.filterNot { it.upgrade }) ?: return
        if (option.built.any { it in Furniture.STAIRS_DOWN }) {
            val (floor, gx, gz) = registry.cellOf(house, loc.coords) ?: return
            val above = Floor.entries.getOrNull(floor.ordinal + 1)
            if (floor == Floor.UPPER) {
                mes("There is nowhere for stairs to lead from this floor.")
                return
            }
            if (house.state.hasStairsBelow(floor, gx, gz)) {
                mes("This room already has a staircase.")
                return
            }
            // Stairs up raise a room over them, which counts towards the limit like any other.
            if (floor != Floor.DUNGEON && above != null && house.state[above, gx, gz] == null && !roomToSpare(house)) {
                return
            }
            if (floor == Floor.DUNGEON) {
                val above = house.state[Floor.GROUND, gx, gz]
                if (above == null || !(above.type.isHall || above.type.outdoors)) {
                    mes("You need a skill hall, quest hall or garden above to build stairs here.")
                    return
                }
            } else if (
                floor == Floor.GROUND &&
                    room.type.isHall &&
                    (player.freeBuild || player.constructionLvl >= DUNGEON_LEVEL)
            ) {
                val down = choice2("Up", false, "Down", true, title = "Which way should the stairs go?")
                if (down) {
                    buildStairsDown(house, room, group, option, loc, gx, gz)
                    return
                }
            }
            val roomAbove = above?.let { house.state[it, gx, gz] }
            if (floor == Floor.GROUND && roomAbove != null && !roomAbove.type.isHall) {
                mes("Stairs can only lead up into a hall, and the room above isn't one.")
                return
            }
        }
        build(house, room, group, option, loc)
    }

    /**
     * Stairs going down from a ground-floor hall belong to the dungeon stairs room beneath it, which
     * is laid down for free if the cell is empty - as building up raises a room above. The hall then
     * becomes the top of those stairs, exactly as an upper-floor hall is.
     */
    private suspend fun ProtectedAccess.buildStairsDown(
        house: ActiveHouse,
        hall: Room,
        group: HotspotGroup,
        option: Buildable,
        loc: BoundLocInfo,
        gx: Int,
        gz: Int,
    ) {
        val below = house.state[Floor.DUNGEON, gx, gz]
        if (below != null && below.type != RoomType.DUNGEON_STAIRS) {
            mes("There is already a dungeon room below this hall that has no room for stairs.")
            return
        }
        if (below == null && !roomToSpare(house)) {
            return
        }
        build(house, hall, group, option, loc) { state, index ->
            val stairs =
                state[Floor.DUNGEON, gx, gz]
                    ?: Room(RoomType.DUNGEON_STAIRS, hall.rotation).also {
                        state[Floor.DUNGEON, gx, gz] = it
                    }
            stairs.furniture[group.key] = index
        }
    }

    private suspend fun ProtectedAccess.upgradeFurniture(loc: BoundLocInfo, built: String) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only upgrade furniture while your house is in building mode.")
            return
        }
        val room = roomAt(house, loc) ?: return
        val group = builtGroup(house, room, loc, built) ?: return
        val current = room.furniture.getValue(group.key)
        val next =
            group.options.firstOrNull { it.upgradeFrom == current }
                ?: group.options.getOrNull(current + 1)?.takeIf { it.upgradable && it.upgradeFrom == null }
        if (next == null) {
            mes("That can't be upgraded any further.")
            return
        }
        val materials = next.upgradeMaterials ?: next.materials
        val option = selectFurniture(group, listOf(next), materials) ?: return
        build(house, room, group, option, loc, materials)
    }

    private suspend fun ProtectedAccess.build(
        house: ActiveHouse,
        room: Room,
        group: HotspotGroup,
        option: Buildable,
        loc: BoundLocInfo,
        materials: List<Material> = option.materials,
        into: ((HouseState, Int) -> Unit)? = null,
    ) {
        val free = player.freeBuild
        // A flatpack is installed in place of the materials, at any Construction level.
        val flatpack = if (free) null else Flatpacks.of(option)?.takeIf { it in inv }
        val quest = option.quest
        if (!free && quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            val name = QuestRow.getRow("dbrow.$quest").displayname
            mes("You need to complete $name to build that.")
            return
        }
        if (option.built.any(Heraldry::isCrestDecor) && player.familyCrest == 0) {
            mes("You need a family crest to put up a shield. Sir Renitee in Falador castle keeps the records.")
            return
        }
        if (!free && flatpack == null && player.constructionLvl < option.level) {
            mes("You need a Construction level of ${option.level} to build that.")
            return
        }
        val skill = option.skill
        if (!free && skill != null && player.stat(skill) < option.skillLevel) {
            mes("You need a ${statName(skill)} level of ${option.skillLevel} to build that.")
            return
        }
        if (!free && option.wateringCan && !hasWateringCan()) {
            mes("You need a watering can with some water in it to plant that.")
            return
        }
        if (!free && flatpack == null && !hasMaterials(materials)) {
            mes("You do not have the materials to build that.")
            mes(materials.joinToString(", ") { "${it.count} x ${objName(it.obj)}" })
            return
        }

        anim(Construction.BUILD_ANIM)
        soundSynth(option.sound.synth)
        delay(Construction.BUILD_CYCLE)
        resetAnim()

        val taken =
            when {
                free -> true
                flatpack != null -> invDel(inv, flatpack).success
                else -> takeMaterials(materials)
            }
        if (!taken) {
            return
        }
        val index = group.options.indexOf(option)
        val cell = registry.cellOf(house, loc.coords)
        store.update(player) {
            if (into != null) {
                into(it, index)
                return@update
            }
            room.furniture[group.key] = index
            if (cell != null) {
                it.raiseFloorFor(option, room, cell)
            }
        }
        if (!free && flatpack == null) {
            statAdvance(Construction.STAT, option.xp * xpMods.get(player, Construction.STAT))
            if (skill != null && option.skillXp > 0) {
                statAdvance(skill, option.skillXp * xpMods.get(player, skill))
            }
        }
        mes("You build the ${option.label.lowercase()}.")
        access.rebuild(this)
    }

    private suspend fun ProtectedAccess.selectFurniture(
        group: HotspotGroup,
        options: List<Buildable>,
        upgradeMaterials: List<Material>? = null,
    ): Buildable? {
        if (options.size > PohInterfaces.MAX_FURNITURE_ENTRIES) {
            return selectFurnitureFromList(group, options)
        }
        val entries =
            options.map { option ->
                val row = FurnitureRows.of(option) ?: return selectFurnitureFromList(group, options)
                val materials = upgradeMaterials ?: option.materials
                PohInterfaces.FurnitureEntry(
                    row = row.rowId,
                    level = option.level,
                    materials = materials.map { "${objName(it.obj)}: ${it.count}" },
                    buildable =
                        player.freeBuild ||
                            option.quest.let { it == null || QuestRequirements.hasCompleted(player, it) } &&
                            (Flatpacks.of(option)?.let { it in inv } == true ||
                                player.constructionLvl >= option.level &&
                                hasMaterials(materials) &&
                                (!option.wateringCan || hasWateringCan())),
                )
            }
        val slot = PohInterfaces.selectFurniture(this, entries) ?: return null
        return options.getOrNull(slot)
    }

    private suspend fun ProtectedAccess.selectFurnitureFromList(
        group: HotspotGroup,
        options: List<Buildable>,
    ): Buildable? {
        val affordable = options.filter { player.freeBuild || player.constructionLvl >= it.level }
        if (affordable.isEmpty()) {
            val lowest = options.minOf { it.level }
            mes("You need a Construction level of $lowest to build anything here.")
            return null
        }
        val choice =
            menu(
                group.label,
                hotkeys = true,
                choices = affordable.map { "${it.label} (level ${it.level})" },
            )
        val option = affordable.getOrNull(choice) ?: return null
        // The list modal stays open server-side after a choice, and a `delay` that captured it
        // would lose protected access the moment the client's close reaches us.
        ifClose()
        return option
    }

    private suspend fun ProtectedAccess.removeFurniture(loc: BoundLocInfo, built: String) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only remove furniture while your house is in building mode.")
            return
        }
        val room = roomAt(house, loc) ?: return
        val group = builtGroup(house, room, loc, built) ?: return
        if (built in Furniture.STAIRS_DOWN && houseAbove(house, loc)) {
            mes("You must remove the rooms above before you can take out the staircase.")
            return
        }
        if (built in Furniture.STAIRS_DOWN || built in Furniture.STAIRS_DOWN.values) {
            val after = HouseState.decode(house.state.encode())
            registry.cellOf(house, loc.coords)?.let { (floor, gx, gz) -> after[floor, gx, gz]?.furniture?.remove(group.key) }
            val size = HouseLayout.sizeOf(player)
            if (Floor.entries.any { HouseLayout.cutOff(after, it, size) > HouseLayout.cutOff(house.state, it, size) }) {
                mes("Taking out the staircase would leave part of your house with no way in.")
                return
            }
        }
        if (house.state.isEntrance(room) && group.key == HouseState.CENTREPIECE &&
            house.state.rooms.values.count(house.state::isEntrance) <= 1
        ) {
            mes("You cannot remove your house's only exit portal.")
            return
        }
        val costumes = Costumes.Store.ofGroup(group.key)
        if (costumes != null && Costumes.setsStored(costumes, player.costumeStorage) > 0) {
            mes("You must take everything out of it before you can remove it.")
            return
        }

        val confirm = choice2("Yes, remove it.", true, "No.", false)
        if (!confirm) {
            return
        }

        anim(Construction.BUILD_ANIM)
        delay(Construction.BUILD_CYCLE)
        resetAnim()

        val removed = group.options.getOrNull(room.furniture.getValue(group.key))
        store.update(player) { room.furniture.remove(group.key) }
        // Free build took nothing for the piece, so it gives nothing back for it either.
        if (!player.freeBuild) {
            for (refund in removed?.refund.orEmpty()) {
                invAddOrDrop(objRepo, refund.obj, refund.count)
            }
        }
        mes("You remove the ${group.label.removeSuffix(" space").lowercase()}.")
        access.rebuild(this)
    }

    /** The head of a staircase belongs to the one below, which this room always stands on. */
    private fun ProtectedAccess.removeStairsTop() {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only remove furniture while your house is in building mode.")
            return
        }
        mes("You must remove this room before you can take out the staircase.")
    }

    private fun roomAt(house: ActiveHouse, loc: BoundLocInfo): Room? {
        val (floor, gx, gz) = registry.cellOf(house, loc.coords) ?: return null
        return house.state[floor, gx, gz]
    }

    /**
     * The group [built], clicked at [loc], was built on: the one the house dressed that tile from,
     * falling back to the first group in the room that builds it.
     */
    private fun builtGroup(house: ActiveHouse, room: Room, loc: BoundLocInfo, built: String): HotspotGroup? =
        house.hotspotAt[loc.coords]?.let(room.type::hotspot)
            ?.takeIf { group -> group.key in room.furniture && group.options.any { built in it.built } }
            ?: room.type.hotspots.firstOrNull { group -> group.options.any { built in it.built } && group.key in room.furniture }

    /**
     * Whether a staircase at [loc] has a room standing at its top that would be left stranded. A
     * dungeon staircase rises into the ground floor, which it never holds up.
     */
    private fun houseAbove(house: ActiveHouse, loc: BoundLocInfo): Boolean {
        val (floor, gx, gz) = registry.cellOf(house, loc.coords) ?: return false
        if (floor == Floor.DUNGEON) {
            return false
        }
        val above = Floor.entries.getOrNull(floor.ordinal + 1) ?: return false
        return house.state[above, gx, gz] != null
    }

    /**
     * Builds the matching room directly above a new staircase. Rooms can only be attached to a door
     * that already exists, so without this first landing the upper floor would be unreachable no
     * matter how many staircases the house had.
     */
    private fun HouseState.raiseFloorFor(
        option: Buildable,
        room: Room,
        cell: Triple<Floor, Int, Int>,
    ) {
        if (option.built.none { it in Furniture.STAIRS_DOWN }) {
            return
        }
        val (floor, gx, gz) = cell
        val above = Floor.entries.getOrNull(floor.ordinal + 1) ?: return
        if (this[above, gx, gz] != null) {
            return
        }
        this[above, gx, gz] = Room(room.type, room.rotation)
    }

    private fun ProtectedAccess.roomToSpare(house: ActiveHouse): Boolean {
        val problem = HouseLayout.roomLimitProblem(house.state, player) ?: return true
        mes(problem)
        return false
    }

    private fun ProtectedAccess.hasWateringCan(): Boolean = Construction.WATERING_CANS.any { inv.contains(it) }

    private fun ProtectedAccess.hasMaterials(materials: List<Material>): Boolean =
        materials.all { invTotal(inv, it.obj) >= it.count }

    private fun ProtectedAccess.takeMaterials(materials: List<Material>): Boolean {
        if (!hasMaterials(materials)) {
            return false
        }
        for (material in materials) {
            if (invDel(inv, material.obj, material.count).failure) {
                return false
            }
        }
        return true
    }

    private fun statName(stat: String): String =
        stat.removePrefix("stat.").replaceFirstChar { it.uppercase() }

    private fun objName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name
            ?: obj.removePrefix("obj.").replace('_', ' ')

    private companion object {
        const val DUNGEON_DOOR = "loc.poh_hotspot_door_dungeon"

        const val ROTATE = 1
        const val REMOVE = 2
        const val CANCEL = 0
        const val CLOCKWISE = 1
        const val ANTICLOCKWISE = 2
        const val CONFIRM = 3
        const val CONFIRM_BUILD = "Build it here."
        const val CONFIRM_ROTATE = "Leave it like this."

        /** Stairs can only be built going down once a dungeon can be built under them. */
        const val DUNGEON_LEVEL = 70
    }
}
