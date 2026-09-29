package org.rsmod.content.skills.hunter.rumours

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HunterGuildScript
@Inject
constructor(
    private val rumours: RumourTracker,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val pets: PetRewards,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (hunter in RumourHunter.entries) {
            onOpNpc1(hunter.npc) { talkToHunter(it.npc, hunter) }
            onOpNpc3(hunter.npc) { talkToHunter(it.npc, hunter) }
        }
        onOpNpc1(VERITY) { talkToVerity(it.npc) }
        onOpNpc3(VERITY) { rumourSettings(it.npc) }
        for (sack in LootSack.entries) {
            onOpHeld1(sack.obj) { openSack(sack) }
        }
    }

    private suspend fun ProtectedAccess.talkToHunter(npc: Npc, hunter: RumourHunter) =
        startDialogue(npc) {
            val level = player.statBase(TrapManager.STAT)
            when {
                !rumours.isUnlocked(player) -> {
                    chatNpc(
                        neutral,
                        "You'll want to speak with Guild Scribe Verity down in the Burrow before " +
                            "I can share any rumours with you.",
                    )
                }
                level < hunter.level -> {
                    chatNpc(
                        neutral,
                        "Come back when you're a little more experienced. You'll need a Hunter " +
                            "level of ${hunter.level} before I trust you with my rumours.",
                    )
                }
                rumours.activeHunter(player) == hunter -> currentRumour(hunter)
                rumours.activeHunter(player) != null -> switchRumour(hunter, level)
                else -> newRumour(hunter, level)
            }
        }

    private suspend fun Dialogue.currentRumour(hunter: RumourHunter) {
        val rumour = rumours.assigned(player, hunter) ?: return
        if (rumour.part !in player.inv) {
            chatNpc(
                neutral,
                "Any luck with that ${rumour.displayName}? Bring me back a rare piece of one and " +
                    "I'll make it worth your while.",
            )
            return
        }
        access.invDel(player.inv, rumour.part)
        val xp = (player.statBase(TrapManager.STAT) + 5) * hunter.xpModifier.toDouble()
        access.statAdvance(TrapManager.STAT, xp * xpMods.get(player, TrapManager.STAT))
        access.invAdd(player.inv, hunter.sack)
        val completed = rumours.complete(player, hunter, rumour)
        chatNpc(happy, "Excellent work! Here's your reward, as promised.")
        mesbox(
            "You have completed <col=ef1020>$completed</col> " +
                "rumour${if (completed == 1) "" else "s"} for the Hunter Guild."
        )
    }

    private suspend fun Dialogue.switchRumour(hunter: RumourHunter, level: Int) {
        val active = rumours.activeHunter(player) ?: return
        val current = rumours.activeRumour(player)
        chatNpc(
            neutral,
            "You're already chasing ${current?.let { "a ${it.displayName}" } ?: "a rumour"} " +
                "for ${active.displayName}.",
        )
        val offered = rumours.offer(player, hunter, level)
        if (offered == null) {
            chatNpc(neutral, "I've nothing for you anyway.")
            return
        }
        chatNpc(neutral, "I've heard a rumour about a ${offered.displayName}, if you'd rather.")
        val switch =
            choice2(
                "Switch to the ${offered.displayName} rumour.",
                true,
                "Stick with my current rumour.",
                false,
            )
        if (switch) {
            rumours.activate(player, hunter)
            chatNpc(happy, rumourText(offered))
        }
    }

    private suspend fun Dialogue.newRumour(hunter: RumourHunter, level: Int) {
        val rumour = rumours.offer(player, hunter, level)
        if (rumour == null) {
            chatNpc(neutral, "I haven't heard any rumours that would suit you, I'm afraid.")
            return
        }
        rumours.activate(player, hunter)
        chatNpc(happy, rumourText(rumour))
    }

    private fun rumourText(rumour: Rumour): String =
        "I've heard there's a ${rumour.displayName} with something special about it. Catch me " +
            "a rare piece of one and bring it back here."

    private suspend fun ProtectedAccess.talkToVerity(npc: Npc) =
        startDialogue(npc) {
            if (!rumours.isUnlocked(player)) {
                if (player.statBase(TrapManager.STAT) < UNLOCK_LEVEL) {
                    chatNpc(
                        neutral,
                        "The guild hunters only share their rumours with hunters of level " +
                            "$UNLOCK_LEVEL or above.",
                    )
                    return@startDialogue
                }
                chatNpc(
                    happy,
                    "Welcome to the Burrow! I keep track of the rumours our guild hunters hand " +
                        "out. I've added your name to the ledger.",
                )
                rumours.unlock(player)
                chatNpc(
                    neutral,
                    "Speak to any of the guild hunters and they'll tell you about a creature " +
                        "worth chasing.",
                )
                return@startDialogue
            }
            chatNpc(
                neutral,
                "You've completed ${rumours.completed(player)} rumours for the guild so far.",
            )
            val current = rumours.activeRumour(player)
            val hunter = rumours.activeHunter(player)
            if (current != null && hunter != null) {
                chatNpc(
                    neutral,
                    "You're currently hunting a ${current.displayName} for ${hunter.displayName}.",
                )
            }
        }

    private suspend fun ProtectedAccess.rumourSettings(npc: Npc) =
        startDialogue(npc) {
            val enabled = rumours.backToBack(player)
            val state = if (enabled) "allowed" else "not allowed"
            chatNpc(
                neutral,
                "The guild hunters are currently $state to give you the same rumour twice in a row.",
            )
            val toggle =
                choice2(
                    if (enabled) "Stop back-to-back rumours." else "Allow back-to-back rumours.",
                    true,
                    "Leave it as it is.",
                    false,
                )
            if (toggle) {
                rumours.setBackToBack(player, !enabled)
                chatNpc(happy, "I've updated the ledger.")
            }
        }

    private fun ProtectedAccess.openSack(sack: LootSack) {
        val loot = rollSack(sack)
        val newSlots = loot.map { it.first }.distinct().count { it !in player.inv }
        if (inv.freeSpace() < newSlots) {
            mes("You need more inventory space to open this sack.")
            return
        }
        if (invDel(inv, sack.obj).failure) {
            return
        }
        if (loot.any { it.first == rumours.nextOutfitPiece(player) }) {
            rumours.advanceOutfit(player)
        }
        for ((obj, count) in loot) {
            if (obj == QUETZIN && pets.give(player, obj)) {
                continue
            }
            invAdd(inv, obj, count)
        }
        mes("You open the loot sack.")
    }

    private fun ProtectedAccess.rollSack(sack: LootSack): List<Pair<String, Int>> {
        val loot = ArrayList<Pair<String, Int>>()
        val slots = sack.main.size + (if (sack.herbs) 1 else 0) + 1
        repeat(sack.rolls) {
            val slot = random.of(slots)
            when {
                slot < sack.main.size -> {
                    val entry = sack.main[slot]
                    loot += entry.obj to random.of(entry.min, entry.max)
                }
                sack.herbs && slot == sack.main.size -> {
                    loot += cascade(SACK_HERBS, player.herbloreLvl) to TIER_QUANTITY
                }
                else -> loot += cascade(SACK_LOGS, player.woodcuttingLvl) to TIER_QUANTITY
            }
        }
        if (sack.tertiaries) {
            if (random.of(TERTIARY_CHANCE) == 0) {
                loot += ENHANCED_BLUEPRINT to 1
            }
            if (sack.perfectedBlueprint && random.of(TERTIARY_CHANCE) == 0) {
                loot += PERFECTED_BLUEPRINT to 1
            }
            if (random.of(TERTIARY_CHANCE) == 0 && HUNTSMANS_KIT !in player.inv) {
                loot += HUNTSMANS_KIT to 1
            }
            if (random.of(TERTIARY_CHANCE) == 0) {
                loot += rumours.nextOutfitPiece(player) to 1
            }
            if (sack.perfectedBlueprint && random.of(QUETZIN_CHANCE) == 0) {
                loot += QUETZIN to 1
            }
        }
        return loot.groupBy({ it.first }, { it.second }).map { (obj, counts) -> obj to counts.sum() }
    }

    private fun cascade(tiers: List<CascadeTier>, level: Int): String {
        for (tier in tiers) {
            if (level < tier.req) {
                continue
            }
            val chance = SkillingSuccessRate.successRate(tier.low, tier.high, level, MAX_LEVEL)
            if (random.randomDouble() < chance) {
                return tier.obj
            }
        }
        return tiers.last().obj
    }

    private companion object {
        const val VERITY = "npc.hg_verity"
        const val UNLOCK_LEVEL = 46
        const val MAX_LEVEL = 99
        const val TIER_QUANTITY = 4
        const val TERTIARY_CHANCE = 50
        const val QUETZIN_CHANCE = 1000
        const val QUETZIN = "obj.quetzalpet"
        const val HUNTSMANS_KIT = "obj.huntsmans_kit"
        const val ENHANCED_BLUEPRINT = "obj.hg_whistle_blueprint_2"
        const val PERFECTED_BLUEPRINT = "obj.hg_whistle_blueprint_3"
    }
}
