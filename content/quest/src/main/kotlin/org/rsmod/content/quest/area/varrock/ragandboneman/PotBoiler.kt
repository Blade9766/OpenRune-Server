package org.rsmod.content.quest.area.varrock.ragandboneman

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_BOILED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_BOILING
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_EMPTY
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_LOADED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_LOGS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.LOGS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT_OF_VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_BOILED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.TINDERBOX
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Odd Old Man's pot-boiler: a log beneath, a specimen in vinegar on top, a tinderbox to light
 * it, then [BOIL_STEPS] steps of [BOIL_STEP_TICKS] ticks (12 seconds) until the vinegar has boiled
 * away and the polished specimen can be lifted out with the empty pot.
 *
 * Each player sees their own boiler through `varbit.rag_boiler`, so nobody else's pot is ever in
 * the way. Every step checks the state and takes its log or pot before changing it, with no
 * suspension in between, so a repeated click finds the boiler already moved on. The countdown is
 * a soft timer, so it keeps going while the player walks off or talks; the steps left are a
 * permanent var, and logging in restarts the timer from there.
 */
@Singleton
class PotBoiler
@Inject
constructor(
    private val rb: RagAndBoneManQuest,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU(BOILER) { useOnBoiler(it.objType, it.invSlot) }
        onOpLoc1(BOILER_WITH_POT) { removePot() }
        onOpLoc1(BOILER_WITH_BONE) { removeBone() }
        onPlayerSoftTimer(BOIL_TIMER) { boilStep(player) }
        onPlayerLogin { resume(player) }
    }

    private fun ProtectedAccess.useOnBoiler(objType: ItemServerType, slot: Int) {
        val obj = objType.internalName
        if (!rb.isStarted(player)) {
            mes("The Odd Old Man would rather you kept your hands off his pot-boiler.")
            return
        }
        val soaking = Specimen.byInVinegar(obj)
        when {
            obj == LOGS -> addLog(slot)
            soaking != null -> loadPot(soaking, slot)
            obj == TINDERBOX -> light()
            "logs" in objType.name.lowercase() ->
                mes("Plain ordinary logs will do. Save those for something that deserves them.")
            Specimen.byRaw(obj) != null ->
                mes("That needs to soak in a pot of vinegar before it goes anywhere near the boiler.")
            Specimen.byPolished(obj) != null -> mes("That's already polished. Boiling it again would be a waste of vinegar.")
            obj == POT_OF_VINEGAR -> mes("There's no specimen in that pot. Put one in to soak first.")
            obj == POT -> mes("Boiling an empty pot won't clean anything.")
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ProtectedAccess.addLog(slot: Int) {
        if (rb.boilerState(player) != BOILER_EMPTY) {
            mes("There's already a log under the pot-boiler.")
            return
        }
        if (invDel(inv, LOGS, 1, slot = slot).failure) {
            return
        }
        rb.setBoiler(player, BOILER_LOGS, specimen = null)
        anim(PLACE_SEQ)
        mes("You place a log beneath the pot-boiler.")
    }

    private fun ProtectedAccess.loadPot(specimen: Specimen, slot: Int) {
        when (rb.boilerState(player)) {
            BOILER_EMPTY -> {
                mes("You need to put a log beneath the pot-boiler first.")
                return
            }
            BOILER_LOGS -> Unit
            else -> {
                mes("There's already a pot on the pot-boiler. One specimen at a time.")
                return
            }
        }
        if (invDel(inv, specimen.inVinegar, 1, slot = slot).failure) {
            return
        }
        rb.setBoiler(player, BOILER_LOADED, specimen)
        anim(PLACE_SEQ)
        mes("You set the pot with the ${name(specimen)} on the pot-boiler.")
    }

    private fun ProtectedAccess.light() {
        when (rb.boilerState(player)) {
            BOILER_EMPTY -> mes("There's nothing beneath the pot-boiler to light.")
            BOILER_LOGS -> mes("Put a pot on the boiler before you light the log, or the fire will be wasted.")
            BOILER_BOILING -> mes("The fire is already burning nicely.")
            BOILER_BOILED -> mes("The pot has already boiled dry. Take the specimen out.")
            BOILER_LOADED -> {
                rb.setBoilSteps(player, BOIL_STEPS)
                rb.setBoiler(player, BOILER_BOILING)
                player.softTimer(BOIL_TIMER, BOIL_STEP_TICKS)
                anim(LIGHT_SEQ)
                soundSynth(LIGHT_SOUND)
                spotanimMap(worldRepo, SMOKE_SPOTANIM, BOILER_TILE)
                mes("You light the log beneath the pot-boiler. The vinegar starts to warm.")
            }
        }
    }

    /** One step of the countdown, or nothing when the boiler isn't boiling any more. */
    fun boilStep(player: Player) {
        if (rb.boilerState(player) != BOILER_BOILING) {
            player.clearSoftTimer(BOIL_TIMER)
            return
        }
        val left = (rb.boilStepsLeft(player) - 1).coerceAtLeast(0)
        rb.setBoilSteps(player, left)
        val specimen = rb.boilerSpecimen(player)
        when (left) {
            0 -> {
                player.clearSoftTimer(BOIL_TIMER)
                rb.setBoiler(player, BOILER_BOILED)
                player.soundSynth(DONE_SOUND)
                worldRepo.spotanimMap(SpotanimType(STEAM_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), BOILER_TILE)
                player.mes("The vinegar boils away, leaving the ${specimen?.let(::name) ?: "specimen"} polished in the pot.")
            }
            BOIL_STEPS - 1 -> {
                player.soundSynth(BUBBLE_SOUND)
                player.mes("The vinegar in the pot-boiler begins to bubble. [${progress(left)}]")
            }
            else -> {
                player.soundSynth(BUBBLE_SOUND)
                player.mes("Steam billows from the pot-boiler. [${progress(left)}]")
            }
        }
    }

    private fun ProtectedAccess.removePot() {
        val specimen = rb.boilerSpecimen(player)
        if (rb.boilerState(player) != BOILER_LOADED || specimen == null) {
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You don't have room to take the pot off the pot-boiler.")
            return
        }
        if (invAdd(inv, specimen.inVinegar, 1).failure) {
            return
        }
        rb.setBoiler(player, BOILER_LOGS, specimen = null)
        anim(PLACE_SEQ)
        mes("You lift the pot with the ${name(specimen)} back off the pot-boiler.")
    }

    private fun ProtectedAccess.removeBone() {
        val specimen = rb.boilerSpecimen(player)
        if (rb.boilerState(player) != BOILER_BOILED || specimen == null) {
            return
        }
        if (inv.freeSpace() < 2) {
            mes("You need two free inventory spaces: one for the ${name(specimen)} and one for the pot.")
            return
        }
        val taken =
            player.invTransaction(inv) {
                val pack = select(inv)
                insert {
                    into = pack
                    obj = specimen.polished.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = POT.asRSCM()
                    strictCount = 1
                }
            }
        if (taken.failure) {
            return
        }
        rb.clearBoiler(player)
        anim(PLACE_SEQ)
        mes("You take the polished ${name(specimen)} out of the pot. It gleams. You keep the empty pot.")
        rb.advanceTo(this, STAGE_BOILED)
    }

    /** Picks up a boil cut short by logging out, and tidies a boiler that lost track of its pot. */
    fun resume(player: Player) {
        val state = rb.boilerState(player)
        if (state >= BOILER_LOADED && rb.boilerSpecimen(player) == null) {
            rb.clearBoiler(player)
            return
        }
        if (state == BOILER_BOILING) {
            if (rb.boilStepsLeft(player) == 0) {
                rb.setBoilSteps(player, BOIL_STEPS)
            }
            player.softTimer(BOIL_TIMER, BOIL_STEP_TICKS)
        }
    }

    private fun progress(left: Int): String {
        val done = BOIL_STEPS - left
        return "#".repeat(done) + "-".repeat(left)
    }

    private fun name(specimen: Specimen): String = specimen.label.lowercase()

    companion object {
        const val BOILER = "loc.rag_multi_potboiler"
        const val BOILER_WITH_POT = "loc.rag_potboiler_with_pot"
        const val BOILER_WITH_BONE = "loc.rag_potboiler_with_pot_boiled"
        val BOILER_TILE = CoordGrid(3360, 3505, 0)

        const val BOIL_TIMER = "timer.rag_boil"
        const val BOIL_STEPS = 4
        const val BOIL_STEP_TICKS = 5

        const val PLACE_SEQ = "seq.human_pickuptable"
        const val LIGHT_SEQ = "seq.human_createfire"
        const val LIGHT_SOUND = "synth.fire_lit"
        const val BUBBLE_SOUND = "synth.cauldron_bubbling"
        const val DONE_SOUND = "synth.boil_off"
        const val SMOKE_SPOTANIM = "spotanim.smokepuff"
        const val STEAM_SPOTANIM = "spotanim.smokepuff_large"
    }
}
