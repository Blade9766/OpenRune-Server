package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BADGES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PALADIN_CARL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PALADIN_HARRY
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PALADIN_JERRO
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Jerro, Sir Carl and Sir Harry, the last three of King Lathas's own paladins to come down
 * here, camped in the cavern north of the unicorn.
 *
 * They are friendly, and they will feed anyone who asks, but their orders are to let nobody
 * through and they mean to keep them. The coats of arms the Doors of Iban want are on their belts,
 * and there is only one way to get all three.
 */
@Singleton
class Paladins
@Inject
constructor(private val quest: UndergroundPassQuest, private val random: GameRandom) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (paladin in PALADINS) {
            for (npc in listOf(paladin.spawn, paladin.head)) {
                onOpNpc1(npc) { startDialogue(it.npc) { paladin(paladin) } }
            }
        }
    }

    private suspend fun Dialogue.paladin(paladin: Paladin) {
        chatNpcSpecific(paladin.displayName, paladin.head, neutral, "Hold there. This is as far as you go.")
        chatPlayer(quiz, "King Lathas sent me. I'm to get through the pass.")
        chatNpcSpecific(
            paladin.displayName,
            paladin.head,
            sad,
            "So were we, friend. Forty of us came down that shaft and there are three left, and " +
                "we have not been past those doors yet.",
        )
        when (
            choice3(
                "What is behind the doors?", 1,
                "Do you have any food to spare?", 2,
                "Stand aside.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What is behind the doors?")
                chatNpcSpecific(
                    paladin.displayName,
                    paladin.head,
                    worried,
                    "Iban. Son of Zamorak, if the tablets tell it true. The well beside the doors " +
                        "wants four tokens before it will open them, and it has had three of ours " +
                        "and will not take a fourth.",
                )
                chatNpcSpecific(
                    paladin.displayName,
                    paladin.head,
                    neutral,
                    "Our badges, and something of this place. A unicorn's horn, Sir Carl reckons. " +
                        "Not that it matters. Nobody is going through.",
                )
            }
            2 -> {
                chatPlayer(quiz, "Do you have any food to spare?")
                offerFood(paladin)
            }
            3 -> {
                chatPlayer(angry, "Stand aside.")
                chatNpcSpecific(
                    paladin.displayName,
                    paladin.head,
                    angry,
                    "I will not. Go back up the shaft while you still can.",
                )
            }
        }
    }

    private suspend fun Dialogue.offerFood(paladin: Paladin) {
        if (player.paladinFood == 1) {
            chatNpcSpecific(
                paladin.displayName,
                paladin.head,
                neutral,
                "You have had your share of it. There are three of us to feed as well.",
            )
            return
        }
        chatNpcSpecific(
            paladin.displayName,
            paladin.head,
            happy,
            "We are not so far gone that we would see anyone starve. Here.",
        )
        val gift = PALADIN_FOOD[random.of(0, PALADIN_FOOD.size - 1)]
        if (access.invAdd(access.inv, gift).failure) {
            chatNpcSpecific(paladin.displayName, paladin.head, neutral, "You have no room to carry it.")
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_paladin_food", 1)
        access.mes("Sir ${paladin.shortName} hands you some of the camp's food.")
    }

    data class Paladin(
        val spawn: String,
        val head: String,
        val displayName: String,
        val shortName: String,
        val badge: String,
        val badgeVarbit: String,
    )

    companion object {
        val PALADINS =
            listOf(
                Paladin(
                    PALADIN_JERRO,
                    "npc.upass_paladin1_vis",
                    "Sir Jerro",
                    "Jerro",
                    BADGES[0],
                    "varbit.upass_paladinbadge_1",
                ),
                Paladin(
                    PALADIN_CARL,
                    "npc.upass_paladin2_vis",
                    "Sir Carl",
                    "Carl",
                    BADGES[1],
                    "varbit.upass_paladinbadge_2",
                ),
                Paladin(
                    PALADIN_HARRY,
                    "npc.upass_paladin3_vis",
                    "Sir Harry",
                    "Harry",
                    BADGES[2],
                    "varbit.upass_paladinbadge_3",
                ),
            )

        val PALADIN_FOOD = arrayOf("obj.bread", "obj.meat_pie", "obj.stew")
    }
}

/**
 * Each paladin's badge goes straight to whoever killed him, and his own varbit takes him off the
 * map for that player: `npc.upass_paladin1` to `_3` are multinpcs on those three varbits with no
 * transform past the first.
 */
class PaladinKillHook
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val objRepo: ObjRepository,
) : NpcDeathKillHook {

    private val badgesByNpcId: Map<Int, Paladins.Paladin> by lazy {
        buildMap {
            for (paladin in Paladins.PALADINS) {
                put(paladin.spawn.asRSCM(RSCMType.NPC), paladin)
                put(paladin.head.asRSCM(RSCMType.NPC), paladin)
            }
        }
    }

    override fun onKill(context: NpcDeathKillContext) {
        val paladin = badgesByNpcId[context.npc.id] ?: return
        val hero = context.hero
        if (hero.vars[paladin.badgeVarbit] == 1) {
            return
        }
        launcher.launch(hero) { claimBadge(paladin) }
    }

    private suspend fun ProtectedAccess.claimBadge(paladin: Paladins.Paladin) {
        UndergroundPassQuest.setVarBit(player, paladin.badgeVarbit, 1)
        invAddOrDrop(objRepo, paladin.badge)
        mesbox("You take ${paladin.displayName}'s coat of arms from his belt.")
    }
}
