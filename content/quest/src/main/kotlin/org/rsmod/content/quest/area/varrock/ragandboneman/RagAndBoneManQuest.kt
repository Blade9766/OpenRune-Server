package org.rsmod.content.quest.area.varrock.ragandboneman

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestItemDrops
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rag and Bone Man I.
 *
 * The stage is the cache varp `varp.rag_quest`, endstate 4 from `dbrow.quest_ragandboneman1`:
 * - [STAGE_STARTED]: the Odd Old Man has handed over his wish-list and the vinegar instructions.
 * - [STAGE_VINEGAR]: Fortunato has heard about the Odd Old Man and sells the player vinegar.
 * - [STAGE_BOILED]: the player has boiled at least one specimen clean.
 * - [STAGE_COMPLETE]: all eight polished specimens are in the collection.
 *
 * No specimen keeps a var of its own: each is tracked by which of its three objs the player holds
 * (inventory or bank), or by being in the pot-boiler. A kill only drops a specimen the player has
 * none of, counting their own loot still on the ground nearby, so two quick kills can't both drop. The boiler is the player's own multiloc
 * view of `loc.rag_multi_potboiler`, driven by the cache varbits `varbit.rag_boiler` (what the
 * boiler shows) and `varbit.rag_potboiler` (which [Specimen] is in it), plus a server-only count
 * of the boiling steps left, all permanent, so a boil survives logging out.
 */
