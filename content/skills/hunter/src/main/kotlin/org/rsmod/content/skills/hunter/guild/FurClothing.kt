package org.rsmod.content.skills.hunter.guild

/**
 * Custom Fur Clothing products in `enum.1559` order (the order the client lays out the grid).
 * Any one of [furs] may be used; tatty and perfect cat furs both make tops and legs.
 */
enum class FurClothing(val product: String, val furs: List<String>, val furCount: Int, val fee: Int) {
    PolarTop("obj.hunting_camoflauge_robe_polar", listOf("obj.huntingbeast_polar_fur"), 2, 20),
    PolarLegs("obj.hunting_trousers_polar", listOf("obj.huntingbeast_polar_fur"), 2, 20),
    WoodTop("obj.hunting_camoflauge_robe_wood", listOf("obj.huntingbeast_woodland_fur"), 2, 20),
    WoodLegs("obj.hunting_trousers_wood", listOf("obj.huntingbeast_woodland_fur"), 2, 20),
    JungleTop("obj.hunting_camoflauge_robe_jungle", listOf("obj.huntingbeast_jungle_fur"), 2, 20),
    JungleLegs("obj.hunting_trousers_jungle", listOf("obj.huntingbeast_jungle_fur"), 2, 20),
    DesertTop("obj.hunting_camoflauge_robe_desert", listOf("obj.huntingbeast_desert_fur"), 2, 20),
    DesertLegs("obj.hunting_trousers_desert", listOf("obj.huntingbeast_desert_fur"), 2, 20),
    LarupiaTop("obj.hunting_torso_jaguar", JAGUAR, 1, 100),
    LarupiaLegs("obj.hunting_trousers_jaguar", JAGUAR, 1, 100),
    LarupiaHat("obj.hunting_hat_jaguar", listOf("obj.hunting_fur_jaguar_perfect"), 1, 500),
    GraahkTop("obj.hunting_torso_leopard", LEOPARD, 1, 150),
    GraahkLegs("obj.hunting_trousers_leopard", LEOPARD, 1, 150),
    GraahkHeaddress("obj.hunting_hat_leopard", listOf("obj.hunting_fur_leopard_perfect"), 1, 750),
    KyattTop("obj.hunting_torso_tiger", TIGER, 1, 200),
    KyattLegs("obj.hunting_trousers_tiger", TIGER, 1, 200),
    KyattHat("obj.hunting_hat_tiger", listOf("obj.hunting_fur_tiger_perfect"), 1, 1000),
    DarkGloves("obj.hunting_silent_gloves", listOf("obj.huntingbeast_silent_fur"), 2, 600),
    SpottedCape("obj.hunting_light_cape", listOf("obj.huntingbeast_speedy_fur"), 2, 400),
    DashingCape("obj.hunting_lighter_cape", listOf("obj.huntingbeast_speedy2_fur"), 2, 800),
}

private val JAGUAR = listOf("obj.hunting_fur_jaguar_shabby", "obj.hunting_fur_jaguar_perfect")
private val LEOPARD = listOf("obj.hunting_fur_leopard_shabby", "obj.hunting_fur_leopard_perfect")
private val TIGER = listOf("obj.hunting_fur_tiger_shabby", "obj.hunting_fur_tiger_perfect")
