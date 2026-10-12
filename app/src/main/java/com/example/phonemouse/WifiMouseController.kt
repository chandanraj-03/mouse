package com.example.phonemouse

import com.example.phonemouse.ConnectionState.Kind
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Sends newline-delimited JSON over TCP to an EXISTING compatible receiver.
 * Nothing on Phone B listens by default, so this mode stays unavailable until
 * the user supplies a host/port where a receiver or privileged service is running.
 *
 * Protocol (one JSON object per line):
 *   {"type":"move","dx":5,"dy":-3}
 *   {"type":"scroll","steps":-1}
 *   {"type":"click","button":"left"|"right","count":1}
 */
class WifiMouseController(
    private val onState: (ConnectionState) -> Unit,
    private val connectTimeoutMs: Int = 3000
) : MouseTransport {

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var connected = false

    override val isConnected: Boolean get() = connected

    fun connect(host: String, port: Int) {
        if (host.isBlank() || port !in 1..65535) {
            onState(ConnectionState.of(Kind.ERROR, "Enter a valid host and port")); return
        }
        onState(ConnectionState.of(Kind.CONNECTING))
        executor.execute {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host.trim(), port), connectTimeoutMs)
                s.soTimeout = connectTimeoutMs
                socket = s
                writer = s.getOutputStream().bufferedWriter()
                connected = true
                onState(ConnectionState.of(Kind.CONNECTED))
            } catch (e: Exception) {
                cleanup()
                onState(ConnectionState.of(Kind.UNSUPPORTED, "No compatible receiver reachable (${e.javaClass.simpleName})"))
            }
        }
    }

    fun disconnect() { executor.execute { cleanup(); onState(ConnectionState.of(Kind.IDLE, "Disconnected")) } }

    private fun cleanup() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null; writer = null
    }

    private fun sendLine(json: String): Boolean {
        if (!connected) return false
        executor.execute {
            try { writer?.apply { write(json); write("\n"); flush() } }
            catch (e: Exception) { cleanup(); onState(ConnectionState.of(Kind.ERROR, "Connection lost")) }
        }
        return true
    }

    override fun sendMove(dx: Int, dy: Int) = sendLine("""{"type":"move","dx":$dx,"dy":$dy}""")
    override fun sendScroll(steps: Int) = sendLine("""{"type":"scroll","steps":$steps}""")
    override fun click(button: Int, count: Int): Boolean {
        val name = if (button == MouseReport.RIGHT) "right" else "left"
        return sendLine("""{"type":"click","button":"$name","count":$count}""")
    }
    override fun close() { cleanup(); executor.shutdown() }
}
