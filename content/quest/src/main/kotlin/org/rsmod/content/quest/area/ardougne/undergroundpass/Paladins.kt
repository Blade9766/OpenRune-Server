package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BADGES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PALADIN_CARL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PALADIN_HARRY
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PALADIN_JERRO
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOORS
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Jerro, Sir Carl and Sir Harry, the last three of King Lathas's own paladins to come down
 * here, camped in the cavern north of the unicorn.
 *
 * Sir Jerro feeds the player; all three warn them off. The coats of arms the well by the Doors of
 * Iban wants are on their belts, and there is only one way to get them. Once the player is through
 * the doors the survivors take them for one of Iban's own and attack on sight.
 */
@Singleton
class Paladins
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val objRepo: ObjRepository,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (paladin in PALADINS) {
            for (npc in listOf(paladin.spawn, paladin.head)) {
                onOpNpc1(npc) { talkTo(it.npc, paladin) }
            }
        }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc, paladin: Paladin) {
        if (quest.stage(player) >= STAGE_DOORS) {
            startDialogue(npc) { chatPlayer(happy, "Hello.") }
            npc.say("You again... die Zamorakian scum!")
            npc.facePlayer(player)
            npc.opPlayer2(player, aiInteractions)
            return
        }
        startDialogue(npc) {
            when (paladin.spawn) {
                PALADIN_JERRO -> jerro(paladin)
                PALADIN_CARL -> {
                    chatPlayer(neutral, "Hello there.")
                    chatNpcSpecific(paladin.displayName, paladin.head, neutral, "Take care down here, Evil things are abroad...")
                }
                else -> {
                    chatPlayer(neutral, "Good day.")
                    chatNpcSpecific(paladin.displayName, paladin.head, neutral, "Watch your back, the undead are about here...")
                }
            }
        }
    }

    private suspend fun Dialogue.jerro(paladin: Paladin) {
        val name = paladin.displayName
        val head = paladin.head
        chatPlayer(happy, "Hello Paladin.")
        if (player.paladinFood == 1) {
            chatNpcSpecific(name, head, neutral, "You should leave this place now traveller, I heard the crashing of rocks further down the cavern. Iban must be restless.")
            chatNpcSpecific(name, head, neutral, "I doubt not that Zamorak still influences these caverns. A little further on lies the great door of Iban. We've tried everything, but it will not let us enter. Leave now before Iban awakes and it's too late.")
            return
        }
        chatNpcSpecific(name, head, confused, "Traveller, what are you doing in this most unholy place?")
        chatPlayer(neutral, "I'm looking for safe route through the caverns, under order of King Lathas.")
        chatNpcSpecific(name, head, happy, "You've done well to get this far traveller, here eat...")
        for (supply in PALADIN_SUPPLIES) {
            access.invAddOrDrop(objRepo, supply)
        }
        access.mes("The Paladin gives you some food.")
        UndergroundPassQuest.setVarBit(player, "varbit.upass_paladin_food", 1)
        chatPlayer(happy, "Great, thanks a lot.")
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

        val PALADIN_SUPPLIES =
            listOf(
                "obj.meat_pie",
                "obj.meat_pie",
                "obj.bread",
                "obj.bread",
                "obj.stew",
                "obj.2dose1attack",
                "obj.2doseprayerrestore",
            )
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
