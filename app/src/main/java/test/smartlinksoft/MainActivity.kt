package test.smartlinksoft

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import test.smartlinksoft.ui.theme.MyApplicationTheme

/**
 * USB permission is not a normal runtime permission. There is no
 * ActivityResultContract for it. Call UsbManager.requestPermission()
 * and the system shows a dialog. The answer comes back on a broadcast.
 *
 * VID 0x303A is Espressif. 0x1001 is the ROM USB-Serial/JTAG.
 * 0x4001 is TinyUSB CDC once the firmware presents it.
 */
class MainActivity : ComponentActivity() {

    private lateinit var usb: UsbManager
    private var status by mutableStateOf("looking for watch...")

    private val actionPermission = "test.smartlinksoft.USB_PERMISSION"

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

        val filter = IntentFilter(actionPermission)
        ContextCompat.registerReceiver(
            this, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text(
                        text = status,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val attached = intent.deviceExtra()
        if (attached != null) requestAndOpen(attached) else scan()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.deviceExtra()?.let { requestAndOpen(it) }
    }

    override fun onDestroy() {
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
        if (usb.hasPermission(device)) {
            openDevice(device)
            return
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 0, Intent(actionPermission), flags)
        status = "asking permission for ${device.label()}"
        usb.requestPermission(device, pi)
    }

    private fun openDevice(device: UsbDevice) {
        val conn = usb.openDevice(device)
        if (conn == null) {
            status = "openDevice failed for ${device.label()}"
            return
        }
        var claimed = false
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == 0x02 || intf.interfaceClass == 0xFF) {
                claimed = conn.claimInterface(intf, true)
                if (claimed) break
            }
        }
        status = if (claimed)
            "open: ${device.label()}  interfaces=${device.interfaceCount}"
        else
            "opened but no CDC interface claimed: ${device.label()}"
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
