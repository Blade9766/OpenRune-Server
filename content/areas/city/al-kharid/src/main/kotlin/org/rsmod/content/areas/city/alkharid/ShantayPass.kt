package org.rsmod.content.areas.city.alkharid

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Shantay Pass, the gateway from Al Kharid into the Kharidian Desert.
 *
 * Going south costs a Shantay pass, bought from Shantay for five coins and handed to the guard at
 * the gate; coming back north is free. Shantay also runs a shop of desert supplies, a bank chest,
 * and a small jail for anyone who claims to be an outlaw.
 */
class ShantayPass
@Inject
constructor(private val shops: Shops, private val passages: GenericPassageScript) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SHANTAY) { startDialogue(it.npc) { shantay() } }
        onOpNpc3(SHANTAY) { openShop(it.npc) }
        onOpNpc4(SHANTAY) { startDialogue(it.npc) { buyPass() } }

        onOpNpc1(GATE_GUARD) {
            startDialogue(it.npc) {
                chatNpc(
                    neutral,
                    "Go talk to Shantay. I'm on duty and I don't have time to talk to the likes of " +
                        "you!",
                )
                mesbox(
                    "The guard seems quite bad tempered, probably from having to wear heavy armour " +
                        "in this intense heat.",
                )
            }
        }
        onOpNpc1(PASS_GUARD) { startDialogue(it.npc) { passGuard() } }
        onOpNpc3(PASS_GUARD) { startDialogue(it.npc) { showPass() } }

        onOpLoc1(PASS) { goThrough(it.loc) }
        onOpLoc2(PASS) {
            mesbox("You look at the huge stone gate. Near the gate is a large billboard poster, it reads:")
            mesbox(POSTER)
            mesbox("Despite this warning lots of people seem to pass through the gate.")
        }

        onOpLoc1(BANK_CHEST) {
            mesbox(
                "This chest is used by Shantay and his men. They can put things in and out of " +
                    "storage for you. You open the bank.",
            )
            tryOpenBank()
        }
        onOpLoc3(BANK_CHEST) { ifOpenMainModal("interface.ge_collect") }

        onOpLoc1(JAIL_DOOR) { jailDoor(it.loc, it.type) }

        onOpHeld1(DISCLAIMER) {
            objbox(
                DISCLAIMER,
                "<col=ff0000>*** Shantay Disclaimer ***</col> The Desert is a VERY Dangerous " +
                    "place. Do not enter if you're afraid of dying. Beware of high temperatures, " +
                    "sand storms, and slavers.",
            )
            objbox(
                DISCLAIMER,
                "No responsibility is taken by Shantay if anything bad happens to you under any " +
                    "circumstances.",
            )
        }
    }

    /* Shantay */

    private suspend fun Dialogue.shantay() {
        if (player.shantayJail == NEW_VISITOR) {
            chatNpc(neutral, "Hello effendi, I am Shantay.")
            chatNpc(
                neutral,
                "I see you're new. Please read the billboard poster before going into the desert. " +
                    "It'll give yer details on the dangers you can face.",
            )
            player.shantayJail = MET_SHANTAY
        } else {
            chatNpc(
                neutral,
                "Hello again friend. Please read the billboard poster before going into the " +
                    "desert. It'll give yer details on the dangers you can face.",
            )
        }
        val option =
            choice4(
                "What is this place?",
                1,
                "Can I see what you have to sell please?",
                2,
                "I must be going.",
                3,
                "I want to buy a shantay pass for 5 gold coins.",
                4,
            )
        when (option) {
            1 -> whatIsThisPlace()
            2 -> seeWhatYouSell()
            3 -> mustBeGoing()
            else -> buyPass()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            neutral,
            "This is the pass of Shantay. I guard this area with my men. I am responsible for " +
                "keeping this pass open and repaired.",
        )
        chatNpc(
            neutral,
            "My men and I prevent outlaws from getting out of the desert. And we stop the " +
                "inexperienced from a dry death in the sands. Which would you say you were?",
        )
        val option =
            choice3(
                "I am definitely an outlaw, prepare to die!",
                1,
                "I am a little inexperienced.",
                2,
                "Er, neither, I'm an adventurer.",
                3,
            )
        when (option) {
            1 -> {
                chatPlayer(angry, "I am definitely an outlaw, prepare to die!")
                chatNpc(laugh, "Ha, very funny.....")
                val pronoun = if (access.isBodyTypeB()) "her" else "him"
                chatNpc(angry, "Guards arrest $pronoun!")
                access.ifClose()
                access.mes("The guards arrest you and place you in the jail.")
                delay(2)
                player.shantayJail = JAILED
                access.telejump(JAIL, TeleportType.Exempt)
                chatNpc(
                    neutral,
                    "You'll have to stay in there until you pay the fine of five gold pieces. Do " +
                        "you want to pay now?",
                )
                payTheFine()
                return
            }
            2 -> {
                chatPlayer(worried, "I am a little inexperienced.")
                chatNpc(
                    neutral,
                    "Can I recommend that you purchase a full waterskin and a knife! These items " +
                        "will no doubt save your life. A waterskin will keep water from " +
                        "evaporating in the desert.",
                )
                chatNpc(
                    neutral,
                    "And a keen woodsman with a knife can extract the juice from a cactus. Before " +
                        "you go into the desert, it's advisable to wear desert clothes. It's very " +
                        "hot in the desert and you'll surely cook if you wear armour.",
                )
                chatNpc(
                    neutral,
                    "To keep the pass bandit free, we charge a small toll of five gold pieces. You " +
                        "can buy a desert pass from me, just ask me to open the shop. You can also " +
                        "use our free banking services by clicking on the chest.",
                )
            }
            else -> {
                chatPlayer(neutral, "Er, neither, I'm an adventurer.")
                chatNpc(
                    neutral,
                    "Great, I have just the thing for the desert adventurer. I sell desert clothes " +
                        "which will keep you cool in the heat of the desert. I also sell " +
                        "waterskins so that you won't die in the desert.",
                )
                chatNpc(
                    neutral,
                    "A waterskin and a knife help you survive from the juice of a cactus. Use the " +
                        "chest to store your items, we'll take them to the bank. It's hot in the " +
                        "desert, you'll bake in all that armour.",
                )
                chatNpc(
                    neutral,
                    "To keep the pass open we ask for 5 gold pieces. And we give you a Shantay " +
                        "Pass, just ask to see what I sell to buy one.",
                )
            }
        }
        val next =
            choice3(
                "Can I see what you have to sell please?",
                1,
                "I must be going.",
                2,
                "Why do I have to pay to go into the desert?",
                3,
            )
        when (next) {
            1 -> seeWhatYouSell()
            2 -> mustBeGoing()
            else -> {
                chatPlayer(quiz, "Why do I have to pay to go into the desert?")
                access.ifClose()
                access.mes("Shantay opens his arms wide as if to embrace you.")
                delay(3)
                chatNpc(
                    neutral,
                    "Effendi, you insult me! I am not interested in making a profit from you! I " +
                        "merely seek to cover my expenses in keeping this pass open.",
                )
                chatNpc(
                    neutral,
                    "There is repair work to carry out and also the men's wages to consider. For " +
                        "the paltry sum of 5 Gold pieces, I think we offer a great service.",
                )
                if (choice2("Can I see what you have to sell please?", true, "I must be going.", false)) {
                    seeWhatYouSell()
                } else {
                    mustBeGoing()
                }
            }
        }
    }

    private suspend fun Dialogue.seeWhatYouSell() {
        chatPlayer(quiz, "Can I see what you have to sell please?")
        chatNpc(happy, "Absolutely Effendi!")
        npc?.let { access.openShop(it) }
    }

    private suspend fun Dialogue.mustBeGoing() {
        chatPlayer(neutral, "I must be going.")
        chatNpc(neutral, "So long...")
    }

    private suspend fun Dialogue.buyPass() {
        chatPlayer(neutral, "I want to buy a shantay pass for 5 gold coins.")
        if (!access.invTakeFee(PASS_PRICE)) {
            chatNpc(
                neutral,
                "Sorry friend, the Shantay Pass is 5 gold coins. You don't seem to have enough " +
                    "money!",
            )
            return
        }
        access.invAdd(access.inv, SHANTAY_PASS)
        objbox(SHANTAY_PASS, "You purchase a Shantay Pass.")
    }

    private fun ProtectedAccess.openShop(shantay: Npc) {
        shops.open(player, shantay, SHOP_TITLE, SHOP_INV)
    }

    /* The jail */

    private suspend fun Dialogue.payTheFine() {
        if (!choice2("Yes, okay.", true, "No thanks, you're not having my money.", false)) {
            chatPlayer(angry, "No thanks, you're not having my money.")
            chatNpc(
                neutral,
                "You have a choice. You can either pay five gold pieces or... You can be " +
                    "transported to a maximum security prison in Port Sarim.",
            )
            chatNpc(neutral, "Will you pay the five gold pieces?")
            if (choice2("Yes, okay.", true, "No, do your worst!", false)) {
                payFine()
                return
            }
            chatPlayer(angry, "No, do your worst!")
            chatNpc(
                neutral,
                "You are to be transported to a maximum security prison in Port Sarim. I hope " +
                    "you've learnt an important lesson from this.",
            )
            sendToPortSarim()
            return
        }
        payFine()
    }

    private suspend fun Dialogue.payFine() {
        chatPlayer(neutral, "Yes, okay.")
        chatNpc(neutral, "Good, I see that you have come to your senses.")
        if (!access.invTakeFee(FINE)) {
            chatNpc(neutral, "You don't have that kind of cash on you I see.")
            chatNpc(
                neutral,
                "You are to be transported to a maximum security prison in Port Sarim. I hope " +
                    "you've learnt an important lesson from this.",
            )
            sendToPortSarim()
            return
        }
        player.shantayJail = MET_SHANTAY
        access.mes("You hand over five gold pieces to Shantay.")
        chatNpc(happy, "Great Effendi, now please try to keep the peace.")
        access.mes("Shantay unlocks the door to the cell.")
    }

    private suspend fun Dialogue.sendToPortSarim() {
        access.ifClose()
        player.shantayJail = MET_SHANTAY
        access.mes("You find yourself in a prison.")
        access.telejump(PORT_SARIM_PRISON, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.jailDoor(door: BoundLocInfo, type: dev.openrune.types.ObjectServerType) {
        arriveDelay()
        val inside = coords.x <= door.coords.x
        if (inside && player.shantayJail == JAILED) {
            startDialogue {
                chatNpcSpecific(
                    "Shantay",
                    SHANTAY,
                    neutral,
                    "If you want to be let out, you have to pay a fine of five gold. Do you want to " +
                        "pay now?",
                )
                payTheFine()
            }
            return
        }
        with(passages) { walkThrough(door, type) }
    }

    /* The gate */

    private suspend fun Dialogue.passGuard() {
        chatNpc(neutral, "Hello there! What can I do for you?")
        if (!choice2("I'd like to go into the desert please.", true, "Nothing thanks.", false)) {
            chatPlayer(neutral, "Nothing thanks.")
            return
        }
        chatPlayer(neutral, "I'd like to go into the desert please.")
        showPass()
    }

    /**
     * The guard takes a pass and lets the player through southwards. A first-timer is shown the
     * warning poster and handed a disclaimer on the way.
     */
    private suspend fun Dialogue.showPass() {
        if (SHANTAY_PASS !in player.inv) {
            chatNpcSpecific(
                "Shantay Guard",
                PASS_GUARD,
                neutral,
                "You need a Shantay pass to get through this gate. See Shantay, he will sell you " +
                    "one for a very reasonable price.",
            )
            return
        }
        val firstTime = DISCLAIMER !in player.inv
        if (firstTime) {
            mesbox("There is a large billboard poster near the gateway. It reads:")
            mesbox(POSTER)
            mesbox("That seems pretty scary! Are you sure you want to go through?")
            val go =
                choice2(
                    "Yeah, that poster doesn't scare me!",
                    true,
                    "No, I'm having serious second thoughts now.",
                    false,
                    title = "Go into Desert?",
                )
            if (!go) {
                return
            }
        }
        chatNpcSpecific("Shantay Guard", PASS_GUARD, neutral, "Can I see your Shantay Desert Pass please.")
        objbox(SHANTAY_PASS, "You hand over a Shantay Pass.")
        chatPlayer(happy, "Sure, here you go!")
        if (access.invDel(access.inv, SHANTAY_PASS).failure) {
            return
        }
        if (firstTime && access.invAdd(access.inv, DISCLAIMER).success) {
            chatNpcSpecific(
                "Shantay Guard",
                PASS_GUARD,
                neutral,
                "Here, have a disclaimer... It means that Shantay isn't responsible if you die in " +
                    "the desert.",
            )
        }
        access.ifClose()
        access.mes("You go through the gate.")
        access.crossGate(southbound = true)
    }

    private suspend fun ProtectedAccess.goThrough(gate: BoundLocInfo) {
        arriveDelay()
        val southbound = coords.z > gate.coords.z
        if (!southbound) {
            crossGate(southbound = false)
            return
        }
        startDialogue { showPass() }
    }

    private suspend fun ProtectedAccess.crossGate(southbound: Boolean) {
        val x = coords.x.coerceIn(GATE_MIN_X, GATE_MAX_X)
        val start = CoordGrid(x, if (southbound) NORTH_Z else SOUTH_Z)
        val dest = CoordGrid(x, if (southbound) SOUTH_Z else NORTH_Z)
        if (coords != start) {
            telejump(start, TeleportType.Exempt)
            delay(1)
        }
        anim(WALK_SEQ)
        exactMove(
            start = start,
            end = dest,
            delay1 = 0,
            delay2 = CROSS_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if ((dest.z - start.z).sign > 0) constants.em_face_north else constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(CROSS_TICKS)
        resetAnim()
    }

    private companion object {
        const val SHANTAY = "npc.shantay"
        const val GATE_GUARD = "npc.shantay_guard"
        const val PASS_GUARD = "npc.shantay_guard_still"
        const val PASS = "loc.shantay_pass_henge_doorway"
        const val BANK_CHEST = "loc.thbankchest"
        const val JAIL_DOOR = "loc.shantay_prisondoor"
        const val SHANTAY_PASS = "obj.shantay_pass"
        const val DISCLAIMER = "obj.thshantaydisc"
        const val SHOP_TITLE = "Shantay Pass Shop"
        const val SHOP_INV = "inv.shantayshop"

        const val PASS_PRICE = 5
        const val FINE = 5

        const val NEW_VISITOR = 0
        const val MET_SHANTAY = 1
        const val JAILED = 2

        val JAIL = CoordGrid(3296, 3124)
        val PORT_SARIM_PRISON = CoordGrid(3017, 3182)

        const val GATE_MIN_X = 3303
        const val GATE_MAX_X = 3304
        const val NORTH_Z = 3117
        const val SOUTH_Z = 3115
        const val CROSS_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30
        const val WALK_SEQ = "seq.human_walk_f"

        const val POSTER =
            "The Desert is a VERY Dangerous place. Do not enter if you are scared of dying. Beware " +
                "of high temperatures, sand storms, robbers, and slavers."
    }
}

/** 0 before meeting Shantay, 1 after, 2 while sitting in his jail owing the fine. */
private var Player.shantayJail by intVarp("varp.shantay_jail")
