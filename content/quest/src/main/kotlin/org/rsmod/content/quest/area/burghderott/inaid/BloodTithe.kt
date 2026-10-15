package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.GADDERANKS_CHAT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.GADDERHAMMER
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.HAMMER_GIVEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.JUVINATE_CHAT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_FURNACE_LIT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_GADDERANKS_DEAD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_GADDERANKS_DEFEATED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_RETURN_TO_HOLLOWS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_TITHE_FIGHT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.WISKIT_CHAT
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusDoors
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gadderanks' blood tithe in the general store.
 *
 * Once the furnace is lit, Gadderanks, two vampyre juvinates and Wiskit stand in the store (cache
 * multinpcs on `varbit.blood_tithe_visible`). Speaking to all three of the tithe party starts the
 * fight, which takes place in a private copy of the store's map square with fighters spawned for
 * the player: Gadderanks (level 35) and juvinates of level 50 and 54. When the first juvinate
 * turns to mist, Veliaf arrives and attacks the remaining juvinate, then Gadderanks; his blows
 * count exactly as the player's, so the quest moves on whoever lands the last one. Stepping out
 * of the store, dying or logging out abandons the fight; speaking to the tithe party again starts
 * it afresh.
 *
 * When everyone is dealt with, the stage moves to [STAGE_GADDERANKS_DEFEATED] and Gadderanks'
 * last words play; Aurel hands over the Gadderhammer, or keeps it for a player with no room.
 * Should that scene be cut short, Veliaf - waiting in the real store where Wiskit stood - tells it
 * again before giving his report.
 */
