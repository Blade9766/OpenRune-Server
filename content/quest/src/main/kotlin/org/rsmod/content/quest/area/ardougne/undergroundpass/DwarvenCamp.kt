package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ASHES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BUCKET
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DWARF_BREW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.GAUNTLETS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN_BOOK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KAMEN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KLANK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.NILOOF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_BIGFIRE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_FIRE_LIT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_TAP_FILL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPOT_IBAN_CLAW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL_READY
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOORS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DWARVES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.TINDERBOX
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Niloof, Klank and Kamen: three dwarves who came down here mining decades ago and are the last
 * people in the caverns Iban has not turned.
 *
 * Niloof sends the player to the witch Kardia and later explains her doll. Klank makes the
 * gauntlets that stand up to the teeth of the Soulless, and Kamen brews something from plant roots
 * that he warns should be kept away from naked flames - which is exactly what Iban's tomb wants.
 * Soaked in the brew and lit, the tomb goes up in a wall of fire and leaves Iban's ashes behind.
 */
@Singleton
class DwarvenCamp
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    private val firewallType by lazy {
        ServerCacheManager.getObject(FIREWALL.asRSCM(RSCMType.LOC)) ?: error("Missing $FIREWALL")
    }

    override fun ScriptContext.startup() {
        onOpNpc1(NILOOF) { startDialogue(it.npc) { niloof() } }
        onOpNpc1(KLANK) { startDialogue(it.npc) { klank() } }
        onOpNpc1(KAMEN) { kamen(it.npc) }

        onOpLoc1(BREW_BARREL) { searchBarrel() }
        onOpLocU(BREW_BARREL, BUCKET) { fillBucket() }
        for (tomb in TOMBS) {
            onOpLoc1(tomb) { openTomb() }
            onOpLocU(tomb, DWARF_BREW) { pourBrew() }
            onOpLocU(tomb, TINDERBOX) { lightTomb() }
        }
    }

    /* Niloof */

    private suspend fun Dialogue.niloof() {
        val name = "Niloof"
        when (quest.stage(player)) {
            STAGE_DOORS -> {
                chatNpc(shocked, "Back away! Back away! ....Wait. ...You're human!")
                chatPlayer(happy, "That's right, I'm on a quest for King Lathas. We need to find a way through these caverns.")
                chatNpc(neutral, "Ha ha, listen up. We came here as miners decades ago, completely unaware of the evil that lurked here. There's no way through, not while Iban still rules. He controls the gateway, the only way to the other side.")
                chatPlayer(confused, "What gateway?")
                chatNpc(neutral, "It once stood as the 'Well of Voyage'. A gateway to the West. Now Iban's moulded it into a pit of the damned, a portal to Zamorak's darkest realms.")
                chatNpc(neutral, "He sends his followers there, never to return. Only once Iban is destroyed can the well be restored.")
                chatPlayer(neutral, "But how?")
                chatNpc(neutral, "If I knew, I would have slain him already. Seek out the Witch, his guide, his only confidante. Only she knows how to rid us of Iban.")
                chatNpc(neutral, "She lives on the platforms above, we dare not go there. Here, take some food to aid your journey.")
                access.ifClose()
                access.mes("$name gives you some food...")
                for (food in NILOOF_FOOD) {
                    access.invAddOrDrop(objRepo, food)
                }
                access.delay(2)
                quest.advanceTo(access, STAGE_DWARVES)
                chatPlayer(happy, "Thanks $name, take care.")
            }
            STAGE_DWARVES -> {
                chatPlayer(happy, "Hello $name.")
                chatNpc(happy, "So you still live, not many survive down here.")
                chatPlayer(neutral, "As I can see.")
                chatNpc(neutral, "Don't stay too long traveller. Iban's calls will soon penetrate your delicate human mind.")
                chatNpc(neutral, "You'll also become one of his minions... You must go above and find the witch Kardia. She holds the secret to Iban's destruction.")
            }
            STAGE_DOLL -> {
                chatPlayer(happy, "$name, I found the Witch's house.")
                chatNpc(neutral, "And...?")
                if (access.inv.contains(DOLL)) {
                    chatPlayer(neutral, "I found a strange doll.")
                } else {
                    chatPlayer(neutral, "I found a strange doll and a book, but I've lost the doll.")
                    chatNpc(neutral, "Well it's a good job I found it.")
                    access.invAddOrDrop(objRepo, DOLL)
                }
                chatNpc(neutral, "The witch's rag doll. This here be black magic traveller. Iban was magically conjured in that very item. His four elements of being are guarded somewhere in this cave...")
                chatNpc(neutral, "His shadow, his flesh, his conscience and his blood. If you can retrieve these, with the flask, you will be able destroy Iban and resurrect the 'Well of Voyage'.")
                if (!access.inv.contains(IBAN_BOOK)) {
                    chatNpc(confused, "I found this old book... I'm not sure if it's of any use to you traveller.")
                    access.invAddOrDrop(objRepo, IBAN_BOOK)
                }
            }
            STAGE_DOLL_READY -> {
                chatPlayer(happy, "Hi $name.")
                chatNpc(happy, "Traveller, thank the stars you're still around! ...I thought your time had come.")
                chatPlayer(happy, "I've still a few years in me yet.")
                if (!access.inv.contains(DOLL)) {
                    chatNpc(neutral, "I found something I think you need traveller...")
                    chatPlayer(neutral, "The doll?")
                    chatNpc(neutral, "I found it while slaying some of the soulless. Here you go.")
                    access.invAddOrDrop(objRepo, DOLL)
                    access.mes("$name gives you the doll of Iban")
                }
                if (!access.inv.contains(IBAN_BOOK)) {
                    chatNpc(neutral, "I also found this old journal. I don't know if it's of any importance.")
                    chatPlayer(happy, "That's great $name.")
                    access.invAddOrDrop(objRepo, IBAN_BOOK)
                }
                chatPlayer(neutral, "It's about time I dealt with Iban.")
                chatNpc(neutral, "Good luck to you, you'll need it. May the strength of the Elders be with you...")
                chatPlayer(neutral, "Take care $name.")
            }
            else -> access.mes("The Dwarf seems to be busy...")
        }
    }

    /* Klank */

    private suspend fun Dialogue.klank() {
        val stage = quest.stage(player)
        when {
            stage >= STAGE_DOLL_READY -> klankAfterwards()
            stage == STAGE_DOLL -> klankGift()
            stage >= STAGE_DOORS -> klankIntroduction()
            else -> access.mes("The Dwarf seems to be busy...")
        }
    }

    private suspend fun Dialogue.klankIntroduction() {
        chatPlayer(happy, "Hello my good man.")
        chatNpc(happy, "Good day to you outsider. I'm Klank, I'm the only blacksmith still alive down here. In fact we're the only ones that haven't yet turned.")
        chatNpc(shocked, "If you're not careful you'll become one of them too!")
        chatPlayer(confused, "Who?.. Iban's followers?")
        chatNpc(neutral, "They're not followers, they're slaves, they're the Soulless...")
        if (choice2("What happened to them?", 1, "No wonder their breath was so bad!", 2) == 1) {
            chatPlayer(neutral, "What happened to them?")
            chatNpc(neutral, "They were normal once, adventurers, treasure hunters. But men are weak, they couldn't ignore the voices.")
            chatNpc(sad, "Now they all seem to think with one conscience... As if they're being controlled by one being...")
            chatPlayer(confused, "Iban?")
            chatNpc(neutral, "Maybe... maybe Zamorak himself. Those who try and fight it Iban locks in cages, until their minds are too weak to resist.")
            chatNpc(sad, "Eventually they all fall to his control...")
        } else {
            chatPlayer(shocked, "No wonder their breath was so bad!")
            chatNpc(angry, "You think this is funny... Eh?")
            chatPlayer(bored, "Not really, just trying to lighten up the conversation.")
            chatNpc(neutral, "Hmph!")
        }
        if (!access.inv.contains(TINDERBOX)) {
            chatNpc(neutral, "Here take this, I don't need it.")
            access.invAddOrDrop(objRepo, TINDERBOX)
            access.mes("Klank gives you a tinderbox.")
        }
    }

    private suspend fun Dialogue.klankGift() {
        chatPlayer(happy, "Hi Klank.")
        chatNpc(shocked, "Traveller, I hear you plan to destroy Iban?")
        chatPlayer(happy, "That's right.")
        chatNpc(happy, "I have a gift for you, they may help. I crafted these long ago to protect myself from the teeth of the Soulless, their bite is vicious.")
        chatNpc(sad, "I haven't seen another pair which can withstand their jaws...")
        if (!access.inv.contains(TINDERBOX)) {
            access.invAddOrDrop(objRepo, TINDERBOX)
            access.mes("Klank gives you a tinderbox.")
        }
        if (access.inv.contains(GAUNTLETS) || player.worn.contains(GAUNTLETS)) {
            chatNpc(happy, "Oh... you have a pair of my gauntlets, look after them.")
        } else {
            access.invAddOrDrop(objRepo, GAUNTLETS)
            player.gauntletsGiven = true
            access.mes("Klank gives you a pair of gauntlets.")
        }
        chatPlayer(happy, "Thanks Klank.")
        chatNpc(happy, "Good luck traveller, give Iban a slap for me!")
    }

    private suspend fun Dialogue.klankAfterwards() {
        chatPlayer(happy, "Hello Klank.")
        chatNpc(happy, "Hello again adventurer, so you're still around?")
        chatPlayer(happy, "Still here!")
        if (choice2("Have you anymore gauntlets?", 1, "Take care Klank.", 2) == 2) {
            chatPlayer(happy, "Take care Klank.")
            chatNpc(happy, "You too adventurer...")
            return
        }
        chatPlayer(neutral, "Have you any more gauntlets?")
        chatNpc(neutral, "Well..yes, but they're not cheap to make. I'll have to sell you a pair...")
        chatPlayer(neutral, "How much?")
        chatNpc(neutral, "5000 coins.")
        if (choice2("5000, you must be joking!", 1, "Okay then, I'll take a pair.", 2) == 1) {
            chatPlayer(angry, "5000, you must be joking!")
            chatNpc(angry, "We don't joke down here friend...")
            return
        }
        chatPlayer(neutral, "Okay then, I'll take a pair.")
        if (access.inv.count(COINS) < GAUNTLETS_PRICE) {
            chatPlayer(sad, "Oh dear, I haven't enough money...")
            chatNpc(neutral, "Sorry, I can't sell them any cheaper than that.")
            return
        }
        access.ifClose()
        access.invDel(access.inv, COINS, GAUNTLETS_PRICE)
        access.invAddOrDrop(objRepo, GAUNTLETS)
        access.mes("You give Klank 5000 coins...")
        access.delay(3)
        chatNpc(happy, "There you go... I hope they help.")
        chatPlayer(happy, "I'll see you around Klank.")
    }

    /* Kamen */

    private suspend fun ProtectedAccess.kamen(npc: org.rsmod.game.entity.Npc) {
        mes("He looks a little drunk.")
        npc.say("Hic!")
        delay(1)
        startDialogue(npc) { kamenTalk() }
    }

    private suspend fun Dialogue.kamenTalk() {
        chatPlayer(neutral, "Hi there, you okay?")
        chatNpc(sad, "Ooooh, my head ...I'm fried.")
        chatPlayer(confused, "What's wrong?")
        chatNpc(neutral, "Too much of this home brew my friend. We make it from plant roots, but it blows your head off.")
        chatNpc(shifty, "You don't wanna put it near any naked flames. Want some?")
        if (choice2("Okay then.", 1, "No thanks.", 2) == 1) {
            chatPlayer(happy, "Okay then.")
            chatNpc(happy, "Here you go... Hic!")
            access.ifClose()
            access.mes("You take a sip of brew from Kamen's glass..")
            access.mes("It tastes horrific and burns your throat.")
            access.say("Aaarrgghh!")
            access.statSub("stat.agility", BREW_AGILITY_LOSS, 0)
            access.statBoost("stat.strength", BREW_STRENGTH_GAIN, 0)
            access.delay(1)
            access.mes("The dwarf kindly gives you some food to help you recover from the ghastly drink.")
            for (food in KAMEN_MEAL) {
                access.invAddOrDrop(objRepo, food)
            }
            access.delay(1)
            access.takeInstantHit(HitType.Typeless, random.of(BREW_MIN_DAMAGE, BREW_MAX_DAMAGE))
            chatNpc(happy, "Ha ha! I warned you - it's strong stuff. Have a good meal; it may sober you up a bit.")
            return
        }
        chatPlayer(neutral, "No thanks.")
        chatNpc(happy, "Your losh... Hic!")
        chatNpc(happy, "Well maybe a good meal? I could part with some food for a few coins.")
        if (choice2("Okay then.", 1, "No thanks.", 2) == 2) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(happy, "Well come back any time.")
            return
        }
        chatPlayer(happy, "Okay then.")
        chatNpc(happy, "75 coins pleashe.")
        if (access.inv.count(COINS) < MEAL_PRICE) {
            chatPlayer(sad, "Oh dear, I haven't enough money...")
            chatNpc(neutral, "Sorry, I can't just give it away to anyone that walks past.")
            return
        }
        access.invDel(access.inv, COINS, MEAL_PRICE)
        chatPlayer(happy, "Here you go.")
        for (food in KAMEN_MEAL) {
            access.invAddOrDrop(objRepo, food)
        }
        chatNpc(neutral, "Thanks... Eat well.")
    }

    /* The brew, and what it is for */

    private suspend fun ProtectedAccess.searchBarrel() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (inv.contains(BUCKET)) {
            fillBucket()
            return
        }
        mes("The barrel is full of the dwarves' home brew. You'd need a bucket to carry any.")
    }

    private suspend fun ProtectedAccess.fillBucket() {
        arriveDelay()
        if (invDel(inv, BUCKET).failure) {
            return
        }
        invAdd(inv, DWARF_BREW)
        mes("You fill the bucket with dwarf brew.")
    }

    /** Iban does not want to be disturbed, and says so with his claws. */
    private suspend fun ProtectedAccess.openTomb() {
        arriveDelay()
        mes("You try to open the lid of the tomb.")
        delay(1)
        mes("But it refuses to open.")
        delay(1)
        mes("You hear a noise from below.")
        delay(1)
        mes("<col=8B0000>Leave me be!</col>")
        delay(2)
        spotanim(SPOT_IBAN_CLAW, height = CLAW_HEIGHT)
        delay(1)
        say("Aaarrgghhh")
        takeInstantHit(HitType.Typeless, CLAW_DAMAGE)
    }

    private suspend fun ProtectedAccess.pourBrew() {
        arriveDelay()
        if (quest.stage(player) != STAGE_DOLL) {
            mes("You consider pouring the brew over the grave...")
            delay(2)
            mes("... but it seems such a waste.")
            return
        }
        if (invDel(inv, DWARF_BREW).failure) {
            return
        }
        invAdd(inv, BUCKET)
        anim(SEQ_SEARCH)
        soundSynth(SOUND_TAP_FILL)
        UndergroundPassQuest.setVarBit(player, "varbit.upass_brew_tomb", 1)
        mes("You pour the strong alcohol over the tomb.")
    }

    /**
     * The soaked tomb goes up in a ring of fire, wall by wall round the slab, and burns long enough
     * for the flesh inside to become ash.
     */
    private suspend fun ProtectedAccess.lightTomb() {
        arriveDelay()
        mes("You try to set light to the tomb.")
        if (player.brewOnTomb == 0) {
            mes("But it will not light.")
            return
        }
        anim(SEQ_LIGHT)
        soundSynth(SOUND_FIRE_LIT)
        delay(1)
        mes("It bursts into flames.")
        soundSynth(SOUND_BIGFIRE)
        for ((coords, angle, shape) in FIREWALLS) {
            locRepo.add(coords, firewallType, FIRE_TICKS, angle, shape)
        }
        delay(2)
        mes("You search through the remains.")
        if (inv.contains(ASHES) || player.ashesOnDoll == 1) {
            mes("But find nothing.")
            return
        }
        invAddOrDrop(objRepo, ASHES)
        mes("You find the ashes of Iban's corpse.")
    }

    private data class Firewall(val coords: CoordGrid, val angle: LocAngle, val shape: LocShape)

    private companion object {
        const val BREW_BARREL = "loc.upassdwarfbrewbarrel"
        val TOMBS = arrayOf("loc.ibantomb_left", "loc.ibantomb_right")
        const val FIREWALL = "loc.iban_firewall_straight"
        const val SEQ_LIGHT = "seq.human_createfire"
        const val COINS = "obj.coins"
        const val MEAL_PRICE = 75
        const val GAUNTLETS_PRICE = 5000
        const val BREW_AGILITY_LOSS = 3
        const val BREW_STRENGTH_GAIN = 1
        const val BREW_MIN_DAMAGE = 3
        const val BREW_MAX_DAMAGE = 5
        const val CLAW_HEIGHT = 96
        const val CLAW_DAMAGE = 10
        const val FIRE_TICKS = 10

        val NILOOF_FOOD = listOf("obj.meat_pie", "obj.meat_pie", "obj.meat_pizza")
        val KAMEN_MEAL = listOf("obj.meat_pie", "obj.stew", "obj.bread")

        /** The fire round the tomb: a wall on each edge of the 4x4 slab, corners on the four corners. */
        val FIREWALLS =
            listOf(
                Firewall(CoordGrid(0, 36, 153, 52, 8), LocAngle.South, LocShape.WallL),
                Firewall(CoordGrid(0, 36, 153, 53, 8), LocAngle.South, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 54, 8), LocAngle.South, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 55, 8), LocAngle.East, LocShape.WallL),
                Firewall(CoordGrid(0, 36, 153, 52, 9), LocAngle.West, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 55, 9), LocAngle.East, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 52, 10), LocAngle.West, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 55, 10), LocAngle.East, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 52, 11), LocAngle.West, LocShape.WallL),
                Firewall(CoordGrid(0, 36, 153, 53, 11), LocAngle.North, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 54, 11), LocAngle.North, LocShape.WallStraight),
                Firewall(CoordGrid(0, 36, 153, 55, 11), LocAngle.North, LocShape.WallL),
            )
    }
}
