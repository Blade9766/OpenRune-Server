package org.rsmod.content.skills.farming.state

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.random.GameRandom
import org.rsmod.api.utils.time.epochMinute
import org.rsmod.content.skills.farming.data.FarmingPatch
import org.rsmod.content.skills.farming.data.PatchKind
import org.rsmod.game.entity.Player

/**
 * Per-player patch state, kept in one persistent attribute and brought up to date on demand.
 *
 * Reading a patch is what makes it grow: [state] replays every growth cycle that has elapsed since
 * the patch was last touched, which is what lets crops finish - and die - while their owner is
 * offline.
 */
@Singleton
class FarmingStore @Inject constructor(private val random: GameRandom) {
    fun state(player: Player, patch: FarmingPatch): PatchState {
        val states = load(player)
        val state = states.getOrPut(patch.loc) { PatchState(lastTick = epochMinute()) }
        if (advance(state)) {
            save(player, states)
        }
        return state
    }

    fun update(player: Player, patch: FarmingPatch, block: (PatchState) -> Unit) {
        val states = load(player)
        val state = states.getOrPut(patch.loc) { PatchState(lastTick = epochMinute()) }
        advance(state)
        block(state)
        save(player, states)
    }

    /** Brings every patch up to date at once, for the login and scene refreshes. */
    fun refreshAll(player: Player): Map<String, PatchState> {
        val states = load(player)
        var changed = false
        for (state in states.values) {
            changed = advance(state) || changed
        }
        if (changed) {
            save(player, states)
        }
        return states
    }

    private fun advance(state: PatchState): Boolean {
        val now = epochMinute()
        if (state.lastTick > now) {
            // The clock moved backwards (a restore, or a host whose time was corrected). Re-anchor
            // rather than letting the patch sit frozen until real time catches up.
            state.lastTick = now
            return true
        }
        val crop = state.crop ?: return advanceWeeds(state, now)
        if (state.dead || (state.stage >= crop.cycles && !state.diseased)) {
            return anchor(state, now)
        }

        val perCycle = crop.minutesPerCycle
        var changed = false
        while (now - state.lastTick >= perCycle) {
            state.lastTick += perCycle
            changed = true
            if (state.diseased) {
                state.diseased = false
                state.dead = true
                break
            }
            if (state.stage >= crop.cycles) {
                break
            }
            val wasWatered = state.watered
            state.stage++
            state.watered = false
            if (!wasWatered && rollDisease(state)) {
                state.diseased = true
            }
        }
        anchor(state, now)
        return changed
    }

    private fun advanceWeeds(state: PatchState, now: Int): Boolean {
        if (state.weeds <= PatchKind.WEEDS_HEAVY) {
            state.lastTick = now
            return false
        }
        val elapsed = now - state.lastTick
        if (elapsed < WEED_REGROWTH_MINUTES) {
            return false
        }
        val steps = elapsed / WEED_REGROWTH_MINUTES
        state.lastTick += steps * WEED_REGROWTH_MINUTES
        state.weeds = (state.weeds - steps).coerceAtLeast(PatchKind.WEEDS_HEAVY)
        return true
    }

    private fun anchor(state: PatchState, now: Int): Boolean {
        val crop = state.crop
        if (crop != null && !state.dead && !state.diseased && state.stage >= crop.cycles) {
            state.lastTick = now
        }
        return false
    }

    private fun rollDisease(state: PatchState): Boolean {
        val crop = state.crop ?: return false
        if (!crop.canCatchDisease(state.stage) || state.protectedByFarmer) {
            return false
        }
        val chance = crop.diseaseChance / state.compost.diseaseDivisor
        return chance > 0 && random.of(0, DISEASE_DENOMINATOR - 1) < chance
    }

    private fun load(player: Player): MutableMap<String, PatchState> {
        val encoded = player.attr[PATCHES] ?: return LinkedHashMap()
        val states = LinkedHashMap<String, PatchState>()
        for (entry in encoded.split(';')) {
            if (entry.isEmpty()) {
                continue
            }
            val split = entry.indexOf('=')
            if (split <= 0) {
                continue
            }
            val state = PatchState.decode(entry.substring(split + 1)) ?: continue
            states[entry.substring(0, split)] = state
        }
        return states
    }

    private fun save(player: Player, states: Map<String, PatchState>) {
        player.attr[PATCHES] = states.entries.joinToString(";") { "${it.key}=${it.value.encode()}" }
    }

    private companion object {
        val PATCHES = AttributeKey<String>(persistenceKey = "farming_patches")

        const val DISEASE_DENOMINATOR = 128

        /** A raked patch grows one weed stage back roughly every five minutes. */
        const val WEED_REGROWTH_MINUTES = 5
    }
}
