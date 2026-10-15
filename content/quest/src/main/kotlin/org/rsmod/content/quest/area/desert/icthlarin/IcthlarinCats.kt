package org.rsmod.content.quest.area.desert.icthlarin

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.other.pets.PetFollowers
import org.rsmod.content.other.pets.cats.CatCare
import org.rsmod.content.other.pets.cats.CatForm
import org.rsmod.content.other.pets.cats.CatStage
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The quest's view of the player's cat. A kitten, a cat, an overgrown cat or a hellcat will do,
 * following or carried; lazy cats are too idle to open a pyramid door.
 */
@Singleton
class IcthlarinCats
@Inject
constructor(private val followers: PetFollowers, private val care: CatCare) {

    fun follower(player: Player): Npc? {
        val npc = followers.follower(player) ?: return null
        val cat = care.following(player) ?: return null
        return npc.takeIf { cat.isQuestCat }
    }

    fun followerPet(player: Player): CatForm? = care.following(player)?.takeIf { follower(player) != null }

    fun heldPet(player: Player): CatForm? =
        player.inv.firstNotNullOfOrNull { obj -> obj?.let { Cats.forObj(it.id) }?.takeIf { it.isQuestCat } }

    fun hasCat(player: Player): Boolean = follower(player) != null || heldPet(player) != null

    /** The cat the player has with them, preferring one that is following. */
    fun anyPet(player: Player): CatForm? = followerPet(player) ?: heldPet(player)

    /** Scoops up the following cat, as the player does to calm the Wanderer. */
    fun ProtectedAccess.pickUpFollower(): Boolean {
        val cat = followerPet(player) ?: return false
        if (inv.isFull()) {
            return false
        }
        if (invAdd(inv, cat.obj).failure) {
            return false
        }
        followers.dismiss(player)
        return true
    }

    /** The Sphinx keeps a cat whose owner answers her riddle wrongly. */
    fun ProtectedAccess.loseCat(): Boolean {
        if (followerPet(player) != null) {
            followers.dismiss(player)
            return true
        }
        val held = heldPet(player) ?: return false
        return invDel(inv, held.obj).success
    }

    private val CatForm.isQuestCat: Boolean
        get() = stage !== CatStage.Lazy
}
