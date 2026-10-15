package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.ENCHANTED_VIAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLLOW_REED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.MACHETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ASKED_HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_COLLECTED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FINAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_FILLED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GERMINATED_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_POOL_DRIED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_REPLACED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SACRED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS_GERMINATED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.npcs.Gujuo
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The sacred pool in the middle of the Kharazi Jungle, the fertile soil the Yommi tree grows in,
 * and the corrupted totem poles the finished Yommi totem replaces.
 *
 * The pool can only be reached through a hollow reed and only keeps its purity in a blessed gold
 * bowl. Once the seeds are germinated in it Nezikchened fouls it, and the player has to find the
 * source below the Viyeldi caves instead.
 *
 * A Yommi tree grows in a few minutes: a sapling that has to be watered with pure water, an adult
 * to fell with a rune axe, a trunk to trim and a totem to carve, each of which rots if left too
 * long. Every tree belongs to whoever planted it.
 */
class YommiTree
@Inject
constructor(
    private val legends: LegendsQuest,
    private val nezikchened: Nezikchened,
    private val gujuo: Gujuo,
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    private val planters = HashMap<CoordGrid, PlayerUid>()

    override fun ScriptContext.startup() {
        onOpLoc1(POOL) {
            mesbox("It looks like a small babbling brook that comes from and disappears underground again. The water bubbles with a strange effervescence.")
        }
        onOpLoc2(POOL) {
            mesbox("It looks like you'll have problems getting some of the water as it's difficult to reach with all the rocks around it. There is a narrow gap that you simply won't be able to get a container into.")
        }
        onOpLocU(POOL) { useOnPool(it.vis, it.objType.internalName) }
        onOpLoc1(POOL_POLLUTED) { pollutedPool() }
        onOpLocU(POOL_POLLUTED) { pollutedPool() }
        onOpLoc1(REEDS) { cutReed(null) }
        onOpLocU(REEDS) { cutReed(it.objType.internalName) }

        onOpLocU(SOIL) { plant(it.vis, it.objType.internalName) }
        onOpLocU(SAPLING) { water(it.vis, it.objType.internalName) }
        onOpLocU(ADULT) { carve(it.vis, it.objType.internalName, FELL) }
        onOpLocU(FELLED) { carve(it.vis, it.objType.internalName, TRIM) }
        onOpLocU(TRIMMED) { carve(it.vis, it.objType.internalName, SHAPE) }
        onOpLoc1(TOTEM) { liftTotem(it.vis) }
        for ((rotten, message) in ROTTEN) {
            onOpLocU(rotten) { clearRotten(it.vis, it.objType.internalName, message) }
        }

        onOpLoc1(TOTEM_POLE) { lookAtTotem(it.vis) }
        onOpLoc1(TOTEM_POLE_GOOD) {
            mesbox("This totem pole is truly awe inspiring. It depicts powerful Karamja jungle animals. It is very well carved and brings a sense of power and spiritual fulfillment to anyone who looks at it.")
        }
        onOpLoc1(TOTEM_POLE_EVIL) {
            mesbox("This totem pole looks corrupted. You don't like to look at it for too long.")
        }
        onOpLocU(TOTEM_POLE) { useOnTotemPole(it.vis, it.objType.internalName) }
        onOpLocU(TOTEM_POLE_EVIL) { useOnTotemPole(it.vis, it.objType.internalName) }
    }

    private suspend fun ProtectedAccess.pollutedPool() {
        arriveDelay()
        mesbox(POLLUTED_TEXT)
    }

    private suspend fun ProtectedAccess.cutReed(tool: String?) {
        arriveDelay()
        val cutter =
            when {
                tool == KNIFE || (tool == null && inv.count(KNIFE) > 0) -> KNIFE
                tool == MACHETE || (tool == null && carries(MACHETE)) -> MACHETE
                tool == null -> {
                    mesbox("These tall reeds look nice and long with a long tube for a stem. They reach all the way down to the water. You'd need something sharp to cut one.")
                    return
                }
                else -> {
                    mes("Nothing interesting happens.")
                    return
                }
            }
        anim(if (cutter == KNIFE) KNIFE_SEQ else MACHETE_SEQ)
        delay(1)
        mes(if (cutter == KNIFE) "You use your knife to cut down a reed." else "You use your machete to cut down a reed.")
        invAdd(inv, HOLLOW_REED, 1)
    }

    private suspend fun ProtectedAccess.useOnPool(pool: BoundLocInfo, obj: String) {
        arriveDelay()
        if (obj != HOLLOW_REED) {
            if (obj in TOO_WIDE) {
                mesbox("The water is too akward to get to, the gap to the water is too narrow to reach with this item.")
            } else {
                mes("Nothing interesting happens.")
            }
            return
        }
        val stage = legends.stage(player)
        if (stage in STAGE_GERMINATED_SEEDS until STAGE_DEFEATED_NEZIKCHENED_WATER) {
            legends.advanceFrom(this, STAGE_GERMINATED_SEEDS, STAGE_POOL_DRIED)
            mesbox(POLLUTED_TEXT)
            locRepo.change(pool, POOL_POLLUTED, POLLUTED_TICKS)
            return
        }
        val fill = FILLS.firstOrNull { inv.count(it.empty) > 0 }
        if (fill == null) {
            mes("You start to syphon some of the water up the tube...")
            delay(2)
            mes("But you have nothing to put the water in.")
            return
        }
        invDel(inv, fill.empty, 1)
        invAdd(inv, fill.full, 1)
        if (fill.usesReed) {
            invDel(inv, HOLLOW_REED, 1)
        }
        if (fill.full == BLESSED_BOWL_PURE) {
            player.legendsBowlUses = 0
            legends.advanceFrom(this, STAGE_DEFEATED_NEZIKCHENED_WATER, STAGE_SACRED_WATER)
            legends.advanceFrom(this, STAGE_ASKED_HOLY_WATER, STAGE_FILLED_BOWL)
        }
        objbox(fill.full, fill.message)
        if (fill.usesReed) {
            objbox(HOLLOW_REED, "The hollow reed is soaked through with water and is now all soggy.")
        }
    }

    private suspend fun ProtectedAccess.plant(soil: BoundLocInfo, obj: String) {
        arriveDelay()
        when (obj) {
            YOMMI_SEEDS -> {
                mesbox("These seeds need to be germinated before you can plant them.")
                return
            }
            YOMMI_SEEDS_GERMINATED -> Unit
            else -> {
                mes("Nothing interesting happens.")
                return
            }
        }
        if (inv.count(YOMMI_TOTEM) > 0) {
            mesbox("You have already made the Yommi tree Totem Pole. You don't need to grow another tree.")
            return
        }
        if (player.herbloreLvl < REQUIRED_HERBLORE) {
            mesbox("You need a Herblore skill of at least 45 to attempt to grow the Yommi tree.")
            return
        }
        if (player.woodcuttingLvl < REQUIRED_WOODCUTTING) {
            mesbox("You need a Woodcutting skill of at least 50 to attempt to fell a Yommi tree.")
            return
        }
        if (inv.count(BLESSED_BOWL_PURE) == 0) {
            mesbox("You'll need some pure sacred water to feed the tree when it starts growing.")
            return
        }
        anim(PLANT_SEQ)
        if (!statRandom(HERBLORE, PLANT_LOW, PLANT_HIGH, 0) || legends.stage(player) < STAGE_SACRED_WATER) {
            invDel(inv, YOMMI_SEEDS_GERMINATED, 1)
            mesbox("You planted the seed incorrectly in the fertile soil. The plant withers and dies.")
            return
        }
        mes("You plant the Yommi tree seed in the soil.")
        invDel(inv, YOMMI_SEEDS_GERMINATED, 1)
        planters[soil.coords] = player.uid
        delay(1)
        mes("It starts to grow at a remarkable rate.")
        delay(1)
        mes("It looks as if this Yommi tree needs to be watered.")
        grow(soil.coords, BABY, 2)
        worldQueues.add(2) { grow(soil.coords, SAPLING, STAGE_TICKS, rot = SAPLING_DEAD) }
    }

    private suspend fun ProtectedAccess.water(sapling: BoundLocInfo, obj: String) {
        arriveDelay()
        if (obj != BLESSED_BOWL_PURE) {
            mes("Nothing interesting happens.")
            return
        }
        if (!ownsTree(sapling)) {
            return
        }
        mes("You water the Yommi tree from the Golden Bowl...")
        invDel(inv, BLESSED_BOWL_PURE, 1)
        invAdd(inv, BLESSED_BOWL, 1)
        player.legendsBowlUses = 0
        delay(1)
        mes("It grows at a remarkable rate...")
        delay(1)
        mes("Soon the tree stops growing.")
        mes("It looks tall enough now to make a good-sized totem pole.")
        grow(sapling.coords, ADULT, STAGE_TICKS, rot = ADULT_DEAD)
    }

    private suspend fun ProtectedAccess.carve(tree: BoundLocInfo, obj: String, step: Int) {
        arriveDelay()
        if (!isAxe(obj)) {
            mes("Nothing interesting happens.")
            return
        }
        if (!ownsTree(tree)) {
            return
        }
        if (obj != RUNE_AXE) {
            mesbox("You need a better axe than that.")
            return
        }
        anim(RUNE_AXE_SEQ)
        soundSynth(CHOP_SOUND)
        when (step) {
            FELL -> {
                mes("You prepare to chop the Yommi tree.")
                delay(1)
                mes("You chop the Yommi tree down.")
                soundSynth(TREE_FALL_SOUND)
                resetAnim()
                grow(tree.coords, FELLED, STAGE_TICKS, rot = FELLED_ROTTEN)
            }
            TRIM -> {
                mes("You professionally wield your axe...")
                delay(1)
                mes("and trim the branches from the Yommi Tree.")
                resetAnim()
                grow(tree.coords, TRIMMED, STAGE_TICKS, rot = TRIMMED_ROTTEN)
            }
            else -> {
                mes("You professionally wield your axe...")
                delay(1)
                mes("as you carve a wonderful totem pole from the Yommi tree trunk.")
                resetAnim()
                grow(tree.coords, TOTEM, STAGE_TICKS, rot = TOTEM_ROTTEN)
            }
        }
    }

    private suspend fun ProtectedAccess.liftTotem(totem: BoundLocInfo) {
        arriveDelay()
        if (!ownsTree(totem)) {
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough space for this in your inventory.")
            return
        }
        mes("This totem pole looks very heavy...")
        anim(PICKUP_SEQ)
        delay(1)
        if (legends.stage(player) < STAGE_COLLECTED_TOTEM) {
            legends.setStage(this, STAGE_COLLECTED_TOTEM)
            player.legendsHeroesSlain = 0
        }
        invAdd(inv, YOMMI_TOTEM, 1)
        mes("But you manage to lift it.")
        planters.remove(totem.coords)
        grow(totem.coords, DAMAGED_EARTH, ROTTEN_TICKS)
    }

    private suspend fun ProtectedAccess.clearRotten(tree: BoundLocInfo, obj: String, message: String) {
        arriveDelay()
        if (!isAxe(obj)) {
            mes("Nothing interesting happens.")
            return
        }
        if (obj != RUNE_AXE) {
            mesbox("You need a better axe than that.")
            return
        }
        mes(message)
        anim(RUNE_AXE_SEQ)
        repeat(3) {
            val roll = random.of(10)
            when {
                roll < 1 -> {
                    invAdd(inv, MAGIC_LOGS, 1)
                    mes("You get some magic logs.")
                }
                roll < 5 -> {
                    invAdd(inv, LOGS, 1)
                    mes("You get some logs.")
                }
                else -> return@repeat
            }
        }
        planters.remove(tree.coords)
        val info = locRepo.findExact(tree.coords, ServerCacheManager.getObject(tree.id) ?: return) ?: return
        locRepo.del(info, 1)
    }

    private fun ProtectedAccess.ownsTree(tree: BoundLocInfo): Boolean {
        if (planters[tree.coords] == player.uid) {
            return true
        }
        mes("This is not your Yommi tree.")
        return false
    }

    /**
     * Swaps the growth stage at [coords] to [into] for [ticks], and turns it to [rot] if it is
     * still at that stage once it has stood for [ROT_AFTER] ticks. The soil comes back when the
     * last stage times out.
     */
    private fun grow(coords: CoordGrid, into: String, ticks: Int, rot: String? = null) {
        val current = locRepo.findAll(coords).firstOrNull { loc -> GROWTH_IDS.any { it == loc.id } } ?: return
        val intoType = ServerCacheManager.getObject(into.asRSCM(RSCMType.LOC)) ?: return
        locRepo.change(current, intoType, ticks)
        if (rot == null) {
            return
        }
        worldQueues.add(ROT_AFTER) {
            val standing = locRepo.findAll(coords).firstOrNull { it.id == intoType.id } ?: return@add
            val rotType = ServerCacheManager.getObject(rot.asRSCM(RSCMType.LOC)) ?: return@add
            locRepo.change(standing, rotType, ROTTEN_TICKS)
            planters.remove(coords)
        }
    }

    private suspend fun ProtectedAccess.lookAtTotem(pole: BoundLocInfo) {
        arriveDelay()
        if (legends.stage(player) >= STAGE_REPLACED_TOTEM) {
            locRepo.change(pole, TOTEM_POLE_GOOD, LOOK_TICKS)
            mesbox("This totem pole is truly awe inspiring. It depicts powerful Karamja jungle animals. It is very well carved and brings a sense of power and spiritual fulfillment to anyone who looks at it.")
            return
        }
        mesbox("This totem pole looks corrupted. You don't like to look at it for too long.")
    }

    private suspend fun ProtectedAccess.useOnTotemPole(pole: BoundLocInfo, obj: String) {
        arriveDelay()
        if (obj != YOMMI_TOTEM) {
            mes("Nothing interesting happens.")
            return
        }
        val stage = legends.stage(player)
        when {
            stage < STAGE_DEFEATED_NEZIKCHENED_FINAL -> {
                mesbox(
                    "You attempt to replace the evil totem pole. A black cloud emanates from the evil " +
                        "totem pole and it slowly forms into the dread demon Nezikchened.",
                )
                with(nezikchened) { defendTotem() }
            }
            stage == STAGE_DEFEATED_NEZIKCHENED_FINAL -> {
                mesbox(
                    "You remove the evil totem pole and replace it with the one you carved yourself. As " +
                        "you do so, you feel a lightness in the air, almost as if the Kharazi Jungle were sighing.",
                )
                legends.setStage(this, STAGE_REPLACED_TOTEM)
                invDel(inv, YOMMI_TOTEM, 1)
                with(gujuo) { approach() }
                locRepo.change(pole, TOTEM_POLE_GOOD, LOOK_TICKS)
            }
            else -> {
                mesbox("You have already replaced the evil totem pole with your own. You feel a great sense of accomplishment.")
                locRepo.change(pole, TOTEM_POLE_GOOD, LOOK_TICKS)
            }
        }
    }

    private fun isAxe(obj: String): Boolean =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.isContentType(AXE_CONTENT) == true

    private data class Fill(val empty: String, val full: String, val usesReed: Boolean, val message: String)

    private companion object {
        const val POOL = "loc.sacred_water"
        const val POOL_POLLUTED = "loc.sacred_water_polluted"
        const val REEDS = "loc.tall_reeds"
        const val SOIL = "loc.fertilesoil"
        const val BABY = "loc.yommitree_baby"
        const val SAPLING = "loc.yommitree_sapling"
        const val SAPLING_DEAD = "loc.yommitree_saplingdead"
        const val ADULT = "loc.yommitree_adult"
        const val ADULT_DEAD = "loc.yommitree_adult_dead"
        const val FELLED = "loc.yommitree_felled"
        const val FELLED_ROTTEN = "loc.yommitree_felled_rotten"
        const val TRIMMED = "loc.yommitree_trimmed"
        const val TRIMMED_ROTTEN = "loc.yommitree_trimmed_rotten"
        const val TOTEM = "loc.yommitree_totem"
        const val TOTEM_ROTTEN = "loc.yommitree_totem_rotten"
        const val DAMAGED_EARTH = "loc.damaged_earth"
        const val TOTEM_POLE = "loc.lg_ord_totem_pole"
        const val TOTEM_POLE_GOOD = "loc.lg_totem_pole_good"
        const val TOTEM_POLE_EVIL = "loc.lg_totem_pole_evil"

        val GROWTH_IDS by lazy {
            listOf(
                SOIL, BABY, SAPLING, SAPLING_DEAD, ADULT, ADULT_DEAD, FELLED, FELLED_ROTTEN, TRIMMED,
                TRIMMED_ROTTEN, TOTEM, TOTEM_ROTTEN, DAMAGED_EARTH,
            ).map { it.asRSCM(RSCMType.LOC) }
        }

        val ROTTEN =
            listOf(
                SAPLING_DEAD to "You use your axe on the dead sapling Yommi tree.",
                ADULT_DEAD to "You use your axe on the rotten adult Yommi tree.",
                FELLED_ROTTEN to "You use your axe on the rotten felled Yommi tree.",
                TRIMMED_ROTTEN to "You use your axe on the rotten trimmed Yommi tree.",
                TOTEM_ROTTEN to "You use your axe on the rotten Yommi totem pole.",
            )

        const val KNIFE = "obj.knife"
        const val RUNE_AXE = "obj.rune_axe"
        const val LOGS = "obj.logs"
        const val MAGIC_LOGS = "obj.magic_logs"
        const val AXE_CONTENT = "content.woodcutting_axe"

        val TOO_WIDE =
            setOf("obj.bucket_empty", "obj.vial_empty", GOLD_BOWL, BLESSED_BOWL, ENCHANTED_VIAL)

        val FILLS =
            listOf(
                Fill(BLESSED_BOWL, BLESSED_BOWL_PURE, true, "You use the cut reed plant to syphon some water from the pool into your blessed golden bowl. The water seems to bubble and sparkle as if alive."),
                Fill(ENCHANTED_VIAL, HOLY_WATER, true, "You use the cut reed plant to syphon some water from the pool into your enchanted vial. The water seems to bubble and sparkle as if alive."),
                Fill(GOLD_BOWL, GOLD_BOWL_PURE, true, "You use the cut reed plant to syphon some water from the pool into your golden bowl. The water doesn't seem as sparkly as it looked in the pool..."),
                Fill("obj.bowl_empty", "obj.bowl_water", false, "You use the cut reed plant to syphon some water from the pool into your bowl The water doesn't seem as sparkly as it looked in the pool."),
                Fill("obj.bucket_empty", "obj.bucket_water", false, "You use the cut reed plant to syphon some water from the pool into your bucket The water doesn't seem as sparkly as it looked in the pool."),
                Fill("obj.jug_empty", "obj.jug_water", false, "You use the cut reed plant to syphon some water from the pool into your jug The water doesn't seem as sparkly as it looked in the pool."),
                Fill("obj.vial_empty", "obj.vial_water", true, "You use the cut reed plant to syphon some water from the pool into your vial The water doesn't seem as sparkly as it looked in the pool."),
            )

        const val POLLUTED_TEXT =
            "It looks as if this pool has dried up. A thick black sludge has replaced the sparkling " +
                "pure water. There is a disgusting stench of death that emanates from this area. " +
                "Maybe Gujuo knows what's happened."

        const val HERBLORE = "stat.herblore"
        const val REQUIRED_HERBLORE = 45
        const val REQUIRED_WOODCUTTING = 50
        const val PLANT_LOW = 40
        const val PLANT_HIGH = 243

        const val FELL = 0
        const val TRIM = 1
        const val SHAPE = 2
        const val STAGE_TICKS = 100
        const val ROT_AFTER = 51
        const val ROTTEN_TICKS = 50
        const val POLLUTED_TICKS = 30
        const val LOOK_TICKS = 20

        const val KNIFE_SEQ = "seq.human_knife_slash"
        const val MACHETE_SEQ = "seq.human_machette_chop"
        const val PLANT_SEQ = "seq.human_pickupfloor"
        const val PICKUP_SEQ = "seq.human_pickupfloor"
        const val RUNE_AXE_SEQ = "seq.human_woodcutting_rune_axe"
        const val CHOP_SOUND = "synth.woodchop_4"
        const val TREE_FALL_SOUND = "synth.tree_fall_sound"
    }
}
