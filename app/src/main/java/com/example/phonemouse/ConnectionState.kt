package com.example.phonemouse

data class ConnectionState(val kind: Kind, val detail: String = "") {
    enum class Kind { IDLE, UNSUPPORTED, REGISTERING, REGISTERED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

    val label: String
        get() = kind.name.lowercase().replaceFirstChar { it.uppercase() } +
                if (detail.isNotEmpty()) ": $detail" else ""

    companion object {
        fun of(kind: Kind, detail: String = "") = ConnectionState(kind, detail)
    }
}
