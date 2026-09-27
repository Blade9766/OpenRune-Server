package org.rsmod.content.other.castlewars

import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.uncert
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The lobby west of Yanille: the three team portals, the waiting rooms, the scoreboards and Lanthus. */
internal class CastleWarsLobbyScript @Inject constructor(private val game: CastleWarsGame) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.castlewars_saradomin_tele") { enterPortal(Team.Saradomin) }
        onOpLoc1("loc.castlewars_zamorak_tele") { enterPortal(Team.Zamorak) }
        onOpLoc1("loc.castlewars_random_tele") { enterPortal(null) }
        onOpLoc1("loc.castlewars_saradomin_exit") { leaveWaitingRoom() }
        onOpLoc1("loc.castlewars_zamorak_exit") { leaveWaitingRoom() }

        onOpLoc1("loc.castlewars_scoreboard") { viewScoreboard() }
        onOpLoc3("loc.castlewars_scoreboard") { viewStats() }

        onOpNpc1(LANTHUS) { startDialogue(it.npc) { lanthus() } }
        onOpNpc3(LANTHUS) { toggleXp() }
        onOpNpc4(LANTHUS) { openOmnishop(SHOP) }
        onOpNpcU(LANTHUS) { useOnLanthus(it.npc, it.objType) }
    }

    /* Portals */

    private suspend fun ProtectedAccess.enterPortal(portal: Team?) {
        if (game.isParticipant(player)) {
            return
        }
        if (player.worn[Wearpos.Back.slot] != null) {
            mesbox("You can't wear a cape into the arena. You will be given your team's colours when you enter.")
            return
        }
        val banned = (player.inv.objs.asSequence() + player.worn.objs.asSequence()).filterNotNull().map(::getInvObj)
            .firstOrNull { !it.isAllowedInArena() }
        if (banned != null) {
            mesbox("You may not take non-combat items into the arena. Please bank your ${uncert(banned).name}.")
            return
        }
        val team =
            game.chooseTeam(portal).getOrElse {
                mesbox(it.message ?: "You can't join that team right now.")
                return
            }
        player.worn[Wearpos.Back.slot] = InvObj(team.cloak)
        val punished = wrongGodNpc(portal, team)
        if (punished != null) {
            transmog(punished)
        }
        rebuildAppearance()
        soundSynth("synth.teleport_all")
        telejump(game.scatter(team.waitingRoom), TeleportType.Exempt)
        game.joinWaitingRoom(player, team)
        ifOpenOverlay(CastleWarsScript.WAITING_OVERLAY)
        game.syncVars(player)
        when {
            punished == CastleWars.GUTHIX_NPC -> mes("Guthix doesn't approve of your choice of equipment!")
            punished != null -> mes("${team.displayName} is angered by the gods you are wearing!")
            else -> mes("You join the ${team.displayName} team.")
        }
    }

    /**
     * Each god turns anyone wearing the colours of a rival into a harmless animal for the wait:
     * Saradomin a rabbit, Zamorak an imp, and the Guthix portal a sheep.
     */
    private fun ProtectedAccess.wrongGodNpc(portal: Team?, team: Team): String? {
        val allowed = portal?.name?.lowercase() ?: "guthix"
        val rivals = CastleWars.GOD_NAMES.filterKeys { it != allowed }.values.flatten()
        val wearsRival =
            player.worn.objs.filterNotNull().any { obj ->
                val name = getInvObj(obj).name.lowercase()
                rivals.any { it in name } && !getInvObj(obj).isType(team.cloak)
            }
        if (!wearsRival) {
            return null
        }
        return if (portal == null) CastleWars.GUTHIX_NPC else team.wrongGodNpc
    }

    private fun ItemServerType.isAllowedInArena(): Boolean {
        if (internalName in ALWAYS_ALLOWED || internalName in CastleWars.GAME_ITEMS) {
            return true
        }
        if (interfaceOptions.any { it == "Eat" }) {
            return false
        }
        if (isEquipable || interfaceOptions.any { it == "Drink" }) {
            return true
        }
        val lower = name.lowercase()
        return lower.endsWith(" rune") || lower.contains("rune pouch")
    }

    private fun ProtectedAccess.leaveWaitingRoom() {
        player.strongQueue(CastleWarsQueues.LEAVE_GAME, 1)
    }

    /* Scoreboards */

    private fun ProtectedAccess.viewScoreboard() {
        ifOpenMainModal("interface.castlewars_score")
        ifSetText(
            "component.castlewars_score:castlewars_saradomin_totalscore",
            "Saradomin: ${game.seasonWins.getValue(Team.Saradomin)}",
        )
        ifSetText(
            "component.castlewars_score:castlewars_zamorak_totalscore",
            "Zamorak: ${game.seasonWins.getValue(Team.Zamorak)}",
        )
    }

    private suspend fun ProtectedAccess.viewStats() {
        val played = player.vars["varp.castlewars_games_played"]
        val wins = player.vars["varp.castlewars_wins"]
        val losses = player.vars["varp.castlewars_loss"]
        val draws = (played - wins - losses).coerceAtLeast(0)
        mesbox(
            "You have played $played ${if (played == 1) "game" else "games"} of Castle Wars: " +
                "$wins won, $losses lost and $draws drawn.",
        )
    }

    /* Lanthus */

    private suspend fun Dialogue.lanthus() {
        chatNpc(happy, "Good day, how may I help you?")
        val bracelets = CastleWars.BRACELETS.any { player.inv.contains(it) }
        val stored = player.vars["varbit.castlewars_bracelet_charges"]
        val ava = CastleWars.AVAS_DEVICES.keys.any { player.inv.contains(it) }
        val options =
            buildList {
                add("What is this place?" to Topic.Place)
                if (ava) add("Take a look at this dead chicken I've got." to Topic.Ava)
                add("What do you have for trade?" to Topic.Trade)
                add("Do you have a manual? I'd like to learn how to play!" to Topic.Manual)
                if (bracelets) add("Is there something I can do with these bracelets?" to Topic.Bracelets)
                if (stored > 0) {
                    add("How many bracelet charges do I have stored?" to Topic.Charges)
                    if (player.vars["varbit.castlewars_bracelet_paused"] == 0) {
                        add("Can I stop using my stored bracelet charges?" to Topic.Pause)
                    } else {
                        add("I'd like to use my stored bracelet charges again." to Topic.Resume)
                    }
                }
            }
        when (choose(options)) {
            Topic.Place -> place()
            Topic.Ava -> showAva()
            Topic.Trade -> access.openOmnishop(SHOP)
            Topic.Manual -> manual()
            Topic.Bracelets -> braceletInfo()
            Topic.Charges -> {
                chatPlayer(quiz, "How many bracelet charges do I have stored?")
                chatNpc(
                    neutral,
                    "Currently, I'm storing $stored charges for you. If you hand me more " +
                        "bracelets, I can store more charges for you.",
                )
            }
            Topic.Pause -> {
                chatPlayer(quiz, "Can I stop using my stored bracelet charges?")
                chatNpc(
                    confused,
                    "That seems like a strange thing to ask for, but very well, I'll stop using " +
                        "your stored bracelet charges on you. Talk to me if you want to start using them again.",
                )
                VarPlayerIntMapSetter.set(player, "varbit.castlewars_bracelet_paused", 1)
            }
            Topic.More -> Unit
            Topic.Resume -> {
                chatPlayer(happy, "I'd like to use my stored bracelet charges again.")
                chatNpc(
                    happy,
                    "Ah, that's more like it! Yes, I'll start using your stored bracelet charges on " +
                        "you if you don't have a Castle Wars bracelet equipped when a game starts. Talk " +
                        "to me again if you decide you'd rather not have me do that.",
                )
                VarPlayerIntMapSetter.set(player, "varbit.castlewars_bracelet_paused", 0)
            }
        }
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Topic>>): Topic =
        when (options.size) {
            2 -> choice2(options[0].first, options[0].second, options[1].first, options[1].second)
            3 ->
                choice3(
                    options[0].first, options[0].second,
                    options[1].first, options[1].second,
                    options[2].first, options[2].second,
                )
            4 ->
                choice4(
                    options[0].first, options[0].second,
                    options[1].first, options[1].second,
                    options[2].first, options[2].second,
                    options[3].first, options[3].second,
                )
            5 ->
                choice5(
                    options[0].first, options[0].second,
                    options[1].first, options[1].second,
                    options[2].first, options[2].second,
                    options[3].first, options[3].second,
                    options[4].first, options[4].second,
                )
            else -> {
                val page = options.take(4) + ("More options..." to Topic.More)
                val picked = choose(page)
                if (picked == Topic.More) choose(options.drop(4)) else picked
            }
        }

    private suspend fun Dialogue.place() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            happy,
            "This is the great Castle Wars arena! Here you can fight for the glory of Saradomin or Zamorak.",
        )
        when (
            choice3(
                "Really, how do I do that?", 1,
                "Are there any rules?", 2,
                "What can I win?", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Really, how do I do that?")
                chatNpc(
                    neutral,
                    "Easy, you just step through one of the three portals. To join Zamorak, pass " +
                        "through the red portal. To join Saradomin, pass through the blue portal. If " +
                        "you don't mind then pass through the green portal.",
                )
            }
            2 -> {
                chatPlayer(quiz, "Are there any rules?")
                chatNpc(
                    neutral,
                    "Of course, there are always rules. Firstly you can't wear a cape as you enter " +
                        "the portal, you'll be given your team colours to wear while in the arena.",
                )
                chatNpc(
                    neutral,
                    "You're also prohibited from taking non-combat related items in with you. So " +
                        "you should only have equipment, potions, and runes on you.",
                )
                chatNpc(
                    neutral,
                    "Secondly, attacking your own team or your team's defences isn't allowed. You " +
                        "don't want to be angering your patron god, do you? Other than that, just " +
                        "have fun and enjoy it!",
                )
                chatPlayer(happy, "Great! Oh, how do I win the game?")
                chatNpc(
                    neutral,
                    "The aim is to get into your opponents' castle and take their team standard. " +
                        "Then bring that back and capture it on your team's standard.",
                )
            }
            else -> {
                chatPlayer(quiz, "What can I win?")
                chatNpc(
                    happy,
                    "Players on the winning team will receive 2 Castle Wars Tickets which you can " +
                        "trade back to me for other items. In the event of a draw every player will " +
                        "get 1 ticket.",
                )
            }
        }
    }

    private suspend fun Dialogue.manual() {
        chatPlayer(quiz, "Do you have a manual? I'd like to learn how to play!")
        chatNpc(happy, "Sure, here you go.")
        if (access.invAdd(player.inv, MANUAL, strict = false).success) {
            objbox(MANUAL, "Lanthus hands you a Castle Wars manual.")
        } else {
            chatNpc(neutral, "Oh, you don't have any space to carry it.")
        }
    }

    private suspend fun Dialogue.braceletInfo() {
        chatPlayer(quiz, "Is there something I can do with these bracelets?")
        chatNpc(
            neutral,
            "Yes, if you're wearing one when you enter a game of Castle Wars, it will use one " +
                "charge to empower your attacks against flag bearers and give you increased " +
                "healing from bandages.",
        )
        chatNpc(neutral, "Bear in mind that you only need to be wearing it when you enter the game to get the benefit.")
        chatNpc(
            neutral,
            "If you'd prefer, if you give the bracelet to me, I can grant you the buff instead, but " +
                "I'll only do it once for each charge you give me.",
        )
        chatNpc(neutral, "Just show me the bracelet and I'll take it off your hands.")
    }

    private suspend fun Dialogue.showAva() {
        val device = CastleWars.AVAS_DEVICES.entries.filter { player.inv.contains(it.key) }.maxBy { it.value }
        val tier = device.value
        val shownTier = player.vars["varbit.castlewars_ava_reward_tier"]
        chatPlayer(neutral, "Take a look at this dead chicken I've got.")
        when {
            player.vars["varbit.castlewars_ava_reward"] == 0 -> {
                chatNpc(confused, "... I'm looking at the dead chicken. Why am I looking at the dead chicken?")
                chatPlayer(
                    neutral,
                    "It's a special device, invented by a friend of mine. If I'm wearing it while " +
                        "ranging, it uses magnets to retrieve some of my ammunition.",
                )
                chatNpc(
                    shocked,
                    "Oh. Gosh. That's clever. So are you wanting to take it into Castle Wars? The " +
                        "gods expect people to fight in the team capes.",
                )
                chatPlayer(quiz, "Can they be a bit more flexible, please?")
                chatNpc(neutral, "I'll ask. Hold on a minute...")
                communeWithGods(tier)
                chatNpc(
                    happy,
                    "The gods say they'll protect your ammunition in the same way that the chicken " +
                        "would, while you're fighting in Castle Wars. You won't need to wear the chicken here.",
                )
                chatPlayer(happy, "Thank you.")
            }
            tier > shownTier -> {
                chatNpc(
                    shocked,
                    "I remember you! You showed me this dead chicken device before, but this one is better is it!?",
                )
                chatPlayer(happy, "That's right!")
                chatNpc(neutral, "Hold on...")
                communeWithGods(tier)
                chatNpc(
                    happy,
                    "The gods say they'll protect your ammunition in the same way that this better " +
                        "chicken would, while you're fighting in Castle Wars.",
                )
                chatPlayer(happy, "Thank you.")
            }
            else -> {
                chatNpc(
                    neutral,
                    "I remember you. You said your dead chicken could help you save ammunition. The " +
                        "gods agreed that you could have the same bonus in Castle Wars without needing " +
                        "to wear the chicken.",
                )
                chatPlayer(neutral, "Yes, that's right.")
                chatNpc(
                    angry,
                    "So you don't need to keep showing me the dead chicken, do you? Take it away!",
                )
            }
        }
    }

    private suspend fun Dialogue.communeWithGods(tier: Int) {
        chatNpc(neutral, "... (Lanthus communes with the gods.)")
        VarPlayerIntMapSetter.set(player, "varbit.castlewars_ava_reward", 1)
        VarPlayerIntMapSetter.set(player, "varbit.castlewars_ava_reward_tier", tier)
    }

    private suspend fun ProtectedAccess.toggleXp() {
        val disabled = player.vars["varbit.castlewars_xp_disabled"] == 0
        VarPlayerIntMapSetter.set(player, "varbit.castlewars_xp_disabled", if (disabled) 1 else 0)
        mes(
            if (disabled) {
                "You will no longer gain combat experience in Castle Wars."
            } else {
                "You will now gain combat experience in Castle Wars."
            },
        )
    }

    private suspend fun ProtectedAccess.useOnLanthus(npc: Npc, obj: ItemServerType) {
        val braceletIndex = CastleWars.BRACELETS.indexOf(obj.internalName)
        if (braceletIndex >= 0) {
            val charges = CastleWars.BRACELETS.size - braceletIndex
            val stored = player.vars["varbit.castlewars_bracelet_charges"]
            if (stored + charges > MAX_STORED_CHARGES) {
                startDialogue(npc) { chatNpc(neutral, "I can't hold any more charges for you right now.") }
                return
            }
            invDel(inv, obj.internalName, 1)
            VarPlayerIntMapSetter.set(player, "varbit.castlewars_bracelet_charges", stored + charges)
            objbox(
                obj.internalName,
                "Lanthus takes your bracelet from you, granting you $charges " +
                    "${if (charges == 1) "charge" else "charges"} for your next games of Castle Wars. " +
                    "You have ${stored + charges} charges stored.",
            )
            return
        }
        if (obj.internalName in CastleWars.AVAS_DEVICES) {
            startDialogue(npc) { showAva() }
            return
        }
        startDialogue(npc) { chatNpc(neutral, "I don't need that, thank you.") }
    }

    private enum class Topic {
        Place,
        Ava,
        Trade,
        Manual,
        Bracelets,
        Charges,
        Pause,
        Resume,
        More,
    }

    private companion object {
        const val LANTHUS = "npc.castlewars_judge"
        const val SHOP = "dbrow.cw_shop_data"
        const val MANUAL = "obj.castlewars_manual"
        const val MAX_STORED_CHARGES = 1023

        val ALWAYS_ALLOWED = setOf(CastleWars.TICKET, "obj.castlewars_manual")
    }
}
