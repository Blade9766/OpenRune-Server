package org.rsmod.tools.wayfindermodels

import dev.openrune.definition.type.model.ModelType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A vertex in model space: x across, y down (feet near 0, head negative), z depth. */
data class Vertex(var x: Int, var y: Int, var z: Int, var label: Int)

data class Face(val a: Int, val b: Int, val c: Int, var colour: Int, val alpha: Int = 0, val priority: Int = 0)

/**
 * An editable triangle mesh. Every vertex keeps the animation label (vertex skin) of the body part
 * it moves with, so a part built here bends and swings with the same animations as the cache part
 * it sits on.
 */
class Mesh {
    val vertices = ArrayList<Vertex>()
    val faces = ArrayList<Face>()

    fun vertex(x: Int, y: Int, z: Int, label: Int): Int {
        vertices += Vertex(x, y, z, label)
        return vertices.lastIndex
    }

    fun face(a: Int, b: Int, c: Int, colour: Int, priority: Int = 0) {
        faces += Face(a, b, c, colour, priority = priority)
    }

    fun quad(a: Int, b: Int, c: Int, d: Int, colour: Int, priority: Int = 0) {
        face(a, b, c, colour, priority)
        face(a, c, d, colour, priority)
    }

    fun append(other: Mesh): Mesh {
        val offset = vertices.size
        other.vertices.forEach { vertices += it.copy() }
        other.faces.forEach { faces += it.copy(a = it.a + offset, b = it.b + offset, c = it.c + offset) }
        return this
    }

    fun recolour(from: Int, to: Int): Mesh {
        faces.forEach { if (it.colour == from) it.colour = to }
        return this
    }

    fun facesColoured(colour: Int): List<Face> = faces.filter { it.colour == colour }

    fun verticesOf(faces: Collection<Face>): Set<Int> = faces.flatMapTo(HashSet()) { listOf(it.a, it.b, it.c) }

    fun bounds(indices: Collection<Int> = vertices.indices.toList()): IntArray {
        val vs = indices.map { vertices[it] }
        return intArrayOf(vs.minOf { it.x }, vs.minOf { it.y }, vs.minOf { it.z }, vs.maxOf { it.x }, vs.maxOf { it.y }, vs.maxOf { it.z })
    }

    /** The label of the vertex nearest (x, y, z), optionally only among [candidates]. */
    fun nearestLabel(x: Int, y: Int, z: Int, candidates: Collection<Int> = vertices.indices.toList()): Int =
        vertices[candidates.minBy { i -> dist2(vertices[i], x, y, z) }].label

    fun copy(): Mesh = Mesh().append(this)

    fun toModel(base: ModelType): ModelType =
        base.copy(
            vertexCount = vertices.size,
            triangleCount = faces.size,
            textureTriangleCount = 0,
            triangleColors = ShortArray(faces.size) { faces[it].colour.toShort() },
            triangleAlphas = IntArray(faces.size) { faces[it].alpha },
            triangleSkins = null,
            triangleRenderTypes = null,
            triangleRenderPriorities = IntArray(faces.size) { faces[it].priority },
            triangleVertex1 = IntArray(faces.size) { faces[it].a },
            triangleVertex2 = IntArray(faces.size) { faces[it].b },
            triangleVertex3 = IntArray(faces.size) { faces[it].c },
            vertexPositionsX = IntArray(vertices.size) { vertices[it].x },
            vertexPositionsY = IntArray(vertices.size) { vertices[it].y },
            vertexPositionsZ = IntArray(vertices.size) { vertices[it].z },
            vertexSkins = IntArray(vertices.size) { vertices[it].label },
            triangleTextures = null,
            textureRenderTypes = null,
            textureTriangleVertex1 = null,
            textureTriangleVertex2 = null,
            textureTriangleVertex3 = null,
            textureScaleX = null,
            textureScaleY = null,
            textureScaleZ = null,
            textureRotation = null,
            textureDirection = null,
            faceZOffsets = null,
            textureSpeed = null,
            textureTransU = null,
            textureTransV = null,
            textureCoordinates = null,
            skeletalBones = null,
            skeletalScales = null,
            emitters = null,
            effectors = null,
            faceBillboards = null,
        )

    companion object {
        fun of(model: ModelType): Mesh {
            val mesh = Mesh()
            val skins = model.vertexSkins
            for (i in 0 until model.vertexCount) {
                mesh.vertex(model.vertexPositionsX!![i], model.vertexPositionsY!![i], model.vertexPositionsZ!![i], skins?.get(i) ?: 0)
            }
            val alphas = model.triangleAlphas
            val priorities = model.triangleRenderPriorities
            for (i in 0 until model.triangleCount) {
                mesh.faces +=
                    Face(
                        model.triangleVertex1!![i],
                        model.triangleVertex2!![i],
                        model.triangleVertex3!![i],
                        model.triangleColors!![i].toInt() and 0xFFFF,
                        alphas?.get(i) ?: 0,
                        priorities?.get(i) ?: model.renderPriority,
                    )
            }
            return mesh
        }

        fun dist2(v: Vertex, x: Int, y: Int, z: Int): Long {
            val dx = (v.x - x).toLong()
            val dy = (v.y - y).toLong()
            val dz = (v.z - z).toLong()
            return dx * dx + dy * dy + dz * dz
        }

        fun length(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z)

        fun rotateY(x: Double, z: Double, angle: Double): Pair<Double, Double> =
            Pair(x * cos(angle) - z * sin(angle), x * sin(angle) + z * cos(angle))
    }
}
