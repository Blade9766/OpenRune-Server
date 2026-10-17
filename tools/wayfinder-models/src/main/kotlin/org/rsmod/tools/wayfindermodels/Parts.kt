package org.rsmod.tools.wayfindermodels

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Colours (Jagex HSL16) for one mage's custom parts. */
data class Palette(
    val robe: Int,
    val trim: Int,
    val accent: Int,
    val motif: Int,
    val leather: Int,
    val rune: Int,
)

private data class Vec(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec(x * k, y * k, z * k)
    fun dot(o: Vec) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec) = Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm(): Vec {
        val l = sqrt(dot(this)).coerceAtLeast(1e-9)
        return Vec(x / l, y / l, z / l)
    }
}

private fun Mesh.v(p: Vec, label: Int) = vertex(p.x.roundToInt(), p.y.roundToInt(), p.z.roundToInt(), label)

private fun Mesh.pos(i: Int) = vertices[i].let { Vec(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }

/** Adds both windings of every face so thin plates show from either side. */
private fun Mesh.doubleSided(from: Int): Mesh {
    val added = faces.subList(from, faces.size).map { it.copy(a = it.a, b = it.c, c = it.b) }
    faces += added
    return this
}

object Parts {
    /**
     * Bends a wizard hat's tip backwards and down, giving the crooked point of the concept art.
     * [tipFraction] is how much of the hat's height, from the top, is bent.
     */
    fun bentHat(hat: Mesh, recolours: Map<Int, Int>, tipFraction: Double = 0.5, bend: Double = 24.0): Mesh {
        val mesh = hat.copy()
        val b = mesh.bounds()
        val top = b[1]
        val bottom = b[4]
        val start = top + (bottom - top) * tipFraction
        for (v in mesh.vertices) {
            if (v.y < start) {
                val t = (start - v.y) / (start - top)
                v.z += (bend * t * t).roundToInt()
                v.y += (bend * 0.35 * t * t).roundToInt()
            }
        }
        recolours.forEach { (from, to) -> mesh.recolour(from, to) }
        return mesh
    }

    /** The crystal staff with a larger, two-tone faceted crystal and recoloured wood and claws. */
    fun crystalStaff(staff: Mesh, crystal: Int, crystalLight: Int, recolours: Map<Int, Int>, growth: Double = 1.35): Mesh {
        val mesh = staff.copy()
        val crystalFaces = mesh.facesColoured(CRYSTAL_STAFF_CRYSTAL)
        val crystalVerts = mesh.verticesOf(crystalFaces)
        val (bottom, top) = axis(mesh)
        val dir = (top - bottom).norm()
        val anchor = crystalVerts.minBy { (mesh.pos(it) - bottom).dot(dir) }.let { mesh.pos(it) }
        for (i in crystalVerts) {
            val p = anchor + (mesh.pos(i) - anchor) * growth
            mesh.vertices[i].apply {
                x = p.x.roundToInt()
                y = p.y.roundToInt()
                z = p.z.roundToInt()
            }
        }
        crystalFaces.forEachIndexed { index, face -> face.colour = if (index % 3 == 0) crystalLight else crystal }
        recolours.forEach { (from, to) -> mesh.recolour(from, to) }
        return mesh
    }

    /**
     * A new staff along the same axis as [template]: a six-sided wooden shaft with gold fittings,
     * topped by a silver crescent that cradles a faceted glowing orb.
     */
    fun crescentStaff(template: Mesh, wood: Int, gold: Int, silver: Int, orb: Int, orbLight: Int): Mesh {
        val (bottom, top) = axis(template)
        val label = template.vertices.first().label
        val u = (top - bottom).norm()
        val across = (Vec(1.0, 0.0, 0.0) - u * u.x).norm()
        val depth = u.cross(across).norm()
        val mesh = Mesh()
        val shaftTop = top - u * 6.0
        tube(mesh, bottom, shaftTop, 2.4, 6, wood, label)
        for (t in listOf(0.18, 0.55, 0.93)) {
            val c = bottom + (shaftTop - bottom) * t
            tube(mesh, c - u * 2.0, c + u * 2.0, 3.2, 6, gold, label)
        }
        val centre = top + u * 12.0
        val ringRadius = 14.0
        val thickness = 3.4
        val segments = 9
        val from = -PI * 0.15
        val to = PI * 1.15
        val ring = ArrayList<Int>()
        for (s in 0..segments) {
            val a = from + (to - from) * s / segments
            val taper = 1.0 - 0.55 * kotlin.math.abs(s - segments / 2.0) / (segments / 2.0)
            val dirOut = (across * cos(a) - u * sin(a))
            val inner = centre + dirOut * (ringRadius - thickness * taper)
            val outer = centre + dirOut * (ringRadius + thickness * taper)
            for (side in listOf(-2.5, 2.5)) {
                ring += mesh.v(inner + depth * side, label)
                ring += mesh.v(outer + depth * side, label)
            }
        }
        for (s in 0 until segments) {
            val a = s * 4
            val b = a + 4
            mesh.quad(ring[a], ring[a + 1], ring[b + 1], ring[b], silver)
            mesh.quad(ring[a + 2], ring[b + 2], ring[b + 3], ring[a + 3], silver)
            mesh.quad(ring[a + 1], ring[a + 3], ring[b + 3], ring[b + 1], silver)
            mesh.quad(ring[a], ring[b], ring[b + 2], ring[a + 2], silver)
        }
        val stem = mesh.faces.size
        tube(mesh, shaftTop, centre - u * (ringRadius - 3.0), 1.6, 4, silver, label)
        mesh.doubleSided(stem)
        orb(mesh, centre, 7.5, orb, orbLight, label)
        return mesh
    }

    /**
     * Layered angular pauldrons, a rune pouch at the hip and a diamond motif on the robe front.
     * Each vertex takes the animation label of the nearest vertex of [body] (torso, arms and
     * skirt), so the pieces move with the shoulders, hips and legs they sit on.
     */
    fun regalia(torso: Mesh, skirt: Mesh, body: Mesh, palette: Palette): Mesh {
        val mesh = Mesh()
        val tb = torso.bounds()
        val shoulderY = tb[1] + 8.0
        val upper = torso.vertices.filter { it.y < tb[1] + 14 }
        val shoulderX = upper.maxOf { kotlin.math.abs(it.x) } - 8.0
        val zMid = (tb[2] + tb[5]) / 2.0
        for (side in listOf(-1.0, 1.0)) {
            pauldron(mesh, Vec(side * shoulderX, shoulderY, zMid), side, palette.robe, palette.trim, body)
        }
        val sb = skirt.bounds()
        val beltY = sb[1] + 9.0
        val frontAtBelt = skirt.vertices.filter { kotlin.math.abs(it.y - beltY) < 10 }.minOf { it.z } - 1.0
        pouch(mesh, Vec(-0.62 * (sb[3] - sb[0]) / 2.0, beltY + 6.0, frontAtBelt + 3.0), palette, body)
        val motifY = sb[1] + (sb[4] - sb[1]) * 0.62
        val frontAtMotif = skirt.vertices.filter { kotlin.math.abs(it.y - motifY) < 12 && kotlin.math.abs(it.x) < 8 }.minOf { it.z } - 1.0
        diamond(mesh, Vec(0.0, motifY, frontAtMotif), 8.0, 16.0, palette.motif, body)
        diamond(mesh, Vec(0.0, motifY, frontAtMotif - 0.8), 4.5, 9.0, palette.robe, body)
        return mesh
    }

    /**
     * A shoulder cap: a section of an ellipsoid shell over the top and outer side of the shoulder,
     * built as two overlapping tiers, each finished with a band of [trim] along its lower edge.
     */
    private fun pauldron(mesh: Mesh, centre: Vec, side: Double, colour: Int, trim: Int, body: Mesh) {
        tier(mesh, centre + Vec(side * 1.5, 3.0, 0.0), side, 14.0, 9.0, 14.0, 45.0, -35.0, colour, trim, body)
        tier(mesh, centre, side, 12.5, 10.0, 13.0, 105.0, 30.0, colour, trim, body)
    }

    private fun tier(
        mesh: Mesh,
        centre: Vec,
        side: Double,
        rx: Double,
        ry: Double,
        rz: Double,
        fromDeg: Double,
        toDeg: Double,
        colour: Int,
        trim: Int,
        body: Mesh,
    ) {
        val rows = 4
        val cols = 6
        val start = mesh.faces.size
        val grid = Array(rows + 1) { IntArray(cols + 1) }
        for (r in 0..rows) {
            val theta = Math.toRadians(fromDeg + (toDeg - fromDeg) * r / rows)
            for (c in 0..cols) {
                val phi = Math.toRadians(-70.0 + 140.0 * c / cols)
                val p = centre + Vec(side * rx * cos(theta) * cos(phi), -ry * sin(theta) * cos(phi), rz * sin(phi))
                grid[r][c] = mesh.v(p, body.nearestLabel(p.x.roundToInt(), p.y.roundToInt(), p.z.roundToInt()))
            }
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            mesh.quad(grid[r][c], grid[r][c + 1], grid[r + 1][c + 1], grid[r + 1][c], if (r == rows - 1) trim else colour)
        }
        mesh.doubleSided(start)
    }

    private fun pouch(mesh: Mesh, centre: Vec, palette: Palette, body: Mesh) {
        val start = mesh.faces.size
        box(mesh, centre, 5.0, 6.5, 3.0, palette.leather, body)
        box(mesh, centre + Vec(0.0, -4.5, -0.6), 5.4, 2.4, 3.2, palette.leather - 4, body)
        diamond(mesh, centre + Vec(0.0, 1.5, -3.4), 2.4, 3.2, palette.rune, body)
        mesh.doubleSided(start)
    }

    private fun box(mesh: Mesh, c: Vec, hx: Double, hy: Double, hz: Double, colour: Int, body: Mesh) {
        val ids = ArrayList<Int>()
        for (dx in listOf(-hx, hx)) for (dy in listOf(-hy, hy)) for (dz in listOf(-hz, hz)) {
            val p = c + Vec(dx, dy, dz)
            ids += mesh.v(p, body.nearestLabel(p.x.roundToInt(), p.y.roundToInt(), p.z.roundToInt()))
        }
        // ids: index = xi*4 + yi*2 + zi
        mesh.quad(ids[0], ids[2], ids[6], ids[4], colour)
        mesh.quad(ids[1], ids[5], ids[7], ids[3], colour)
        mesh.quad(ids[0], ids[4], ids[5], ids[1], colour)
        mesh.quad(ids[2], ids[3], ids[7], ids[6], colour)
        mesh.quad(ids[0], ids[1], ids[3], ids[2], colour)
        mesh.quad(ids[4], ids[6], ids[7], ids[5], colour)
    }

    private fun diamond(mesh: Mesh, c: Vec, hw: Double, hh: Double, colour: Int, body: Mesh) {
        val start = mesh.faces.size
        val pts = listOf(Vec(0.0, -hh, 0.0), Vec(hw, 0.0, 0.0), Vec(0.0, hh, 0.0), Vec(-hw, 0.0, 0.0)).map { c + it }
        val ids = pts.map { p -> mesh.v(p, body.nearestLabel(p.x.roundToInt(), p.y.roundToInt(), p.z.roundToInt())) }
        mesh.quad(ids[0], ids[1], ids[2], ids[3], colour, priority = 10)
        mesh.doubleSided(start)
    }

    private fun tube(mesh: Mesh, from: Vec, to: Vec, radius: Double, sides: Int, colour: Int, label: Int) {
        val u = (to - from).norm()
        val a = (if (kotlin.math.abs(u.x) < 0.9) Vec(1.0, 0.0, 0.0) else Vec(0.0, 1.0, 0.0)).let { (it - u * u.dot(it)).norm() }
        val b = u.cross(a).norm()
        val start = mesh.vertices.size
        for (end in listOf(from, to)) for (s in 0 until sides) {
            val ang = 2 * PI * s / sides
            mesh.v(end + a * (radius * cos(ang)) + b * (radius * sin(ang)), label)
        }
        for (s in 0 until sides) {
            val n = (s + 1) % sides
            mesh.quad(start + s, start + n, start + sides + n, start + sides + s, colour)
        }
        val c0 = mesh.v(from, label)
        val c1 = mesh.v(to, label)
        for (s in 0 until sides) {
            val n = (s + 1) % sides
            mesh.face(c0, start + n, start + s, colour)
            mesh.face(c1, start + sides + s, start + sides + n, colour)
        }
    }

    private fun orb(mesh: Mesh, c: Vec, r: Double, colour: Int, light: Int, label: Int) {
        val start = mesh.faces.size
        val top = mesh.v(c + Vec(0.0, -r, 0.0), label)
        val bottom = mesh.v(c + Vec(0.0, r, 0.0), label)
        val ring1 = (0 until 6).map { i -> val a = 2 * PI * i / 6; mesh.v(c + Vec(r * 0.8 * cos(a), -r * 0.55, r * 0.8 * sin(a)), label) }
        val ring2 = (0 until 6).map { i -> val a = 2 * PI * (i + 0.5) / 6; mesh.v(c + Vec(r * 0.8 * cos(a), r * 0.55, r * 0.8 * sin(a)), label) }
        for (i in 0 until 6) {
            val n = (i + 1) % 6
            mesh.face(top, ring1[n], ring1[i], if (i % 2 == 0) light else colour)
            mesh.face(ring1[i], ring1[n], ring2[i], colour)
            mesh.face(ring1[n], ring2[n], ring2[i], if (i % 2 == 1) light else colour)
            mesh.face(bottom, ring2[i], ring2[n], colour)
        }
        mesh.doubleSided(start)
    }

    /** The two vertices furthest apart, ordered bottom (higher y) then top. */
    private fun axis(mesh: Mesh): Pair<Vec, Vec> {
        var best = 0.0
        var pair = Pair(0, 0)
        for (i in mesh.vertices.indices) for (j in i + 1 until mesh.vertices.size) {
            val d = (mesh.pos(i) - mesh.pos(j)).let { it.dot(it) }
            if (d > best) {
                best = d
                pair = Pair(i, j)
            }
        }
        val a = mesh.pos(pair.first)
        val b = mesh.pos(pair.second)
        return if (a.y > b.y) Pair(a, b) else Pair(b, a)
    }

    private const val CRYSTAL_STAFF_CRYSTAL = 99
}
