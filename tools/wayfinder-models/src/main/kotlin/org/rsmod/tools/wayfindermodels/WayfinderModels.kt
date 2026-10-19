package org.rsmod.tools.wayfindermodels

import dev.openrune.definition.codec.ModelCodec
import dev.openrune.definition.type.model.ModelType
import dev.openrune.filesystem.Cache
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import java.io.File
import java.nio.file.Path

private const val MODELS_INDEX = 7
private val PREVIEWS = File("build/wayfinder-models")
private val OUTPUT = File("content/travel/wayfinder/pack/src/main/resources/pack/models")

class CacheModels(private val cache: Cache) {
    fun model(id: Int): ModelType {
        val data = checkNotNull(cache.data(MODELS_INDEX, id, 0)) { "No model $id in the cache" }
        return ModelCodec(id, emptyList()).read(Unpooled.wrappedBuffer(data))
    }

    fun mesh(id: Int): Mesh = Mesh.of(model(id))

    fun exists(id: Int): Boolean = cache.data(MODELS_INDEX, id, 0) != null
}

/** One mage: the cache parts it wears, the colours the npc config swaps on them, and its palette. */
private class Mage(
    val name: String,
    val hair: List<Int>,
    val hat: Int,
    val hatHead: Int,
    val torso: Int,
    val arms: Int,
    val skirt: Int,
    val hands: Int,
    val feet: Int,
    val staff: Int,
    val recolours: Map<Int, Int>,
    val palette: Palette,
)

private val VAELOR =
    Mage(
        name = "vaelor",
        hair = listOf(214, 250),
        hat = 11331,
        hatHead = 11403,
        torso = 10984,
        arms = 10979,
        skirt = 10982,
        hands = 176,
        feet = 181,
        staff = 38312,
        recolours = mapOf(8741 to 50846, 25238 to 50846, 38814 to 50846, 24 to 7114, 6798 to 86, 0 to 4626),
        palette = Palette(robe = 50846, trim = 7114, accent = 7097, motif = 50645, leather = 5662, rune = 51022),
    )

private val ELOWEN =
    Mage(
        name = "elowen",
        hair = listOf(393, 437),
        hat = 11354,
        hatHead = 11407,
        torso = 11359,
        arms = 11348,
        skirt = 11357,
        hands = 353,
        feet = 358,
        staff = 39590,
        recolours = mapOf(38814 to 43808, 8741 to 35484, 25238 to 42644, 24 to 103, 6798 to 2842, 0 to 4626),
        palette = Palette(robe = 35484, trim = 103, accent = 103, motif = 38490, leather = 5662, rune = 33760),
    )

/** Model ids for the generated parts; each is a `[gamevals.models]` entry in the wayfinder module. */
private val IDS =
    mapOf(
        "wayfinder_vaelor_hat" to 64100,
        "wayfinder_vaelor_hat_head" to 64101,
        "wayfinder_vaelor_staff" to 64102,
        "wayfinder_vaelor_regalia" to 64103,
        "wayfinder_elowen_hat" to 64104,
        "wayfinder_elowen_hat_head" to 64105,
        "wayfinder_elowen_staff" to 64106,
        "wayfinder_elowen_regalia" to 64107,
    )

fun main(args: Array<String>) {
    val cache = Cache.load(Path.of(".data/cache/LIVE"))
    try {
        val models = CacheModels(cache)
        if (args.firstOrNull() == "inspect") {
            inspect(models, args.drop(1).map { it.toInt() })
            return
        }
        for (mage in listOf(VAELOR, ELOWEN)) {
            build(models, mage)
        }
    } finally {
        cache.close()
    }
}

private fun build(models: CacheModels, mage: Mage) {
    Parts.measureWinding(models.mesh(mage.skirt))
    val hatColours = mapOf(38814 to mage.recolours.getValue(38814), 24 to mage.recolours.getValue(24))
    val hat = Parts.bentHat(models.mesh(mage.hat), hatColours)
    val hatHead = Parts.bentHat(models.mesh(mage.hatHead), hatColours)
    val staff =
        if (mage === VAELOR) {
            Parts.crystalStaff(models.mesh(mage.staff), crystal = 43968, crystalLight = 43986, recolours = mapOf(398 to 7073, 280 to 7114, 6449 to 7097))
        } else {
            Parts.crescentStaff(models.mesh(mage.staff), wood = 7073, gold = 7114, silver = 103, orb = 33760, orbLight = 33790)
        }
    val torso = models.mesh(mage.torso)
    val arms = models.mesh(mage.arms)
    val skirt = models.mesh(mage.skirt)
    val body = Mesh().append(torso).append(arms).append(skirt)
    val regalia = Parts.regalia(arms, torso, skirt, body, mage.palette, chestMotif = mage === VAELOR)

    val base = models.model(mage.hat)
    write(models, "wayfinder_${mage.name}_hat", hat, base)
    write(models, "wayfinder_${mage.name}_hat_head", hatHead, models.model(mage.hatHead))
    write(models, "wayfinder_${mage.name}_staff", staff, models.model(mage.staff))
    write(models, "wayfinder_${mage.name}_regalia", regalia, base)

    val figure = Mesh()
    for (id in mage.hair + listOf(mage.torso, mage.arms, mage.skirt, mage.hands, mage.feet)) {
        figure.append(models.mesh(id))
    }
    mage.recolours.forEach { (from, to) -> figure.recolour(from, to) }
    figure.append(hat).append(staff).append(regalia)
    MeshRenderer.render(figure, File(PREVIEWS, "${mage.name}.png"))
    MeshRenderer.render(figure, File(PREVIEWS, "${mage.name}_closeup.png"), scale = 2.2)
    val head = Mesh()
    mage.hair.forEach { head.append(models.mesh(it)) }
    mage.recolours.forEach { (from, to) -> head.recolour(from, to) }
    MeshRenderer.render(head.append(hat), File(PREVIEWS, "${mage.name}_head.png"))
    MeshRenderer.render(staff, File(PREVIEWS, "${mage.name}_staff.png"))
}

private fun write(models: CacheModels, name: String, mesh: Mesh, base: ModelType) {
    val id = IDS.getValue(name)
    check(!models.exists(id) || File(OUTPUT, "$name.dat").exists()) { "Model id $id is already used in the cache" }
    val encoded = mesh.toModel(base).encode()
    OUTPUT.mkdirs()
    File(OUTPUT, "$name.dat").writeBytes(ByteBufUtil.getBytes(encoded))
    println("wrote $name ($id): ${mesh.vertices.size} vertices, ${mesh.faces.size} faces")
}

private fun inspect(models: CacheModels, ids: List<Int>) {
    val all = Mesh()
    for (id in ids) {
        val mesh = models.mesh(id)
        val b = mesh.bounds()
        val labels = mesh.vertices.map { it.label }.toSortedSet()
        val colours = mesh.faces.groupingBy { it.colour }.eachCount().entries.sortedByDescending { it.value }.take(8)
        println("model $id: v=${mesh.vertices.size} f=${mesh.faces.size} x=${b[0]}..${b[3]} y=${b[1]}..${b[4]} z=${b[2]}..${b[5]} labels=$labels colours=$colours")
        MeshRenderer.render(mesh, File(PREVIEWS, "inspect_$id.png"))
        all.append(mesh)
    }
    MeshRenderer.render(all, File(PREVIEWS, "inspect_all.png"))
}
