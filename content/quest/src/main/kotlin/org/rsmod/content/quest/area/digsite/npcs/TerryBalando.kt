package org.rsmod.content.quest.area.digsite.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.AMMONIUM_NITRATE
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEMICAL_POWDER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.INVITATION
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.NITROGLYCERIN
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_INVITED
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_TALISMAN
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STONE_TABLET
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TALISMAN
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TERRY
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.UNIDENTIFIED_LIQUID
import org.rsmod.content.quest.area.digsite.carriesOrBanks
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Terry Balando, the archaeological expert in the Exam Centre. Every find on the site is reported
 * to him: he names the talisman, hands out the invitation to the private shafts, identifies the two
 * chemicals and finally takes the stone tablet that ends the quest.
 */
class TerryBalando @Inject constructor(private val quest: TheDigSiteQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(TERRY) { startDialogue(it.npc) { terry() } }
        onOpNpcU(TERRY) { useOnTerry(it.npc, it.objType.internalName) }
    }

    private suspend fun ProtectedAccess.useOnTerry(npc: Npc, obj: String) {
        when (obj) {
            TALISMAN -> startDialogue(npc) { identifyTalisman() }
            STONE_TABLET -> startDialogue(npc) { handInTablet() }
            CHEMICAL_POWDER -> startDialogue(npc) { identifyPowder() }
            UNIDENTIFIED_LIQUID -> startDialogue(npc) { identifyLiquid() }
            INVITATION ->
                startDialogue(npc) {
                    chatNpc(bored, "There's no point in giving me this back!")
                }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.terry() {
        val stage = quest.stage(player)
        when {
            stage == STAGE_TALISMAN && access.player.inv.contains(TALISMAN) -> identifyTalisman()
            stage == STAGE_INVITED && access.player.inv.contains(STONE_TABLET) -> handInTablet()
            else -> standingChat(stage)
        }
    }

    private suspend fun Dialogue.standingChat(stage: Int) {
        if (stage >= STAGE_INVITED) {
            chatNpc(happy, "Oh, hello. I hope you're getting on well in the dig shafts.")
        } else {
            chatPlayer(quiz, "Hello. Who are you?")
            chatNpc(
                neutral,
                "Good day to you. My name is Terry Balando; I am an expert on digsite finds.",
            )
            chatNpc(
                neutral,
                "I am employed by the museum in Varrock to oversee all finds at this digsite. " +
                    "Anything you find must be reported to me.",
            )
            chatPlayer(neutral, "Oh, ok. If I find anything of interest I will bring it here.")
        }
        chatNpc(quiz, "Can I help you at all?")
        val choice =
            choice3(
                "Can you tell me anything about the digsite?",
                1,
                "I lost the letter you gave me.",
                2,
                "No thanks.",
                3,
            )
        when (choice) {
            1 -> aboutTheDigsite()
            2 -> replaceInvitation(stage)
            else -> chatPlayer(neutral, "No thanks.")
        }
    }

    private suspend fun Dialogue.aboutTheDigsite() {
        chatPlayer(quiz, "Can you tell me anything about the digsite?")
        chatNpc(
            neutral,
            "We believe the city of Saranthium once stood here, long before Varrock was built. " +
                "Every scrap of pottery we turn up tells us a little more about the people who " +
                "lived in it.",
        )
        chatNpc(
            neutral,
            "The deeper digs are where the real finds are. That is why we do not let just anyone " +
                "onto them.",
        )
    }

    private suspend fun Dialogue.replaceInvitation(stage: Int) {
        chatPlayer(sad, "I lost the letter you gave me.")
        if (stage < STAGE_INVITED) {
            chatNpc(bored, "I have not written you one yet!")
            return
        }
        if (access.player.vars[TheDigSiteQuest.INVITATION_VARBIT] != 0) {
            chatNpc(happy, "A workman has already seen it; you do not need it any more.")
            return
        }
        if (access.carriesOrBanks(INVITATION)) {
            chatNpc(angry, "No you haven't! You have one in your pack!")
            return
        }
        chatNpc(
            angry,
            "I can't believe it! I go to all the effort of writing you a letter of recommendation " +
                "and you lose it! Here, I'll write another... Don't lose it again!",
        )
        access.invAdd(access.inv, INVITATION)
        objbox(INVITATION, "Terry hands you another invitation letter.")
    }

    private suspend fun Dialogue.identifyTalisman() {
        if (!access.player.inv.contains(TALISMAN)) {
            return
        }
        chatPlayer(happy, "Take a look at this talisman.")
        chatNpc(confused, "Unusual... This object doesn't appear right...")
        chatNpc(confused, "Hmmm...")
        chatNpc(shocked, "I wonder... Let me check my guide... Could it be? Surely not!")
        chatNpc(
            neutral,
            "From the markings on it, it seems to be a ceremonial ornament to a god named...",
        )
        chatNpc(
            shocked,
            "...Zaros? I haven't heard much about him before. This is a great discovery; we know " +
                "very little of the pagan gods that people worshipped.",
        )
        chatNpc(
            neutral,
            "There is some strange writing embossed upon it - it says, 'Zaros will return and " +
                "wreak his vengeance upon Zamorak the pretender.'",
        )
        chatNpc(quiz, "I wonder what it means by that? Some silly superstition, probably.")
        chatNpc(
            happy,
            "Still, I wonder what this is doing around here. I'll tell you what; as you have " +
                "found this, I will allow you to use the private dig shafts.",
        )
        chatNpc(
            happy,
            "You obviously have a keen eye. Take this letter and give it to one of the workmen, " +
                "and they will allow you to use them.",
        )
        access.invDel(access.inv, TALISMAN)
        access.invAdd(access.inv, INVITATION)
        quest.advanceTo(access, STAGE_INVITED)
        objbox(INVITATION, "Terry hands you an invitation letter.")
    }

    private suspend fun Dialogue.identifyPowder() {
        if (!access.player.inv.contains(CHEMICAL_POWDER)) {
            return
        }
        chatPlayer(quiz, "Do you know what this powder is?")
        chatNpc(
            neutral,
            "Really, you do find the most unusual items. I know what this is, it's a strong " +
                "chemical called ammonium nitrate. Why you want this I'll never know...",
        )
        access.invReplace(access.inv, CHEMICAL_POWDER, 1, AMMONIUM_NITRATE)
        objbox(AMMONIUM_NITRATE, "Terry labels the powder as ammonium nitrate.")
    }

    private suspend fun Dialogue.identifyLiquid() {
        if (!access.player.inv.contains(UNIDENTIFIED_LIQUID)) {
            return
        }
        chatPlayer(quiz, "Do you know what this is?")
        chatNpc(shocked, "Where did you get this?")
        chatPlayer(neutral, "From one of the barrels at the digsite.")
        chatNpc(
            worried,
            "This is a VERY dangerous liquid called nitroglycerin. Be careful how you handle it. " +
                "Don't drop it or it will explode!",
        )
        access.invReplace(access.inv, UNIDENTIFIED_LIQUID, 1, NITROGLYCERIN)
        objbox(NITROGLYCERIN, "Terry labels the vial as nitroglycerin.")
    }

    private suspend fun Dialogue.handInTablet() {
        if (!access.player.inv.contains(STONE_TABLET)) {
            return
        }
        chatPlayer(happy, "I found this in a hidden cavern beneath the digsite.")
        chatNpc(shocked, "Incredible!")
        chatPlayer(neutral, "There is an altar down there. The place is crawling with skeletons!")
        chatNpc(shocked, "Yuck!")
        chatNpc(
            happy,
            "This is an amazing discovery! All this while we were convinced that no other race " +
                "had lived here.",
        )
        chatNpc(
            neutral,
            "It seems the followers of Saradomin have tried to cover up the evidence of the Zaros " +
                "altar. This whole city must have been built over it!",
        )
        chatNpc(
            happy,
            "Thanks for your help; your sharp eyes have spotted what many have missed. Here, take " +
                "this gold as your reward.",
        )
        access.invDel(access.inv, STONE_TABLET)
        quest.advanceTo(access, STAGE_COMPLETE)
    }
}
