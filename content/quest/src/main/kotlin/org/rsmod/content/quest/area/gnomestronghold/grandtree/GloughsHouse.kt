package org.rsmod.content.quest.area.gnomestronghold.grandtree

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GLOUGHS_KEY
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.INVASION_PLANS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_CHARLIE_QUESTIONED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KEY_HINTED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_TRAPDOOR_OPEN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TWIG_O
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TWIG_T
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TWIG_U
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TWIG_Z
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.WATCHTOWER_AGILITY
import org.rsmod.content.quest.manager.QuestAttribute
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Glough's tree house south of the Grand Tree: the cupboard with his journal, the chest his key
 * opens, the tree up to his watchtower, the four pillars that spell the old-tongue word for
 * "open", and the trapdoors. The ground-floor trapdoor of the Grand Tree shares the watchtower
 * trapdoor's closed type, so the two are told apart by where they stand.
 */
class GloughsHouse
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CUPBOARD_CLOSED) { openCupboard(it.loc) }
        onOpLoc2(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc3(CUPBOARD_OPEN) { closeCupboard(it.loc) }
        onOpLoc1(CHEST_CLOSED) { openChest(it.loc) }
        onOpLoc1(CLIMB_TREE) { climbTree() }
        onOpLoc1(DOWN_TREE) { climbDownTree() }
        onOpLoc1(TRAPDOOR_CLOSED) { openTrapdoor(it.loc) }

        for ((pillar, attribute) in PILLARS) {
            for ((twig, letter) in TWIG_LETTERS) {
                onOpLocU(pillar, twig) { placeTwig(attribute, twig, letter) }
            }
            onOpLocU(pillar) { mes("You cannot put that on the pillar.") }
        }
    }

    private fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        soundSynth(CUPBOARD_OPEN_SOUND)
        locRepo.del(cupboard, OPEN_TICKS)
        locRepo.add(cupboard.coords, CUPBOARD_OPEN, OPEN_TICKS, cupboard.angle, cupboard.shape)
    }

    private fun ProtectedAccess.closeCupboard(cupboard: BoundLocInfo) {
        soundSynth(CUPBOARD_CLOSE_SOUND)
        locRepo.del(cupboard, OPEN_TICKS)
        locRepo.add(cupboard.coords, CUPBOARD_CLOSED, OPEN_TICKS, cupboard.angle, cupboard.shape)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        val stage = grandTree.stage(player)
        if (stage < STAGE_CHARLIE_QUESTIONED || player.inv.contains(JOURNAL)) {
            mesbox("You search the cupboard but find nothing of interest.")
            return
        }
        if (grandTree.foundJournal.get(player) && stage > STAGE_CHARLIE_QUESTIONED) {
            mesbox("You search the cupboard but find nothing of interest.")
            return
        }
        anim(GrandTree.SEARCH_SEQ)
        invAddOrDrop(objRepo, JOURNAL)
        grandTree.foundJournal.set(player, true)
        objbox(JOURNAL, "You've found Glough's Journal!")
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        if (grandTree.stage(player) < STAGE_KEY_HINTED || !player.inv.contains(GLOUGHS_KEY)) {
            mesbox("The chest is locked.")
            return
        }
        soundSynth(CHEST_OPEN_SOUND)
        locRepo.del(chest, CHEST_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_OPEN, CHEST_OPEN_TICKS, chest.angle, chest.shape)
        if (grandTree.plansFound.get(player) || player.inv.contains(INVASION_PLANS)) {
            mesbox("The chest is empty.")
            return
        }
        invAddOrDrop(objRepo, INVASION_PLANS)
        grandTree.plansFound.set(player, true)
        objbox(INVASION_PLANS, "You have found a scroll!")
    }

    private suspend fun ProtectedAccess.climbTree() {
        if (statBase(AGILITY) < WATCHTOWER_AGILITY) {
            mesbox("You need an Agility level of $WATCHTOWER_AGILITY to climb up to the watchtower.")
            return
        }
        anim(GrandTree.LADDER_SEQ)
        delay(1)
        telejump(landNear(GrandTree.TOWER_TREE_TOP))
    }

    private suspend fun ProtectedAccess.climbDownTree() {
        anim(GrandTree.LADDER_SEQ)
        delay(1)
        telejump(landNear(GrandTree.TOWER_TREE_BOTTOM))
    }

    /** The watchtower trapdoor is a multiloc that only opens itself once the pillars are right. */
    private suspend fun ProtectedAccess.openTrapdoor(trapdoor: BoundLocInfo) {
        if (trapdoor.coords == GrandTree.TOWER_TRAPDOOR) {
            mesbox("The trapdoor is shut fast. There must be some mechanism that opens it.")
            return
        }
        anim(GrandTree.LADDER_SEQ)
        soundSynth(TRAPDOOR_OPEN_SOUND)
        delay(1)
        telejump(landNear(GrandTree.TUNNEL_LANDING))
    }

    private suspend fun ProtectedAccess.placeTwig(pillar: QuestAttribute<String>, twig: String, letter: String) {
        if (grandTree.stage(player) >= STAGE_TRAPDOOR_OPEN) {
            mes("The trapdoor is already open.")
            return
        }
        if (pillar.get(player).isNotEmpty()) {
            mes("There are already some twigs lashed to that pillar.")
            return
        }
        if (invDel(inv, twig).failure) {
            return
        }
        pillar.set(player, letter)
        soundSynth(TWIGS_SOUND)
        mes("You lash the twigs to the pillar.")
        val word = PILLARS.joinToString("") { (_, attribute) -> attribute.get(player) }
        if (word.length < PILLARS.size) {
            return
        }
        if (word != OPEN_WORD) {
            mesbox("Nothing happens. The twigs fall from the pillars and snap. That can't have been the right word.")
            grandTree.clearPillars(player)
            return
        }
        soundSynth(MACHINERY_SOUND)
        grandTree.advanceTo(this, STAGE_TRAPDOOR_OPEN)
        mesbox("With a grinding of machinery, a trapdoor snaps open!")
    }

    private val PILLARS: List<Pair<String, QuestAttribute<String>>>
        get() =
            listOf(
                PILLAR_T to grandTree.pillarT,
                PILLAR_U to grandTree.pillarU,
                PILLAR_Z to grandTree.pillarZ,
                PILLAR_O to grandTree.pillarO,
            )

    private companion object {
        const val CUPBOARD_CLOSED = "loc.grandtree_cupboardclosed"
        const val CUPBOARD_OPEN = "loc.grandtree_cupboardopen"
        const val CHEST_CLOSED = "loc.grandtree_chestclosed"
        const val CHEST_OPEN = "loc.grandtree_chestopen"
        const val CLIMB_TREE = "loc.grandtree_climbtree"
        const val DOWN_TREE = "loc.grandtree_downtree"
        const val TRAPDOOR_CLOSED = "loc.grandtree_trapdoorunder"
        const val PILLAR_T = "loc.grandtree_pillart"
        const val PILLAR_U = "loc.grandtree_pillaru"
        const val PILLAR_Z = "loc.grandtree_pillarz"
        const val PILLAR_O = "loc.grandtree_pillaro"
        const val AGILITY = "stat.agility"

        /** "Tuzo", the old gnome tongue for "open", spelled west to east across the pillars. */
        const val OPEN_WORD = "TUZO"
        val TWIG_LETTERS = listOf(TWIG_T to "T", TWIG_U to "U", TWIG_Z to "Z", TWIG_O to "O")

        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
        const val CHEST_OPEN_SOUND = "synth.chest_open"
        const val TRAPDOOR_OPEN_SOUND = "synth.trapdoor_open"
        const val TWIGS_SOUND = "synth.twigs_snapping"
        const val MACHINERY_SOUND = "synth.grandtree_machinery"

        const val OPEN_TICKS = 100
        const val CHEST_OPEN_TICKS = 10
    }
}
