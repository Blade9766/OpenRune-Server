package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_END
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_GRID
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_MAZE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_OUTSIDE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_TEMPLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_ENTERED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Koftik, King Lathas's tracker, who went down the pass first and is waiting at each of the
 * places the player is going to need him.
 *
 * He is the same npc six times over, spawned at six points along the route; the quest hides five
 * of them at a time behind their own varbits. The deeper in he goes the less of him is left, until
 * the temple, where he is not making sense at all - and then it comes down, and he is himself
 * again with no memory of any of it.
 */
@Singleton
class Koftik @Inject constructor(private val quest: UndergroundPassQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KOFTIK_OUTSIDE) { startDialogue(it.npc) { outside() } }
        onOpNpc1(KOFTIK_BRIDGE) { startDialogue(it.npc) { atTheFire() } }
        onOpNpc1(KOFTIK_GRID) { startDialogue(it.npc) { atTheGrid() } }
        onOpNpc1(KOFTIK_MAZE) { startDialogue(it.npc) { atTheMaze() } }
        onOpNpc1(KOFTIK_TEMPLE) { startDialogue(it.npc) { atTheTemple() } }
        onOpNpc1(KOFTIK_END) { startDialogue(it.npc) { attheEnd() } }
    }

    private suspend fun Dialogue.outside() {
        chatNpc(neutral, "So you're the one he's sending. I'm Koftik. I've been down there twice and I'll not go a third time alone.")
        chatPlayer(quiz, "What's down there?")
        chatNpc(worried, "A road. It runs west under the mountains and comes out the other side, and there's something living on it that doesn't want it used.")
        chatNpc(neutral, "The path of the righteous man is beset on all sides by the inequities of the selfish. Remember that when you're down there. I find I have to.")
        chatPlayer(quiz, "Are you coming with me?")
        chatNpc(neutral, "I'll go ahead of you and keep a fire lit. That's as much as I'm good for. The cave's just there.")
        UndergroundPassQuest.setVarBit(player, "varbit.upass_koftik_chat", 1)
    }

    private suspend fun Dialogue.atTheFire() {
        if (quest.stage(player) >= STAGE_ENTERED && !player.clothTaken) {
            chatNpc(neutral, "There's a bridge past the swamp and someone's cut the guide rope on the far side. It'll not come down on its own.")
            chatNpc(neutral, "There's abandoned kit by the fire - whoever came down before us left it. There's a cloth in it, stiff with lamp oil. Wrap that round an arrow and light it here.")
            return
        }
        if (quest.stage(player) < STAGE_ENTERED) {
            chatNpc(worried, "You came, then. Sit by the fire a minute, it's the only warm thing down here.")
            chatNpc(neutral, "The bridge ahead has been cut. The rope holding it is across the chasm, so it wants burning, not cutting.")
            chatNpc(neutral, "Search that kit by the wall. There's an oily cloth in it. Tie it to an arrow, light it in my fire and put it through the rope.")
            quest.advanceTo(access, STAGE_ENTERED)
            return
        }
        chatNpc(neutral, "Burn the rope and the span comes down. I'll follow you over.")
    }

    private suspend fun Dialogue.atTheGrid() {
        chatNpc(worried, "Don't walk out on that floor without looking. It's grilles, and most of them are rusted through.")
        chatPlayer(quiz, "How do I get across?")
        chatNpc(neutral, "One square at a time, and never backwards. There's a way over it that holds. I found mine. You'll have to find yours.")
        chatNpc(neutral, "The lever on the far side opens the gate. I'd go with you but I've had one fall already today.")
    }

    private suspend fun Dialogue.atTheMaze() {
        chatNpc(shocked, "It's warm down here. Don't you think it's warm? I've taken my boots off.")
        chatPlayer(worried, "Koftik, are you all right?")
        chatNpc(shocked, "There's a pipe in the wall there. I've been through it. There's cages on the other side and a horse with a horn on it, and it looks at you.")
        chatNpc(angry, "Don't touch my fire.")
        chatPlayer(worried, "...I'll go on ahead.")
    }

    private suspend fun Dialogue.atTheTemple() {
        chatNpc(shocked, "He knows your name. He's been saying it. All night, in the walls, saying your name.")
        chatPlayer(worried, "Koftik, sit down.")
        chatNpc(shocked, "Zamorak's boy. Zamorak's boy in the house on the hill, and the doors won't open for a good heart, and mine's no good any more so they opened for me.")
        chatNpc(angry, "Don't go in. Don't. Go in. Don't.")
    }

    private suspend fun Dialogue.attheEnd() {
        if (quest.isComplete(player)) {
            chatNpc(happy, "Daylight that way, friend. I'll not be going back down there.")
            return
        }
        chatNpc(confused, "Who... where is this? My head's like a bell that's been rung.")
        chatPlayer(neutral, "It's Koftik, isn't it? You've been down here a long while.")
        chatNpc(confused, "Koftik. Yes. That's right, that's my name. I can't think what I was doing.")
        chatNpc(neutral, "There's a way up just there. Go and tell the King, and I'll follow you when my legs have stopped shaking.")
    }
}
