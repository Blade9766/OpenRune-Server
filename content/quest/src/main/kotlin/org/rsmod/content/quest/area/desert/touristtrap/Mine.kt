package org.rsmod.content.quest.area.desert.touristtrap

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_BARREL_HEAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_CARRIED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_ON_LIFT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_WINCH_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_WITH_MINE_CART_BARRELS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.EMPTY_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.PUNISHMENT_ROCK
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_CART_LOOP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_WINCHING
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ANA_AT_SURFACE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ANA_IN_MINE_CART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ANA_ON_LIFT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_CAUGHT_ANA
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_GIVEN_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_RETRIEVED_ANA_MINE_CART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_RETRIEVED_ANA_SURFACE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_USED_MINE_CART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.WROUGHT_IRON_KEY
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mine under the Desert Mining Camp and the rock's way out of it, which is also Ana's: into a
 * barrel, onto the mine cart, up the lift to the winch in the compound.
 *
 * The caverns are not joined by walkable ground. The dark passage past the pineapple guard, the
 * two mine carts and the doors up to the compound all move the player between named tiles.
 */
class Mine
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val passages: GenericPassageScript,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (door in EXIT_DOORS) {
            onOpLoc1(door) { climbToSurface() }
            onOpLoc2(door) { mes("Nothing much seems to happen.") }
        }
        for (cave in CAVES) {
            onOpLoc1(cave) { walkThroughCave(it.loc) }
        }

        onOpLoc1(FULL_BARREL) { mesbox("You search the full barrel... It's full of rocks.") }
        onOpLoc2(FULL_BARREL) { searchFullBarrel(it.loc) }
        onOpLoc1(EMPTY_BARREL_LOC) {
            mesbox(
                "This looks like an empty mining barrel. Slaves use this to load up the rocks and " +
                    "stones that they're mining.",
            )
        }
        onOpLoc2(EMPTY_BARREL_LOC) { searchEmptyBarrel(it.loc) }

        onOpLoc1(MINE_CART) { lookAtCart(it.loc) }
        onOpLoc2(MINE_CART) { rideCart(it.loc) }
        onOpLocU(MINE_CART, ANA_IN_A_BARREL) { sendAnaByCart(it.loc) }

        onOpLoc1(WINCH) { mesbox("This looks like a winch, it probably brings rocks up from underground.") }
        onOpLoc2(WINCH) { operateWinch(it.loc) }
        onOpLoc1(WINCH_BARREL) { mesbox("This looks like an interesting mining barrel.") }
        onOpLoc2(WINCH_BARREL) { liftAnaOffTheWinch() }

        for (gate in PRISON_GATES) {
            onOpLoc1(gate) { prisonGate(it.loc, it.type) }
            onOpLoc2(gate) { searchPrisonGate() }
        }
        for (gate in WROUGHT_GATES) {
            onOpLoc1(gate) { wroughtGate(it.loc, it.type, usedKey = false) }
            onOpLoc2(gate) {
                mesbox(
                    "These wrought iron gates look like they're designed to keep people out. It " +
                        "looks like you'll need a key to get past these.",
                )
            }
            onOpLocU(gate, WROUGHT_IRON_KEY) { wroughtGate(it.loc, it.type, usedKey = true) }
        }
    }

    /* The way in and out */

    private suspend fun ProtectedAccess.climbToSurface() {
        arriveDelay()
        mes("You push the door.")
        delay(2)
        player.say("Ugh!")
        if (ANA_IN_A_BARREL in inv && !security.isExempt(player)) {
            caughtWithTheBarrel()
            return
        }
        mes("The doors open with some effort!")
        soundSynth(BIG_DOOR_SOUND)
        telejump(TouristTrapCoords.MINE_DOORS_SURFACE, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.walkThroughCave(cave: BoundLocInfo) {
        arriveDelay()
        val westSide = coords.x < cave.coords.x
        val exempt = security.isExempt(player)
        if (!player.wearingSlaveRobes() && !exempt) {
            with(security) { caughtInFancyClothes(security.guardNear(coords, CAVE_GUARD_REACH)) }
            return
        }
        if (westSide) {
            if (quest.stage(player) < STAGE_GIVEN_PINEAPPLE && !exempt) {
                mes("Two guards block your way further into the caves.")
                startDialogue {
                    chatNpcSpecific("Guard", GUARD_HEAD, angry, "Hey you, move away from there!")
                }
                return
            }
        } else if (ANA_IN_A_BARREL in inv && !exempt) {
            caughtWithTheBarrel()
            return
        }
        mes("You walk into the darkness of the cavern...")
        delay(2)
        mes("...and emerge in a different part of this huge underground complex.")
        telejump(if (westSide) TouristTrapCoords.CAVE_EAST else TouristTrapCoords.CAVE_WEST, TeleportType.Exempt)
    }

    /** Ana's barrel is far too heavy to pass for rock, and she will not keep quiet about it. */
    private suspend fun ProtectedAccess.caughtWithTheBarrel() {
        val guard = security.guardNear(coords, CAVE_GUARD_REACH)
        guard?.say("Hey, where d'ya think you're going with that Barrel?")
        delay(3)
        mes("A guard comes over and takes the barrel off you.")
        delay(2)
        guard?.say("Cor! This barrel is really heavy! Have you been mining lead?")
        delay(2)
        guard?.say("Har, har har!")
        startDialogue {
            chatNpcSpecific("Ana-in-barrel", ANA_BARREL_HEAD, angry, "How rude! Why I ought to teach you a lesson.")
        }
        guard?.say("What was that!")
        delay(2)
        mes("The guard kicks the barrel open!")
        mes("The guards drag Ana away and then throw you into a cell.")
        with(security) {
            loseAna()
            throwInCell(guard)
        }
    }

    /* The barrels */

    private suspend fun ProtectedAccess.searchFullBarrel(barrel: BoundLocInfo) {
        if (player.ttAnaLocation == ANA_WITH_MINE_CART_BARRELS && nearBarrelRoomCart(barrel)) {
            mesbox("You search the barrels and find Ana.")
            pickUpAna("Let me out!", STAGE_ANA_IN_MINE_CART, STAGE_RETRIEVED_ANA_MINE_CART)
            return
        }
        mesbox(
            "This looks like a full mining barrel. Slaves use this to load up the rocks and stones " +
                "that they're mining. ${BARREL_CONTENTS.random()}",
        )
    }

    private suspend fun ProtectedAccess.searchEmptyBarrel(barrel: BoundLocInfo) {
        if (ANA_IN_A_BARREL in inv) {
            mes("You cannot carry another barrel while you're carrying Ana.")
            return
        }
        if (EMPTY_BARREL in inv) {
            mes("You can only manage one of these at a time.")
            return
        }
        when (player.ttAnaLocation) {
            ANA_WITH_MINE_CART_BARRELS ->
                if (nearBarrelRoomCart(barrel)) {
                    mesbox("You search the barrels and find Ana.")
                    pickUpAna("Let me out!", STAGE_ANA_IN_MINE_CART, STAGE_RETRIEVED_ANA_MINE_CART)
                } else {
                    mes("You can see plenty of barrels, but not the one with Ana in it.")
                }
            ANA_ON_LIFT -> mes("Ana is in a barrel on the lift underground, she's not here!")
            else -> takeEmptyBarrel()
        }
    }

    private suspend fun ProtectedAccess.takeEmptyBarrel() {
        objbox(EMPTY_BARREL, "This barrel is quite big, but you may be able to carry one. Would you like to take one?")
        var take = false
        startDialogue { take = choice2("Yeah, cool!", true, "No thanks.", false, title = "Take an empty barrel?") }
        if (!take) {
            mes("You decide not to take the barrel.")
            return
        }
        if (invAdd(inv, EMPTY_BARREL).failure) {
            mes("You don't have enough space in your inventory.")
            return
        }
        anim(PICK_UP_SEQ)
        objbox(EMPTY_BARREL, "You take the barrel, it's not that heavy, just awkward.")
    }

    private suspend fun ProtectedAccess.pickUpAna(line: String, from: Int, to: Int) {
        startDialogue { chatNpcSpecific("Ana (in a Barrel)", ANA_BARREL_HEAD, angry, line) }
        if (inv.isFull()) {
            mesbox("You're carrying so many items that you cannot manage Ana as well.")
            return
        }
        if (invAdd(inv, ANA_IN_A_BARREL).failure) {
            return
        }
        anim(PICK_UP_SEQ)
        quest.moveAna(player, ANA_CARRIED)
        quest.advanceFrom(this, from, to)
        mes("You pick up Ana in a Barrel.")
    }

    private fun nearBarrelRoomCart(barrel: BoundLocInfo): Boolean =
        barrel.coords.chebyshevDistance(TouristTrapCoords.BARREL_ROOM_CART) <=
            TouristTrapCoords.CART_BARREL_RADIUS

    /* The mine carts */

    private suspend fun ProtectedAccess.lookAtCart(cart: BoundLocInfo) {
        if (cart.coords == TouristTrapCoords.BARREL_ROOM_CART) {
            mesbox(
                "This cart is being unloaded into this section of the mine. Before being sent back " +
                    "to another section for another load.",
            )
        } else {
            mesbox(
                "This mine cart is being loaded up with new rocks and stone. It gets sent to a " +
                    "different section of the mine for unloading.",
            )
        }
    }

    /**
     * The player squeezes into a cart and rides it to the other one. Getting in is an agility
     * scramble; a miss means a knock or a fall and no ride.
     */
    private suspend fun ProtectedAccess.rideCart(cart: BoundLocInfo) {
        arriveDelay()
        mesbox("You search the mine cart...")
        mesbox("There may be just enough space to squeeze yourself into the cart. Would you like to try?")
        var squeeze = false
        startDialogue {
            squeeze =
                choice2("Yes, of course.", true, "No thanks, it looks pretty dangerous.", false, title = "Squeeze into the cart?")
        }
        if (!squeeze) {
            mes("You decide not to get into the dangerous looking mine cart.")
            return
        }
        if (ANA_IN_A_BARREL in inv) {
            startDialogue {
                chatNpcSpecific(
                    "Ana (in a Barrel)",
                    ANA_BARREL_HEAD,
                    neutral,
                    "There isn't enough space for both you and this barrel in the cart.",
                )
            }
            return
        }
        if (!statRandom("stat.agility", CART_LOW, CART_HIGH, 0)) {
            anim(CART_FAIL_SEQS.random())
            delay(2)
            mes("You fail to fit yourself into the cart in time before it starts its journey.")
            val damage = random.of(1, CART_MAX_DAMAGE).coerceAtMost(player.hitpoints - 1).coerceAtLeast(0)
            queueHit(player, delay = 0, type = HitType.Typeless, damage = damage)
            mes(if (random.randomBoolean()) "You bang your head on the cart as you try to jump in." else "You jump onto the edge of the cart, but fall off.")
            return
        }
        val fromBarrelRoom = cart.coords == TouristTrapCoords.BARREL_ROOM_CART
        anim(CART_SUCCESS_SEQ)
        soundSynth(SOUND_CART_LOOP)
        delay(1)
        fadeToBlack()
        val dest =
            if (fromBarrelRoom) TouristTrapCoords.FAR_CART_LANDING else TouristTrapCoords.BARREL_ROOM_CART_LANDING
        telejump(dest, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        quest.advanceFrom(this, STAGE_GIVEN_PINEAPPLE, STAGE_USED_MINE_CART)
        if (fromBarrelRoom) {
            mesbox(
                "You appear in a large open room with what looks like lots of miners working " +
                    "away. This is a very rough looking area, the miners look like they're on " +
                    "their last legs.",
            )
        } else {
            mesbox(
                "You appear back in the barrel loading room. A nearby slave looks surprised to see " +
                    "you popping out of the cart.",
            )
        }
    }

    private suspend fun ProtectedAccess.sendAnaByCart(cart: BoundLocInfo) {
        if (cart.coords == TouristTrapCoords.BARREL_ROOM_CART) {
            mesbox("You dare not put the barrel back in the mine cart. The guards might see you.")
            return
        }
        if (invDel(inv, ANA_IN_A_BARREL).failure) {
            return
        }
        anim(LOAD_SEQ)
        soundSynth(SOUND_CART_LOOP)
        quest.moveAna(player, ANA_WITH_MINE_CART_BARRELS)
        quest.advanceFrom(this, STAGE_CAUGHT_ANA, STAGE_ANA_IN_MINE_CART)
        mesbox(
            "You carefully place Ana in the barrel into the mine cart. Soon the cart moves out of " +
                "sight and then it returns.",
        )
    }

    /* The winch in the compound */

    private suspend fun ProtectedAccess.operateWinch(winch: BoundLocInfo) {
        arriveDelay()
        mes("You try to operate the winch.")
        anim(WINCHING_SEQ)
        locAnim(worldRepo, winch, WINCH_MACHINE_SEQ)
        soundSynth(SOUND_WINCHING)
        delay(WINCH_TICKS)
        resetAnim()
        if (player.ttAnaLocation != ANA_ON_LIFT) {
            mes("You operate the winch but nothing significant seems to happen.")
            return
        }
        quest.moveAna(player, ANA_WINCH_BARREL)
        quest.advanceFrom(this, STAGE_ANA_ON_LIFT, STAGE_ANA_AT_SURFACE)
        mesbox(
            "You see a barrel coming to the surface. Before too long you haul it onto the side. " +
                "The barrel seems quite heavy and you hear a muffled sound coming from inside.",
        )
        startDialogue {
            chatNpcSpecific("Ana (in-a-barrel)", ANA_BARREL_HEAD, angry, "Get me OUT OF HERE!")
        }
    }

    private suspend fun ProtectedAccess.liftAnaOffTheWinch() {
        if (player.ttAnaLocation != ANA_WINCH_BARREL) {
            return
        }
        mesbox("You search the barrel and find Ana.")
        pickUpAna("Let me out of here, I feel sick!", STAGE_ANA_AT_SURFACE, STAGE_RETRIEVED_ANA_SURFACE)
    }

    /* The punishment mine */

    private suspend fun ProtectedAccess.prisonGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val prisoner = coords.x <= TouristTrapCoords.PRISON_GATE_X
        if (!prisoner) {
            mes("The gate seems to be locked.")
            mes("The nearby guard doesn't want to let you in.")
            startDialogue {
                chatNpcSpecific(
                    "Guard",
                    GUARD_HEAD,
                    angry,
                    "Hey, move away from the gate. There's nothing interesting for you here.",
                )
            }
            return
        }
        if (security.isExempt(player)) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        mes("The gate seems to be locked.")
        if (inv.count(PUNISHMENT_ROCK) < PUNISHMENT_ROCKS) {
            startDialogue {
                chatNpcSpecific(
                    "Guard",
                    GUARD_HEAD,
                    angry,
                    "Hey, move away from the gate. If you wanna get out you're gonna have to mine " +
                        "for it. You're gonna have to bring me 15 loads of rocks in one go!",
                )
                chatNpcSpecific(
                    "Guard",
                    GUARD_HEAD,
                    angry,
                    "And then I'll let you out. You can go back to work with the other slaves then!",
                )
            }
            return
        }
        startDialogue {
            chatPlayer(
                neutral,
                "Okay, I have all your rocks here, let me out now. <col=0000ff>-- The guard unlocks " +
                    "the gate. --</col>",
            )
            chatNpcSpecific("Guard", GUARD_HEAD, neutral, "Okay, okay, come on out.")
            access.invDel(access.inv, PUNISHMENT_ROCK, PUNISHMENT_ROCKS)
            chatNpcSpecific(
                "Guard",
                GUARD_HEAD,
                neutral,
                "Okay, you've got all the rocks, you can go now, but keep your nose clean in future.",
            )
        }
        with(passages) { walkThrough(gate, type) }
    }

    private suspend fun ProtectedAccess.searchPrisonGate() {
        mes("You search the gates... yep, they're locked.")
        mes("A nearby guard looks you over. He doesn't seem too impressed.")
        mesbox("It looks as if this is where very difficult prisoners are sent as a punishment.")
    }

    /* The wrought iron gates to the good ore */

    private suspend fun ProtectedAccess.wroughtGate(gate: BoundLocInfo, type: ObjectServerType, usedKey: Boolean) {
        arriveDelay()
        val inside = coords.z > TouristTrapCoords.WROUGHT_GATE_Z
        if (!inside && WROUGHT_IRON_KEY !in inv) {
            mes("This gate looks like it needs a key to open it.")
            return
        }
        mes(if (usedKey) "You use the wrought iron key on gates." else "You push the gates open and walk through.")
        with(passages) { walkThrough(gate, type) }
    }

    private companion object {
        const val FULL_BARREL = "loc.thminebarrel_full"
        const val EMPTY_BARREL_LOC = "loc.thminebarrel_empty"
        const val MINE_CART = "loc.touristtrap_minecart"
        const val WINCH = "loc.tourtrap_qip_ropepullthingy"
        const val WINCH_BARREL = "loc.tourtrap_qip_anabarrel_winchside_multi"
        const val GUARD_HEAD = "npc.tourtrap_qip_desert_mining_guard_1"

        val EXIT_DOORS = listOf("loc.thttmineexitl", "loc.thttmineexitr")
        val CAVES = listOf("loc.thminecavel", "loc.thminecaver")
        val PRISON_GATES = listOf("loc.undergroundcellgatel", "loc.undergroundcellgater")
        val WROUGHT_GATES = listOf("loc.undergroundniceminel", "loc.undergroundniceminer")

        const val CAVE_GUARD_REACH = 8
        const val PUNISHMENT_ROCKS = 15

        const val CART_LOW = 150
        const val CART_HIGH = 250
        const val CART_MAX_DAMAGE = 4
        const val WINCH_TICKS = 4

        const val BIG_DOOR_SOUND = "synth.bigdoor_open"
        const val PICK_UP_SEQ = "seq.human_pickupfloor"
        const val LOAD_SEQ = "seq.tourtrap_qip_loading_barrel_a"
        const val CART_SUCCESS_SEQ = "seq.tourtrap_qip_cart_success"
        const val WINCHING_SEQ = "seq.tourtrap_qip_human_winching"
        const val WINCH_MACHINE_SEQ = "seq.tourtrap_qip_winching_machine"

        val CART_FAIL_SEQS =
            listOf(
                "seq.tourtrap_qip_cart_failure_a",
                "seq.tourtrap_qip_cart_failure_b",
                "seq.tourtrap_qip_cart_failure_c",
            )

        val BARREL_CONTENTS =
            listOf(
                "This barrel is full of rocks with copper ore.",
                "This barrel is full of sand.",
                "This barrel is full of rocks with iron ore.",
                "This barrel is full of coal.",
                "This barrel is full of debris.",
                "This barrel is full of rocks rich in tin ore.",
            )
    }
}
