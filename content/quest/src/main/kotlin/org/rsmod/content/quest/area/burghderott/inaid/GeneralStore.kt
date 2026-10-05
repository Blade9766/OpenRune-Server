package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.BRONZE_AXE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE_AXES
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE_AXES_VAR
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE_FOOD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE_FOOD_VAR
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE_TINDERBOXES
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.CRATE_TINDERBOX_VAR
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.FOOD_TYPE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.GADDERHAMMER
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.HAMMER_GIVEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CRATE_GIVEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_FURNACE_LIT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_GADDERANKS_DEAD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_HELP_OFFERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_REPAIRS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_STOCKED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STORE_ROOF
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STORE_STOCKED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.CrateFood
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Aurel's general store: the roof and wall he wants mended, the crate of stock he sends the
 * player off to fill, and his shop, which only opens once the crate is delivered.
 *
 * Aurel picks mackerel or snails when he hands the crate over, and the choice is saved together
 * with the crate in one step; leaving the conversation before then picks again next time, as in
 * OSRS. What has gone into the crate lives on three cache varbits, so a lost crate is replaced
 * with its contents intact and nothing can be added twice or past what he asked for.
 */
class GeneralStore
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val citizens: BurghCitizens,
    private val repairs: Repairs,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(AUREL) { startDialogue(it.npc) { aurel(it.npc) } }
        onOpNpc3(AUREL) { trade(it.npc) }
        onOpLoc1(LADDER_UP) {
            arriveDelay()
            anim(CLIMB_UP_SEQ)
            delay(CLIMB_TICKS)
            telejump(BurghCoords.STORE_ROOF_ARRIVAL, TeleportType.Exempt)
        }
        onOpLoc1(LADDER_DOWN) {
            arriveDelay()
            anim(CLIMB_DOWN_SEQ)
            delay(CLIMB_TICKS)
            telejump(BurghCoords.STORE_LADDER_FOOT, TeleportType.Exempt)
        }
        onOpLoc1(ROOF_HOLE) {
            arriveDelay()
            with(repairs) {
                repair(
                    describe = "The roof looks like it's seen better days. The planks are all rotten and the nails have gone rusty.",
                    question = "Fix the roof?",
                    allowed = iaom.stage(player) >= STAGE_STORE_REPAIRS,
                    cost = Repairs.Cost(planks = 3, nails = 12),
                    varbit = STORE_ROOF,
                    done = "You use some wooden planks and nails to patch up the hole in the roof.",
                )
            }
        }
        onOpHeld1(CRATE) { searchCrate() }
        onOpHeldU(CRATE) { fillCrate(it.first, it.second, it.firstSlot, it.secondSlot) }
    }

    private suspend fun ProtectedAccess.trade(npc: Npc) {
        if (!iaom.isStoreOpen(player)) {
            startDialogue(npc) { chatNpc(sad, "I'm sorry my friend, but I can't open a store which has no stock.") }
            return
        }
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private suspend fun Dialogue.aurel(npc: Npc) {
        val stage = iaom.effectiveStage(player)
        when {
            stage < STAGE_HELP_OFFERED -> with(citizens) { talk(npc) }
            stage < STAGE_STORE_REPAIRS -> askForRepairs()
            stage < STAGE_CRATE_GIVEN -> afterRepairs(npc)
            stage < STAGE_STORE_STOCKED -> collectCrate()
            stage in STAGE_FURNACE_LIT until STAGE_GADDERANKS_DEAD -> with(citizens) { talk(npc) }
            else -> shopkeeper(npc)
        }
    }

    private suspend fun Dialogue.askForRepairs() {
        chatPlayer(happy, "Hey there, I'm finished with my project at the inn.")
        chatNpc(neutral, "Oh, that's good to know.")
        while (true) {
            when (
                choice4(
                    "What do you do here?", 1,
                    "What do you think of your town?", 2,
                    "I'd like to help fix up the town.", 3,
                    "I'd best be off.", 4,
                )
            ) {
                1 -> noStock()
                2 -> {
                    chatPlayer(quiz, "What do you think of your town?")
                    chatNpc(neutral, "It's barely a town. Not exactly a breeding ground for high society, is it?")
                }
                3 -> {
                    chatPlayer(happy, "I'd like to help fix up the town.")
                    chatNpc(happy, "Oh, that's very kind of you!")
                    chatNpc(neutral, "Perhaps you could help me fix up my general store. It certainly needs sorting out.")
                    chatPlayer(quiz, "What needs fixing?")
                    chatNpc(neutral, "Could you fix the roof, then the walls? Once that's done, come and have a chat with me about the store's stock.")
                    chatPlayer(neutral, "I'll take a look.")
                    iaom.advanceTo(access, STAGE_STORE_REPAIRS)
                    return
                }
                else -> {
                    chatPlayer(neutral, "I'd best be off.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.noStock() {
        chatPlayer(quiz, "What do you do here?")
        chatNpc(neutral, "I'm Aurel, the village store owner.")
        chatPlayer(quiz, "Can you open the shop for me please?")
        chatNpc(sad, "I'm sorry my friend, but I can't open a store which has no stock.")
    }

    private suspend fun Dialogue.afterRepairs(npc: Npc) {
        val repaired = iaom.isStoreRepaired(player)
        if (repaired) {
            chatNpc(happy, "Hi there. You've done a good job on my store! It's starting to look great!")
            chatPlayer(neutral, "Yeah, but I've only fixed the roof and the walls.")
        } else {
            chatPlayer(neutral, "Hello!")
            chatNpc(neutral, "Yeah, what can I do ya for?")
        }
        while (true) {
            when (
                choice4(
                    "What do you do here?", 1,
                    "What do you think of your town?", 2,
                    "What should I do now?", 3,
                    "I'd best be off.", 4,
                )
            ) {
                1 -> noStock()
                2 -> {
                    chatPlayer(quiz, "What do you think of your town?")
                    if (repaired) {
                        chatNpc(neutral, "It's looking better since you fixed my store, that's for sure. It would be good to get some stock in as well though.")
                        chatNpc(neutral, "Just some basic commodities, nothing too fancy like.")
                    } else {
                        chatNpc(neutral, "Well, it would look a lot nicer if someone fixed up the general store, I think.")
                    }
                }
                3 -> {
                    chatPlayer(quiz, "What should I do now?")
                    if (!repaired) {
                        chatNpc(neutral, "I thought you were fixing up my store? First fix the roof, then the walls. After that, come and have a chat with me.")
                    } else {
                        offerCrate()
                        return
                    }
                }
                else -> {
                    chatPlayer(neutral, "I'd best be off.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.offerCrate() {
        chatNpc(happy, "Well, you could stock the store for us. We'd really start looking like a town then.")
        chatPlayer(quiz, "What stock do you need?")
        val food = CrateFood.entries[access.random.of(0, CrateFood.entries.lastIndex)]
        val wanted = if (food == CrateFood.Mackerel) "raw mackerel" else "snails of any type"
        chatNpc(neutral, "I need 3 tinderboxes, 10 $wanted and 10 bronze axes.")
        if (access.inv.isFull()) {
            chatNpc(neutral, "I'd give you a crate to put them in, but you've got no room to carry it. Come back when you've made some space.")
            return
        }
        if (access.invAdd(access.inv, CRATE).failure) {
            return
        }
        VarPlayerIntMapSetter.set(player, FOOD_TYPE, food.varValue)
        iaom.advanceTo(access, STAGE_CRATE_GIVEN)
        objbox(CRATE, "Aurel gives you a crate.")
        chatNpc(happy, "There you go. You can put them in that.")
    }

    private suspend fun Dialogue.collectCrate() {
        if (iaom.isCrateFull(player) && CRATE in access.inv) {
            access.invDel(access.inv, CRATE)
            VarPlayerIntMapSetter.set(player, STORE_STOCKED, 1)
            iaom.advanceTo(access, STAGE_STORE_STOCKED)
            objbox(CRATE, "You show the full crate to Aurel who takes it off your hands.")
            chatNpc(happy, "Wow, that's fantastic. Well done! You're doing a great job.")
            mesbox("Aurel quickly puts the stock out around the shop.")
            chatNpc(neutral, "Hey, you've done such a great job for me. Would you consider helping us fix up the bank as well?")
            chatPlayer(neutral, "I can probably look into it.")
            return
        }
        chatPlayer(quiz, "What should I do now?")
        chatNpc(neutral, "You need to collect ${iaom.crateNeeds(player)}.")
        if (player.holdsAnywhere(CRATE)) {
            return
        }
        if (access.inv.isFull()) {
            chatNpc(neutral, "Lost the crate, have you? I'd give you another, but you've no room for it.")
            return
        }
        access.invAdd(access.inv, CRATE)
        objbox(CRATE, "Aurel gives you another crate. Whatever you'd already packed is still in it.")
    }

    private suspend fun Dialogue.shopkeeper(npc: Npc) {
        chatPlayer(quiz, "What do you do here?")
        chatNpc(neutral, "I'm Aurel, the village store owner.")
        val owed = iaom.reached(player, STAGE_GADDERANKS_DEAD) && iaom.stage(player) != 0 && player.vars[HAMMER_GIVEN] == 0
        val lost = player.vars[HAMMER_GIVEN] == 1 && !player.holdsAnywhere(GADDERHAMMER)
        when (
            choice4(
                "Can you open the shop for me please?", 1,
                "How did everyone come to be here?", 2,
                if (owed) "Do you still have Gadderanks' warhammer?" else "I've lost the Gadderhammer.", 3,
                "I'd best be off.", 4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Can you open the shop for me please?")
                shops.open(player, npc, SHOP_TITLE, SHOP_INV)
            }
            2 -> {
                chatPlayer(quiz, "How did everyone come to be here?")
                chatNpc(neutral, "We escaped from Meiyerditch, where we were being farmed as food for the vampyres.")
                chatPlayer(quiz, "Meiyerditch?")
                chatNpc(neutral, "That's right. It's part of the Sanguinesti region, east of here.")
            }
            3 -> gadderhammer(owed, lost)
            else -> chatPlayer(neutral, "I'd best be off.")
        }
    }

    private suspend fun Dialogue.gadderhammer(owed: Boolean, lost: Boolean) {
        when {
            owed -> {
                if (access.inv.isFull()) {
                    chatNpc(neutral, "I've been keeping it for you, but you've got no room to carry it.")
                    return
                }
                access.invAdd(access.inv, GADDERHAMMER)
                VarPlayerIntMapSetter.set(player, HAMMER_GIVEN, 1)
                objbox(GADDERHAMMER, "Aurel gives you Gadderanks' warhammer.")
            }
            lost -> {
                chatNpc(neutral, "I can make you another, but the materials don't come cheap. It'll be ${"%,d".format(GADDERHAMMER_PRICE)} coins.")
                if (!choice2("Okay, here you go.", true, "No thanks.", false)) {
                    chatPlayer(neutral, "No thanks.")
                    return
                }
                if (access.inv.isFull() && !access.inv.contains(COINS)) {
                    chatNpc(neutral, "You haven't got room for it.")
                    return
                }
                if (!access.invTakeFee(GADDERHAMMER_PRICE)) {
                    chatNpc(neutral, "You don't have enough coins.")
                    return
                }
                access.invAdd(access.inv, GADDERHAMMER)
                objbox(GADDERHAMMER, "Aurel sells you a Gadderhammer.")
            }
            else -> chatNpc(neutral, "I don't know what you mean.")
        }
    }

    private suspend fun ProtectedAccess.searchCrate() {
        val food = iaom.crateRequest(player)
        when {
            food == null || iaom.stage(player) >= STAGE_STORE_STOCKED -> mesbox("The crate is empty.")
            iaom.isCrateFull(player) -> mesbox("The crate is packed with stock. You should return it to the store.")
            player.vars[CRATE_AXES_VAR] + player.vars[CRATE_FOOD_VAR] + player.vars[CRATE_TINDERBOX_VAR] == 0 ->
                mesbox("The crate is empty.")
            else -> mesbox("More still needs adding to the crate. You need to collect ${iaom.crateNeeds(player)}.")
        }
    }

    private suspend fun ProtectedAccess.fillCrate(first: ItemServerType, second: ItemServerType, firstSlot: Int, secondSlot: Int) {
        val (item, slot) = if (first.isObj(CRATE)) second to secondSlot else first to firstSlot
        val food = iaom.crateRequest(player)
        if (food == null || iaom.stage(player) != STAGE_CRATE_GIVEN) {
            mes("Nothing interesting happens.")
            return
        }
        if (item.certtemplate > 0) {
            mes("Aurel wants real stock, not bank notes.")
            return
        }
        val slotFor = slotFor(item, food)
        if (slotFor == null) {
            mes("That's not something Aurel asked for.")
            return
        }
        val (varbit, needed, label) = slotFor
        val have = player.vars[varbit]
        if (have >= needed) {
            objbox(CRATE, "You've already filled the crate with $label.")
            return
        }
        val symbol = item.internalName
        val carried = inv.count(symbol)
        if (carried == 0 || inv[slot]?.isType(symbol) != true) {
            return
        }
        val all = carried > 1 &&
            startDialogueChoice("Would you like to fill the crate with $label?", "Yes, I'll fill the crate.", "No, I just want to add one.")
        val add = minOf(if (all) carried else 1, needed - have)
        if (invDel(inv, symbol, add).failure) {
            return
        }
        VarPlayerIntMapSetter.set(player, varbit, have + add)
        val left = needed - have - add
        mesbox("You add $add ${if (add == 1) objName(symbol).lowercase() else label} to the crate.")
        if (left > 0) {
            mesbox("You need another $left $label to fill this crate.")
        } else {
            mesbox("The crate is now full of $label.")
        }
    }

    private suspend fun ProtectedAccess.startDialogueChoice(title: String, yes: String, no: String): Boolean {
        var result = false
        startDialogue { result = choice2(yes, true, no, false, title = title) }
        return result
    }

    private fun slotFor(item: ItemServerType, food: CrateFood): Triple<String, Int, String>? =
        when {
            item.isObj(BRONZE_AXE) -> Triple(CRATE_AXES_VAR, CRATE_AXES, "bronze axes")
            item.isObj(TINDERBOX) -> Triple(CRATE_TINDERBOX_VAR, CRATE_TINDERBOXES, "tinderboxes")
            food.objs.any { item.isObj(it) } -> Triple(CRATE_FOOD_VAR, CRATE_FOOD, food.plural)
            else -> null
        }

    internal companion object {
        const val AUREL = "npc.burgh_general_store_owner"
        const val LADDER_UP = "loc.burgh_ladder_generalstore_up"
        const val LADDER_DOWN = "loc.burgh_ladder_generalstore_down"
        const val ROOF_HOLE = "loc.burgh_store_roof_broken"
        const val SHOP_TITLE = "Aurel's Supplies"
        const val SHOP_INV = "inv.burgh_general_store"
        const val COINS = "obj.coins"
        const val GADDERHAMMER_PRICE = 3000
        const val CLIMB_UP_SEQ = "seq.human_reachforladdertop"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val CLIMB_TICKS = 2
    }
}
