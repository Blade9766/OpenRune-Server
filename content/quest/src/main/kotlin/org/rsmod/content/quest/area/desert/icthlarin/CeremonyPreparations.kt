package org.rsmod.content.quest.area.desert.icthlarin

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.BAG_OF_SALT
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.BUCKET
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.HOLY_SYMBOL
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.KNIFE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.LINEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.PILE_OF_SALT
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.SALTWATER_BUCKET
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.SAP_BUCKET
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_CEREMONY_STARTED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PREPARING_CEREMONY
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.WILLOW_LOGS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting the reconsecration ceremony ready: the Embalmer wants salt, tree sap and linen, and the
 * Carpenter needs willow logs to carve a new holy symbol. Whichever of them is helped last tells
 * the player that the High Priest has gone ahead and started the ceremony.
 *
 * Also the ways the ingredients are made: salt water from the lake north of the city, dried out
 * in the suntrap by the Embalmer's house, and sap cut from any common tree into a bucket.
 */
class CeremonyPreparations
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(EMBALMER) { startDialogue(it.npc) { embalmer() } }
        onOpNpcU(EMBALMER) { startDialogue(it.npc) { embalmer() } }
        onOpNpc3(EMBALMER) { openSpiceShop() }
        onOpNpc1(CARPENTER) { startDialogue(it.npc) { carpenter() } }
        onOpNpcU(CARPENTER) { startDialogue(it.npc) { carpenter() } }

        onOpLoc1(SALT_LAKE) { collectSaltWater() }
        onOpLocU(SALT_LAKE, BUCKET) { collectSaltWater() }
        onOpLoc1(SUNTRAP) { useSuntrap() }
        onOpLocU(SUNTRAP, SALTWATER_BUCKET) { useSuntrap() }
        for (tree in sapTrees()) {
            onOpLocU(tree, KNIFE) { collectSap() }
        }
    }

    /* The Embalmer */

    private suspend fun Dialogue.embalmer() {
        val stage = quest.stage(player)
        when {
            quest.isAccused(player) -> {
                chatNpc(angry, "Clear off you evil body snatcher! You won't get any of my wares.")
            }
            stage == STAGE_PREPARING_CEREMONY && !player.ilhMetEmbalmer -> embalmerAsksForSupplies()
            stage == STAGE_PREPARING_CEREMONY && !player.ilhEmbalmerSupplied -> embalmerCollects()
            stage == STAGE_PREPARING_CEREMONY -> {
                chatPlayer(happy, "Hi there.")
                chatNpc(
                    neutral,
                    "Oh, it's you. You've already gathered everything I need. I'm sure you have plenty " +
                        "of other things to be doing.",
                )
                chatPlayer(neutral, "I guess I do still need to speak to the carpenter.")
            }
            stage in STAGE_CEREMONY_STARTED until IcthlarinsLittleHelperQuest.STAGE_COMPLETE -> {
                chatPlayer(happy, "Hello.")
                chatNpc(neutral, "Do you need something?")
                chatPlayer(neutral, "No.")
                chatNpc(neutral, "Well I have lots to do, so good day.")
            }
            else -> {
                chatPlayer(happy, "Hi.")
                chatNpc(neutral, "Hello again. Do you need something?")
                chatPlayer(neutral, "No thanks.")
                chatNpc(neutral, "Well I'm afraid I can't stop to talk right now. Lots to do.")
            }
        }
    }

    private suspend fun Dialogue.embalmerAsksForSupplies() {
        chatPlayer(happy, "Hi there.")
        chatNpc(
            neutral,
            "Oh, it's you. I hear you're helping us now. To be honest, I'm still not thrilled to have " +
                "you around, but so be it.",
        )
        chatPlayer(
            neutral,
            "I guess that's probably the best I can ask for, given the situation. Anyway, the High " +
                "Priest said you needed help gathering some supplies for the ceremony.",
        )
        chatNpc(neutral, "That's right. I need three things, but the quarantine is making some of them hard to obtain.")
        explainSalt(first = true)
        explainSap(first = true)
        explainLinen(first = true)
        player.ilhMetEmbalmer = true
        if (handOverSupplies() > 0) {
            if (player.ilhEmbalmerSupplied) {
                embalmerSatisfied()
            } else {
                chatNpc(happy, "Well that was unexpected. Thank you.")
            }
        }
    }

    private suspend fun Dialogue.embalmerCollects() {
        chatPlayer(happy, "Hi there.")
        chatNpc(neutral, "Oh, it's you. Any luck obtaining those items?")
        val handed = handOverSupplies()
        when {
            player.ilhEmbalmerSupplied -> {
                embalmerSatisfied()
                return
            }
            handed > 0 -> chatNpc(happy, "Thank you.")
            else -> {
                chatPlayer(neutral, "I'm still working on it.")
                chatNpc(neutral, "Well you'd better get a move on.")
            }
        }
        while (true) {
            val options = buildList {
                if (!player.ilhGaveSap) add("Where do I get tree sap?" to TOPIC_SAP)
                if (!player.ilhGaveSalt) add("Where do I get salt?" to TOPIC_SALT)
                if (!player.ilhGaveLinen) add("Where do I get linen?" to TOPIC_LINEN)
                add("Okay, I'll get going." to TOPIC_LEAVE)
            }
            when (choose(options)) {
                TOPIC_SAP -> {
                    chatPlayer(quiz, "Where do I get tree sap?")
                    explainSap(first = false)
                }
                TOPIC_SALT -> {
                    chatPlayer(quiz, "Where do I get salt?")
                    explainSalt(first = false)
                }
                TOPIC_LINEN -> {
                    chatPlayer(quiz, "Where do I get linen?")
                    explainLinen(first = false)
                }
                else -> {
                    chatPlayer(neutral, "Okay, I'll get going.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Int>>): Int =
        when (options.size) {
            2 -> choice2(options[0].first, options[0].second, options[1].first, options[1].second)
            3 -> choice3(options[0].first, options[0].second, options[1].first, options[1].second, options[2].first, options[2].second)
            else ->
                choice4(
                    options[0].first, options[0].second,
                    options[1].first, options[1].second,
                    options[2].first, options[2].second,
                    options[3].first, options[3].second,
                )
        }

    private suspend fun Dialogue.explainSalt(first: Boolean) {
        val prefix = if (first) "First, I need some salt. " else ""
        chatNpc(
            neutral,
            prefix + "Normally I get mine from the salt lake just north of the city. Just fill a bucket " +
                "from the lake and then use the suntrap outside to separate the salt from the water.",
        )
    }

    private suspend fun Dialogue.explainSap(first: Boolean) {
        val prefix = if (first) "Next, I need some tree sap. " else ""
        chatNpc(
            neutral,
            prefix + "You can use a knife to extract some from a tree, but make sure you have a bucket " +
                "ready. Most coniferous evergreen trees should have some. They're the cone-shaped ones.",
        )
    }

    private suspend fun Dialogue.explainLinen(first: Boolean) {
        if (first) {
            chatNpc(
                neutral,
                "Finally, I need some linen. That one shouldn't be too hard. The local cloth salesman " +
                    "should have some.",
            )
        } else {
            chatNpc(neutral, "The local cloth salesman should have some for sale.")
        }
    }

    /** Hands over whichever of the three supplies the player is carrying; returns how many. */
    private suspend fun Dialogue.handOverSupplies(): Int {
        var handed = 0
        if (!player.ilhGaveSap && access.invDel(access.inv, SAP_BUCKET).success) {
            player.ilhGaveSap = true
            objbox(SAP_BUCKET, "You hand a bucket of sap to the embalmer.")
            handed++
        }
        if (!player.ilhGaveSalt) {
            val salt = listOf(PILE_OF_SALT, BAG_OF_SALT).firstOrNull { it in player.inv }
            if (salt != null && access.invDel(access.inv, salt).success) {
                player.ilhGaveSalt = true
                objbox(salt, "You hand some salt to the embalmer.")
                handed++
            }
        }
        if (!player.ilhGaveLinen && access.invDel(access.inv, LINEN).success) {
            player.ilhGaveLinen = true
            objbox(LINEN, "You hand a sheet of linen to the embalmer.")
            handed++
        }
        return handed
    }

    private suspend fun Dialogue.embalmerSatisfied() {
        chatNpc(happy, "Thank you. I think that's everything I need.")
        if (player.ilhGaveLogs) {
            ceremonyBegun()
        } else {
            chatPlayer(neutral, "Great. I guess I should go and see how the carpenter is getting on.")
        }
    }

    private fun ProtectedAccess.openSpiceShop() {
        if (!quest.isComplete(player)) {
            mes("The embalmer is far too busy to trade right now.")
            return
        }
        shops.open(
            player,
            SPICE_SHOP_TITLE,
            SPICE_SHOP,
            buyPercentage = SPICE_SHOP_BUY,
            sellPercentage = SPICE_SHOP_SELL,
            changePercentage = SPICE_SHOP_CHANGE,
        )
    }

    /* The Carpenter */

    private suspend fun Dialogue.carpenter() {
        val stage = quest.stage(player)
        when {
            quest.isAccused(player) -> {
                chatPlayer(happy, "Good day.")
                chatNpc(angry, "Get away from me you filthy grave robber.")
                chatPlayer(sad, "Oh... okay.")
            }
            stage == STAGE_PREPARING_CEREMONY && !player.ilhMetCarpenter -> carpenterNeedsWillow()
            stage == STAGE_PREPARING_CEREMONY && !player.ilhGaveLogs -> carpenterWaitsForWillow()
            stage == STAGE_PREPARING_CEREMONY -> carpenterCarving()
            stage in STAGE_CEREMONY_STARTED until IcthlarinsLittleHelperQuest.STAGE_CEREMONY_COMPLETE -> carpenterAfterPreparations()
            quest.isComplete(player) -> {
                chatPlayer(happy, "Hey there. How're you holding up?")
                chatNpc(neutral, "Not too bad. Still, I hope we can get rid of these spots soon.")
            }
            else -> {
                chatPlayer(happy, "Good day.")
                chatNpc(neutral, "Good day to you. Mind the locusts on your way out.")
            }
        }
    }

    private suspend fun Dialogue.carpenterNeedsWillow() {
        chatPlayer(happy, "Good day.")
        chatNpc(neutral, "Hello, adventurer. The High Priest says that you're here to help us.")
        chatPlayer(neutral, "That's right. He asked me to come and speak to you about a holy symbol for the ceremony.")
        chatNpc(neutral, "Ah yes, it needs to be made from willow. Thing is, there's a small problem with that.")
        chatPlayer(quiz, "Is this the part where you need me to get you something?")
        chatNpc(
            happy,
            "Wow, you're quick. I'm all out of willow, and due to the quarantine, I'm unable to leave " +
                "the city to get more. However, the High Priest mentioned that a special exception has " +
                "been made for you.",
        )
        player.ilhMetCarpenter = true
        if (WILLOW_LOGS !in player.inv) {
            chatPlayer(neutral, "Meaning I can leave the city and get you some. Alright, I'll be back soon.")
            return
        }
        chatPlayer(
            happy,
            "Well there's actually no need for me to go anywhere. I already have some willow logs here.",
        )
        takeWillow()
    }

    private suspend fun Dialogue.carpenterWaitsForWillow() {
        chatPlayer(happy, "Good day.")
        chatNpc(quiz, "Do you have that wood for me?")
        if (WILLOW_LOGS !in player.inv) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "I'll be here when you do.")
            return
        }
        takeWillow()
    }

    /**
     * The Carpenter carves while the player sees to the Embalmer; if the Embalmer has already been
     * seen to, the symbol is finished on the spot.
     */
    private suspend fun Dialogue.takeWillow() {
        if (access.invDel(access.inv, WILLOW_LOGS).failure) {
            return
        }
        player.ilhGaveLogs = true
        objbox(WILLOW_LOGS, "You give the carpenter some willow logs.")
        chatNpc(happy, "Thank you. Now, just give me a moment...")
        if (!player.ilhEmbalmerSupplied) {
            chatNpc(
                neutral,
                "Actually, this will take a little longer than I thought. Why don't you see to the " +
                    "embalmer in the meantime? I'm sure I'll have it ready by the time you're done.",
            )
            return
        }
        chatNpc(happy, "There we go. All done.")
        giveSymbol()
        ceremonyBegun()
    }

    private suspend fun Dialogue.carpenterCarving() {
        chatPlayer(happy, "Good day.")
        if (!player.ilhEmbalmerSupplied) {
            chatNpc(
                neutral,
                "Hello, adventurer. I'm still working on that symbol. Do you have something else you " +
                    "can do in the meantime?",
            )
            chatPlayer(neutral, "Yes, I need to help out the embalmer.")
            chatNpc(neutral, "Well you're probably best off doing that then. I'm sure I'll have it ready by the time you're done.")
            return
        }
        chatNpc(happy, "Hello, adventurer. The symbol is all finished now.")
        giveSymbol()
        ceremonyBegun()
    }

    private suspend fun Dialogue.carpenterAfterPreparations() {
        chatPlayer(happy, "Good day.")
        chatNpc(quiz, "Hello. Have you given the symbol to the High Priest yet?")
        if (HOLY_SYMBOL in player.inv || quest.stage(player) >= IcthlarinsLittleHelperQuest.STAGE_PRIEST_DEFEATED) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "Well you should as soon as possible. He'll be down in the pyramid by now.")
            return
        }
        chatPlayer(worried, "Actually, there's a slight problem with the symbol.")
        chatNpc(
            angry,
            "What? But I made it to exactly match the drawings from the High Priest. I can't believe this!",
        )
        chatPlayer(sad, "Oh, I'm sure the symbol itself is fine. The problem is... I lost it.")
        chatNpc(angry, "Agh, you fool! Is stupidity the newest plague in town?")
        chatPlayer(
            neutral,
            "Can you please calm down? I gave you plenty of willow. I'm sure there's some left over " +
                "for another symbol.",
        )
        chatNpc(neutral, "Fine. Just give me a moment...")
        chatNpc(neutral, "There we go. All done. Try not to lose this one.")
        access.invAdd(access.inv, HOLY_SYMBOL)
        objbox(HOLY_SYMBOL, "The carpenter gives you a holy symbol.")
        chatNpc(
            neutral,
            "Now you should take that to the High Priest as soon as possible. He'll be down in the " +
                "pyramid by now.",
        )
        chatPlayer(neutral, "Will do.")
    }

    private suspend fun Dialogue.giveSymbol() {
        access.invAdd(access.inv, HOLY_SYMBOL)
        objbox(HOLY_SYMBOL, "The carpenter gives you a holy symbol.")
    }

    /** Both preparations are done: the High Priest has not waited for the player. */
    private suspend fun Dialogue.ceremonyBegun() {
        quest.advanceTo(access, STAGE_CEREMONY_STARTED)
        chatNpc(
            neutral,
            "I imagine the High Priest will be down in the pyramid by now. You should take that to " +
                "him as soon as possible.",
        )
        chatPlayer(happy, "Will do. Thanks.")
    }

    /* Salt and sap */

    private suspend fun ProtectedAccess.collectSaltWater() {
        if (BUCKET !in inv) {
            mes("You need a bucket to collect the water.")
            return
        }
        anim(FILL_SEQ)
        delay(1)
        if (invReplace(inv, BUCKET, 1, SALTWATER_BUCKET).success) {
            soundSynth(FILL_SOUND)
            mes("You fill the bucket with salty water.")
        }
    }

    private suspend fun ProtectedAccess.useSuntrap() {
        if (SALTWATER_BUCKET !in inv) {
            mes("You don't have anything suitable to use with the suntrap.")
            return
        }
        anim(SCRAPE_SALT_SEQ)
        soundSynth(BOIL_OFF)
        delay(2)
        if (invReplace(inv, SALTWATER_BUCKET, 1, BUCKET).failure) {
            return
        }
        invAdd(inv, PILE_OF_SALT)
        mes("You pour the water into the suntrap and it quickly evaporates, leaving you with a pile of salt.")
    }

    private suspend fun ProtectedAccess.collectSap() {
        if (BUCKET !in inv) {
            mes("You need a bucket to catch the sap.")
            return
        }
        anim(COLLECT_SAP_SEQ)
        soundSynth(COLLECT_SAP)
        delay(3)
        if (invReplace(inv, BUCKET, 1, SAP_BUCKET).success) {
            mes("You cut the tree and allow some sap to drip into your bucket.")
        }
    }

    /**
     * The common trees and evergreens: every tree that gives plain logs when chopped. Their bark
     * yields the sap the embalmer wants.
     */
    private fun sapTrees(): List<ObjectServerType> {
        val logs = "obj.logs".asRSCM(RSCMType.OBJ)
        return ServerCacheManager.getObjects().values.filter { type ->
            type.paramOrNull(params.skill_productitem)?.id == logs &&
                type.actions.getOpOrNull(0) != null
        }
    }

    private companion object {
        const val EMBALMER = "npc.ics_little_embalmer"
        const val CARPENTER = "npc.ics_little_carpenter"
        const val SALT_LAKE = "loc.icthalarins_waters_edge"
        const val SUNTRAP = "loc.icthalarins_suntrap_centre"

        const val TOPIC_SAP = 1
        const val TOPIC_SALT = 2
        const val TOPIC_LINEN = 3
        const val TOPIC_LEAVE = 4

        const val SPICE_SHOP = "inv.contact_embalmer"
        const val SPICE_SHOP_TITLE = "The Spice Is Right"
        const val SPICE_SHOP_SELL = 150.0
        const val SPICE_SHOP_BUY = 25.0
        const val SPICE_SHOP_CHANGE = 1.0

        const val FILL_SEQ = "seq.human_pickupfloor"
        const val FILL_SOUND = "synth.liquid"
        const val SCRAPE_SALT_SEQ = "seq.scrape_salt"
        const val BOIL_OFF = "synth.boil_off"
        const val COLLECT_SAP_SEQ = "seq.human_collect_sap"
        const val COLLECT_SAP = "synth.collect_sap"
    }
}
