package org.rsmod.content.quest.area.zanaris.fairytale1

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fairytale I - Growing Pains.
 *
 * The stage is `varbit.fairy_farmers_quest` (bits 0-6 of `varp.fairytale_multi` 671). The rest of
 * that varp holds the three ingredients Malignius Mortifer names (5 bits each) and the three
 * "who is in Zanaris" checks, so the quest manager is handed the varbit and everything else is
 * kept in attributes and mirrored by [syncVars].
 *
 * The stage values are fixed by the cache: `loc.fairy_walls_multi` and
 * `npc.fairy_mindslayer_guard_multi` only resolve at multiples of ten, so the quest runs 10, 20,
 * ... 90, with 90 being the endstate from `dbrow.quest_fairytale1`.
 */
@Singleton
class Fairytale1Quest : QuestScript(
    "quest_fairytale1",
    "varp.fairytale_multi",
    rewards {
        xp("stat.farming", FARMING_XP)
        xp("stat.attack", ATTACK_XP)
        xp("stat.magic", MAGIC_XP)
        extra("Magic secateurs")
    },
    ItemRewardDisplay(MAGIC_SECATEURS, zoom = 180),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    questVarbit = "varbit.fairy_farmers_quest",
) {
    /** One bit per [GARDENERS] entry, set when that gardener has given their theory. */
    val gardenersAsked = quest.attribute(name = "GARDENERS_ASKED", default = 0)

    /** Set once Malignius Mortifer has the Draynor skull and has named the three ingredients. */
    val mortiferListGiven = quest.attribute(name = "MORTIFER_LIST_GIVEN", default = false)

    /** Index into [MORTIFER_ITEMS] of each ingredient Mortifer named, or -1 before he has read. */
    val ingredient1 = quest.attribute(name = "INGREDIENT_1", default = -1)
    val ingredient2 = quest.attribute(name = "INGREDIENT_2", default = -1)
    val ingredient3 = quest.attribute(name = "INGREDIENT_3", default = -1)

    /** Set once the Tanglefoot has been cut down and the Queen's secateurs picked up. */
    val tanglefootSlain = quest.attribute(name = "TANGLEFOOT_SLAIN", default = false)

    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
        onPlayerLogin { syncVars(player) }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
        syncVars(access.player)
    }

    fun meetsRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, LOST_CITY) &&
            QuestRequirements.hasCompleted(player, NATURE_SPIRIT)

    fun hasAskedGardener(player: Player, index: Int): Boolean =
        gardenersAsked.get(player) and (1 shl index) != 0

    fun gardenersAskedCount(player: Player): Int = Integer.bitCount(gardenersAsked.get(player))

    fun markGardenerAsked(player: Player, index: Int) {
        gardenersAsked.set(player, gardenersAsked.get(player) or (1 shl index))
    }

    /** Indices into [MORTIFER_ITEMS] of the three items Mortifer named, in his order. */
    fun ingredientIndices(player: Player): List<Int> =
        listOf(ingredient1.get(player), ingredient2.get(player), ingredient3.get(player))
            .filter { it in MORTIFER_ITEMS.indices }

    /** The three items Mortifer named, in the order he named them. */
    fun ingredients(player: Player): List<String> =
        ingredientIndices(player).map(MORTIFER_ITEMS::get)

    /**
     * Mortifer's divination always lands on three consecutive entries of his list, so the three
     * ingredients are rolled from a single starting point.
     */
    fun rollIngredients(player: Player, start: Int) {
        ingredient1.set(player, start % MORTIFER_ITEMS.size)
        ingredient2.set(player, (start + 1) % MORTIFER_ITEMS.size)
        ingredient3.set(player, (start + 2) % MORTIFER_ITEMS.size)
        syncVars(player)
    }

    /**
     * Pushes the attribute-backed state into the varbits that share the quest varp: the three
     * ingredient slots the client's quest helper reads, and the two checks that decide whether
     * Zanaris shows the Fairy Queen on her throne or the Fairy Godfather on his.
     */
    fun syncVars(player: Player) {
        val godfatherInCharge = stage(player) >= STAGE_SENT_TO_ZANARIS
        setVar(player, "varbit.fairy_queen_check", if (godfatherInCharge) 1 else 0)
        setVar(player, "varbit.fairy_godfather_check", if (godfatherInCharge) 1 else 0)
        setVar(player, "varbit.fairy_nature_item1", ingredient1.get(player) + 1)
        setVar(player, "varbit.fairy_nature_item2", ingredient2.get(player) + 1)
        setVar(player, "varbit.fairy_nature_item3", ingredient3.get(player) + 1)
    }

    private fun setVar(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != value) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Martin the Master Gardener</col> in <col=800000>Draynor " +
            "Village</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val asked = gardenersAskedCount(player.player)

            objective(
                "<red>Martin the Master Gardener</red> in <red>Draynor Village</red> says his " +
                    "roses have stopped growing, and he is not the only gardener with the " +
                    "problem.",
            ) {}

            objective(
                "I should ask <red>five</red> members of the <red>Group of Advanced Gardeners" +
                    "</red> - the farmers who tend the allotments, herb and tree patches around " +
                    "Gielinor - what they make of it. So far I have asked <red>$asked</red> of " +
                    "them.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective("I should go back to <red>Martin</red> with what the gardeners told me.") {
                visibleWhen { stage(access.player) == STAGE_GARDENERS_ASKED }
            }

            objective(
                "The gardeners blame the fairies. I should travel to <red>Zanaris</red> - the " +
                    "shed in <red>Lumbridge Swamp</red>, with a <red>dramen staff</red> wielded " +
                    "- and find out what has gone wrong there.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SENT_TO_ZANARIS }
            }

            objective(
                "A <red>Fairy Godfather</red> sits on the throne in Zanaris and will not say " +
                    "where the <red>Fairy Queen</red> has gone. <red>Fairy Nuff</red>, north of " +
                    "the Zanaris bank, might be more forthcoming.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SEEN_GODFATHER }
            }

            objective(
                "The Queen lies ill in Fairy Nuff's grotto. Nuff gave me a <red>symptoms list" +
                    "</red> to show to <red>Zandar Horfyre</red>, at the top of the <red>Dark " +
                    "Wizards' Tower</red> west of Falador.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAS_SYMPTOMS }
            }

            objective(
                "Zandar says a <red>Tanglefoot</red> has stolen the Queen's secateurs, and that " +
                    "only the necromancer <red>Malignius Mortifer</red>, south of Falador, knows " +
                    "how to kill one.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SEEN_ZANDAR }
            }

            objective(
                "Mortifer wants a <red>Draynor skull</red> from the grave behind <red>Draynor " +
                    "Manor</red>. I will need a <red>spade</red> to dig it up.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_SEEN_MORTIFER &&
                        !mortiferListGiven.get(access.player)
                }
                hasItem("fairy_skull", "I have dug up the Draynor skull for Mortifer.").strike()
            }

            objective(
                "Mortifer read the skull. The <red>Nature Spirit</red> in the grotto in " +
                    "<red>Mort Myre</red> will enchant a pair of secateurs for me if I bring him:",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_SEEN_MORTIFER &&
                        mortiferListGiven.get(access.player)
                }
            }

            objective("A pair of <red>secateurs</red>.") {
                visibleWhen {
                    stage(access.player) == STAGE_SEEN_MORTIFER &&
                        mortiferListGiven.get(access.player)
                }
                hasItem("secateurs", "A pair of <red>secateurs</red>.").strike()
            }

            for (index in ingredientIndices(player.player)) {
                val label = MORTIFER_ITEM_NAMES[index]
                objective("<red>$label</red>.") {
                    visibleWhen {
                        stage(access.player) == STAGE_SEEN_MORTIFER &&
                            mortiferListGiven.get(access.player)
                    }
                    hasItem(MORTIFER_ITEMS[index].removePrefix("obj."), "<red>$label</red>.")
                        .strike()
                }
            }

            objective(
                "I have the magic secateurs. Now to find the <red>Tanglefoot</red>, through the " +
                    "gap in the wall south-west of the Zanaris wheat field. Nothing else will " +
                    "scratch it.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_HAS_SECATEURS &&
                        !tanglefootSlain.get(access.player)
                }
            }

            objective(
                "The Tanglefoot is dead. I should take the <red>Queen's secateurs</red> back to " +
                    "the <red>Fairy Godfather</red> in Zanaris.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_HAS_SECATEURS &&
                        tanglefootSlain.get(access.player)
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Martin the Master Gardener could not get his roses to grow, and neither could " +
                    "any other member of the Group of Advanced Gardeners. Between them they " +
                    "decided the fairies had stopped tending the crops.",
            )
            line(
                "In Zanaris a Fairy Godfather had taken the throne while the Fairy Queen lay " +
                    "ill in Fairy Nuff's grotto. Zandar Horfyre read her symptoms and named the " +
                    "cause: a Tanglefoot had her secateurs.",
            )
            line(
                "Malignius Mortifer read a Draynor skull for the ingredients, the Nature Spirit " +
                    "of Mort Myre enchanted my secateurs with them, and I cut the Tanglefoot " +
                    "down and took the Queen's secateurs back to the Godfather.",
            )
        }

    companion object {
        const val STAGE_STARTED = 10
        const val STAGE_GARDENERS_ASKED = 20
        const val STAGE_SENT_TO_ZANARIS = 30
        const val STAGE_SEEN_GODFATHER = 40
        const val STAGE_HAS_SYMPTOMS = 50
        const val STAGE_SEEN_ZANDAR = 60
        const val STAGE_SEEN_MORTIFER = 70
        const val STAGE_HAS_SECATEURS = 80
        const val STAGE_COMPLETE = 90

        const val GARDENERS_NEEDED = 5

        const val FARMING_XP = 3500.0
        const val ATTACK_XP = 2000.0
        const val MAGIC_XP = 1000.0

        const val LOST_CITY = "quest_lostcity"
        const val NATURE_SPIRIT = "quest_naturespirit"

        const val MARTIN = "npc.martin_the_master_farmer"
        const val GODFATHER = "npc.fairy_godfather_multi"
        const val SLIM_LOUIE = "npc.fairy_henchman1"
        const val SLIM_LOUIE_MULTI = "npc.fairy_henchman1_multi"
        const val FAT_ROCCO = "npc.fairy_henchman2"
        const val FAT_ROCCO_MULTI = "npc.fairy_henchman2_multi"
        const val FAIRY_QUEEN = "npc.fairy_queen_multi"
        const val FAIRY_NUFF = "npc.fairy_nuff_multi"
        const val ZANDAR = "npc.zandar_horfyre"
        const val MORTIFER = "npc.elemental_wizard_boss"
        const val GATEKEEPER = "npc.fairy_mindslayer_guard_multi"
        const val TANGLEFOOT = "npc.fairy_tanglefoot"

        const val SECATEURS = "obj.secateurs"
        const val MAGIC_SECATEURS = "obj.fairy_enchanted_secateurs"
        const val QUEENS_SECATEURS = "obj.fairy_queen_secateurs"
        const val SYMPTOMS_LIST = "obj.fairy_symptoms_list"
        const val DRAYNOR_SKULL = "obj.fairy_skull"
        const val SPADE = "obj.spade"

        /** The gardeners who can be asked, in the order their bit sits in [gardenersAsked]. */
        val GARDENERS =
            listOf(
                "npc.elstan",
                "npc.dantaera",
                "npc.kragen",
                "npc.lyra",
                "npc.francis",
                "npc.garth",
                "npc.farming_gardener_fruit_4",
                "npc.farming_gardener_hops_1",
                "npc.farming_gardener_hops_3",
                "npc.farming_gardener_hops_4",
                "npc.farming_gardener_bush_1",
                "npc.farming_gardener_bush_2",
                "npc.farming_gardener_bush_3",
                "npc.farming_gardener_bush_4",
                "npc.farming_gardener_tree_1",
                "npc.farming_gardener_tree_2",
                "npc.farming_gardener_tree_3",
                "npc.farming_gardener_tree_4",
                "npc.farming_gardener_fruit_1",
                "npc.farming_gardener_fruit_2",
                "npc.farming_gardener_spirit_tree_1",
                "npc.farming_gardener_spirit_tree_2",
                "npc.farming_gardener_spirit_tree_3",
                "npc.farming_gardener_tree_gnome",
                "npc.farming_gardener_calquat",
                "npc.farming_gardener_fruit_tree_5",
                "npc.farming_gardener_cactus",
            )

        /**
         * The thirty-one things a Draynor skull can point Mortifer at, in the order he reads
         * them. The roll always takes three consecutive entries.
         */
        val MORTIFER_ITEMS =
            listOf(
                "obj.white_berries",
                "obj.mortmyrepear",
                "obj.mortmyrebuddingstem",
                "obj.mortmyremushroom",
                "obj.nature_talisman",
                "obj.avantoe",
                "obj.irit_leaf",
                "obj.blue_dragon_scale",
                "obj.mosquito_proboscis",
                "obj.jangerberries",
                "obj.cactus_potato",
                "obj.crushed_gemstone",
                "obj.snapdragon",
                "obj.bucket_supercompost",
                "obj.volencia_moss",
                "obj.babydragon_bones",
                "obj.uncut_diamond",
                "obj.raw_cave_eel",
                "obj.edible_seaweed",
                "obj.oystershell",
                "obj.charcoal",
                "obj.red_vine_worm",
                "obj.snail_corpse3",
                "obj.red_spiders_eggs",
                "obj.mort_slimey_eel",
                "obj.grapes",
                "obj.uncut_ruby",
                "obj.tbwt_jogre_bones",
                "obj.king_worm",
                "obj.snape_grass",
                "obj.lime",
            )

        val MORTIFER_ITEM_NAMES =
            listOf(
                "White berries",
                "A Mort myre pear",
                "A Mort myre stem",
                "Some Mort myre fungus",
                "A nature talisman",
                "A clean avantoe",
                "A clean irit leaf",
                "A blue dragon scale",
                "A proboscis",
                "Some jangerberries",
                "A potato cactus",
                "A crushed gem",
                "A clean snapdragon",
                "A bucket of supercompost",
                "Some clean volencia moss",
                "Some babydragon bones",
                "An uncut diamond",
                "A raw cave eel",
                "Some edible seaweed",
                "An unopened oyster",
                "Some charcoal",
                "A red vine worm",
                "A fat snail",
                "Some red spiders' eggs",
                "A raw slimy eel",
                "Some grapes",
                "An uncut ruby",
                "Some jogre bones",
                "A king worm",
                "Some snape grass",
                "A lime",
            )
    }
}
