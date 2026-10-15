package org.rsmod.content.events.halloween

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.enums.EnumTypeMap
import jakarta.inject.Inject
import org.rsmod.api.enums.HalloweenEnums
import org.rsmod.api.enums.HalloweenTrickEnums
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onOpWorn5
import org.rsmod.api.table.HalloweenTreatRewardsRow
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HalloweenItemsScript @Inject constructor(private val season: HalloweenSeason) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (style in CAULDRON_OBJS) {
            onOpHeld3(style) { checkTreats() }
            onOpHeld4(style) { changeStyle(inv, it.slot, unlockedCauldronStyles()) }
            onOpWorn2(style) { checkTreats() }
            onOpWorn3(style) { changeStyle(worn, it.slot, unlockedCauldronStyles()) }
            onOpWorn4(style) { mes("Nobody is trying to Trick-or-Treat you at the moment.") }
            onOpWorn5(style) { cauldronEmote() }
        }
        for (wig in WIG_STYLES) {
            onOpHeld3(wig) { changeStyle(inv, it.slot, WIG_STYLES) }
            onOpWorn2(wig) { changeStyle(worn, it.slot, WIG_STYLES) }
        }
        for (hat in SPIDER_HAT_STYLES) {
            onOpHeld3(hat) { changeStyle(inv, it.slot, unlockedSpiderHats()) }
            onOpWorn2(hat) { changeStyle(worn, it.slot, unlockedSpiderHats()) }
        }
        for (cape in COBWEB_CAPES) {
            onOpHeld3(cape) { changeStyle(inv, it.slot, COBWEB_CAPES) }
            onOpWorn2(cape) { changeStyle(worn, it.slot, COBWEB_CAPES) }
        }
        onOpHeld3(CHARM) { checkCharm(it.obj.vars) }
        onOpWorn2(CHARM) { checkCharm(it.obj.vars) }

        val junk = JUNK
        for (i in junk.indices) {
            for (j in i + 1 until junk.size) {
                onOpHeldU(junk[i], junk[j]) { makeCharm() }
            }
        }
        onOpLoc1(SPOOKY_CAULDRON) { rummage() }
        onOpLoc2(SPOOKY_CAULDRON) { stats() }
    }

    private fun ProtectedAccess.checkTreats() {
        mes(
            "Your Treat cauldron holds ${player.treats} treats. You have deposited " +
                "${player.treatsDeposited} treats into the Great cauldron.",
        )
    }

    private fun ProtectedAccess.cauldronEmote() {
        anim("seq.emote_trick_or_treat_${player.cauldronStyleIndex()}")
    }

    private fun ProtectedAccess.checkCharm(uses: Int) {
        val left = CHARM_USES - uses
        val times = if (left == 1) "1 more time" else "$left more times"
        mes("Your Terrifying charm will scare up treats $times.")
    }

    private fun ProtectedAccess.changeStyle(inv: Inventory, slot: Int, styles: List<String>) {
        val current = inv[slot] ?: return
        val index = styles.indexOfFirst { obj(it).id == current.id }
        if (styles.size <= 1 || index == -1) {
            mes("You haven't unlocked any other styles for that yet.")
            return
        }
        val next = obj(styles[(index + 1) % styles.size])
        if (invReplaceSlot(inv, slot, 1, next, vars = current.vars).failure) {
            return
        }
        if (inv === worn) {
            rebuildAppearance()
        }
        mes("You change the style of your ${next.name}.")
    }

    private fun ProtectedAccess.unlockedCauldronStyles(): List<String> =
        HalloweenEnums.halloween_cauldron_styles
            .filter { it.key <= player.treatsDeposited }
            .sortedBy { it.key }
            .mapNotNull { it.value?.internalName }

    private fun ProtectedAccess.unlockedSpiderHats(): List<String> {
        val unlocked =
            HalloweenTreatRewardsRow.all()
                .filter { vars["varbit.hw23_reward_received_${it.index}"] != 0 }
                .mapNotNull { it.obj?.id }
                .toSet()
        return SPIDER_HAT_STYLES.filterIndexed { index, style ->
            index == 0 || obj(style).id in unlocked
        }
    }

    private fun ProtectedAccess.makeCharm() {
        if (JUNK.any { it !in inv }) {
            mes(
                "You'll need a Bruised banana, Smelly sock, Spooky egg and Old wool to make " +
                    "anything scary out of these.",
            )
            return
        }
        val combined =
            player.invTransaction(inv) {
                val from = select(inv)
                for (junk in JUNK) {
                    delete {
                        this.from = from
                        this.obj = junk.asRSCM(RSCMType.OBJ)
                        this.strictCount = 1
                    }
                }
                insert {
                    this.into = from
                    this.obj = CHARM.asRSCM(RSCMType.OBJ)
                    this.strictCount = 1
                }
            }
        if (combined.failure) {
            return
        }
        mes(
            "You combine the questionable items together to create something that could scare " +
                "Death himself!",
        )
    }

    private fun ProtectedAccess.rummage() {
        if (season.isActive) {
            mes("The cauldron is empty for now. Perhaps it'll fill up again after Halloween.")
            return
        }
        val missing = JUNK.filter { it !in inv }
        if (missing.isEmpty()) {
            mes("You already have one of everything you could find in there.")
            return
        }
        if (!inv.hasFreeSpace()) {
            mes("You don't have enough inventory space.")
            return
        }
        val found = random.pick(missing)
        invAdd(inv, found)
        val article = if (found == WOOL) "some" else "a"
        mes("You rummage through the cauldron and find $article ${obj(found).name}.")
    }

    private fun ProtectedAccess.stats() {
        mes("You have deposited ${player.treatsDeposited} treats into the Great cauldron.")
    }

    private fun obj(internal: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
            "Unknown $internal"
        }

    private companion object {
        fun EnumTypeMap<Int, ItemServerType>.styles(): List<String> =
            sortedBy { it.key }.mapNotNull { it.value?.internalName }

        const val CHARM = "obj.hw22_trick_charm"
        const val SPOOKY_CAULDRON = "loc.hw22_trick_junk_source"
        const val WOOL = "obj.hw22_trick_wool"
        const val CHARM_USES = 3

        val CAULDRON_OBJS = (0..4).map { "obj.hw22_treat_cauldron_$it" }
        val WIG_STYLES: List<String> by lazy { HalloweenEnums.halloween_wig_styles.styles() }
        val SPIDER_HAT_STYLES: List<String> by lazy {
            HalloweenEnums.halloween_spider_hat_styles.styles()
        }
        val COBWEB_CAPES = listOf("obj.hw23_reward_cobwebcape", "obj.hw23_reward_cobwebcape2")

        val JUNK: List<String> by lazy {
            HalloweenTrickEnums.halloween_trick_junk.mapNotNull { it.value?.internalName }
        }
    }
}
