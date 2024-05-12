import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.util.UUID
import kotlin.experimental.and

suspend fun ByteWriteChannel.writeVarInt(value: Int): Unit = writeVarInt(value) { writeByte(it) }
fun BytePacketBuilder.writeVarInt(value: Int): Unit = writeVarInt(value) { writeByte(it) }

suspend fun ByteReadChannel.readVarInt(): Int = readVarInt { readByte() }
fun ByteReadPacket.readVarInt(): Int = readVarInt { readByte() }

internal inline fun writeVarInt(value: Int, writeByte: (Byte) -> Unit) {
    var _value = value

    while (true) {
        if ((_value and 0xFFFFFF80.toInt()) == 0) {
            writeByte(_value.toByte())
            return
        }

        writeByte(((_value and 0x7F) or 0x80).toByte())
        _value = _value ushr 7
    }
}

internal inline fun readVarInt(readByte: () -> Byte): Int {
    var offset = 0
    var value = 0L
    var byte: Byte

    do {
        if (offset == 35) error("VarInt too long")

        byte = readByte()
        value = value or ((byte.toLong() and 0x7FL) shl offset)

        offset += 7
    } while ((byte and 0x80.toByte()) != 0.toByte())

    return value.toInt()
}

fun BytePacketBuilder.writeUuid(uuid: UUID) {
    writeLong(uuid.mostSignificantBits)
    writeLong(uuid.leastSignificantBits)
}

fun ByteReadPacket.readUuid(): UUID {
    val mostSignificantBits = readLong()
    val leastSignificantBits = readLong()

    return UUID(mostSignificantBits, leastSignificantBits)
}

fun BytePacketBuilder.writeString(string: String) {
    val bytes = string.toByteArray()
    writeVarInt(bytes.size)
    writeFully(bytes)
}

fun ByteReadPacket.readString(max: Int = -1): String {
    val size = readVarInt()
    return when {
        max == -1 -> readBytes(size).decodeToString()
        size > max -> error("The string size is larger than the supported: $max")
        else -> readBytes(size).decodeToString()
    }
}

suspend fun ByteWriteChannel.sendMinecraftPacket(packet: Packet) {
    if (packet.data == null) throw IllegalStateException("Packet data is null")
    val bytes = packet.data.readBytes()
    writeVarInt(bytes.size)
    writeAvailable(bytes)
    flush()
}

suspend fun ByteReadChannel.readMinecraftPacket(
    protocolData: () -> ProtocolData.Side,
    debugPrefix: () -> String
): Packet {
    val size = readVarInt()
    val packet = readPacket(size)
    val data = packet.copy()

    val id = packet.readVarInt()
    var prefix = debugPrefix()
    val packetDefinition =
        protocolData().packetsById[id] ?: throw IllegalStateException("$prefix Unknown packet id $id")
    prefix = "$prefix[${packetDefinition.name}]"
    // println("$prefix start reading packet ${packetDefinition.fieldDefinitions}")
    val fields = packetDefinition.fieldDefinitions.entries.associate { (name, type) ->
        name to packet.readMinecraftType(name, type) { prefix }
    }

    val parsedPacket = Packet(id, packetDefinition.name, packetDefinition.fieldDefinitions, fields, data)
    println("$prefix got packet $parsedPacket")
    if (packet.remaining > 0) throw IllegalStateException("$prefix packet has remaining data: ${packet.remaining}");
    return parsedPacket
}

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteReadPacket.readMinecraftType(name: String, type: Any, debugPrefix: () -> String): Any {
    val prefix = debugPrefix()
    // println("$prefix reading field $name of type $type")
    return when (type) {
        "string" -> readString()
        "varint" -> readVarInt()
        "bool" -> readByte() == 1.toByte()
        "i8" -> readByte()
        "u8" -> readUByte()
        "u16" -> readUShort()
        "i32" -> readInt()
        "i64" -> readLong()
        "UUID" -> readUuid()
        "buffer" -> readBytes(readVarInt())
        "restBuffer" -> readBytes(remaining.toInt())
        is ArrayField -> {
            val count = readMinecraftType(name, type.countType, debugPrefix) as Int
            val array = mutableListOf<Any>()
            repeat(count) {
                array.add(readMinecraftType(name, type.type, debugPrefix))
            }
            array
        }

        is ContainerField ->
            type.fields.entries.associate { (key, type) ->
                key to readMinecraftType(key, type, debugPrefix)
            }
        "tags" -> {
            val length = readVarInt()
            val tags = mutableMapOf<String, List<Int>>()
            repeat(length) {
                val tag = readString()
                val entries = mutableListOf<Int>()
                repeat(readVarInt()) {
                    entries.add(readVarInt())
                }
                tags[tag] = entries
            }
            tags
        }
        // todo fix codec
        "anonymousNbt" -> readBytes(remaining.toInt())
        else -> throw IllegalStateException("$prefix Unknown type $type for field $name")
    }
}
