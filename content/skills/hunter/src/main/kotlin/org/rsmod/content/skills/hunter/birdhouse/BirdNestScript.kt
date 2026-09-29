package org.rsmod.content.skills.hunter.birdhouse

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Searching bird nests (seed, ring and egg nests) and opening clue nests, using the wiki tables. */
class BirdNestScript @Inject constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        for (nest in SEED_NESTS) {
            onOpHeld1(nest) { search(nest, it.slot, roll(SEED_TABLE), "some seeds") }
        }
        onOpHeld1(RING_NEST) { search(RING_NEST, it.slot, roll(RING_TABLE), "a ring") }
        for ((nest, egg) in EGG_NESTS) {
            onOpHeld1(nest) { search(nest, it.slot, egg, "a bird's egg") }
        }
        for ((nest, clue) in CLUE_NESTS) {
            onOpHeld1(nest) { search(nest, it.slot, clue, "a clue scroll") }
        }
    }

    private fun ProtectedAccess.search(nest: String, slot: Int, reward: String, what: String) {
        if (reward !in player.inv && inv.freeSpace() == 0) {
            mes("You need a free inventory space to search the nest.")
            return
        }
        if (invDel(inv, nest, slot = slot).failure) {
            return
        }
        invAdd(inv, EMPTY_NEST)
        invAdd(inv, reward)
        mes("You take $what out of the bird's nest.")
    }

    private fun roll(table: List<Pair<String, Int>>): String {
        var roll = random.of(table.sumOf { it.second })
        for ((obj, weight) in table) {
            roll -= weight
            if (roll < 0) {
                return obj
            }
        }
        return table.last().first
    }

    private companion object {
        const val EMPTY_NEST = "obj.bird_nest_empty"
        const val RING_NEST = "obj.bird_nest_ring"

        val SEED_NESTS =
            listOf(
                "obj.bird_nest_seeds",
                "obj.bird_nest_seeds_jan2019",
                "obj.bird_nest_cheapseeds",
                "obj.bird_nest_decentseeds",
                "obj.bird_nest_decentseeds_jan2019",
            )

        val EGG_NESTS =
            listOf(
                "obj.bird_nest_egg_red" to "obj.bird_egg_red",
                "obj.bird_nest_egg_green" to "obj.bird_egg_green",
                "obj.bird_nest_egg_blue" to "obj.bird_egg_blue",
            )

        val CLUE_NESTS =
            listOf(
                "obj.wc_clue_nest_beginner" to "obj.trail_clue_beginner",
                "obj.wc_clue_nest_easy" to "obj.trail_clue_easy_simple001",
                "obj.wc_clue_nest_medium" to "obj.trail_medium_emote_exp1",
                "obj.wc_clue_nest_hard" to "obj.trail_clue_hard_map001",
                "obj.wc_clue_nest_elite" to "obj.trail_elite_emote_exp1",
            )

        val RING_TABLE =
            listOf(
                "obj.gold_ring" to 35,
                "obj.sapphire_ring" to 40,
                "obj.emerald_ring" to 15,
                "obj.ruby_ring" to 9,
                "obj.diamond_ring" to 1,
            )

        val SEED_TABLE =
            listOf(
                "obj.acorn" to 214,
                "obj.apple_tree_seed" to 170,
                "obj.banana_tree_seed" to 108,
                "obj.orange_tree_seed" to 85,
                "obj.willow_seed" to 135,
                "obj.teak_seed" to 4,
                "obj.curry_tree_seed" to 68,
                "obj.maple_seed" to 54,
                "obj.pineapple_tree_seed" to 42,
                "obj.mahogany_seed" to 4,
                "obj.papaya_tree_seed" to 34,
                "obj.palm_tree_seed" to 22,
                "obj.calquat_tree_seed" to 17,
                "obj.yew_seed" to 27,
                "obj.magic_tree_seed" to 5,
                "obj.spirit_tree_seed" to 11,
                "obj.celastrus_tree_seed" to 3,
                "obj.redwood_tree_seed" to 2,
                "obj.dragonfruit_tree_seed" to 6,
            )
    }
}
