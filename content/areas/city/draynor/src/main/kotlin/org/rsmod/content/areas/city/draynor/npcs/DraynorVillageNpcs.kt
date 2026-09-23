package org.rsmod.content.areas.city.draynor.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseWoodcuttingLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.city.draynor.npcs.DiangoHolidayItems.Companion.openHolidayItems
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorVillageNpcs @Inject constructor(private val shops: Shops) : PluginScript() {
    private var Player.bankJob by intVarBit("varbit.wom_bankjob")
    private var Player.metForester by intVarBit("varbit.forestry_forester_met")

    override fun ScriptContext.startup() {
        onOpNpc1("npc.wgs_lucien_spy") { startDialogue(it.npc) { shadyStranger() } }
        onOpNpc1("npc.wgs_spy2") { startDialogue(it.npc) { suspiciousOutsider() } }
        onOpNpc1("npc.leela") { startDialogue(it.npc) { leela() } }
        onOpNpc1("npc.wom_gossip") { startDialogue(it.npc) { missSchism() } }
        onOpNpc1("npc.wom_bankguard") { startDialogue(it.npc) { bankGuard() } }
        onOpNpc1("npc.rag_wine_merchant") { startDialogue(it.npc) { fortunato() } }
        onOpNpc3("npc.rag_wine_merchant") { openWineShop() }
        onOpNpc1("npc.pmod_town_crier_draynor") { startDialogue(it.npc) { townCrier() } }
        onOpNpc1("npc.seed_merchant") { startDialogue(it.npc) { olivia() } }
        onOpNpc3("npc.seed_merchant") { openSeedShop() }
        onOpNpc1("npc.forestry_forester") { startDialogue(it.npc) { forester() } }
        onOpNpc3("npc.forestry_forester") { openOmnishop(FORESTRY_SHOP) }
        onOpNpc1("npc.diary_queen") { startDialogue(it.npc) { twiggy() } }
        onOpNpc1("npc.aprilfoolshorsesalesman") { startDialogue(it.npc) { diango() } }
        onOpNpc3("npc.aprilfoolshorsesalesman") { openToyShop() }
    }

    private suspend fun Dialogue.shadyStranger() {
        chatPlayer(quiz, "Hello there. What are you doing here?")
        chatNpc(shifty, "Oh, nothing much. I'd just heard Draynor was a nice place to visit.")
        chatPlayer(confused, "Fair enough.")
    }

    private suspend fun Dialogue.suspiciousOutsider() {
        chatPlayer(quiz, "Hello there. What are you doing here?")
        chatNpc(quiz, "That's not really any of your business, is it?")
        chatPlayer(confused, "Fair enough.")
    }

    private suspend fun Dialogue.leela() {
        if (!QuestRequirements.hasCompleted(player, PRINCE_ALI_RESCUE)) {
            chatPlayer(quiz, "What are you waiting here for?")
            chatNpc(neutral, "That is no concern of yours, adventurer.")
            return
        }
        chatNpc(
            happy,
            "Al Kharid will forever owe you for your help in saving Prince Ali. It's good to know " +
                "that we have you as a friend.",
        )
        chatPlayer(quiz, "It's no problem. So how come you're still out here?")
        chatNpc(
            neutral,
            "We still don't know why Keli and her bandits took the Prince. I'm hoping I can find " +
                "out. The place where they imprisoned him seems a good starting point.",
        )
        chatPlayer(happy, "Well if you need help, you know where I am. Good luck.")
    }

    private suspend fun Dialogue.missSchism() {
        chatNpc(shocked, "Oooh, my dear, have you heard the news?")
        var asked = false
        while (true) {
            val options = buildList {
                add("Okay, tell me about the news." to SchismTopic.News)
                if (!asked) add("Who are you?" to SchismTopic.Who)
                add("I'm not talking to you, you horrible woman." to SchismTopic.Rude)
            }
            when (menu(options)) {
                SchismTopic.News -> return schismNews()
                SchismTopic.Who -> {
                    asked = true
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        happy,
                        "I, my dear, am a concerned citizen of Draynor Village. Ever since the " +
                            "Council allowed those farmers to set up their stalls here, we've " +
                            "had a constant flow of thieves and murderers through our fair " +
                            "village, and I decided",
                    )
                    chatNpc(happy, "that someone HAD to stand up and keep an eye on the situation.")
                    chatNpc(
                        happy,
                        "I also do voluntary work for the Draynor Manor Restoration Fund. We're " +
                            "campaigning to have Draynor Manor turned into a museum before the " +
                            "wet- rot destroys it completely.",
                    )
                    chatPlayer(neutral, "Right...")
                }
                SchismTopic.Rude -> {
                    chatPlayer(angry, "I'm not talking to you, you horrible woman.")
                    chatNpc(shocked, "Oooh.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.schismNews() {
        chatPlayer(bored, "Okay, tell me about the news.")
        chatNpc(shocked, "It's terrible, absolutely terrible! Those poor people!")
        chatPlayer(bored, "Okay, yeah.")
        chatNpc(
            shocked,
            "And who'd have ever thought such a sweet old gentleman would do such a thing?",
        )
        if (player.bankJob < BANKJOB_WATCHED) {
            chatPlayer(neutral, "I really don't know what you're talking about.")
            chatNpc(shocked, "Oooh, my dear, had you not heard?")
            chatPlayer(bored, "At this rate I don't think I want to know...")
            chatNpc(
                shocked,
                "Oh, you must quickly go and speak to the bank guard outside the bank. He'll " +
                    "tell you all about it, oooh, such a shock it was...",
            )
            chatPlayer(neutral, "...")
            return
        }
        chatPlayer(quiz, "Are we talking about the bank robbery?")
        chatNpc(shocked, "Oh yes, my dear. It was terrible! TERRIBLE!")
        chatNpc(
            quiz,
            "But tell me - have you been around here before, or are you new to these parts?",
        )
        val choice =
            menu(
                "I'm quite new." to 0,
                "I've been around here for ages." to 1,
                "I've had enough of talking to you." to 2,
            )
        when (choice) {
            0 -> {
                chatPlayer(neutral, "I'm quite new.")
                chatNpc(
                    neutral,
                    "Aah, perhaps you missed the excitement. It's that old man in this house " +
                        "here. Do you know him?",
                )
            }
            1 -> {
                chatPlayer(neutral, "I've been around here for ages.")
                chatNpc(
                    neutral,
                    "Ah, so you'd have seen the changes here. It's that old man in this house " +
                        "here. Do you know him?",
                )
            }
            else -> {
                chatPlayer(neutral, "I've had enough of talking to you.")
                chatNpc(neutral, "Maybe another time, my dear.")
                return
            }
        }
        chatPlayer(neutral, "Well, I know of him.")
        chatNpc(
            neutral,
            "When he first moved here, he didn't bring much. From the window you could see he " +
                "just had some old furniture and a few dusty ornaments.",
        )
        chatNpc(neutral, "Here, look at this picture:")
        chatNpc(
            neutral,
            "Also he always seemed so poor. When I went round to collect donations for the " +
                "Draynor Manor Restoration Fund, he couldn't spare them a penny!",
        )
        chatPlayer(quiz, "So he's redecorated?")
        chatNpc(neutral, "Well, just you look in there now!")
        chatNpc(
            shocked,
            "You see? It's full of jewellery and decorations! And all those expensive things " +
                "appeared just after the bank got robbed.",
        )
        chatNpc(
            shocked,
            "He changed his hat too - he used to wear a scruffy old black thing, but suddenly he " +
                "was wearing that party hat!",
        )
        chatPlayer(quiz, "So that's why you're telling people he was the bank robber?")
        chatNpc(
            shocked,
            "Oooh, my dear, I'm SURE of it! I went upstairs in his house once, while he was out " +
                "walking, and do you know what I found?",
        )
        chatPlayer(quiz, "A sign saying 'Trespassers will be prosecuted'?")
        chatNpc(
            shocked,
            "No, it was a telescope! It was pointing right at the bank! He was spying on the " +
                "bankers, planning the big robbery!",
        )
        chatNpc(
            neutral,
            "I bet if you go and look through it now, you'll find it's pointing somewhere " +
                "different now he's finished with the bank.",
        )
        chatPlayer(neutral, "I'd like to go now.")
        chatNpc(
            neutral,
            "Oh, really? Well, do keep an eye on him - I just KNOW he's planning something...",
        )
    }

    private suspend fun Dialogue.bankGuard() {
        chatNpc(neutral, "Yes?")
        when {
            player.bankJob >= BANKJOB_WATCHED -> bankGuardAfterRecording()
            player.bankJob == BANKJOB_KNOWN -> {
                chatPlayer(quiz, "Do you have any idea who robbed the bank yet?")
                bankGuardSuspect()
            }
            else -> bankGuardIntro()
        }
    }

    private suspend fun Dialogue.bankGuardIntro() {
        var deposited = false
        while (true) {
            val options = buildList {
                if (!deposited) add("Can I deposit my stuff here?" to GuardTopic.Deposit)
                add("That wall doesn't look very good." to GuardTopic.Wall)
                if (deposited) add(GUARD_LEAVE to GuardTopic.Leave)
                else add("Sorry, I don't want anything." to GuardTopic.Nothing)
            }
            when (menu(options, title = GUARD_MENU)) {
                GuardTopic.Deposit -> {
                    deposited = true
                    chatPlayer(quiz, "Hello. Can I deposit my stuff here?")
                    chatNpc(neutral, "No. I'm a security guard, not a bank clerk.")
                }
                GuardTopic.Wall -> return bankGuardWall()
                GuardTopic.Leave -> return bankGuardLeave()
                GuardTopic.Nothing -> {
                    chatPlayer(neutral, "Sorry, I don't want anything.")
                    chatNpc(neutral, "Ok.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.bankGuardWall() {
        chatPlayer(quiz, "That wall doesn't look very good.")
        chatNpc(neutral, "No, it doesn't.")
        val tell =
            menu(
                "Are you going to tell me what happened?" to true,
                GUARD_LEAVE to false,
                title = GUARD_MENU,
            )
        if (!tell) return bankGuardLeave()
        chatPlayer(quiz, "Are you going to tell me what happened?")
        chatNpc(neutral, "I could do.")
        chatPlayer(happy, "Ok, go on!")
        chatNpc(neutral, "Someone smashed the wall when they were robbing the bank.")
        if (player.bankJob < BANKJOB_KNOWN) player.bankJob = BANKJOB_KNOWN
        chatPlayer(shocked, "Someone's robbed the bank?")
        chatNpc(neutral, "Yes.")
        chatPlayer(shocked, "But... was anyone hurt? Did they get anything valuable?")
        chatNpc(neutral, "Yes, but we were able to get more staff and mend the wall easily enough.")
        chatNpc(neutral, "The Bank has already replaced all the stolen items that belonged to customers.")
        chatPlayer(shocked, "Oh, good... but the bank staff got hurt?")
        chatNpc(neutral, "Yes, but the new ones are just as good.")
        chatPlayer(neutral, "You're not very nice, are you?")
        chatNpc(neutral, "No-one's expecting me to be nice.")
        chatPlayer(confused, "Anyway... So, someone's robbed the bank?")
        chatNpc(neutral, "Yes.")
        chatPlayer(quiz, "Do you know who did it?")
        bankGuardSuspect()
    }

    private suspend fun Dialogue.bankGuardSuspect() {
        chatNpc(
            neutral,
            "We are fairly sure we know who the robber was. The security recording was damaged " +
                "in the attack, but it still shows his face clearly enough.",
        )
        chatPlayer(quiz, "You've got a security recording?")
        chatNpc(neutral, "Yes. Our insurers insisted that we install a magical scrying orb.")
        var askedWho = false
        while (true) {
            val options = buildList {
                add("Can I see the recording?" to true)
                if (askedWho) add(GUARD_LEAVE to false) else add("So who was the robber?" to null)
            }
            when (menu(options, title = GUARD_MENU)) {
                true -> return offerRecording()
                false -> return bankGuardLeave()
                null -> {
                    askedWho = true
                    chatPlayer(happy, "So who was the robber?")
                    chatNpc(neutral, "I can't disclose that information.")
                }
            }
        }
    }

    private suspend fun Dialogue.offerRecording() {
        chatPlayer(quiz, "Can I see the recording?")
        chatNpc(neutral, "I suppose so. But it's quite long.")
        val watch =
            menu(
                "That's ok, show me the recording." to true,
                "Thanks, maybe another day." to false,
                title = GUARD_MENU,
            )
        if (!watch) {
            chatPlayer(neutral, "Thanks, maybe another day.")
            chatNpc(neutral, "Ok.")
            return
        }
        chatPlayer(neutral, "That's ok, show me the recording.")
        chatNpc(
            neutral,
            "Alright... The bank's magical playback device will feed the recorded images into " +
                "your mind. Just shut your eyes.",
        )
        watchRecording()
    }

    private suspend fun Dialogue.watchRecording() {
        mesbox("You close your eyes and watch the recording...")
        player.bankJob = BANKJOB_WATCHED
        mesbox("End of recording.")
    }

    private suspend fun Dialogue.bankGuardAfterRecording() {
        val topic =
            menu(
                "The robber in the recording looked familiar." to 0,
                "Can I see that recording again, please?" to 1,
                "Sorry, I don't want anything." to 2,
                title = GUARD_MENU,
            )
        when (topic) {
            0 -> {
                chatPlayer(neutral, "The robber in the recording looked familiar.")
                chatNpc(neutral, "Oh, you recognised him too?")
                chatPlayer(neutral, "Yes, and I can tell you where he lives.")
                chatNpc(neutral, "Thanks, but we already know where to find our suspect.")
                chatPlayer(quiz, "So are you going to have him arrested?")
                chatNpc(
                    neutral,
                    "We're certainly keeping an eye on him. I'm afraid I can't give you any " +
                        "more details about the case.",
                )
                chatPlayer(neutral, "Fair enough.")
            }
            1 -> {
                chatPlayer(quiz, "Can I see that recording again, please?")
                chatNpc(neutral, "I'd like you to pay me 50 gp first.")
                if (access.inv.count(COINS) < RECORDING_FEE) {
                    chatPlayer(sad, "I'm not carrying that much.")
                    chatNpc(neutral, "Oh well, maybe another day.")
                    return
                }
                val pay =
                    menu("Ok, here's 50 gp." to true, "Thanks, maybe another day." to false)
                if (!pay) {
                    chatPlayer(neutral, "Thanks, maybe another day.")
                    chatNpc(neutral, "Ok.")
                    return
                }
                chatPlayer(neutral, "Ok, here's 50 gp.")
                if (access.invDel(access.inv, COINS, RECORDING_FEE).failure) return
                watchRecording()
            }
            else -> {
                chatPlayer(neutral, "Sorry, I don't want anything.")
                chatNpc(neutral, "Ok.")
            }
        }
    }

    private suspend fun Dialogue.bankGuardLeave() {
        chatPlayer(neutral, GUARD_LEAVE)
        chatNpc(neutral, "Good day, ${if (isLad()) "sir" else "ma'am"}.")
    }

    private suspend fun Dialogue.fortunato() {
        chatNpc(neutral, "Can I help you at all?")
        chatNpc(happy, "Ah! Good afternoon to you. I take it you have come for a refill?")
        if (menu("Yes" to true, "Not today" to false)) {
            chatPlayer(neutral, "Yes.")
            access.openWineShop()
        } else {
            chatPlayer(neutral, "Not today.")
        }
    }

    private fun ProtectedAccess.openWineShop() {
        shops.open(
            player = player,
            title = "Wine Shop.",
            shopInv = "inv.wine_vinegar_merchant",
            buyPercentage = 60.0,
            sellPercentage = 100.0,
            changePercentage = 2.0,
        )
    }

    private suspend fun Dialogue.townCrier() {
        chatNpc(happy, "Hello citizen!")
        if (menu("What do you do around here?" to true, "See you later." to false)) {
            chatPlayer(quiz, "What do you do around here?")
            chatNpc(
                happy,
                "I'm a Town Crier. It's my job to let people know of any recent news. After " +
                    "all, there's all sorts of things happening around here.",
            )
            chatPlayer(happy, "I see. See you later.")
        } else {
            chatPlayer(happy, "See you later.")
        }
        chatNpc(happy, "Until next time.")
    }

    private suspend fun Dialogue.olivia() {
        chatNpc(happy, "Would you like to trade in seeds?")
        val topic = menu("Yes" to 0, "No" to 1, "Where do I get rarer seeds from?" to 2)
        when (topic) {
            0 -> access.openSeedShop()
            1 -> chatPlayer(neutral, "No, thanks.")
            else -> {
                chatPlayer(quiz, "Where do I get rarer seeds from?")
                chatNpc(
                    neutral,
                    "The Master Farmers usually carry a few rare seeds around with them, " +
                        "although I don't know if they'd want to part with them for any price " +
                        "to be honest.",
                )
            }
        }
    }

    private fun ProtectedAccess.openSeedShop() {
        shops.open(
            player = player,
            title = "Draynor Seed Market",
            shopInv = "inv.seed_stall",
            buyPercentage = 60.0,
            sellPercentage = 120.0,
            changePercentage = 3.0,
        )
    }

    private suspend fun Dialogue.forester() {
        chatPlayer(neutral, "Hello there.")
        if (player.metForester == 0) {
            chatNpc(happy, "Greetings! Have you come to talk forestry?")
            chatPlayer(quiz, "Forestry?")
            chatNpc(
                happy,
                "That's right! In this day and age, we need those trained in the way of the " +
                    "forester now more than ever!",
            )
            chatPlayer(quiz, "Way of the forester? What do you mean?")
            chatNpc(
                happy,
                "You know, helping the woodland flourish with your fellow foresters! And of " +
                    "course, drinking plenty of tea to fuel yourself while you do so!",
            )
            chatPlayer(quiz, "Sounds interesting. How can I get started?")
            if (player.baseWoodcuttingLvl < FORESTRY_LEVEL) {
                chatNpc(
                    happy,
                    "Well, I'd recommend spending some more time in the forests before " +
                        "embarking on your forestry journey. Come back when you can fell a " +
                        "mighty oak!",
                )
                chatPlayer(happy, "I'll do that. Thanks for the information.")
                chatNpc(happy, "Good luck out there!")
                return
            }
            player.metForester = 1
            chatNpc(
                happy,
                "First, you'll need a forestry kit. Once you have one, all you need to do is chop " +
                    "some trees while you have it on you. The woodland will do the rest!",
            )
            chatNpc(
                happy,
                "Oh, and if you find any anima-infused bark while tending to the forests, I'll " +
                    "gladly trade with you. My shop has all sorts of useful items.",
            )
            chatPlayer(confused, "That's all rather vague...")
            chatNpc(
                happy,
                "It is, but I find it's better to let the forests do the talking. You'll " +
                    "understand once you get going. Speaking of which, would you like one of " +
                    "those kits now? I have them free of charge in my shop.",
            )
            chatPlayer(happy, "Yes please.")
            chatNpc(happy, "Excellent!")
            access.openOmnishop(FORESTRY_SHOP)
            return
        }
        chatNpc(happy, "Hello again! How goes your forestry journey?")
        val topic =
            menu(
                "I'd like to trade with you." to 0,
                "Could you tell me more about forestry?" to 1,
                "Pretty good, thanks." to 2,
            )
        when (topic) {
            0 -> {
                chatPlayer(neutral, "I'd like to trade with you.")
                chatNpc(happy, "Of course!")
                access.openOmnishop(FORESTRY_SHOP)
            }
            1 -> {
                chatPlayer(quiz, "Could you tell me more about forestry?")
                chatNpc(
                    happy,
                    "It's very simple. All you need is a forestry kit. I have them free of " +
                        "charge in my shop. Once you have one, just chop some trees while you " +
                        "have it on you. The woodland will do the rest!",
                )
                chatNpc(
                    happy,
                    "Oh, and if you find any anima-infused bark while tending to the forests, " +
                        "I'll gladly trade with you. My shop has all sorts of useful items.",
                )
                chatNpc(happy, "Speaking of which, would you like to trade now?")
                if (menu("Yes please." to true, "I'm good, thanks." to false)) {
                    chatPlayer(happy, "Yes please.")
                    chatNpc(happy, "Excellent!")
                    access.openOmnishop(FORESTRY_SHOP)
                } else {
                    chatPlayer(neutral, "I'm good, thanks.")
                    chatNpc(happy, "Very well.")
                }
            }
            else -> {
                chatPlayer(happy, "Pretty good, thanks.")
                chatNpc(happy, "I'm glad to hear it.")
            }
        }
    }

    private suspend fun Dialogue.twiggy() {
        chatNpc(happy, "Hey there!")
        if (access.random.of(0, 49) == 0 && !access.inv.isFull()) {
            chatNpc(happy, "Here, have some chocolate!")
            chatPlayer(happy, "Wow, thanks!")
            access.invAdd(access.inv, CHOCOLATE_BAR)
        }
        var askedWho = false
        while (true) {
            val options = buildList {
                if (!askedWho) add("Who are you?" to 0)
                add("That's a nice cape - please tell me about it." to 1)
                add("Goodbye!" to 2)
            }
            when (menu(options)) {
                0 -> {
                    askedWho = true
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        neutral,
                        "My name is Twiggy O'Korn and I am the Mistress of all of the " +
                            "Achievement Diaries. It is a pleasure to make your acquaintance.",
                    )
                    chatPlayer(happy, "Likewise.")
                }
                1 -> {
                    chatPlayer(happy, "That's a nice cape - please tell me about it.")
                    chatNpc(
                        happy,
                        "Ah yes, this is a Cape of Accomplishment, available only to those who " +
                            "complete every single Achievement Diary.",
                    )
                    return
                }
                else -> {
                    chatPlayer(happy, "Goodbye!")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.diango() {
        chatNpc(
            happy,
            "Howdy there partner! Want to see my spinning plates? Or did ya want a holiday item " +
                "back?",
        )
        val topic =
            menu(
                "Spinning plates?" to 0,
                "I'd like to check holiday items please!" to 3,
                "What else are you selling?" to 1,
                "I'm fine, thanks." to 2,
            )
        when (topic) {
            0 -> {
                chatPlayer(quiz, "Spinning plates?")
                chatNpc(
                    happy,
                    "That's right. There's a funny story behind them, their shipment was held up " +
                        "by thieves.",
                )
                chatNpc(
                    laugh,
                    "The crate was marked 'Dragon Plates'. Apparently they thought it was some " +
                        "kind of armour, when really it's just a plate with a dragon on it!",
                )
                access.openToyShop()
            }
            1 -> {
                chatPlayer(quiz, "What else are you selling?")
                access.openToyShop()
            }
            3 -> {
                chatPlayer(happy, "I'd like to check holiday items please!")
                chatNpc(happy, "Sure thing, let me just see what you're missing.")
                access.openHolidayItems()
            }
            else -> chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    private fun ProtectedAccess.openToyShop() {
        shops.open(
            player = player,
            title = "Diango's Toy Store",
            shopInv = "inv.aprilfoolshorseshop",
            buyPercentage = 50.0,
            sellPercentage = 150.0,
            changePercentage = 2.0,
        )
    }

    private fun Dialogue.isLad(): Boolean = player.appearance.bodyType == Constants.bodytype_a

    private enum class SchismTopic {
        News,
        Who,
        Rude,
    }

    private enum class GuardTopic {
        Deposit,
        Wall,
        Leave,
        Nothing,
    }

    private companion object {
        const val PRINCE_ALI_RESCUE = "quest_princealirescue"

        const val COINS = "obj.coins"
        const val CHOCOLATE_BAR = "obj.chocolate_bar"

        const val RECORDING_FEE = 50
        const val FORESTRY_LEVEL = 15
        const val FORESTRY_SHOP = "dbrow.forestry_shop"

        const val BANKJOB_KNOWN = 1
        const val BANKJOB_WATCHED = 2

        const val GUARD_MENU = "What would you like to say?"
        const val GUARD_LEAVE = "Alright, I'll stop bothering you now."
    }
}
