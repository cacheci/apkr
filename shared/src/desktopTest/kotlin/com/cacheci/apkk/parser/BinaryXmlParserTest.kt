package com.cacheci.apkk.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryXmlParserTest {
    @Test
    fun littleEndianReadersAreUnsigned() {
        val bytes = byteArrayOf(0xff.toByte(), 0x80.toByte(), 0x34, 0x12)
        assertEquals(0x80ff, bytes.u16(0))
        assertEquals(0x123480ff, bytes.u32(0))
    }
}
