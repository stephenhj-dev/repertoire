package app.oslo.repertoire.remote

import java.io.ByteArrayOutputStream

internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Int) {
        tag(field, 0)
        varint(value.toLong() and 0xFFFFFFFFL)
    }

    fun bool(field: Int, value: Boolean) {
        int(field, if (value) 1 else 0)
    }

    fun str(field: Int, value: String) {
        bytes(field, value.toByteArray(Charsets.UTF_8))
    }

    fun bytes(field: Int, value: ByteArray) {
        tag(field, 2)
        varint(value.size.toLong())
        out.write(value)
    }

    fun msg(field: Int, block: ProtoWriter.() -> Unit) {
        val nested = ProtoWriter().apply(block).toByteArray()
        bytes(field, nested)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) {
        varint(((field shl 3) or wire).toLong())
    }

    private fun varint(value: Long) {
        var rest = value
        while (rest and 0x7FL.inv() != 0L) {
            out.write(((rest and 0x7F) or 0x80).toInt())
            rest = rest ushr 7
        }
        out.write(rest.toInt())
    }
}

internal class ProtoFields(private val items: List<Item>) {
    fun has(field: Int): Boolean = items.any { it.field == field }

    fun int(field: Int): Int? = items.firstOrNull { it.field == field && it.wire == 0 }?.varint?.toInt()

    fun bool(field: Int): Boolean? = int(field)?.let { it != 0 }

    fun text(field: Int): String? =
        items.firstOrNull { it.field == field && it.wire == 2 }?.payload?.toString(Charsets.UTF_8)

    fun payload(field: Int): ByteArray? =
        items.firstOrNull { it.field == field && it.wire == 2 }?.payload

    fun msg(field: Int): ProtoFields? = payload(field)?.let { parseFields(it) }

    data class Item(val field: Int, val wire: Int, val varint: Long, val payload: ByteArray)
}

internal fun parseFields(bytes: ByteArray): ProtoFields {
    val items = mutableListOf<ProtoFields.Item>()
    var offset = 0
    while (offset < bytes.size) {
        val (tag, tagBytes) = readVarint(bytes, offset) ?: break
        offset += tagBytes
        val field = (tag ushr 3).toInt()
        val wire = (tag and 7).toInt()
        when (wire) {
            0 -> {
                val (value, used) = readVarint(bytes, offset) ?: break
                offset += used
                items += ProtoFields.Item(field, wire, value, ByteArray(0))
            }
            2 -> {
                val (length, used) = readVarint(bytes, offset) ?: break
                offset += used
                val size = length.toInt()
                if (offset + size > bytes.size) break
                items += ProtoFields.Item(field, wire, 0, bytes.copyOfRange(offset, offset + size))
                offset += size
            }
            5 -> offset += 4
            1 -> offset += 8
            else -> break
        }
    }
    return ProtoFields(items)
}

internal fun readVarint(bytes: ByteArray, offset: Int): Pair<Long, Int>? {
    var value = 0L
    var shift = 0
    var index = offset
    while (index < bytes.size && shift < 64) {
        val bite = bytes[index].toInt() and 0xFF
        value = value or ((bite and 0x7F).toLong() shl shift)
        index++
        if (bite and 0x80 == 0) return value to (index - offset)
        shift += 7
    }
    return null
}

internal class FrameBuffer {
    private var pending = ByteArray(0)

    fun append(chunk: ByteArray, length: Int): List<ByteArray> {
        if (length <= 0) return emptyList()
        pending = pending + chunk.copyOf(length)
        val frames = mutableListOf<ByteArray>()
        while (true) {
            val header = readVarint(pending, 0) ?: return frames
            val (size, headerBytes) = header
            val total = headerBytes + size.toInt()
            if (pending.size < total) return frames
            frames += pending.copyOfRange(headerBytes, total)
            pending = pending.copyOfRange(total, pending.size)
        }
    }
}

internal fun frame(message: ByteArray): ByteArray {
    val writer = ByteArrayOutputStream()
    var rest = message.size.toLong()
    while (rest and 0x7FL.inv() != 0L) {
        writer.write(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    writer.write(rest.toInt())
    writer.write(message)
    return writer.toByteArray()
}
