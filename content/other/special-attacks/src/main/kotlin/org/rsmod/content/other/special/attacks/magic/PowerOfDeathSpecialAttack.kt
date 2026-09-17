package org.rsmod.content.other.special.attacks.magic

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.instant.InstantSpecialAttack
import org.rsmod.content.other.special.attacks.specialAnim
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

/**
 * Power of Death - the staff of the dead family special: melee damage taken is halved for the next
 * minute, for as long as the staff stays equipped.
 *
 * The halving itself lives in the combat formulas, which read the expiration clock this sets;
 * [PowerOfDeathUnequipScript] clears it when the staff comes off.
 */
class PowerOfDeathSpecialAttack @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val staffOfLight =
            PowerOfDeath(
                worldRepo,
                "seq.staff_of_light_special",
                "spotanim.staff_of_light_special_start",
                "spotanim.staff_of_light_special_extra",
            )
        registerInstant("obj.staff_of_light", staffOfLight)

        val staffOfTheDead =
            PowerOfDeath(
                worldRepo,
                "seq.sotd_special",
                "spotanim.sotd_special_start",
                "spotanim.sotd_special_extra",
            )
        for (staff in STAFF_OF_THE_DEAD_VARIANTS) {
            registerInstant(staff, staffOfTheDead)
        }
    }

    private class PowerOfDeath(
        private val worldRepo: WorldRepository,
        private val seq: String,
        private val startSpot: String,
        private val extraSpot: String,
    ) : InstantSpecialAttack {
        override suspend fun ProtectedAccess.activate(): Boolean {
            specialAnim(seq)
            spotanim(startSpot)
            spotanim(extraSpot, height = 96, slot = constants.spotanim_slot_combat)

            val weapon = getOrNull(player.righthand)
            val sound = weapon?.paramOrNull(params.attack_sound_stance1)
            if (sound != null) {
                worldRepo.soundArea(player, sound.id, radius = SOUND_RADIUS)
            }

            player.powerOfDeathExpiration = mapClock + DURATION_TICKS
            return true
        }
    }

    internal companion object {
        internal var Player.powerOfDeathExpiration by intVarp("varp.sotd_spec_expiration")

        /** Every Power of Death staff that is not the staff of light. */
        internal val STAFF_OF_THE_DEAD_VARIANTS =
            listOf(
                "obj.sotd",
                "obj.br_sotd",
                "obj.staff_of_balance",
                "obj.toxic_sotd",
                "obj.toxic_sotd_charged",
                "obj.toxic_sotd_deadman",
                "obj.toxic_sotd_charged_deadman",
            )

        internal val POWER_OF_DEATH_STAVES = STAFF_OF_THE_DEAD_VARIANTS + "obj.staff_of_light"

        private const val SOUND_RADIUS = 10

        /** One minute, as the special's own description states. */
        private const val DURATION_TICKS = 100
    }
}
