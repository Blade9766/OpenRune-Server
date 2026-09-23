package org.rsmod.content.quest.area.burthorpe.heroesquest

import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_LOOTED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_MANSION
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_REPORTED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.CANDLESTICK
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.GRIP_KEYS
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.MISC_KEY
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.WHISKY
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.GARV
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.GRIP
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.garvAtDoor
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Scarface Pete's mansion in Brimhaven: Garv's front door, the blocked side entrance off Mr
 * Olbors' garden, the treasure room, Grip's drinks cabinet and the chest of candlesticks.
 *
 * The cabinet is the lure: a pirate guard warns the deputy off, Grip storms over to it, and he
 * ends up standing in front of the arrow slit in the side entrance's hidden room, where a Phoenix
 * player can shoot him.
 */
class ScarfaceMansion
@Inject
constructor(
    private val heroes: HeroesQuest,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val search: NpcSearch,
    private val routeFactory: RouteFactory,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(FRONT_DOOR) { frontDoor(it.vis, it.type) }
        onOpLoc1(SIDE_DOOR) { sideDoor(it.vis, it.type, keyUsed = false) }
        onOpLocU(SIDE_DOOR, MISC_KEY) { sideDoor(it.vis, it.type, keyUsed = true) }
        onOpLoc1(TREASURE_DOOR) { treasureDoor(it.vis, it.type, keyUsed = false) }
        onOpLocU(TREASURE_DOOR, GRIP_KEYS) { treasureDoor(it.vis, it.type, keyUsed = true) }
        onOpLoc1(CABINET_SHUT) { openCabinet(it.vis) }
        onOpLoc1(CABINET_OPEN) { rummage() }
        onOpLoc2(CABINET_OPEN) { shut(it.vis, CABINET_SHUT, CUPBOARD_CLOSE_SOUND) }
        onPlayerQueue(CABINET_QUEUE) { rummage() }
        onOpLoc1(CHEST_SHUT) { openChest(it.vis) }
        onOpLoc1(CHEST_OPEN) { searchChest(it.vis) }
        onOpLoc2(CHEST_OPEN) { shut(it.vis, CHEST_SHUT, CHEST_CLOSE_SOUND) }
    }

    /* Doors */

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.z > door.coords.z
        if (leaving || heroes.blackArmAt(player, BLACKARM_MANSION)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        if (heroes.isComplete(player) && heroes.isPhoenix(player)) {
            mes("You don't have any reason to go in there.")
            return
        }
        val garv = npcFind(coords, GARV, GARV_RANGE, HuntVis.Off, search)
        if (garv == null) {
            soundSynth(LOCKED_SOUND)
            mes("The door is locked.")
            return
        }
        var admitted = false
        startDialogue(garv) {
            chatNpc(angry, "Oi! Where do you think you're going pal?")
            admitted = garvAtDoor(heroes)
        }
        if (admitted) {
            with(passages) { walkThrough(door, type) }
        }
    }

    private suspend fun ProtectedAccess.sideDoor(
        door: BoundLocInfo,
        type: ObjectServerType,
        keyUsed: Boolean,
    ) {
        val leaving = coords.z >= door.coords.z
        if (leaving) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (keyUsed) {
            soundSynth(UNLOCK_SOUND)
            mes("You unlock the door.")
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        soundSynth(LOCKED_SOUND)
        mesbox("This door is locked.")
        startDialogue {
            chatPlayer(
                neutral,
                "This room isn't a lot of use on its own... Maybe I can get extra help from the " +
                    "inside somehow... I wonder if any of the other players have found a way in.",
            )
        }
    }

    private suspend fun ProtectedAccess.treasureDoor(
        door: BoundLocInfo,
        type: ObjectServerType,
        keyUsed: Boolean,
    ) {
        val leaving = coords.x >= door.coords.x
        if (leaving) {
            mes("The door locks shut behind you.")
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        if (!keyUsed) {
            soundSynth(LOCKED_SOUND)
            mes("The door is locked.")
            return
        }
        if (!heroes.blackArmAt(player, BLACKARM_REPORTED)) {
            mes("The key doesn't fit.")
            return
        }
        soundSynth(UNLOCK_SOUND)
        mes("Grip's key unlocks the door.")
        with(passages) { walkThrough(door, type) }
    }

    /* Grip's drinks cabinet */

    private suspend fun ProtectedAccess.openCabinet(cabinet: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_CUPBOARD_SEQ)
        soundSynth(CUPBOARD_OPEN_SOUND)
        player.queue(CABINET_QUEUE, 1)
        locRepo.change(cabinet, CABINET_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.rummage() {
        val grip = npcFind(coords, GRIP, GRIP_RANGE, HuntVis.Off, search)
        val guard = grip?.let { npcFind(coords, PIRATE_GUARD, GUARD_RANGE, HuntVis.Off, search) }
        if (grip == null || guard == null) {
            findWhisky()
            return
        }
        var summon = false
        startDialogue(guard) {
            chatNpc(
                confused,
                "I don't think Mr Grip will like you opening that. That's his private drinks " +
                    "cabinet.",
            )
            summon = choice2("He won't notice me having a quick look.", true, "Ok, I'll leave it.", false)
            if (summon) {
                chatPlayer(neutral, "He won't notice me having a quick look.")
            } else {
                chatPlayer(neutral, "Ok, I'll leave it.")
            }
        }
        if (!summon) {
            return
        }
        ifClose()
        grip.say("Stay out of my drinks cabinet!")
        grip.walkTo(routeFactory, LURE_TILE)
    }

    private suspend fun ProtectedAccess.findWhisky() {
        if (inv.isFull()) {
            objRepo.add(WHISKY, coords, DROP_TICKS, receiver = player)
        } else {
            invAdd(inv, WHISKY)
        }
        objbox(WHISKY, "You find a bottle of whisky in the cupboard.")
    }

    /* The candlesticks */

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_CHEST_SEQ)
        soundSynth(CHEST_OPEN_SOUND)
        mes("You open the chest.")
        locRepo.change(chest, CHEST_OPEN, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.searchChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_CHEST_SEQ)
        if (heroes.owns(this, CANDLESTICK)) {
            mesbox("The chest is empty.")
            if (bank.count(CANDLESTICK) > 0) {
                startDialogue {
                    chatPlayer(
                        happy,
                        "I'm sure I've got a candlestick from this chest somewhere in my bank.",
                    )
                }
            }
        } else {
            val room = inv.freeSpace()
            invAdd(inv, CANDLESTICK, room.coerceAtMost(CANDLESTICKS))
            if (room < CANDLESTICKS) {
                objRepo.add(CANDLESTICK, coords, DROP_TICKS, receiver = player, count = CANDLESTICKS - room)
            }
            doubleobjbox(
                CANDLESTICK,
                CANDLESTICK,
                "You find two candlesticks in the chest. So that will be one for you, and one for " +
                    "the person who killed Grip for you.",
            )
            if (heroes.stage(player) == BLACKARM_REPORTED && heroes.isBlackArm(player)) {
                heroes.setStage(this, BLACKARM_LOOTED)
            }
        }
        soundSynth(CHEST_CLOSE_SOUND)
        locRepo.change(chest, CHEST_SHUT, OPEN_TICKS)
    }

    private fun ProtectedAccess.shut(loc: BoundLocInfo, into: String, sound: String) {
        soundSynth(sound)
        locRepo.change(loc, into, OPEN_TICKS)
    }

    internal companion object {
        const val FRONT_DOOR = "loc.garvdoor"
        const val SIDE_DOOR = "loc.pete_sidedoor"
        const val TREASURE_DOOR = "loc.pete_treasuredoor"
        const val CABINET_SHUT = "loc.gripcbshut"
        const val CABINET_OPEN = "loc.gripcbopen"
        const val CHEST_SHUT = "loc.shutcandlechest"
        const val CHEST_OPEN = "loc.opencandlechest"
        const val PIRATE_GUARD = "npc.pirate_guard"

        const val CABINET_QUEUE = "queue.hero_drinks_cabinet"

        /** The tile in front of the arrow slit that Grip storms over to. */
        val LURE_TILE = CoordGrid(2777, 3198, 0)

        const val GARV_RANGE = 7
        const val GRIP_RANGE = 12
        const val GUARD_RANGE = 4
        const val OPEN_TICKS = 100
        const val DROP_TICKS = 200
        const val CANDLESTICKS = 2

        const val OPEN_CHEST_SEQ = "seq.human_openchest"
        const val OPEN_CUPBOARD_SEQ = "seq.human_opencupboard"

        const val UNLOCK_SOUND = "synth.unlock"
        const val LOCKED_SOUND = "synth.locked"
        const val CHEST_OPEN_SOUND = "synth.chest_open"
        const val CHEST_CLOSE_SOUND = "synth.chest_close"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
    }
}
