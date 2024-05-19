import dev.benndorf.minisniffer.protocol.BitReader
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.*
import org.jglrxavpok.hephaistos.nbt.*
import java.util.*
import kotlin.experimental.and

suspend fun ByteWriteChannel.writeVarInt(value: Int): Unit = writeVarInt(value) { writeByte(it) }
fun BytePacketBuilder.writeVarInt(value: Int): Unit = writeVarInt(value) { writeByte(it) }

suspend fun ByteReadChannel.readVarInt(): Int = readVar(32) { readByte() }
fun ByteReadPacket.readVarInt(): Int = readVar(32) { readByte() }

suspend fun ByteReadChannel.readVarLong(): Int = readVar(64) { readByte() }
fun ByteReadPacket.readVarLong(): Int = readVar(64) { readByte() }

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

internal inline fun readVar(maxSize: Int, readByte: () -> Byte): Int {
    var offset = 0
    var value = 0L
    var byte: Byte

    do {
        if (offset >= maxSize) error("VarInt/Long too long")

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

fun ByteReadPacket.readNbt(): NBT {
    val nbtReader = NBTReader(inputStream(), CompressedProcesser.NONE)
    val tagId = readByte()
    if (tagId.toInt() == NBTType.TAG_End.ordinal) return NBTEnd
    return nbtReader.readRaw(tagId.toInt())
}

@OptIn(ExperimentalStdlibApi::class)
val format = HexFormat {
    bytes.bytesPerGroup = 1
    bytes.bytePrefix = "0x"
    bytes.groupSeparator = " "
}
const val hexLogging = false

@OptIn(ExperimentalStdlibApi::class)
suspend fun ByteWriteChannel.sendMinecraftPacket(packet: Packet) {
    if (packet.data == null) throw IllegalStateException("Packet data is null")

    val bytes = packet.data.readBytes()
    writeVarInt(bytes.size)
    writeAvailable(bytes)
    flush()
//    println("flushed packet ${packet.name} with size ${bytes.size}")
    if (hexLogging) {
        println(bytes.toHexString(format))
    }
}

@OptIn(ExperimentalStdlibApi::class)
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
    prefix = "$prefix[${packetDefinition.name.padStart(25)}]"
//    println("$prefix start reading packet ${packetDefinition.fieldDefinitions}")
    if (ignoredForParsing.contains(packetDefinition.name)) {
        return Packet(id, packetDefinition.name, packetDefinition.fieldDefinitions, mapOf(), data)
    }
    val fields = mutableMapOf<String, Any?>()
    packetDefinition.fieldDefinitions.entries.forEach { (name, type) ->
        fields[name] = packet.readMinecraftType(name, type, fields) { prefix }
    }

    val parsedPacket = Packet(id, packetDefinition.name, packetDefinition.fieldDefinitions, fields, data)
    println("$prefix parsed packet $fields")
    if (hexLogging) {
        println(data.copy().readBytes().toHexString(format))
    }
    if (packet.remaining > 0) throw IllegalStateException("$prefix packet has remaining data: ${packet.remaining}");
    return parsedPacket
}

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteReadPacket.readMinecraftType(
    name: String,
    type: Field,
    fields: MutableMap<String, Any?>,
    debugPrefix: () -> String
): Any? {
    val prefix = debugPrefix()
    // println("$prefix reading field $name of type $type")
    return when (type) {
        is NativeField -> when (type.name) {
            "string" -> readString()
            "varint" -> readVarInt()
            "varlong" -> readVarLong()
            "bool" -> readByte() == 1.toByte()
            "u8" -> readUByte()
            "u16" -> readUShort()
            "u32" -> readUInt()
            "u64" -> readULong()
            "i8" -> readByte()
            "i16" -> readShort()
            "i32" -> readInt()
            "i64" -> readLong()
            "f32" -> readFloat()
            "f64" -> readDouble()
            "UUID" -> readUuid()
            "restBuffer" -> readBytes()
            "anonymousNbt" -> readNbt()
            else -> throw IllegalStateException("$prefix Unknown native type ${type.name} for field $name")
        }

        is VoidField -> Unit
        is BufferField -> readBytes(readMinecraftType("$name.size", type.countType, fields, debugPrefix) as Int)
        is CountedBufferField -> readBytes(type.count)

        is ArrayField -> {
            val count = readMinecraftType("$name.size", type.countType, fields, debugPrefix) as Int
            val array = mutableListOf<Any?>()
            repeat(count) {
                array.add(readMinecraftType("$name.entry", type.type, fields, debugPrefix))
            }
            array
        }

        is ContainerField -> type.fields.entries.associate { (key, type) ->
            key to readMinecraftType(key, type, fields, debugPrefix)
        }

        is OptionalField -> if (readByte() == 1.toByte()) {
            Optional.ofNullable(readMinecraftType(name, type.type, fields, debugPrefix))
        } else {
            Optional.empty()
        }

        is BitSetField -> {
            val reader = BitReader(readBytes(type.numBytes))
            // TODO signed vs unsigned
            type.entries.associate { it.name to reader.readBits(it.bits) }
        }

        is MapperField -> {
            val value = readMinecraftType("$name.value", type.type, fields, debugPrefix)
            type.mappings[value.toString()]
        }

        is SwitchField -> {
            val path = type.compareTo.replaceFirst("../", "")
            if (path.contains("/")) {
                throw IllegalStateException("$prefix complex switch $type for field $name ($path)")
            }
            val value = fields[path]
            val field = type.fields[value.toString()] ?: type.default
            readMinecraftType("$name.$value", field, fields, debugPrefix)
        }

        else -> throw IllegalStateException("$prefix Unknown type $type for field $name")
    }
}
