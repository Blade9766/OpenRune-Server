package org.rsmod.tools.wayfindermodels

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Flat-shaded orthographic preview renders of a [Mesh], one panel per yaw angle, so a model's
 * shape and colours can be checked without a client.
 */
object MeshRenderer {
    private const val PANEL = 256
    private val ANGLES = doubleArrayOf(0.0, Math.PI / 4, Math.PI / 2, Math.PI)

    fun render(mesh: Mesh, file: File, scale: Double = 1.0) {
        val image = BufferedImage(PANEL * ANGLES.size, PANEL, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until image.width) for (y in 0 until image.height) image.setRGB(x, y, 0x2b2b2b)
        val b = mesh.bounds()
        val cx = (b[0] + b[3]) / 2.0
        val cy = (b[1] + b[4]) / 2.0
        val cz = (b[2] + b[5]) / 2.0
        val extent = max(max(b[3] - b[0], b[4] - b[1]), b[5] - b[2]).coerceAtLeast(1)
        val zoom = (PANEL - 16) / extent.toDouble() * scale
        ANGLES.forEachIndexed { panel, angle -> drawPanel(mesh, image, panel * PANEL, angle, cx, cy, cz, zoom) }
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun drawPanel(
        mesh: Mesh,
        image: BufferedImage,
        ox: Int,
        angle: Double,
        cx: Double,
        cy: Double,
        cz: Double,
        zoom: Double,
    ) {
        val depth = Array(PANEL) { DoubleArray(PANEL) { Double.MAX_VALUE } }
        val px = DoubleArray(mesh.vertices.size)
        val py = DoubleArray(mesh.vertices.size)
        val pz = DoubleArray(mesh.vertices.size)
        mesh.vertices.forEachIndexed { i, v ->
            val x = v.x - cx
            val z = v.z - cz
            val rx = x * cos(angle) - z * sin(angle)
            val rz = x * sin(angle) + z * cos(angle)
            px[i] = PANEL / 2 + rx * zoom
            py[i] = PANEL / 2 + (v.y - cy) * zoom
            pz[i] = rz
        }
        for (face in mesh.faces) {
            if (face.alpha >= 255) continue
            val (a, b, c) = Triple(face.a, face.b, face.c)
            val ux = px[b] - px[a]
            val uy = py[b] - py[a]
            val uz = pz[b] - pz[a]
            val vx = px[c] - px[a]
            val vy = py[c] - py[a]
            val vz = pz[c] - pz[a]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            val len = Mesh.length(nx, ny, nz).coerceAtLeast(1e-6)
            val light = 0.55 + 0.45 * abs((nx * -0.3 + ny * -0.6 + nz * -0.75) / len)
            val rgb = shade(hslToRgb(face.colour), light)
            fill(image, depth, ox, px[a], py[a], pz[a], px[b], py[b], pz[b], px[c], py[c], pz[c], rgb)
        }
    }

    private fun fill(
        image: BufferedImage,
        depth: Array<DoubleArray>,
        ox: Int,
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        rgb: Int,
    ) {
        val minX = max(0, min(x0, min(x1, x2)).toInt())
        val maxX = min(PANEL - 1, max(x0, max(x1, x2)).toInt() + 1)
        val minY = max(0, min(y0, min(y1, y2)).toInt())
        val maxY = min(PANEL - 1, max(y0, max(y1, y2)).toInt() + 1)
        val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if (abs(area) < 1e-9) return
        for (y in minY..maxY) for (x in minX..maxX) {
            val sx = x + 0.5
            val sy = y + 0.5
            val w0 = ((x1 - sx) * (y2 - sy) - (x2 - sx) * (y1 - sy)) / area
            val w1 = ((x2 - sx) * (y0 - sy) - (x0 - sx) * (y2 - sy)) / area
            val w2 = 1 - w0 - w1
            if (w0 < 0 || w1 < 0 || w2 < 0) continue
            val z = w0 * z0 + w1 * z1 + w2 * z2
            if (z < depth[y][x]) {
                depth[y][x] = z
                image.setRGB(ox + x, y, rgb)
            }
        }
    }

    private fun shade(rgb: Int, light: Double): Int {
        val r = ((rgb shr 16 and 0xFF) * light).toInt().coerceIn(0, 255)
        val g = ((rgb shr 8 and 0xFF) * light).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * light).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    /** Jagex's packed HSL (hue 6 bits, saturation 3, lightness 7) to RGB. */
    fun hslToRgb(hsl: Int): Int {
        val h = (hsl shr 10 and 63) / 64.0 + 0.0078125
        val s = (hsl shr 7 and 7) / 8.0 + 0.0625
        val l = (hsl and 127) / 128.0
        var r = l
        var g = l
        var b = l
        if (s != 0.0) {
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            r = hue(p, q, h + 1.0 / 3)
            g = hue(p, q, h)
            b = hue(p, q, h - 1.0 / 3)
        }
        return ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    }

    private fun hue(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            6 * t < 1 -> p + (q - p) * 6 * t
            2 * t < 1 -> q
            3 * t < 2 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
}
