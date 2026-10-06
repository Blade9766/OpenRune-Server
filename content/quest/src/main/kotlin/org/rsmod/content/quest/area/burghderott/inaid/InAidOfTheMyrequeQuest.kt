package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * In Aid of the Myreque.
 *
 * The stage is the cache varbit `varbit.myreque_2_quest` (endstate [STAGE_COMPLETE], 430). Every
 * personal change to Burgh de Rott is a cache varbit the client already reads: the inn's rubble
 * and trapdoor, the store's roof, wall, crates and shelves, the bank booth, wall and teller, the
 * furnace, the blood tithe npcs and the Paterdomus library trapdoor. [syncWorld] derives each one
 * from the stage on every login and stage change, so a jump or reset never leaves a stale repair.
 *
 * Under the default `assume-completed` requirement policy a player who never started the quest
 * is treated as having finished it ([effectiveStage]): the town, its services and Temple
 * Trekking are open to them, exactly as they would be after a real completion.
 */
@Singleton
class InAidOfTheMyrequeQuest :
    QuestScript(
        QUEST_KEY,
        "varp.myreque2_multivar",
        rewards {
            xp("stat.attack", REWARD_XP)
            xp("stat.strength", REWARD_XP)
            xp("stat.crafting", REWARD_XP)
            xp("stat.defence", REWARD_XP)
            scroll(
                "2,000 Attack XP",
                "2,000 Strength XP",
                "2,000 Crafting XP",
                "2,000 Defence XP",
                "Access to Temple Trekking",
                "The ability to make the Rod of Ivandis",
            )
        },
        ItemRewardDisplay(ROD_FULL, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
        questVarbit = "varbit.myreque_2_quest",
    ) {

    override fun ScriptContext.init() {
        quest.onVarSync(::syncWorld)
    }

    override fun subTitle(): String =
        "speaking to <col=800000>Veliaf Hurtz</col> in the <col=800000>Myreque hideout</col> " +
            "beneath <col=800000>Canifis</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val start = "Veliaf Hurtz asked me to find the Myreque a new hideout in Burgh de Rott, south of Mort'ton, and to win over the townsfolk."
            if (stage < STAGE_ADMITTED) {
                line(start)
                line("The people at the <col=800000>gate</col> won't let strangers in. Perhaps a show of good faith would help.")
                return@questJournal
            }
            strike(start)
            strike("I left some food in the chest by the gate and Florin let me into town.")
            if (stage < STAGE_CELLAR_SUGGESTED) {
                line("I should ask the townsfolk if there's anywhere <col=800000>out of the way</col> to hide.")
                return@questJournal
            }
            if (stage < STAGE_CELLAR_CLEARED) {
                line("The <col=800000>inn's cellar</col> is buried in rubble. I need a <col=800000>pickaxe</col> to break it up, then a <col=800000>spade</col> and <col=800000>buckets</col> to carry it out to the rubble pile outside.")
                line("I have cleared ${rubbleRemoved(p)} of $RUBBLE_PILES piles of rubble.")
                return@questJournal
            }
            strike("I cleared the rubble out of the inn's cellar.")
            if (stage < STAGE_HELP_OFFERED) {
                line("I should offer to help the townsfolk <col=800000>fix up the town</col>.")
                return@questJournal
            }
            if (stage < STAGE_STORE_STOCKED) {
                if (stage < STAGE_STORE_REPAIRS) {
                    line("<col=800000>Aurel</col> runs the general store. I should talk to him about repairs.")
                    return@questJournal
                }
                if (!isStoreRepaired(p)) {
                    line("Aurel wants the store's <col=800000>roof</col> and <col=800000>wall</col> fixed, each with a <col=800000>hammer</col>, <col=800000>3 planks</col> and <col=800000>12 nails</col>.")
                    if (p.storeRoof == 1) strike("I have fixed the roof.")
                    if (p.storeWall == 1) strike("I have fixed the wall.")
                    return@questJournal
                }
                strike("I repaired the store's roof and wall.")
                if (stage < STAGE_CRATE_GIVEN) {
                    line("I should ask <col=800000>Aurel</col> what to do now.")
                    return@questJournal
                }
                line("Aurel gave me a crate to fill: ${crateNeeds(p)}.")
                return@questJournal
            }
            strike("I stocked Aurel's general store.")
            if (stage < STAGE_BANK_OPEN) {
                line("The <col=800000>bank</col> needs its booth (hammer, 2 planks, 8 nails and swamp paste) and wall (hammer, 3 planks, 12 nails) repaired, and then someone to run it.")
                if (p.bankBooth == 1) strike("I have repaired the booth.")
                if (p.bankWall == 1) strike("I have repaired the wall.")
                return@questJournal
            }
            strike("I repaired the bank and Cornelius agreed to be the banker.")
            if (stage < STAGE_FURNACE_LIT) {
                line("The town's <col=800000>furnace</col> is broken. It needs <col=800000>2 steel bars</col> and a <col=800000>hammer</col>, then <col=800000>coal</col> and a <col=800000>tinderbox</col>.")
                return@questJournal
            }
            strike("I repaired and lit the furnace.")
            if (stage < STAGE_GADDERANKS_DEFEATED) {
                line("The smoke drew <col=800000>Gadderanks</col>, a blood tithe collector, and two vampyre juvinates to the general store. I'll need a <col=800000>silver weapon</col> or <col=800000>Efaritay's aid</col> to hurt them.")
                return@questJournal
            }
            strike("With Veliaf's help I defeated Gadderanks and his juvinates.")
            if (stage < STAGE_GADDERANKS_DEAD) {
                line("Gadderanks is badly hurt. I should <col=800000>speak to him</col> before he dies.")
                return@questJournal
            }
            strike("Before he died, Gadderanks told me what the vampyres fear: silver, garlic and a potion of harralander and red spiders' eggs.")
            if (stage < STAGE_RETURN_TO_HOLLOWS) {
                line("I should tell <col=800000>Veliaf</col> about the inn's cellar.")
                return@questJournal
            }
            if (stage < STAGE_RELOCATION_BRIEFED) {
                line("Veliaf wants to meet me back at the <col=800000>old hideout</col>.")
                return@questJournal
            }
            if (stage < STAGE_IVAN_DELIVERED) {
                strike("Veliaf asked me to escort Ivan Strom to Drezel at Paterdomus.")
                if (stage < STAGE_PARTY_TOLD) {
                    line("First I should tell <col=800000>Polmafi</col> or <col=800000>Radigad</col> that it is time to leave.")
                } else {
                    line("I should take <col=800000>Ivan</col> to Paterdomus. Steel armour, a silver sickle and food would help keep him alive.")
                }
                return@questJournal
            }
            strike("I brought Ivan safely to Paterdomus.")
            if (stage < STAGE_LIBRARY_KEY) {
                line("I should ask <col=800000>Drezel</col> about Ivandis Seergaze.")
                return@questJournal
            }
            if (stage < STAGE_BOOK_READ) {
                line("Drezel gave me a key to a secret library beneath the mausoleum. I should look for something about the Seven Priestly Warriors.")
                return@questJournal
            }
            strike("I read The sleeping seven in the Paterdomus library.")
            if (stage < STAGE_MOULD_MADE) {
                line("Ivandis may be buried near the Myreque's old hideout. I should look for a <col=800000>boarded-up cave</col> there.")
                return@questJournal
            }
            strike("I made a clay mould of the rod fused to Ivandis' coffin.")
            line("I need to make a rod from a <col=800000>silver bar</col>, <col=800000>mithril bar</col> and <col=800000>sapphire</col> at a furnace, enchant it with <col=800000>Lvl-1 Enchant</col>, bless it in the Salve, then bring it to <col=800000>Veliaf</col> in Burgh de Rott.")
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("I found the Myreque a new home in the cellar of the inn at Burgh de Rott, and helped the townsfolk repair their store, bank and furnace.")
            line("The furnace smoke drew Gadderanks, a blood tithe collector, whom Veliaf and I defeated. Before he died he told us what vampyres fear.")
            line("I escorted Ivan Strom to Paterdomus and learned of Ivandis Seergaze, one of the Seven Priestly Warriors.")
            line("I recreated the Rod of Ivandis and gave it to Veliaf.")
        }

    fun hint(player: Player): String {
        val stage = stage(player)
        return when {
            stage == 0 -> "Speak to Veliaf in the Myreque hideout."
            stage < STAGE_ADMITTED -> "Talk to Florin at the north gate of Burgh de Rott, then leave a piece of food in the open chest outside."
            stage < STAGE_CELLAR_SUGGESTED -> "Ask any townsperson about 'out of the way' places."
            stage < STAGE_CELLAR_CLEARED -> "Climb over the broken wall of the inn, mine the rubble on the trapdoor and clear the cellar with a pickaxe, spade and buckets."
            stage < STAGE_HELP_OFFERED -> "Tell a townsperson you'd like to help fix up the town."
            stage < STAGE_STORE_REPAIRS -> "Talk to Aurel in the general store."
            !isStoreRepaired(player) -> "Repair the store's roof (up the ladder) and wall with a hammer, 3 planks and 12 nails each."
            stage < STAGE_CRATE_GIVEN -> "Ask Aurel what to do now."
            stage < STAGE_STORE_STOCKED -> "Fill the crate with ${crateNeeds(player)}, then show it to Aurel."
            stage < STAGE_BANK_OPEN -> "Repair the bank booth and wall, then ask Cornelius what should happen next."
            stage < STAGE_FURNACE_LIT -> "Repair the furnace with 2 steel bars, add coal and light it with a tinderbox."
            stage < STAGE_TITHE_FIGHT -> "Talk to Gadderanks, Wiskit and a vampyre juvinate in the general store."
            stage < STAGE_GADDERANKS_DEFEATED -> "Fight Gadderanks and his juvinates in the store with a silver weapon or Efaritay's aid. Leaving resets the fight."
            stage < STAGE_GADDERANKS_DEAD -> "Talk to Veliaf in the general store."
            stage < STAGE_RETURN_TO_HOLLOWS -> "Talk to Veliaf in the general store."
            stage < STAGE_RELOCATION_BRIEFED -> "Meet Veliaf in the Myreque's old hideout."
            stage < STAGE_PARTY_TOLD -> "Tell Polmafi or Radigad it is time to leave."
            stage < STAGE_IVAN_DELIVERED -> "Give Ivan steel armour, a silver sickle and food if you like, then talk to him to set off."
            stage < STAGE_LIBRARY_KEY -> "Ask Drezel how to learn more about Ivandis."
            stage < STAGE_BOOK_READ -> "Use the library key on the keyhole near Drezel, then search the library's bookcases and read what you find."
            stage < STAGE_MOULD_MADE -> "Break the boards over the cave near the old hideout with a hammer and use soft clay on the coffin inside."
            else -> "Make the silvthrill rod at a furnace, cast Lvl-1 Enchant on it, dip it in the well under Paterdomus with a rope, and give it to Veliaf in Burgh de Rott."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** The real stage, or [STAGE_COMPLETE] for an unstarted player the requirement policy counts as done. */
    fun effectiveStage(player: Player): Int {
        val stage = stage(player)
        if (stage == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            return STAGE_COMPLETE
        }
        return stage
    }

    fun reached(player: Player, stage: Int): Boolean = effectiveStage(player) >= stage

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    /** For progress made outside the player's own script, such as an escort finished by a tick. */
    fun jumpForward(player: Player, stage: Int) {
        if (stage > stage(player) && stage < STAGE_COMPLETE) {
            quest.jumpToStage(player, stage)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun canStart(player: Player): Boolean = QuestRequirements.hasCompleted(player, IN_SEARCH)

    /** Crafting and Mining are checked at their base level, Agility and Magic may be boosted. */
    fun missingSkills(player: Player): List<String> =
        buildList {
            if (player.statBase(CRAFTING) < REQUIRED_CRAFTING) add("level $REQUIRED_CRAFTING Crafting")
            if (player.statBase(MINING) < REQUIRED_MINING) add("level $REQUIRED_MINING Mining")
            if (player.stat(AGILITY) < REQUIRED_AGILITY) add("level $REQUIRED_AGILITY Agility")
            if (player.stat(MAGIC) < REQUIRED_MAGIC) add("level $REQUIRED_MAGIC Magic")
        }

    fun isAdmitted(player: Player): Boolean = reached(player, STAGE_ADMITTED)

    fun isStoreOpen(player: Player): Boolean = reached(player, STAGE_STORE_STOCKED)

    fun isBankOpen(player: Player): Boolean = reached(player, STAGE_BANK_OPEN)

    fun isFurnaceLit(player: Player): Boolean = reached(player, STAGE_FURNACE_LIT)

    fun knowsGuthixBalance(player: Player): Boolean = reached(player, STAGE_GADDERANKS_DEAD)

    fun canWieldGadderhammer(player: Player): Boolean = reached(player, STAGE_GADDERANKS_DEAD)

    /** What Temple Trekking (not yet on this server) should check before letting anyone trek. */
    fun templeTrekkingUnlocked(player: Player): Boolean = reached(player, STAGE_COMPLETE)

    fun isFlorinRefused(player: Player): Boolean = player.vars[FLORIN_REFUSED] == 1

    fun markFlorinRefused(player: Player) {
        VarPlayerIntMapSetter.set(player, FLORIN_REFUSED, 1)
    }

    /* The cellar rubble */

    fun isPileRemoved(player: Player, pile: Int): Boolean = player.vars[RUBBLE_REMOVED] and (1 shl pile) != 0

    fun markPileRemoved(player: Player, pile: Int) {
        val mask = player.vars[RUBBLE_REMOVED] or (1 shl pile)
        VarPlayerIntMapSetter.set(player, RUBBLE_REMOVED, mask)
        VarPlayerIntMapSetter.set(player, RUBBLE_COUNT, Integer.bitCount(mask))
    }

    fun rubbleRemoved(player: Player): Int = Integer.bitCount(player.vars[RUBBLE_REMOVED])

    fun isCellarClear(player: Player): Boolean = rubbleRemoved(player) >= RUBBLE_PILES

    /* The general store */

    fun isStoreRepaired(player: Player): Boolean = player.storeRoof == 1 && player.storeWall == 1

    fun crateRequest(player: Player): CrateFood? = CrateFood.entries.firstOrNull { it.varValue == player.foodType }

    fun crateNeeds(player: Player): String {
        val food = crateRequest(player) ?: return "10 bronze axes, 3 tinderboxes and 10 mackerel or snails"
        val parts = buildList {
            val axes = CRATE_AXES - player.vars[CRATE_AXES_VAR]
            val boxes = CRATE_TINDERBOXES - player.vars[CRATE_TINDERBOX_VAR]
            val meals = CRATE_FOOD - player.vars[CRATE_FOOD_VAR]
            if (axes > 0) add("$axes bronze axe${if (axes == 1) "" else "s"}")
            if (meals > 0) add("$meals ${if (meals == 1) food.single else food.plural}")
            if (boxes > 0) add("$boxes tinderbox${if (boxes == 1) "" else "es"}")
        }
        return if (parts.isEmpty()) "nothing more - it's full" else parts.joinToString(", ")
    }

    fun isCrateFull(player: Player): Boolean =
        player.vars[CRATE_AXES_VAR] >= CRATE_AXES &&
            player.vars[CRATE_FOOD_VAR] >= CRATE_FOOD &&
            player.vars[CRATE_TINDERBOX_VAR] >= CRATE_TINDERBOXES

    private fun syncWorld(player: Player) {
        val stage = effectiveStage(player)
        fun window(varbit: String, from: Int, done: Int, doneValue: Int) {
            val value = when {
                stage < from -> 0
                stage >= done -> doneValue
                else -> player.vars[varbit]
            }
            if (player.vars[varbit] != value) VarPlayerIntMapSetter.set(player, varbit, value)
        }
        window(INN_WALL, STAGE_CELLAR_SUGGESTED, STAGE_CELLAR_CLEARED, 1)
        window(INN_TRAPDOOR, STAGE_CELLAR_SUGGESTED, STAGE_CELLAR_CLEARED, 1)
        window(RUBBLE_REMOVED, STAGE_CELLAR_SUGGESTED, STAGE_CELLAR_CLEARED, ALL_PILES)
        window(RUBBLE_COUNT, STAGE_CELLAR_SUGGESTED, STAGE_CELLAR_CLEARED, RUBBLE_PILES)
        window(STORE_ROOF, STAGE_STORE_REPAIRS, STAGE_STORE_STOCKED, 1)
        window(STORE_WALL, STAGE_STORE_REPAIRS, STAGE_STORE_STOCKED, 1)
        window(FOOD_TYPE, STAGE_CRATE_GIVEN, STAGE_STORE_STOCKED, player.vars[FOOD_TYPE])
        window(CRATE_AXES_VAR, STAGE_CRATE_GIVEN, STAGE_STORE_STOCKED, 0)
        window(CRATE_FOOD_VAR, STAGE_CRATE_GIVEN, STAGE_STORE_STOCKED, 0)
        window(CRATE_TINDERBOX_VAR, STAGE_CRATE_GIVEN, STAGE_STORE_STOCKED, 0)
        window(STORE_STOCKED, STAGE_STORE_STOCKED, STAGE_STORE_STOCKED, 1)
        window(BANK_BOOTH, STAGE_STORE_STOCKED, STAGE_BANK_OPEN, 1)
        window(BANK_WALL, STAGE_STORE_STOCKED, STAGE_BANK_OPEN, 1)
        window(BANK_TELLER, STAGE_BANK_OPEN, STAGE_BANK_OPEN, 1)
        window(FURNACE, STAGE_BANK_OPEN, STAGE_FURNACE_LIT, FURNACE_LIT)
        window(GADDERANKS_CHAT, STAGE_FURNACE_LIT, STAGE_GADDERANKS_DEFEATED, 0)
        window(WISKIT_CHAT, STAGE_FURNACE_LIT, STAGE_GADDERANKS_DEFEATED, 0)
        window(JUVINATE_CHAT, STAGE_FURNACE_LIT, STAGE_GADDERANKS_DEFEATED, 0)
        window(HAMMER_GIVEN, STAGE_GADDERANKS_DEAD, STAGE_GADDERANKS_DEAD, player.vars[HAMMER_GIVEN])
        val tithe = when {
            stage < STAGE_FURNACE_LIT -> TITHE_NONE
            stage < STAGE_GADDERANKS_DEFEATED -> TITHE_COLLECTING
            stage < STAGE_RETURN_TO_HOLLOWS -> TITHE_VELIAF
            else -> TITHE_OVER
        }
        if (player.vars[TITHE_VISIBLE] != tithe) VarPlayerIntMapSetter.set(player, TITHE_VISIBLE, tithe)
        window(IVAN_HELM, STAGE_RELOCATION_BRIEFED, STAGE_IVAN_DELIVERED, 0)
        window(IVAN_BODY, STAGE_RELOCATION_BRIEFED, STAGE_IVAN_DELIVERED, 0)
        window(IVAN_LEGS, STAGE_RELOCATION_BRIEFED, STAGE_IVAN_DELIVERED, 0)
        window(IVAN_SICKLE, STAGE_RELOCATION_BRIEFED, STAGE_IVAN_DELIVERED, 0)
        window(IVAN_FOOD, STAGE_RELOCATION_BRIEFED, STAGE_IVAN_DELIVERED, 0)
        window(IVAN_FOOD_HEAL, STAGE_RELOCATION_BRIEFED, STAGE_IVAN_DELIVERED, 0)
        window(AMBUSH_ROUTE, STAGE_PARTY_TOLD, STAGE_IVAN_DELIVERED, player.vars[AMBUSH_ROUTE])
        window(AMBUSH_DEATHS, STAGE_PARTY_TOLD, STAGE_IVAN_DELIVERED, 0)
        // The real stage: Veliaf starts the quest from this hideout, so it stays manned until then.
        val hideoutEmpty = if (stage(player) >= STAGE_IVAN_DELIVERED) 1 else 0
        if (player.vars[HIDEOUT_NPCS] != hideoutEmpty) VarPlayerIntMapSetter.set(player, HIDEOUT_NPCS, hideoutEmpty)
        window(LIBRARY_TRAPDOOR, STAGE_LIBRARY_KEY, Int.MAX_VALUE, 0)
        if (stage >= STAGE_COMPLETE && stage(player) == 0) {
            VarPlayerIntMapSetter.set(player, LIBRARY_TRAPDOOR, 1)
        }
        window(TOMB_BOARDS, STAGE_BOOK_READ, STAGE_COMPLETE, 1)
        if (stage == 0) {
            VarPlayerIntMapSetter.set(player, FLORIN_REFUSED, 0)
        }
    }

    enum class CrateFood(val varValue: Int, val single: String, val plural: String, val objs: List<String>) {
        Mackerel(1, "mackerel", "mackerel", listOf("obj.raw_mackerel", "obj.mackerel")),
        Snail(
            2,
            "snail",
            "snails",
            listOf(
                "obj.snail_corpse1",
                "obj.snail_corpse2",
                "obj.snail_corpse3",
                "obj.snail_corpse_cooked1",
                "obj.snail_corpse_cooked2",
                "obj.snail_corpse_cooked3",
            ),
        ),
    }

    companion object {
        const val QUEST_KEY = "quest_inaidofthemyreque"
        const val IN_SEARCH = "quest_insearchofthemyreque"

        const val STAGE_STARTED = 10
        const val STAGE_ADMITTED = 20
        const val STAGE_CELLAR_SUGGESTED = 30
        const val STAGE_CELLAR_CLEARED = 40
        const val STAGE_HELP_OFFERED = 50
        const val STAGE_STORE_REPAIRS = 60
        const val STAGE_CRATE_GIVEN = 70
        const val STAGE_STORE_STOCKED = 80
        const val STAGE_BANK_OPEN = 90
        const val STAGE_FURNACE_LIT = 100
        const val STAGE_TITHE_FIGHT = 110
        const val STAGE_GADDERANKS_DEFEATED = 120
        const val STAGE_GADDERANKS_DEAD = 130
        const val STAGE_RETURN_TO_HOLLOWS = 140
        const val STAGE_RELOCATION_BRIEFED = 150
        const val STAGE_PARTY_TOLD = 160
        const val STAGE_IVAN_DELIVERED = 170
        const val STAGE_LIBRARY_KEY = 180
        const val STAGE_BOOK_READ = 190
        const val STAGE_TOMB_FOUND = 200
        const val STAGE_MOULD_MADE = 210
        const val STAGE_COMPLETE = 430

        const val REWARD_XP = 2000.0
        const val MUSEUM_KUDOS = 5

        const val REQUIRED_AGILITY = 25
        const val REQUIRED_CRAFTING = 25
        const val REQUIRED_MINING = 15
        const val REQUIRED_MAGIC = 7
        const val AGILITY = "stat.agility"
        const val CRAFTING = "stat.crafting"
        const val MINING = "stat.mining"
        const val MAGIC = "stat.magic"

        const val RUBBLE_PILES = 15
        const val ALL_PILES = (1 shl RUBBLE_PILES) - 1

        const val CRATE_AXES = 10
        const val CRATE_FOOD = 10
        const val CRATE_TINDERBOXES = 3

        const val FURNACE_BROKEN = 0
        const val FURNACE_REPAIRED = 1
        const val FURNACE_FUELLED = 2
        const val FURNACE_LIT = 3

        const val TITHE_NONE = 0
        const val TITHE_COLLECTING = 1
        const val TITHE_VELIAF = 2
        const val TITHE_OVER = 3

        const val INN_WALL = "varbit.burgh_inn_colapsed_wall"
        const val INN_TRAPDOOR = "varbit.burgh_inn_trapdoor"
        const val RUBBLE_COUNT = "varbit.burgh_inn_rubble_pile"
        const val RUBBLE_REMOVED = "varbit.burgh_rubble_removed"
        const val FLORIN_REFUSED = "varbit.burgh_florin_refused"
        const val STORE_ROOF = "varbit.burgh_store_roof"
        const val STORE_WALL = "varbit.burgh_store_wall"
        const val FOOD_TYPE = "varbit.burgh_food_type"
        const val CRATE_AXES_VAR = "varbit.burgh_axes_crate"
        const val CRATE_FOOD_VAR = "varbit.burgh_food_crate"
        const val CRATE_TINDERBOX_VAR = "varbit.burgh_tinderbox_crate"
        const val STORE_STOCKED = "varbit.burgh_store_stocked"
        const val BANK_BOOTH = "varbit.burgh_bank_booth_open"
        const val BANK_WALL = "varbit.burgh_bank_wall"
        const val BANK_TELLER = "varbit.burgh_bank_teller"
        const val FURNACE = "varbit.burgh_furnace_fix"
        const val TITHE_VISIBLE = "varbit.blood_tithe_visible"
        const val GADDERANKS_CHAT = "varbit.gadderanks_blood_tithe_chat"
        const val WISKIT_CHAT = "varbit.villager_blood_tithe_chat"
        const val JUVINATE_CHAT = "varbit.juve_blood_tithe_chat"
        const val HAMMER_GIVEN = "varbit.gadderanks_warhammer_give"
        const val IVAN_HELM = "varbit.burgh_ivan_armour_give_helm"
        const val IVAN_BODY = "varbit.burgh_ivan_armour_give_body"
        const val IVAN_LEGS = "varbit.burgh_ivan_armour_give_legs"
        const val IVAN_SICKLE = "varbit.ivan_sickle_give"
        const val IVAN_FOOD = "varbit.burgh_ivan_food_give"
        const val IVAN_FOOD_HEAL = "varbit.burgh_ivan_food_heal"
        const val AMBUSH_ROUTE = "varbit.juvinate_ambush_routetaken"
        const val AMBUSH_DEATHS = "varbit.juvinate_ambush_deaths"
        const val HIDEOUT_NPCS = "varbit.route_hideout_npcs"
        const val LIBRARY_TRAPDOOR = "varbit.burgh_temple_trapdoor"
        const val TOMB_BOARDS = "varbit.ivandis_tomb_boards"

        const val ROD_MOULD = "obj.burgh_rod_clay"
        const val SILVTHRILL = "obj.burgh_rod_command1"
        const val SILVTHRILL_ENCHANTED = "obj.burgh_rod_command2"
        const val ROD_FULL = "obj.burgh_rod_command_final_10"
        const val GADDERHAMMER = "obj.gadderanks_warhammer"
        const val CRATE = "obj.burgh_generalstore_crate"
        const val LIBRARY_KEY = "obj.burgh_key"
        const val SLEEPING_SEVEN = "obj.burgh_book_sevenwarriors"
        const val HISTORIES = "obj.burgh_book_historiesofhallowland"
        const val MODERN_MORYTANIA = "obj.burgh_book_moderndaymorytania"
        const val DUSTY_SCROLL = "obj.burgh_efaritay_info_scroll"
        const val PLASTER_FRAGMENT = "obj.burgh_plaster_inscription_ivandis"

        const val HAMMER = "obj.hammer"
        const val IMCANDO_HAMMER = "obj.imcando_hammer"
        const val PLANK = "obj.woodplank"
        const val SWAMP_PASTE = "obj.swamppaste"
        const val STEEL_BAR = "obj.steel_bar"
        const val COAL = "obj.coal"
        const val TINDERBOX = "obj.tinderbox"
        const val BRONZE_AXE = "obj.bronze_axe"
        const val SPADE = "obj.spade"
        const val POT = "obj.pot_empty"
        const val BUCKET = "obj.bucket_empty"
        const val SOFT_CLAY = "obj.softclay"
        const val ROPE = "obj.rope"

        /** Every kind of nail; any mix counts towards a repair, the plainest used first. */
        val NAILS =
            listOf(
                "obj.nails_bronze",
                "obj.nails_iron",
                "obj.nails",
                "obj.nails_black",
                "obj.nails_mithril",
                "obj.nails_adamant",
                "obj.nails_rune",
            )

        /** The three buckets of rubble, one, two and three loads full. */
        val RUBBLE_BUCKETS = listOf("obj.burgh_rubble_bucket_1", "obj.burgh_rubble_bucket_2", "obj.burgh_rubble_bucket_3")

        const val VELIAF_HOLLOWS = "npc.route_veliaf_hurtz"
        const val POLMAFI_HOLLOWS = "npc.route_polmafi_ferdygris"
        const val RADIGAD_HOLLOWS = "npc.route_radigad_ponfit"
        const val IVAN_HOLLOWS = "npc.route_ivan_strom"
        const val VELIAF_BURGH = "npc.myq5_veliaf_child"
        const val POLMAFI_BURGH = "npc.myq5_polmafi_child"
        const val RADIGAD_BURGH = "npc.myq5_radigad_child"
    }
}

internal val Player.storeRoof: Int get() = vars[InAidOfTheMyrequeQuest.STORE_ROOF]
internal val Player.storeWall: Int get() = vars[InAidOfTheMyrequeQuest.STORE_WALL]
internal val Player.foodType: Int get() = vars[InAidOfTheMyrequeQuest.FOOD_TYPE]
internal val Player.bankBooth: Int get() = vars[InAidOfTheMyrequeQuest.BANK_BOOTH]
internal val Player.bankWall: Int get() = vars[InAidOfTheMyrequeQuest.BANK_WALL]
