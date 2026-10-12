@file:Suppress("DEPRECATION")

package com.example.phonemouse

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class MainActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var permissions: PermissionManager
    private lateinit var btStatus: TextView
    private lateinit var wifiStatus: TextView
    private lateinit var spinner: Spinner
    private lateinit var sensitivity: SeekBar
    private var devices: List<BluetoothDevice> = emptyList()
    private var remX = 0f
    private var remY = 0f
    private var pendingStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        permissions = PermissionManager(this)
        manager = ConnectionManager(this) { source, state ->
            runOnUiThread {
                if (source == "Bluetooth") btStatus.text = "Bluetooth: ${state.label}"
                else wifiStatus.text = "Wi-Fi: ${state.label}"
            }
        }
        btStatus = findViewById(R.id.btStatus)
        wifiStatus = findViewById(R.id.wifiStatus)
        spinner = findViewById(R.id.deviceSpinner)
        sensitivity = findViewById(R.id.sensitivity)

        button(R.id.btnRegister) { startHid() }
        button(R.id.btnDiscoverable) { requestDiscoverable() }
        button(R.id.btnConnect) {
            val d = devices.getOrNull(spinner.selectedItemPosition)
            if (d == null) toast("Select a paired device first") else manager.bluetooth.connect(d)
        }
        button(R.id.btnDisconnect) { manager.bluetooth.disconnect() }
        button(R.id.btnLeft) { clickOrWarn(MouseReport.LEFT, 1) }
        button(R.id.btnDouble) { clickOrWarn(MouseReport.LEFT, 2) }
        button(R.id.btnRight) { clickOrWarn(MouseReport.RIGHT, 1) }
        button(R.id.btnScrollUp) { manager.scroll(1) }
        button(R.id.btnScrollDown) { manager.scroll(-1) }
        button(R.id.btnWifiConnect) {
            val host = findViewById<EditText>(R.id.wifiHost).text.toString()
            val port = findViewById<EditText>(R.id.wifiPort).text.toString().toIntOrNull() ?: -1
            manager.wifi.connect(host, port)
        }
        button(R.id.btnWifiDisconnect) { manager.wifi.disconnect() }

        findViewById<TouchpadView>(R.id.touchpad).listener = object : TouchpadView.Listener {
            override fun onMove(dx: Float, dy: Float) {
                val k = maxOf(1, sensitivity.progress) / 10f
                remX += dx * k; remY += dy * k
                val ix = remX.toInt(); val iy = remY.toInt()
                if (ix != 0 || iy != 0) { remX -= ix; remY -= iy; manager.move(ix, iy) }
            }
            override fun onScroll(steps: Int) { manager.scroll(steps) }
            override fun onTap() { manager.click(MouseReport.LEFT) }
            override fun onTwoFingerTap() { manager.click(MouseReport.RIGHT) }
        }
        refreshDevices()
    }

    private fun button(id: Int, action: () -> Unit) =
        findViewById<Button>(id).setOnClickListener { action() }

    private fun clickOrWarn(btn: Int, n: Int) {
        if (!manager.click(btn, n)) toast("Not connected to a target")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun startHid() {
        if (!permissions.hasAll()) { pendingStart = true; permissions.request(REQ_PERM); return }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { btStatus.text = "Bluetooth: Unsupported: no adapter"; return }
        if (!adapter.isEnabled) {
            pendingStart = true
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE)
            return
        }
        pendingStart = false
        manager.bluetooth.start()
        refreshDevices()
    }

    private fun requestDiscoverable() {
        if (!permissions.hasAll()) { permissions.request(REQ_PERM); return }
        startActivityForResult(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120), REQ_DISCOVER
        )
    }

    private fun refreshDevices() {
        if (!permissions.hasAll()) return
        devices = manager.bluetooth.pairedDevices()
        val names = devices.map { it.name ?: "Unnamed device" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            if (names.isEmpty()) listOf("No paired devices") else names)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != REQ_PERM) return
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (pendingStart) startHid() else refreshDevices()
        } else {
            pendingStart = false
            btStatus.text = "Bluetooth: Error: permission denied (Nearby devices permission is required)"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ENABLE ->
                if (resultCode == RESULT_OK) startHid()
                else { pendingStart = false; btStatus.text = "Bluetooth: Error: Bluetooth not enabled" }
            REQ_DISCOVER ->
                if (resultCode == RESULT_CANCELED) toast("Discoverability was declined")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.close()
    }

    private companion object {
        const val REQ_PERM = 1; const val REQ_ENABLE = 2; const val REQ_DISCOVER = 3
    }
}
