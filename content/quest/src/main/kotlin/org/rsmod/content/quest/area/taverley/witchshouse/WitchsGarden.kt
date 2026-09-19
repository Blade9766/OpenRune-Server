package org.rsmod.content.quest.area.taverley.witchshouse

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.BALL
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.EVICTION_TILE
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.NORA
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.SHED_KEY
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_DEFEATED_EXPERIMENT
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.inGarden
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.inPorch
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.inShed
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nora T. Hagg's back garden. She paces the lawn in the middle, and any trespasser she can see
 * from a few paces away - one not ducked behind a hedge - is cursed back out beside the boy,
 * losing the shed key and the ball. The shed key is hidden in the fountain; the shed holds the
 * ball and the experiment guarding it.
 */
class WitchsGarden
@Inject
constructor(
    private val witchsHouse: WitchsHouseQuest,
    private val experiments: WitchsExperiments,
    private val passages: GenericPassageScript,
    private val search: NpcSearch,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val playerList: PlayerList,
) : PluginScript() {

    private val catching = HashSet<PlayerUid>()

    private val hedgeTypes: List<ObjectServerType> by lazy {
        HEDGES.map { ServerCacheManager.getObject(it.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $it") }
    }

    private val ballType: ItemServerType =
        ServerCacheManager.getItem(BALL.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $BALL")

    override fun ScriptContext.startup() {
        onPlayerTimer(WATCH_TIMER) { watch() }
        onPlayerLogin {
            if (inGarden(player.coords) || inShed(player.coords)) {
                player.timer(WATCH_TIMER, 1)
            }
        }
        onOpLoc2(FOUNTAIN) { checkFountain() }
        onOpLoc1(SHED_DOOR) { shedDoor(it.loc, it.type, usedKey = false) }
        onOpLocU(SHED_DOOR, SHED_KEY) { shedDoor(it.loc, it.type, usedKey = true) }
        onOpObj3(ballType) { takeBall(it.obj) }
    }

    /* Nora's watch */

    private suspend fun ProtectedAccess.watch() {
        val here = coords
        if (!inGarden(here) && !inPorch(here) && !inShed(here)) {
            clearTimer(WATCH_TIMER)
            return
        }
        if (!inGarden(here) || player.uid in catching) {
            return
        }
        val nora = npcFind(here, NORA, SIGHT_RANGE, HuntVis.Off, search) ?: return
        if (!lineOfSight(nora.coords, here) || behindHedge(here, nora.coords)) {
            return
        }
        catching += player.uid
        try {
            caught(nora)
        } finally {
            catching -= player.uid
        }
    }

    /** Whether the tile beside [player], on the side facing [nora], holds a hedge to hide behind. */
    private fun behindHedge(player: CoordGrid, nora: CoordGrid): Boolean {
        val dz = player.z - nora.z
        val cover =
            if (dz != 0) {
                player.translateZ(-dz.sign)
            } else {
                player.translateX(-(player.x - nora.x).sign)
            }
        return hedgeTypes.any { locRepo.findExact(cover, it) != null }
    }

    private suspend fun ProtectedAccess.caught(nora: Npc) {
        ifClose()
        stopAction()
        nora.facePlayer(player)
        nora.say("Stop! Thief!")
        delay(1)
        nora.say("Klarata... Sepptento... Valkan!")
        nora.anim(CAST_SEQ)
        nora.spotanim(CASTING_SPOT, height = CASTING_HEIGHT)
        worldRepo.soundArea(nora, CAST_SOUND)
        worldRepo.projAnim(
            ProjAnim(
                spotanim = TRAVEL_SPOT.asRSCM(RSCMType.SPOTANIM),
                startHeight = 31,
                endHeight = 31,
                startTime = 61,
                endTime = IMPACT_CYCLES,
                angle = 16,
                progress = 128,
                sourceIndex = nora.slotId + 1,
                targetIndex = -(player.slotId + 1),
                startCoord = nora.coords,
                endCoord = player.coords,
            ),
        )
        spotanim(IMPACT_SPOT, delay = IMPACT_CYCLES, height = IMPACT_HEIGHT)
        soundSynth(HIT_SOUND, delay = IMPACT_CYCLES)
        delay(EVICT_DELAY)
        witchsHouse.relockBackDoor(player)
        for (obj in listOf(SHED_KEY, BALL)) {
            val count = inv.count(obj)
            if (count > 0) {
                invDel(inv, obj, count)
            }
        }
        experiments.dismiss(player)
        clearTimer(WATCH_TIMER)
        nora.resetFaceEntity()
        telejump(EVICTION_TILE)
    }

    /* The fountain */

    private suspend fun ProtectedAccess.checkFountain() {
        arriveDelay()
        anim(SEARCH_SEQ)
        if (inv.count(SHED_KEY) > 0 || bank.count(SHED_KEY) > 0) {
            objbox(
                SHED_KEY,
                "You already have the key that was in the secret compartment in this fountain.",
            )
            return
        }
        invAddOrDrop(objRepo, SHED_KEY)
        objbox(
            SHED_KEY,
            "You search for the secret compartment mentioned in the diary. Inside it you find a " +
                "small key. You take the key.",
        )
    }

    /* The shed */

    private suspend fun ProtectedAccess.shedDoor(
        door: BoundLocInfo,
        type: ObjectServerType,
        usedKey: Boolean,
    ) {
        val inside = coords.x >= door.coords.x
        when {
            inside -> experiments.dismiss(player)
            witchsHouse.stage(player) >= STAGE_DEFEATED_EXPERIMENT -> Unit
            !usedKey -> {
                mes("The shed door is locked.")
                return
            }
            playerList.any { it !== player && inShed(it.coords) } -> {
                startDialogue {
                    chatPlayer(
                        neutral,
                        "I'd better not go in there yet... I think I can hear someone inside!",
                    )
                }
                return
            }
            else -> {
                soundSynth(UNLOCK_SOUND)
                experiments.summon(player, form = 0, attack = false)
            }
        }
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.takeBall(ball: Obj) {
        if (witchsHouse.isComplete(player)) {
            mes("Another ball! The witch must have found another one. You decide to leave this one")
            mes("here.")
            return
        }
        if (witchsHouse.stage(player) == STAGE_DEFEATED_EXPERIMENT) {
            pickUp(ball)
            return
        }
        val experiment = experiments.of(player)
        if (experiment == null) {
            experiments.summon(player, form = 0, attack = false)
            mes("A shapeshifter appears, and knocks you back from the ball!")
            return
        }
        mes("The shapeshifter glares at you. You feel slightly weakened.")
        statSub(ATTACK, constant = 1, percent = 5)
        statSub(DEFENCE, constant = 1, percent = 5)
        experiments.attack(player, experiment)
        queueHit(delay = 1, type = HitType.Typeless, damage = 1 + player.hitpoints * 5 / 100)
    }

    private suspend fun ProtectedAccess.pickUp(ball: Obj) {
        if (BALL in inv) {
            mes("You already have the boys ball...")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        if (coords != ball.coords) {
            delay(1)
        }
        anim(TAKE_SEQ)
        if (!objRepo.del(ball)) {
            return
        }
        invAdd(inv, BALL)
        soundSynth(PICKUP_SOUND)
    }

    companion object {
        const val WATCH_TIMER = "timer.witchshouse_garden_watch"

        const val FOUNTAIN = "loc.witchfountain"
        const val SHED_DOOR = "loc.witchsheddoor"
        val HEDGES = listOf("loc.hedge", "loc.hedgecorner")

        const val SIGHT_RANGE = 3

        const val ATTACK = "stat.attack"
        const val DEFENCE = "stat.defence"

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val TAKE_SEQ = "seq.human_pickupfloor"
        const val CAST_SEQ = "seq.human_castcurse"

        const val CASTING_SPOT = "spotanim.curse_casting"
        const val TRAVEL_SPOT = "spotanim.curse_travel"
        const val IMPACT_SPOT = "spotanim.curse_impact"
        const val CASTING_HEIGHT = 92
        const val IMPACT_HEIGHT = 124
        const val IMPACT_CYCLES = 100
        const val EVICT_DELAY = 3

        const val CAST_SOUND = "synth.curse_cast_and_fire"
        const val HIT_SOUND = "synth.curse_hit"
        const val UNLOCK_SOUND = "synth.unlock"
        const val PICKUP_SOUND = "synth.pick"
    }
}
