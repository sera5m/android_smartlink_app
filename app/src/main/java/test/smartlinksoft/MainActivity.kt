package test.smartlinksoft

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import test.smartlinksoft.ui.theme.B0
import test.smartlinksoft.ui.theme.B1
import test.smartlinksoft.ui.theme.B2
import test.smartlinksoft.ui.theme.IridA
import test.smartlinksoft.ui.theme.IridB
import test.smartlinksoft.ui.theme.Pixel
import test.smartlinksoft.ui.theme.RedOverlay
import test.smartlinksoft.ui.theme.Title
import test.smartlinksoft.ui.theme.Tm
import test.smartlinksoft.ui.theme.Tx
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import test.smartlinksoft.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LinkMode(val label: String) {
    Terminal("terminal"),
    Capture("capture"),
    Media("media"),
    Sleep("sleep"),
}

/**
 * Four modes on one USB/BT link.
 *
 * Terminal  read/write the USB serial port (0x303A:0x1001 works today).
 * Capture   same read path, appended to a text file.
 * Media     send a 3-byte media packet. Over USB now. Over BLE when the
 *           watch advertises. Also injects the matching media key locally
 *           so the phone reacts even before the watch speaks BLE.
 * Sleep     send a sleep-window packet and, if a GATT link exists, ask
 *           Android for CONNECTION_PRIORITY_LOW_POWER. The phone cannot
 *           raise the supervision timeout by itself. The watch has to.
 */
class MainActivity : ComponentActivity() {

    private lateinit var usb: UsbManager
    private var status by mutableStateOf("looking for watch...")
    private var mode by mutableStateOf(LinkMode.Terminal)
    private var log by mutableStateOf("")
    private var draft by mutableStateOf("")
    private var link: UsbLink? = null
    private var gatt: BluetoothGatt? = null
    private var capture: FileOutputStream? = null
    private var capturePath by mutableStateOf("")
    private var sleepSec by mutableStateOf(30)

    private val actionPermission = "test.smartlinksoft.USB_PERMISSION"

