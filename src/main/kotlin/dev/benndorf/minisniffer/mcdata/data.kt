import ProtocolState.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.jglrxavpok.hephaistos.nbt.NBT
import java.util.LinkedHashMap

@Serializable
data class DataPaths(val pc: Map<String, DataPath>, val bedrock: Map<String, DataPath>)

@Serializable
data class DataPath(
    val attributes: String? = null,
    val blocks: String? = null,
    val blockCollisionShapes: String? = null,
    val biomes: String? = null,
    val effects: String? = null,
    val items: String? = null,
    val enchantments: String? = null,
    val recipes: String? = null,
    val instruments: String? = null,
    val materials: String? = null,
    val language: String? = null,
    val entities: String? = null,
    val protocol: String? = null,
    val windows: String? = null,
    val version: String? = null,
    val foods: String? = null,
    val particles: String? = null,
    val blockLoot: String? = null,
    val entityLoot: String? = null,
    val loginPacket: String? = null,
    val tints: String? = null,
    val mapIcons: String? = null,
    val sounds: String? = null,
)

enum class ProtocolState {
    HANDSHAKING,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY;

    fun toPrettyString(): String {
        return when (this) {
            HANDSHAKING -> "Hand"
            STATUS -> "Stat"
            LOGIN -> "Logn"
            CONFIGURATION -> "Conf"
            PLAY -> "Play"
        }
    }
}

@Serializable
data class ProtocolData(
    val handshaking: Phase,
    val status: Phase,
    val login: Phase,
    val configuration: Phase,
    val play: Phase,
    val types: Map<String, JsonElement>,
) {
    operator fun get(state: ProtocolState): Phase {
        return when (state) {
            HANDSHAKING -> handshaking
            STATUS -> status
            LOGIN -> login
            CONFIGURATION -> configuration
            PLAY -> play
        }
    }

    @Serializable
    data class Phase(
        val toClient: Side,
        val toServer: Side,
    ) {
        operator fun get(direction: String): Side {
            return when (direction) {
                "toClient" -> toClient
                "toServer" -> toServer
                else -> throw IllegalArgumentException("Unknown direction $direction")
            }
        }
    }

    @Serializable
    data class Side(
        val types: MutableMap<String, JsonArray>
    ) {
        @Transient
        lateinit var packets: List<Packet>
        @Transient
        lateinit var packetsById: Map<Int, Packet>
        @Transient
        lateinit var packetsByName: Map<String, Packet>
        override fun toString(): String {
            return "Side(types=$types, packets=$packets)"
        }
    }
}


data class Packet(
    val id: Int,
    val name: String,
    val fieldDefinitions: LinkedHashMap<String, Field>,
    val fields: Map<String, Any?> = LinkedHashMap(),
    val data: ByteReadPacket? = null
) {
    override fun toString(): String {
        return "Packet(id=$id, name='$name', fieldDefinitions=$fieldDefinitions, fields=${fieldsToString()}, size=${data?.remaining}"
    }

    private fun fieldsToString(): String {
        return fields.entries.joinToString(", ", "{", "}") { (name, value) ->
            when (name) {
                // tags types with all tag names
//                "tags" -> "tags=${(value as List<*>).joinToString(", ", "[", "]") {
//                    val tag = it as Map<*, *>
//                    "{tagType=${tag["tagType"]}, tags=${(tag["tags"] as Map<*, *>).keys}} "
//                }}"
                // tag types with tag count
                "tags" -> "tags=${(value as List<*>).joinToString(", ", "[", "]") {
                    val tag = it as Map<*, *>
                    "{tagType=${tag["tagType"]}, tags=${(tag["tags"] as Map<*, *>).size}}"
                }}"
                else -> "$name=$value"
            }
        }
    }
}

open class Field(val typeName: String)
data class NativeField(val name: String) : Field(name)
data class BufferField( val countType: Field) : Field("buffer")
data class CountedBufferField(val count: Int): Field("buffer")
data class ArrayField(val type: Field, val countType: Field): Field("array")
data class ContainerField(val fields: Map<String, Field>): Field("container")
data class OptionalField(val type: Field): Field("option")
data class MapperField(val type: Field, val mappings: Map<String, Any>): Field("mapper")
data class BitSetField(val entries: List<BitSetEntry>): Field("bitfield") {
    val numBytes = entries.sumOf { it.bits } / 8
    data class BitSetEntry(val name: String, val bits: Int, val signed: Boolean)
}

// TODO remove
data class TodoField(val type: String, val name: String): Field(type)

data class Position(val x: Int, val y: Int, val z: Int)
data class Slot(val item: Int, val count: Byte, val nbt: NBT)
