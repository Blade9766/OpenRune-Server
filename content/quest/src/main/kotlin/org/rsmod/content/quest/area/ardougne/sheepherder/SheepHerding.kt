package org.rsmod.content.quest.area.ardougne.sheepherder

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onNpcTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CATTLEPROD
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Prodding the grazing sheep towards Farmer Brumty's enclosure, and the sheep wandering home again.
 *
 * The field sheep are shared by everyone; whether one has been penned is the prodding player's own
 * colour varbit. A prod stops the sheep grazing and starts [RESTLESS_TIMER]: when it runs out the
 * sheep bleats a warning, and [RETURN_TIMER] later it walks back to its spawn by a real route. If
 * it still isn't home after [STUCK_TIMER] it is put back there, so no sheep stays wedged anywhere.
 * Any new prod cancels the whole sequence.
 */
class SheepHerding
@Inject
constructor(
    private val sheep: SheepHerderQuest,
    private val collision: CollisionFlagMap,
    private val routeFactory: RouteFactory,
    private val worldRepo: WorldRepository,
    private val players: PlayerList,
) : PluginScript() {
    private val steps = StepValidator(collision)

    override fun ScriptContext.startup() {
        for (colour in SheepColour.entries) {
            onOpNpc1(colour.shownNpc) { prod(it.npc, colour) }
        }
        onNpcTimer(RESTLESS_TIMER) { restless(npc) }
        onNpcTimer(RETURN_TIMER) { startReturn(npc) }
        onNpcTimer(STUCK_TIMER) { unstick(npc) }
    }

    suspend fun ProtectedAccess.prod(npc: Npc, colour: SheepColour) {
        if (npc.isType(colour.enclosureNpc)) {
            mes("This sheep is already safely penned. Use the poisoned feed on it.")
            return
        }
        val problem = prodProblem(player, colour)
        if (problem != null) {
            mes(problem)
            return
        }
        val dir = HerdingRules.direction(coords, npc.coords) ?: return
        anim(PROD_SEQ)
        holdForHerding(npc)
        val path = HerdingRules.push(steps, npc.coords, dir, BLOCKERS)
        if (path.isEmpty()) {
            npc.say("Baa!")
            mes("The ${colour.label} sheep can't go any further ${dir.label}: something's in the way.")
            if (HerdingRules.isDeadEnd(steps, npc.coords)) {
                wedged(npc, colour, ticks = 1)
            }
            return
        }
        bleat(npc)
        HintArrows.hintCoord(player, HerdingRules.GATE_THRESHOLD.first())
        val dest = path.last()
        npc.walk(dest)
        if (!HerdingRules.isThreshold(dest)) {
            mes("You prod the ${colour.label} sheep and it trots ${dir.label}.")
            if (HerdingRules.isDeadEnd(steps, dest)) {
                wedged(npc, colour, ticks = path.size + 1)
            }
            showTutorial()
            return
        }
        mes("You prod the ${colour.label} sheep towards the gate...")
        delay(path.size)
        pen(player, npc, colour)
    }

    /** Why [player] can't herd a [colour] sheep right now, or null when they can. */
    fun prodProblem(player: Player, colour: SheepColour): String? {
        val stage = sheep.stage(player)
        return when {
            stage == 0 -> "You have no reason to go poking somebody else's sheep."
            stage > STAGE_STARTED -> "You've done your part. Leave the rest of the flock in peace."
            sheep.state(player, colour) != SheepState.LOOSE ->
                "You've already penned a ${colour.label} sheep. One of each colour is enough."
            else ->
                sheep.protectionProblem(player)
                    ?: when {
                        CATTLEPROD in player.worn -> null
                        CATTLEPROD in player.inv -> "You need to wield the cattleprod to herd sheep."
                        else ->
                            "You need a cattleprod to herd the sheep. Farmer Brumty keeps one " +
                                "by the incinerator in his enclosure."
                    }
        }
    }

    private suspend fun ProtectedAccess.showTutorial() {
        if (sheep.hasSeenTutorial(player)) {
            return
        }
        sheep.markTutorialSeen(player)
        mesbox(
            "A prodded sheep runs straight away from you: stand south of it to drive it north, " +
                "east of it to drive it west. Walk round it to change direction.",
        )
        mesbox(
            "Fences, walls, rocks and trees stop it short. Leave a sheep alone for long and it " +
                "will bleat, then wander back home. The western gate of the enclosure is marked " +
                "with an arrow.",
        )
    }

    fun pen(player: Player, npc: Npc, colour: SheepColour) {
        HintArrows.hintStop(player)
        sendHome(npc)
        if (sheep.state(player, colour) != SheepState.LOOSE) {
            return
        }
        sheep.setState(player, colour, SheepState.PENNED)
        player.mes("The ${colour.label} sheep trots through the gate and into the enclosure.")
        player.mes("Now feed it the poisoned sheep feed.")
    }

    /** The sheep is somewhere it can't be prodded out of, so it heads home once it gets there. */
    private fun ProtectedAccess.wedged(npc: Npc, colour: SheepColour, ticks: Int) {
        mes("The ${colour.label} sheep is wedged in where you can't get behind it. It'll wander back to its pasture.")
        npc.clearTimer(RESTLESS_TIMER)
        npc.timer(RETURN_TIMER, ticks)
    }

    private fun holdForHerding(npc: Npc) {
        npc.clearTimer(RETURN_TIMER)
        npc.clearTimer(STUCK_TIMER)
        npc.moveRestrict = npc.type.moveRestrict
        npc.noneMode()
        npc.timer(RESTLESS_TIMER, RESTLESS_TICKS)
    }

    fun restless(npc: Npc) {
        npc.clearTimer(RESTLESS_TIMER)
        if (npc.coords == npc.spawnCoords) {
            npc.defaultMode()
            return
        }
        npc.say("Baa...?")
        for (player in players) {
            if (player == null || !sheep.isActive(player)) continue
            if (player.coords.chebyshevDistance(npc.coords) > WARN_RADIUS) continue
            player.mes("The ${colourOf(npc)?.label ?: "plague"} sheep looks restless. Prod it soon or it'll wander home.")
        }
        npc.timer(RETURN_TIMER, RETURN_GRACE_TICKS)
    }

    fun startReturn(npc: Npc) {
        npc.clearTimer(RETURN_TIMER)
        npc.say("Baa!")
        npc.timer(STUCK_TIMER, STUCK_TICKS)
        npc.walkTo(routeFactory, npc.spawnCoords) {
            if (npc.coords.chebyshevDistance(npc.spawnCoords) > HOME_SLACK) {
                unstick(npc)
                return@walkTo
            }
            npc.clearTimer(STUCK_TIMER)
            npc.defaultMode()
        }
    }

    fun unstick(npc: Npc) {
        npc.clearTimer(STUCK_TIMER)
        if (npc.coords != npc.spawnCoords) {
            worldRepo.spotanimMap(SpotanimType(PUFF.asRSCM(RSCMType.SPOTANIM)), npc.coords)
            sendHome(npc)
            return
        }
        npc.defaultMode()
    }

    private fun sendHome(npc: Npc) {
        npc.clearTimer(RESTLESS_TIMER)
        npc.clearTimer(RETURN_TIMER)
        npc.clearTimer(STUCK_TIMER)
        npc.moveRestrict = npc.type.moveRestrict
        npc.telejump(collision, npc.spawnCoords)
        npc.defaultMode()
    }

    private fun bleat(npc: Npc) {
        npc.say("Baa!")
        worldRepo.soundArea(npc, BLEAT_SOUND)
    }

    private fun colourOf(npc: Npc): SheepColour? = SheepColour.entries.firstOrNull { npc.isType(it.fieldNpc) }

    companion object {
        const val PROD_SEQ = "seq.cattleprod"
        const val BLEAT_SOUND = "synth.sheep_atmospheric1"
        const val PUFF = "spotanim.smokepuff"

        const val RESTLESS_TIMER = "timer.sheepherder_restless"
        const val RETURN_TIMER = "timer.sheepherder_return"
        const val STUCK_TIMER = "timer.sheepherder_stuck"

        /** About 18 seconds of peace before the warning, then 6 more before the sheep heads home. */
        const val RESTLESS_TICKS = 30
        const val RETURN_GRACE_TICKS = 10
        const val STUCK_TICKS = 60

        const val WARN_RADIUS = 15
        const val HOME_SLACK = 1

        const val BLOCKERS = CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS
    }
}
