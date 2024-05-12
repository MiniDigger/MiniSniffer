package dev.benndorf.minisniffer.mcdata

import ArrayField
import BitSetEntry
import BitSetField
import BufferField
import ContainerField
import CountedBufferField
import DataPaths
import OptionalField
import Packet
import ProtocolData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

import java.nio.file.Path
import java.util.LinkedHashMap

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

@OptIn(ExperimentalSerializationApi::class)
fun parseProtocolData(version: String): ProtocolData? {
    val rootPath = Path.of("./minecraft-data/data")
    val dataPaths =
        json.decodeFromStream<DataPaths>(rootPath.resolve("dataPaths.json").toFile().inputStream())

    val protocolPath = dataPaths.pc[version]?.protocol?.let { rootPath.resolve(it).resolve("protocol.json") }
    val protocolData = protocolPath?.let { json.decodeFromStream<ProtocolData>(it.toFile().inputStream()) }


    protocolData?.let {
        parsePhase(it.handshaking)
        parsePhase(it.status)
        parsePhase(it.login)
        parsePhase(it.configuration)
        parsePhase(it.play)
    }

    return protocolData
}

fun parsePhase(phase: ProtocolData.Phase) {
    parseSide(phase.toClient)
    parseSide(phase.toServer)
}

fun parseSide(side: ProtocolData.Side) {
    val meta = (side.types["packet"]?.get(1) ?: return) as JsonArray
    val idMapping = (((meta[0] as JsonObject)["type"] as JsonArray)[1] as JsonObject)["mappings"] as JsonObject
    val nameMapping = (((meta[1] as JsonObject)["type"] as JsonArray)[1] as JsonObject)["fields"] as JsonObject

    side.packets = idMapping.map { (k, v) ->
        Packet(
            k.replace("0x", "").toInt(16),
            v.jsonPrimitive.content,
            parseFields(nameMapping[v.jsonPrimitive.content]!!.jsonPrimitive.content, side)
        )
    }
    side.packetsById = side.packets.associateBy { it.id }
    side.packetsByName = side.packets.associateBy { it.name }
    side.types.clear()
}

fun parseFields(name: String, side: ProtocolData.Side): LinkedHashMap<String, Any> {
    if (name == "void") return linkedMapOf()
    val fields = side.types[name]?.get(1) as JsonArray
    val result = linkedMapOf<String, Any>()
    fields.forEach { field ->
        val fieldObj = field as JsonObject
        val fieldName = fieldObj["name"]?.jsonPrimitive?.content ?: return@forEach
        result[fieldName] = parseType(fieldObj["type"], fieldName)
    }

    return result
}

fun parseType(fieldType: JsonElement?, fieldName: String): Any {
    return when (fieldType) {
        // TODO look up if its native for composite!
        is JsonPrimitive -> fieldType.jsonPrimitive.content

        is JsonArray -> when (val complexType = fieldType[0].jsonPrimitive.content) {
            "array" -> ArrayField(
                parseType(fieldType[1].jsonObject["type"], fieldName),
                fieldType[1].jsonObject["countType"]!!.jsonPrimitive.content
            )

            "container" -> ContainerField(
                fieldType[1].jsonArray.associate {
                    val name = it.jsonObject["name"]!!.jsonPrimitive.content
                    name to parseType(it.jsonObject["type"], name)
                }
            )
            // TODO implement switch
            "switch" -> complexType
            "option" -> OptionalField(parseType(fieldType[1], fieldName))
            "buffer" -> {
                val countType = fieldType[1].jsonObject["countType"]
                val count = fieldType[1].jsonObject["count"]
                if (countType != null) {
                    BufferField(countType.jsonPrimitive.content)
                } else if (count != null) {
                    CountedBufferField(count.jsonPrimitive.int)
                } else {
                    throw IllegalArgumentException("buffer field $fieldName is invalid; $fieldType")
                }
            }
            // TODO implement particleData
            "particleData" -> complexType
            "bitfield" -> BitSetField(fieldType[1].jsonArray.map {
                val entry = it.jsonObject
                BitSetEntry(
                    entry["name"]!!.jsonPrimitive.content,
                    entry["size"]!!.jsonPrimitive.int,
                    entry["signed"]!!.jsonPrimitive.boolean
                )
            })
            // TODO implement topBitSetTerminatedArray
            "topBitSetTerminatedArray" -> complexType
            else -> {
                println("unknown complex type $complexType for field $fieldName")
                complexType
            }
        }

        else -> throw IllegalArgumentException("unknown type $fieldType")
    }
}