@Singleton
class RagAndBoneManQuest @Inject constructor(private val objs: ObjRegistry) :
    QuestScript(
        QUEST_KEY,
        "varp.rag_quest",
        rewards {
            xp("stat.cooking", REWARD_XP)
            xp("stat.prayer", REWARD_XP)
        },
        ItemRewardDisplay(Specimen.GOBLIN.polished, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
    ) {
    override fun ScriptContext.init() {
        for (specimen in Specimen.entries) {
            QuestItemDrops.register(specimen.raw) { player -> isNeeded(player, specimen) }
        }
        quest.onVarSync(::normalise)
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Odd Old Man</col>, who camps among the bones north of the " +
            "<col=800000>Digsite</col>, east of <col=800000>Varrock</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            line(
                "The Odd Old Man wants eight unusual specimens for his collection. Each must be " +
                    "cleaned before he will take it: soaked in a pot of vinegar, then boiled in " +
                    "the pot-boiler beside his camp.",
            )
            line("<col=000080>His wish-list:</col>")
            for (specimen in Specimen.entries) {
                val state = specimenState(p, specimen, access.bank)
                line("<col=${state.journalColour}>${specimen.label}</col>: ${state.label}.")
                if (state == SpecimenState.MISSING) {
                    line("  ${specimen.habitat}")
                }
            }
            if (stage >= STAGE_VINEGAR) {
                strike("Fortunato in Draynor Village sells me vinegar, a coin a jug.")
            } else {
                line(
                    "Fortunato, the wine merchant in Draynor Village market, might sell me " +
                        "vinegar if I mention the Odd Old Man.",
                )
            }
            val method =
                "To clean a specimen I pour a jug of vinegar into an empty pot and add the " +
                    "specimen. At the pot-boiler I put an ordinary log beneath it, set the pot on " +
                    "top, light the log with a tinderbox and wait for it to boil dry."
            if (stage >= STAGE_BOILED) strike(method) else line(method)
            boilerLine(p)?.let { line(it) }
            if (unfinished(p).isEmpty()) {
                line("I have all eight polished specimens. I should take them to the Odd Old Man.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I tracked down eight curious specimens for the Odd Old Man, from a giant rat in " +
                    "Lumbridge Swamp to a giant bat under the Karamja volcano.",
            )
            line(
                "Each was soaked in vinegar from Fortunato of Draynor and boiled clean in the " +
                    "Odd Old Man's pot-boiler. He was delighted, though the sack on his back " +
                    "seemed rather less so.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = quest.isQuestInProgress(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    fun isVinegarUnlocked(player: Player): Boolean = stage(player) >= STAGE_VINEGAR

    /**
     * How far [specimen] has got for [player]: the furthest of whatever copies they hold, in the
     * inventory or [bank], or have in the pot-boiler.
     */
    fun specimenState(player: Player, specimen: Specimen, bank: Inventory? = bankOf(player)): SpecimenState {
        if (isComplete(player)) {
            return SpecimenState.DELIVERED
        }
        fun held(obj: String): Boolean = obj in player.inv || bank?.contains(obj) == true
        return when {
            held(specimen.polished) -> SpecimenState.POLISHED
            boilerSpecimen(player) == specimen && boilerState(player) == BOILER_BOILED -> SpecimenState.BOILED
            boilerSpecimen(player) == specimen -> SpecimenState.BOILING
            held(specimen.inVinegar) -> SpecimenState.IN_VINEGAR
            held(specimen.raw) -> SpecimenState.RAW
            else -> SpecimenState.MISSING
        }
    }

    /** Whether a kill should drop [specimen]: only during the quest, and only if none is held. */
    fun isNeeded(player: Player, specimen: Specimen): Boolean =
        isStarted(player) &&
            specimenState(player, specimen) == SpecimenState.MISSING &&
            !isOnGroundNearby(player, specimen)

    /** Whether the player's own drop of [specimen], in any form, is lying near them. */
    fun isOnGroundNearby(player: Player, specimen: Specimen): Boolean {
        val observer = player.observerUUID ?: return false
        val types = setOf(specimen.raw, specimen.inVinegar, specimen.polished).map { it.asRSCM() }.toSet()
        val centre = ZoneKey.from(player.coords)
        for (dx in -GROUND_ZONE_RADIUS..GROUND_ZONE_RADIUS) {
            for (dz in -GROUND_ZONE_RADIUS..GROUND_ZONE_RADIUS) {
                val found =
                    objs.findAll(centre.translate(dx, dz)).any { obj ->
                        obj.type in types && (obj.receiverId == observer || obj.ownerId == observer)
                    }
                if (found) return true
            }
        }
        return false
    }

    /** Specimens the Odd Old Man would still refuse: not polished and in the inventory. */
    fun unfinished(player: Player): List<Specimen> =
        Specimen.entries.filter { it.polished !in player.inv }

    fun boilerState(player: Player): Int = player.boiler

    fun boilerSpecimen(player: Player): Specimen? = Specimen.byId(player.boilerContents)

    fun boilStepsLeft(player: Player): Int = player.boilSteps

    fun setBoiler(player: Player, state: Int, specimen: Specimen? = boilerSpecimen(player)) {
        player.boilerContents = specimen?.id ?: 0
        player.boiler = state
    }

    fun setBoilSteps(player: Player, steps: Int) {
        player.boilSteps = steps
    }

    fun clearBoiler(player: Player) {
        player.boilSteps = 0
        player.boilerContents = 0
        player.boiler = BOILER_EMPTY
    }

    fun boilerLine(player: Player): String? {
        val specimen = boilerSpecimen(player)?.label?.lowercase()
        return when (boilerState(player)) {
            BOILER_LOGS -> "There is a log under the pot-boiler, ready for a pot."
            BOILER_LOADED -> "My $specimen is in the pot-boiler. I need to light the log beneath it."
            BOILER_BOILING -> "My $specimen is boiling in the pot-boiler."
            BOILER_BOILED -> "My $specimen has finished boiling. I should take it out of the pot-boiler."
            else -> null
        }
    }

    fun hint(player: Player): String {
        val stage = stage(player)
        if (stage == 0) {
            return "Talk to the Odd Old Man, north of the Digsite and east of Varrock."
        }
        if (stage >= STAGE_COMPLETE) {
            return "You have completed this quest."
        }
        val states = Specimen.entries.associateWith { specimenState(player, it) }
        val boiling = boilerSpecimen(player)
        return when {
            boilerState(player) == BOILER_BOILED ->
                "Your ${boiling?.label?.lowercase()} has boiled clean. Remove it from the pot-boiler."
            boilerState(player) == BOILER_BOILING -> "Wait for the pot-boiler to finish boiling."
            boilerState(player) == BOILER_LOADED -> "Light the log under the pot-boiler with a tinderbox."
            unfinished(player).isEmpty() -> "Take all eight polished specimens to the Odd Old Man."
            states.values.any { it == SpecimenState.IN_VINEGAR } && boilerState(player) == BOILER_EMPTY ->
                "Put an ordinary log under the pot-boiler beside the Odd Old Man."
            states.values.any { it == SpecimenState.IN_VINEGAR } ->
                "Use a specimen in vinegar on the pot-boiler."
            states.values.any { it == SpecimenState.RAW } && !isVinegarUnlocked(player) ->
                "Ask Fortunato in Draynor Village market about vinegar for the Odd Old Man."
            states.values.any { it == SpecimenState.RAW } && VINEGAR !in player.inv && POT_OF_VINEGAR !in player.inv ->
                "You're out of vinegar. Fortunato in Draynor Village market sells it at a coin a jug."
            states.values.any { it == SpecimenState.RAW } ->
                "Pour vinegar into an empty pot, then add a raw specimen to it."
            SpecimenState.MISSING !in states.values ->
                "Some of your polished specimens are in the bank. Bring all eight to the Odd Old Man."
            else -> {
                val next = Specimen.entries.first { states[it] == SpecimenState.MISSING }
                "Find ${next.source} for its ${next.label.lowercase()}. ${next.habitat}"
            }
        }
    }

    private fun bankOf(player: Player): Inventory? = player.invMap["inv.bank"]

    private fun normalise(player: Player) {
        if (stage(player) == 0) {
            clearBoiler(player)
        }
    }

    companion object {
        const val QUEST_KEY = "quest_ragandboneman1"

        const val STAGE_STARTED = 1
        const val STAGE_VINEGAR = 2
        const val STAGE_BOILED = 3
        const val STAGE_COMPLETE = 4

        const val REWARD_XP = 500.0

        /** Zones either side of the player searched for their own dropped specimens. */
        const val GROUND_ZONE_RADIUS = 4

        /** `varbit.rag_boiler` values, which pick the pot-boiler's multiloc variant. */
        const val BOILER_EMPTY = 0
        const val BOILER_LOGS = 1
        const val BOILER_LOADED = 2
        const val BOILER_BOILING = 3
        const val BOILER_BOILED = 4

        const val ODD_OLD_MAN = "npc.rag_odd_old_man"
        const val FORTUNATO = "npc.rag_wine_merchant"

        const val VINEGAR = "obj.rag_vinegar"
        const val POT_OF_VINEGAR = "obj.rag_pot_vinegar"
        const val POT = "obj.pot_empty"
        const val JUG = "obj.jug_empty"
        const val LOGS = "obj.logs"
        const val TINDERBOX = "obj.tinderbox"
        const val COINS = "obj.coins"
        const val VINEGAR_PRICE = 1
    }
}

private var Player.boiler: Int by intVarBit("varbit.rag_boiler")
private var Player.boilerContents: Int by intVarBit("varbit.rag_potboiler")
private var Player.boilSteps: Int by intVarBit("varbit.rag_boil_steps")
