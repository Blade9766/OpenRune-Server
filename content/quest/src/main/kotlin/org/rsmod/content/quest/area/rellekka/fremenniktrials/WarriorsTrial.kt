package org.rsmod.content.quest.area.rellekka.fremenniktrials

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.THORVALD
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.outerlanderRebuff
import org.rsmod.content.quest.area.wilderness.magearena.freeFootprint
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** The Koschei each challenger is fighting beneath Thorvald's house. */
@Singleton
class KoscheiFights {
    class Fight(val owner: PlayerUid, var npc: Npc?, var spawnedAt: Int, var yielding: Boolean = false)

    private val fights = HashMap<PlayerUid, Fight>()
    private val owners = HashMap<NpcUid, PlayerUid>()

    fun fightOf(player: Player): Fight? = fights[player.uid]

    fun begin(player: Player, cycle: Int): Fight =
        fights.getOrPut(player.uid) { Fight(player.uid, null, cycle) }

    fun register(fight: Fight, npc: Npc, cycle: Int) {
        fight.npc = npc
        fight.spawnedAt = cycle
        owners[npc.uid] = fight.owner
    }

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc.uid]

    fun forgetNpc(npc: Npc) {
        owners.remove(npc.uid)
    }

    fun end(player: Player): Fight? {
        val fight = fights.remove(player.uid) ?: return null
        fight.npc?.let { owners.remove(it.uid) }
        return fight
    }
}

/**
 * Thorvald the Warrior's trial: fight Koschei the deathless in the battleground beneath his house,
 * with no armour, weapons or runes.
 *
 * Koschei shows himself once the player has explored the battleground a little, and rises
 * stronger each time he is beaten: three forms must fall, and the fourth, who strikes every tick
 * and drains prayer, is fought until one side is spent. The vote is won either way; beating the
 * fourth form also earns a Fremennik blade. Dying in the battleground costs nothing, and leaving it
 * starts the fight over.
 */
