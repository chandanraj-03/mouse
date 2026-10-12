package com.example.phonemouse

import android.content.Context

/** Owns both transports and routes input to whichever is connected (Bluetooth preferred). */
class ConnectionManager(context: Context, private val listener: (source: String, ConnectionState) -> Unit) {
    val bluetooth = BluetoothMouseController(context) { listener("Bluetooth", it) }
    val wifi = WifiMouseController { listener("Wi-Fi", it) }

    private val active: MouseTransport?
        get() = when {
            bluetooth.isConnected -> bluetooth
            wifi.isConnected -> wifi
            else -> null
        }

    val hasActive: Boolean get() = active != null
    fun move(dx: Int, dy: Int) = active?.sendMove(dx, dy) ?: false
    fun scroll(steps: Int) = active?.sendScroll(steps) ?: false
    fun click(button: Int, count: Int = 1) = active?.click(button, count) ?: false

    fun close() { bluetooth.close(); wifi.close() }
}
