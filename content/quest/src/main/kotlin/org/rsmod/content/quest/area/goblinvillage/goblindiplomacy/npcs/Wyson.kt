package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Wyson the gardener in Falador Park, the only grower of the woad leaves Aggie needs for blue dye. */
class Wyson : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(WYSON) { startDialogue(it.npc) { wyson() } }
    }

    private suspend fun Dialogue.wyson() {
        chatNpc(happy, "I'm the head gardener around here. If you're looking for woad leaves, or if you need help with owt, I'm yer man.")
        when (
            choice3(
                "Yes please, I need woad leaves.", 1,
                "How about ME helping YOU instead?", 2,
                "Sorry, but I'm not interested.", 3,
            )
        ) {
            1 -> woadLeaves()
            2 -> mole()
            3 -> {
                chatPlayer(neutral, "Sorry, but I'm not interested.")
                chatNpc(neutral, "Fair enough.")
            }
        }
    }

    private suspend fun Dialogue.woadLeaves() {
        chatPlayer(happy, "Yes please, I need woad leaves.")
        chatNpc(quiz, "How much are you willing to pay?")
        val offer =
            choice4(
                "How about 5 coins?", 5,
                "How about 10 coins?", 10,
                "How about 15 coins?", 15,
                "How about 20 coins?", 20,
            )
        when (offer) {
            5, 10 -> {
                chatPlayer(quiz, "How about ${if (offer == 5) 5 else 10} coins?")
                chatNpc(angry, "No no, that's far too little. Woad leaves are hard to get. I used to have plenty but someone kept stealing them off me.")
            }
            15 -> {
                chatPlayer(quiz, "How about 15 coins?")
                chatNpc(neutral, "Mmmm... okay, that sounds fair.")
                if (!pay(15, leaves = 1)) {
                    return
                }
                chatPlayer(happy, "Thanks.")
                chatNpc(happy, "I'll be around if you have any more gardening needs.")
            }
            20 -> {
                chatPlayer(quiz, "How about 20 coins?")
                chatNpc(happy, "Okay, that's more than fair.")
                if (!pay(20, leaves = 2)) {
                    return
                }
                chatNpc(happy, "Here, have two, you're a generous person.")
                chatPlayer(happy, "Thanks.")
            }
        }
    }

    /** Takes the coins and hands over the leaves, or explains why not. */
    private suspend fun Dialogue.pay(coins: Int, leaves: Int): Boolean {
        if (player.inv.count(COINS) < coins) {
            chatPlayer(sad, "I don't have enough coins to buy the leaves. I'll come back later.")
            return false
        }
        if (access.invDel(access.inv, COINS, coins).failure) {
            return false
        }
        access.invAdd(access.inv, WOAD_LEAF, leaves)
        return true
    }

    private suspend fun Dialogue.mole() {
        chatPlayer(happy, "How about ME helping YOU instead?")
        chatNpc(happy, "That's a nice thing to say. I do need a hand, now you mention it. You see, there's some stupid mole digging up my lovely garden.")
        chatPlayer(quiz, "A mole? Surely you've dealt with moles in the past?")
        chatNpc(worried, "Ah, well this is no ordinary mole! He's a big'un for sure. Ya see... I'm always relied upon to make the most of this 'ere garden - the faster and bigger I can grow plants the better!")
        chatNpc(worried, "In my quest for perfection I looked into 'Malignius-Mortifer's-Super-Ultra-Flora-Growth-Potion'. It worked well on my plants, no doubt about it! But it had the same effect on a nearby mole. Ya can imagine the")
        chatNpc(worried, "havoc he causes to my patches of sunflowers! Why, if any of the other gardeners knew about this mole, I'd be looking for a new job in no time!")
        chatPlayer(quiz, "I see. What do you need me to do?")
        chatNpc(neutral, "If ya are willing maybe yer wouldn't mind killing it for me? Take a spade and use it to shake up them mole hills. Be careful though, he really is big!")
        chatPlayer(quiz, "Is there anything in this for me?")
        chatNpc(neutral, "Well, if yer gets any mole skin or mole claws off 'un, I'd trade 'em for bird nests if ye brings 'em here to me.")
        chatPlayer(neutral, "Right, I'll bear it in mind.")
    }

    private companion object {
        const val WYSON = "npc.wyson"
        const val COINS = "obj.coins"
        const val WOAD_LEAF = "obj.woadleaf"
    }
}