class WarriorsTrial
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val fights: KoscheiFights,
    private val npcRepo: NpcRepository,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val clock: MapClock,
    private val random: GameRandom,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val formTypes: List<NpcServerType> =
        FORMS.map { ServerCacheManager.getNpc(it.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $it") }

    override fun ScriptContext.startup() {
        onOpNpc1(THORVALD) { startDialogue(it.npc) { thorvald() } }
        onOpNpcU(THORVALD) {
            if (it.objType.id != RAW_SARDINE.asRSCM(RSCMType.OBJ)) {
                mes("Nothing interesting happens.")
                return@onOpNpcU
            }
            startDialogue(it.npc) { sardines() }
        }
        onOpLoc2(HOUSE_LADDER) { descend() }
        onOpLoc1(ARENA_LADDER) { leaveArena() }
        onPlayerTimer(KOSCHEI_TIMER) { koscheiTick() }
        onPlayerLogin { if (isInArena(player.coords)) player.timer(KOSCHEI_TIMER, 1) }
        for ((index, type) in formTypes.withIndex()) {
            onNpcQueue(type, "queue.death") { formDefeated(index) }
        }
        onEvent<PlayerHitEvents.Modify> { spareOnLastBreath(player, hit.isFromNpc, hit.sourceSlot) }
    }

    /* Thorvald */

    private suspend fun Dialogue.thorvald() {
        if (!quest.isStarted(player)) {
            outerlanderRebuff()
            return
        }
        with(merchant) {
            withMerchantOption(MerchantContact.Thorvald) {
                if (quest.hasVote(player, Trial.Warrior)) thorvaldVoted() else warriorsTrial()
            }
        }
    }

    private suspend fun Dialogue.thorvaldVoted() {
        chatPlayer(quiz, "So can I count on your vote at the council of elders now Thorvald?")
        chatNpc(
            happy,
            "Absolutely! I watched the entire battle, and was extremely impressed with your " +
                "bravery in combat!",
        )
    }

    private suspend fun Dialogue.warriorsTrial() {
        if (!player.ftThorvaldStarted) {
            challenge()
            return
        }
        val topic =
            choice3(
                "What do I have to do again?",
                1,
                "Who is my opponent?",
                2,
                "Can't I do something else?",
                3,
            )
        when (topic) {
            1 -> {
                chatPlayer(quiz, "So what do I have to do to earn your vote at the council again?")
                chatNpc(
                    neutral,
                    "I will not offer my vote to anybody whose bravery in combat I do not trust " +
                        "completely. You must go down that ladder and fight your foe to the death.",
                )
            }
            2 -> opponent()
            else -> {
                chatPlayer(
                    sad,
                    "I don't really like fighting that much... Isn't there something else I can do to " +
                        "earn your vote at the council of elders?",
                )
                chatNpc(
                    happy,
                    "Yes of course outerlander! If you bring me five raw sardines then I will vote for " +
                        "you instead!",
                )
                chatPlayer(shocked, "REALLY?!?!?")
                chatNpc(
                    laugh,
                    "HAHAHAHAHAHA! No, of course not! You are stupid, even by outerlander standards!",
                )
                chatNpc(neutral, "If you want my vote, you must pass my trial. It is as simple as that.")
            }
        }
    }

    private suspend fun Dialogue.challenge() {
        chatPlayer(happy, "Hello!")
        chatNpc(
            neutral,
            "Hello yourself, outerlander. What brings you to dare speak to a mighty Fremennik warrior " +
                "such as myself?",
        )
        chatPlayer(quiz, "Erm... are you a member of the council?")
        chatNpc(
            neutral,
            "The Fremennik council of elders? Why, of course I am. I am recognised as one the clans " +
                "mightiest warriors. What is it to you outerlander?",
        )
        chatPlayer(neutral, "Well... I was wondering if you could vote for me to become a Fremennik.")
        chatNpc(laugh, "An outerlander wishes to become a Fremennik!?!? Ha! That is priceless!")
        chatNpc(
            neutral,
            "Well, let us say that I am not totally against this concept. As a warrior, I appreciate " +
                "the value of brave and powerful warriors to our clan,",
        )
        chatNpc(
            neutral,
            "and even though you may be an outerlander, I will not hold this against you if you can " +
                "prove yourself to be fierce of heart in a combat situation to me.",
        )
        chatPlayer(
            angry,
            "So how can I prove that? You want to fight me? Come on then, bring it on! Right here, right " +
                "now, buddy!",
        )
        chatNpc(laugh, "Hahahahaha! You certainly show some spirit for an outerlander!")
        chatNpc(
            neutral,
            "But spirit does not always make a good warrior. It takes both skill and spirit to be so. I " +
                "have a test that I give all Fremenniks on their path to be a member of the clan.",
        )
        chatNpc(
            neutral,
            "My test will challenge both your combat prowess and your bravery equally. Should you pass it " +
                "you will earn my vote at the council, and more importantly my respect for you as a " +
                "warrior.",
        )
        chatNpc(quiz, "So what say you, outerlander? Are you prepared for the battle?")
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(neutral, "No thanks, I'm pretty sure that I can find someone else to vote for me.")
            chatNpc(
                bored,
                "Hmm, not so brave after all, outerlander? Perhaps it is for the best. I doubt you have " +
                    "what it takes to pass my challenge.",
            )
            return
        }
        chatPlayer(
            angry,
            "Am I prepared? I'll show you what combat's all about, you big sissy barbarian type guy!",
        )
        player.ftThorvaldStarted = true
        chatNpc(laugh, "Hahahahaha! I'm beginning to like you already, outerlander!")
        chatNpc(
            neutral,
            "Then allow me to present you with my challenge; This ladder here will take you to a place " +
                "of combat. I have placed a special warrior down there to challenge you.",
        )
        chatNpc(
            neutral,
            "Battle him to the death, and you will pass my challenge. If at any point you wish to leave " +
                "combat, simply climb back up the ladder, to leave that place.",
        )
        chatNpc(
            neutral,
            "If you leave you will of course fail the test. You may retry my test in the future if you " +
                "fail, but you must stay down there until the death if you wish for my vote at the " +
                "council.",
        )
        chatNpc(
            neutral,
            "You must defeat him three times to prove that you are worthy. The fourth time that you " +
                "fight him will be to the death, so do not show cowardice.",
        )
        chatPlayer(happy, "Is that all? It will be easy!")
        chatNpc(neutral, "No, there is one more important rule;")
        equipmentRule()
    }

    private suspend fun Dialogue.equipmentRule() {
        chatNpcSpecific(
            "Thorvald the Warrior",
            THORVALD,
            neutral,
            "You may not enter the battleground with any armour or weaponry of any kind.",
        )
        chatNpcSpecific(
            "Thorvald the Warrior",
            THORVALD,
            neutral,
            "If you need to place your equipment into your bank account, I recommend that you speak to " +
                "the seer, who knows a spell that will do that for you.",
        )
    }

    private suspend fun Dialogue.opponent() {
        chatPlayer(quiz, "So, who is my opponent?")
        chatNpc(
            neutral,
            "Ah, a wise question before entering combat. His name is Koschei the deathless. He is " +
                "something of a mystery, even to us.",
        )
        chatNpc(
            neutral,
            "On one of our regular raiding parties, our longship discovered a man in the frozen waters " +
                "far north-east of here. We took him aboard our ship, thinking he must be dead.",
        )
        chatNpc(
            neutral,
            "To our amazement he was perfectly healthy, even though he must have been in those deadly " +
                "icy waters for many weeks.",
        )
        chatNpc(
            neutral,
            "He has no memory of who he is, or how he came to be there, except for his own name: " +
                "Koschei. We named him 'The Deathless' because he is seemingly unkillable!",
        )
        chatNpc(
            neutral,
            "Any combat technique used against him, he learns instantly! He also apparently can heal " +
                "himself from any wound at will! When he attacks, his weapon moves like a whirlwind!",
        )
        chatNpc(
            neutral,
            "He can hide his combat level from his opponents at will as well! He is truly a horribly " +
                "fierce opponent to face! I am only glad that he has chosen to stay here with us!",
        )
        chatNpc(
            neutral,
            "The daylight makes him feel weak, so we have built him his own battleground beneath this " +
                "building, where he can train his fearsome skills without being disturbed.",
        )
        chatPlayer(worried, "But he can't REALLY be unkillable...  ...can he?")
        chatNpc(
            neutral,
            "Some say he cannot die, for he has hidden his heart outside of his body to be kept forever " +
                "safe in a duck egg.",
        )
        chatNpc(
            neutral,
            "Others say he has been cursed by the gods to wander this land forever, never knowing any " +
                "peace in his life, but only combat.",
        )
        chatNpc(
            neutral,
            "Some claim that the sword he carries is the source of all his power, and if he should lose " +
                "it, then exactly one minute later, he will turn back into his true form;",
        )
        chatNpc(neutral, "A weakened, lame, old man.")
        chatPlayer(quiz, "And what do you believe?")
        chatNpc(
            neutral,
            "I believe you shouldn't look a gift horse in the mouth. He is a fearfully powerful " +
                "warrior, but more importantly; He is on OUR side, not against us.",
        )
        chatNpc(
            neutral,
            "He is content testing the battle skills of anyone taking their Fremennik trials of " +
                "manhood, and I am content knowing that should an enemy ever invade our town,",
        )
        chatNpc(
            neutral,
            "while our warriors are out on a raiding party, Koschei will be able to hold off ANY " +
                "invader long enough for us to make our return.",
        )
    }

    private suspend fun Dialogue.sardines() {
        chatNpc(laugh, "I was only joking about the raw sardines outerlander.")
        chatPlayer(sad, "Oh. Really? Oh. So I have to pass your combat trial?")
        chatNpc(neutral, "Yes.")
    }

    /* The battleground */

    private suspend fun ProtectedAccess.descend() {
        when {
            quest.hasVote(player, Trial.Warrior) -> {
                startDialogue {
                    chatPlayer(
                        neutral,
                        "I'd really rather not go back down there again, now that I have Thorvald's " +
                            "vote at the council of elders.",
                    )
                }
                return
            }
            !quest.isInProgress(player) || !player.ftThorvaldStarted -> {
                startDialogue {
                    chatNpcSpecific(
                        "Thorvald the Warrior",
                        THORVALD,
                        angry,
                        "That battleground is not for idle visitors, outerlander!",
                    )
                }
                return
            }
            carriesForbiddenGear() -> {
                startDialogue { equipmentRule() }
                return
            }
        }
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(ARENA_LANDING, TeleportType.Exempt)
        mes("Explore this battleground and find your foe...")
        player.ftKoscheiForm = 0
        fights.end(player)?.npc?.let { npcRepo.del(it, Int.MAX_VALUE) }
        fights.begin(player, clock.cycle)
        timer(KOSCHEI_TIMER, TIMER_TICKS)
    }

    private fun ProtectedAccess.carriesForbiddenGear(): Boolean {
        for (slot in Wearpos.entries) {
            if (slot.slot in ALLOWED_WEARPOS) {
                continue
            }
            if (player.worn[slot.slot] != null) {
                return true
            }
        }
        return player.inv.any { obj ->
            obj != null && getInvObj(obj).let { type ->
                type.category == RUNE_CATEGORY ||
                    (type.isEquipable && listOf(type.wearpos1, type.wearpos2, type.wearpos3).any {
                        it >= 0 && it !in ALLOWED_WEARPOS
                    })
            }
        }
    }

    private suspend fun ProtectedAccess.leaveArena() {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(1)
        endFight(player)
        telejump(HOUSE_LANDING, TeleportType.Exempt)
    }

    private fun endFight(player: Player) {
        val fight = fights.end(player)
        fight?.npc?.let { npcRepo.del(it, Int.MAX_VALUE) }
        player.ftKoscheiForm = 0
        player.clearTimer(KOSCHEI_TIMER)
    }

    private suspend fun ProtectedAccess.koscheiTick() {
        val fight = fights.fightOf(player)
        if (fight == null || !isInArena(coords) || quest.hasVote(player, Trial.Warrior)) {
            endFight(player)
            return
        }
        if (fight.yielding) {
            yieldToKoschei(fight)
            return
        }
        val koschei = fight.npc?.takeIf { it.isSlotAssigned }
        val form = player.ftKoscheiForm
        if (koschei == null) {
            if (clock.cycle - fight.spawnedAt >= REVEAL_TICKS && coords != ARENA_LANDING) {
                spawnForm(player, fight, form, coords, taunt = true)
            }
            return
        }
        if (form >= LAST_REQUIRED_FORM && clock.cycle - fight.spawnedAt >= FORM_TIME_LIMIT) {
            mes("You have failed to impress Koschei. He has left in disgust.")
            mes("You will need to improve your combat skills before challenging him again.")
            npcRepo.del(koschei, Int.MAX_VALUE)
            fights.forgetNpc(koschei)
            fight.npc = null
            fight.spawnedAt = clock.cycle
            player.ftKoscheiForm = 0
            return
        }
        if (form == FINAL_FORM && player.prayerLvl > 0) {
            statSub(PRAYER, constant = random.of(1, 2), percent = 0)
        }
        if (random.of(TAUNT_CHANCE) == 0) {
            koschei.say(taunt(player))
        }
    }

    private fun spawnForm(player: Player, fight: KoscheiFights.Fight, form: Int, near: CoordGrid, taunt: Boolean) {
        val type = formTypes[form]
        val tile = collision.freeFootprint(near, type.size, radius = SPAWN_RADIUS) ?: near
        val koschei = Npc(type, tile)
        npcRepo.add(koschei, FORM_LINGER_TICKS)
        fights.register(fight, koschei, clock.cycle)
        koschei.facePlayer(player)
        koschei.say(if (taunt) taunt(player) else FORM_LINES.getValue(form))
        koschei.opPlayer2(player, aiInteractions)
    }

    private fun taunt(player: Player): String {
        val name = player.displayName
        val self = if (player.appearance.bodyType == 1) "herself" else "himself"
        val lines =
            listOf(
                "Prepare to die, $name!",
                "I will crush your dead bones!",
                "Outerlander... you have made a mistake coming down here!",
                "What kind of a person calls $self $name?",
                "You have no idea of my true power, outerlander!",
                "Today is the day you die, outerlander!",
                "$name! Meet your doom!",
                "Prepare to face my power, outerlander!",
            )
        return lines[random.of(lines.size)]
    }

    private suspend fun StandardNpcAccess.formDefeated(index: Int) {
        val owner = fights.ownerOf(npc)?.resolve(playerList)
        val coords = npc.coords
        fights.forgetNpc(npc)
        death.deathNoDrops(this)
        if (owner == null) {
            return
        }
        val fight = fights.fightOf(owner) ?: return
        fight.npc = null
        if (index == FINAL_FORM) {
            launcher.launch(owner) { koscheiVanquished() }
            return
        }
        val next = index + 1
        owner.ftKoscheiForm = next
        spawnForm(owner, fight, next, coords, taunt = false)
        if (next == FINAL_FORM) {
            launcher.launch(owner) { statSub(PRAYER, constant = player.prayerLvl, percent = 0) }
        }
    }

    /**
     * The fourth Koschei never lands the killing blow: once the challenger is down to their last
     * breath his hits stop, and the fight ends on the next tick.
     */
    private fun spareOnLastBreath(player: Player, fromNpc: Boolean, sourceSlot: Int?) {
        if (!fromNpc || player.ftKoscheiForm != FINAL_FORM) {
            return
        }
        val fight = fights.fightOf(player) ?: return
        val koschei = fight.npc ?: return
        if (sourceSlot != koschei.slotId || player.hitpoints > LAST_BREATH) {
            return
        }
        fight.yielding = true
    }

    private suspend fun ProtectedAccess.yieldToKoschei(fight: KoscheiFights.Fight) {
        fight.npc?.let {
            fights.forgetNpc(it)
            npcRepo.del(it, Int.MAX_VALUE)
        }
        fights.end(player)
        clearTimer(KOSCHEI_TIMER)
        player.ftKoscheiForm = 0
        stopAction()
        combatClearQueue()
        player.clearInteraction()
        mes("Oh dear you are...")
        resetAnim()
        delay(1)
        anim(KNEEL_SEQ)
        animProtect(true)
        delay(KNEEL_TICKS)
        animProtect(false)
        telejump(UPSTAIRS_LANDING, TeleportType.Exempt)
        mes("...still alive somehow?")
        startDialogue {
            chatNpcSpecific(
                "Thorvald the Warrior",
                THORVALD,
                laugh,
                "Hahaha! Well fought, outerlander! Now come down from there, you have passed my trial " +
                    "with flying colours!",
            )
            chatPlayer(confused, "But... I don't understand... I did not manage to beat Koschei...")
            chatNpcSpecific(
                "Thorvald the Warrior",
                THORVALD,
                happy,
                "I would be honoured to represent you to the council as worthy of being a Fremennik " +
                    "after watching that superb battle!",
            )
        }
        quest.grantVote(this, Trial.Warrior)
    }

    private suspend fun ProtectedAccess.koscheiVanquished() {
        fights.end(player)
        clearTimer(KOSCHEI_TIMER)
        player.ftKoscheiForm = 0
        mes("Koschei disappears into a mist...")
        invAddOrDrop(objRepo, FREMENNIK_BLADE)
        mes("...and leaves behind his Fremennik blade.")
        quest.grantVote(this, Trial.Warrior)
    }

    companion object {
        val FORMS =
            listOf("npc.viking_enemy1", "npc.viking_enemy2", "npc.viking_enemy3", "npc.viking_enemy4")

        const val LAST_REQUIRED_FORM = 2
        const val FINAL_FORM = 3

        val FORM_LINES =
            mapOf(
                1 to
                    "It seems you have some idea of combat after all, outerlander! I will not hold " +
                    "back so much this time!",
                2 to "Impressive start... But now we fight for real!",
                3 to
                    "You show some skill at combat... I will hold back no longer! This time you lose " +
                    "your prayer however, and fight like a warrior!",
            )

        const val HOUSE_LADDER = "loc.viking_warrior_ladder"
        const val ARENA_LADDER = "loc.viking_warrior_ladder_up"
        const val RAW_SARDINE = "obj.raw_sardine"
        const val FREMENNIK_BLADE = "obj.viking_sword"
        const val KOSCHEI_TIMER = "timer.fremtrials_koschei"
        const val PRAYER = "stat.prayer"

        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val CLIMB_UP_SEQ = "seq.human_climbing"
        const val KNEEL_SEQ = "seq.viking_drop_to_knee"

        val ARENA_LANDING = CoordGrid(2672, 10098, 2)
        val HOUSE_LANDING = CoordGrid(2668, 3694, 0)
        val UPSTAIRS_LANDING = CoordGrid(2668, 3694, 1)

        /** Amulets and rings may be worn into the battleground; nothing else may. */
        val ALLOWED_WEARPOS = setOf(Wearpos.Front.slot, Wearpos.Ring.slot)
        const val RUNE_CATEGORY = 149

        const val TIMER_TICKS = 2
        const val REVEAL_TICKS = 6
        const val SPAWN_RADIUS = 4
        const val TAUNT_CHANCE = 8
        const val LAST_BREATH = 2
        const val KNEEL_TICKS = 3

        /** Koschei's third and fourth forms leave in disgust after ten minutes. */
        const val FORM_TIME_LIMIT = 1000
        const val FORM_LINGER_TICKS = 1200

        fun isInArena(coords: CoordGrid): Boolean =
            coords.level == 2 && coords.x in 2638..2675 && coords.z in 10060..10102
    }
}

