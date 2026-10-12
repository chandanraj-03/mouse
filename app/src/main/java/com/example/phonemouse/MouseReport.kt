package com.example.phonemouse

/** HID boot-compatible-style mouse report: [buttons, dx, dy, wheel], no report ID. */
object MouseReport {
    const val LEFT = 0x01
    const val RIGHT = 0x02
    const val MIDDLE = 0x04

    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1, 0x01,       // Collection (Application)
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Buttons)
        0x19, 0x01,       //     Usage Min (1)
        0x29, 0x03,       //     Usage Max (3)
        0x15, 0x00,       //     Logical Min (0)
        0x25, 0x01,       //     Logical Max (1)
        0x95, 0x03,       //     Report Count (3)
        0x75, 0x01,       //     Report Size (1)
        0x81, 0x02,       //     Input (Data,Var,Abs)
        0x95, 0x01,       //     Report Count (1)
        0x75, 0x05,       //     Report Size (5) padding
        0x81, 0x03,       //     Input (Const,Var,Abs)
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x38,       //     Usage (Wheel)
        0x15, 0x81,       //     Logical Min (-127)
        0x25, 0x7F,       //     Logical Max (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x06,       //     Input (Data,Var,Rel)
        0xC0,             //   End Collection
        0xC0              // End Collection
    ).map { it.toByte() }.toByteArray()

    fun clamp(v: Int): Int = v.coerceIn(-127, 127)

    fun build(buttons: Int, dx: Int, dy: Int, wheel: Int): ByteArray = byteArrayOf(
        (buttons and 0x07).toByte(),
        clamp(dx).toByte(),
        clamp(dy).toByte(),
        clamp(wheel).toByte()
    )

    /** Splits a large movement into several valid reports. */
    fun splitMove(dx: Int, dy: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var rx = dx; var ry = dy
        while (rx != 0 || ry != 0) {
            val sx = clamp(rx); val sy = clamp(ry)
            out.add(sx to sy)
            rx -= sx; ry -= sy
        }
        return out
    }
}
