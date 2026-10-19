package org.rsmod.tools.wayfindermodels

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
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

private val TOWARD_VIEWER = Vec(0.0, 0.0, -1.0)

private fun Mesh.v(p: Vec, label: Int) = vertex(p.x.roundToInt(), p.y.roundToInt(), p.z.roundToInt(), label)

private fun Mesh.v(p: Vec, body: Mesh) = v(p, body.labelAt(p))

private fun Mesh.labelAt(p: Vec) = nearestLabel(p.x.roundToInt(), p.y.roundToInt(), p.z.roundToInt())

private fun Mesh.pos(i: Int) = vertices[i].let { Vec(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }

private fun Mesh.normal(f: Face): Vec {
    val a = pos(f.a)
    return (pos(f.b) - a).cross(pos(f.c) - a)
}

private fun Mesh.centroid(f: Face) = (pos(f.a) + pos(f.b) + pos(f.c)) * (1.0 / 3)

/**
 * Winds every face added since [from] so its front points away from [inside] (given the face's
 * centroid). The client culls back faces and has no depth buffer, so each face must be single
 * and wound like the cache's own models ([Parts.winding]), or it disappears or flickers.
 */
private fun Mesh.orient(from: Int, inside: (Vec) -> Vec) {
    for (i in from until faces.size) {
        val f = faces[i]
        val c = centroid(f)
        if (normal(f).dot(c - inside(c)) * Parts.winding < 0) {
            faces[i] = f.copy(b = f.c, c = f.b)
        }
    }
}

private fun Mesh.orientAway(from: Int, centre: Vec) = orient(from) { centre }

private fun Mesh.orientToward(from: Int, outward: Vec) = orient(from) { it - outward }

object Parts {
    /** +1 or -1: the sign of (normal . outward) on the outside faces of cache models. */
    var winding = 1.0

    /** Measures [winding] from a closed cache model such as a robe skirt. */
    fun measureWinding(reference: Mesh) {
        val centre = reference.vertices.indices.map { reference.pos(it) }.reduce { a, b -> a + b } * (1.0 / reference.vertices.size)
        val sum = reference.faces.sumOf { f -> sign(reference.normal(f).dot(reference.centroid(f) - centre)) }
        winding = if (sum >= 0) 1.0 else -1.0
    }

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
        val tip = crystalVerts.maxBy { (mesh.pos(it) - bottom).dot(dir) }.let { mesh.pos(it) }
        val centre = anchor + (tip - anchor) * 0.5
        scatter(mesh, centre, dir, count = 6, radius = 27.0, size = 3.2, colour = crystal, light = crystalLight, label = mesh.vertices.first().label)
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
        val ringRadius = 15.0
        val thickness = 4.2
        val segments = 9
        val from = -PI * 0.15
        val to = PI * 1.15
        val ring = ArrayList<Int>()
        for (s in 0..segments) {
            val a = from + (to - from) * s / segments
            val taper = 1.0 - 0.55 * abs(s - segments / 2.0) / (segments / 2.0)
            val dirOut = (across * cos(a) - u * sin(a))
            val inner = centre + dirOut * (ringRadius - thickness * taper)
            val outer = centre + dirOut * (ringRadius + thickness * taper)
            for (side in listOf(-3.5, 3.5)) {
                ring += mesh.v(inner + depth * side, label)
                ring += mesh.v(outer + depth * side, label)
            }
        }
        for (s in 0 until segments) {
            val a = s * 4
            val b = a + 4
            val start = mesh.faces.size
            mesh.quad(ring[a], ring[a + 1], ring[b + 1], ring[b], silver)
            mesh.quad(ring[a + 2], ring[b + 2], ring[b + 3], ring[a + 3], silver)
            mesh.quad(ring[a + 1], ring[a + 3], ring[b + 3], ring[b + 1], silver)
            mesh.quad(ring[a], ring[b], ring[b + 2], ring[a + 2], silver)
            val segmentCentre = (a until a + 8).map { mesh.pos(ring[it]) }.reduce { x, y -> x + y } * (1.0 / 8)
            mesh.orientAway(start, segmentCentre)
        }
        tube(mesh, shaftTop, centre - u * (ringRadius - 3.0), 1.6, 4, silver, label)
        orb(mesh, centre, 8.5, orb, orbLight, label)
        scatter(mesh, centre, u, count = 8, radius = 21.0, size = 2.2, colour = orb, light = orbLight, label = label)
        return mesh
    }

    /**
     * Layered angular pauldrons, a rune pouch at the hip, a belt buckle and a diamond motif on the
     * robe front (or chest). Each vertex takes the animation label of the nearest vertex of [body]
     * (torso, arms and skirt), so the pieces move with the shoulders, hips and legs they sit on.
     */
    fun regalia(arms: Mesh, torso: Mesh, skirt: Mesh, body: Mesh, palette: Palette, chestMotif: Boolean = false): Mesh {
        val mesh = Mesh()
        val ab = arms.bounds()
        val shoulderY = ab[1] + 8.0
        val upper = arms.vertices.filter { it.y < ab[1] + 14 }
        val shoulderX = upper.maxOf { abs(it.x) } - 8.0
        val zMid = (ab[2] + ab[5]) / 2.0
        for (side in listOf(-1.0, 1.0)) {
            pauldron(mesh, Vec(side * shoulderX, shoulderY, zMid), side, palette.robe, palette.trim, body)
        }
        val sb = skirt.bounds()
        val surface = Surface(skirt)
        val beltY = sb[1] + 9.0
        pouch(mesh, -0.6 * (sb[3] - sb[0]) / 2.0, beltY + 7.0, surface, palette, body)
        buckle(mesh, sb[1] + 3.0, surface, palette, body)
        if (chestMotif) {
            val tb = torso.bounds()
            val height = tb[4] - tb[1]
            motif(mesh, tb[4] - height * 0.38, height * 0.17 / MOTIF_HALF_HEIGHT, Surface(torso), palette)
        } else {
            motif(mesh, sb[1] + (sb[4] - sb[1]) * 0.6, 1.0, surface, palette)
        }
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
                grid[r][c] = mesh.v(p, body)
            }
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            mesh.quad(grid[r][c], grid[r][c + 1], grid[r + 1][c + 1], grid[r + 1][c], if (r == rows - 1) trim else colour)
        }
        mesh.orientAway(start, centre)
    }

    /**
     * The front of a body part: how far forward (lowest z) its triangles reach around any
     * (x, y). Panels such as the robe's front split are large triangles whose corners sit far above
     * and below a detail, so the surface is ray-cast through the faces, not read off the vertices.
     */
    private class Surface(private val model: Mesh) {
        fun frontZ(x: Double, y: Double): Double {
            var front = Double.MAX_VALUE
            for (dx in SAMPLES) for (dy in SAMPLES) {
                for (f in model.faces) {
                    front = minOf(front, zAt(f, x + dx, y + dy) ?: continue)
                }
            }
            return if (front < Double.MAX_VALUE) front else model.vertices.minOf { it.z }.toDouble()
        }

        /** The animation label of the front-most face at (x, y), so a decal bends with it. */
        fun labelAt(x: Double, y: Double): Int {
            val face = model.faces.filter { zAt(it, x, y) != null }.minByOrNull { zAt(it, x, y)!! }
                ?: return model.nearestLabel(x.roundToInt(), y.roundToInt(), 0)
            val p = Vec(x, y, zAt(face, x, y)!!)
            return model.vertices[listOf(face.a, face.b, face.c).minBy { (model.pos(it) - p).dot(model.pos(it) - p) }].label
        }

        private fun zAt(f: Face, x: Double, y: Double): Double? {
            val a = model.pos(f.a)
            val b = model.pos(f.b)
            val c = model.pos(f.c)
            val det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
            if (abs(det) < 1e-9) return null
            val wa = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / det
            val wb = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / det
            val wc = 1 - wa - wb
            if (wa < 0 || wb < 0 || wc < 0) return null
            return wa * a.z + wb * b.z + wc * c.z
        }

        /** One above the part's highest render priority (or level with 9+, which interleave by depth). */
        val detailPriority = model.faces.maxOf { it.priority }.let { if (it >= 9) it else it + 1 }
    }

    /** A small leather pouch resting on the robe at the hip, with a flap and a rune on its face. */
    private fun pouch(mesh: Mesh, x: Double, y: Double, surface: Surface, palette: Palette, body: Mesh) {
        val hx = 4.5
        val hy = 5.0
        val hz = 2.0
        val back = surface.frontZ(x, y) - 0.5
        val label = body.labelAt(Vec(x, y, back))
        box(mesh, Vec(x, y, back - hz), hx, hy, hz, palette.leather, label)
        box(mesh, Vec(x, y - hy + 1.5, back - hz - 0.3), hx + 0.4, 1.6, hz + 0.3, palette.leather - 4, label)
        flatDiamond(mesh, Vec(x, y + 1.2, back - 2 * hz - DECAL_GAP), 2.0, 2.6, palette.rune, surface.detailPriority, label)
    }

    private fun buckle(mesh: Mesh, y: Double, surface: Surface, palette: Palette, body: Mesh) {
        val back = surface.frontZ(0.0, y) - 0.5
        val label = body.labelAt(Vec(0.0, y, back))
        box(mesh, Vec(0.0, y, back - 0.9), 4.0, 2.8, 0.9, BUCKLE, label)
        flatDiamond(mesh, Vec(0.0, y, back - 1.8 - DECAL_GAP), 1.6, 1.6, palette.leather - 4, surface.detailPriority, label)
    }

    /**
     * An outlined diamond on the robe front: a band between two diamonds with a small gem in the
     * middle, laid over the robe like a decal: each vertex sits a fixed gap in front of the robe
     * triangle under it and takes that triangle's animation label, so it moves with the panel.
     */
    private fun motif(mesh: Mesh, y: Double, scale: Double, surface: Surface, palette: Palette) {
        fun lift(q: Vec): Int {
            val p = q * scale
            return mesh.v(Vec(p.x, y + p.y, surface.frontZ(p.x, y + p.y) - MOTIF_GAP), surface.labelAt(p.x, y + p.y))
        }
        val outer = diamondPoints(7.5, MOTIF_HALF_HEIGHT).map(::lift)
        val inner = diamondPoints(4.5, 8.5).map(::lift)
        val start = mesh.faces.size
        for (k in 0 until 4) {
            val n = (k + 1) % 4
            mesh.quad(outer[k], outer[n], inner[n], inner[k], palette.motif, surface.detailPriority)
        }
        val gem = diamondPoints(1.8, 3.2).map(::lift)
        mesh.quad(gem[0], gem[1], gem[2], gem[3], palette.motif, surface.detailPriority)
        mesh.orientToward(start, TOWARD_VIEWER)
    }

    private fun diamondPoints(hw: Double, hh: Double) =
        listOf(Vec(0.0, -hh, 0.0), Vec(hw, 0.0, 0.0), Vec(0.0, hh, 0.0), Vec(-hw, 0.0, 0.0))

    private fun box(mesh: Mesh, c: Vec, hx: Double, hy: Double, hz: Double, colour: Int, label: Int) {
        val start = mesh.faces.size
        val ids = ArrayList<Int>()
        for (dx in listOf(-hx, hx)) for (dy in listOf(-hy, hy)) for (dz in listOf(-hz, hz)) {
            ids += mesh.v(c + Vec(dx, dy, dz), label)
        }
        // ids: index = xi*4 + yi*2 + zi
        mesh.quad(ids[0], ids[2], ids[6], ids[4], colour)
        mesh.quad(ids[1], ids[5], ids[7], ids[3], colour)
        mesh.quad(ids[0], ids[4], ids[5], ids[1], colour)
        mesh.quad(ids[2], ids[3], ids[7], ids[6], colour)
        mesh.quad(ids[0], ids[1], ids[3], ids[2], colour)
        mesh.quad(ids[4], ids[6], ids[7], ids[5], colour)
        mesh.orientAway(start, c)
    }

    /** A flat diamond facing the viewer, e.g. a rune mark on a pouch. */
    private fun flatDiamond(mesh: Mesh, c: Vec, hw: Double, hh: Double, colour: Int, priority: Int, label: Int) {
        val start = mesh.faces.size
        val ids = diamondPoints(hw, hh).map { mesh.v(c + it, label) }
        mesh.quad(ids[0], ids[1], ids[2], ids[3], colour, priority)
        mesh.orientToward(start, TOWARD_VIEWER)
    }

    private fun tube(mesh: Mesh, from: Vec, to: Vec, radius: Double, sides: Int, colour: Int, label: Int) {
        val u = (to - from).norm()
        val a = (if (abs(u.x) < 0.9) Vec(1.0, 0.0, 0.0) else Vec(0.0, 1.0, 0.0)).let { (it - u * u.dot(it)).norm() }
        val b = u.cross(a).norm()
        val start = mesh.vertices.size
        val faceStart = mesh.faces.size
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
        mesh.orientAway(faceStart, (from + to) * 0.5)
    }

    /** A small floating gem: an octahedron stretched along [u], lit on alternate faces. */
    private fun shard(mesh: Mesh, c: Vec, u: Vec, size: Double, colour: Int, light: Int, label: Int) {
        val a = (if (abs(u.x) < 0.9) Vec(1.0, 0.0, 0.0) else Vec(0.0, 1.0, 0.0)).let { (it - u * u.dot(it)).norm() }
        val b = u.cross(a).norm()
        val start = mesh.faces.size
        val top = mesh.v(c + u * (size * 1.6), label)
        val bottom = mesh.v(c - u * (size * 1.6), label)
        val ring = listOf(a, b, a * -1.0, b * -1.0).map { mesh.v(c + it * size, label) }
        for (i in 0 until 4) {
            val n = (i + 1) % 4
            mesh.face(top, ring[i], ring[n], if (i % 2 == 0) light else colour)
            mesh.face(bottom, ring[n], ring[i], if (i % 2 == 1) light else colour)
        }
        mesh.orientAway(start, c)
    }

    /** Small gems scattered in a loose ring around the [centre] of a staff head, in the staff's own space. */
    private fun scatter(mesh: Mesh, centre: Vec, u: Vec, count: Int, radius: Double, size: Double, colour: Int, light: Int, label: Int) {
        val a = (if (abs(u.x) < 0.9) Vec(1.0, 0.0, 0.0) else Vec(0.0, 1.0, 0.0)).let { (it - u * u.dot(it)).norm() }
        val b = u.cross(a).norm()
        for (i in 0 until count) {
            val angle = 2 * PI * i / count + 0.4
            val lift = (i % 3 - 1) * radius * 0.45
            val r = radius * (0.85 + 0.3 * ((i * 7) % 5) / 4.0)
            val p = centre + a * (r * cos(angle)) + b * (r * sin(angle)) + u * lift
            shard(mesh, p, u, size * (0.75 + 0.25 * (i % 2)), colour, light, label)
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
        mesh.orientAway(start, c)
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
    private const val BUCKLE = 7114

    /** Clearance between a flat detail and the surface behind it; GPU renderers depth-test, so less z-fights. */
    private const val DECAL_GAP = 1.5
    private const val MOTIF_GAP = 3.0
    private const val MOTIF_HALF_HEIGHT = 14.0
    private val SAMPLES = listOf(-3.0, 0.0, 3.0)
}
