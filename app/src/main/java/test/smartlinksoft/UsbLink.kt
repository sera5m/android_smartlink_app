package test.smartlinksoft

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds an open Espressif USB device and talks to the first interface
 * that has a bulk IN and a bulk OUT. 0x303A:0x1001 is the ROM
 * USB-Serial/JTAG. It is not a textbook CDC device, so this does not
 * require class 0x02. It claims whatever has the two bulk endpoints.
 */
class UsbLink(
    private val device: UsbDevice,
    private val conn: UsbDeviceConnection,
) : Closeable {

    var onText: ((String) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    private var intf: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private val running = AtomicBoolean(false)
    private var reader: Thread? = null

    val label: String get() = "%04X:%04X".format(device.vendorId, device.productId)

    fun start(baud: Int = 115200): Boolean {
        val found = findBulkPair() ?: return false
        intf = found.first
        epIn = found.second
        epOut = found.third
        if (!conn.claimInterface(found.first, true)) return false
        setLineCoding(found.first.id, baud)
        running.set(true)
        reader = Thread({
            val buf = ByteArray(512)
            while (running.get()) {
                val n = conn.bulkTransfer(epIn, buf, buf.size, 200)
                if (n != null && n > 0) {
                    onText?.invoke(String(buf, 0, n, Charsets.UTF_8))
                }
            }
        }, "usb-read").also { it.isDaemon = true; it.start() }
        return true
    }

    fun write(text: String): Boolean {
        val ep = epOut ?: return false
        val bytes = text.toByteArray(Charsets.UTF_8)
        val n = conn.bulkTransfer(ep, bytes, bytes.size, 500)
        return n == bytes.size
    }

    fun write(bytes: ByteArray): Boolean {
        val ep = epOut ?: return false
        val n = conn.bulkTransfer(ep, bytes, bytes.size, 500)
        return n == bytes.size
    }

    override fun close() {
        if (!running.getAndSet(false) && reader == null) return
        reader?.join(300)
        reader = null
        intf?.let { conn.releaseInterface(it) }
        conn.close()
        onClosed?.invoke()
    }

    private fun findBulkPair(): Triple<UsbInterface, UsbEndpoint, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var inn: UsbEndpoint? = null
            var out: UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) inn = ep else out = ep
            }
            if (inn != null && out != null) return Triple(intf, inn, out)
        }
        return null
    }

    /** CDC SET_LINE_CODING. Ignored by interfaces that are not CDC. */
    private fun setLineCoding(intfId: Int, baud: Int) {
        val data = byteArrayOf(
            (baud and 0xFF).toByte(),
            ((baud shr 8) and 0xFF).toByte(),
            ((baud shr 16) and 0xFF).toByte(),
            ((baud shr 24) and 0xFF).toByte(),
            0, 0, 8,
        )
        conn.controlTransfer(0x21, 0x20, 0, intfId, data, data.size, 200)
    }
}
