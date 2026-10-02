package org.rsmod.content.quest.area.ardougne.sheepherder

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sheep Herder.
 *
 * The stage is the cache varp `varp.sheepherderquest`, endstate 3 from `dbrow.quest_sheepherder`:
 * - [STAGE_STARTED]: Councillor Halgrive has handed over the poisoned feed.
 * - [STAGE_DISPOSED]: all four colours have been burned; Halgrive owes the payment.
 * - [STAGE_COMPLETE]: paid off.
 *
 * Each colour has its own 3-bit cache varbit on `varp.sheepherdervar` holding a [SheepState]; the
 * enclosure sheep are multinpcs on those varbits, so a sheep shows in the pen only to the player
 * who herded it there. Two server-only bits on the same varp remember that Doctor Orbon has been
 * paid and that the herding tutorial has been shown.
 */
@Singleton
class SheepHerderQuest :
    QuestScript(
        QUEST_KEY,
        "varp.sheepherderquest",
        rewards {
            item(COINS, REWARD_COINS)
            scroll("3,100 coins:", "100 for the protective clothing", "and 3,000 for the job")
        },
        ItemRewardDisplay(CATTLEPROD, zoom = 250),
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::normalise)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Councillor Halgrive</col> outside the church in " +
            "<col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val start =
                "Councillor Halgrive asked me to dispose of four plague-ridden sheep, one red, one " +
                    "green, one blue and one yellow, which have strayed near Farmer Brumty's farm " +
                    "north-west of Ardougne."
            if (stage >= STAGE_DISPOSED) {
                strike(start)
                for (colour in SheepColour.entries) strike(colour.journalDone())
                line("All four sheep have been incinerated. I should tell Councillor Halgrive.")
                return@questJournal
            }
            line(start)
            if (!hasFeed(p, access.bank)) {
                line("I've lost the poisoned sheep feed. Councillor Halgrive will have more.")
            }
            val missing = protectionMissing(p)
            when {
                !hasBoughtClothing(p) ->
                    line(
                        "Doctor Orbon, inside the church, sells protective clothing for 100 " +
                            "coins. I shouldn't go near the sheep without it.",
                    )
                missing.isNotEmpty() ->
                    line("I need to wear my ${missing.joinToString(" and ")} to handle the sheep.")
                else -> strike("I'm wearing Doctor Orbon's protective clothing.")
            }
            when {
                CATTLEPROD in p.worn -> strike("I'm wielding the cattleprod.")
                CATTLEPROD in p.inv -> line("I need to wield the cattleprod to herd the sheep.")
                else ->
                    line(
                        "Farmer Brumty keeps a cattleprod near the incinerator inside his enclosure.",
                    )
            }
            line(
                "I must herd one sheep of each colour through the enclosure's western gate, feed " +
                    "it the poison, then burn its bones in the incinerator:",
            )
            for (colour in SheepColour.entries) {
                val text = colour.journalLine(state(p, colour), colour.bones in p.inv)
                if (state(p, colour) == SheepState.BURNED) strike(text) else line(text)
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Councillor Halgrive asked me to dispose of four plague-ridden sheep near Farmer " +
                    "Brumty's farm. Wearing Doctor Orbon's protective clothing, I herded one sheep " +
                    "of each colour into the enclosure with a cattleprod, fed them the council's " +
                    "poisoned feed and burned the remains in the incinerator.",
            )
            line("Halgrive paid me back for the clothing, and 3,000 coins on top.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isActive(player: Player): Boolean = stage(player) == STAGE_STARTED

    fun state(player: Player, colour: SheepColour): SheepState =
        SheepState.entries[player.vars[colour.varbit].coerceIn(0, SheepState.entries.size - 1)]

    fun setState(player: Player, colour: SheepColour, state: SheepState) {
        VarPlayerIntMapSetter.set(player, colour.varbit, state.ordinal)
    }

    fun remaining(player: Player): List<SheepColour> =
        SheepColour.entries.filter { state(player, it) != SheepState.BURNED }

    fun allBurned(player: Player): Boolean = remaining(player).isEmpty()

    fun hasBoughtClothing(player: Player): Boolean = player.clothingBought == 1

    fun markClothingBought(player: Player) {
        player.clothingBought = 1
    }

    fun hasSeenTutorial(player: Player): Boolean = player.prodTutorial == 1

    fun markTutorialSeen(player: Player) {
        player.prodTutorial = 1
    }

    fun hasFeed(player: Player, bank: Inventory?): Boolean =
        FEED in player.inv || (bank != null && FEED in bank)

    /** The protective pieces the player isn't wearing, by name; empty when fully protected. */
    fun protectionMissing(player: Player): List<String> = buildList {
        if (JACKET !in player.worn) add("plague jacket")
        if (TROUSERS !in player.worn) add("plague trousers")
    }

    /** Why the player can't safely handle the sheep, the gate or the remains, or null. */
    fun protectionProblem(player: Player): String? {
        val missing = protectionMissing(player)
        if (missing.isEmpty()) {
            return null
        }
        val owned = missing.filter { name ->
            val obj = if (name == "plague jacket") JACKET else TROUSERS
            obj in player.inv
        }
        return when {
            !hasBoughtClothing(player) ->
                "You need protective clothing first. Doctor Orbon, in the East Ardougne church, " +
                    "sells it."
            owned.size == missing.size ->
                "You need to wear your ${missing.joinToString(" and ")} for protection first."
            else ->
                "You're not wearing your ${missing.joinToString(" and ")}. Doctor Orbon can " +
                    "replace anything you've lost."
        }
    }

    fun hint(player: Player, bank: Inventory?): String {
        val stage = stage(player)
        if (stage == 0) {
            return "Talk to Councillor Halgrive outside the church in East Ardougne."
        }
        if (stage >= STAGE_COMPLETE) {
            return "You have completed this quest."
        }
        if (stage == STAGE_DISPOSED) {
            return "All four sheep are dealt with. Report to Councillor Halgrive for your payment."
        }
        if (!hasFeed(player, bank)) {
            return "You've lost the poisoned feed. Councillor Halgrive will give you more."
        }
        protectionProblem(player)?.let { return it }
        if (CATTLEPROD !in player.worn) {
            return if (CATTLEPROD in player.inv) {
                "Wield the cattleprod before you prod a sheep."
            } else {
                "Pick up the cattleprod inside Farmer Brumty's enclosure, by the incinerator, " +
                    "or ask Farmer Brumty for a spare."
            }
        }
        val next = remaining(player).sortedByDescending { state(player, it).ordinal }.first()
        return when (state(player, next)) {
            SheepState.LOOSE ->
                "The ${next.label} sheep graze ${next.area}. ${next.route} Stand on the side " +
                    "opposite to where you want the sheep to go."
            SheepState.PENNED ->
                "Use the poisoned feed on the ${next.label} sheep inside the enclosure."
            SheepState.BONES ->
                if (next.bones in player.inv) {
                    "Use the ${next.label} sheep's bones on the incinerator inside the enclosure."
                } else {
                    "Pick up the ${next.label} sheep's bones in the enclosure. If they're gone, " +
                        "Farmer Brumty saved them."
                }
            SheepState.BURNED -> "Report to Councillor Halgrive."
        }
    }

    private fun normalise(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (colour in SheepColour.entries) {
            setState(player, colour, SheepState.LOOSE)
        }
        player.clothingBought = 0
        player.prodTutorial = 0
    }

    companion object {
        const val QUEST_KEY = "quest_sheepherder"

        const val STAGE_STARTED = 1
        const val STAGE_DISPOSED = 2
        const val STAGE_COMPLETE = 3

        const val CLOTHING_PRICE = 100
        const val REWARD_COINS = 3_100

        const val HALGRIVE = "npc.councillor_halgrive_vis"
        const val ORBON = "npc.doctor_orbon"
        const val BRUMTY = "npc.farmer_brumty"

        const val FEED = "obj.poisoned_feed"
        const val CATTLEPROD = "obj.cattleprod"
        const val JACKET = "obj.plague_jacket"
        const val TROUSERS = "obj.plague_trousers"
        const val COINS = "obj.coins"
    }
}