@Singleton
class BloodTithe
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val copies: BurghCopies,
    private val fights: VampyreFights,
    private val doors: PaterdomusDoors,
    private val aiInteractions: AiPlayerInteractions,
    private val npcHitModifier: NpcHitModifier,
    private val random: GameRandom,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    private class Fight(val owner: PlayerUid, val copy: BurghCopies.Copy) {
        val juvinates = ArrayList<Npc>()
        var gadderanks: Npc? = null
        var wounded: Npc? = null
        var veliaf: Npc? = null
        var gadderanksDown = false
        var finished = false
        var veliafTicks = 0
    }

    private val fightsByPlayer = HashMap<PlayerUid, Fight>()

    override fun ScriptContext.startup() {
        onOpNpc1(GADDERANKS) { startDialogue(it.npc) { gadderanks() } }
        onOpNpc1(WISKIT) { startDialogue(it.npc) { wiskit() } }
        for (juvinate in TITHE_JUVINATES) {
            onOpNpc1(juvinate) { startDialogue(it.npc) { juvinate() } }
        }
        onOpNpc1(VELIAF_TALK) { startDialogue(it.npc) { veliaf() } }
        onOpNpc1(GADDERANKS_WOUNDED) { startDialogue(it.npc) { chatNpc(sad, "*cough*") } }
        onPlayerSoftTimer(FIGHT_TIMER) { tick(player) }
        onPlayerLogout { abandon(player) }
    }

    fun isFighting(player: Player): Boolean = fightsByPlayer.containsKey(player.uid)

    private suspend fun Dialogue.gadderanks() {
        chatPlayer(quiz, "What's going on here?")
        if (player.vars[GADDERANKS_CHAT] == 0) {
            chatNpc(happy, "Oh happy days! So many specimens, all in one place! If some good soul hadn't lit that furnace, we'd never have come down here!")
            chatNpc(happy, "Now I'll make my blood tithe quota for sure... especially once you've paid yours. You look like a remarkably fit specimen!")
            chatNpc(laugh, "I bet you could give twice as much as the others before passing out!")
            chatPlayer(angry, "I have no intention of paying a blood tithe!")
            chatNpc(laugh, "Ha! We'll see about that!")
            VarPlayerIntMapSetter.set(player, GADDERANKS_CHAT, 1)
        } else {
            chatNpc(angry, "Do I need to explain it all again? Get in line and be ready to pay your blood tithe!")
        }
        provoked()
    }

    private suspend fun Dialogue.wiskit() {
        chatPlayer(quiz, "What's going on here?")
        chatNpc(worried, "They're draining my blood, that's what they're doing!")
        VarPlayerIntMapSetter.set(player, WISKIT_CHAT, 1)
        provoked()
    }

    private suspend fun Dialogue.juvinate() {
        chatPlayer(quiz, "What's going on here?")
        if (player.vars[JUVINATE_CHAT] == 0) {
            chatNpc(happy, "We're getting some delicious blood tithes out of these sacks of food. Yum!")
            VarPlayerIntMapSetter.set(player, JUVINATE_CHAT, 1)
        } else {
            chatNpc(angry, "You deaf? We're getting some delicious blood tithes out of these sacks of food.")
        }
        provoked()
    }

    /** After the third conversation, or any at all once the fight has been started before. */
    private suspend fun Dialogue.provoked() {
        val stage = iaom.stage(player)
        val talkedToAll = player.vars[GADDERANKS_CHAT] == 1 && player.vars[WISKIT_CHAT] == 1 && player.vars[JUVINATE_CHAT] == 1
        if (stage == STAGE_FURNACE_LIT && talkedToAll) {
            iaom.advanceTo(access, STAGE_TITHE_FIGHT)
        }
        if (iaom.stage(player) != STAGE_TITHE_FIGHT || isFighting(player)) {
            return
        }
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS, angry, "I've had enough of you poking your nose in here! If you think you can stop us, go ahead!")
        access.startFight()
    }

    private suspend fun ProtectedAccess.startFight() {
        val world = if (BurghCoords.inStore(player.coords)) player.coords else BurghCoords.STORE_FIGHT_ENTRY
        fadeOverlay(startColour = 0, startTransparency = 255, endColour = 0, endTransparency = 0, clientDuration = FADE_DURATION)
        delay(FADE_TICKS)
        val copy = with(copies) { enter(INSTANCE_KEY, world, BurghCoords.STORE_EXIT) }
        closeFadeOverlay()
        copy ?: return
        val fight = Fight(player.uid, copy)
        fightsByPlayer[player.uid] = fight
        val listener = VampyreFights.Listener { npc, owner -> dispatched(npc, owner) }
        fight.gadderanks = spawnFighter(fight, GADDERANKS_FIGHTING, BurghCoords.GADDERANKS, listener)
        fight.juvinates += spawnFighter(fight, JUVINATE_ONE, BurghCoords.JUVINATE_ONE, listener)
        fight.juvinates += spawnFighter(fight, JUVINATE_TWO, BurghCoords.JUVINATE_TWO, listener)
        val uid = player.uid
        worldQueues.add(ATTACK_DELAY) {
            val target = uid.resolve(playerList) ?: return@add
            val current = fightsByPlayer[uid] ?: return@add
            for (npc in current.juvinates + listOfNotNull(current.gadderanks)) {
                if (npc.isSlotAssigned) npc.opPlayer2(target, aiInteractions)
            }
        }
        softTimer(FIGHT_TIMER, 1)
    }

    private fun ProtectedAccess.spawnFighter(fight: Fight, type: String, world: CoordGrid, listener: VampyreFights.Listener): Npc {
        val npc = copies.spawn(fight.copy, type, world, Direction.East)
        fights.own(npc, player, listener)
        return npc
    }

    internal fun tick(player: Player) {
        val fight = fightsByPlayer[player.uid] ?: return
        if (fight.finished) {
            return
        }
        val storeX = fight.copy.at(CoordGrid(3512, 3239, 0)).x..fight.copy.at(CoordGrid(3518, 3239, 0)).x
        val storeZ = fight.copy.at(CoordGrid(3512, 3239, 0)).z..fight.copy.at(CoordGrid(3512, 3243, 0)).z
        val inside = player.coords.level == 0 && player.coords.x in storeX && player.coords.z in storeZ
        if (!inside) {
            val world = fight.toWorld(player.coords)
            abandon(player)
            doors.launchWhenFree(player.uid) {
                with(copies) { leave() }
                telejump(world, TeleportType.Exempt)
                mes("You leave the store, and Gadderanks and his juvinates fall back to their tithing.")
            }
            return
        }
        stepVeliaf(fight)
        player.softTimer(FIGHT_TIMER, 1)
    }

    private fun Fight.toWorld(coords: CoordGrid): CoordGrid = copy.toWorld(coords)

    private fun stepVeliaf(fight: Fight) {
        val veliaf = fight.veliaf?.takeIf { it.isSlotAssigned } ?: return
        val target = fight.juvinates.firstOrNull { it.isSlotAssigned } ?: fight.gadderanks?.takeIf { it.isSlotAssigned } ?: return
        if (veliaf.coords.chebyshevDistance(target.coords) > 1) {
            veliaf.walk(target.coords)
            return
        }
        veliaf.faceNpc(target)
        if (++fight.veliafTicks < VELIAF_ATTACK_TICKS) {
            return
        }
        fight.veliafTicks = 0
        veliaf.anim(VELIAF_ATTACK_SEQ)
        target.queueHit(veliaf, HIT_DELAY, HitType.Melee, random.of(0, VELIAF_MAX_HIT), npcHitModifier)
    }

    private fun dispatched(npc: Npc, owner: PlayerUid) {
        val fight = fightsByPlayer[owner] ?: return
        val player = owner.resolve(playerList) ?: return
        if (fight.juvinates.remove(npc)) {
            if (fight.veliaf == null) {
                val veliaf = copies.spawn(fight.copy, VELIAF_FIGHTING, BurghCoords.VELIAF_ENTRY, Direction.West)
                veliaf.say("Fear not my friend! I will come to your aid!")
                fight.veliaf = veliaf
            }
        } else if (npc === fight.gadderanks) {
            fight.gadderanks = null
            fight.gadderanksDown = true
            fight.wounded = copies.spawn(fight.copy, GADDERANKS_WOUNDED, fight.toWorld(npc.coords), Direction.South)
        }
        if (fight.juvinates.isEmpty() && fight.gadderanksDown && !fight.finished) {
            fight.finished = true
            iaom.jumpForward(player, STAGE_GADDERANKS_DEFEATED)
            doors.launchWhenFree(owner) { aftermath(fight) }
        }
    }

    /** What runs once the last of the tithe party is dealt with; launched when the player is free. */
    internal suspend fun ProtectedAccess.finishFight() {
        val fight = fightsByPlayer[player.uid] ?: return
        if (fight.finished) aftermath(fight)
    }

    internal fun fighters(player: Player): List<Npc> =
        fightsByPlayer[player.uid]?.let { it.juvinates + listOfNotNull(it.gadderanks) }.orEmpty()

    internal fun veliafOf(player: Player): Npc? = fightsByPlayer[player.uid]?.veliaf

    private suspend fun ProtectedAccess.aftermath(fight: Fight) {
        try {
            startDialogue { lastWords() }
            fight.wounded?.let { copies.remove(it) }
            handOverHammer()
            mes("Veliaf is waiting by the counter to speak with you.")
        } finally {
            val world = fight.toWorld(player.coords)
            abandon(player)
            if (with(copies) { isInside() }) {
                with(copies) { leave() }
                telejump(world, TeleportType.Exempt)
            }
        }
    }

    private suspend fun Dialogue.lastWords() {
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS_WOUNDED, sad, "*cough*")
        chatNpcSpecific(VELIAF_NAME, VELIAF_TALK, neutral, "Huh, it looks like Gadderanks is still alive. Perhaps he'll give us some information before he meets his maker.")
        chatPlayer(sad, "You don't look too well, Gadderanks. Against my better judgement, I feel sorry for you.")
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS_WOUNDED, shocked, "You... you do?")
        chatPlayer(neutral, "Yes, I do. I understand that you wanted to feel strong, but you chose the wrong side.")
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS_WOUNDED, sad, "But... you don't understand. I just wanted to keep my family safe.")
        chatPlayer(neutral, "Then think of them now. Think of why you did all this. It'll count for nothing if you don't help us!")
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS_WOUNDED, sad, "Then... will you do something for me? Please try to save them. Please! I beg you!")
        chatPlayer(neutral, "You have my word. If we find them, we'll look after them.")
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS_WOUNDED, sad, "Thank you. I know something that may help you...")
        chatNpcSpecific(
            GADDERANKS_NAME,
            GADDERANKS_WOUNDED,
            sad,
            "They fear some things. Silver dust and garlic, for two. Also forbidden are *cough* -ralander and *cough* -ders eggs, and the potion you make from them.",
        )
        chatPlayer(worried, "Take it easy, Gadderanks. Let's see if we can sort you out.")
        chatNpcSpecific(GADDERANKS_NAME, GADDERANKS_WOUNDED, sad, "It's too late for me *cough*. But with what I've told you, perhaps you can *cough* bring light to Morytania.")
        mesbox("Gadderanks passes away.")
        iaom.advanceTo(access, STAGE_GADDERANKS_DEAD)
    }

    private suspend fun ProtectedAccess.handOverHammer() {
        if (player.vars[HAMMER_GIVEN] == 1) {
            return
        }
        startDialogue {
            chatNpcSpecific(AUREL_NAME, GeneralStore.AUREL, happy, "Hey there! Gadderanks dropped this warhammer. I guess it belongs to you now!")
        }
        if (inv.isFull()) {
            mesbox("You have no room for the warhammer. Aurel will keep it for you at the store.")
            return
        }
        invAdd(inv, GADDERHAMMER)
        VarPlayerIntMapSetter.set(player, HAMMER_GIVEN, 1)
        objbox(GADDERHAMMER, "Aurel gives you Gadderanks' warhammer.")
    }

    private suspend fun Dialogue.veliaf() {
        when (iaom.stage(player)) {
            STAGE_GADDERANKS_DEFEATED -> {
                chatNpc(neutral, "Gadderanks is still clinging on. You should hear what he has to say before it's too late.")
                lastWords()
                access.handOverHammer()
                report()
            }
            STAGE_GADDERANKS_DEAD -> report()
            else -> chatNpc(neutral, "Meet me back at the Hollows, my friend.")
        }
    }

    private suspend fun Dialogue.report() {
        chatPlayer(happy, "Phew! Thanks for your help, Veliaf. It sure was good of you to turn up.")
        chatNpc(happy, "Don't mention it. Have you had any luck finding us a new hideout?")
        chatPlayer(happy, "I have. The cellar of the inn here should be perfect.")
        chatNpc(happy, "Excellent! Now, I must get back to the Hollows. Meet me there so we can arrange getting everyone down here safely.")
        chatPlayer(neutral, "Sure.")
        iaom.advanceTo(access, STAGE_RETURN_TO_HOLLOWS)
        mesbox("Veliaf heads back to the Hollows.")
    }

    private fun abandon(player: Player) {
        val fight = fightsByPlayer.remove(player.uid) ?: return
        player.clearSoftTimer(FIGHT_TIMER)
        for (npc in fight.juvinates + listOfNotNull(fight.gadderanks, fight.wounded, fight.veliaf)) {
            copies.remove(npc)
        }
    }

    internal companion object {
        const val INSTANCE_KEY = "burgh_blood_tithe"
        const val FIGHT_TIMER = "timer.burgh_tithe_fight"

        const val GADDERANKS = "npc.burgh_gadderanks"
        const val WISKIT = "npc.burgh_villager_blood_tithe"
        val TITHE_JUVINATES = listOf("npc.burgh_vampire_juve1_blood_tithe", "npc.burgh_vampire_juve2_blood_tithe")
        const val VELIAF_TALK = "npc.burgh_rescue_veliaf_hurtz_talk"
        const val GADDERANKS_FIGHTING = VampyreFights.GADDERANKS_FIGHTING
        const val GADDERANKS_WOUNDED = "npc.burgh_gadderanks_wounded"
        const val JUVINATE_ONE = "npc.burgh_vampire_juve_1_attackable"
        const val JUVINATE_TWO = "npc.burgh_vampire_juve_2_attackable"
        const val VELIAF_FIGHTING = "npc.burgh_rescue_veliaf_hurtz"

        const val GADDERANKS_NAME = "Gadderanks"
        const val VELIAF_NAME = "Veliaf Hurtz"
        const val AUREL_NAME = "Aurel"

        const val VELIAF_ATTACK_SEQ = "seq.human_sword_slash"
        const val VELIAF_ATTACK_TICKS = 4
        const val VELIAF_MAX_HIT = 8
        const val HIT_DELAY = 1
        const val ATTACK_DELAY = 1
        const val FADE_DURATION = 30
        const val FADE_TICKS = 1
    }
}
