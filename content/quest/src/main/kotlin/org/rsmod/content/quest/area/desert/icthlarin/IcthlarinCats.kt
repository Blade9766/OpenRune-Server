package org.rsmod.content.quest.area.desert.icthlarin

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.content.other.pets.cats.CatPetManager
import org.rsmod.content.other.pets.cats.CatStage
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The quest's view of the player's cat. A kitten, a cat, an overgrown cat or a hellcat will do,
 * following or carried; lazy cats are too idle to open a pyramid door.
 */
@Singleton
class IcthlarinCats @Inject constructor(private val cats: CatPetManager) {

    fun follower(player: Player): Npc? {
        val npc = cats.following(player) ?: return null
        val pet = cats.followingPet(player) ?: return null
        return npc.takeIf { pet.isQuestCat }
    }

    fun followerPet(player: Player): CatPet? = cats.followingPet(player)?.takeIf { follower(player) != null }

    fun heldPet(player: Player): CatPet? =
        player.inv.firstNotNullOfOrNull { obj -> obj?.let { CatPet.fromObj(it.id) }?.takeIf { it.isQuestCat } }

    fun hasCat(player: Player): Boolean = follower(player) != null || heldPet(player) != null

    /** The cat the player has with them, preferring one that is following. */
    fun anyPet(player: Player): CatPet? = followerPet(player) ?: heldPet(player)

    /** Scoops up the following cat, as the player does to calm the Wanderer. */
    fun ProtectedAccess.pickUpFollower(): Boolean {
        val pet = followerPet(player) ?: return false
        if (inv.isFull()) {
            return false
        }
        if (invAdd(inv, pet.obj).failure) {
            return false
        }
        cats.release(player)
        return true
    }

    /** The Sphinx keeps a cat whose owner answers her riddle wrongly. */
    fun ProtectedAccess.loseCat(): Boolean {
        if (followerPet(player) != null) {
            cats.runAway(player)
            return true
        }
        val held = heldPet(player) ?: return false
        return invDel(inv, held.obj).success
    }

    private val CatPet.isQuestCat: Boolean
        get() = stage != CatStage.LAZY
}
