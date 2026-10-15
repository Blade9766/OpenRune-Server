package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CHARCOAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.MACHETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.NOTES
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.NOTES_COMPLETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.PAPYRUS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_MAPPED_JUNGLE
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Kharazi Jungle in the far south of Karamja. Its trees and bushes wall the jungle off and
 * have to be hacked through with an axe or a machete, each one carrying the player two tiles on
 * into the jungle; Radimus's notes are completed by mapping each of its three areas; and its leafy
 * palms drop the leaves the jungle natives use.
 */
class KharaziJungle
@Inject
constructor(
    private val legends: LegendsQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (tree in TREES) {
            onOpLoc1(tree) { hackThrough(it.vis, it.type, bush = false) }
        }
        for (bush in BUSHES) {
            onOpLoc1(bush) { hackThrough(it.vis, it.type, bush = true) }
        }
        onOpLoc1(LEAFY_PALM) { shakePalm(it.vis) }
        onOpHeld1(NOTES) { readNotes() }
        onOpHeld2(NOTES) { startMapping() }
        onOpHeld1(NOTES_COMPLETE) { readCompleteNotes() }
    }

    private suspend fun ProtectedAccess.hackThrough(loc: BoundLocInfo, type: ObjectServerType, bush: Boolean) {
        arriveDelay()
        if (!carries(NOTES) && !carries(NOTES_COMPLETE) && !legends.isComplete(player)) {
            mesbox("You'll get lost in this jungle without a map. You decide not to go any further.")
            return
        }
        val axe = bestAxe()
        if (axe == null) {
            mesbox(
                "You'll need an axe to get through this rough jungle. You don't think it would be a " +
                    "good idea to continue without one you've got the level to use.",
            )
            escapeDenseJungle()
            return
        }
        if (!carries(MACHETE)) {
            mesbox("You need a machete to cut your way through this dense jungle bush.")
            escapeDenseJungle()
            return
        }
        evaporateBowls()
        if (bush) {
            mes("You swing your machete at the jungle plant.")
        } else {
            mes("You swing your axe at the tree.")
        }
        faceLoc(loc)
        val anim = if (bush) MACHETE_SEQ else toolAnim(axe, DEFAULT_AXE_SEQ)
        while (true) {
            anim(anim)
            if (bush) {
                soundSynth(WOODCHOP_SOUND)
            }
            delay(if (bush) BUSH_CHOP_TICKS else TREE_CHOP_TICKS)
            if (!statRandom(WOODCUTTING, CHOP_LOW, CHOP_HIGH, 0)) {
                continue
            }
            if (!inv.isFull() && random.randomBoolean()) {
                mes("You get some logs.")
                statAdvance(WOODCUTTING, JUNGLE_XP)
                invAdd(inv, LOGS, 1)
            }
            if (random.of(DEPLETE_ROLL) < DEPLETE_CHANCE) {
                break
            }
        }
        resetAnim()
        if (coords == loc.coords) {
            mes("This way is blocked off, no chance to get through here!")
            return
        }
        val dest = twoTilesToward(loc.coords)
        val blocked = mapBlocked(dest) && !jungleAt(dest)
        val stump = if (bush) SLASHED_BUSH else STUMP
        if (blocked) {
            mes("This way is blocked off, no chance to get through here!")
            locRepo.change(loc, stump, REGROW_TICKS)
            return
        }
        mes(if (bush) "You hack your way through the jungle bush." else "You hack your way through the tree.")
        mes("You move deeper into the jungle.")
        teleport(dest)
        locRepo.change(loc, stump, REGROW_TICKS)
    }

    private fun ProtectedAccess.twoTilesToward(target: CoordGrid): CoordGrid {
        val dx = (target.x - coords.x).sign
        val dz = (target.z - coords.z).sign
        return CoordGrid(coords.x + dx * 2, coords.z + dz * 2, coords.level)
    }

    private fun jungleAt(coords: CoordGrid): Boolean =
        locRepo.findAll(coords).any { loc -> JUNGLE_IDS.any { it == loc.id } }

    private suspend fun ProtectedAccess.escapeDenseJungle() {
        val exit = LegendsCoords.denseJungleExit(coords) ?: return
        mesbox("It looks like you're lost in the jungle! Would you like to try and scrabble out?")
        val out =
            choice2(
                "Yes, I'll scrabble out of this jungle.", true,
                "No, I think I can find my own way out.", false,
                title = "Scrabble out of jungle?",
            )
        if (!out) {
            mes("You decide to stay where you are.")
            return
        }
        teleport(exit)
        mes("You scrabble out of the jungle.")
        mes("You're scratched to pieces by the dense jungle trees,")
        mes("but at least you're alive and out of the jungle.")
        hurt(1)
    }

    /** The jungle's heat boils away any water carried in a golden bowl. */
    private fun ProtectedAccess.evaporateBowls() {
        val filled = listOf(GOLD_BOWL_WATER to GOLD_BOWL, GOLD_BOWL_PURE to GOLD_BOWL, BLESSED_BOWL_WATER to BLESSED_BOWL, BLESSED_BOWL_PURE to BLESSED_BOWL)
        if (filled.none { inv.count(it.first) > 0 }) {
            return
        }
        mes("The heat in this jungle is terrific.")
        mes("The water from your golden bowl evaporates.")
        for ((full, empty) in filled) {
            val count = inv.count(full)
            if (count > 0) {
                invDel(inv, full, count)
                invAdd(inv, empty, count)
            }
        }
    }

    private suspend fun ProtectedAccess.shakePalm(palm: BoundLocInfo) {
        arriveDelay()
        mes("You give the bamboo tree a good shake.")
        anim(PUSH_SEQ)
        delay(1)
        mes("A palm leaf falls to the ground.")
        val spot = mapFindSquareLineOfWalk(coords, 1, 1) ?: coords
        objRepo.add(PALM_LEAF, spot, PALM_LEAF_TICKS, receiver = player)
        locRepo.change(palm, LEAFY_PALM_BARE, PALM_REGROW_TICKS)
    }

    private suspend fun ProtectedAccess.readNotes() {
        objbox(NOTES, "You open and start to read the scrolls that Radimus gave you.")
        val begin =
            choice2("Read Mission Briefing.", false, "Start Mapping Kharazi Jungle.", true)
        if (begin) {
            startMapping()
        } else {
            missionBriefing()
        }
    }

    private suspend fun ProtectedAccess.readCompleteNotes() {
        objbox(NOTES_COMPLETE, "The map of the Kharazi Jungle is complete, Sir Radimus Erkle will be pleased.")
        if (choice2("Read Mission Briefing.", true, "Close.", false)) {
            missionBriefing()
        } else {
            mes("You put the scrolls away.")
        }
    }

    private suspend fun ProtectedAccess.missionBriefing() {
        mesbox(
            "<col=0000ff>*** Legends Guild Quest ***</col><br>Map the Kharazi Jungle (Southern end " +
                "of Karamja), there are three main areas that need to be mapped. Try to meet up with " +
                "the local friendly natives. Some are not so friendly so be careful.",
        )
        mesbox(
            "See if you can get a trophy or native jungle item from the natives to display in the " +
                "Legends Guild. You may be given a task or test to earn this. * Note - You may need to " +
                "get help from other people near the jungle, for example, the local woodsmen may have " +
                "some knowledge of the Jungle area.",
        )
    }

    private suspend fun ProtectedAccess.startMapping() {
        val section =
            when {
                LegendsCoords.inJungleWest(coords) -> WEST
                LegendsCoords.inJungleMiddle(coords) -> MIDDLE
                LegendsCoords.inJungleEast(coords) -> EAST
                else -> {
                    mesbox(
                        "You're not even in the Kharazi Jungle yet. You need to get to the Southern " +
                            "end of Karamja before you can start mapping.",
                    )
                    return
                }
            }
        if (player.legendsMapped and section != 0) {
            objbox(NOTES, "You have already completed this part of the map.")
            showProgress()
            return
        }
        mapSection(section)
    }

    private suspend fun ProtectedAccess.mapSection(section: Int) {
        val hasPapyrus = inv.count(PAPYRUS) > 0
        val hasCharcoal = inv.count(CHARCOAL) > 0
        when {
            !hasPapyrus && !hasCharcoal -> {
                doubleobjbox(PAPYRUS, CHARCOAL, "You'll need some papyrus and charcoal to complete this map.")
                return
            }
            !hasPapyrus -> {
                objbox(PAPYRUS, "You'll need some additional papyrus to complete this map.")
                return
            }
            !hasCharcoal -> {
                objbox(CHARCOAL, "You'll need some additional charcoal to complete this map.")
                return
            }
        }
        if (player.craftingLvl < REQUIRED_CRAFTING) {
            mesbox("Mapping the Kharazi jungle is a difficult task. You need a Crafting level of at least 50 to complete the map.")
            return
        }
        mesbox("You prepare to start mapping this area...")
        if (!statRandom(CRAFTING, MAP_LOW, MAP_HIGH, 0)) {
            val roll = random.of(128)
            when {
                roll < 40 -> objbox(NOTES, "You make a mess but are able to rescue the paper.")
                roll < 65 -> {
                    invDel(inv, CHARCOAL, 1)
                    objbox(CHARCOAL, "You snap your stick of charcoal.")
                }
                roll < 90 -> {
                    invDel(inv, PAPYRUS, 1)
                    objbox(PAPYRUS, "You make a mess of the map, the paper is totally ruined.")
                }
                else -> {
                    invDel(inv, PAPYRUS, 1)
                    invDel(inv, CHARCOAL, 1)
                    doubleobjbox(PAPYRUS, CHARCOAL, "You fall over, landing on your charcoal and papyrus, destroying them both.")
                }
            }
            return
        }
        ifClose()
        invDel(inv, PAPYRUS, 1)
        anim(MAPPING_SEQ)
        delay(1)
        player.legendsMapped = player.legendsMapped or section
        objbox(NOTES, "You use your crafting skill to neatly add a new section to your map.")
        ifClose()
        anim(MAPPING_SEQ)
        delay(1)
        if (player.legendsMapped != ALL_SECTIONS) {
            mes("You still have some sections of the map to complete.")
            showProgress()
            return
        }
        objbox(NOTES_COMPLETE, "Well done! You have completed mapping the Kharazi Jungle. Grand Vizier Erkle will be pleased.")
        invDel(inv, NOTES, 1)
        invAdd(inv, NOTES_COMPLETE, 1)
        legends.raiseTo(this, STAGE_MAPPED_JUNGLE)
        player.legendsMapped = 0
        objbox(NOTES, progressText(ALL_SECTIONS))
    }

    private suspend fun ProtectedAccess.showProgress() {
        objbox(NOTES, progressText(player.legendsMapped))
    }

    private fun progressText(mapped: Int): String {
        fun line(name: String, section: Int): String =
            if (mapped and section != 0) {
                "<col=0000ff>$name *** Completed ***</col>"
            } else {
                "<col=ff0000>$name *** Incomplete ***</col>"
            }
        return "*** Mapping the Kharazi Jungle ***<br>" +
            line("Eastern Kharazi Jungle -", EAST) + "<br>" +
            line("Middle Kharazi Jungle-", MIDDLE) + "<br>" +
            line("Western Kharazi Jungle-", WEST)
    }

    private companion object {
        const val WEST = 1
        const val MIDDLE = 2
        const val EAST = 4
        const val ALL_SECTIONS = WEST or MIDDLE or EAST

        val TREES = listOf("loc.kharazi_jungle_tree_logs", "loc.kharazi_jungle_tree1", "loc.kharazi_jungle_tree2")
        val BUSHES = listOf("loc.kharazi_jungle_plant1", "loc.kharazi_jungle_plant2")
        val JUNGLE_IDS by lazy {
            (TREES + BUSHES).map { dev.openrune.rscm.RSCM.getRSCM(it) }
        }
        const val STUMP = "loc.junglestump_kharazi"
        const val SLASHED_BUSH = "loc.kharazi_jungle_plant1_slashed"
        const val LEAFY_PALM = "loc.kharazi_bamboo_tree_base_leafy"
        const val LEAFY_PALM_BARE = "loc.kharazi_bamboo_tree_inac"
        const val PALM_LEAF = "obj.palm_leaf"
        const val LOGS = "obj.logs"

        const val WOODCUTTING = "stat.woodcutting"
        const val CRAFTING = "stat.crafting"
        const val REQUIRED_CRAFTING = 50
        const val CHOP_LOW = 70
        const val CHOP_HIGH = 233
        const val MAP_LOW = 100
        const val MAP_HIGH = 250
        const val JUNGLE_XP = 100.0
        const val DEPLETE_ROLL = 4
        const val DEPLETE_CHANCE = 3
        const val BUSH_CHOP_TICKS = 2
        const val TREE_CHOP_TICKS = 4
        const val REGROW_TICKS = 30
        const val PALM_LEAF_TICKS = 100
        const val PALM_REGROW_TICKS = 44

        const val MACHETE_SEQ = "seq.human_machette_chop"
        const val DEFAULT_AXE_SEQ = "seq.human_woodcutting_bronze_axe"
        const val MAPPING_SEQ = "seq.human_mapping"
        const val PUSH_SEQ = "seq.human_push"
        const val WOODCHOP_SOUND = "synth.woodchop_4"
    }
}