    private val btPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        status = if (granted.values.all { it }) "bluetooth granted" else "bluetooth denied"
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != actionPermission) return
            val device = intent.deviceExtra() ?: return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted) openDevice(device) else status = "permission denied for ${device.label()}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(UsbManager::class.java)
        ContextCompat.registerReceiver(
            this, permissionReceiver,
            IntentFilter(actionPermission), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize().background(B0)) {
                    Box(Modifier.fillMaxSize().background(RedOverlay))
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                    ) { pad ->
                        Column(
                            Modifier.padding(pad).padding(12.dp).fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "SMARTLINK",
                                color = Title,
                                fontFamily = Pixel,
                                letterSpacing = 2.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinkMode.entries.forEach { m ->
                                    FilterChip(
                                        selected = mode == m,
                                        onClick = { mode = m },
                                        label = {
                                            Text(
                                                m.label.uppercase(),
                                                fontFamily = Pixel,
                                                letterSpacing = 1.sp,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = B1,
                                            labelColor = Tm,
                                            selectedContainerColor = B2,
                                            selectedLabelColor = Title,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = mode == m,
                                            borderColor = B2,
                                            selectedBorderColor = IridA,
                                        ),
                                    )
                                }
                            }
                            Text(status, color = Tm, fontFamily = Pixel)
                            when (mode) {
                                LinkMode.Terminal -> TerminalPane()
                                LinkMode.Capture -> CapturePane()
                                LinkMode.Media -> MediaPane()
                                LinkMode.Sleep -> SleepPane()
                            }
                        }
                    }
                }
            }
        }
        intent.deviceExtra()?.let { requestAndOpen(it) } ?: scan()
    }

    // ColumnScope so Modifier.weight resolves. It is a scope function,
    // not a property of Modifier itself.
    @Composable
    private fun ColumnScope.TerminalPane() {
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                log.ifEmpty { "(no data yet)" },
                fontFamily = Pixel,
                color = Tx,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("send") },
                singleLine = true,
            )
            Button(onClick = { sendLine() }) { Text("send") }
        }
    }

    @Composable
    private fun ColumnScope.CapturePane() {
        Text(if (capturePath.isEmpty()) "not logging" else capturePath)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { startCapture() }) { Text("start") }
            Button(onClick = { stopCapture() }) { Text("stop") }
        }
        Text(
            "bytes land in the app's files dir. pull with adb or a file manager.",
            modifier = Modifier.weight(1f)
        )
    }

    @Composable
    private fun ColumnScope.MediaPane() {
        Text("sends 4D 43 <cmd> over USB. also fires the media key on this phone.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { media(LinkPackets.PREV) }) { Text("prev") }
            Button(onClick = { media(LinkPackets.PLAY_PAUSE) }) { Text("play") }
            Button(onClick = { media(LinkPackets.NEXT) }) { Text("next") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { media(LinkPackets.VOL_DOWN) }) { Text("vol-") }
            Button(onClick = { media(LinkPackets.VOL_UP) }) { Text("vol+") }
        }
        Button(onClick = { scanBle() }) { Text("scan ble") }
    }

    @Composable
    private fun ColumnScope.SleepPane() {
        Text("heartbeat window: ${sleepSec}s. phone asks for low-power priority. watch has to accept.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(4, 10, 30).forEach { s ->
                Button(onClick = { sleepSec = s }) { Text("${s}s") }
            }
        }
        Button(onClick = { sendSleep() }) { Text("send sleep window") }
        Button(onClick = { scanBle() }) { Text("scan ble") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.deviceExtra()?.let { requestAndOpen(it) }
    }

    override fun onDestroy() {
        stopCapture()
        link?.close()
        gatt?.close()
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun scan() {
        val devs = usb.deviceList.values.filter { it.vendorId == ESPRESSIF_VID }
        if (devs.isEmpty()) {
            status = "no Espressif device. plug the watch in with a data cable."
            return
        }
        requestAndOpen(devs.first())
    }

    private fun requestAndOpen(device: UsbDevice) {
        if (usb.hasPermission(device)) { openDevice(device); return }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 0, Intent(actionPermission), flags)
        status = "asking permission for ${device.label()}"
        usb.requestPermission(device, pi)
    }

    private fun openDevice(device: UsbDevice) {
        link?.close()
        val conn = usb.openDevice(device)
        if (conn == null) { status = "openDevice failed for ${device.label()}"; return }
        val u = UsbLink(device, conn)
        u.onText = { chunk -> append(chunk) }
        u.onClosed = { if (link === u) link = null }
        if (!u.start()) {
            status = "no bulk in/out on ${device.label()} (interfaces=${device.interfaceCount})"
            conn.close()
            return
        }
        link = u
        status = "open: ${u.label}  terminal ready"
    }

    private fun sendLine() {
        val line = if (draft.endsWith("\n")) draft else draft + "\n"
        val ok = link?.write(line) == true
        append(if (ok) "> $line" else "(not open)\n")
        if (ok) draft = ""
    }

    private fun media(cmd: Byte) {
        val pkt = LinkPackets.media(cmd)
        val overUsb = link?.write(pkt) == true
        injectMediaKey(cmd)
        status = "sent ${LinkPackets.describe(pkt)}  usb=$overUsb"
    }

    private fun injectMediaKey(cmd: Byte) {
        val key = when (cmd) {
            LinkPackets.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            LinkPackets.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            LinkPackets.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            LinkPackets.VOL_UP -> KeyEvent.KEYCODE_VOLUME_UP
            LinkPackets.VOL_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
            else -> return
        }
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    private fun sendSleep() {
        val pkt = LinkPackets.sleep(sleepSec)
        val overUsb = link?.write(pkt) == true
        gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        status = "sent ${LinkPackets.describe(pkt)}  usb=$overUsb  gatt=${gatt != null}"
    }

    private fun startCapture() {
        stopCapture()
        val dir = getExternalFilesDir(null) ?: filesDir
        val name = "watch_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        val f = File(dir, name)
        capture = FileOutputStream(f, true)
        capturePath = f.absolutePath
        status = "logging to $capturePath"
    }

    private fun stopCapture() {
        capture?.flush()
        capture?.close()
        capture = null
        if (capturePath.isNotEmpty()) status = "saved $capturePath"
    }

    private fun append(chunk: String) {
        log = (log + chunk).takeLast(8000)
        capture?.write(chunk.toByteArray(Charsets.UTF_8))
    }

    private fun scanBle() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.BLUETOOTH_SCAN
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (need.isNotEmpty()) { btPermission.launch(need.toTypedArray()); return }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { status = "bluetooth off"; return }
        status = "scanning for Oiria..."
        adapter.bluetoothLeScanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (!name.contains("Oiria", true) && !name.contains("ESP", true)) return
                adapter.bluetoothLeScanner?.stopScan(this)
                connectGatt(result)
            }
        })
    }

    private fun connectGatt(result: ScanResult) {
        status = "connecting ${result.device.address}"
        gatt = result.device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                this@MainActivity.status = if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
                    g.discoverServices()
                    "ble connected ${g.device.address}"
                } else "ble disconnected"
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                append(String(value, Charsets.UTF_8))
            }
        })
    }

    private fun Intent.deviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33)
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else
            @Suppress("DEPRECATION") getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private fun UsbDevice.label() = "%04X:%04X".format(vendorId, productId)

    companion object {
        const val ESPRESSIF_VID = 0x303A
    }
}
