package dev.openrune.pack

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.cache.tools.TaskPriority
import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.zip.GZIPInputStream

/**
 * Puts Jagex's own compiled bytes back for every clientscript that touches a long varp.
 *
 * The CS2 compiler has no long-varp push/pop opcodes, so it compiles `%ge_itemsink_price_long_0`
 * and friends as int varps. The client then underflows its long stack and the script dies
 * (the Grand Exchange setup panel never opens). Scripts a pack overrides are left as compiled;
 * the rest are fetched from the OpenRS2 cache the CS2 bundle was dumped from and kept on disk.
 */
class RestoreLongVarpScripts(
    private val cs2Root: File,
    private val overrideSources: List<File>,
) : CacheTask() {
    override val priority: TaskPriority = TaskPriority.CS2

    override fun init(cache: Cache) {
        val longVarps = longVarps()
        if (longVarps.isEmpty()) {
            return
        }
        val cacheId = openRs2CacheId() ?: run {
            logger.warn { "No openRs2CacheId in ${File(cs2Root, "bundle.json")}; long-varp scripts stay miscompiled." }
            return
        }
        val overridden = overriddenScripts()
        val usage = Regex("%(${longVarps.joinToString("|") { Regex.escape(it) }})(?![A-Za-z0-9_])")
        val scripts =
            File(cs2Root, "script").listFiles { file -> file.extension == "cs2" }.orEmpty()
                .filter { it.nameWithoutExtension !in overridden }
                .mapNotNull { file ->
                    val text = file.readText()
                    if (!usage.containsMatchIn(text)) return@mapNotNull null
                    SCRIPT_ID.find(text)?.groupValues?.get(1)?.toInt()
                }
        var restored = 0
        for (id in scripts) {
            val container = jagexScript(cacheId, id) ?: continue
            val bytes = unpack(container) ?: continue
            cache.write(SCRIPT_INDEX, id, bytes)
            restored++
        }
        logger.info { "Restored $restored/${scripts.size} long-varp clientscripts from OpenRS2 cache $cacheId." }
    }

    private fun longVarps(): List<String> =
        File(cs2Root, "symbols/varp.sym").takeIf { it.isFile }?.readLines().orEmpty()
            .map { it.split('\t') }
            .filter { it.size >= 3 && it[2] == "long" }
            .map { it[1] }

    private fun openRs2CacheId(): Int? {
        val bundle = File(cs2Root, "bundle.json").takeIf { it.isFile } ?: return null
        return CACHE_ID.find(bundle.readText())?.groupValues?.get(1)?.toInt()
    }

    private fun overriddenScripts(): Set<String> =
        overrideSources
            .flatMap { source -> source.walkTopDown().filter { it.isFile && it.extension == "cs2" }.toList() }
            .flatMap { file -> SCRIPT_HEADER.findAll(file.readText()).map { it.value }.toList() }
            .toSet()

    private fun jagexScript(cacheId: Int, id: Int): ByteArray? {
        val stored = File(cs2Root, ".jagex/$cacheId/$id.dat")
        if (stored.isFile) {
            return stored.readBytes()
        }
        val uri = URI("https://archive.openrs2.org/caches/runescape/$cacheId/archives/$SCRIPT_INDEX/groups/$id.dat")
        val response =
            try {
                http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofByteArray())
            } catch (e: Exception) {
                logger.warn(e) { "Could not fetch clientscript $id from $uri." }
                return null
            }
        if (response.statusCode() != 200) {
            logger.warn { "Could not fetch clientscript $id from $uri (HTTP ${response.statusCode()})." }
            return null
        }
        stored.parentFile.mkdirs()
        stored.writeBytes(response.body())
        return response.body()
    }

    private fun unpack(container: ByteArray): ByteArray? {
        val buffer = ByteBuffer.wrap(container)
        val compression = buffer.get().toInt()
        val length = buffer.getInt()
        return when (compression) {
            0 -> ByteArray(length).also(buffer::get)
            2 -> {
                buffer.getInt()
                GZIPInputStream(container.inputStream(buffer.position(), length)).use { it.readBytes() }
            }
            else -> {
                logger.warn { "Unsupported clientscript compression $compression." }
                null
            }
        }
    }

    private companion object {
        private val logger = InlineLogger()
        private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        private const val SCRIPT_INDEX = 12
        private val SCRIPT_ID = Regex("""\A// (\d+)""")
        private val SCRIPT_HEADER = Regex("""(?m)^\[[a-z_]+,[^\]\r\n]+]""")
        private val CACHE_ID = Regex(""""openRs2CacheId"\s*:\s*(\d+)""")
    }
}
