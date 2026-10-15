package org.rsmod.content.quest.area.desert.touristtrap.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapCoords
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.AL_SHABIM
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.BEDABIN_GUARD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.BEDABIN_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.BEDABIN_NOMAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.BRONZE_BAR
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.BRONZE_DART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.FEATHER
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.FLETCHING_REQ
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.HAMMER
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.PROTOTYPE_DART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.PROTOTYPE_DART_TIP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SMITHING_REQ
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_ANVIL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_CURTAIN
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_FINDING_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_FINISHED_DART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_GIVEN_BEDABIN_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_GIVEN_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_LEARNED_DARTS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_MADE_DART_TIP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_RETRIEVED_PLANS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_SHOWN_PLANS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.TECHNICAL_PLANS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.TENTI_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.ttAlZabaDebunked
import org.rsmod.content.quest.area.desert.touristtrap.ttAskedAlZaba
import org.rsmod.content.quest.area.desert.touristtrap.ttTentAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Bedabin camp west of the mining camp: Al Shabim, who wants Captain Siad's plans and has the
 * only Tenti pineapples in the desert, his nomads, and the tent with the experimental anvil.
 */
class Bedabin
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val shops: Shops,
    private val locRepo: LocRepository,
) : PluginScript() {
    private val tentDoorType: ObjectServerType =
        ServerCacheManager.getObject(TENT_DOOR.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $TENT_DOOR")

    private val openTentDoorType: ObjectServerType =
        ServerCacheManager.getObject(OPEN_TENT_DOOR.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $OPEN_TENT_DOOR")

    override fun ScriptContext.startup() {
        onOpNpc1(AL_SHABIM) { startDialogue(it.npc) { alShabim() } }
        onOpNpcU(AL_SHABIM) { event ->
            when (event.objType.internalName) {
                PROTOTYPE_DART -> startDialogue(event.npc) { showDart() }
                TECHNICAL_PLANS -> startDialogue(event.npc) { havePlans() }
                else -> mes("Nothing interesting happens.")
            }
        }

        onOpNpc1(BEDABIN_NOMAD) { startDialogue(it.npc) { nomad(it.npc) } }
        onOpNpc3(BEDABIN_NOMAD) { openShop(it.npc) }

        onOpNpc1(BEDABIN_GUARD) { startDialogue(it.npc) { tentGuard() } }
        onOpNpcU(BEDABIN_GUARD) { event ->
            if (event.objType.internalName == TECHNICAL_PLANS) {
                startDialogue(event.npc) { plansShownToGuard() }
            } else {
                startDialogue(event.npc) { chatNpc(neutral, "Sorry, but I have no use for that!") }
            }
        }
        onOpLoc1(TENT_DOOR) { tentDoor(it.loc) }

        onOpLoc1(ANVIL) { mes("To forge items use the metal you wish to work with the anvil.") }
        onOpLocU(ANVIL) { event -> useOnAnvil(event.objType.internalName) }
        onOpHeldU(PROTOTYPE_DART_TIP, FEATHER) { attachFeathers() }
        onOpHeld1(TECHNICAL_PLANS) {
            doubleobjbox(
                TECHNICAL_PLANS,
                HAMMER,
                "The plans look very technical! But you can see that this item will require a " +
                    "bronze bar and at least 10 feathers.",
            )
        }
    }

    /* Al Shabim */

    private suspend fun Dialogue.alShabim() {
        chatNpc(neutral, "Hello Effendi!")
        val stage = quest.stage(player)
        if (stage >= STAGE_LEARNED_DARTS) {
            chatNpc(happy, "Many thanks with your help previously Effendi!")
            if (stage < STAGE_GIVEN_PINEAPPLE && !ownsAnywhere(TENTI_PINEAPPLE)) {
                if (choice2("I am looking for a pineapple.", true, "What is this place?", false)) {
                    lookingForPineapple()
                } else {
                    whatIsThisPlace()
                }
                return
            }
            if (choice2("What is this place?", true, "Goodbye!", false)) whatIsThisPlace() else goodbye()
            return
        }
        if (stage >= STAGE_GIVEN_BEDABIN_KEY) {
            when {
                stage == STAGE_FINISHED_DART && PROTOTYPE_DART in player.inv -> {
                    chatNpc(happy, "Wonderful, I see you have made the new weapon!")
                    showDart()
                }
                TECHNICAL_PLANS in player.inv -> havePlans()
                !ownsAnywhere(BEDABIN_KEY) && (stage == STAGE_GIVEN_BEDABIN_KEY || !ownsAnywhere(TECHNICAL_PLANS)) ->
                    lostSomething(stage)
                else -> {
                    chatNpc(neutral, "How are things going Effendi?")
                    if (choice2("What is this place?", true, "Goodbye!", false)) whatIsThisPlace() else goodbye()
                }
            }
            return
        }
        chatNpc(
            neutral,
            "I am Al Shabim, greetings on behalf of the Bedabin nomads. Now... what can I do for " +
                "you?",
        )
        val option =
            when {
                stage == STAGE_FINDING_PINEAPPLE ->
                    choice3("I am looking for a pineapple.", 1, "What is this place?", 2, "Who are you?", 3)
                stage >= STAGE_STARTED && player.ttAskedAlZaba ->
                    choice3("Who are you?", 3, "I am looking for Al Zaba Bhasim.", 4, "What is this place?", 2)
                else -> choice2("Who are you?", 3, "What is this place?", 2)
            }
        when (option) {
            1 -> lookingForPineapple()
            2 -> whatIsThisPlace()
            3 -> whoAreYou()
            else -> alZabaBhasim()
        }
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(happy, "I am Al Shabim Effendi! I am the leader of the Bedabin peoples!")
        val option = choice3("Okay thanks!", 1, "What is there to do around here?", 2, "What is this place?", 3)
        when (option) {
            1 -> okayThanks()
            2 -> whatToDo()
            else -> whatIsThisPlace()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            neutral,
            "This is the home of the Bedabin. We're a peaceful tribe of desert dwellers. Some " +
                "idiots call us 'Tenti's', a childish name borne of ignorance.",
        )
        chatNpc(
            neutral,
            "We're renowned for surviving in the harshest desert climate. We also grow the " +
                "'Bedabin ambrosia.'... A pineapple of such delicious sumptiousness that it defies " +
                "description.",
        )
        chatNpc(neutral, "Take a look around our camp if you like!")
        if (choice2("Okay thanks!", true, "What is there to do around here?", false)) {
            okayThanks()
        } else {
            whatToDo()
        }
    }

    private suspend fun Dialogue.whatToDo() {
        chatPlayer(quiz, "What is there to do around here?")
        chatNpc(
            confused,
            "Well, we are all very busy most of the time, tending to the pineapples. They are " +
                "grown in a secret location, to stop thieves from raiding our most precious prize.",
        )
    }

    private suspend fun Dialogue.okayThanks() {
        chatPlayer(neutral, "Okay thanks!")
        chatNpc(happy, "Good day Effendi!")
    }

    private suspend fun Dialogue.goodbye() {
        chatPlayer(neutral, "Goodbye!")
        chatNpc(neutral, "Very well, good day Effendi!")
    }

    private suspend fun Dialogue.alZabaBhasim() {
        chatPlayer(quiz, "I am looking for Al Zaba Bhasim.")
        if (player.ttAlZabaDebunked) {
            chatNpc(bored, "I've already explained that he doesn't exist. Now, can we move on?")
            return
        }
        chatNpc(
            angry,
            "Huh! You have been talking to the guards at the mining camp. Or worse, that " +
                "cowardly mercenary captain. Al Zaba Bhasim does not exist, he is a figment of " +
                "their imagination!",
        )
        chatNpc(
            neutral,
            "Go back and tell this captain that if he wants to find this man he should search " +
                "for him personally. See how much of his own time he would like to waste.",
        )
        player.ttAlZabaDebunked = true
    }

    private suspend fun Dialogue.lookingForPineapple() {
        chatPlayer(quiz, "I am looking for a pineapple.")
        if (quest.stage(player) >= STAGE_LEARNED_DARTS) {
            chatNpc(neutral, "Here is another pineapple, try not to lose this one.")
            access.invAdd(access.inv, TENTI_PINEAPPLE)
            return
        }
        chatNpc(
            quiz,
            "Oh yes, well that is interesting. Our sweet pineapples are renowned throughout the " +
                "whole of Kharid! And I'll give you one if you do me a favour?",
        )
        chatPlayer(shifty, "Oh yes?")
        chatNpc(
            neutral,
            "Captain Siad at the mining camp is holding some secret information. It is very " +
                "important to us and we would like you to get it for us. It gives details of an " +
                "interesting, yet ancient weapon.",
        )
        chatNpc(
            neutral,
            "We would gladly share this information with you. All you have to do is gain access " +
                "to his private room upstairs. We have a key for the chest that contains this " +
                "information. Are you interested in our deal?",
        )
        if (!choice2("Yes, I'm interested.", true, "Not at the moment.", false)) {
            chatPlayer(neutral, "Not at the moment.")
            chatNpc(sad, "Very well Effendi!")
            return
        }
        chatPlayer(shifty, "Yes, I'm interested.")
        chatNpc(happy, "That's great Effendi!")
        if (access.invAdd(access.inv, BEDABIN_KEY).failure) {
            chatNpc(neutral, "You have no room for the key, Effendi. Come back when you do.")
            return
        }
        quest.advanceTo(access, STAGE_GIVEN_BEDABIN_KEY)
        objbox(BEDABIN_KEY, "Al Shabim gives you a key.")
        chatNpc(neutral, "Here is a copy of the key that should give you access to the chest.")
        chatNpc(
            happy,
            "Bring us back the plans inside the chest, they should be sealed. All haste to you " +
                "Effendi!",
        )
    }

    private suspend fun Dialogue.lostSomething(stage: Int) {
        chatNpc(neutral, "How are things going Effendi?")
        val lostBoth = stage > STAGE_GIVEN_BEDABIN_KEY
        val lost = if (lostBoth) "I've lost the key and the plans!" else "I've lost the key!"
        val option = choice3(lost, 1, "What is this place?", 2, "Goodbye!", 3)
        when (option) {
            1 -> {
                chatPlayer(sad, lost)
                if (!lostBoth) {
                    chatNpc(
                        angry,
                        "How very careless of you! Here is another key, don't lose it this time !",
                    )
                } else {
                    chatNpc(angry, "How very careless of you!")
                    mesbox("Al Shabim thinks for a moment.")
                    chatNpc(
                        confused,
                        "The Captain may have some new plans drawn up. Go back and see if you can " +
                            "collect them.",
                    )
                    chatNpc(neutral, "Here is the key you'll need for the chest!")
                }
                access.invAdd(access.inv, BEDABIN_KEY)
                objbox(BEDABIN_KEY, "Al Shabim gives you another key.")
            }
            2 -> whatIsThisPlace()
            else -> goodbye()
        }
    }

    private suspend fun Dialogue.havePlans() {
        if (quest.stage(player) >= STAGE_LEARNED_DARTS) {
            access.invDel(access.inv, TECHNICAL_PLANS)
            mesbox("Al Shabim takes the technical plans off you.")
            chatNpc(happy, "Thanks for the technical plans Effendi! We've been lost without them!")
            return
        }
        chatNpc(
            happy,
            "Aha! I see you have the plans. This is great! However, these plans do indeed look " +
                "very technical. My people have further need of your skills.",
        )
        chatNpc(
            neutral,
            "If you can help us to manufacture this item, we will share its secret with you. " +
                "Does this deal interest you effendi?",
        )
        if (!choice2("Yes, I'm very interested.", true, "No, sorry.", false)) {
            noSorry()
            return
        }
        chatPlayer(happy, "Yes, I'm very interested.")
        val hasBar = BRONZE_BAR in player.inv
        val hasFeathers = player.inv.count(FEATHER) >= FEATHERS
        val hasHammer = HAMMER in player.inv
        val missing =
            when {
                !hasBar && !hasFeathers && !hasHammer ->
                    "Great, we need the following items: a bar of pure bronze, 10 feathers and a " +
                        "hammer. Bring them to me and we'll continue to make the item."
                !hasBar && !hasFeathers ->
                    "Great, we need the following items: a bar of pure bronze and 10 feathers. " +
                        "Bring them to me and we'll continue to make the item."
                !hasFeathers && !hasHammer ->
                    "Great, I can see that you have a bar of bronze. Now we just need some " +
                        "feathers and a hammer before we can continue."
                !hasBar && !hasHammer ->
                    "Great, I can see that you have some feathers. Now we just need a bar of " +
                        "bronze and a hammer before we can continue."
                !hasFeathers ->
                    "Great, I can see that you have a bar of bronze. Now we just need some " +
                        "feathers before we can continue."
                !hasBar ->
                    "Great, I can see that you have some feathers. Now we just need a bar of " +
                        "bronze before we can continue."
                !hasHammer ->
                    "Great, I can see that you have some feathers and a bar of Bronze, but you " +
                        "just need a hammer now."
                else -> null
            }
        if (missing != null) {
            chatNpc(happy, missing)
            return
        }
        chatNpc(
            happy,
            "Great, I can see that you have some feathers, a bar of Bronze and a hammer. Are you " +
                "still willing to help make the weapon?",
        )
        if (!choice2("Yes, I'm kind of curious.", true, "No, sorry.", false)) {
            noSorry()
            return
        }
        chatPlayer(quiz, "Yes, I'm kind of curious.")
        chatNpc(
            neutral,
            "Okay Effendi, you need to follow the plans. You will need some special tools for " +
                "this... There is an anvil in the other tent. You have my permission to use it, " +
                "but show the plans to the guard.",
        )
        chatNpc(
            neutral,
            "You have the plans and all the items needed. You should be able to complete the " +
                "item on your own. Please bring me the item when it is finished.",
        )
        quest.advanceFrom(access, STAGE_RETRIEVED_PLANS, STAGE_SHOWN_PLANS)
    }

    private suspend fun Dialogue.noSorry() {
        chatPlayer(neutral, "No, sorry.")
        chatNpc(neutral, "As you wish Effendi!")
        chatNpc(neutral, "Come back if you change your mind!")
    }

    /**
     * The prototype dart earns the player the secret of dart making, six darts of their own, and
     * at last the pineapple; the plans and the chest key go back to the Bedabin.
     */
    private suspend fun Dialogue.showDart() {
        if (quest.stage(player) >= STAGE_LEARNED_DARTS) {
            access.invDel(access.inv, PROTOTYPE_DART)
            chatNpc(
                angry,
                "Where did you get this from Effendi! I'll have to confiscate this for your own " +
                    "safety!",
            )
            return
        }
        if (PROTOTYPE_DART !in player.inv) {
            return
        }
        objbox(PROTOTYPE_DART, "You show Al Shabim the prototype dart.")
        chatNpc(happy, "This is truly fantastic Effendi!")
        if (TECHNICAL_PLANS in player.inv) {
            chatNpc(neutral, "We will take the technical plans for the weapon as well.")
            access.invDel(access.inv, TECHNICAL_PLANS)
            objbox(TECHNICAL_PLANS, "You hand over the technical plans for the weapon.")
        }
        chatNpc(
            happy,
            "We are forever grateful for this gift. My advisors have discovered some secrets which " +
                "we will share with you.",
        )
        mesbox("Al Shabim's advisors show you some advanced techniques for making the new weapon.")
        if (BRONZE_DART !in player.inv) {
            chatNpc(
                happy,
                "Please accept this selection of six bronze throwing darts as a token of our " +
                    "appreciation.",
            )
            access.invAdd(access.inv, BRONZE_DART, BRONZE_DARTS)
            objbox(BRONZE_DART, "You receive six bronze throwing darts from Al Shabim.")
        }
        if (BEDABIN_KEY in player.inv) {
            chatNpc(neutral, "I'll take that key off your hands as well effendi! Many thanks!")
            access.invDel(access.inv, BEDABIN_KEY)
        }
        access.invDel(access.inv, PROTOTYPE_DART)
        quest.advanceTo(access, STAGE_LEARNED_DARTS)
        objbox(
            PROTOTYPE_DART,
            "<col=000080>*** Dart Construction ***</col> Congratulations! You can now construct " +
                "darts.",
        )
        chatNpc(happy, "Oh, and here is your pineapple!")
        access.invAdd(access.inv, TENTI_PINEAPPLE)
        objbox(TENTI_PINEAPPLE, "You receive a tasty looking pineapple from Al Shabim")
    }

    private fun Dialogue.ownsAnywhere(obj: String): Boolean =
        obj in player.inv || obj in player.worn || obj in access.bank

    /* The nomads */

    private suspend fun Dialogue.nomad(nomad: Npc) {
        chatNpc(neutral, "Hello Effendi! How can I help you?")
        while (true) {
            val option =
                if (quest.stage(player) == STAGE_FINDING_PINEAPPLE) {
                    choice5(
                        "What is this place?",
                        1,
                        "Do you know where I could get a tenti pineapple?",
                        4,
                        "Where is the Shantay Pass?",
                        2,
                        "What do you have to sell?",
                        3,
                        "Okay, thanks",
                        0,
                    )
                } else {
                    choice4(
                        "What is this place?",
                        1,
                        "Where is the Shantay Pass?",
                        2,
                        "What do you have to sell?",
                        3,
                        "Okay, thanks",
                        0,
                    )
                }
            when (option) {
                1 -> {
                    chatPlayer(quiz, "What is this place?")
                    chatNpc(
                        neutral,
                        "This is the camp of the Bedabin. Talk to our leader, Al Shabim, he'll be " +
                            "happy to chat. We can sell you very reasonably priced water...",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Where is the Shantay Pass?")
                    chatNpc(
                        neutral,
                        "It is North East of here effendi, across the trackless desert. It will be " +
                            "a thirsty trip, can I interest you in a drink?",
                    )
                }
                3 -> {
                    access.openShop(nomad)
                    return
                }
                4 -> {
                    chatPlayer(quiz, "Do you know where I could get a tenti pineapple?")
                    val who = if (access.isBodyTypeB()) "madam" else "sir"
                    chatNpc(
                        shifty,
                        "Oooh, $who is looking for a really very special item then! You must go " +
                            "and talk to our very illustrious leader, Al Shabim. He's located in " +
                            "the largest tent in our village.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks")
                    return
                }
            }
        }
    }

    private fun ProtectedAccess.openShop(nomad: Npc) {
        shops.open(player, nomad, SHOP_TITLE, SHOP_INV)
    }

    /* The anvil tent */

    private fun allowedInTent(stage: Int): Boolean =
        stage in STAGE_SHOWN_PLANS until STAGE_LEARNED_DARTS

    private suspend fun Dialogue.tentGuard() {
        val stage = quest.stage(player)
        if (stage >= STAGE_GIVEN_PINEAPPLE) {
            chatNpc(
                happy,
                "Sorry, but you can't use the tent without permission. But thanks for all your " +
                    "help with the Bedabin people.",
            )
            return
        }
        if (player.ttTentAccess && allowedInTent(stage)) {
            chatNpc(happy, "Oh, I remember you; yeah go on in. Hope the weapon construction is going okay.")
            access.ifClose()
            access.enterTent()
            return
        }
        chatNpc(angry, "Sorry, but you can't use the tent without permission. Orders of Al Shabim.")
        if (!allowedInTent(stage)) {
            return
        }
        if (TECHNICAL_PLANS !in player.inv) {
            refusedWithoutPlans()
            return
        }
        chatNpc(neutral, "Okay, you can go in, Al Shabim has told me about you.")
        player.ttTentAccess = true
        access.ifClose()
        access.enterTent()
    }

    private suspend fun Dialogue.refusedWithoutPlans() {
        chatNpc(neutral, "Sorry, no one is allowed to enter.")
        chatPlayer(neutral, "But Al Shabim said I could enter!")
        chatNpc(neutral, "I can only let someone in who has plans for the secret weapon.")
        if (TECHNICAL_PLANS in access.bank) {
            chatPlayer(sad, "Oh drat, I've gone and left those in my bank. I'll have to go and get them.")
        }
    }

    private suspend fun Dialogue.plansShownToGuard() {
        val stage = quest.stage(player)
        when {
            allowedInTent(stage) -> {
                chatNpc(neutral, "Okay, you can go in, Al Shabim has told me about you.")
                player.ttTentAccess = true
                access.ifClose()
                access.enterTent()
            }
            stage == STAGE_RETRIEVED_PLANS ->
                chatNpc(
                    neutral,
                    "Hmm, those plans look interesting. Go and show them to Al Shabim... I'm sure " +
                        "he'll be pleased to see them.",
                )
            stage >= STAGE_LEARNED_DARTS -> {
                access.invDel(access.inv, TECHNICAL_PLANS)
                chatNpc(
                    neutral,
                    "Sorry, but you can't use the tent without permission. But thanks for all your " +
                        "help with the Bedabin people. And we'll take those plans off your hands as " +
                        "well!",
                )
            }
            else -> chatNpc(neutral, "Sorry, but I have no use for that!")
        }
    }

    private suspend fun ProtectedAccess.tentDoor(door: BoundLocInfo) {
        arriveDelay()
        val inside = coords.z >= TouristTrapCoords.TENT_DOOR_Z
        if (inside) {
            mes("You walk back out of the tent.")
            passThrough(door, TouristTrapCoords.TENT_OUTSIDE)
            return
        }
        val stage = quest.stage(player)
        if (player.ttTentAccess && allowedInTent(stage)) {
            mes("You walk into the tent.")
            passThrough(door, TouristTrapCoords.TENT_INSIDE)
            return
        }
        startDialogue {
            when {
                stage >= STAGE_GIVEN_PINEAPPLE ->
                    chatNpcSpecific(
                        "Bedabin Nomad Guard",
                        BEDABIN_GUARD,
                        happy,
                        "Sorry, but you can't use the tent without permission. But thanks for all " +
                            "your help with the Bedabin people.",
                    )
                allowedInTent(stage) && TECHNICAL_PLANS in player.inv -> {
                    chatPlayer(neutral, "Al Shabim said I could enter, here are the plans!")
                    chatNpcSpecific("Bedabin Nomad Guard", BEDABIN_GUARD, happy, "Okay go ahead.")
                    player.ttTentAccess = true
                    access.ifClose()
                    access.mes("You walk into the tent.")
                    access.enterTent()
                }
                else ->
                    chatNpcSpecific(
                        "Bedabin Nomad Guard",
                        BEDABIN_GUARD,
                        confused,
                        "Sorry, this is a private tent, no one is allowed in. Orders of Al " +
                            "Shabim...",
                    )
            }
        }
    }

    private fun ProtectedAccess.enterTent() {
        soundSynth(SOUND_CURTAIN)
        telejump(TouristTrapCoords.TENT_INSIDE, TeleportType.Exempt)
        val door = locRepo.findExact(TENT_DOOR_COORDS, tentDoorType) ?: return
        locRepo.change(door, openTentDoorType, TENT_DOOR_OPEN_TICKS)
    }

    /** The curtain is held open for a moment; swapping it ends the script, so it goes last. */
    private fun ProtectedAccess.passThrough(door: BoundLocInfo, dest: CoordGrid) {
        soundSynth(SOUND_CURTAIN)
        telejump(dest, TeleportType.Exempt)
        locRepo.change(door, openTentDoorType, TENT_DOOR_OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.useOnAnvil(obj: String?) {
        if (obj != BRONZE_BAR && obj != TECHNICAL_PLANS) {
            if (obj != null && obj.endsWith("_bar")) {
                mesbox("You're not sure what experimental techniques to use with this metal.")
            } else {
                mes("Nothing interesting happens.")
            }
            return
        }
        if (ownsAnywhere(PROTOTYPE_DART_TIP)) {
            objbox(
                PROTOTYPE_DART_TIP,
                "You have already made the prototype dart tip. You don't need to make another one.",
            )
            return
        }
        if (ownsAnywhere(PROTOTYPE_DART)) {
            objbox(
                PROTOTYPE_DART,
                "You have already made the prototype dart. You don't need to make another one.",
            )
            return
        }
        if (BRONZE_BAR !in inv) {
            objbox(BRONZE_BAR, "You need a bronze bar to make this weapon.")
            return
        }
        if (HAMMER !in inv) {
            objbox(HAMMER, "You need a hammer to work anything on the anvil.")
            return
        }
        if (TECHNICAL_PLANS !in inv) {
            mesbox(
                "This anvil is experimental... You need detailed plans of the item you want to " +
                    "make in order to use it.",
            )
            return
        }
        objbox(TECHNICAL_PLANS, "Do you want to follow the technical plans ?")
        val tryIt = choice2("Yes. I'd like to try.", true, "No, not just yet.", false, title = "Follow the technical plans?")
        if (!tryIt) {
            return
        }
        if (stat("stat.smithing") < SMITHING_REQ) {
            mesbox("You need level $SMITHING_REQ in smithing before you can attempt this.")
            return
        }
        mesbox("You begin experimenting in forging the weapon...")
        ifClose()
        repeat(HAMMER_BLOWS) {
            anim(SMITHING_SEQ)
            soundSynth(SOUND_ANVIL)
            delay(2)
        }
        if (!statRandom("stat.smithing", FORGE_LOW, FORGE_HIGH, 0)) {
            if (random.randomBoolean()) {
                invDel(inv, BRONZE_BAR)
                objbox(
                    BRONZE_BAR,
                    "You waste the bronze bar through an unlucky accident. But you think you know " +
                        "where you went wrong and perhaps next time you'll be successful.",
                )
            } else {
                mesbox(
                    "Through an unlucky accident you were not able to make the dart tip, but you " +
                        "think you know where you went wrong and perhaps next time you'll succeed.",
                )
            }
            return
        }
        if (invDel(inv, BRONZE_BAR).failure) {
            return
        }
        invAdd(inv, PROTOTYPE_DART_TIP)
        quest.advanceFrom(this, STAGE_SHOWN_PLANS, STAGE_MADE_DART_TIP)
        objbox(
            PROTOTYPE_DART_TIP,
            "You follow the plans carefully, and after some careful work, you finally manage to " +
                "forge a sharp, pointed... dart tip.",
        )
        mesbox(
            "You study the technical plans even more... You need to attach feathers to the tip to " +
                "complete the weapon.",
        )
    }

    private suspend fun ProtectedAccess.attachFeathers() {
        if (ownsAnywhere(PROTOTYPE_DART)) {
            objbox(
                PROTOTYPE_DART,
                "You have already made the prototype dart. You don't need to make another one.",
            )
            return
        }
        if (inv.count(FEATHER) < FEATHERS) {
            mes("You need at least ten feathers to make this item.")
            return
        }
        objbox(FEATHER, "You try to attach feathers to the bronze dart tip.")
        if (stat("stat.fletching") < FLETCHING_REQ) {
            mesbox("You need a fletching level of at least $FLETCHING_REQ to complete this.")
            return
        }
        if (!statRandom("stat.fletching", FLETCH_LOW, FLETCH_HIGH, 0)) {
            invDel(inv, FEATHER, FEATHERS)
            mesbox(
                "An unlucky accident causes you to waste the feathers. But you feel that you're " +
                    "close to making this item though.",
            )
            return
        }
        mesbox("Following the plans is tricky, but you persevere.")
        if (invDel(inv, FEATHER, FEATHERS).failure || invDel(inv, PROTOTYPE_DART_TIP).failure) {
            return
        }
        invAdd(inv, PROTOTYPE_DART)
        statAdvance("stat.fletching", DART_FLETCHING_XP)
        quest.advanceFrom(this, STAGE_MADE_DART_TIP, STAGE_FINISHED_DART)
        objbox(PROTOTYPE_DART, "You successfully attach the feathers to the dart tip.")
    }

    private fun ProtectedAccess.ownsAnywhere(obj: String): Boolean =
        obj in inv || obj in player.worn || obj in bank

    private companion object {
        const val TENT_DOOR = "loc.bedabin_tentdoor"
        const val OPEN_TENT_DOOR = "loc.bedabin_tentdoor_open"
        const val ANVIL = "loc.experimental_anvil"
        const val SHOP_TITLE = "Bedabin Village Bartering"
        const val SHOP_INV = "inv.bedabincampshop"

        val TENT_DOOR_COORDS = CoordGrid(3169, 3046)
        const val TENT_DOOR_OPEN_TICKS = 3

        const val FEATHERS = 10
        const val BRONZE_DARTS = 6
        const val HAMMER_BLOWS = 3
        const val SMITHING_SEQ = "seq.human_smithing"

        const val FORGE_LOW = 61
        const val FORGE_HIGH = 245
        const val FLETCH_LOW = 61
        const val FLETCH_HIGH = 254
        const val DART_FLETCHING_XP = 10.0
    }
}
