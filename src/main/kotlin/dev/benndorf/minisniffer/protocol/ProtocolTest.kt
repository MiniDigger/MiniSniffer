import dev.benndorf.minisniffer.protocol.Session
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

val ignoredForParsing = setOf(
    // TODO entityMetadataLoop
    "entity_metadata",
    // TODO topBitSetTerminatedArray
    "entity_equipment",
    // TODO anonOptionalNbt
    "map_chunk",
    // TODO bug in switch
    "declare_recipes",
    // TODO nested switch
    "declare_commands", "advancements"
)

fun main(): Unit = runBlocking(SupervisorJob()) {
    val server = aSocket(ActorSelectorManager(Dispatchers.IO))
        .tcp()
        .bind("0.0.0.0", 25577)
    println("Listening on ${server.localAddress}")

    while (true) {
        val clientSocket = server.accept()
        println("Accepted connection from ${clientSocket.remoteAddress}")
        val serverSocket = aSocket(ActorSelectorManager(Dispatchers.IO))
            .tcp()
            .connect("localhost", 25565)
        println("Connected to server")
        Session(clientSocket, serverSocket)
    }
}
