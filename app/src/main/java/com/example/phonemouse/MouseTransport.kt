package com.example.phonemouse

/** Transport abstraction so Bluetooth and Wi-Fi are independent. */
interface MouseTransport {
    val isConnected: Boolean
    /** Returns false if the report could not be sent. */
    fun sendMove(dx: Int, dy: Int): Boolean
    fun sendScroll(steps: Int): Boolean
    fun click(button: Int, count: Int = 1): Boolean
    fun close()
}
