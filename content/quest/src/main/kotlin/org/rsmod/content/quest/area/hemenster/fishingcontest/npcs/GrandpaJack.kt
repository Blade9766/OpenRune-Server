package org.rsmod.content.quest.area.hemenster.fishingcontest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.COINS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.GRANDPA_JACK
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.ROD
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.ROD_PRICE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.SPADE
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Grandpa Jack, a retired champion living just north-east of the competition grounds. He hands out
 * the clues the contest needs (the bait, where to dig it and the best spot), hints at what is odd
 * about the stranger without spelling it out, and sells a plain rod for five coins.
 */
class GrandpaJack @Inject constructor(private val fc: FishingContestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GRANDPA_JACK) { startDialogue(it.npc) { jack() } }
    }

    private suspend fun Dialogue.jack() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Hello young 'un! Come to visit old Grandpa Jack? Sit down, sit down. Mind the cat.")
        val options =
            buildList {
                add("Can I have some fishing tips?" to Topic.TIPS)
                add("I'd like to buy a fishing rod." to Topic.ROD)
                if (fc.isContestStage(player)) {
                    add("Do you have a spade I could borrow?" to Topic.SPADE)
                }
                add("Tell me about the fishing competition." to Topic.COMPETITION)
                add("I'd better be going." to Topic.LEAVE)
            }
        when (menu(options)) {
            Topic.TIPS -> tips()
            Topic.ROD -> sellRod()
            Topic.SPADE -> lendSpade()
            Topic.COMPETITION -> competition()
            Topic.LEAVE -> {
                chatPlayer(neutral, "I'd better be going.")
                chatNpc(happy, "Come back any time. Bring biscuits.")
            }
        }
    }

    private suspend fun Dialogue.tips() {
        chatPlayer(quiz, "Can I have some fishing tips?")
        chatNpc(happy, "Tips? From the three-time Hemenster champion? Pull up a stool.")
        chatNpc(neutral, "First: bait. Shop bait's fine for sardines, but the big carp in that lake only want red vine worms. Juicy little wrigglers.")
        chatPlayer(quiz, "Where do I find red vine worms?")
        chatNpc(neutral, "They live among the roots of the red vines in McGrubor's Wood, just north of here. You'll need a spade to dig them out. Grab a few; worms have a way of disappearing.")
        chatNpc(worried, "McGrubor keeps his gate locked and his dogs hungry. Mind you, in my day I'd squeeze in through a loose bit of the fence up the north side. Before the pies caught up with me.")
        chatNpc(neutral, "Second: location. The fattest fish gather where the wall pipes on the north building empty into the lake. Warm water, plenty of scraps.")
        chatNpc(sad, "Trouble is, a fellow in a long black cloak has had that spot every round this season. Pale as a boiled cod. Never seen him by day without his hood up.")
        chatNpc(confused, "He came round for supper once. Wouldn't touch the garlic bread, went green when my wife brought out her garlic soup, and left before we'd even said grace. Odd sort.")
    }

    private suspend fun Dialogue.sellRod() {
        chatPlayer(quiz, "I'd like to buy a fishing rod.")
        chatNpc(neutral, "I've a spare plain rod, same as the rules ask for. Yours for $ROD_PRICE coins.")
        if (!choice2("Yes please.", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        chatPlayer(happy, "Yes please.")
        if (access.inv.count(COINS) < ROD_PRICE) {
            chatNpc(sad, "Five coins, young 'un. I can't eat good intentions.")
            return
        }
        if (access.invDel(access.inv, COINS, ROD_PRICE).failure) {
            return
        }
        if (access.invAdd(access.inv, ROD, 1).failure) {
            access.invAdd(access.inv, COINS, ROD_PRICE)
            chatNpc(confused, "You've no room for it! Free up some space and I'll sell it to you.")
            return
        }
        objbox(ROD, zoom = 400, "You hand over $ROD_PRICE coins and receive a fishing rod.")
        chatNpc(happy, "Treat her gently and she'll land you a whopper.")
    }

    private suspend fun Dialogue.lendSpade() {
        chatPlayer(quiz, "Do you have a spade I could borrow?")
        if (SPADE in access.inv) {
            chatNpc(confused, "You've one right there, young 'un. Are you sure you're feeling well?")
            return
        }
        if (access.invAdd(access.inv, SPADE, 1).failure) {
            chatNpc(neutral, "There's one by the door, but you've no room to carry it.")
            return
        }
        objbox(SPADE, zoom = 400, "Grandpa Jack hands you a rusty old spade.")
        chatNpc(happy, "Keep it. My worm-digging days are behind me.")
    }

    private suspend fun Dialogue.competition() {
        chatPlayer(quiz, "Tell me about the fishing competition.")
        chatNpc(happy, "Bonzo runs it, in the grounds just south-west of here. Five coins a round, and whoever lands the biggest fish wins the trophy.")
        chatNpc(neutral, "It's pass holders and club members only. Morris on the gate takes his job very seriously. Too seriously, if you ask me.")
        chatNpc(laugh, "Big Dave's won it three seasons running now. Never with anything bigger than a trout, mind.")
    }

    private enum class Topic {
        TIPS,
        ROD,
        SPADE,
        COMPETITION,
        LEAVE,
    }
}
