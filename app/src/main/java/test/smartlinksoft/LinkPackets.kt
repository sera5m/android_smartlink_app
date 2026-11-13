package test.smartlinksoft

/**
 * Tiny binary packets the watch can parse later.
 *
 * Media:  4D 43 <cmd>
 *   cmd 01 play/pause  02 next  03 prev  04 vol+  05 vol-
 *
 * Sleep:  53 4C <seconds>
 *   ask the watch to stretch its heartbeat. The phone cannot
 *   unilaterally raise the BLE supervision timeout. The peripheral
 *   proposes it. This packet is the request. CONNECTION_PRIORITY_LOW_POWER
 *   is the Android-side half, applied when a GATT link exists.
 */
object LinkPackets {
    const val MEDIA_0: Byte = 0x4D
    const val MEDIA_1: Byte = 0x43
    const val SLEEP_0: Byte = 0x53
    const val SLEEP_1: Byte = 0x4C

    const val PLAY_PAUSE: Byte = 0x01
    const val NEXT: Byte = 0x02
    const val PREV: Byte = 0x03
    const val VOL_UP: Byte = 0x04
    const val VOL_DOWN: Byte = 0x05

    fun media(cmd: Byte): ByteArray = byteArrayOf(MEDIA_0, MEDIA_1, cmd)
    fun sleep(seconds: Int): ByteArray =
        byteArrayOf(SLEEP_0, SLEEP_1, seconds.coerceIn(1, 255).toByte())

    fun describe(p: ByteArray): String = when {
        p.size == 3 && p[0] == MEDIA_0 && p[1] == MEDIA_1 -> "media ${mediaName(p[2])}"
        p.size == 3 && p[0] == SLEEP_0 && p[1] == SLEEP_1 -> "sleep ${p[2].toInt() and 0xFF}s"
        else -> p.joinToString(" ") { "%02X".format(it) }
    }

    private fun mediaName(c: Byte) = when (c) {
        PLAY_PAUSE -> "play/pause"
        NEXT -> "next"
        PREV -> "prev"
        VOL_UP -> "vol+"
        VOL_DOWN -> "vol-"
        else -> "?"
    }
}
