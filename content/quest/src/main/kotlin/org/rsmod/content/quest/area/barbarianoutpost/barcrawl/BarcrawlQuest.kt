package org.rsmod.content.quest.area.barbarianoutpost.barcrawl

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Alfred Grimhand's Barcrawl.
 *
 * `varp.barcrawl` holds the stage, which the Barbarian guard multinpc reads (0 and 1 show the
 * pre-crawl guard, [STAGE_COMPLETE] the one with Toggle-vials); `varbit.barcrawl_progress` on the
 * shared varp 3403 mirrors it for the quest list. The cache has no vars for the individual bars,
 * so the card's signatures are a bitmask quest attribute, cleared whenever the guard hands out a
 * fresh card.
 *
 * Every barcrawl bartender serves through [serve]; the bartenders whose everyday dialogue lives in
 * other modules add the "I'm doing Alfred Grimhand's Barcrawl." option when [canServe] allows it.
 * Using the card on a bartender skips straight to the drink.
 */
@Singleton
class BarcrawlQuest @Inject constructor() :
    QuestScript(
        QUEST_KEY,
        "varp.barcrawl",
        rewards {
            extra("Access to the Barbarian Outpost agility course, and the vial-smashing trick.")
        },
        ItemRewardDisplay(CARD),
    ) {
    private val signatures = quest.attribute(name = "SIGNATURES", default = 0)

    override fun ScriptContext.init() {
        quest.onVarSync { player ->
            VarPlayerIntMapSetter.set(player, PROGRESS_VARBIT, quest.getQuestStage(player))
        }

        onOpHeld1(CARD) { readCard() }

        val card = ServerCacheManager.getItem(CARD.asRSCM(RSCMType.OBJ)) ?: error("Missing $CARD")
        for ((npc, bar) in BARTENDERS) {
            val type = ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: error("Missing $npc")
            onOpNpcU(type, card) { useCardOn(it.npc, bar) }
        }
    }

    override fun subTitle(): String =
        "talking to a <col=800000>Barbarian guard</col> at the gate of the " +
            "<col=800000>Barbarian Outpost</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The <red>Barbarian guards</red> at the <red>Barbarian Outpost</red> only let " +
                    "barbarians in. To prove I can drink like one I must complete the " +
                    "<red>Alfred Grimhand Barcrawl</red>.",
            ) {}

            if (!hasCard(access)) {
                objective(
                    "I have lost my <red>barcrawl card</red>. The guard can give me a new one, " +
                        "but I will have to start the barcrawl all over again.",
                ) {}
                return@questJournal
            }

            objective(
                "Each bar on my <red>barcrawl card</red> will serve me its strongest drink and " +
                    "sign the card:",
            ) {}
            for (bar in BarcrawlBar.entries) {
                val entry = "${bar.barName} - ${bar.drinkName}"
                if (isSigned(access.player, bar)) strike(entry) else line(entry)
            }

            if (allSigned(access.player)) {
                objective(
                    "Every bar has signed my card. I should take it back to a " +
                        "<red>Barbarian guard</red>.",
                ) {}
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I drank the strongest drink in each of the ten bars on the Alfred Grimhand " +
                    "barcrawl card and had every one of them signed.",
            )
            line(
                "The barbarians now let me into the Barbarian Outpost agility course, and one of " +
                    "the guards showed me how to smash my empty vials as I finish my potions.",
            )
        }

    fun isSigned(player: Player, bar: BarcrawlBar): Boolean = signatures.get(player) and bar.mask != 0

    fun allSigned(player: Player): Boolean =
        signatures.get(player) and BarcrawlBar.ALL_SIGNED == BarcrawlBar.ALL_SIGNED

    fun clearSignatures(player: Player) {
        signatures.set(player, 0)
    }

    private fun sign(player: Player, bar: BarcrawlBar) {
        signatures.set(player, signatures.get(player) or bar.mask)
    }

    fun canServe(player: Player, bar: BarcrawlBar): Boolean =
        quest.isQuestInProgress(player) && CARD in player.inv && !isSigned(player, bar)

    fun hasCard(access: ProtectedAccess): Boolean =
        CARD in access.player.inv || access.bank.contains(CARD)

    /**
     * The player tells [Dialogue.npc] that they are on the barcrawl and, if they can pay, is served
     * [bar]'s drink, suffers its effects and has the card signed. [server] names whoever signs in
     * the chat messages (the Rising Sun's barmaids each sign under their own name).
     */
    suspend fun Dialogue.serve(bar: BarcrawlBar, server: String = "The bartender") {
        chatPlayer(neutral, BARCRAWL_LINE)
        for (line in bar.pitch) {
            speak(line)
        }
        if (player.inv.count(COINS) < bar.price) {
            chatPlayer(sad, bar.brokeLine)
            return
        }
        if (access.invDel(access.inv, COINS, bar.price).failure) {
            return
        }
        access.drink(bar, server)
        sign(player, bar)
        bar.afterwards?.let { speak(it) }
    }

    private suspend fun Dialogue.speak(line: BarcrawlLine) {
        when (line) {
            is BarcrawlLine.Npc -> chatNpc(happy, line.text)
            is BarcrawlLine.Player -> chatPlayer(drunk, line.text)
            is BarcrawlLine.Overhead -> {
                access.say(line.text)
                if (line.text == HICCUP_TEXT) {
                    access.soundSynth(HICCUP_SOUND)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.drink(bar: BarcrawlBar, server: String) {
        for ((index, message) in bar.messages.withIndex()) {
            if (index == DRINK_MESSAGE) {
                anim(DRINK_SEQ)
                soundSynth(DRINK_SOUND)
            }
            mes(message.format(server))
            if (index == DRINK_MESSAGE) {
                applyEffect(bar.effect)
            }
            if (index != bar.messages.lastIndex) {
                delay(MESSAGE_TICKS)
            }
        }
        val sway = bar.effect.swayTicks ?: return
        delay(random.of(sway))
        camShakeResetAll()
    }

    private fun ProtectedAccess.applyEffect(effect: DrinkEffect) {
        for (stat in effect.drained) {
            statSub(stat, DRAIN_CONSTANT, DRAIN_PERCENT)
        }
        val percent = effect.damagePercent
        if (percent != null) {
            val damage =
                (player.baseHitpointsLvl * random.of(percent) / 100)
                    .coerceAtLeast(1)
                    .coerceAtMost(player.hitpoints - 1)
            if (damage > 0) {
                queueHit(delay = 1, type = HitType.Typeless, damage = damage)
            }
        }
        if (effect.swayTicks != null) {
            camShake(CamShakeAxis.PAN_LEFT_RIGHT, random = 0, amplitude = SWAY_AMPLITUDE, rate = SWAY_RATE)
        }
    }

    private suspend fun ProtectedAccess.useCardOn(npc: Npc, bar: BarcrawlBar) {
        if (!canServe(player, bar)) {
            mes("Nothing interesting happens.")
            return
        }
        startDialogue(npc) { serve(bar, npc.visType.name) }
    }

    private suspend fun ProtectedAccess.readCard() {
        if (quest.isQuestCompleted(player) || allSigned(player)) {
            mes("You are too drunk to be able to read the barcrawl card.")
            return
        }
        ifOpenMainModal(SCROLL_INTERFACE)
        val lines = buildList {
            add("The Official Alfred Grimhand Barcrawl")
            add("")
            for (bar in BarcrawlBar.entries) {
                val colour = if (isSigned(player, bar)) SIGNED_COLOUR else UNSIGNED_COLOUR
                add("<col=$colour>${bar.barName} - ${bar.drinkName}</col>")
            }
        }
        for (line in 1..SCROLL_LINES) {
            ifSetText("component.scroll:line$line", lines.getOrElse(line - 1) { "" })
        }
        soundSynth(PAPER_SOUND)
    }

    companion object {
        const val QUEST_KEY = "miniquest_barcrawl"
        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val CARD = "obj.barcrawl_card"
        const val COINS = "obj.coins"
        const val BARCRAWL_LINE = "I'm doing Alfred Grimhand's Barcrawl."

        const val PROGRESS_VARBIT = "varbit.barcrawl_progress"

        const val DRINK_SEQ = "seq.human_eat"
        const val DRINK_SOUND = "synth.drink"
        const val HICCUP_SOUND = "synth.hiccup"
        const val HICCUP_TEXT = "Hiccup!"
        const val PAPER_SOUND = "synth.paper_move"

        const val DRINK_MESSAGE = 1
        const val MESSAGE_TICKS = 2
        const val DRAIN_CONSTANT = 5
        const val DRAIN_PERCENT = 4
        const val SWAY_AMPLITUDE = 12
        const val SWAY_RATE = 2

        const val SCROLL_INTERFACE = "interface.scroll"
        const val SCROLL_LINES = 14
        const val SIGNED_COLOUR = "00ff00"
        const val UNSIGNED_COLOUR = "ff0000"

        val BARTENDERS =
            listOf(
                "npc.bluemoon_bartender" to BarcrawlBar.BlueMoon,
                "npc.blurberry" to BarcrawlBar.Blurberry,
                "npc.deadmans_bartender" to BarcrawlBar.DeadMansChest,
                "npc.dragon_bartender" to BarcrawlBar.DragonInn,
                "npc.flyinghorse_bartender" to BarcrawlBar.FlyingHorseInn,
                "npc.foresters_bartender" to BarcrawlBar.ForestersArms,
                "npc.jollyboar_bartender" to BarcrawlBar.JollyBoarInn,
                "npc.zembo" to BarcrawlBar.KaramjaSpiritsBar,
                "npc.risingsun_barmaid" to BarcrawlBar.RisingSun,
                "npc.risingsun_barmaid2" to BarcrawlBar.RisingSun,
                "npc.risingsun_barmaid3" to BarcrawlBar.RisingSun,
                "npc.rustyanchor_bartender" to BarcrawlBar.RustyAnchor,
            )
    }
}
