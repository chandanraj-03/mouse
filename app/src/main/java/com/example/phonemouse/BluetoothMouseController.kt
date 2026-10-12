package com.example.phonemouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.phonemouse.ConnectionState.Kind
import java.util.concurrent.Executors

/**
 * Bluetooth HID mouse via the public BluetoothHidDevice API (API 28+).
 * Callers must hold BLUETOOTH_CONNECT on Android 12+ before calling start()/connect().
 * State callbacks arrive on a background thread.
 */
@SuppressLint("MissingPermission")
class BluetoothMouseController(
    private val context: Context,
    private val onState: (ConnectionState) -> Unit
) : MouseTransport {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var registered = false
    @Volatile private var connected = false
    @Volatile private var sendErrorReported = false

    override val isConnected: Boolean get() = connected && host != null
    val isRegistered: Boolean get() = registered

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = proxy as BluetoothHidDevice
            registerApp()
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null; registered = false; connected = false; host = null
            onState(ConnectionState.of(Kind.IDLE, "HID service disconnected"))
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothMouseController.registered = registered
            onState(
                if (registered) ConnectionState.of(Kind.REGISTERED, "Pair from the other phone's Bluetooth settings")
                else ConnectionState.of(Kind.IDLE, "HID app unregistered")
            )
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> onState(ConnectionState.of(Kind.CONNECTING))
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device; connected = true; sendErrorReported = false
                    onState(ConnectionState.of(Kind.CONNECTED))
                }
                BluetoothProfile.STATE_DISCONNECTING -> onState(ConnectionState.of(Kind.DISCONNECTING))
                BluetoothProfile.STATE_DISCONNECTED -> {
                    host = null; connected = false
                    onState(ConnectionState.of(if (registered) Kind.REGISTERED else Kind.IDLE, "Disconnected"))
                }
            }
        }
    }

    fun start() {
        val a = adapter
        if (a == null) { onState(ConnectionState.of(Kind.UNSUPPORTED, "No Bluetooth adapter")); return }
        if (!a.isEnabled) { onState(ConnectionState.of(Kind.ERROR, "Bluetooth is off")); return }
        if (hid != null) { registerApp(); return }
        onState(ConnectionState.of(Kind.REGISTERING))
        val ok = try {
            a.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            onState(ConnectionState.of(Kind.ERROR, "Bluetooth permission denied")); return
        }
        if (!ok) onState(ConnectionState.of(Kind.UNSUPPORTED, "HID Device profile not available on this phone"))
    }

    private fun registerApp() {
        val h = hid ?: return
        if (registered) { onState(ConnectionState.of(Kind.REGISTERED)); return }
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Phone Mouse", "Touchpad", "PhoneMouseController",
            BluetoothHidDevice.SUBCLASS1_MOUSE, MouseReport.DESCRIPTOR
        )
        val ok = try { h.registerApp(sdp, null, null, executor, hidCallback) }
        catch (e: SecurityException) { false }
        if (!ok) onState(ConnectionState.of(Kind.ERROR, "registerApp failed (another HID app may hold the profile, or firmware blocks it)"))
    }

    fun pairedDevices(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList().orEmpty()
    } catch (e: SecurityException) { emptyList() }

    fun connect(device: BluetoothDevice) {
        val h = hid
        if (h == null || !registered) { onState(ConnectionState.of(Kind.ERROR, "Register HID first")); return }
        onState(ConnectionState.of(Kind.CONNECTING))
        val ok = try { h.connect(device) } catch (e: SecurityException) { false }
        if (!ok) onState(ConnectionState.of(Kind.ERROR, "connect() was rejected"))
    }

    fun disconnect() {
        val h = hid; val d = host
        if (h == null || d == null) return
        val ok = try { h.disconnect(d) } catch (e: SecurityException) { false }
        if (!ok) onState(ConnectionState.of(Kind.ERROR, "disconnect() failed"))
    }

    private fun send(buttons: Int, dx: Int, dy: Int, wheel: Int): Boolean {
        val h = hid; val d = host
        if (h == null || d == null || !connected) return false
        val ok = try { h.sendReport(d, 0, MouseReport.build(buttons, dx, dy, wheel)) }
        catch (e: SecurityException) { false }
        if (!ok && !sendErrorReported) {
            sendErrorReported = true
            Log.w(TAG, "sendReport returned false")
            onState(ConnectionState.of(Kind.ERROR, "sendReport failed"))
        }
        return ok
    }

    override fun sendMove(dx: Int, dy: Int): Boolean {
        var ok = true
        for ((x, y) in MouseReport.splitMove(dx, dy)) ok = send(0, x, y, 0) && ok
        return ok
    }

    override fun sendScroll(steps: Int): Boolean {
        var rest = steps; var ok = true
        while (rest != 0) {
            val s = MouseReport.clamp(rest); ok = send(0, 0, 0, s) && ok; rest -= s
        }
        return ok
    }

    override fun click(button: Int, count: Int): Boolean {
        if (!isConnected) return false
        for (i in 0 until count) {
            handler.postDelayed({ send(button, 0, 0, 0) }, i * 120L)
            handler.postDelayed({ send(0, 0, 0, 0) }, i * 120L + 40L)
        }
        return true
    }

    override fun close() {
        handler.removeCallbacksAndMessages(null)
        try { disconnect() } catch (_: Exception) {}
        try { hid?.unregisterApp() } catch (_: Exception) {}
        try { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) } catch (_: Exception) {}
        hid = null; registered = false; connected = false; host = null
        executor.shutdown()
    }

    private companion object { const val TAG = "BtMouse" }
}
