package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.BUCKET
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.DUSTY_SCROLL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.INN_TRAPDOOR
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.INN_WALL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.PLASTER_FRAGMENT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.POT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.REQUIRED_MINING
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.RUBBLE_BUCKETS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.RUBBLE_PILES
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SPADE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CELLAR_CLEARED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CELLAR_SUGGESTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_IVAN_DELIVERED
import org.rsmod.content.quest.area.karamja.legendsquest.bestPickaxe
import org.rsmod.content.quest.area.karamja.legendsquest.toolAnim
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The inn and its cellar.
 *
 * The cellar is three copies of one room on levels 0-2. While it is still full of rubble the
 * player goes down into a private copy of level 1, where only the piles they have not yet removed
 * are spawned. A pile is mined three times and then shovelled into a bucket; only shovelling it
 * removes it for good (a bit on `varbit.burgh_rubble_removed`), so a pile left mined but not
 * removed is whole again on the next visit, as are any items dropped down there. Once all fifteen
 * are gone the cleared copy (level 2) is used, and once the Myreque have moved in, their base
 * (level 0).
 *
 * Every pile's index is fixed by its tile, so removing piles in any order counts each exactly once.
 */
@Singleton
class InnCellar
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val copies: BurghCopies,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private class Visit(val copy: BurghCopies.Copy, val piles: MutableMap<CoordGrid, Int>)

    private val visits = HashMap<PlayerUid, Visit>()

    override fun ScriptContext.startup() {
        onOpLoc1(CLIMB_OVER) { climbOverWall() }
        onOpLocU(RUBBLE_BLOCKED) { breakTrapdoorRubble(it.objType) }
        onOpLoc1(TRAPDOOR_CLOSED) { openTrapdoor() }
        onOpLoc1(TRAPDOOR_OPEN) { climbDown() }
        onOpLoc2(TRAPDOOR_OPEN) {
            arriveDelay()
            VarPlayerIntMapSetter.set(player, INN_TRAPDOOR, 0)
            soundSynth(TRAPDOOR_CLOSE_SOUND)
        }
        onOpLoc1(CELLAR_LADDER) { climbUp() }
        for (stage in RUBBLE_STAGES.dropLast(1)) {
            onOpLoc1(stage) { mine(it.loc) }
        }
        onOpLoc1(RUBBLE_STAGES.last()) { remove(it.loc) }
        onOpLoc1(PLAQUE) { readPlaque() }
        onOpLoc2(RUBBLE_DUMP) { searchDump() }
        for (bucket in RUBBLE_BUCKETS) {
            onOpLocU(RUBBLE_DUMP, bucket) { emptyBucket(it.invSlot) }
            onOpHeld4(bucket) { emptyHere(it.slot) }
        }
        onOpHeld1(DUSTY_SCROLL) { mesbox(SCROLL_TEXT) }
        onOpHeld1(PLASTER_FRAGMENT) { mesbox(PLASTER_TEXT) }
        onPlayerLogout { visits.remove(player.uid) }
    }

    fun isInRubbleCellar(player: Player): Boolean = visits[player.uid] != null

    private suspend fun ProtectedAccess.climbOverWall() {
        arriveDelay()
        val inside = player.coords.z > BurghCoords.INN_BROKEN_WALL.z
        anim(CLIMB_OVER_SEQ)
        delay(CLIMB_TICKS)
        telejump(if (inside) BurghCoords.INN_WALL_OUTSIDE else BurghCoords.INN_WALL_INSIDE, TeleportType.Exempt)
        mes("You climb over the broken wall.")
    }

    private suspend fun ProtectedAccess.breakTrapdoorRubble(used: ItemServerType) {
        arriveDelay()
        if (!used.isContentType(PICKAXE_CONTENT)) {
            mes("Nothing interesting happens.")
            return
        }
        if (iaom.stage(player) < STAGE_CELLAR_SUGGESTED) {
            mes("You have no reason to dig through this rubble.")
            return
        }
        if (statBase(MINING) < REQUIRED_MINING) {
            mes("You need a Mining level of $REQUIRED_MINING to mine this rubble.")
            return
        }
        mes("You swing your pickaxe at the rubble...")
        anim(toolAnim(used, DEFAULT_MINING_SEQ))
        delay(MINE_TICKS)
        resetAnim()
        VarPlayerIntMapSetter.set(player, INN_WALL, 1)
        mes("...and successfully break it down.")
    }

    private suspend fun ProtectedAccess.openTrapdoor() {
        arriveDelay()
        if (player.vars[INN_WALL] != 1 && !iaom.reached(player, STAGE_CELLAR_CLEARED)) {
            mes("The trapdoor is buried under rubble.")
            return
        }
        anim(OPEN_SEQ)
        soundSynth(TRAPDOOR_OPEN_SOUND)
        VarPlayerIntMapSetter.set(player, INN_TRAPDOOR, 1)
    }

    private suspend fun ProtectedAccess.climbDown() {
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        delay(CLIMB_TICKS)
        val stage = iaom.effectiveStage(player)
        when {
            stage < STAGE_CELLAR_CLEARED -> enterRubbleCellar()
            stage < STAGE_IVAN_DELIVERED -> telejump(BurghCoords.CELLAR_FOOT.copy(level = BurghCoords.CELLAR_CLEARED_LEVEL), TeleportType.Exempt)
            else -> telejump(BurghCoords.CELLAR_FOOT.copy(level = BurghCoords.CELLAR_HIDEOUT_LEVEL), TeleportType.Exempt)
        }
    }

    private fun ProtectedAccess.enterRubbleCellar() {
        val world = BurghCoords.CELLAR_FOOT.copy(level = BurghCoords.CELLAR_RUBBLE_LEVEL)
        val copy = with(copies) { enter(INSTANCE_KEY, world, BurghCoords.INN_WALL_INSIDE) } ?: return
        val piles = HashMap<CoordGrid, Int>()
        for ((index, tile) in BurghCoords.RUBBLE_PILES.withIndex()) {
            if (iaom.isPileRemoved(player, index)) continue
            val at = copy.at(tile)
            locRepo.add(at, RUBBLE_STAGES.first(), Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
            piles[at] = index
        }
        visits[player.uid] = Visit(copy, piles)
        mes("The cellar is full of rubble.")
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(CLIMB_TICKS)
        val visit = visits.remove(player.uid)
        if (visit != null) {
            for (tile in visit.piles.keys) {
                locRepo.findAll(tile).filter { it.id in pileIds }.toList().forEach { locRepo.del(it, Int.MAX_VALUE) }
            }
            with(copies) { leave() }
        }
        telejump(BurghCoords.INN_WALL_INSIDE, TeleportType.Exempt)
    }

    private fun ProtectedAccess.pileAt(loc: BoundLocInfo): Int? = visits[player.uid]?.piles?.get(loc.coords)

    private suspend fun ProtectedAccess.mine(loc: BoundLocInfo) {
        arriveDelay()
        pileAt(loc) ?: return
        val pickaxe = bestPickaxe()
        if (pickaxe == null) {
            mes("You need a pickaxe to break up this rubble.")
            return
        }
        if (statBase(MINING) < REQUIRED_MINING) {
            mes("You need a Mining level of $REQUIRED_MINING to mine this rubble.")
            return
        }
        anim(toolAnim(pickaxe, DEFAULT_MINING_SEQ))
        delay(MINE_TICKS)
        resetAnim()
        val next = RUBBLE_STAGES[RUBBLE_STAGES.indexOf(locName(loc)) + 1]
        locRepo.change(loc, next, Int.MAX_VALUE)
        if (next != RUBBLE_STAGES.last()) {
            mes("You chip away at the rubble.")
            return
        }
        if (player.vars[FIRST_MINE_MESSAGE] == 0) {
            VarPlayerIntMapSetter.set(player, FIRST_MINE_MESSAGE, 1)
            mesbox("You manage to mine the rubble to pieces. You should be able to move what's left out of the cellar now. Though you'll need something to carry it in.")
        } else {
            mes("You manage to mine the rubble to pieces.")
        }
    }

    private fun locName(loc: BoundLocInfo): String = RUBBLE_STAGES.first { it.asLocId() == loc.id }

    private suspend fun ProtectedAccess.remove(loc: BoundLocInfo) {
        arriveDelay()
        val pile = pileAt(loc) ?: return
        if (iaom.isPileRemoved(player, pile)) return
        val scoop =
            when {
                SPADE in inv -> SPADE
                POT in inv -> POT
                else -> {
                    mes("You need a spade or a pot to scoop up the rubble.")
                    return
                }
            }
        val bucket = (RUBBLE_BUCKETS.dropLast(1).reversed() + BUCKET).firstOrNull { it in inv }
        if (bucket == null) {
            mes("You need a bucket with some room in it to carry the rubble away.")
            return
        }
        anim(if (scoop == POT) POT_SCOOP_SEQ else SPADE_SCOOP_SEQ)
        delay(SCOOP_TICKS)
        resetAnim()
        val fuller = if (bucket == BUCKET) RUBBLE_BUCKETS.first() else RUBBLE_BUCKETS[RUBBLE_BUCKETS.indexOf(bucket) + 1]
        if (invReplace(inv, bucket, 1, fuller).failure) {
            return
        }
        locRepo.del(loc, Int.MAX_VALUE)
        visits[player.uid]?.piles?.remove(loc.coords)
        iaom.markPileRemoved(player, pile)
        mes("You use ${if (scoop == POT) "a pot" else "a spade"} to scoop the rubble into a bucket.")
        val removed = iaom.rubbleRemoved(player)
        FINDS[removed]?.let { find -> find(find) }
        if (iaom.isCellarClear(player)) {
            discoverPlaque()
        }
    }

    private suspend fun ProtectedAccess.find(find: Find) {
        when (find) {
            is Find.Rubbish -> {
                for ((obj, count) in find.items) {
                    invAddOrDrop(objRepo, obj, count)
                }
                doubleobjbox(find.items[0].first, find.items.last().first, "Some rubbish falls out of the rubble as you pick it up. It's too big to fit in the bucket.")
            }
            is Find.Lore -> {
                if (inv.isFull()) {
                    objbox(find.obj, "You find ${find.what} with some interesting ${find.marks} on it, but you don't have enough space to keep it. You throw it into the bucket with the rest of the rubble.")
                    return
                }
                invAdd(inv, find.obj)
                objbox(find.obj, "You find ${find.what} with some interesting ${find.marks} on it.")
            }
        }
    }

    private suspend fun ProtectedAccess.discoverPlaque() {
        mesbox("You clear out the last of the rubble.")
        val visit = visits[player.uid]
        val plaque = visit?.copy?.at(BurghCoords.CELLAR_PLAQUE) ?: BurghCoords.CELLAR_PLAQUE
        try {
            camMoveTo(plaque.translate(PLAQUE_CAMERA_DX, PLAQUE_CAMERA_DZ), PLAQUE_CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            camLookAt(plaque, PLAQUE_LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            delay(PLAQUE_TICKS)
            mesbox("As you clear away the last of the rubble, you notice a dusty looking wall plaque which you hadn't spotted before.")
        } finally {
            camReset()
        }
        iaom.advanceTo(this, STAGE_CELLAR_CLEARED)
    }

    private suspend fun ProtectedAccess.readPlaque() {
        VarPlayerIntMapSetter.set(player, PLAQUE_READ, 1)
        mesbox("The plaque is caked in dust. Beneath it you can just make out the outline of a lion and a few worn letters. It must be older than the inn itself.")
    }

    private suspend fun ProtectedAccess.searchDump() {
        arriveDelay()
        val removed = iaom.rubbleRemoved(player)
        val lost = FINDS.filter { (at, find) -> at <= removed && find is Find.Lore && !player.holdsAnywhere(find.obj) }
            .map { (it.value as Find.Lore).obj }
        if (lost.isEmpty()) {
            mes("You search the rubble pile but find nothing of interest.")
            return
        }
        for (obj in lost) {
            if (inv.isFull()) {
                mes("You don't have enough space to take anything else from the rubble pile.")
                return
            }
            invAdd(inv, obj)
            objbox(obj, "You search through the rubble pile and find ${objName(obj).lowercase().let { "the $it" }} you threw away.")
        }
    }

    private suspend fun ProtectedAccess.emptyBucket(slot: Int) {
        arriveDelay()
        val obj = inv[slot] ?: return
        val bucket = RUBBLE_BUCKETS.firstOrNull { obj.isType(it) } ?: return
        anim(EMPTY_SEQ)
        invReplace(inv, bucket, 1, BUCKET)
        soundSynth(EMPTY_SOUND)
        mes("You empty the bucket onto the rubble pile.")
    }

    private suspend fun ProtectedAccess.emptyHere(slot: Int) {
        val obj = inv[slot] ?: return
        val bucket = RUBBLE_BUCKETS.firstOrNull { obj.isType(it) } ?: return
        when {
            isInRubbleCellar(player) || BurghCoords.inCellar(player.coords) ->
                objbox(RUBBLE_BUCKETS.last(), "You'll just fill the room up with rubble again if you empty the bucket here.")
            BurghCoords.inTown(player.coords) ->
                objbox(RUBBLE_BUCKETS.last(), "The people of Burgh de Rott would not be happy if you dumped the rubble here.")
            else -> {
                anim(EMPTY_SEQ)
                invReplace(inv, bucket, 1, BUCKET)
                mes("You empty the bucket of rubble.")
            }
        }
    }

    private sealed class Find {
        data class Rubbish(val items: List<Pair<String, Int>>) : Find()

        data class Lore(val obj: String, val what: String, val marks: String) : Find()
    }

    internal companion object {
        const val INSTANCE_KEY = "burgh_inn_cellar"
        const val CLIMB_OVER = "loc.burgh_inn_climb_over"
        const val RUBBLE_BLOCKED = "loc.burgh_inn_rubble_blocked"
        const val TRAPDOOR_CLOSED = "loc.burgh_inn_trapdoor_closed"
        const val TRAPDOOR_OPEN = "loc.burgh_inn_trapdoor_open"
        const val CELLAR_LADDER = "loc.burgh_inn_basement_ladderup"
        const val PLAQUE = "loc.burgh_inn_wall_plaque"
        const val RUBBLE_DUMP = "loc.burgh_outside_rubble_pile"
        const val FIRST_MINE_MESSAGE = "varbit.burgh_rubble_bucket_message"
        const val PLAQUE_READ = "varbit.burgh_wall_plaque_read"
        const val MINING = "stat.mining"
        const val PICKAXE_CONTENT = "content.mining_pickaxe"

        /** One pile's four looks: three to mine through, the last to shovel up. */
        val RUBBLE_STAGES = listOf("loc.burgh_rubble_a_1", "loc.burgh_rubble_a_2", "loc.burgh_rubble_a_3", "loc.burgh_rubble_a_4")

        /**
         * What turns up as the piles are removed, keyed by how many are gone. The OSRS wiki lists
         * the nine items (21 nails, a rock, broken glass, the scroll and the fragment) but not the
         * order; this spreads them across the job.
         */
        private val FINDS: Map<Int, Find> =
            mapOf(
                3 to Find.Rubbish(listOf("obj.nails_bronze" to 10, "obj.limestone" to 1)),
                6 to Find.Rubbish(listOf("obj.nails_iron" to 5, "obj.broken_glass" to 1)),
                8 to Find.Lore(DUSTY_SCROLL, "a scroll", "writing"),
                10 to Find.Rubbish(listOf("obj.nails" to 3, "obj.nails_black" to 2)),
                12 to Find.Lore(PLASTER_FRAGMENT, "a piece of plaster", "inscriptions"),
                14 to Find.Rubbish(listOf("obj.nails_mithril" to 1)),
            )

        const val SCROLL_TEXT =
            "The scroll is faded and torn. It speaks of a ring once worn by Efaritay, a ruler of " +
                "old Hallowvale, that let a fighter's blows bite even into the undead flesh of a vampyre."
        const val PLASTER_TEXT =
            "The plaster bears part of an old inscription: '...Ivandis... fell beyond the river... " +
                "his rod lies with him still...' The rest has crumbled away."

        const val CLIMB_OVER_SEQ = "seq.human_jump_hurdle"
        const val DEFAULT_MINING_SEQ = "seq.human_mining_bronze_pickaxe"
        const val OPEN_SEQ = "seq.human_pickuptable"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val CLIMB_UP_SEQ = "seq.human_reachforladdertop"
        const val SPADE_SCOOP_SEQ = "seq.human_dig"
        const val POT_SCOOP_SEQ = "seq.burgh_pot_scoop"
        const val EMPTY_SEQ = "seq.human_pickuptable"
        const val TRAPDOOR_OPEN_SOUND = "synth.trapdoor_open"
        const val TRAPDOOR_CLOSE_SOUND = "synth.trapdoor_close"
        const val EMPTY_SOUND = "synth.digspade"

        const val CLIMB_TICKS = 2
        const val MINE_TICKS = 3
        const val SCOOP_TICKS = 2
        const val PLAQUE_TICKS = 3
        const val PLAQUE_CAMERA_DX = -3
        const val PLAQUE_CAMERA_DZ = -4
        const val PLAQUE_CAMERA_HEIGHT = 380
        const val PLAQUE_LOOK_HEIGHT = 200
        const val CAMERA_RATE = 100

        val pileIds by lazy { RUBBLE_STAGES.map { it.asLocId() }.toSet() }

        init {
            check(BurghCoords.RUBBLE_PILES.size == RUBBLE_PILES)
        }
    }
}

private fun String.asLocId(): Int = dev.openrune.rscm.RSCM.getRSCM(this)
