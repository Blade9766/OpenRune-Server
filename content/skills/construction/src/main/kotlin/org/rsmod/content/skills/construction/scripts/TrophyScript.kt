package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Trophies
import org.rsmod.content.skills.construction.data.Trophies.Trophy
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.mountTrophy
import org.rsmod.content.skills.construction.house.mountedTrophies
import org.rsmod.content.skills.construction.house.showTrophy
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The skill hall's trophies: the Canifis taxidermist stuffs them, a stuffed head or fish is mounted
 * on a display in building mode, and a display's Trophies op picks which mounted one it shows.
 * Old School picks that from its own trophy menu; here it is a plain list.
 */
class TrophyScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(TAXIDERMIST) { startDialogue(it.npc) { greet() } }
        onOpNpcU(TAXIDERMIST) { offer(it.npc, RSCM.getReverseMapping(RSCMType.OBJ, it.objType.id)) }
        for (display in Trophies.DISPLAYS.keys) {
            onOpLocU(display) { mount(display, RSCM.getReverseMapping(RSCMType.OBJ, it.objType.id)) }
            onOpLoc2(display) { chooseShown(display) }
        }
    }

    // ---------------------------------------------------------------------- taxidermist

    private suspend fun Dialogue.greet() {
        chatNpc(neutral, "Oh, hello. Have you got something you want preserving?")
        when (choice3("Yes please", 0, "Not right now", 1, "What?", 2)) {
            0 -> {
                chatPlayer(neutral, "Yes please.")
                chatNpc(neutral, "Give it to me to look at then.")
            }
            1 -> {
                chatPlayer(neutral, "Not right now.")
                chatNpc(neutral, "Well, you go kill things so I can stuff them, eh?")
            }
            else -> {
                chatPlayer(confused, "What?")
                chatNpc(
                    neutral,
                    "If you bring me a monster head or a very big fish, I can preserve it for you " +
                        "so you can mount it in your house.",
                )
                chatNpc(
                    neutral,
                    "I hear there are all sorts of exotic creatures in the Slayer Tower -- I'd like a " +
                        "chance to stuff one of them!",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.offer(npc: Npc, obj: String) {
        val trophy = Trophies.ALL.firstOrNull { obj in it.raw }
        startDialogue(npc) {
            if (trophy == null) {
                chatNpc(neutral, "Don't be silly, I can't preserve that!")
                return@startDialogue
            }
            for (remark in REMARKS[obj].orEmpty()) {
                chatNpc(happy, remark)
            }
            chatNpc(neutral, "I can preserve that for you for ${"%,d".format(trophy.cost)} coins.")
            if (access.invCoinTotal() < trophy.cost) {
                chatPlayer(neutral, "Maybe another time.")
                return@startDialogue
            }
            if (!choice2("Yes please", true, "No thanks", false)) {
                chatPlayer(neutral, "No thanks.")
                chatNpc(neutral, "All right, come back if you change your mind, eh?")
                return@startDialogue
            }
            chatPlayer(happy, "Yes please.")
            val stuffed = trophy.stuffed[trophy.raw.indexOf(obj)]
            if (obj in access.inv && access.invTakeFee(trophy.cost)) {
                access.invDel(access.inv, obj)
                access.invAdd(access.inv, stuffed)
                chatNpc(happy, "There you go!")
            }
        }
    }

    // ------------------------------------------------------------------------- mounting

    private suspend fun ProtectedAccess.mount(display: String, obj: String) {
        val trophy = Trophies.ALL.firstOrNull { obj in it.stuffed }
        if (trophy == null) {
            mes("You can only mount stuffed trophies on this display.")
            return
        }
        val (kind, tier) = Trophies.DISPLAYS.getValue(display)
        if (trophy.kind != kind) {
            mes("That doesn't belong on this display.")
            return
        }
        if (registry.active(player)?.buildMode != true) {
            mes("You can only mount trophies while your house is in building mode.")
            return
        }
        if (trophy.tier > tier) {
            mes("You need a better display to mount that.")
            return
        }
        if (player.constructionLvl < trophy.level) {
            mes("You need a Construction level of ${trophy.level} to mount that.")
            return
        }
        if (player.hasMounted(trophy)) {
            mes("You already have that trophy mounted.")
            return
        }
        val combat = trophy.skill == null && acceptsCombatXp(trophy)
        anim(Construction.BUILD_ANIM)
        delay(Construction.BUILD_CYCLE)
        resetAnim()
        if (player.hasMounted(trophy) || invDel(inv, obj).failure) {
            return
        }
        player.mountTrophy(kind, trophy.bit)
        player.showTrophy(kind, Trophies.ALL.filter { it.kind == kind }.indexOf(trophy) + 1)
        statAdvance(Construction.STAT, trophy.constructionXp * xpMods.get(player, Construction.STAT))
        val skills = trophy.skill?.let(::listOf) ?: if (combat) Trophies.COMBAT_SKILLS else emptyList()
        for (skill in skills) {
            statAdvance(skill, trophy.skillXp * xpMods.get(player, skill))
        }
        mes("You mount the ${trophy.label.lowercase()} on the display.")
        houses.rebuild(this)
    }

    private fun Player.hasMounted(trophy: Trophy): Boolean =
        mountedTrophies(trophy.kind) and (1 shl trophy.bit) != 0

    private suspend fun ProtectedAccess.acceptsCombatXp(trophy: Trophy): Boolean =
        choice2(
            "Yes, take ${trophy.skillXp.toInt()} experience in each combat skill.",
            true,
            "No, just mount it.",
            false,
            title = "Gain combat experience from mounting this?",
        )

    private suspend fun ProtectedAccess.chooseShown(display: String) {
        if (registry.active(player) == null) {
            return
        }
        val (kind, tier) = Trophies.DISPLAYS.getValue(display)
        val trophies = Trophies.ALL.filter { it.kind == kind }
        val mask = player.mountedTrophies(kind)
        val choices = trophies.filter { it.tier <= tier && mask and (1 shl it.bit) != 0 }
        if (choices.isEmpty()) {
            mes("You haven't mounted any trophies that fit this display yet.")
            return
        }
        val labels = listOf("Nothing") + choices.map { it.label }
        val choice = menu("Trophies", hotkeys = false, choices = labels)
        ifClose()
        if (choice !in labels.indices) {
            return
        }
        val shown = if (choice == 0) 0 else trophies.indexOf(choices[choice - 1]) + 1
        player.showTrophy(kind, shown)
        houses.rebuild(this)
    }

    private companion object {
        const val TAXIDERMIST = "npc.poh_taxidermist"

        /** What she says about each specimen before naming her price, from the wiki transcript. */
        val REMARKS: Map<String, List<String>> =
            mapOf(
                "obj.poh_trophydrop_bass" to listOf("That's a mighty fine sea bass you've caught there."),
                "obj.poh_trophydrop_swordfish" to listOf("Don't point that thing at me!"),
                "obj.poh_trophydrop_harpoonfish" to
                    listOf("Goodness me! That thing's more of a weapon than a fish!"),
                "obj.poh_trophydrop_shark" to
                    listOf(
                        "That's quite a fearsome shark! You've done everyone a service by " +
                            "removing it from the sea!"
                    ),
                "obj.poh_trophydrop_crawlinghand" to listOf("That's a very fine crawling hand."),
                "obj.poh_trophydrop_cockatrice" to
                    listOf("A cockatrice! Beautiful, isn't it? Look at the plumage!"),
                "obj.poh_trophydrop_basilisk" to
                    listOf("My, he's a scary-looking fellow, isn't he? He'll look good on your wall!"),
                "obj.poh_trophydrop_kurask" to listOf("A kurask? Splendid! Look at those horns!"),
                "obj.poh_trophydrop_abyssaldemon" to
                    listOf(
                        "Goodness, an abyssal demon!",
                        "See how it's still glowing? I'll have to use some magic to preserve that.",
                    ),
                "obj.poh_trophydrop_kbd" to
                    listOf(
                        "Three?! This must be a King Black Dragon!",
                        "I'll have to get out my heavy duty tools -- this skin's as tough as iron!",
                    ),
                "obj.poh_trophydrop_kalphitequeen" to
                    listOf(
                        "That must be the biggest kalphite I've ever seen!",
                        "Preserving insects is always tricky. I'll have to be careful...",
                    ),
                "obj.poh_pitydrop_kalphitequeen" to
                    listOf(
                        "That's not the most impressive head I've seen but I think I can just " +
                            "about manage this one.",
                        "Preserving insects is always tricky. I'll have to be careful...",
                    ),
                "obj.vorkath_head" to
                    listOf(
                        "This blue dragon smells like it's been dead for a remarkably long time. " +
                            "Even by my standards, it smells awful."
                    ),
                "obj.poh_alchemical_hydra_head" to
                    listOf(
                        "This hydra looks like it might fall to pieces soon. I'll have to be " +
                            "careful to preserve it properly."
                    ),
            )
    }
}
