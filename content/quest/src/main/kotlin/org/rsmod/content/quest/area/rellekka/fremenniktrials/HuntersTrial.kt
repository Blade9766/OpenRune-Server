package org.rsmod.content.quest.area.rellekka.fremenniktrials

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.SIGLI
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.outerlanderRebuff
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Which player each Draugen and butterfly in the province was raised for. */
@Singleton
class DraugenHunts {
    private val owners = HashMap<NpcUid, PlayerUid>()
    private val butterflies = HashMap<PlayerUid, Npc>()
    private val draugens = HashMap<PlayerUid, Npc>()

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc.uid]

    fun claim(npc: Npc, owner: Player) {
        owners[npc.uid] = owner.uid
    }

    fun forget(npc: Npc) {
        owners.remove(npc.uid)
    }

    fun draugenFor(owner: Player): Npc? = draugens[owner.uid]?.takeIf { it.isSlotAssigned }

    fun setDraugen(owner: Player, npc: Npc?) {
        if (npc == null) draugens.remove(owner.uid) else draugens[owner.uid] = npc
    }

    fun butterflyFor(owner: Player): Npc? = butterflies[owner.uid]?.takeIf { it.isSlotAssigned }

    fun setButterfly(owner: Player, npc: Npc?) {
        if (npc == null) butterflies.remove(owner.uid) else butterflies[owner.uid] = npc
    }
}

/**
 * Sigli the Huntsman's trial: track down the Draugen with his hunters' talisman and defeat it.
 *
 * The Draugen haunts one of a handful of spots around the Fremennik province and moves on every
 * few minutes; a pale butterfly flutters about wherever it lurks. Locating with the talisman
 * points the player towards it, and doing so within three tiles of it makes the Draugen appear
 * and attack. It is only ever the hunter's own Draugen, and beating it charges the talisman.
 */
