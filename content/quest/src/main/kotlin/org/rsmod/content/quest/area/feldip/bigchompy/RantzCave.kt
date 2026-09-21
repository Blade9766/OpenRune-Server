package org.rsmod.content.quest.area.feldip.bigchompy

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_EMPTY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_FULL
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_ONE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_TWO
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CAVE_ARRIVAL
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CAVE_ENTRANCE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CAVE_EXIT_ARRIVAL
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CAVE_EXIT_LEFT
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CAVE_EXIT_RIGHT
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_CHEST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_CHEST_OPEN
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_ASKED_ABOUT_TOADS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_OPENED_CHEST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TABLE_SEQ
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rantz's cave: the mouth in the hillside north of him, the two exits back out, and the chest his
 * children are no longer allowed near.
 *
 * The chest's "lock" is a boulder. Lifting it is a strength check the player fails about a third of
 * the time, and each failure costs a level; a success leaves the chest open for a while before the
 * rock settles back on it.
 */
class RantzCave
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val locRepo: LocRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CAVE_ENTRANCE) { enterCave() }
        onOpLoc1(CAVE_EXIT_LEFT) { leaveCave(lit = false) }
        onOpLoc1(CAVE_EXIT_RIGHT) { leaveCave(lit = true) }
        onOpLoc1(OGRE_CHEST) { liftRock(it.loc) }
        onOpLoc1(OGRE_CHEST_OPEN) { searchChest() }
    }

    private suspend fun ProtectedAccess.enterCave() {
        arriveDelay()
        mes("You walk through the cave entrance into a dimly lit cave.")
        delay(1)
        telejump(CAVE_ARRIVAL)
    }

    private suspend fun ProtectedAccess.leaveCave(lit: Boolean) {
        arriveDelay()
        if (lit) {
            mes("You walk back out of the darkness of the cave into daylight.")
        }
        delay(1)
        telejump(CAVE_EXIT_ARRIVAL)
    }

    private suspend fun ProtectedAccess.liftRock(chest: BoundLocInfo) {
        arriveDelay()
        if (quest.stage(player) < STAGE_ASKED_ABOUT_TOADS) {
            mes("Perhaps you'd better ask permission before opening this.")
            return
        }
        say("Humph!")
        anim(TABLE_SEQ)
        if (random.of(maxExclusive = LIFT_ODDS) == 0) {
            mes("You strain to lift the huge rock off the chest...")
            delay(1)
            mes("... but it's just too heavy for you.")
            mes("The experience has left you feeling temporarily weakened.")
            say("Arrgghhhhh!")
            statSub("stat.strength", constant = 1, percent = 0)
            return
        }
        mes("You manage to lift the huge rock off the chest.")
        say("I guess this is what an ogre would call a locked chest.")
        quest.advanceTo(this, STAGE_OPENED_CHEST)
        locRepo.change(chest, OGRE_CHEST_OPEN, OPEN_CYCLES)
    }

    private suspend fun ProtectedAccess.searchChest() {
        arriveDelay()
        anim(TABLE_SEQ)
        if (BELLOWS.any { invTotal(inv, it) > 0 || invTotal(bank, it) > 0 }) {
            mes("You search but find nothing in the ogre chest.")
            return
        }
        if (inv.freeSpace() == 0) {
            mes("You find some ogre bellows in the chest but do not have the space to take them.")
            return
        }
        invAdd(inv, BELLOWS_EMPTY)
        objbox(BELLOWS_EMPTY, OBJBOX_ZOOM, "You search the chest and find a pair of ogre bellows.")
    }

    private companion object {
        val BELLOWS = listOf(BELLOWS_EMPTY, BELLOWS_ONE, BELLOWS_TWO, BELLOWS_FULL)

        /** One lift in this many fails and costs a strength level. */
        const val LIFT_ODDS = 3

        /** How long the rock stays off before it settles back over the lid. */
        const val OPEN_CYCLES = 50

        const val OBJBOX_ZOOM = 250
    }
}
