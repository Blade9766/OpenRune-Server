package org.rsmod.content.quest.area.digsite

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.AMMONIUM_NITRATE
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.ARCENIA_ROOT
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.BARREL_VARBIT
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_1
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_2
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_3
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHARCOAL
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHARCOAL_MIXTURE
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEMICAL_BOOK
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEMICAL_COMPOUND
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEMICAL_POWDER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.GROUND_CHARCOAL
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.INVITATION
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.MIXED_CHEMICALS
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.NITROGLYCERIN
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PESTLE_AND_MORTAR
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.ROCK_PICK
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STONE_TABLET
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TABLET_VARBIT
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TROWEL
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.UNIDENTIFIED_LIQUID
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.VIAL
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The chemist's leftovers and what they are for: the locked chest of ammonium nitrate, the sealed
 * barrel of nitroglycerin, the four steps that turn them into a chemical compound, and the pile of
 * bricks it is meant to shift.
 *
 * Both unstable ingredients go off if they are dropped, which is why the vial is corked and the
 * compound is only ever poured onto the bricks.
 */
class DigSiteExplosive
@Inject
constructor(
    private val quest: TheDigSiteQuest,
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST_SHUT) { openChest(it.loc) }
        onOpLocU(CHEST_SHUT, CHEST_KEY) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { searchChest() }
        onOpLoc2(CHEST_OPEN) { closeChest(it.loc) }

        onOpLoc1(BARREL) { mes("Mmmm... The lid is shut tight; I'll have to find something to lever it off.") }
        onOpLoc2(BARREL) { searchBarrel() }
        // An item used on a multiloc arrives under the form the player can see, not the base type,
        // so both of the barrel's forms have to be registered as well as the base.
        for (barrel in BARREL_FORMS) {
            onOpLocU(barrel, TROWEL) { leverBarrel() }
            onOpLocU(barrel, VIAL) { fillVial() }
        }

        onOpHeldU(AMMONIUM_NITRATE, NITROGLYCERIN) { mixChemicals() }
        onOpHeldU(CHARCOAL, PESTLE_AND_MORTAR) { grindCharcoal() }
        onOpHeldU(GROUND_CHARCOAL, MIXED_CHEMICALS) { addCharcoal() }
        onOpHeldU(ARCENIA_ROOT, CHARCOAL_MIXTURE) { addArcenia() }

        // The vial is unstable from the moment it leaves the barrel, named or not.
        onOpHeld5(UNIDENTIFIED_LIQUID) { dropUnstable(UNIDENTIFIED_LIQUID, NITRO_DAMAGE) }
        onOpHeld5(NITROGLYCERIN) { dropUnstable(NITROGLYCERIN, NITRO_DAMAGE) }
        onOpHeld5(CHEMICAL_COMPOUND) { dropUnstable(CHEMICAL_COMPOUND, COMPOUND_DAMAGE) }
        onOpHeld2(UNIDENTIFIED_LIQUID) { emptyVial() }

        onOpLoc1(BRICKS) { searchBricks() }
        onOpLocU(BRICKS, CHEMICAL_COMPOUND) { pourCompound() }
        onOpLocU(BRICKS, TINDERBOX) { lightCompound() }
        onOpLocU(BRICKS, ROCK_PICK) {
            startDialogue {
                chatPlayer(
                    bored,
                    "That would be like cutting the lawn with nail scissors! It would take a year " +
                        "to chip away these rocks!",
                )
            }
        }
        for (pickaxe in PICKAXES) {
            onOpLocU(BRICKS, pickaxe) {
                startDialogue {
                    chatPlayer(
                        bored,
                        "That was a good idea, but these blocks are huge. I'm going to need " +
                            "something much more powerful to shift these...",
                    )
                }
            }
        }

        onOpLoc1(TABLET) { takeTablet() }
        onOpHeld2(STONE_TABLET) {
            startDialogue {
                chatPlayer(
                    neutral,
                    "It says: Tremble mortal, before the altar of our dread lord Zaros.",
                )
            }
        }
        onOpHeld2(INVITATION) {
            startDialogue {
                chatPlayer(
                    neutral,
                    "It says, 'I give permission for the bearer... to use the mine shafts on " +
                        "site. - signed Terrance Balando, Archaeological Expert, City of Varrock.'",
                )
            }
        }
        onOpHeld2(CHEMICAL_BOOK) { readChemicalBook() }
        for (certificate in CERTIFICATES) {
            onOpHeld1(certificate) { readCertificate(certificate) }
        }
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        if (!inv.contains(CHEST_KEY)) {
            mes("The chest is locked.")
            return
        }
        anim(OPEN_CHEST_SEQ)
        soundSynth(UNLOCK_SOUND)
        locRepo.change(chest, CHEST_OPEN, CHEST_TICKS)
    }

    private suspend fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_CHEST_SEQ)
        soundSynth(CHEST_CLOSE_SOUND)
        locRepo.change(chest, CHEST_SHUT, CHEST_TICKS)
    }

    private suspend fun ProtectedAccess.searchChest() {
        arriveDelay()
        if (inv.contains(CHEMICAL_POWDER) || inv.contains(AMMONIUM_NITRATE)) {
            mes("You have already taken the powder from the chest.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to take anything.")
            return
        }
        invAdd(inv, CHEMICAL_POWDER)
        objbox(CHEMICAL_POWDER, "You find some unusual powder inside...")
    }

    private suspend fun ProtectedAccess.searchBarrel() {
        arriveDelay()
        if (player.vars[BARREL_VARBIT] == 0) {
            startDialogue { chatPlayer(neutral, "It's closed tight.") }
            return
        }
        startDialogue {
            chatPlayer(
                quiz,
                "I can't pick this up with my bare hands! I'll need something to put it in. It " +
                    "looks and smells rather dangerous though, so it'll need to be something " +
                    "small and capable of containing dangerous chemicals.",
            )
        }
    }

    private suspend fun ProtectedAccess.leverBarrel() {
        arriveDelay()
        if (player.vars[BARREL_VARBIT] != 0) {
            startDialogue { chatPlayer(neutral, "The barrel is already open.") }
            return
        }
        anim(LEVER_SEQ)
        delay(1)
        setVarBit(player, BARREL_VARBIT, 1)
        startDialogue { chatPlayer(happy, "Great! It's opened.") }
    }

    private suspend fun ProtectedAccess.fillVial() {
        arriveDelay()
        if (player.vars[BARREL_VARBIT] == 0) {
            startDialogue { chatPlayer(neutral, "It's closed tight.") }
            return
        }
        anim(LEVER_SEQ)
        soundSynth(LIQUID_SOUND)
        delay(1)
        invReplace(inv, VIAL, 1, UNIDENTIFIED_LIQUID)
        objbox(UNIDENTIFIED_LIQUID, "You fill the vial with the liquid.")
        startDialogue {
            chatPlayer(
                worried,
                "I'm not sure what this stuff is. I had better be VERY careful with it; I had " +
                    "better not drop it either...",
            )
        }
        setVarBit(player, BARREL_VARBIT, 0)
        mes("You put the lid back on the barrel just in case it's dangerous.")
    }

    private suspend fun ProtectedAccess.mixChemicals() {
        if (!hasHerblore(MIX_LEVEL)) {
            return
        }
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, AMMONIUM_NITRATE)
        invReplace(inv, NITROGLYCERIN, 1, MIXED_CHEMICALS)
        objbox(MIXED_CHEMICALS, "You mix the ammonium nitrate into the nitroglycerin.")
    }

    private suspend fun ProtectedAccess.grindCharcoal() {
        anim(GRIND_SEQ)
        invReplace(inv, CHARCOAL, 1, GROUND_CHARCOAL)
        objbox(GROUND_CHARCOAL, "You grind the charcoal into a fine powder.")
    }

    private suspend fun ProtectedAccess.addCharcoal() {
        if (!hasHerblore(CHARCOAL_LEVEL)) {
            return
        }
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, GROUND_CHARCOAL)
        invReplace(inv, MIXED_CHEMICALS, 1, CHARCOAL_MIXTURE)
        objbox(CHARCOAL_MIXTURE, "You stir the ground charcoal into the mixture.")
    }

    private suspend fun ProtectedAccess.addArcenia() {
        if (!hasHerblore(COMPOUND_LEVEL)) {
            return
        }
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, ARCENIA_ROOT)
        invReplace(inv, CHARCOAL_MIXTURE, 1, CHEMICAL_COMPOUND)
        statAdvance("stat.herblore", COMPOUND_XP * xpMods.get(player, "stat.herblore"))
        objbox(CHEMICAL_COMPOUND, "You add the arcenia root to the mixture.")
        startDialogue { chatPlayer(happy, "Excellent! This looks just right!") }
    }

    private fun ProtectedAccess.hasHerblore(level: Int): Boolean {
        if (player.herbloreLvl >= level) {
            return true
        }
        mes("You need a Herblore level of $level to mix this.")
        return false
    }

    /** Pouring the vial out is safe, but the liquid is gone and the barrel has to be filled again. */
    private suspend fun ProtectedAccess.emptyVial() {
        anim(POUR_SEQ)
        soundSynth(LIQUID_SOUND)
        invReplace(inv, UNIDENTIFIED_LIQUID, 1, VIAL)
        mes("You pour the liquid away.")
    }

    /** Both halves of the compound go off in the player's hands rather than hit the floor. */
    private suspend fun ProtectedAccess.dropUnstable(obj: String, damage: Int) {
        if (invDel(inv, obj).failure) {
            return
        }
        spotanim(BLAST_SPOTANIM)
        soundSynth(VIAL_SOUND)
        queueHit(
            delay = 0,
            type = HitType.Typeless,
            damage = damage.coerceAtMost(player.hitpoints - 1).coerceAtLeast(0),
        )
        mes("The vial shatters as it leaves your hand and the chemicals go up with a bang!")
    }

    private suspend fun ProtectedAccess.searchBricks() {
        arriveDelay()
        startDialogue {
            chatPlayer(
                quiz,
                "Hmmm, there's a room past these bricks. If I could move them out of the way then " +
                    "I could find out what's inside. Maybe there's someone around here who can " +
                    "help...",
            )
        }
    }

    private suspend fun ProtectedAccess.pourCompound() {
        arriveDelay()
        if (player.digsiteBricksPrimed) {
            mes("There is already a charge on the bricks.")
            return
        }
        if (invDel(inv, CHEMICAL_COMPOUND).failure) {
            return
        }
        anim(POUR_SEQ)
        soundSynth(LIQUID_SOUND)
        delay(1)
        player.digsiteBricksPrimed = true
        mes("You pour the compound over the bricks...")
        startDialogue {
            chatPlayer(
                neutral,
                "Okay, the mixture is all over the bricks. I need some way to ignite this compound.",
            )
        }
    }

    private suspend fun ProtectedAccess.lightCompound() {
        arriveDelay()
        if (!player.digsiteBricksPrimed) {
            say("Now, what am I trying to achieve here?")
            return
        }
        anim(LIGHT_SEQ)
        mes("You strike the tinderbox...")
        soundSynth(FUSE_SOUND)
        delay(2)
        mes("Fizz...")
        say("Whoa! This is going to blow! I'd better run!")
        delay(2)
        player.digsiteBricksPrimed = false
        player.digsiteBricksBlown = true
        telejump(DigSiteCoords.BLAST_ESCAPE)
        spotanim(BLAST_SPOTANIM)
        soundSynth(EXPLOSION_SOUND)
        delay(1)
        startDialogue {
            chatPlayer(
                shocked,
                "Wow, that was a big explosion! What's that noise I can hear? Sounds like bones " +
                    "moving or something...",
            )
        }
    }

    private suspend fun ProtectedAccess.takeTablet() {
        arriveDelay()
        if (player.vars[TABLET_VARBIT] != 0 && (quest.isComplete(player) || carriesOrBanks(STONE_TABLET))) {
            mes("You have already taken the tablet.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to take the tablet.")
            return
        }
        anim(TAKE_SEQ)
        delay(1)
        invAdd(inv, STONE_TABLET)
        setVarBit(player, TABLET_VARBIT, 1)
        objbox(STONE_TABLET, "You take the stone tablet.")
    }

    private suspend fun ProtectedAccess.readChemicalBook() {
        mesbox(
            "The chemist's notes describe an explosive compound: ammonium nitrate stirred into " +
                "nitroglycerin, thickened with ground charcoal and finally bound with arcenia root.",
        )
        mesbox(
            "A warning is scrawled underneath: the mixture is unstable at every stage. Do not " +
                "drop it.",
        )
    }

    private suspend fun ProtectedAccess.readCertificate(certificate: String) {
        val level = CERTIFICATES.indexOf(certificate) + 1
        mesbox(
            "This is to certify that the bearer has passed the Earth Sciences level $level " +
                "examination, and may dig the level $level sites of the Varrock digsite.",
        )
    }

    private companion object {
        const val CHEST_SHUT = "loc.digchestclosed"
        const val CHEST_OPEN = "loc.digchestopen"
        const val BARREL = "loc.digbarrelclosed"
        val BARREL_FORMS =
            listOf(BARREL, "loc.qip_digsite_barrel", "loc.qip_digsite_barrel_open")
        const val BRICKS = "loc.digblastbrick"
        const val TABLET = "loc.qip_digsite_zaros_stone_tablet_multiloc"

        const val CHEST_TICKS = 100
        const val MIX_LEVEL = 6
        const val CHARCOAL_LEVEL = 8
        const val COMPOUND_LEVEL = 10
        const val COMPOUND_XP = 7.5
        const val NITRO_DAMAGE = 25
        const val COMPOUND_DAMAGE = 65

        const val OPEN_CHEST_SEQ = "seq.human_openchest"
        const val LEVER_SEQ = "seq.human_pickuptable"
        const val MIX_SEQ = "seq.human_herbing_vial"
        const val GRIND_SEQ = "seq.human_herbing_grind"
        const val POUR_SEQ = "seq.farming_pour_water"
        const val LIGHT_SEQ = "seq.human_createfire"
        const val TAKE_SEQ = "seq.human_pickupfloor"

        const val UNLOCK_SOUND = "synth.unlock"
        const val CHEST_CLOSE_SOUND = "synth.chest_close"
        const val LIQUID_SOUND = "synth.liquid"
        const val MIX_SOUND = "synth.vial_mix"
        const val FUSE_SOUND = "synth.fuse"
        const val EXPLOSION_SOUND = "synth.explosion"
        const val VIAL_SOUND = "synth.exploding_vial"
        const val BLAST_SPOTANIM = "spotanim.explodingvial"

        val CERTIFICATES = listOf(CERTIFICATE_1, CERTIFICATE_2, CERTIFICATE_3)

        val PICKAXES =
            listOf(
                "obj.bronze_pickaxe",
                "obj.iron_pickaxe",
                "obj.steel_pickaxe",
                "obj.black_pickaxe",
                "obj.mithril_pickaxe",
                "obj.adamant_pickaxe",
                "obj.rune_pickaxe",
                "obj.dragon_pickaxe",
            )
    }
}
