package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_EQUIPMENT
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_MET_ONEIROMANCER
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_POTION
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarPiece
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarTravel
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarAmuletIntro
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarCapeIntro
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarClothesIntro
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarRingIntro
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The people of the Moon Clan's town. Anyone who deals with them without a Seal of Passage is
 * sent back to Rellekka. Five of them help with the ceremonial clothing: Meteora trades her amulet
 * for the tiara a Suqah stole, Selene points the way to her grandfather's buried ring, Pauline
 * Polaris gives her cape to whoever guesses her real name, and Rimae Sirsalis tans Suqah hides.
 */
class MoonClan
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val travel: LunarTravel,
    private val shops: Shops,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        talk(BABA_YAGA) { babaYaga(it) }
        onOpNpc3(BABA_YAGA) { guarded(it.npc) { babaYagaShop(it.npc) } }
        talk(METEORA) { meteora() }
        talk(SELENE) { selene() }
        talk(PAULINE) { pauline() }
        talk(RIMAE) { rimae(it) }
        onOpNpc3(RIMAE) { guarded(it.npc) { clothesShop(it.npc) } }
        talk(MELANA) { melana(it) }
        onOpNpc3(MELANA) { guarded(it.npc) { generalStore(it.npc) } }
        talk(BOUQUET) { bouquet() }
        talk(GUARD) { chatNpc(neutral, "Move along. The Moon Clan's affairs are not yours.") }

        onApNpc1(BANKER) { if (isWithinApRange(it.npc, BANKER_REACH)) guarded(it.npc) { bankerTalk(it.npc) } }
        onOpNpc1(BANKER) { guarded(it.npc) { bankerTalk(it.npc) } }
        onApNpc3(BANKER) { if (isWithinApRange(it.npc, BANKER_REACH)) guarded(it.npc) { tryOpenBank() } }
        onOpNpc3(BANKER) { guarded(it.npc) { tryOpenBank() } }
        onApNpc4(BANKER) { if (isWithinApRange(it.npc, BANKER_REACH)) guarded(it.npc) { collectionBox() } }
        onOpNpc4(BANKER) { guarded(it.npc) { collectionBox() } }
    }

    private fun ScriptContext.talk(type: String, conversation: suspend Dialogue.(Npc) -> Unit) {
        onOpNpc1(type) { guarded(it.npc) { startDialogue(it.npc) { conversation(it.npc) } } }
    }

    private suspend fun ProtectedAccess.guarded(npc: Npc, action: suspend ProtectedAccess.() -> Unit) {
        if (with(travel) { expelWithoutSeal(npc) }) {
            return
        }
        action()
    }

    /* Baba Yaga */

    private suspend fun Dialogue.babaYaga(npc: Npc) {
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Ah! A visitor from a distant land! How can I help?")
        val stage = lunar.stage(player)
        val potion = stage == STAGE_MET_ONEIROMANCER || stage == STAGE_POTION
        val topic = if (stage == STAGE_MET_ONEIROMANCER) "The Oneiromancer told me you may be able to help..." else "About the potion you wanted me to make..."
        val choice =
            if (potion) {
                choice4("Have you got anything to trade?", 1, topic, 2, "It's a very interesting house you have here.", 3, "I'm good, thanks, bye.", 4)
            } else {
                choice3("Have you got anything to trade?", 1, "It's a very interesting house you have here.", 3, "I'm good, thanks, bye.", 4)
            }
        when (choice) {
            1 -> access.babaYagaShop(npc)
            2 -> if (stage == STAGE_MET_ONEIROMANCER) potionRecipe() else potionReminder()
            3 -> {
                chatPlayer(quiz, "It's a very interesting house you have here. Does it have a name?")
                chatNpc(happy, "Of course. It's Berty.")
                chatPlayer(confused, "Berty? Berty the chicken-legged house?")
                chatNpc(happy, "It has a certain ring to it, don't you think? Beeerteeee!")
                chatPlayer(shocked, "You're ins...")
                chatNpc(laugh, "Insane? Very.")
            }
            else -> chatPlayer(neutral, "I'm good, thanks, bye.")
        }
    }

    private suspend fun Dialogue.potionRecipe() {
        chatPlayer(neutral, "The Oneiromancer told me you may be able to help...")
        chatNpc(quiz, "With a potion?")
        chatPlayer(bored, "I'll never get used to people reading my mind. Yes, a potion of waking sleep. I need it to go to the...")
        chatNpc(neutral, "Dreamland.")
        chatPlayer(angry, "You know that's really quite rude!")
        chatNpc(neutral, "Sorry. Carry on. Are you sure you're ready for the dreamland? Some have gone mad from visiting their dreams.")
        chatPlayer(worried, "You really know how to put someone at ease. How do I make it?")
        recipe()
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "You'll also need a special vial, but you've no room for it. Come back when you do.")
            return
        }
        access.invAdd(access.inv, EMPTY_VIAL)
        lunar.advanceTo(access, STAGE_POTION)
        chatNpc(neutral, "You'll also need a special vial. Here's one.")
        objbox(EMPTY_VIAL, "Baba Yaga gives you an empty vial.")
    }

    private suspend fun Dialogue.potionReminder() {
        chatPlayer(neutral, "About the potion you wanted me to make...")
        recipe()
        chatPlayer(angry, "Stop pre-empting what I'm going to say!")
        chatNpc(laugh, "I think you shall just have to get used to it on this island.")
        val hasVial = VIALS.any { player.inv.contains(it) || access.bank.contains(it) }
        if (!hasVial && player.inv.freeSpace() > 0) {
            access.invAdd(access.inv, EMPTY_VIAL)
            chatNpc(neutral, "And since you've lost that vial, here is another.")
        }
    }

    private suspend fun Dialogue.recipe() {
        chatNpc(
            neutral,
            "One guam leaf, one marrentill and a Suqah tooth ground with a pestle and mortar. " +
                "Mix them in my special vial with water and BAM! There's your potion. The Suqah " +
                "monsters should give you all you need.",
        )
    }

    private fun ProtectedAccess.babaYagaShop(npc: Npc) {
        val inv = if (lunar.isComplete(player)) RUNE_SHOP_COMPLETE else RUNE_SHOP
        shops.open(player, npc, "Baba Yaga's Magic Shop", inv)
    }

    /* Meteora */

    private suspend fun Dialogue.meteora() {
        val amulet = LunarPiece.Amulet
        val needsAmulet = lunar.stage(player) == STAGE_EQUIPMENT && !amulet.given(player) && !owns(amulet.obj)
        when {
            needsAmulet && player.inv.contains(TIARA) -> {
                chatPlayer(happy, "Hi.")
                chatNpc(quiz, "Yo yo yo! You got me tiara yet?")
                chatPlayer(happy, "I have indeed. Want to swap?")
                chatNpc(happy, "Sure, yeah, gimme it.")
                access.invReplace(access.inv, TIARA, 1, amulet.obj)
                objbox(amulet.obj, "Meteora trades you her amulet for the tiara.")
            }
            needsAmulet && player.lunarAmuletIntro -> {
                chatPlayer(happy, "Hi.")
                chatNpc(quiz, "Yo yo yo! You got me tiara yet?")
                chatPlayer(quiz, "Not yet. Where did you say it went?")
                chatNpc(angry, "One o' dem Suqah nicked it. Hunt 'em down, an' hurry up, yeah?")
            }
            needsAmulet -> {
                chatPlayer(happy, "Hi. Say, that's a nice amulet you're wearing.")
                chatNpc(confused, "Wot?")
                chatPlayer(neutral, "On your head.")
                chatNpc(neutral, "Oh, dat. It's a replacement for me lucky tiara, innit.")
                chatPlayer(quiz, "What happened to your tiara?")
                chatNpc(sad, "One o' dem Suqah nicked it. Wish I 'ad it back, yeah.")
                player.lunarAmuletIntro = true
                chatPlayer(happy, "I'll see wot I can do, yeah? I mean, I'll see what I can do.")
                chatNpc(happy, "Gud.")
            }
            else -> {
                chatPlayer(happy, "Take me to your leader.")
                chatNpc(confused, "Wot?")
                chatPlayer(neutral, "Isn't that what you say when you meet a new race of people?")
                chatNpc(
                    neutral,
                    "We ain't got no leader, mate. We all got equal rights an' that. But if ya want " +
                        "an intro, go see the Oneiromancer. She's dead wise, yeah? Hangs out in the " +
                        "south-east of the island.",
                )
                chatPlayer(happy, "Okey dokey... yeah.")
                chatNpc(confused, "Huh?")
            }
        }
    }

    /* Selene */

    private suspend fun Dialogue.selene() {
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Greetings, sweetie. How can I help?")
        val ring = LunarPiece.Ring
        val wantsRing = lunar.stage(player) == STAGE_EQUIPMENT && !ring.given(player) && !owns(ring.obj)
        val ringTopic = if (player.lunarRingIntro) "About that ring..." else "I'm looking for a ring."
        val choice =
            if (wantsRing) {
                choice3("Can you tell me a bit about your people?", 1, ringTopic, 2, "I'm not going to talk to someone who calls me 'sweetie'!", 3)
            } else {
                choice2("Can you tell me a bit about your people?", 1, "I'm not going to talk to someone who calls me 'sweetie'!", 3)
            }
        when (choice) {
            1 -> {
                chatPlayer(quiz, "Can you tell me a bit about your people? What does the Moon Clan value?")
                chatNpc(neutral, "Knowledge of self, above all. It is where our strength comes from.")
                chatPlayer(happy, "I know things about myself. I know I like hot chocolate!")
                chatNpc(neutral, "Something a little deeper than that, dear. We also value a good listener. They say a wise man listens.")
                chatPlayer(neutral, "....")
                chatNpc(quiz, "Did you hear me?")
                chatPlayer(neutral, ".... I'm listening.")
                chatNpc(happy, "Most wise.")
            }
            2 -> ringClue()
            else -> {
                chatPlayer(angry, "I'm not going to talk to someone who calls me 'sweetie'!")
                chatNpc(sad, "No need to be rude, dear!")
            }
        }
    }

    private suspend fun Dialogue.ringClue() {
        if (!player.lunarRingIntro) {
            chatPlayer(quiz, "I'm looking for a ring to go with the ceremonial clothes for the dreamland.")
            chatNpc(
                neutral,
                "I haven't one of those, I'm afraid. But my grandfather did, and he may have buried " +
                    "it. He was a treasure hunter, and used to bury things around the island and " +
                    "give me clues to find them.",
            )
            player.lunarRingIntro = true
        } else {
            chatPlayer(quiz, "About that ring...")
        }
        chatNpc(
            neutral,
            "His clue reads: 'From the water's source, cross the western-most bridge, then travel " +
                "on south-west to a bloom of blue.'",
        )
        chatPlayer(neutral, "Thanks... I hope.")
    }

    /* Pauline Polaris */

    private suspend fun Dialogue.pauline() {
        val cape = LunarPiece.Cape
        val wantsCape = lunar.stage(player) == STAGE_EQUIPMENT && !cape.given(player) && !owns(cape.obj)
        if (!wantsCape) {
            chatPlayer(happy, "Hello.")
            chatNpc(happy, "Hello, dear. Lovely day for it, isn't it?")
            return
        }
        if (player.lunarCapeIntro) {
            chatPlayer(quiz, "Can I have another try at guessing your name? And the clue again?")
            chatNpc(neutral, "I don't like repeating myself, but very well...")
            guessName()
            return
        }
        chatPlayer(quiz, "I'm looking for ceremonial clothing so I can visit my dreams. Could you help?")
        chatNpc(
            neutral,
            "I know the ceremony well, and I do have a cape. But why should I give it to some " +
                "stranger off the street?",
        )
        chatPlayer(happy, "Because you're kind and like helping people in need?")
        chatNpc(neutral, "Nice try. I'll help someone who can show some insight, though. Try guessing my name!")
        when (choice3("Bob?", 1, "Pauline?", 2, "Tina?", 3)) {
            1 -> {
                chatPlayer(quiz, "Bob?")
                chatNpc(angry, "How rude!")
                chatPlayer(shifty, "Well, you do seem to have a moustache growing.")
                chatNpc(angry, "Get out of my sight!")
            }
            2 -> {
                chatPlayer(quiz, "Pauline?")
                chatNpc(
                    neutral,
                    "You'd think so, but no, that's my alias. I want my REAL name: one first name " +
                        "and a triple-barrelled surname.",
                )
                chatPlayer(worried, "Can't you give me a clue?")
                player.lunarCapeIntro = true
                guessName()
            }
            else -> {
                chatPlayer(quiz, "Tina?")
                chatNpc(laugh, "Ha! Nice try, but no.")
            }
        }
    }

    private suspend fun Dialogue.guessName() {
        chatNpc(
            neutral,
            "Change one letter of the word 'Dane' to get my first name. The first part of my " +
                "surname rhymes with 'wood', the second with 'magic' and the third with 'spade'.",
        )
        val wrong = WRONG_NAMES.shuffled().take(WRONG_NAME_CHOICES)
        val names = (wrong + REAL_NAME).shuffled()
        val guess = choice4(names[0], names[0], names[1], names[1], names[2], names[2], names[3], names[3], title = "Her name is?")
        if (guess != REAL_NAME) {
            chatNpc(sad, "Nope. Not even close.")
            return
        }
        chatNpc(happy, "That's it! Well done.")
        chatPlayer(happy, "Woo hoo! Not the most normal name, is it?")
        chatNpc(neutral, "It's not my fault my parents had overactive imaginations. Do you want this cape or not?")
        chatPlayer(happy, "Yes please!")
        access.invAddOrDrop(objRepo, LunarPiece.Cape.obj)
        objbox(LunarPiece.Cape.obj, "Pauline Polaris hands you a lunar cape.")
    }

    /* Rimae Sirsalis */

    private suspend fun Dialogue.rimae(npc: Npc) {
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Welcome to the clothes store. How might I help you?")
        val choice =
            choice4(
                "What can you sell me?",
                1,
                "You know the ceremonial clothes?",
                2,
                "It's a very interesting island you have here.",
                3,
                "I'm good, thanks, bye.",
                4,
            )
        when (choice) {
            1 -> access.clothesShop(npc)
            2 -> ceremonialClothes()
            3 -> {
                chatPlayer(happy, "It's a very interesting island you have here.")
                chatNpc(happy, "Thank you! We like it. Mind the Suqah if you wander outside the town, though.")
            }
            else -> chatPlayer(neutral, "I'm good, thanks, bye.")
        }
    }

    private suspend fun Dialogue.ceremonialClothes() {
        chatPlayer(quiz, "You know the ceremonial clothes?")
        if (!player.lunarClothesIntro) {
            chatNpc(happy, "I certainly do! A tricky set of garments to make.")
            chatPlayer(sad, "That's a shame, because I was going to ask how to get some.")
            chatNpc(
                neutral,
                "All I can help with is the tanning. The clothes are made from Suqah hides; bring " +
                    "them to me and I'll tan them, then you can craft the top, trousers, gloves and " +
                    "boots yourself with a needle and thread.",
            )
            chatNpc(
                neutral,
                "Interesting creatures, the Suqah. They thrive on the minerals in the mines under " +
                    "the town. We live in harmony with them, and in return for their hides we gave " +
                    "them magic to protect themselves.",
            )
            player.lunarClothesIntro = true
            return
        }
        chatNpc(quiz, "Got any Suqah hides you want me to tan?")
        val hides = player.inv.count(RAW_HIDE)
        if (hides == 0) {
            chatPlayer(quiz, "No, I don't have any on me. Where do I get them?")
            chatNpc(neutral, "Just kill some of the Suqah dotted around the outskirts of the island.")
            return
        }
        val cost = hides * TAN_PRICE
        chatPlayer(happy, "Yes please!")
        chatNpc(neutral, "At $TAN_PRICE gold per hide, that'll cost you $cost gp.")
        if (!choice2("That seems like a fair deal.", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        chatPlayer(happy, "That seems like a fair deal.")
        if (access.invDel(access.inv, COINS, cost).failure) {
            chatPlayer(sad, "Sorry, I don't seem to have enough money. I'll be right back!")
            return
        }
        access.invReplace(access.inv, RAW_HIDE, hides, TANNED_HIDE)
        access.spotanim(TAN_SPOT)
        chatNpc(happy, "A pleasure doing business with you!")
    }

    private fun ProtectedAccess.clothesShop(npc: Npc) {
        shops.open(player, npc, "Moon Clan Fine Clothes", CLOTHES_SHOP)
    }

    /* Melana, Bouquet and the bankers */

    private suspend fun Dialogue.melana(npc: Npc) {
        chatNpc(happy, "Welcome to the Moon Clan general store. Would you like to see my wares?")
        if (choice2("Yes, please.", true, "No, thanks.", false)) {
            access.generalStore(npc)
        }
    }

    private fun ProtectedAccess.generalStore(npc: Npc) {
        shops.open(player, npc, "Moon Clan General Store", GENERAL_STORE)
    }

    private suspend fun Dialogue.bouquet() {
        chatPlayer(happy, "Hello there.")
        chatNpc(
            happy,
            "Hello! Don't mind me, just tending the flowers. A little magic goes a long way with " +
                "plants, you know. They grow ever so much better when you ask them nicely.",
        )
    }

    private suspend fun ProtectedAccess.bankerTalk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Good day. How may I help you?")
            if (choice2("I'd like to access my bank account, please.", true, "Nothing, thanks.", false)) {
                access.tryOpenBank()
            }
        }
    }

    private fun ProtectedAccess.collectionBox() {
        ifOpenMainModal("interface.ge_collect")
    }

    private fun Dialogue.owns(obj: String): Boolean =
        player.inv.contains(obj) || obj in player.worn || access.bank.contains(obj)

    private companion object {
        const val BABA_YAGA = "npc.lunar_moonclan_baba_yaga"
        const val PAULINE = "npc.lunar_moonclan_monk1"
        const val METEORA = "npc.lunar_moonclan_monk2"
        const val MELANA = "npc.lunar_moonclan_monk3"
        const val SELENE = "npc.lunar_moonclan_monk4"
        const val RIMAE = "npc.lunar_moonclan_monk5"
        const val BANKER = "npc.lunar_moonclan_monk_man"
        const val BOUQUET = "npc.lunar_moonclan_monk_watering"
        const val GUARD = "npc.lunar_moonclan_guard"
        const val BANKER_REACH = 2

        const val RUNE_SHOP = "inv.lunar_runeshop"
        const val RUNE_SHOP_COMPLETE = "inv.lunar_runeshop_compl"
        const val CLOTHES_SHOP = "inv.lunar_clotheshop"
        const val GENERAL_STORE = "inv.lunar_general"

        const val EMPTY_VIAL = "obj.lunar_moonclan_liminal_vial_empty"
        val VIALS =
            listOf(
                EMPTY_VIAL,
                "obj.lunar_moonclan_liminal_vial_water",
                "obj.lunar_moonclan_liminal_guam",
                "obj.lunar_moonclan_liminal_marr",
                "obj.lunar_moonclan_liminal_guammarr",
                "obj.lunar_moonclan_liminal_vial_full",
            )
        const val TIARA = "obj.lunar_tiara"
        const val RAW_HIDE = "obj.suqka_hide_untanned"
        const val TANNED_HIDE = "obj.suqka_hide"
        const val COINS = "obj.coins"
        const val TAN_PRICE = 100
        const val TAN_SPOT = "spotanim.lunar_tan_leather"

        const val REAL_NAME = "Jane Blud-Hagic-Maid"
        const val WRONG_NAME_CHOICES = 3
        val WRONG_NAMES =
            listOf(
                "Dave Floob-Raid-Traid",
                "Jave Nag-Brabik-Faide",
                "June Slagit-Magic-Aade",
                "June Slagic-Lagic-Fade",
                "Dave Flood-Raid-Traid",
                "Jave Nud-Tragik-Baide",
                "June Rlagic-Ladic-Bade",
                "Dave Rlood-Raid-Thraid",
                "Jave Bud-Tragik-Daide",
            )
    }
}