enum class SheepState {
    LOOSE,
    PENNED,
    BONES,
    BURNED,
}

/**
 * One of the four discoloured sheep. [fieldNpc] is the type the grazing sheep show as (they are
 * multinpcs on a Mourning's End varbit), [enclosureNpc] the per-player copy that appears in the pen.
 */
enum class SheepColour(
    val label: String,
    val varbit: String,
    val fieldNpc: String,
    val shownNpc: String,
    val enclosureNpc: String,
    val bones: String,
    val area: String,
    val route: String,
) {
    RED(
        "red",
        "varbit.sheepherder_sheep_a",
        "npc.plaguesheep_1",
        "npc.herder_plaguesheep_1",
        "npc.herder_plaguesheep_1_enclosure",
        "obj.sheepbonesa",
        "south of the enclosure",
        "Drive it north up the enclosure's east side, west along its north fence, then south to the gate.",
    ),
    GREEN(
        "green",
        "varbit.sheepherder_sheep_b",
        "npc.plaguesheep_2",
        "npc.herder_plaguesheep_2",
        "npc.herder_plaguesheep_2_enclosure",
        "obj.sheepbonesb",
        "east of the enclosure",
        "Drive it west past the enclosure's north fence, then south down its west wall.",
    ),
    BLUE(
        "blue",
        "varbit.sheepherder_sheep_c",
        "npc.plaguesheep_3",
        "npc.herder_plaguesheep_3",
        "npc.herder_plaguesheep_3_enclosure",
        "obj.sheepbonesc",
        "far to the north-west",
        "Drive it east across the open field, then south down to the western gate.",
    ),
    YELLOW(
        "yellow",
        "varbit.sheepherder_sheep_d",
        "npc.plaguesheep_4",
        "npc.herder_plaguesheep_4",
        "npc.herder_plaguesheep_4_enclosure",
        "obj.sheepbonesd",
        "to the north",
        "Drive it south and west round the trees, then down the enclosure's west wall.",
    );

    val title: String = label.replaceFirstChar { it.uppercase() }

    fun journalLine(state: SheepState, bonesCarried: Boolean): String =
        when (state) {
            SheepState.LOOSE -> "$title sheep ($area): not yet herded."
            SheepState.PENNED -> "$title sheep: penned. It needs the poisoned feed."
            SheepState.BONES ->
                if (bonesCarried) {
                    "$title sheep: bones collected. They need incinerating."
                } else {
                    "$title sheep: bones left in the enclosure to collect."
                }
            SheepState.BURNED -> journalDone()
        }

    fun journalDone(): String = "$title sheep: remains incinerated."

    companion object {
        fun ofBones(obj: String): SheepColour? = entries.firstOrNull { it.bones == obj }
    }
}

private var Player.clothingBought: Int by intVarBit("varbit.sheepherder_clothing_bought")
private var Player.prodTutorial: Int by intVarBit("varbit.sheepherder_prod_tutorial")
