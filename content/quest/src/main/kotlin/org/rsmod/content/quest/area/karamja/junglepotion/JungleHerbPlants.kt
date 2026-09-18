package org.rsmod.content.quest.area.karamja.junglepotion

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.POTHOLE_EXIT
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.POTHOLE_HANDHOLDS
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.POTHOLE_LANDING
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.POTHOLE_ROCKS
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The plants the Jungle Potion herbs are searched from, and the hidden way into the Pothole
 * Dungeon where the Rogue's Purse grows.
 *
 * The palm, scorched earth and moss rock give up their herb on the first search. The marshy vines
 * and the fungus covered walls are searched over and over, each attempt a Herblore roll, until a
 * herb turns up. A picked plant shows its bare form to everyone for a minute.
 */
class JungleHerbPlants
@Inject
constructor(private val junglePotion: JunglePotionQuest, private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (herb in JungleHerb.entries) {
            onOpLoc2(herb.plant) { search(it.loc, herb) }
        }
        onOpLoc2(POTHOLE_ROCKS) { searchPotholeRocks() }
        onOpLoc1(POTHOLE_HANDHOLDS) { climbOut() }
    }

    private suspend fun ProtectedAccess.search(plant: BoundLocInfo, herb: JungleHerb) {
        if (!junglePotion.canGather(player, herb)) {
            mes(herb.nothingFound)
            return
        }
        if (herb.slowSearch) {
            searchUntilFound(plant, herb)
        } else {
            mes("You search the ${herb.searchNoun}...")
            faceSquare(plant.coords)
            anim(PICK_SEQ)
            delay(1)
            pick(plant, herb)
        }
    }

    /**
     * One attempt per [SEARCH_CYCLE] ticks, driven by re-queueing the op like the gathering
     * skills do, so walking away stops the search without a lingering delay.
     */
    private suspend fun ProtectedAccess.searchUntilFound(plant: BoundLocInfo, herb: JungleHerb) {
        val wall = herb == JungleHerb.RoguesPurse
        if (actionDelay < mapClock) {
            mes("You search the ${herb.searchNoun}...")
            faceSquare(plant.coords)
            anim(if (wall) WALL_START_SEQ else VINE_SEQ)
            if (!wall) {
                soundSynth(VINE_SOUND)
            }
            actionDelay = mapClock + SEARCH_CYCLE
            opLoc2(plant)
            return
        }
        if (actionDelay > mapClock) {
            opLoc2(plant)
            return
        }
        if (!statRandom(HERBLORE, SEARCH_LOW, SEARCH_HIGH, invisibleBoost = 0)) {
            anim(if (wall) WALL_LOOP_SEQ else VINE_SEQ)
            if (!wall) {
                soundSynth(VINE_SOUND)
            }
            actionDelay = mapClock + SEARCH_CYCLE
            opLoc2(plant)
            return
        }
        anim(if (wall) WALL_END_SEQ else VINE_END_SEQ)
        pick(plant, herb)
    }

    private suspend fun ProtectedAccess.pick(plant: BoundLocInfo, herb: JungleHerb) {
        if (inv.isFull()) {
            objbox(herb.grimy, "You find a herb, but you have no place to store it in your inventory.")
            return
        }
        junglePotion.onHerbPicked(this, herb)
        invAdd(inv, herb.grimy)
        objbox(herb.grimy, "You find a herb.")
        locRepo.change(plant, herb.pickedPlant, REGROW_TICKS)
    }

    private suspend fun ProtectedAccess.searchPotholeRocks() {
        mesbox("You search the rocks... You find an entrance into some caves.")
        var enter = false
        startDialogue {
            enter =
                choice2(
                    "Yes, I'll enter the cave.", true,
                    "No thanks, I'll give it a miss.", false,
                    title = "Would you like to enter the caves?",
                )
        }
        if (!enter) {
            mesbox("You decide to stay where you are!")
            return
        }
        mesbox("You decide to enter the caves. You climb down several steep rock faces into the cavern below.")
        anim(CLIMB_DOWN_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(CLIMB_TICKS)
        telejump(POTHOLE_LANDING, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbOut() {
        mesbox("You attempt to climb the rocks back out.")
        anim(CLIMB_UP_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(CLIMB_TICKS)
        telejump(POTHOLE_EXIT, TeleportType.Exempt)
    }

    private companion object {
        const val HERBLORE = "stat.herblore"
        const val SEARCH_LOW = 9
        const val SEARCH_HIGH = 219
        const val SEARCH_CYCLE = 4
        const val REGROW_TICKS = 100
        const val CLIMB_TICKS = 2

        const val PICK_SEQ = "seq.human_pickupfloor"
        const val VINE_SEQ = "seq.human_openingchest_mid"
        const val VINE_END_SEQ = "seq.human_openingchest_end"
        const val WALL_START_SEQ = "seq.human_pick_wall_start"
        const val WALL_LOOP_SEQ = "seq.human_pick_wall_middle"
        const val WALL_END_SEQ = "seq.human_pick_wall_end"
        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val CLIMB_UP_SEQ = "seq.human_climbing"

        const val VINE_SOUND = "synth.search_vine"
        const val CLIMB_SOUND = "synth.climb_wall"
    }
}
