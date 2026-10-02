package org.rsmod.content.quest.area.ardougne.sheepherder

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.FEED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_DISPOSED
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Farmer Brumty's plague enclosure: its western gate, feeding the penned sheep and the incinerator.
 *
 * Feeding and burning change the colour's state only after their animation delay, in the same
 * tick as the bones are dropped or taken, so an interrupted action leaves nothing half done.
 */
class PlagueEnclosure
@Inject
constructor(
    private val sheep: SheepHerderQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE_LEFT) { gate(it.loc) }
        onOpLoc1(GATE_RIGHT) { gate(it.loc) }
        for (colour in SheepColour.entries) {
            onOpNpcU(colour.shownNpc, FEED) { feed(it.npc, colour) }
            onOpLocU(INCINERATOR, colour.bones) { incinerate(it.loc, colour) }
        }
    }

    private suspend fun ProtectedAccess.gate(gate: BoundLocInfo) {
        arriveDelay()
        faceLoc(gate)
        val inside = HerdingRules.isInPen(coords)
        if (!inside) {
            val problem = sheep.protectionProblem(player)
            if (problem != null) {
                mes(problem)
                mes("Farmer Brumty won't have anyone in the plague enclosure without protection.")
                return
            }
        }
        val dest = CoordGrid(if (inside) OUTSIDE_X else INSIDE_X, gate.coords.z, 0)
        locRepo.del(gate, OPEN_CYCLES)
        soundSynth(OPEN_SOUND)
        playerWalk(dest)
    }

    suspend fun ProtectedAccess.feed(npc: Npc, colour: SheepColour) {
        if (!sheep.isActive(player)) {
            mes(
                if (sheep.stage(player) == 0) {
                    "You have no reason to poison anyone's sheep."
                } else {
                    "The job's done. There's no need to poison any more sheep."
                },
            )
            return
        }
        if (!npc.isType(colour.enclosureNpc)) {
            mes("You need to get the sheep into the enclosure before you feed it the poison.")
            return
        }
        sheep.protectionProblem(player)?.let {
            mes(it)
            return
        }
        if (sheep.state(player, colour) != SheepState.PENNED) {
            mes("You've already dealt with the ${colour.label} sheep.")
            return
        }
        faceSquare(npc.coords)
        anim(FEED_SEQ)
        mes("You hold out the feed and the ${colour.label} sheep eats it up.")
        npc.anim(SHEEP_DEATH_SEQ)
        delay(FEED_DELAY)
        if (sheep.state(player, colour) != SheepState.PENNED) {
            return
        }
        sheep.setState(player, colour, SheepState.BONES)
        objRepo.add(colour.bones, npc.coords, BONES_TICKS, receiver = player, reveal = BONES_TICKS)
        mes("The sheep lies down and goes still. All that's left are its bones.")
        mes("Collect the bones and burn them in the incinerator.")
    }

    suspend fun ProtectedAccess.incinerate(furnace: BoundLocInfo, colour: SheepColour) {
        arriveDelay()
        faceLoc(furnace)
        if (!sheep.isActive(player)) {
            mes("There's nothing that needs burning now.")
            return
        }
        sheep.protectionProblem(player)?.let {
            mes(it)
            return
        }
        anim(FURNACE_SEQ)
        soundSynth(FURNACE_SOUND)
        delay(BURN_DELAY)
        if (invDel(inv, colour.bones, 1).failure) {
            return
        }
        spotanimMap(worldRepo, SMOKE, furnace.coords)
        if (sheep.state(player, colour) != SheepState.BONES) {
            mes("You throw the bones into the incinerator.")
            mes("The ${colour.label} sheep had already been dealt with, so they make no difference.")
            return
        }
        sheep.setState(player, colour, SheepState.BURNED)
        mes("You put the ${colour.label} sheep's bones into the incinerator. They burn away to ash.")
        val left = sheep.remaining(player)
        if (left.isNotEmpty()) {
            mes("Still to dispose of: ${left.joinToString { it.label }}.")
            return
        }
        sheep.quest.setQuestStage(this, STAGE_DISPOSED)
        mes("<col=800000>That's all four sheep disposed of. Councillor Halgrive will want to know.</col>")
    }

    companion object {
        const val GATE_LEFT = "loc.plaguesheep_gatel"
        const val GATE_RIGHT = "loc.plaguesheep_gater"
        const val INCINERATOR = "loc.plaguesheep_furnace"

        const val OPEN_SOUND = "synth.picketgate_open"
        const val FURNACE_SOUND = "synth.furnace"
        const val FEED_SEQ = "seq.human_pickuptable"
        const val FURNACE_SEQ = "seq.human_furnace"
        const val SHEEP_DEATH_SEQ = "seq.sheep_update_death"
        const val SMOKE = "spotanim.smokepuff"

        const val OPEN_CYCLES = 2
        const val FEED_DELAY = 2
        const val BURN_DELAY = 2
        const val BONES_TICKS = 500

        /** The gate leaves hang on the east edge of x [OUTSIDE_X]. */
        const val OUTSIDE_X = 2594
        const val INSIDE_X = 2595
    }
}