/**
 * Nobody may fight another challenger's Koschei, dying beneath Thorvald's house costs nothing, and
 * the Fremennik blade and helms are for Fremenniks only.
 */
class WarriorsTrialHooks
@Inject
constructor(
    private val fights: KoscheiFights,
    private val quest: FremennikTrialsQuest,
    private val launcher: Provider<ProtectedAccessLauncher>,
    private val npcRepo: Provider<NpcRepository>,
) : NpcAttackValidateHook, PlayerDeathHook, PlayerRespawnHook, PlayerDeathCleanupHook, PlayerRestrictionHook {
    private val formIds by lazy { WarriorsTrial.FORMS.map { it.asRSCM(RSCMType.NPC) }.toSet() }
    private val fremennikOnly by lazy { FREMENNIK_ONLY.map { it.asRSCM(RSCMType.OBJ) }.toSet() }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in formIds) {
            return NpcAttackValidateResult.Pass
        }
        val owner = fights.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("This is not your opponent.")
        }
        return NpcAttackValidateResult.Pass
    }

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!WarriorsTrial.isInArena(context.coords)) {
            return null
        }
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = null,
            dropDuration = 0,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }

    override fun respawn(player: Player): CoordGrid? =
        if (fights.fightOf(player) != null) WarriorsTrial.HOUSE_LANDING else null

    override fun cleanup(player: Player) {
        val fight = fights.end(player) ?: return
        fight.npc?.let { npcRepo.get().del(it, Int.MAX_VALUE) }
        val finalForm = player.ftKoscheiForm == WarriorsTrial.FINAL_FORM
        player.ftKoscheiForm = 0
        if (finalForm) {
            launcher.get().launch(player) { quest.grantVote(this, Trial.Warrior) }
        }
    }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id !in fremennikOnly) {
            return null
        }
        if (quest.isComplete(player)) {
            return null
        }
        return if (action.obj.id == FREMENNIK_BLADE_ID) {
            "Only Fremenniks are worthy of wielding this blade."
        } else {
            "You need to complete The Fremennik Trials to wear this."
        }
    }

    private companion object {
        val FREMENNIK_ONLY =
            listOf(
                "obj.viking_sword",
                "obj.viking_helmet_range",
                "obj.viking_helmet_crush",
                "obj.viking_helmet_slash",
                "obj.viking_helmet_magic",
            )
        val FREMENNIK_BLADE_ID by lazy { "obj.viking_sword".asRSCM(RSCMType.OBJ) }
    }
}
