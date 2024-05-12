package dev.benndorf.minisniffer.protocol

class BitReader(private val data: ByteArray) {
    private var byteIndex = 0
    private var bitIndex = 0

    fun readBits(count: Int): Int {
        var value = 0
        for (i in 0 until count) {
            val byte = data[byteIndex].toInt()
            val bit = (byte shr (7 - bitIndex)) and 1
            value = (value shl 1) or bit

            if (++bitIndex == 8) {
                bitIndex = 0
                byteIndex++
            }
        }
        return value
    }
}
