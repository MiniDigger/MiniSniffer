package dev.benndorf.minisniffer.protocol

import Packet
import ProtocolState.*
import dev.benndorf.minisniffer.mcdata.parseProtocolData
import io.ktor.network.sockets.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import readMinecraftPacket
import sendMinecraftPacket
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

data class Session(val clientSocket: Socket, val serverSocket: Socket) :
    CoroutineScope by CoroutineScope(CoroutineName("session-${clientSocket.remoteAddress}")) {

    private var protocolData = parseProtocolData(765) // default to 1.20.4, will be overriden in handshake
    private var protocolVersion: Int = -1

    private var clientState = HANDSHAKING
    private var serverState = HANDSHAKING

    private val fromClient = clientSocket.openReadChannel()
    private val toClient = clientSocket.openWriteChannel()
    private val fromServer = serverSocket.openReadChannel()
    private val toServer = serverSocket.openWriteChannel()

    init {
        startServerListener()
        startClientListener()
    }

    private fun startServerListener() = launch(CoroutineName("session-${clientSocket.remoteAddress}-server")) {
        try {
            println("start reading from server")
            while (true) {
                if (protocolData == null) throw IllegalStateException("Protocol data not loaded")
                val packet = fromServer.readMinecraftPacket(
                    { protocolData!![serverState].toClient },
                    { "S -> C [${serverState.toPrettyString()}]" }
                )
                toClient.sendMinecraftPacket(packet)
                handleServerPacket(packet)
            }
        } catch (error: Throwable) {
            handleError(error, "server")
        }
    }

    private fun startClientListener() = launch(CoroutineName("session-${clientSocket.remoteAddress}-client")) {
        try {
            println("start reading from client")
            while (true) {
                if (protocolData == null) throw IllegalStateException("Protocol data not loaded")
                val packet = fromClient.readMinecraftPacket(
                    { protocolData!![clientState].toServer },
                    { "C -> S [${clientState.toPrettyString()}]" }
                )
                toServer.sendMinecraftPacket(packet)
                handleClientPacket(packet)
            }
        } catch (error: Throwable) {
            handleError(error, "client")
        }
    }

    private fun handleError(error: Throwable, side: String) = when (error) {
        is ClosedReceiveChannelException -> {
            println("Got EOF from $side")
            close()
        }
        is CancellationException -> close(error)
        is SocketException -> {
            println("Error reading from $side ${error.message}")
            close()
        }
        else -> {
            println("Error reading from $side ${error.message}")
            error.printStackTrace()
            close()
        }
    }

    private fun handleServerPacket(packet: Packet) {
        when (packet.name) {
            "finish_configuration" -> {
                serverState = PLAY
            }

            // pre config phase
            "success" -> {
                clientState = PLAY
                serverState = PLAY
            }
        }
    }

    private fun handleClientPacket(packet: Packet) {
        when (packet.name) {
            "set_protocol" -> {
                clientState = when (packet.fields["nextState"]) {
                    1 -> STATUS
                    2 -> LOGIN
                    else -> throw IllegalStateException("Unknown state ${packet.fields["nextState"]}")
                }
                protocolVersion = packet.fields["protocolVersion"] as Int
                protocolData = parseProtocolData(protocolVersion)
                serverState = clientState
            }

            "login_acknowledged" -> {
                clientState = CONFIGURATION
                serverState = CONFIGURATION
            }

            "finish_configuration" -> {
                clientState = PLAY
            }
        }
    }

    private fun close(cause: CancellationException? = null) {
        clientSocket.close()
        cancel(cause)
    }
}

