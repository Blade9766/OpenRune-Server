package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.time.epochMinute
import org.rsmod.content.skills.farming.Farming
import org.rsmod.content.skills.farming.data.Crop
import org.rsmod.content.skills.farming.data.Crops
import org.rsmod.content.skills.farming.data.FarmingPatch
import org.rsmod.content.skills.farming.data.FarmingPatches
import org.rsmod.content.skills.farming.data.PatchKind
import org.rsmod.content.skills.farming.state.Compost
import org.rsmod.content.skills.farming.state.FarmingStore
import org.rsmod.content.skills.farming.state.PatchState
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Every interaction with an allotment, herb, flower or hops patch.
 *
 * Handlers are bound to the patch multilocs' *child* locs - the weeds, the growing crop, the
 * diseased and dead forms - because that is the variant the interaction system publishes an op
 * under. Which child a click came from does not matter, though: the patch is identified by the base
 * loc it belongs to and the action is chosen from the player's stored state, so a click that raced
 * a growth cycle still does the right thing.
 */
class FarmingPatchScript
@Inject
constructor(
    private val store: FarmingStore,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {
    private val patchesByLocId: Map<Int, FarmingPatch> =
        FarmingPatches.ALL.associateBy { it.loc.asRSCM(RSCMType.LOC) }

    override fun ScriptContext.startup() {
        for (locId in childLocIds()) {
            val type = ServerCacheManager.getObject(locId) ?: continue
            onOpLoc1(type) { primary(it.loc) }
            onOpLoc2(type) { inspect(it.loc) }
            onOpLocU(type) { useOn(it.loc, it.objType) }
        }
    }

    /**
     * Every loc the four patch families can transform into. Walking the cache transform lists keeps
     * this in step with the data instead of listing several hundred crop locs by hand.
     */
    private fun childLocIds(): Set<Int> {
        val ids = LinkedHashSet<Int>()
        for (patch in FarmingPatches.ALL) {
            val type = ServerCacheManager.getObject(patch.loc.asRSCM(RSCMType.LOC)) ?: continue
            type.transforms?.forEach { if (it > 0) ids += it }
        }
        return ids
    }

    private suspend fun ProtectedAccess.primary(loc: BoundLocInfo) {
        val patch = patchesByLocId[loc.id] ?: return
        val state = store.state(player, patch)
        val crop = state.crop
        when {
            crop == null && state.weeds < PatchKind.WEEDED -> rake(patch)
            crop == null -> mes("This ${patch.kind.label} is empty and ready for a seed.")
            state.dead -> clear(patch, state)
            state.diseased -> cure(patch, state)
            state.stage >= crop.cycles -> harvest(patch, crop)
            else -> mes("The ${crop.displayName} is still growing.")
        }
    }

    private fun ProtectedAccess.inspect(loc: BoundLocInfo) {
        val patch = patchesByLocId[loc.id] ?: return
        val state = store.state(player, patch)
        transmit(patch, state)
        mes(describe(patch, state))
    }

    private fun describe(patch: FarmingPatch, state: PatchState): String {
        val crop = state.crop
        if (crop == null) {
            return when (state.weeds) {
                PatchKind.WEEDED -> "This ${patch.kind.label} is raked and ready for a seed."
                PatchKind.WEEDS_LIGHT -> "This ${patch.kind.label} has a few weeds in it."
                PatchKind.WEEDS_MEDIUM -> "This ${patch.kind.label} is getting weedy."
                else -> "This ${patch.kind.label} is overgrown with weeds."
            }
        }
        val name = crop.displayName
        return when {
            state.dead -> "The $name is dead. You will need to dig it up."
            state.diseased -> "The $name has become diseased. Plant cure would fix it."
            state.stage >= crop.cycles -> "The $name is ready to be harvested."
            else -> {
                val protection = if (state.protectedByFarmer) " The farmer is watching over it." else ""
                "The $name is at growth stage ${state.stage + 1} of ${crop.cycles + 1}.$protection"
            }
        }
    }

    private suspend fun ProtectedAccess.rake(patch: FarmingPatch) {
        if (!playerContainsObj(Farming.RAKE)) {
            mes("You need a rake to clear this patch.")
            return
        }
        while (true) {
            val state = store.state(player, patch)
            if (!state.isEmpty || state.weeds >= PatchKind.WEEDED) {
                break
            }
            anim(Farming.RAKE_ANIM)
            soundSynth(Farming.RAKE_SOUND)
            delay(Farming.ACTION_CYCLE)

            var cleared = false
            store.update(player, patch) {
                if (it.isEmpty && it.weeds < PatchKind.WEEDED) {
                    it.weeds++
                    it.lastTick = epochMinute()
                    cleared = true
                }
            }
            if (!cleared) {
                break
            }
            statAdvance(Farming.STAT, Farming.WEED_XP * xpMods.get(player, Farming.STAT))
            if (inv.hasFreeSpace()) {
                invAdd(inv, Farming.WEEDS)
            }
            transmit(patch, store.state(player, patch))
        }
        resetAnim()
    }

    private suspend fun ProtectedAccess.useOn(loc: BoundLocInfo, used: ItemServerType) {
        val patch = patchesByLocId[loc.id] ?: return
        val obj = RSCM.getReverseMapping(RSCMType.OBJ, used.id)
        val compost = Compost.forObj(obj)
        when {
            compost != null -> applyCompost(patch, compost)
            obj in Farming.WATERING_CANS -> water(patch, obj)
            obj == Farming.PLANT_CURE -> cure(patch, store.state(player, patch))
            obj == Farming.SPADE -> clear(patch, store.state(player, patch))
            obj == Farming.RAKE -> primary(loc)
            else -> plant(patch, obj)
        }
    }

    private suspend fun ProtectedAccess.plant(patch: FarmingPatch, seed: String) {
        val crop = Crops.forSeed(seed) ?: return
        val state = store.state(player, patch)
        if (crop.kind != patch.kind) {
            mes("You cannot plant ${crop.displayName} in ${article(patch.kind.label)}.")
            return
        }
        if (!state.isEmpty) {
            mes("This ${patch.kind.label} already has something growing in it.")
            return
        }
        if (state.weeds < PatchKind.WEEDED) {
            mes("This ${patch.kind.label} needs to be raked first.")
            return
        }
        if (player.farmingLvl < crop.level) {
            mes("You need a Farming level of ${crop.level} to plant ${crop.displayName}.")
            return
        }
        if (!playerContainsObj(Farming.DIBBER)) {
            mes("You need a seed dibber to plant a seed.")
            return
        }
        if (invTotal(inv, seed) < crop.seedsPerPlant) {
            mes("You need ${crop.seedsPerPlant} ${crop.displayName} seeds to plant this patch.")
            return
        }

        anim(Farming.DIBBING_ANIM)
        soundSynth(Farming.DIBBING_SOUND)
        delay(Farming.ACTION_CYCLE)
        resetAnim()

        if (invDel(inv, seed, crop.seedsPerPlant).failure) {
            return
        }
        store.update(player, patch) {
            it.cropKey = crop.key
            it.stage = 0
            it.weeds = PatchKind.WEEDED
            it.lastTick = epochMinute()
            it.diseased = false
            it.dead = false
            it.watered = false
            it.lives = if (crop.singleHarvest) 1 else crop.lives + it.compost.extraLives
        }
        statAdvance(Farming.STAT, crop.plantXp * xpMods.get(player, Farming.STAT))
        transmit(patch, store.state(player, patch))
        mes("You plant ${crop.displayName} in the ${patch.kind.label}.")
    }

    private suspend fun ProtectedAccess.applyCompost(patch: FarmingPatch, compost: Compost) {
        val state = store.state(player, patch)
        val crop = state.crop
        if (state.compost != Compost.NONE) {
            mes("This patch has already been treated.")
            return
        }
        if (crop != null && (state.dead || state.stage >= crop.cycles)) {
            mes("There is no point treating this patch now.")
            return
        }

        anim(Farming.POUR_ANIM)
        soundSynth(Farming.COMPOST_SOUND)
        delay(Farming.ACTION_CYCLE)
        resetAnim()

        if (invReplace(inv, compost.obj, 1, Farming.EMPTY_BUCKET).failure) {
            return
        }
        store.update(player, patch) {
            it.compost = compost
            if (it.crop != null && !it.crop!!.singleHarvest) {
                it.lives += compost.extraLives
            }
        }
        mes("You treat the ${patch.kind.label} with ${compost.label}.")
    }

    private suspend fun ProtectedAccess.water(patch: FarmingPatch, can: String) {
        val state = store.state(player, patch)
        val crop = state.crop
        if (crop == null) {
            mes("There is nothing to water in this patch.")
            return
        }
        if (crop.watered.isEmpty()) {
            mes("${crop.displayName.replaceFirstChar(Char::uppercase)} does not need watering.")
            return
        }
        if (state.dead || state.diseased) {
            mes("Watering will not help this crop now.")
            return
        }
        if (state.stage >= crop.cycles) {
            mes("The ${crop.displayName} is fully grown; it does not need watering.")
            return
        }
        if (state.watered) {
            mes("The ${crop.displayName} has already been watered.")
            return
        }

        anim(Farming.WATERING_ANIM)
        soundSynth(Farming.WATERING_SOUND)
        delay(Farming.ACTION_CYCLE)
        resetAnim()

        if (invReplace(inv, can, 1, Farming.drainedCan(can)).failure) {
            return
        }
        store.update(player, patch) { it.watered = true }
        transmit(patch, store.state(player, patch))
        mes("You water the ${crop.displayName}.")
    }

    private suspend fun ProtectedAccess.cure(patch: FarmingPatch, state: PatchState) {
        val crop = state.crop
        if (crop == null || !state.diseased) {
            mes("There is nothing here that needs curing.")
            return
        }
        if (!playerContainsObj(Farming.PLANT_CURE)) {
            mes("You need plant cure to treat this crop.")
            return
        }

        anim(Farming.CURE_ANIM)
        soundSynth(Farming.CURE_SOUND)
        delay(Farming.ACTION_CYCLE)
        resetAnim()

        if (invDel(inv, Farming.PLANT_CURE, 1).failure) {
            return
        }
        store.update(player, patch) { it.diseased = false }
        transmit(patch, store.state(player, patch))
        mes("You treat the ${crop.displayName} with plant cure.")
    }

    private suspend fun ProtectedAccess.clear(patch: FarmingPatch, state: PatchState) {
        if (state.isEmpty) {
            mes("There is nothing here to dig up.")
            return
        }
        if (!playerContainsObj(Farming.SPADE)) {
            mes("You need a spade to dig up this patch.")
            return
        }

        anim(Farming.DIG_ANIM)
        delay(Farming.ACTION_CYCLE)
        resetAnim()

        store.update(player, patch) {
            it.clearToWeeded()
            it.lastTick = epochMinute()
        }
        transmit(patch, store.state(player, patch))
        mes("You clear the ${patch.kind.label}.")
    }

    private suspend fun ProtectedAccess.harvest(patch: FarmingPatch, crop: Crop) {
        val anim = if (patch.kind == PatchKind.HOPS) Farming.PICK_MID_ANIM else Farming.PICK_LOW_ANIM
        while (true) {
            val state = store.state(player, patch)
            if (state.crop != crop || state.stage < crop.cycles || state.dead) {
                break
            }
            if (!inv.hasFreeSpace()) {
                mes("Your inventory is too full to hold any more ${crop.displayName}.")
                soundSynth("synth.pillory_wrong")
                break
            }

            anim(anim)
            soundSynth(Farming.PICK_SOUND)
            delay(Farming.ACTION_CYCLE)

            if (invAdd(inv, crop.produce).failure) {
                break
            }
            statAdvance(Farming.STAT, crop.harvestXp * xpMods.get(player, Farming.STAT))
            spam("You pick some ${crop.displayName}.")

            val keepsLife =
                !crop.singleHarvest &&
                    statRandom(Farming.STAT, saveLow(crop), saveHigh(crop), invisibleLvls)
            if (keepsLife) {
                continue
            }

            var emptied = false
            store.update(player, patch) {
                it.lives--
                if (it.lives <= 0) {
                    it.clearToWeeded()
                    it.lastTick = epochMinute()
                    emptied = true
                }
            }
            transmit(patch, store.state(player, patch))
            if (emptied) {
                break
            }
        }
        resetAnim()
    }

    /** Magic secateurs raise the chance to save a harvest life by a tenth. */
    private fun ProtectedAccess.saveLow(crop: Crop): Int = boosted(crop.saveLifeLow)

    private fun ProtectedAccess.saveHigh(crop: Crop): Int = boosted(crop.saveLifeHigh)

    private fun ProtectedAccess.boosted(chance: Int): Int =
        if (playerContainsObj(Farming.MAGIC_SECATEURS)) chance * 110 / 100 else chance

    private fun ProtectedAccess.transmit(patch: FarmingPatch, state: PatchState) {
        VarPlayerIntMapSetter.set(player, patch.varbit, state.varbitValue())
    }

    private fun article(noun: String): String =
        if (noun.first() in "aeiou") "an $noun" else "a $noun"
}
