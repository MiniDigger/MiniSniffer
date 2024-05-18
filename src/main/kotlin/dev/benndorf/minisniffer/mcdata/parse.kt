package dev.benndorf.minisniffer.mcdata

import ArrayField
import BitSetField
import BufferField
import ContainerField
import CountedBufferField
import DataPaths
import Field
import MapperField
import NativeField
import OptionalField
import Packet
import ProtocolData
import TodoField
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
        parsePhase(it.types, it.handshaking)
        parsePhase(it.types, it.status)
        parsePhase(it.types, it.login)
        parsePhase(it.types, it.configuration)
        parsePhase(it.types, it.play)
    }

    return protocolData
}

fun parsePhase(types: Map<String, JsonElement>, phase: ProtocolData.Phase) {
    parseSide(types, phase.toClient)
    parseSide(types, phase.toServer)
}

fun parseSide(types: Map<String, JsonElement>, side: ProtocolData.Side) {
    val meta = (side.types["packet"]?.get(1) ?: return) as JsonArray
    val idMapping = (((meta[0] as JsonObject)["type"] as JsonArray)[1] as JsonObject)["mappings"] as JsonObject
    val nameMapping = (((meta[1] as JsonObject)["type"] as JsonArray)[1] as JsonObject)["fields"] as JsonObject

    side.packets = idMapping.map { (k, v) ->
        Packet(
            k.replace("0x", "").toInt(16),
            v.jsonPrimitive.content,
            parseFields(types, nameMapping[v.jsonPrimitive.content]!!.jsonPrimitive.content, side)
        )
    }
    side.packetsById = side.packets.associateBy { it.id }
    side.packetsByName = side.packets.associateBy { it.name }
    side.types.clear()
}

fun parseFields(types: Map<String, JsonElement>, name: String, side: ProtocolData.Side): LinkedHashMap<String, Field> {
    if (name == "void") return linkedMapOf()
    val fields = side.types[name]?.get(1) as JsonArray
    val result = linkedMapOf<String, Field>()
    fields.forEach { field ->
        val fieldObj = field as JsonObject
        val fieldName = fieldObj["name"]?.jsonPrimitive?.content ?: return@forEach
        result[fieldName] = parseType(types, fieldObj["type"], fieldName)
    }

    return result
}

fun parseType(types: Map<String, JsonElement>, fieldType: JsonElement?, fieldName: String): Field {
    return when (fieldType) {
        is JsonPrimitive -> {
            val typeName = fieldType.jsonPrimitive.content
            val type = types[typeName] ?: throw IllegalArgumentException("unknown type $typeName for field $fieldName")
            if (type is JsonPrimitive) {
                if (type.content == "native") NativeField(typeName)
                else {
                    throw IllegalArgumentException("unknown primitive type $typeName -> ${type.content} for field $fieldName")
                }
            } else if (type is JsonArray) {
                val complexType = type[0].jsonPrimitive.content
                if (complexType == "pstring") NativeField("string")
                else parseType(types, type, typeName)
            } else {
                throw IllegalArgumentException("unknown type $typeName -> $type for field $fieldName")
            }
        }

        is JsonArray -> when (val complexType = fieldType[0].jsonPrimitive.content) {
            "array" -> ArrayField(
                parseType(types, fieldType[1].jsonObject["type"], "$fieldName -> type"),
                parseType(types, fieldType[1].jsonObject["countType"], "$fieldName -> counter"),
            )

            "container" -> ContainerField(
                fieldType[1].jsonArray.associate {
                    val name = it.jsonObject["name"]?.jsonPrimitive?.content ?: "anon"
                    name to parseType(types, it.jsonObject["type"], "$fieldName -> $name")
                }
            )

            "switch" -> TodoField("switch", fieldName) // TODO implement switch
            "option" -> OptionalField(parseType(types, fieldType[1], "$fieldName -> option"))
            "buffer" -> {
                val countType = fieldType[1].jsonObject["countType"]
                val count = fieldType[1].jsonObject["count"]
                if (countType != null) {
                    BufferField(parseType(types, countType, "$fieldName -> counter"))
                } else if (count != null) {
                    CountedBufferField(count.jsonPrimitive.int)
                } else {
                    throw IllegalArgumentException("buffer field $fieldName is invalid; $fieldType")
                }
            }

            "mapper" -> MapperField(
                parseType(types, fieldType[1].jsonObject["type"], "$fieldName -> type"),
                fieldType[1].jsonObject["mappings"]!!.jsonObject
            )

            "particleData" -> TodoField("particleData", fieldName) // TODO implement particleData
            "bitfield" -> BitSetField(fieldType[1].jsonArray.map {
                val entry = it.jsonObject
                BitSetField.BitSetEntry(
                    entry["name"]!!.jsonPrimitive.content,
                    entry["size"]!!.jsonPrimitive.int,
                    entry["signed"]!!.jsonPrimitive.boolean
                )
            })

            "topBitSetTerminatedArray" -> TodoField(
                "topBitSetTerminatedArray",
                fieldName
            ) // TODO implement topBitSetTerminatedArray
            "entityMetadataLoop" -> TodoField("entityMetadataLoop", fieldName) // TODO implement entityMetadataLoop
            else -> throw IllegalArgumentException("unknown complex type $complexType for field $fieldName")
        }

        else -> throw IllegalArgumentException("unknown type $fieldType")
    }
}


