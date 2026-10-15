package org.rsmod.content.quest.area.taverley.witchshouse

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.CHEESE
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.DIARY
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.DOOR_KEY
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.MAGNET
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.MOUSE
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_FOUND_MAGNET
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_READ_DIARY
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_UNLOCKED_DOOR
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.inPorch
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nora T. Hagg's house: the locked front door and the key under the plant pot, her diary, the
 * basement with its electrified gate and magnet cupboard, and Professor Oddenstein's security
 * system on the back door - a mouse that carries a magnet into the wall once it has been lured
 * out with cheese.
 */
class WitchsHouse
@Inject
constructor(
    private val witchsHouse: WitchsHouseQuest,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    private var mouse: Npc? = null

    private val mouseType by lazy {
        ServerCacheManager.getNpc(MOUSE.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $MOUSE")
    }

    override fun ScriptContext.startup() {
        onOpLoc1(FRONT_DOOR) { frontDoor(it.loc, it.type, usedKey = false) }
        onOpLocU(FRONT_DOOR, DOOR_KEY) { frontDoor(it.loc, it.type, usedKey = true) }
        onOpLoc1(PLANT_POT) { lookUnderPot() }
        onOpHeld1(DIARY) { readDiary() }

        onOpLoc1(GATE_LEFT) { openGate(it.loc, it.type) }
        onOpLoc1(GATE_RIGHT) { openGate(it.loc, it.type) }
        onOpLoc1(CUPBOARD_SHUT) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc2(CUPBOARD_OPEN) { shutCupboard(it.loc) }
        onOpLoc1(BOXES) { searchBoxes() }

        onOpLocU(MOUSE_HOLE, CHEESE) { lureMouse("the") }
        onOpHeld5(CHEESE) {
            if (inPorch(coords)) {
                lureMouse("a")
            } else {
                invDrop(it.slot)
            }
        }
        onOpNpcU(MOUSE) { useOnMouse(it.npc, it.objType.id) }
        onOpLoc1(BACK_DOOR) { backDoor(it.loc, it.type) }
    }

    /* Front door and plant pot */

    private suspend fun ProtectedAccess.frontDoor(
        door: BoundLocInfo,
        type: ObjectServerType,
        usedKey: Boolean,
    ) {
        val leaving = coords.x > door.coords.x
        if (!leaving && !usedKey && DOOR_KEY !in inv) {
            mes("The door is locked.")
            return
        }
        if (!leaving && (!witchsHouse.isStarted(player) || witchsHouse.isComplete(player))) {
            startDialogue { chatPlayer(neutral, "It would be rude to break into this house.") }
            return
        }
        if (!leaving) {
            soundSynth(UNLOCK_SOUND)
        }
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.lookUnderPot() {
        arriveDelay()
        anim(SEARCH_SEQ)
        if (DOOR_KEY in inv) {
            mes("You don't find anything interesting.")
            return
        }
        invAddOrDrop(objRepo, DOOR_KEY)
        objbox(DOOR_KEY, "You find a key hidden under the flower pot.")
    }

    /* Basement */

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        if (GATE_GLOVES.none { it in player.worn }) {
            soundSynth(SHOCK_SOUND)
            queueHit(delay = 1, type = HitType.Typeless, damage = player.hitpoints / 10 + 1)
            mesbox("As your bare hands touch the gate you feel a shock.")
            return
        }
        with(passages) { walkThrough(gate, type) }
    }

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(CUPBOARD_OPEN_SEQ)
        soundSynth(CUPBOARD_OPEN_SOUND)
        locRepo.change(cupboard, CUPBOARD_OPEN, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.shutCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(CUPBOARD_CLOSE_SEQ)
        soundSynth(CUPBOARD_CLOSE_SOUND)
        locRepo.change(cupboard, CUPBOARD_SHUT, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        anim(SEARCH_SEQ)
        if (inv.count(MAGNET) > 0 || bank.count(MAGNET) > 0) {
            mes("You don't find anything interesting.")
            return
        }
        invAddOrDrop(objRepo, MAGNET)
        objbox(MAGNET, "You find a magnet in the cupboard.")
        if (witchsHouse.stage(player) == STAGE_STARTED) {
            witchsHouse.advanceTo(this, STAGE_FOUND_MAGNET)
        }
    }

    private suspend fun ProtectedAccess.searchBoxes() {
        arriveDelay()
        anim(SEARCH_SEQ)
        mes("You search the boxes...")
        delay(1)
        val find = if (inv.isFull()) null else BOX_FINDS.roll(random.of(BOX_ROLL))
        if (find == null) {
            mes("You find nothing of interest.")
            return
        }
        invAdd(inv, find.obj)
        mes("You find ${find.description}.")
    }

    /* The mouse and the back door */

    private suspend fun ProtectedAccess.lureMouse(hole: String) {
        if (invDel(inv, CHEESE).failure) {
            return
        }
        mouse?.takeIf { it.isSlotAssigned }?.let { npcRepo.del(it, Int.MAX_VALUE) }
        val spawned = Npc(mouseType, MOUSE_TILE)
        npcRepo.add(spawned, MOUSE_TICKS)
        spawned.respawns = false
        spawned.facePlayer(player)
        mouse = spawned
        soundSynth(SQUEAK_SOUND)
        mesbox("A mouse runs out of $hole hole.")
    }

    private suspend fun ProtectedAccess.useOnMouse(target: Npc, objId: Int) {
        if (objId != MAGNET.asRSCM(RSCMType.OBJ)) {
            mes("Nothing interesting happens.")
            return
        }
        val stage = witchsHouse.stage(player)
        when {
            stage >= STAGE_UNLOCKED_DOOR -> mes("You have already unlocked this door.")
            stage < STAGE_FOUND_MAGNET ->
                mesbox("This doesn't seem to be the right magnet to open this door...")
            else -> {
                if (invDel(inv, MAGNET).failure) {
                    return
                }
                npcRepo.del(target, Int.MAX_VALUE)
                if (mouse === target) {
                    mouse = null
                }
                witchsHouse.advanceTo(this, STAGE_UNLOCKED_DOOR)
                soundSynth(WHIRR_SOUND, delay = WHIRR_DELAY)
                mesbox(
                    "You attach the magnet to the mouse's harness. The mouse finishes the cheese " +
                        "and runs back into its hole. You hear some odd noises from inside the " +
                        "walls. There is a strange whirring noise from above the door frame.",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.backDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leavingGarden = coords.z <= door.coords.z
        if (!leavingGarden && witchsHouse.stage(player) < STAGE_UNLOCKED_DOOR) {
            startDialogue {
                mesbox("This door is locked.")
                chatPlayer(
                    confused,
                    "Strange... I can't see any kind of lock or handle to open this door...",
                )
            }
            return
        }
        if (!leavingGarden) {
            timer(WitchsGarden.WATCH_TIMER, 1)
        }
        with(passages) { walkThrough(door, type) }
    }

    /* The diary */

    /**
     * The book's page arrows are pause buttons; the client ignores further presses until the
     * interface is sent again, so every turn re-opens the book at the new spread.
     */
    private suspend fun ProtectedAccess.readDiary() {
        if (witchsHouse.stage(player) == STAGE_UNLOCKED_DOOR) {
            witchsHouse.advanceTo(this, STAGE_READ_DIARY)
        }
        var spread = 0
        openSpread(spread)
        while (true) {
            val input = pauseButton()
            val turned =
                when (input.component) {
                    PAGE_LEFT -> spread - 1
                    PAGE_RIGHT -> spread + 1
                    else -> spread
                }
            if (turned in DIARY_SPREADS.indices) {
                spread = turned
            }
            openSpread(spread)
        }
    }

    private fun ProtectedAccess.openSpread(spread: Int) {
        ifOpenMainModal(BOOK_INTERFACE)
        player.runClientScript(
            BOOK_INIT_SCRIPT,
            RSCM.getRSCM("component.book:close_button"),
            RSCM.getRSCM("component.book:close_graphic"),
            RSCM.getRSCM(PAGE_LEFT),
            RSCM.getRSCM("component.book:page_left_graphic"),
            RSCM.getRSCM(PAGE_RIGHT),
            RSCM.getRSCM("component.book:page_right_graphic"),
        )
        ifSetText("component.book:title", DIARY_TITLE)
        ifSetEvents(PAGE_LEFT, -1..-1, IfEvent.PauseButton)
        ifSetEvents(PAGE_RIGHT, -1..-1, IfEvent.PauseButton)
        val (left, right) = DIARY_SPREADS[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == DIARY_SPREADS.lastIndex)
        soundSynth(PAGE_SOUND)
    }

    private class BoxFind(val obj: String, val description: String, val weight: Int)

    private fun List<BoxFind>.roll(value: Int): BoxFind? {
        var remaining = value
        for (find in this) {
            if (remaining < find.weight) {
                return find
            }
            remaining -= find.weight
        }
        return null
    }

    private companion object {
        const val FRONT_DOOR = "loc.witchhousedoor"
        const val BACK_DOOR = "loc.witchbackdoor"
        const val PLANT_POT = "loc.witchpot"
        const val GATE_LEFT = "loc.shockgatel"
        const val GATE_RIGHT = "loc.shockgater"
        const val CUPBOARD_SHUT = "loc.magnetcbshut"
        const val CUPBOARD_OPEN = "loc.magnetcbopen"
        const val BOXES = "loc.grim_basement_crate_many"
        const val MOUSE_HOLE = "loc.witchmousehole"

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val CUPBOARD_OPEN_SEQ = "seq.human_opencupboard"
        const val CUPBOARD_CLOSE_SEQ = "seq.human_closecupboard"

        const val UNLOCK_SOUND = "synth.unlock"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
        const val SQUEAK_SOUND = "synth.rat_squeak_1"
        const val WHIRR_SOUND = "synth.grandtree_machinery"
        const val SHOCK_SOUND = "synth.shock"
        const val WHIRR_DELAY = 30
        const val PAGE_SOUND = "synth.turn_book_page"

        const val CUPBOARD_TICKS = 500
        const val MOUSE_TICKS = 50
        val MOUSE_TILE = CoordGrid(2903, 3466, 0)

        const val BOX_ROLL = 1000
        val BOX_FINDS =
            listOf(
                BoxFind("obj.cabbage", "a cabbage", 150),
                BoxFind("obj.leather_boots", "a pair of leather boots", 100),
                BoxFind("obj.leather_gloves", "a pair of leather gloves", 38),
                BoxFind("obj.needle", "a needle", 143),
                BoxFind("obj.thread", "some thread", 83),
            )

        /** Every pair of gloves that insulates a hand from the basement gate's charge. */
        val GATE_GLOVES =
            listOf(
                "obj.leather_gloves",
                "obj.ice_gloves",
                "obj.wolfengloves_grey",
                "obj.wolfengloves_crimson",
                "obj.wolfengloves_tangerine",
                "obj.wolfengloves_ocean",
                "obj.wolfengloves_purple",
                "obj.vikinggloves",
                "obj.ham_gloves",
                "obj.mourning_mourner_gloves",
                "obj.secret_ghost_gloves",
                "obj.dagganoth_range_gloves",
                "obj.dagganoth_melee_gloves",
                "obj.dagganoth_mage_gloves",
                "obj.white_gloves",
                "obj.pest_void_knight_gloves",
                "obj.hunting_silent_gloves",
                "obj.barbassault_penance_gloves",
                "obj.barbassault_penance_gloves_worn",
                "obj.easter16_onsie_gloves",
                "obj.clue_hunter_gloves",
                "obj.holy_wraps",
                "obj.mguild_gloves",
                "obj.mguild_gloves_superior",
                "obj.mguild_gloves_expert",
                "obj.xmas17_imp_gloves",
                "obj.aerial_fishing_gloves_no_bird",
                "obj.aerial_fishing_gloves_bird",
                "obj.ornate_gloves",
                "obj.hw19_ghost_gloves_slime",
                "obj.smithing_uniform_gloves_ice",
            )

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26
        const val DIARY_TITLE = "Witches' Diary"

        /** One diary spread per list: each entry is a dated heading followed by its text. */
        val DIARY_ENTRIES: List<List<Pair<String?, String>>> =
            listOf(
                listOf(
                    "2nd of Pentember" to
                        "Experiment is growing larger daily. Making excellent progress now. I am " +
                        "currently feeding it on a mixture of fungus, tar and clay. It seems to " +
                        "like this combination a lot!",
                    "3rd of Pentember" to
                        "Experiment still going extremely well. Moved it to the wooden garden " +
                        "shed; it does too much damage in the house! It is getting very strong " +
                        "now, but unfortunately is not too intelligent yet. It has a really mean " +
                        "stare too!",
                ),
                listOf(
                    "4th of Pentember" to "Sausages for dinner tonight! Lovely!",
                    "5th of Pentember" to
                        "A guy called Professor Oddenstein installed a new security system for " +
                        "me in the basement. He seems to have a lot of good security ideas.",
                    "6th of Pentember" to
                        "Don't want people getting into back garden to see the experiment. " +
                        "Professor Oddenstein is fitting me a new security system, after his " +
                        "successful installation in the cellar.",
                ),
                listOf(
                    "7th of Pentember" to
                        "That pesky kid keeps kicking his ball into my garden. I swear, if he " +
                        "does it AGAIN, I'm going to lock his ball away in the shed.",
                    "8th of Pentember" to
                        "The security system is done. By Zamorak! Wow, is it contrived! Now, to " +
                        "open my own back door, I lure a mouse out of the hole in the back " +
                        "porch, I fit a magic curved piece of metal to the harness on its back, " +
                        "the mouse goes back in the hole, and the door unlocks! The prof tells " +
                        "me that this is cutting edge technology!",
                ),
                listOf(
                    null to
                        "As an added precaution I have hidden the key to the shed in a secret " +
                        "compartment of the fountain in the garden. No one will ever look there!",
                    "9th of Pentember" to
                        "Still can't think of a good name for 'The Experiment'. Leaning towards " +
                        "'Fritz'... Although am considering Lucy as it reminds me of my mother!",
                ),
            )

        val DIARY_SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            DIARY_ENTRIES.map { entries ->
                val lines = mutableListOf<String>()
                for ((date, text) in entries) {
                    if (date != null) {
                        lines += "<col=ff0000>$date</col>"
                    }
                    lines += wrap(text)
                    lines += ""
                }
                val left = lines.take(LINES_PER_PAGE)
                val right = lines.drop(LINES_PER_PAGE).take(LINES_PER_PAGE)
                left to right
            }
        }

        fun wrap(text: String): List<String> {
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in text.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > WRAP_WIDTH) {
                    lines += line.toString()
                    line = StringBuilder()
                }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            if (line.isNotEmpty()) lines += line.toString()
            return lines
        }
    }
}
