package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_IN_BACKPACK
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Greegrees: wielding one on Ape Atoll or in the Ardougne Zoo turns the player into the monkey
 * whose bones went into it. `varp.mm_greegree_form` mirrors the transformation so the atoll's
 * hunt modes leave monkeys alone, and the appearance is rebuilt from the wielded weapon on login
 * because a transmog does not survive a relog.
 */
@Singleton
class Greegree @Inject constructor(private val launcher: ProtectedAccessLauncher) : PluginScript() {

    class Form(val greegree: String, val npc: String, val into: String, val outOf: String) {
        val npcId: Int = npc.asRSCM(RSCMType.NPC)
    }

    override fun ScriptContext.startup() {
        onWearposChange {
            if (wearpos == Wearpos.RightHand) {
                launcher.launch(player) { refresh(announce = true) }
            }
        }
        onPlayerLogin { launcher.launch(player) { refresh(announce = false) } }
    }

    fun isMonkey(player: Player): Boolean = Greegree.isMonkey(player)

    private suspend fun ProtectedAccess.refresh(announce: Boolean) {
        val form = formOf(player.righthand)
        if (form != null) {
            if (player.transmog?.id != form.npcId) {
                transmog(form.npc)
                rebuildAppearance()
                VarPlayerIntMapSetter.set(player, FORM_VARP, 1)
                if (announce) {
                    soundSynth(form.into)
                    mes("You feel your body twist and shrink. You have become a monkey!")
                }
            }
        } else if (player.transmog != null && player.transmog?.id in formsByNpc) {
            val outOf = formsByNpc[player.transmog?.id]?.outOf
            resetTransmog()
            rebuildAppearance()
            VarPlayerIntMapSetter.set(player, FORM_VARP, 0)
            if (announce) {
                outOf?.let { soundSynth(it) }
                mes("You are yourself again.")
            }
        }
    }

    companion object {
        const val FORM_VARP = "varp.mm_greegree_form"

        private val formsByObj by lazy { FORMS.associateBy { it.greegree.asRSCM(RSCMType.OBJ) } }
        private val formsByNpc by lazy { FORMS.associateBy { it.npcId } }

        fun isMonkey(player: Player): Boolean = formOf(player.righthand) != null

        fun formOf(obj: InvObj?): Form? = obj?.let { formsByObj[it.id] }

        fun isGreegree(objId: Int): Boolean = objId in formsByObj

        fun canTransform(coords: CoordGrid): Boolean = coords.onApeAtoll() || coords.inZoo()

        val APE_ATOLL_X = 2688..2815
        val APE_ATOLL_Z = 2688..2815
        val APE_ATOLL_DUNGEON_Z = 9088..9215

        val FORMS =
            listOf(
                Form("obj.mm_monkey_greegree_for_normal_monkey", "npc.mm_transmogrification_normal_monkey", MonkeyMadness.SOUND_HUMAN_INTO_MONKEY, MonkeyMadness.SOUND_MONKEY_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_small_ninja_monkey", "npc.mm_transmogrification_small_ninja_monkey", MonkeyMadness.SOUND_HUMAN_INTO_SMALLMONKEY, MonkeyMadness.SOUND_SMALLMONKEY_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_medium_ninja_monkey", "npc.mm_transmogrification_medium_ninja_monkey", MonkeyMadness.SOUND_HUMAN_INTO_MONKEY, MonkeyMadness.SOUND_MONKEY_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_normal_gorilla", "npc.mm_transmogrification_normal_gorilla", MonkeyMadness.SOUND_HUMAN_INTO_GORILLA, MonkeyMadness.SOUND_GORILLA_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_bearded_gorilla", "npc.mm_transmogrification_bearded_gorilla", MonkeyMadness.SOUND_HUMAN_INTO_GORILLA, MonkeyMadness.SOUND_GORILLA_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_ancient_monkey_skull", "npc.mm_transmogrification_ancient_monkey", MonkeyMadness.SOUND_HUMAN_INTO_GORILLA, MonkeyMadness.SOUND_GORILLA_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_small_zombie_monkey", "npc.mm_transmogrification_small_zombie_monkey", MonkeyMadness.SOUND_HUMAN_INTO_ZOMBIE, MonkeyMadness.SOUND_ZOMBIE_INTO_HUMAN),
                Form("obj.mm_monkey_greegree_for_large_zombie_monkey", "npc.mm_transmogrification_large_zombie_monkey", MonkeyMadness.SOUND_HUMAN_INTO_ZOMBIE, MonkeyMadness.SOUND_ZOMBIE_INTO_HUMAN),
            )

        fun CoordGrid.onApeAtoll(): Boolean = x in APE_ATOLL_X && (z in APE_ATOLL_Z || z in APE_ATOLL_DUNGEON_Z)

        fun CoordGrid.inZoo(): Boolean = level == 0 && x in MonkeyMadness.ZOO_X && z in MonkeyMadness.ZOO_Z
    }
}

/** A greegree only works where there are monkeys to fool. */
class GreegreeWearHook @Inject constructor() : PlayerRestrictionHook {
    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || !Greegree.isGreegree(action.obj.id)) {
            return null
        }
        if (Greegree.canTransform(player.coords)) {
            return null
        }
        return "You can only use the greegree on Ape Atoll or in the Ardougne Zoo."
    }
}

/** Monkeys do not fight. */
class GreegreeAttackHook @Inject constructor() : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (Greegree.isMonkey(player)) NpcAttackValidateResult.Deny("You can't fight while you're a monkey.") else NpcAttackValidateResult.Pass
}

/** A monkey in a backpack does not care for magic: any teleport sends it fleeing. */
class MonkeyBackpackTeleportHook @Inject constructor() : PlayerTeleportValidateHook {
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        if (player.inv.contains(MONKEY_IN_BACKPACK)) {
            player.invDel(player.inv, MONKEY_IN_BACKPACK.asRSCM(RSCMType.OBJ), player.inv.count(MONKEY_IN_BACKPACK))
            player.mes("The monkey shrieks at the magic and leaps out of your backpack!")
        }
        return null
    }
}
