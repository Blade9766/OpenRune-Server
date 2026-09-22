package org.rsmod.content.quest.area.rellekka.fremenniktrials

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpContentMixedLocU
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.ASKELADDEN
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.BOUNCER
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.COINS
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.FOSSEGRIMEN
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.LALLI
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.OLAF
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.outerlanderRebuff
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Olaf the Bard's trial: make a lyre, have the Fossegrimen bless it, and sing an epic on the
 * longhall stage.
 *
 * The lyre is cut from the swaying tree east of Rellekka, carved with a knife and strung with
 * golden wool. The wool comes from Lalli the troll, who only parts with his golden fleece after the
 * player fools him with rock soup made from Askeladden's pet rock. A raw shark, manta ray, sea
 * turtle or bass left on the Fossegrimen's altar south-west of the town enchants the lyre, and the
 * longhall bouncer then lets the player backstage to perform.
 */
class BardsTrial
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val passages: GenericPassageScript,
    private val search: NpcSearch,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(OLAF) { startDialogue(it.npc) { olaf() } }
        onOpLoc1(SWAYING_TREE) { cutBranch() }
        onOpHeldU(KNIFE, BRANCH) { carveLyre() }
        onOpHeldU(GOLDEN_WOOL, UNSTRUNG_LYRE) { stringLyre() }
        onOpContentMixedLocU(SPINNING_WHEEL_CONTENT, GOLDEN_FLEECE) { spinFleece(it.loc) }
        onOpLocU(RELLEKKA_SPINNING_WHEEL, GOLDEN_FLEECE) {
            if (!quest.isComplete(player)) {
                mes("Only Fremenniks may use this spinning wheel.")
                return@onOpLocU
            }
            spinFleece(it.loc)
        }
        onOpHeld1(LYRE) { startDialogue { lyreUntuned() } }
        onOpHeld1(ENCHANTED_LYRE) { playEnchantedLyre() }

        onOpNpc1(LALLI) { startDialogue(it.npc) { lalli() } }
        onOpLocU(STEW) { addToStew(it.objType) }

        onOpNpc1(ASKELADDEN) { startDialogue(it.npc) { askeladden() } }
        onOpNpc3(ASKELADDEN) { claimPetRock() }
        onOpHeld1(PET_ROCK) { strokePetRock() }

        onOpNpc1(FOSSEGRIMEN) { startDialogue(it.npc) { fossegrimen() } }
        onOpLoc1(ALTAR) { summonFossegrimen() }
        onOpLocU(ALTAR) { offerOnAltar(it.objType) }

        onOpNpc1(BOUNCER) { startDialogue(it.npc) { bouncer() } }
        onOpLoc1(BACKSTAGE_DOOR) { backstageDoor(it.loc, it.type) }
    }

    /* Olaf */

    private suspend fun Dialogue.olaf() {
        if (!quest.isStarted(player)) {
            outerlanderRebuff()
            return
        }
        with(merchant) {
            withMerchantOption(MerchantContact.Olaf) {
                if (quest.hasVote(player, Trial.Bard)) olafVoted() else olafTrial()
            }
        }
    }

    private suspend fun Dialogue.olafVoted() {
        chatPlayer(quiz, "So can I rely on your vote with the council of elders in my favour?")
        chatNpc(
            happy,
            "You have a truly poetic soul! Anyone who can compose such a beautiful epic, and " +
                "then perform it so flawlessly can only bring good to our clan!",
        )
        chatPlayer(confused, "Erm... so that's a yes, then?")
        chatNpc(happy, "Absolutely! We must collaborate together on a duet sometime, don't you think?")
    }

    private suspend fun Dialogue.olafTrial() {
        if (!player.ftOlafStarted) {
            olafIntroduction()
            return
        }
        chatPlayer(quiz, "So how would I go about writing this epic?")
        chatNpc(
            neutral,
            "Well, first of all you are going to need an instrument. Like all true bards you are " +
                "going to have to make this yourself.",
        )
        chatPlayer(quiz, "How do I make an instrument?")
        chatNpc(
            neutral,
            "Well, it is a long and drawn-out process. Just east of this village there is an " +
                "unusually musical tree that can be used to make very high quality instruments.",
        )
        chatNpc(
            neutral,
            "Cut a piece from it, and then carve it into a special shape that will allow you to " +
                "string it. Using a knife as you would craft any other wooden object would be best " +
                "for this.",
        )
        chatPlayer(quiz, "Then what do I need to do?")
        chatNpc(
            neutral,
            "Next you will need to string your lyre. There is a troll to the South-east who has some " +
                "golden wool. I would not recommend using anything else to string your lyre with.",
        )
        chatPlayer(quiz, "Anything else?")
        chatNpc(
            neutral,
            "Well, when you have crafted your lyre you will need the blessing of the Fossegrimen to " +
                "tune your lyre to perfection before you even consider a public performance.",
        )
        chatPlayer(quiz, "Who or what is Fossegrimen?")
        chatNpc(
            neutral,
            "Fossegrimen is a lake spirit that lives just a little way South-west of this village. " +
                "Make her an offering of fish, and you will then be ready for your performance.",
        )
        chatNpc(
            neutral,
            "Make sure you give her a suitable offering however. If the offering is found to be " +
                "unworthy, then you may find yourself unable to play your lyre with any skill at all!",
        )
        chatPlayer(quiz, "So what would be a worthy offering?")
        chatNpc(neutral, "A raw shark, manta ray, or sea turtle should be sufficient as an offering.")
        chatPlayer(quiz, "Okay, what else do I need to do?")
        chatNpc(
            neutral,
            "When you have crafted your lyre and been blessed by Fossegrimen, then you will finally " +
                "be ready to make your performance to the revellers at the longhall.",
        )
        chatNpc(
            neutral,
            "Head past the bouncers and onto the stage, and then begin to play. If all goes well, " +
                "you should find the music spring to your mind and sing your own epic on the spot.",
        )
        chatNpc(
            neutral,
            "I will observe both you and the audience, and if you show enough talent, I will " +
                "happily vote in your favour at the council of elders.",
        )
        reminders()
    }

    private suspend fun Dialogue.olafIntroduction() {
        chatNpc(neutral, "Hello? Yes? You want something, outerlander?")
        chatPlayer(quiz, "Are you a member of the council?")
        chatNpc(
            happy,
            "Why, indeed I am, outerlander! My talents as an exemplary musician made it difficult for " +
                "them not to accept me! Why do you wish to know this?",
        )
        chatPlayer(
            neutral,
            "Well, I ask because I am currently doing the Fremennik trials so as to join your clan. I " +
                "need seven of the twelve council of elders to vote for me.",
        )
        chatNpc(
            happy,
            "Ahhh... and you wish to earn my vote? I will gladly accept you as a Fremennik should you " +
                "be able to prove yourself to have a little musical ability!",
        )
        chatPlayer(quiz, "So how would I do that?")
        chatNpc(
            happy,
            "Why, by playing in our longhall! All you need to do is impress the revellers there with " +
                "a few verses of an epic of your own creation!",
        )
        chatNpc(quiz, "So what say you outerlander? Are you up for the challenge?")
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(
                angry,
                "Forget it! This all sounds like way too much work for a stupid vote at a meeting! " +
                    "I'll find someone else instead.",
            )
            return
        }
        chatPlayer(
            happy,
            "Sure! This certainly sounds pretty easy to accomplish - I'll have your vote in no time!",
        )
        player.ftOlafStarted = true
        chatNpc(happy, "That is great news outerlander! We always need more music lovers here!")
    }

    private suspend fun Dialogue.reminders() {
        while (true) {
            chatNpc(quiz, "Is that clear enough, outerlander? Would you like me to repeat anything?")
            val topic =
                choice4(
                    "Remind me about crafting a lyre",
                    1,
                    "Remind me about Fossegrimen",
                    2,
                    "Remind me about playing on stage",
                    3,
                    "I don't need a reminder",
                    4,
                )
            when (topic) {
                1 -> {
                    chatPlayer(quiz, "Can you remind me about making a lyre again, please Olaf?")
                    chatNpc(
                        neutral,
                        "There are three parts to making the lyre; First you need to cut some " +
                            "special wood from the special musical tree which can be found just " +
                            "east of Rellekka.",
                    )
                    chatNpc(
                        neutral,
                        "When you have the wood, you will need to use a knife to craft it into a " +
                            "Lyre. When you have an unstrung lyre, you will need to string it before " +
                            "you can play it.",
                    )
                    chatNpc(
                        neutral,
                        "I would not use anything other than the golden string of Lalli, a troll " +
                            "who lives in a cave just to the South-east.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Can you remind me about getting a blessing from Fossegrimen again?")
                    chatNpc(
                        neutral,
                        "Certainly outerlander! Fossegrimen is a lake spirit who lives in the lake " +
                            "just to the South-west of here. She will bless you so that you may play " +
                            "your lyre,",
                    )
                    chatNpc(
                        neutral,
                        "if you give her a worthy offering. She will only accept raw fish, so place " +
                            "a raw fish on her pedestal, and she will bless you. The better the " +
                            "offering, the longer",
                    )
                    chatNpc(
                        neutral,
                        "the amount of time you will be blessed for. You will not be able to perform " +
                            "on stage without the blessing of the Fossegrimen.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Can you remind me about how to perform at the longhall again please?")
                    chatNpc(
                        neutral,
                        "Not a problem outerlander. When you have your lyre and have been blessed, " +
                            "you will be able to pass the bouncer at the stage entrance of the " +
                            "longhall.",
                    )
                    chatNpc(
                        neutral,
                        "He will not let you pass unless he believes you are ready to perform on " +
                            "stage. Simply go past him and onto the stage and use your lyre, and you " +
                            "will",
                    )
                    chatNpc(
                        neutral,
                        "begin to play to entertain the revellers. If all has gone well, you will " +
                            "feel the music come to your mind, unbidden, as you sing an epic of your " +
                            "exploits.",
                    )
                    chatNpc(
                        neutral,
                        "I will watch your performance from backstage, and also study how much the " +
                            "crowd likes you, and if I believe you have enough talent, then I will " +
                            "vote for you at the council.",
                    )
                }
                else -> {
                    chatPlayer(
                        happy,
                        "No thanks, I think that's all pretty straight forward. I'll go and make my " +
                            "lyre now.",
                    )
                    return
                }
            }
        }
    }

    /* Making the lyre */

    private suspend fun ProtectedAccess.cutBranch() {
        val axe = usableAxe()
        if (axe == null) {
            mes("You need an axe to cut a branch from this tree.")
            return
        }
        if (player.woodcuttingLvl < BRANCH_WOODCUTTING) {
            mes("You need a Woodcutting level of $BRANCH_WOODCUTTING to cut a branch from this tree.")
            return
        }
        if (player.inv.isFull()) {
            mesbox("You do not have enough free space in your inventory to chop this tree.")
            return
        }
        arriveDelay()
        anim(RSCM.getReverseMapping(RSCMType.SEQ, axe.param(params.skill_anim).id))
        soundSynth(CHOP_SOUND)
        delay(CUT_TICKS)
        resetAnim()
        invAdd(inv, BRANCH)
        mes("You cut a branch from the strangely musical tree.")
    }

    private fun ProtectedAccess.usableAxe(): ItemServerType? {
        val candidates = buildList {
            player.righthand?.let { add(getInvObj(it)) }
            for (obj in player.inv) {
                if (obj != null) {
                    add(getInvObj(obj))
                }
            }
        }
        return candidates
            .filter { it.isContentType(AXE_CONTENT) && it.internalName !in UNUSABLE_AXES }
            .filter { player.woodcuttingLvl >= it.param(params.levelrequire) }
            .maxByOrNull { it.param(params.levelrequire) }
    }

    private suspend fun ProtectedAccess.carveLyre() {
        if (player.craftingLvl < LYRE_CRAFTING) {
            mesbox("You need to have a Crafting level of $LYRE_CRAFTING to carve this branch into a lyre.")
            return
        }
        anim(FLETCH_SEQ)
        soundSynth(FLETCH_SOUND)
        delay(2)
        invReplace(inv, BRANCH, 1, UNSTRUNG_LYRE)
        mes("You craft an unstrung lyre out of the branch.")
    }

    private suspend fun ProtectedAccess.stringLyre() {
        if (player.fletchingLvl < LYRE_FLETCHING) {
            mesbox("You need to have a Fletching level of $LYRE_FLETCHING to string the lyre.")
            return
        }
        anim(FLETCH_SEQ)
        delay(2)
        invDel(inv, GOLDEN_WOOL)
        invReplace(inv, UNSTRUNG_LYRE, 1, LYRE)
        mes("You attach the golden strings to the lyre.")
    }

    private suspend fun ProtectedAccess.spinFleece(wheel: BoundLocInfo) {
        arriveDelay()
        faceSquare(wheel.coords)
        anim(SPIN_SEQ)
        locAnim(worldRepo, wheel, SPINNING_WHEEL_SEQ)
        soundSynth(SPIN_SOUND)
        delay(SPIN_TICKS)
        invReplace(inv, GOLDEN_FLEECE, 1, GOLDEN_WOOL)
        mes("You spin the golden fleece into a ball of golden wool.")
    }

    private suspend fun Dialogue.lyreUntuned() {
        chatPlayer(confused, "I really wouldn't know where to begin playing anything on this...")
    }

    /* Lalli and the rock soup */

    private suspend fun Dialogue.lalli() {
        chatPlayer(neutral, "Hello there.")
        when {
            quest.isComplete(player) -> lalliSellsFleece()
            !quest.isInProgress(player) || !player.ftOlafStarted || player.voted(Trial.Bard) ->
                chatNpc(
                    angry,
                    "Bah! Puny humans always try steal Lallis' golden apples! You go away now!",
                )
            player.ftStewTasted -> lalliGivesFleece()
            player.ftStewPlanned -> {
                chatNpc(angry, "Bah! Puny humans always try steal Lallis' golden apples! You go away now!")
                chatNpc(neutral, "Unless you got tasty food for Lalli. Then I might trade you for it.")
                chatPlayer(sad, "Actually I don't...")
                chatNpc(
                    laugh,
                    "Hah! Me knew it! You human just try trick Lalli into giving away golden apple! Me " +
                        "not as stupid as you look!",
                )
            }
            player.ftLalliTalked && PET_ROCK in player.inv -> lalliRefusesRock()
            else -> lalliFirstMeeting()
        }
    }

    private suspend fun Dialogue.lalliFirstMeeting() {
        chatNpc(angry, "Bah! Puny humans always try steal Lallis' golden apples! You go away now!")
        chatPlayer(
            neutral,
            "Actually, I'm not after your golden apples. I was wondering if I could have some golden " +
                "wool; I need it to string a lyre.",
        )
        chatNpc(
            laugh,
            "Ha! You not fool me human! Me am smart! Other trolls so jealous of how brainy I are, they " +
                "kick me out of camp and make me live down here in cave! But me have last funny!",
        )
        chatNpc(
            laugh,
            "Me find golden apples on tree and me build wall to stop anyone who not Lalli eating " +
                "lovely golden apples! Did me not tell you I are smart?",
        )
        chatPlayer(bored, "Yes, yes, you are incredibly clever. Now please can I have some golden wool?")
        chatNpc(
            angry,
            "Hum, me think you not really think I are clever. Me think you is trying trick Lalli. Me " +
                "not like you as much as other human. He give me present. I give him wool.",
        )
        val option =
            choice3(
                "Other human?",
                1,
                "No, honest, you're REALLY clever.",
                2,
                "Can I give you a present?",
                3,
            )
        when (option) {
            1 -> {
                chatPlayer(
                    quiz,
                    "Other human? You mean someone else has been there and you gave them wool?",
                )
                chatNpc(
                    laugh,
                    "Human call itself Askeladden! It not trick Lalli, Lalli do good deal with human! " +
                        "Stupid human get some dumb wool, but did not get golden apples!",
                )
                player.ftLalliTalked = true
                chatPlayer(neutral, "I see... okay, well, bye!")
            }
            2 -> {
                chatPlayer(
                    happy,
                    "No, honest, you're REALLY clever. Why, I could not contemplate a mere chicanery " +
                        "with an advanced intellect such as you currently present.",
                )
                chatNpc(
                    angry,
                    "Grrr! Why you insult me stupid human? Me know it are because you jealous of " +
                        "Lalli's huge brain and you try steal apples!",
                )
            }
            else -> {
                chatPlayer(
                    quiz,
                    "Can I give you a present? Or maybe exchange you something for some wool?",
                )
                chatNpc(
                    laugh,
                    "Ha! You stupid human! You think you trick Lalli? Me know you want my golden " +
                        "apples Well! You not get them! HAHAHA!",
                )
                chatPlayer(confused, "Erm... okay then.")
            }
        }
    }

    private suspend fun Dialogue.lalliRefusesRock() {
        chatNpc(angry, "Bah! Puny humans always try steal Lallis' golden apples! You go away now!")
        chatPlayer(happy, "Wait! I have something that might interest you... a pet rock!")
        chatNpc(
            angry,
            "Hah! You think me stupid or something human? Me already got one! Me don't want lotta " +
                "baby rocks either, so me don't want another one around the place!",
        )
        chatPlayer(sad, "Please... all I want is some of your golden wool...")
        chatNpc(
            angry,
            "Stupid human think he can trick me into giving away some golden apples! Hah! Golden " +
                "apples is all me got to eat, you not get them, no way!",
        )
        chatPlayer(
            shifty,
            "Hmm... So you're hungry? I think I will have the perfect thing for you to eat... I just " +
                "need to get myself an onion, a potato and a cabbage...",
        )
        player.ftStewPlanned = true
        mesbox(
            "You have a cunning plan to trick this troll. You need your pet rock, a cabbage, a potato " +
                "and an onion.",
        )
    }

    private suspend fun Dialogue.lalliGivesFleece() {
        chatNpc(
            happy,
            "Your soup very tasty, human! But me still not want trade golden apples for your stone. " +
                "Me think pet rock get jealous.",
        )
        chatPlayer(
            angry,
            "I. DON'T. WANT. ANY. GOLDEN. APPLES. ALL. I. WANT. IS. A. GOLDEN. FLEECE.",
        )
        chatNpc(
            sad,
            "Gee, sorry human, all you have do is ask, me not need you to shout. You act like you " +
                "think Lalli am stupid or something...",
        )
        if (GOLDEN_FLEECE in player.inv) {
            access.mes("You already have the golden fleece.")
            return
        }
        chatNpc(
            laugh,
            "Here you go. Hah! Me trick you human! All you got is worthless golden fleece! Me got very " +
                "rare soup-making stone!",
        )
        access.invAdd(access.inv, GOLDEN_FLEECE)
        objbox(GOLDEN_FLEECE, "Lalli gives you some golden fleece.")
        chatPlayer(happy, "Glad you're happy Lalli!")
    }

    private suspend fun Dialogue.lalliSellsFleece() {
        chatNpc(
            neutral,
            "Human back! Lalli have more golden fleece, but Lalli not stupid no more. Golden fleece " +
                "cost $FLEECE_PRICE gold!",
        )
        if (!choice2("Buy some golden fleece", true, "No thanks", false)) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        if (player.inv.count(COINS) < FLEECE_PRICE) {
            chatPlayer(sad, "I don't have enough money.")
            return
        }
        if (player.inv.isFull() && player.inv.count(COINS) != FLEECE_PRICE) {
            access.mes("You don't have enough inventory space.")
            return
        }
        access.invDel(access.inv, COINS, FLEECE_PRICE)
        access.invAdd(access.inv, GOLDEN_FLEECE)
        objbox(GOLDEN_FLEECE, "Lalli sells you some golden fleece.")
    }

    private suspend fun ProtectedAccess.addToStew(obj: ItemServerType) {
        val ingredient = STEW_INGREDIENTS.firstOrNull { it.obj.asRSCM(RSCMType.OBJ) == obj.id }
        if (ingredient == null || !player.ftStewPlanned || player.ftStewTasted) {
            mes("Nothing interesting happens.")
            return
        }
        if (ingredient.added(player)) {
            mes("There is already ${ingredient.article} ${ingredient.name} in the stew.")
            return
        }
        arriveDelay()
        anim(PICKUP_TABLE_SEQ)
        invDel(inv, ingredient.obj)
        ingredient.add(player)
        soundSynth(STEW_SOUND)
        mes("You put ${ingredient.putMessage} into the cauldron.")
        val ready = STEW_INGREDIENTS.all { it.added(player) }
        startDialogue {
            chatNpcSpecific("Lalli", LALLI, quiz, "It am ready now?")
            if (!ready) {
                chatPlayer(neutral, "Not just yet...")
                return@startDialogue
            }
            chatPlayer(happy, "Indeed it is. Try it and see.")
            chatNpcSpecific(
                "Lalli",
                LALLI,
                happy,
                "Hmm... YUM! That are delicious! Me never know human know to make soup out of stone? It " +
                    "some special stone?",
            )
            chatPlayer(shifty, "Indeed it is. But I'm willing to trade it.")
            player.ftStewTasted = true
            chatNpcSpecific("Lalli", LALLI, neutral, "Let me think about that, me like to think.")
        }
    }

    /* Askeladden */

    private suspend fun Dialogue.askeladden() {
        when {
            !quest.isStarted(player) -> outerlanderRebuff()
            quest.isComplete(player) ->
                chatNpc(
                    happy,
                    "Hey buddy! You're a real Fremennik now! I'll catch you up soon, you'll see!",
                )
            else -> with(merchant) { withMerchantOption(MerchantContact.Askeladden) { askeladdenTrial() } }
        }
    }

    private suspend fun Dialogue.askeladdenTrial() {
        when {
            player.askeladdenHasRockOp && PET_ROCK in player.inv -> {
                chatPlayer(quiz, "So this is the exact same type of rock you managed to sell to Lalli as a pet?")
                chatNpc(laugh, "Yup, sure is buddy! That Lalli is stupid, even by troll standards!")
            }
            player.askeladdenHasRockOp -> anotherPetRock()
            player.ftOlafStarted && player.ftLalliTalked -> firstPetRock()
            else -> {
                chatPlayer(quiz, "Hi. Are you a member of the council of elders?")
                chatNpc(
                    laugh,
                    "Me? On the council? Ha ha ha ha! Sorry buddy, I'm not a 'real' Fremennik yet! Let " +
                        "me guess, you're trying to get some council members to vote for you?",
                )
                chatPlayer(neutral, "That's right, to become a Fremennik.")
                chatNpc(
                    happy,
                    "You and me both buddy! We're both doing the same trials! This is my trial of " +
                        "manhood! How you finding it so far? Pretty easy, huh buddy?",
                )
                chatPlayer(neutral, "It's okay I guess...")
                chatNpc(
                    happy,
                    "Yeah, that's the spirit buddy! It's all real easy, I wouldn't worry yourself " +
                        "about it, you'll be quaffing beers in the longhall as a Fremennik in no time!",
                )
            }
        }
    }

    private suspend fun Dialogue.firstPetRock() {
        chatPlayer(
            quiz,
            "Hello there. I understand you managed to get some golden wool from Lalli?",
        )
        chatNpc(laugh, "HAHAHA! Yeah, that Lalli... what a maroon!")
        chatPlayer(quiz, "So how did you manage to get the wool?")
        chatNpc(
            neutral,
            "Well, as you know, I am doing the same trials that you are as part of my test of " +
                "manhood, and that troll is the only one who can get that wool.",
        )
        chatNpc(
            laugh,
            "You might have noticed he's kind of... messed in the head buddy! He's real paranoid about " +
                "people stealing his golden apples, isn't he?",
        )
        chatPlayer(quiz, "Indeed he is. So how did you manage to get some golden wool from him?")
        chatNpc(
            happy,
            "It was easy buddy! I persuaded him he needed a pet to help him guard his apples. A pet " +
                "that would never sleep! A pet that would never need food, or exercise!",
        )
        chatNpc(
            happy,
            "A pet that would never need him to clean up its... well, you know, buddy. A pet that " +
                "would always be loyal to him! A faithful companion for life!",
        )
        if (!givePetRock()) {
            return
        }
        chatPlayer(quiz, "What pet is this then?")
        chatNpc(laugh, "A pet ROCK!")
        chatNpc(
            laugh,
            "Man, can you believe that stupid troll traded me some of his golden wool for a " +
                "worthless ROCK?",
        )
        chatNpc(
            laugh,
            "Buddy, I hafta say; if brains were explosives, that troll wouldn't have enough to blow " +
                "his nose!",
        )
        chatPlayer(quiz, "Do you have any spare rocks then?")
        chatNpc(
            neutral,
            "Sure thing buddy, although I have to say, I doubt even that troll is stupid enough to " +
                "fall for the SAME trick TWICE in a row! You can try anyway though!",
        )
    }

    private suspend fun Dialogue.anotherPetRock() {
        chatPlayer(sad, "Can I have another rock? I lost mine...")
        chatNpc(
            laugh,
            "Sure thing buddy! I'd say take better care of this one, but it's just a rock! I have " +
                "hundreds of them! Go wild!",
        )
        givePetRock()
    }

    private suspend fun Dialogue.givePetRock(): Boolean {
        if (player.inv.isFull()) {
            mesbox("You don't have enough space in your inventory for a pet rock.")
            return false
        }
        access.invAdd(access.inv, PET_ROCK)
        player.askeladdenHasRockOp = true
        objbox(PET_ROCK, "Askeladden hands you a pet rock.")
        return true
    }

    private suspend fun ProtectedAccess.claimPetRock() {
        if (ownsAnywhere(PET_ROCK)) {
            mes("You already have a pet rock.")
            return
        }
        if (player.inv.isFull()) {
            mes("You don't have enough space in your inventory for a pet rock.")
            return
        }
        invAdd(inv, PET_ROCK)
        mes("Askeladden gives you another pet rock.")
    }

    private suspend fun ProtectedAccess.strokePetRock() {
        anim(PET_ROCK_STROKE_SEQ)
        delay(2)
        mes("You stroke your pet rock. It seems much happier.")
    }

    /* The Fossegrimen */

    private suspend fun Dialogue.fossegrimen() {
        chatNpc(neutral, "What can I do for you?")
        fossegrimenOffering()
    }

    private suspend fun ProtectedAccess.summonFossegrimen() {
        arriveDelay()
        soundSynth(SPIRIT_SOUND)
        startDialogue {
            chatNpcSpecific("Fossegrimen", FOSSEGRIMEN, neutral, "What can I do for you?")
            fossegrimenOffering()
        }
    }

    private suspend fun Dialogue.fossegrimenOffering() {
        chatPlayer(
            neutral,
            "I hear that I can present you with an offering and you will enchant my lyre for me.",
        )
        chatNpcSpecific(
            "Fossegrimen",
            FOSSEGRIMEN,
            neutral,
            "This is true. For a simple enchantment, you may place an offering on my Altar. Can I help " +
                "you with anything else?",
        )
        val offer =
            choice2("Present offering of a single fish.", true, "Nothing.", false)
        if (!offer) {
            chatPlayer(neutral, "Nothing, sorry.")
            chatNpcSpecific(
                "Fossegrimen",
                FOSSEGRIMEN,
                angry,
                "Do not summon me for idle conversation! Come back when you have an offering.",
            )
            return
        }
        val fish =
            choice4(
                "Raw shark.",
                OFFERINGS[0],
                "Raw manta ray.",
                OFFERINGS[1],
                "Raw sea turtle.",
                OFFERINGS[2],
                "Raw bass.",
                OFFERINGS[3],
            )
        if (fish.obj !in player.inv) {
            mesbox("You do not have a ${fish.name} to offer.")
            return
        }
        access.enchant(fish)
    }

    private suspend fun ProtectedAccess.offerOnAltar(obj: ItemServerType) {
        val fish = OFFERINGS.firstOrNull { it.obj.asRSCM(RSCMType.OBJ) == obj.id }
        if (fish == null) {
            mes("Nothing interesting happens.")
            return
        }
        arriveDelay()
        enchant(fish)
    }

    private suspend fun ProtectedAccess.enchant(fish: Offering) {
        val lyre = if (LYRE in player.inv) LYRE else null
        if (lyre == null) {
            mes("You have no lyre for the Fossegrimen to enchant.")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        invDel(inv, fish.obj)
        soundSynth(SPIRIT_SOUND)
        startDialogue {
            chatNpcSpecific(
                "Fossegrimen",
                FOSSEGRIMEN,
                happy,
                "Many thanks for the offering outerlander. Please accept as a gift the ability to play " +
                    "your lyre...",
            )
        }
        anim(ENCHANT_SEQ)
        val enchanted =
            if (quest.isComplete(player)) CHARGED_LYRES[fish.charges - 1] else ENCHANTED_LYRE
        invReplace(inv, LYRE, 1, enchanted)
        mes("Fossegrimen has enchanted your lyre so that you may play it.")
    }

    /* The longhall stage */

    private suspend fun Dialogue.bouncer() {
        if (ENCHANTED_LYRE in player.inv && !player.voted(Trial.Bard)) {
            chatNpc(neutral, "You have an instrument now, huh? Go on through the door.")
            return
        }
        chatNpc(
            neutral,
            "Only performers get to go backstage. Olaf's the bard around here, and you ain't Olaf.",
        )
    }

    private suspend fun ProtectedAccess.backstageDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.x < door.coords.x
        if (leaving) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (ENCHANTED_LYRE !in player.inv || !quest.isInProgress(player) || player.voted(Trial.Bard)) {
            startDialogue {
                chatNpcSpecific(
                    "Longhall Bouncer",
                    BOUNCER,
                    angry,
                    "Oi! Where do you think you're going? Only performers get to go backstage.",
                )
            }
            return
        }
        startDialogue {
            chatNpcSpecific(
                "Longhall Bouncer",
                BOUNCER,
                neutral,
                "Yeah, you're good to go through. Olaf tells me you're some kind of outerlander bard " +
                    "here on tour. I doubt you're worse than Olaf is.",
            )
        }
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.playEnchantedLyre() {
        if (isOnStage(coords) && quest.isInProgress(player) && !player.voted(Trial.Bard)) {
            perform()
            return
        }
        mes("You withdraw your lyre.")
        for (line in TUNING) {
            anim(PLAY_ONCE_SEQ)
            say(line)
            delay(VERSE_TICKS)
        }
        mes("Your lyre is perfectly tuned.")
    }

    private suspend fun ProtectedAccess.perform() {
        mes("You withdraw your lyre.")
        player.midiJingle(BALLAD_OPENING_JINGLE)
        val hecklers =
            HECKLERS.mapNotNull { search.find(coords, it, HECKLER_RADIUS, HuntVis.Off) }
        for ((index, line) in ballad().withIndex()) {
            anim(PLAY_LOOP_SEQ)
            say(line)
            if (index == REFRAIN_VERSE) {
                player.midiJingle(BALLAD_REFRAIN_JINGLE)
            }
            if (hecklers.isNotEmpty()) {
                hecklers[random.of(hecklers.size)].say(HECKLES[random.of(HECKLES.size)])
            }
            delay(VERSE_TICKS)
        }
        player.midiJingle(PERFECTLY_TUNED_JINGLE)
        anim(PUT_AWAY_SEQ)
        mes("Your lyre is perfectly tuned.")
        startDialogue {
            chatNpcSpecific(
                "Olaf the Bard",
                OLAF,
                happy,
                "Wow! That was awesome! You are one of the greatest bards I have ever had the pleasure " +
                    "of watching performing! You have certainly earned my vote! I hope we can duet " +
                    "together soon!",
            )
        }
        quest.grantVote(this, Trial.Bard)
        mes("As you finished playing you felt Fossegrimen's power leave you...")
        invReplace(inv, ENCHANTED_LYRE, 1, LYRE)
        mes("You feel the musical ability from the Fossegrimen leave you...")
    }

    private fun ProtectedAccess.ballad(): List<String> {
        val maxedSkill = MAXED_SKILLS.firstOrNull { (stat, _) -> statBase(stat) >= MAX_LEVEL }
        return when {
            QuestRequirements.hasCompleted(player, "quest_legends") ->
                listOf(
                    "I cannot even start to list",
                    "the amount of foes I've killed.",
                    "I will simply tell you this:",
                    "I've joined the Legends' Guild!",
                )
            QuestRequirements.hasCompleted(player, "quest_heroes") ->
                listOf(
                    "When it comes to fighting",
                    "I hit my share of zeroes",
                    "But I'm well respected at",
                    "the Guild reserved for Heroes.",
                )
            player.questPoints >= CHAMPIONS_GUILD_QP ->
                listOf(
                    "The thought of lots of questing",
                    "leaves some people unfulfilled,",
                    "but I have done my simple best",
                    "in entering the Champions' Guild.",
                )
            maxedSkill != null ->
                listOf(
                    "When people speak of training",
                    "some people think they're fine.",
                    "But they just all seem jealous that",
                    "my ${maxedSkill.second}'s ninety-nine!",
                )
            else ->
                listOf(
                    "${player.displayName} is my name,",
                    "I haven't much to say.",
                    "But since I have to sing this song,",
                    "I'll just go ahead and play.",
                )
        }
    }

    private class Offering(val obj: String, val name: String, val charges: Int)

    private class StewIngredient(
        val obj: String,
        val name: String,
        val article: String,
        val putMessage: String,
        val added: (Player) -> Boolean,
        val add: (Player) -> Unit,
    )

    private companion object {
        const val SWAYING_TREE = "loc.viking_musical_tree"
        const val STEW = "loc.viking_troll_cauldron"
        const val ALTAR = "loc.viking_lake_shrine_altar"
        const val BACKSTAGE_DOOR = "loc.viking_bard_backstage_door"
        const val RELLEKKA_SPINNING_WHEEL = "loc.viking_spinningwheel"
        const val SPINNING_WHEEL_CONTENT = "content.crafting_spinning_wheel"
        const val AXE_CONTENT = "content.woodcutting_axe"
        val UNUSABLE_AXES = setOf("obj.blessed_axe")

        const val BRANCH = "obj.viking_musical_tree_branch"
        const val UNSTRUNG_LYRE = "obj.viking_unstrung_lyre"
        const val LYRE = "obj.viking_strung_lyre"
        const val ENCHANTED_LYRE = "obj.viking_enchanted_strung_lyre"
        const val GOLDEN_FLEECE = "obj.viking_golden_fleece"
        const val GOLDEN_WOOL = "obj.viking_golden_wool"
        const val PET_ROCK = "obj.vt_useless_rock"
        const val KNIFE = "obj.knife"

        val CHARGED_LYRES =
            listOf(
                "obj.magic_strung_lyre",
                "obj.magic_strung_lyre_2",
                "obj.magic_strung_lyre_3",
                "obj.magic_strung_lyre_4",
            )

        val OFFERINGS =
            listOf(
                Offering("obj.raw_shark", "raw shark", 2),
                Offering("obj.raw_mantaray", "raw manta ray", 4),
                Offering("obj.raw_seaturtle", "raw sea turtle", 3),
                Offering("obj.raw_bass", "raw bass", 1),
            )

        val STEW_INGREDIENTS =
            listOf(
                StewIngredient("obj.onion", "onion", "an", "an onion", { it.ftStewOnion }, { it.ftStewOnion = true }),
                StewIngredient("obj.cabbage", "cabbage", "a", "a cabbage", { it.ftStewCabbage }, { it.ftStewCabbage = true }),
                StewIngredient("obj.potato", "potato", "a", "a potato", { it.ftStewPotato }, { it.ftStewPotato = true }),
                StewIngredient(PET_ROCK, "pet rock", "a", "your pet rock", { it.ftStewRock }, { it.ftStewRock = true }),
            )

        const val BRANCH_WOODCUTTING = 40
        const val LYRE_CRAFTING = 40
        const val LYRE_FLETCHING = 25
        const val FLEECE_PRICE = 1000
        const val CUT_TICKS = 3
        const val SPIN_TICKS = 3

        const val FLETCH_SEQ = "seq.human_fletching"
        const val SPIN_SEQ = "seq.human_spinningwheel_90"
        const val SPINNING_WHEEL_SEQ = "seq.spinningwheel"
        const val PICKUP_TABLE_SEQ = "seq.human_pickuptable"
        const val PET_ROCK_STROKE_SEQ = "seq.viking_pet_rock_stroke"
        const val ENCHANT_SEQ = "seq.viking_lyre_enchant"
        const val PLAY_ONCE_SEQ = "seq.viking_lyre_play_once"
        const val PLAY_LOOP_SEQ = "seq.viking_lyre_playing_loop"
        const val PUT_AWAY_SEQ = "seq.viking_lyre_putaway"

        const val CHOP_SOUND = "synth.chopping"
        const val FLETCH_SOUND = "synth.fletching_cut"
        const val SPIN_SOUND = "synth.spinning"
        const val STEW_SOUND = "synth.bubbling_soup"
        const val SPIRIT_SOUND = "synth.spirit_transform"

        /** Js5 archive 11 groups of the three ballad jingles, from their OSRS wiki pages. */
        const val BALLAD_OPENING_JINGLE = 165
        const val BALLAD_REFRAIN_JINGLE = 164
        const val PERFECTLY_TUNED_JINGLE = 163

        const val VERSE_TICKS = 4
        const val REFRAIN_VERSE = 2
        const val HECKLER_RADIUS = 15
        const val CHAMPIONS_GUILD_QP = 32
        const val MAX_LEVEL = 99

        val HECKLERS =
            listOf(
                "npc.viking_heckler",
                "npc.viking_heckler_2",
                "npc.viking_heckler_3",
                "npc.viking_heckler_4",
            )

        /** The backstage room behind the bouncer's door, whose south side is the longhall stage. */
        val STAGE_SW = CoordGrid(2663, 3682)
        val STAGE_NE = CoordGrid(2666, 3685)

        fun isOnStage(coords: CoordGrid): Boolean =
            coords.level == 0 &&
                coords.x in STAGE_SW.x..STAGE_NE.x &&
                coords.z in STAGE_SW.z..STAGE_NE.z

        val TUNING = listOf("Dor Ray Me So.", "Fah La Ti Doh.", "La La La La.", "De-Doo-De-Doo.")

        val MAXED_SKILLS =
            listOf(
                "stat.ranged" to "Ranged",
                "stat.prayer" to "Prayer",
                "stat.magic" to "Magic",
                "stat.cooking" to "Cooking",
                "stat.woodcutting" to "Woodcutting",
                "stat.fletching" to "Fletching",
                "stat.fishing" to "Fishing",
                "stat.firemaking" to "Firemaking",
                "stat.crafting" to "Crafting",
                "stat.smithing" to "Smithing",
                "stat.mining" to "Mining",
                "stat.herblore" to "Herblore",
                "stat.agility" to "Agility",
                "stat.thieving" to "Thieving",
                "stat.slayer" to "Slayer",
                "stat.farming" to "Farming",
                "stat.runecrafting" to "Runecraft",
                "stat.hunter" to "Hunter",
                "stat.construction" to "Construction",
            )

        val HECKLES =
            listOf(
                "Hey, I've done that quest too!",
                "I have met cows more musical than you!",
                "I thought somebody was murdering a cat!",
                "Is your singing some kind of outerlander weapon?",
                "My grandmother could do better, and she is dead!",
                "Please, ye gods! Make it stop!",
                "Why would you torture us so, outerlander?",
                "Worst. Bard. EVER.",
                "You call THAT singing?",
                "Don't give up your day job outerlander!",
                "I do such feats before breakfast!",
                "Never before have mine ears been so offended!",
                "Your song makes no sense outerlander!",
                "You know, I actually quite liked that verse...",
                "That's kind of catchy. Irritating. But catchy.",
                "Please... Kill me now...",
                "When do we get to the looting and drinking part?",
                "When do you get to the good parts?",
                "Truly, this is the worst sound I have ever heard!",
                "What kind of bard are you anyway?",
                "Please tell me that was the last verse...",
                "The screams of my dying comrades sound better!",
                "I have produced better sounds in the outhouse!",
                "YOU ARE A TERRIBLE BARD!",
            )
    }
}

/** Askeladden offers "Claim Pet Rock" once he has handed the player their first one. */
var Player.askeladdenHasRockOp by boolVarBit("varbit.askelladen_hasop")

private val Player.questPoints: Int by intVarp("varp.qp")
