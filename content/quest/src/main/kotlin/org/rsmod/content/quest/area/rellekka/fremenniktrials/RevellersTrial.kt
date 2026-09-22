package org.rsmod.content.quest.area.rellekka.fremenniktrials

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.COINS
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.MANNI
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.outerlanderRebuff
import org.rsmod.content.quest.area.seers.murdermystery.npcs.PoisonSalesmanInquiry
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Manni the Reveller's trial: out-drink him in the longhall.
 *
 * The keg on the longhall table always loses. The way to win is a keg of low alcohol beer from
 * the poison salesman in Seers' Village, switched into the longhall keg while everyone is
 * distracted by a firework: the council workman on the Rellekka bridge trades a strange object
 * for a beer, and once it is lit and pushed into the drain pipe on the east wall of the longhall,
 * it goes off with a bang as the kegs are switched.
 */
class RevellersTrial
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val murderInquiry: PoisonSalesmanInquiry,
    private val merchant: MerchantTrial,
    private val search: NpcSearch,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(MANNI) { startDialogue(it.npc) { manni() } }
        onOpNpc1(WORKMAN) { startDialogue(it.npc) { workman() } }
        onOpNpcU(WORKMAN) {
            val beer = WORKMAN_BEERS.firstOrNull { beer -> beer.asRSCM(RSCMType.OBJ) == it.objType.id }
            if (beer == null || !quest.isInProgress(player)) {
                mes("Nothing interesting happens.")
                return@onOpNpcU
            }
            startDialogue(it.npc) { workmanBeer(beer) }
        }
        onOpNpc1(POISON_SALESMAN) { startDialogue(it.npc) { poisonSalesman() } }
        onOpHeldU(TINDERBOX, STRANGE_OBJECT) { lightStrangeObject() }
        onPlayerTimer(FIRECRACKER_TIMER) { firecrackerExplodes() }
        onOpLoc1(PIPE) { pipe() }
        onOpLocU(PIPE) { pipeWith(it.objType) }
        onOpHeldU(LOW_ALCOHOL_KEG, KEG_OF_BEER) { switchKegs() }
    }

    /* Manni */

    private suspend fun Dialogue.manni() {
        if (!quest.isStarted(player)) {
            outerlanderRebuff()
            return
        }
        with(merchant) {
            withMerchantOption(MerchantContact.Manni) {
                if (quest.hasVote(player, Trial.Reveller)) manniVoted() else manniTrial()
            }
        }
    }

    private suspend fun Dialogue.manniVoted() {
        chatPlayer(quiz, "So I can rely on your vote at the council?")
        chatNpc(drunk, "Absholutely! (hic) You're one mighty drinker!")
    }

    private suspend fun Dialogue.manniTrial() {
        when {
            player.ftManniChallenged && KEG_OF_BEER in player.inv -> offerContest()
            player.ftManniLost -> {
                chatPlayer(quiz, "What do I have to do to earn your vote again?")
                chatNpc(
                    neutral,
                    "Beat me in a drinking contest! Grab a keg of beer from that table near the " +
                        "bar, and come back here with it.",
                )
                chatPlayer(neutral, "Oh yeah. Okay, I will do.")
            }
            else -> challenge()
        }
    }

    private suspend fun Dialogue.challenge() {
        chatPlayer(happy, "Hello there!")
        chatNpc(
            neutral,
            "Hello outerlander. I overheard your conversation with Brundt just now. You wish to " +
                "become a member of the Fremennik?",
        )
        chatPlayer(quiz, "That's right! Why, are you on the council?")
        chatNpc(
            neutral,
            "Do not let my drink-soused appearance fool you, I earnt my place on the council many " +
                "years past. I am always glad to see new blood enter our tribe, and will happily " +
                "vote for you.",
        )
        chatPlayer(happy, "Great!")
        chatNpc(
            neutral,
            "...Providing you can pass a little test for me. As a Fremennik, you will need to show " +
                "cunning, stamina, fortitude, and an iron constitution. I know of only one way to " +
                "test all of these.",
        )
        chatPlayer(quiz, "And what's that?")
        chatNpc(happy, "Why, a drinking contest!")
        chatNpc(
            neutral,
            "The task is simple enough! You versus me, a stiff drink each, last man standing wins " +
                "the trial. So what say you?",
        )
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(
                neutral,
                "I don't think drinking myself into a stupor is a very good way of testing my " +
                    "worthiness.",
            )
            chatNpc(
                neutral,
                "Well, perhaps it's not, but it's my way. If you want my vote at the council, I'm " +
                    "afraid you're going to have to drink me under the table.",
            )
            chatPlayer(neutral, "I'm going to find someone else to vote for me then.")
            chatNpc(neutral, "As you wish, outerlander.")
            return
        }
        chatPlayer(happy, "A drinking contest? Easy. Set them up, and I'll knock them back.")
        player.ftManniChallenged = true
        if (KEG_OF_BEER in player.inv) {
            offerContest()
            return
        }
        chatNpc(
            neutral,
            "When you are ready to begin, go and pick up a keg from that table over there, and " +
                "come back here.",
        )
        chatNpc(
            neutral,
            "We start when you have your keg of beer with you, and finish when one of us can drink " +
                "no more and yields.",
        )
    }

    private suspend fun Dialogue.offerContest() {
        chatNpc(
            neutral,
            "Ah, I see you have your keg of beer. Are we ready to drink against each other?",
        )
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(neutral, "I'm not quite ready yet...")
            chatNpc(
                neutral,
                "Want to line your stomach with some food or something, huh outerlander? Come back " +
                    "to me when you're ready to drink.",
            )
            return
        }
        chatPlayer(happy, "Yes, let's start this drinking contest!")
        chatNpc(neutral, "As you wish, outerlander; I will drink first, then you will drink.")
        val manni = npc ?: return
        manni.anim(TANKARD_DRINK_SEQ)
        access.soundSynth(DRINK_SOUND)
        access.mes("The Fremennik drinks his tankard first. He staggers a little bit.")
        delay(3)
        val switched = player.ftKegSwitched
        access.invDel(access.inv, KEG_OF_BEER)
        player.ftKegSwitched = false
        access.anim(KEG_DRINK_SEQ)
        access.soundSynth(DRINK_SOUND)
        delay(4)
        if (switched) {
            access.mes("You drink from your keg. You don't feel at all drunk.")
            chatPlayer(
                happy,
                "Aaaah, lovely stuff. So you want to get the next round in, or shall I? You don't " +
                    "look so good there!",
            )
            chatNpc(
                drunk,
                "Wassha? Guh? You drank that whole keg! But it dinna affect you at all! I conshede! " +
                    "You can probably outdrink me!",
            )
            chatNpc(
                drunk,
                "I jusht can't (hic) believe it! Thatsh shome mighty fine drinking legs you got! " +
                    "Anyone who can drink like THAT getsh my vote atta consh.. counsh... gets my vote!",
            )
            quest.grantVote(access, Trial.Reveller)
            return
        }
        access.mes("You drink from your keg. You feel extremely drunk...")
        player.ftManniLost = true
        access.statSub(ATTACK, constant = DRUNK_ATTACK_LOSS, percent = 0)
        chatPlayer(
            drunk,
            "Ish no' fair! (hic) I canna drink another drop! Alsho feel veddy, veddy ill...",
        )
        chatNpc(drunk, "I guessh I win then ouddaladder! (hic) Niche try, anyway!")
        chatNpc(
            drunk,
            "Come back if'n you fanshy a rematch! (hic) Jusht let me have a coffee firsht...",
        )
    }

    /* The council workman on the Rellekka bridge */

    private suspend fun Dialogue.workman() {
        chatPlayer(neutral, "Hello.")
        chatNpc(
            neutral,
            "How do. You planning on crossing this here bridge and heading up to Rellekka then?",
        )
        if (choice2("Yes", true, "No", false)) {
            chatPlayer(neutral, "Yes, actually I was.")
            chatNpc(
                neutral,
                "Aye, 'tis a good thing we fixed this 'ere bridge, I reckon. You best be careful up " +
                    "there. Them Fremenniks are odd 'uns.",
            )
            chatPlayer(quiz, "So you fixed this bridge yourself?")
            chatNpc(
                neutral,
                "Aye, that I did. 'Twas real thirsty work too. If only some kind stranger would buy " +
                    "us a bit of beer to sup, eh?",
            )
            chatNpc(neutral, "What with that inn at seers so close by and all, eh?")
        } else {
            chatNpc(
                neutral,
                "Aye, that's what I told t'gaffer. Nobody wants to go up and hang out with a " +
                    "buncha dumb barbarians when there's a perfectly good inn to sup in, in t'seers " +
                    "village.",
            )
            chatNpc(
                neutral,
                "Ain't much better in life than having a nice cold beer to sup on a hot day like " +
                    "this, eh? Better'n fixing bridges, eh?",
            )
        }
        mesbox("The workman winks at you.")
        chatNpc(
            neutral,
            "Just make sure yer don't get me none of that non alcoholic rubbish from that poison " +
                "salesman guy at the tavern!",
        )
    }

    private suspend fun Dialogue.workmanBeer(beer: String) {
        if (access.ownsAnywhere(STRANGE_OBJECT) || access.ownsAnywhere(LIT_STRANGE_OBJECT)) {
            chatNpc(neutral, "Ta very much, but I'm alright for now. Maybe later, eh?")
            return
        }
        access.invReplace(access.inv, beer, 1, STRANGE_OBJECT)
        npc?.anim(TANKARD_DRINK_SEQ)
        access.soundSynth(DRINK_SOUND)
        chatNpc(
            happy,
            "Ta very much, like. That'll hit the spot nicely. Here, you can have this. I picked it " +
                "up as a souvenir on me last hols.",
        )
        objbox(STRANGE_OBJECT, "The workman hands you a strange object.")
        chatPlayer(quiz, "What is it?")
        chatNpc(
            neutral,
            "I dunno rightly, but if you use a tinderbox on it, it don't half make a loud noise!",
        )
    }

    /* The poison salesman in the Seers' Village inn */

    private suspend fun Dialogue.poisonSalesman() {
        if (murderInquiry.isInvestigating(player)) {
            with(murderInquiry) { murderInquiry() }
            return
        }
        if (!quest.isInProgress(player) || player.voted(Trial.Reveller)) {
            chatNpc(
                happy,
                "Howdy! You seem like someone with discerning taste! Howsabout you try my brand " +
                    "new range of alcohol?",
            )
            chatPlayer(neutral, "No thanks.")
            return
        }
        if (player.ftSalesmanPitched) {
            chatPlayer(quiz, "I understand you have some low alcohol beer for sale...?")
            chatNpc(
                happy,
                "That I do, and sales are rocketing upwards as word of mouth spreads of it's clean " +
                    "taste and cool flavour! So... you want to buy some?",
            )
            buyLowAlcoholKeg()
            return
        }
        chatPlayer(neutral, "Hello.")
        chatNpc(
            happy,
            "Howdy! You seem like someone with discerning taste! Howsabout you try my brand new " +
                "range of alcohol?",
        )
        chatPlayer(quiz, "Didn't you used to sell poison?")
        chatNpc(
            happy,
            "That I did, indeed! Peter Potter's Patented Multipurpose poison! A miracle of modern " +
                "apothecaries! My exclusive concoction has been tested on...",
        )
        chatPlayer(bored, "Uh, yeah, I've already heard the sales pitch.")
        chatNpc(sad, "Sorry stranger, old habits die hard I guess.")
        chatPlayer(quiz, "So you don't sell poison any more?")
        chatNpc(
            sad,
            "Well, I would, but I ran out of stock. Business wasn't helped with that stuff that " +
                "happened up at the Sinclair Mansion much either, I'll be honest.",
        )
        chatNpc(
            happy,
            "So, being the man of enterprise that I am I decided to branch out a little bit!",
        )
        chatPlayer(quiz, "Into alcohol?")
        chatNpc(
            happy,
            "Absolutely! The basic premise between alcohol and poison is pretty much the same, " +
                "after all! The difference is that my alcohol has a unique property others do not!",
        )
        chatPlayer(quiz, "And what is that?")
        mesbox("The salesman takes a deep breath.")
        chatNpc(
            happy,
            "Ever been too drunk to find your own home? Ever wished that you could party away all " +
                "night long, and still wake up fresh as a daisy the next morning?",
        )
        chatNpc(
            happy,
            "Thanks to the miracles of modern magic we have come up with just the solution you " +
                "need! Peter Potter's Patented Party Potions!",
        )
        chatNpc(
            happy,
            "It looks just like beer! It tastes just like beer! It smells just like beer! But... " +
                "it's not beer!",
        )
        chatNpc(
            happy,
            "Our mages have mused for many moments to bring you this miracle of modern magic! It " +
                "has all the great tastes you'd expect, but contains absolutely no alcohol!",
        )
        chatNpc(
            happy,
            "That's right! You can drink Peter Potters Patented Party Potion as much as you want, " +
                "and suffer absolutely no ill effects whatsoever!",
        )
        chatNpc(
            happy,
            "The clean fresh taste you know you can trust, from the people who brought you; Peter " +
                "Potters Patented multipurpose poison, Peter Potters peculiar paint packs",
        )
        chatNpc(
            happy,
            "and Peter Potters paralysing panic pins. Available now from all good stockists! Ask " +
                "your local bartender now, and experience the taste revolution of the century!",
        )
        mesbox("He seems to have finished for the time being.")
        chatPlayer(quiz, "So... when you say 'all good stockists'...")
        chatNpc(quiz, "Yes?")
        chatPlayer(quiz, "How many inns actually sell this stuff?")
        chatNpc(
            sad,
            "Well... nobody has actually bought any yet. Everyone I try and sell it to always asks " +
                "me what exactly the point of beer that has absolutely no effect on you is.",
        )
        chatPlayer(quiz, "So what is the point?")
        chatNpc(confused, "Well... Um... Er... Hmmm. You, er, don't get drunk.")
        chatPlayer(bored, "I see...")
        chatNpc(
            sad,
            "Aw man... You don't want any now do you? I've really tried to push this product, but I " +
                "just don't think the world is ready for beer that doesn't get you drunk.",
        )
        chatNpc(
            angry,
            "I'm a man ahead of my time I tell you! It's not that my products are bad, it's that " +
                "they're too good for the market!",
        )
        chatPlayer(happy, "Actually, I would like some. How much do you want for it?")
        player.ftSalesmanPitched = true
        chatNpc(
            shocked,
            "Y-you would??? Um, okay! I knew I still had the old salesmans skills going on!",
        )
        chatNpc(
            happy,
            "I'll sell you a keg of it for only 250 gold pieces! So what do you say?",
        )
        buyLowAlcoholKeg()
    }

    private suspend fun Dialogue.buyLowAlcoholKeg() {
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(neutral, "No, not really.")
            return
        }
        chatPlayer(happy, "Yes please!")
        if (player.inv.count(COINS) < KEG_PRICE) {
            chatNpc(
                neutral,
                "Sorry pal, we do not offer credit for any purchases made of Peter Potter's " +
                    "patented party potion! Come back when you have the cash!",
            )
            return
        }
        if (player.inv.isFull() && player.inv.count(COINS) != KEG_PRICE) {
            chatNpc(neutral, "Sorry pal, doesn't look like you have room on you to carry it.")
            return
        }
        access.invDel(access.inv, COINS, KEG_PRICE)
        access.invAdd(access.inv, LOW_ALCOHOL_KEG)
        objbox(LOW_ALCOHOL_KEG, "The salesman hands you a keg of his low alcohol beer.")
    }

    /* The distraction */

    private fun ProtectedAccess.lightStrangeObject() {
        invReplace(inv, STRANGE_OBJECT, 1, LIT_STRANGE_OBJECT)
        soundSynth(FUSE_SOUND)
        mes("You light the string of the strange object. It starts to hiss slightly.")
        timer(FIRECRACKER_TIMER, random.of(FUSE_MIN_TICKS, FUSE_MAX_TICKS))
    }

    private fun ProtectedAccess.firecrackerExplodes() {
        clearTimer(FIRECRACKER_TIMER)
        if (LIT_STRANGE_OBJECT !in player.inv) {
            return
        }
        invDel(inv, LIT_STRANGE_OBJECT)
        soundSynth(EXPLOSION_SOUND)
        mes("The strange object you lit earlier explodes in your inventory!")
        say("Ow!")
        val damage = (statBase(HITPOINTS) * EXPLOSION_PERCENT / 100).coerceAtLeast(EXPLOSION_MIN)
        queueHit(delay = 0, type = HitType.Typeless, damage = damage)
    }

    private suspend fun ProtectedAccess.pipe() {
        when {
            LIT_STRANGE_OBJECT in player.inv -> primePipe()
            STRANGE_OBJECT in player.inv -> mes("Nothing interesting happens.")
            else ->
                startDialogue {
                    chatPlayer(
                        neutral,
                        "I don't have anything I really want to put inside a smelly old drain pipe.",
                    )
                }
        }
    }

    private suspend fun ProtectedAccess.pipeWith(obj: ItemServerType) {
        when (obj.id) {
            LIT_STRANGE_OBJECT.asRSCM(RSCMType.OBJ) -> primePipe()
            STRANGE_OBJECT.asRSCM(RSCMType.OBJ) -> mes("Nothing interesting happens.")
            else ->
                startDialogue {
                    chatPlayer(
                        neutral,
                        "I don't have anything I really want to put inside a smelly old drain pipe.",
                    )
                }
        }
    }

    private suspend fun ProtectedAccess.primePipe() {
        invDel(inv, LIT_STRANGE_OBJECT)
        clearTimer(FIRECRACKER_TIMER)
        player.ftPipePrimed = true
        mes("You put the lit strange object into the pipe.")
        startDialogue {
            chatPlayer(
                happy,
                "That is going to make a really loud bang when it goes off! It would be a perfect " +
                    "distraction to help me cheat in the drinking contest!",
            )
        }
    }

    private suspend fun ProtectedAccess.switchKegs() {
        if (!player.ftPipePrimed) {
            startDialogue {
                chatNpcSpecific(
                    "Manni the Reveller",
                    MANNI,
                    angry,
                    "Thinking of cheating my test, outerlander? I don't think so! You drink that " +
                        "keg fairly, or not at all! Don't let me catch you cheating!",
                )
                chatPlayer(
                    shifty,
                    "I'm going to have to find some way to distract this guy while I cheat...",
                )
            }
            return
        }
        player.ftPipePrimed = false
        soundSynth(EXPLOSION_SOUND)
        mes("You hear a loud bang from outside. It echoes through the drain.")
        for (npc in search.findAllAny(LONGHALL_CENTRE, LONGHALL_RADIUS, HuntVis.Off)) {
            npc.say("what was THAT?")
        }
        invDel(inv, LOW_ALCOHOL_KEG)
        player.ftKegSwitched = true
        mes("You empty the keg and refill it with low alcohol beer.")
    }

    companion object {
        const val KEG_OF_BEER = "obj.viking_beerkeg"
        const val LOW_ALCOHOL_KEG = "obj.viking_low_alcahol_beerkeg"
        const val STRANGE_OBJECT = "obj.viking_firecracker"
        const val LIT_STRANGE_OBJECT = "obj.viking_firecracker_lit"
        const val TINDERBOX = "obj.tinderbox"
        val WORKMAN_BEERS = listOf("obj.beer", "obj.viking_tankard_full")

        const val WORKMAN = "npc.vt_council_workmen"
        const val POISON_SALESMAN = "npc.poison_salesman"
        const val PIPE = "loc.viking_pipe_end_longhall"

        const val TANKARD_DRINK_SEQ = "seq.viking_tankard_drink"
        const val KEG_DRINK_SEQ = "seq.viking_keg_drink_full"
        const val DRINK_SOUND = "synth.drink"
        const val FUSE_SOUND = "synth.fuse"
        const val EXPLOSION_SOUND = "synth.explosion"
        const val FIRECRACKER_TIMER = "timer.fremtrials_firecracker"

        const val ATTACK = "stat.attack"
        const val HITPOINTS = "stat.hitpoints"
        const val DRUNK_ATTACK_LOSS = 3

        const val KEG_PRICE = 250

        /** The strange object burns for 70 to 100 seconds before it goes off in the pack. */
        const val FUSE_MIN_TICKS = 117
        const val FUSE_MAX_TICKS = 167
        const val EXPLOSION_PERCENT = 4
        const val EXPLOSION_MIN = 2

        val LONGHALL_CENTRE = CoordGrid(2658, 3672)
        const val LONGHALL_RADIUS = 12
    }
}

/** The longhall keg is only for the drinking contest, and only one at a time. */
class LonghallKegTakeHook @Inject constructor(private val quest: FremennikTrialsQuest) :
    PlayerObjTakeValidateHook {
    private val kegId by lazy { RevellersTrial.KEG_OF_BEER.asRSCM(RSCMType.OBJ) }

    override fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String? {
        if (objType.id != kegId) {
            return null
        }
        if (!quest.isInProgress(player) || player.voted(Trial.Reveller) || !player.ftManniChallenged) {
            return "You have no reason to take that."
        }
        val bank = player.invMap.getOrPut("inv.bank")
        if (RevellersTrial.KEG_OF_BEER in player.inv || RevellersTrial.KEG_OF_BEER in bank) {
            return "You already have a keg of beer."
        }
        player.ftKegSwitched = false
        return null
    }
}
