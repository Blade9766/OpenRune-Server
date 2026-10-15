package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.types.ItemServerType
import jakarta.inject.Singleton
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Extension points for the three god spells (Saradomin Strike, Claws of Guthix and Flames of
 * Zamorak). The Mage Arena content decides where they may be cast, keeps count of the casts made
 * inside the arena and adds the Charge bonus; this registry lets it do so without the spell
 * module knowing anything about the arena.
 */
@Singleton
class GodSpellHooks {
    /** Returns a message refusing the cast, or `null` to allow it. */
    fun interface CastValidator {
        fun validate(player: Player, spell: ItemServerType, target: PathingEntity): String?
    }

    /** Called once per successful cast, whether or not the spell lands. */
    fun interface CastListener {
        fun onCast(player: Player, spell: ItemServerType, target: PathingEntity)
    }

    /** Extra max hit for a cast, e.g. the Charge spell's boost. */
    fun interface MaxHitBonus {
        fun bonus(player: Player, spell: ItemServerType): Int
    }

    private val validators = mutableListOf<CastValidator>()
    private val listeners = mutableListOf<CastListener>()
    private val bonuses = mutableListOf<MaxHitBonus>()

    fun addValidator(validator: CastValidator) {
        validators += validator
    }

    fun addListener(listener: CastListener) {
        listeners += listener
    }

    fun addMaxHitBonus(bonus: MaxHitBonus) {
        bonuses += bonus
    }

    fun validate(player: Player, spell: ItemServerType, target: PathingEntity): String? =
        validators.firstNotNullOfOrNull { it.validate(player, spell, target) }

    fun notifyCast(player: Player, spell: ItemServerType, target: PathingEntity) {
        for (listener in listeners) {
            listener.onCast(player, spell, target)
        }
    }

    fun maxHitBonus(player: Player, spell: ItemServerType): Int = bonuses.sumOf { it.bonus(player, spell) }
}
