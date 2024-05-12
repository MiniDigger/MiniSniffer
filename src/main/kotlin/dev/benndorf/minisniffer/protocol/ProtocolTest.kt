import dev.benndorf.minisniffer.protocol.Session
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

// TODO fix these, sending them causes the client to stall
val ignoredForSending = setOf("registry_data", "tags", "declare_recipes", "map_chunk")
val ignoredForParsing = setOf(
    // TODO switch
    "declare_recipes", "unlock_recipes", "player_info", "sound_effect", "advancements",
    // TODO command_node
    "declare_commands",
    // TODO entityMetadata
    "entity_metadata",
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
