package com.codein.ide.signer.apk

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-Kotlin `zipalign`. Rewrites an archive so that every uncompressed (STORED) entry's data
 * starts at an offset aligned to 4 bytes, and uncompressed native libraries (`.so` files under `lib/`) are aligned
 * to 16 KiB pages (required for 16 KB page-size devices on Android 15+). Alignment is achieved by
 * padding the local file header's extra field, exactly like the SDK tool.
 */
@Singleton
class ZipAligner @Inject constructor() {

    fun align(input: File, output: File, alignment: Int = DEFAULT_ALIGNMENT, soAlignment: Int = PAGE_ALIGNMENT_16K) {
        output.parentFile?.mkdirs()
        ZipFile(input).use { zip ->
            CountingOutputStream(output.outputStream().buffered()).use { counting ->
                ZipOutputStream(counting).use { out ->
                    for (entry in zip.entries().toList()) {
                        val data = zip.getInputStream(entry).use { it.readBytes() }
                        val stored = entry.method == ZipEntry.STORED
                        val newEntry = ZipEntry(entry.name).apply {
                            time = FIXED_TIMESTAMP
                            comment = entry.comment
                        }
                        if (stored) {
                            val crc = CRC32().apply { update(data) }
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = data.size.toLong()
                            newEntry.compressedSize = data.size.toLong()
                            newEntry.crc = crc.value
                            val required = if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) soAlignment else alignment
                            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
                            val originalExtra = stripAlignmentExtra(entry.extra)
                            // Local header = 30 bytes + name + extra; data starts right after.
                            val headerStart = counting.count
                            val dataStartNoPad = headerStart + LOCAL_HEADER_SIZE + nameBytes.size + originalExtra.size
                            var padding = ((required - (dataStartNoPad % required)) % required).toInt()
                            if (padding != 0) {
                                while (padding < ALIGNMENT_EXTRA_HEADER) padding += required
                            }
                            newEntry.extra = if (padding == 0) originalExtra.takeIf { it.isNotEmpty() } else originalExtra + alignmentExtra(padding, required)
                        } else {
                            newEntry.method = ZipEntry.DEFLATED
                        }
                        out.putNextEntry(newEntry)
                        out.write(data)
                        out.closeEntry()
                    }
                }
            }
        }
    }

    /** Verifies that all STORED entries are aligned (like `zipalign -c`). */
    fun verify(file: File, alignment: Int = DEFAULT_ALIGNMENT): List<String> {
        val misaligned = mutableListOf<String>()
        java.io.RandomAccessFile(file, "r").use { raf ->
            ZipFile(file).use { zip ->
                val offsets = LocalHeaderScanner.dataOffsets(raf)
                for (entry in zip.entries()) {
                    if (entry.method != ZipEntry.STORED) continue
                    val offset = offsets[entry.name] ?: continue
                    if (offset % alignment != 0L) misaligned += "${entry.name} @ $offset"
                }
            }
        }
        return misaligned
    }

    private fun alignmentExtra(totalLength: Int, alignment: Int): ByteArray {
        // Extensible data field 0xD935 (Android zipalign): header id, size, alignment (u16), zero padding.
        val bytes = ByteArray(totalLength)
        bytes[0] = (ALIGNMENT_EXTRA_ID and 0xFF).toByte()
        bytes[1] = (ALIGNMENT_EXTRA_ID shr 8 and 0xFF).toByte()
        val dataSize = totalLength - 4
        bytes[2] = (dataSize and 0xFF).toByte()
        bytes[3] = (dataSize shr 8 and 0xFF).toByte()
        bytes[4] = (alignment and 0xFF).toByte()
        bytes[5] = (alignment shr 8 and 0xFF).toByte()
        return bytes
    }

    private fun stripAlignmentExtra(extra: ByteArray?): ByteArray {
        if (extra == null || extra.isEmpty()) return ByteArray(0)
        val result = java.io.ByteArrayOutputStream()
        var i = 0
        while (i + 4 <= extra.size) {
            val id = (extra[i].toInt() and 0xFF) or ((extra[i + 1].toInt() and 0xFF) shl 8)
            val size = (extra[i + 2].toInt() and 0xFF) or ((extra[i + 3].toInt() and 0xFF) shl 8)
            val end = minOf(extra.size, i + 4 + size)
            if (id != ALIGNMENT_EXTRA_ID && id != 0) result.write(extra, i, end - i)
            i = end
        }
        return result.toByteArray()
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    companion object {
        const val DEFAULT_ALIGNMENT = 4
        const val PAGE_ALIGNMENT_16K = 16384
        private const val LOCAL_HEADER_SIZE = 30
        private const val ALIGNMENT_EXTRA_ID = 0xD935
        private const val ALIGNMENT_EXTRA_HEADER = 6

        /** 1981-01-01 00:00 local time keeps DOS timestamps valid and avoids extended-time extras. */
        private val FIXED_TIMESTAMP: Long = java.util.GregorianCalendar(1981, 0, 1, 0, 0, 0).timeInMillis
    }
}

/**
 * Computes each entry's data offset from the central directory (which always carries the real
 * local-header offsets and sizes, unlike local headers written with data descriptors).
 */
internal object LocalHeaderScanner {
    fun dataOffsets(raf: java.io.RandomAccessFile): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val length = raf.length()
        if (length < EOCD_SIZE) return result
        // Locate the End Of Central Directory record (it may be followed by a comment of up to 64 KiB).
        val searchLen = minOf(length, (EOCD_SIZE + 0xFFFF).toLong()).toInt()
        val tail = ByteArray(searchLen)
        raf.seek(length - searchLen)
        raf.readFully(tail)
        var eocd = -1
        var i = searchLen - EOCD_SIZE
        while (i >= 0) {
            if (le32(tail, i) == 0x06054b50L) { eocd = i; break }
            i--
        }
        if (eocd < 0) return result
        val entryCount = le16(tail, eocd + 10)
        val cdSize = le32(tail, eocd + 12)
        val cdOffset = le32(tail, eocd + 16)
        val cd = ByteArray(cdSize.toInt())
        raf.seek(cdOffset)
        raf.readFully(cd)
        val local = ByteArray(LOCAL_HEADER_SIZE)
        var p = 0
        repeat(entryCount) {
            if (p + 46 > cd.size || le32(cd, p) != 0x02014b50L) return result
            val nameLen = le16(cd, p + 28)
            val extraLen = le16(cd, p + 30)
            val commentLen = le16(cd, p + 32)
            val localOffset = le32(cd, p + 42)
            val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
            raf.seek(localOffset)
            raf.readFully(local)
            if (le32(local, 0) == 0x04034b50L) {
                result[name] = localOffset + LOCAL_HEADER_SIZE + le16(local, 26) + le16(local, 28)
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return result
    }

    private const val EOCD_SIZE = 22
    private const val LOCAL_HEADER_SIZE = 30

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        ((b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24))
}