class HuntersTrial
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val hunts: DraugenHunts,
    private val npcRepo: NpcRepository,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val random: GameRandom,
) : PluginScript() {

    private val draugenType: NpcServerType =
        ServerCacheManager.getNpc(DRAUGEN.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DRAUGEN")

    override fun ScriptContext.startup() {
        onOpNpc1(SIGLI) { startDialogue(it.npc) { sigli() } }
        onOpHeld1(TALISMAN) { locate() }
        onOpHeld1(CHARGED_TALISMAN) { mes("You have already captured the Draugen.") }
        onPlayerTimer(DRAUGEN_TIMER) { draugenWanders() }
        onPlayerLogin { resumeHunt(player) }
        onNpcQueue(draugenType, "queue.death") { draugenDefeated() }
    }

    /* Sigli */

    private suspend fun Dialogue.sigli() {
        if (!quest.isStarted(player)) {
            outerlanderRebuff()
            return
        }
        with(merchant) {
            withMerchantOption(MerchantContact.Sigli) {
                if (quest.hasVote(player, Trial.Hunter)) sigliVoted() else huntersTrial()
            }
        }
    }

    private suspend fun Dialogue.sigliVoted() {
        chatNpc(
            happy,
            "Absolutely outerlander! Anyone brave enough to hunt and defeat the Draugen will be " +
                "a great addition to our clan!",
        )
    }

    private suspend fun Dialogue.huntersTrial() {
        when {
            !player.ftSigliStarted -> challenge()
            CHARGED_TALISMAN in player.inv -> {
                chatNpc(
                    happy,
                    "I saw the entire hunt. Let me take that talisman from you; I would be honoured to " +
                        "speak out for you to our council of elders after such a hunt, outerlander.",
                )
                access.invDel(access.inv, CHARGED_TALISMAN)
                chatPlayer(happy, "Thanks!")
                access.clearTimer(DRAUGEN_TIMER)
                quest.grantVote(access, Trial.Hunter)
            }
            else -> {
                chatNpc(
                    neutral,
                    "Hello again outerlander. Have you managed to hunt down the terrible Draugen yet?",
                )
                if (access.ownsAnywhere(TALISMAN)) {
                    chatPlayer(neutral, "Not yet...")
                    chatNpc(
                        angry,
                        "Hmmm. Laziness is not a trait that I admire. I suggest you go and do so if you " +
                            "wish me to vouchsafe for you to our council of elders.",
                    )
                    return
                }
                chatPlayer(sad, "Not yet... I lost the talisman you gave me.")
                chatNpc(
                    angry,
                    "The typical careless regard for valuable objects I would expect from an " +
                        "outerlander such as yourself. Do not lose THIS one.",
                )
                access.giveTalisman()
            }
        }
    }

    private suspend fun Dialogue.challenge() {
        chatNpc(neutral, "What do you want outerlander?")
        chatPlayer(quiz, "Are you a member of the council?")
        chatNpc(
            neutral,
            "The Fremennik council of elders? I am pleased to say that I am. My value as a huntsman is " +
                "recognised by my position there.",
        )
        chatPlayer(quiz, "I was wondering if I could persuade you to vouch for me as a member of your clan?")
        chatNpc(
            neutral,
            "You? ... well... I am not totally against the idea outerlander. If you can demonstrate " +
                "some hunting skills then perhaps I may offer my vote.",
        )
        chatPlayer(
            happy,
            "How can I prove my hunting skills to you? I can go kill, like, a hundred chickens for you " +
                "right now!",
        )
        chatNpc(bored, "Chickens? You think that would impress me?")
        chatPlayer(happy, "Cows then? I can kill cows until, er, the cows come home.")
        chatNpc(
            neutral,
            "No. The prey I have in mind for you to prove your worth to me is something far more " +
                "dangerous. Far more difficult. Far more deadly.",
        )
        chatPlayer(shocked, "Not... Giant Rats?!?!")
        chatNpc(
            angry,
            "I suspect you are mocking me outerlander. You will need to prove your skill as a hunter to " +
                "me by tracking and defeating... The Draugen.",
        )
        if (!choice2("What's a Draugen?", true, "Forget it.", false)) {
            refuse()
            return
        }
        chatPlayer(quiz, "What's a Draugen? Some kind of cheap barbarian rip- off of a dragon?")
        chatNpc(
            neutral,
            "Hmmm. No, the words are slightly similar I suppose, but they are very different creatures.",
        )
        chatNpc(
            neutral,
            "The Draugen is an evil ghost from Fremennik mythology, that devours the souls of those " +
                "brave warriors who meet their ends at sea.",
        )
        chatNpc(
            neutral,
            "It stalks the coastlines, invisible to all. It brings us bad fortunes, and curses our " +
                "journeys across the seas. It is also unkillable by normal means.",
        )
        chatPlayer(
            confused,
            "...Let me get this straight; You want me to hunt an unkillable, invincible, and invisible " +
                "enemy? How am I supposed to do that?",
        )
        chatNpc(
            neutral,
            "Well outerlander, should you accept my challenge I will show you a special hunter's trick " +
                "that will help you. Do you accept the challenge?",
        )
        if (!choice2("Yes", true, "No", false)) {
            refuse()
            return
        }
        chatPlayer(
            neutral,
            "Well, I need every vote I can get in the council of elders, but this certainly sounds " +
                "impossible to do...",
        )
        chatNpc(
            neutral,
            "Not at all outerlander. The Draugen is indeed impossible to kill, but that is not the " +
                "same as being impossible to fight against.",
        )
        chatNpc(
            neutral,
            "Every time he takes a Fremennik life, he gains in power, so to keep it from becoming too " +
                "powerful we hunters hunt it and steal its life force.",
        )
        if (player.inv.isFull()) {
            chatNpc(
                neutral,
                "To defeat the Draugen you will need a special item; I have one to give you but it " +
                    "seems as though your inventory is too full to accept it.",
            )
            return
        }
        player.ftSigliStarted = true
        chatNpc(
            neutral,
            "We do this with a special talisman. Here, take it; it will let you track the Draugen while " +
                "it's invisible, and when you defeat it will absorb its essence.",
        )
        access.giveTalisman()
        chatNpc(
            neutral,
            "I want you to track the Draugen, defeat it, and store its essence in that talisman for me. " +
                "If you can do this important task for my clan, I will vote for you.",
        )
        chatNpc(neutral, "Take care of the talisman, and see me when you have completed this task.")
    }

    private suspend fun Dialogue.refuse() {
        chatPlayer(
            angry,
            "Forget it! This all sounds like way too much work for a stupid vote at a meeting! I'll find " +
                "someone else instead.",
        )
    }

    private suspend fun ProtectedAccess.giveTalisman() {
        invAdd(inv, TALISMAN)
        objbox(TALISMAN, "Sigli gives you a hunters' talisman.")
        moveDraugen(player)
    }

    /* The hunt */

    private fun isHunting(player: Player): Boolean =
        quest.isInProgress(player) && player.ftSigliStarted && !player.voted(Trial.Hunter)

    private fun resumeHunt(player: Player) {
        if (isHunting(player) && TALISMAN in player.inv) {
            player.timer(DRAUGEN_TIMER, WANDER_TICKS)
            showButterfly(player)
        }
    }

    private fun spotOf(player: Player): CoordGrid =
        SPOTS[(player.ftDraugenSpot - 1).coerceIn(SPOTS.indices)]

    private fun moveDraugen(player: Player) {
        val current = player.ftDraugenSpot
        var next = randomSpot()
        if (next == current) {
            next = next % SPOTS.size + 1
        }
        player.ftDraugenSpot = next
        player.timer(DRAUGEN_TIMER, WANDER_TICKS)
        showButterfly(player)
    }

    private fun randomSpot(): Int = random.of(1, SPOTS.size)

    private fun showButterfly(player: Player) {
        hunts.butterflyFor(player)?.let {
            hunts.forget(it)
            npcRepo.del(it, Int.MAX_VALUE)
        }
        val butterfly = Npc(BUTTERFLY, spotOf(player))
        npcRepo.add(butterfly, WANDER_TICKS)
        hunts.claim(butterfly, player)
        hunts.setButterfly(player, butterfly)
    }

    private fun ProtectedAccess.draugenWanders() {
        if (!isHunting(player) || hunts.draugenFor(player) != null) {
            clearTimer(DRAUGEN_TIMER)
            return
        }
        moveDraugen(player)
    }

    private suspend fun ProtectedAccess.locate() {
        if (!isHunting(player)) {
            mes("Nothing interesting happens.")
            return
        }
        if (player.ftDraugenSpot == 0) {
            moveDraugen(player)
        }
        val spot = spotOf(player)
        val existing = hunts.draugenFor(player)
        if (existing != null) {
            mes("The Draugen is here! Beware!")
            return
        }
        if (coords.level == spot.level && coords.chebyshevDistance(spot) <= REVEAL_DISTANCE) {
            raiseDraugen()
            return
        }
        mes("The talisman guides you ${direction(coords, spot)}.")
    }

    private fun direction(from: CoordGrid, to: CoordGrid): String {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val north = dz > DIRECTION_SLACK
        val south = dz < -DIRECTION_SLACK
        val east = dx > DIRECTION_SLACK
        val west = dx < -DIRECTION_SLACK
        return when {
            north && east -> "north-east"
            north && west -> "north-west"
            south && east -> "south-east"
            south && west -> "south-west"
            north -> "north"
            south -> "south"
            east -> "east"
            else -> "west"
        }
    }

    private fun ProtectedAccess.raiseDraugen() {
        mes("The Draugen is here! Beware!")
        val draugen = Npc(draugenType, spotOf(player))
        npcRepo.add(draugen, DRAUGEN_LINGER_TICKS)
        hunts.claim(draugen, player)
        hunts.setDraugen(player, draugen)
        draugen.spotanim(APPEAR_SPOTANIM)
        draugen.facePlayer(player)
        draugen.opPlayer2(player, aiInteractions)
    }

    private suspend fun StandardNpcAccess.draugenDefeated() {
        val owner = hunts.ownerOf(npc)?.resolve(playerList)
        hunts.forget(npc)
        death.deathNoDrops(this)
        if (owner == null) {
            return
        }
        hunts.setDraugen(owner, null)
        hunts.butterflyFor(owner)?.let {
            hunts.forget(it)
            hunts.setButterfly(owner, null)
            npcRepo.del(it, Int.MAX_VALUE)
        }
        launcher.launch(owner) { absorbEssence() }
    }

    private fun ProtectedAccess.absorbEssence() {
        clearTimer(DRAUGEN_TIMER)
        if (TALISMAN !in player.inv) {
            return
        }
        invReplace(inv, TALISMAN, 1, CHARGED_TALISMAN)
        mes("You absorb the Draugen's essence into your talisman.")
    }

    companion object {
        const val DRAUGEN = "npc.viking_draugen"
        const val BUTTERFLY = "npc.viking_draugen_safe"
        const val TALISMAN = "obj.viking_draugen_talisman_uncharged"
        const val CHARGED_TALISMAN = "obj.viking_draugen_talisman"
        const val DRAUGEN_TIMER = "timer.fremtrials_draugen"
        const val APPEAR_SPOTANIM = "spotanim.smokepuff"

        /** How close the talisman has to be for the Draugen to show itself. */
        const val REVEAL_DISTANCE = 3
        const val DIRECTION_SLACK = 2

        /** The Draugen moves to another haunt every five minutes. */
        const val WANDER_TICKS = 500

        /** The Draugen gives up and fades after ten minutes. */
        const val DRAUGEN_LINGER_TICKS = 1000

        /** Open ground around the Fremennik province, well away from the town's walls. */
        val SPOTS =
            listOf(
                CoordGrid(2641, 3626),
                CoordGrid(2630, 3637),
                CoordGrid(2626, 3633),
                CoordGrid(2659, 3627),
                CoordGrid(2705, 3634),
                CoordGrid(2739, 3636),
                CoordGrid(2725, 3602),
                CoordGrid(2738, 3597),
            )
    }
}

/** Nobody but the hunter who raised it may fight a Draugen. */
class DraugenAttackHook @Inject constructor(private val hunts: DraugenHunts) : NpcAttackValidateHook {
    private val draugenId by lazy { HuntersTrial.DRAUGEN.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != draugenId) {
            return NpcAttackValidateResult.Pass
        }
        val owner = hunts.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("This is not your Draugen.")
        }
        return NpcAttackValidateResult.Pass
    }
}
