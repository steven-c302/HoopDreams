package com.partyos.tv.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class AdvertisedUrlTest {
    @Test fun overrideWinsWhenSet() =
        assertEquals("http://192.168.1.50:8080/j/KXQT", advertisedUrl(" 192.168.1.50:8080 ", "10.0.2.15", 8081, "KXQT"))

    @Test fun usesLiveIpAndBoundPortOtherwise() =
        assertEquals("http://192.168.1.23:8081/j/KXQT", advertisedUrl("", "192.168.1.23", 8081, "KXQT"))

    @Test fun neverAdvertisesTheWildcardOrMissingAddress() {
        assertNull(advertisedUrl(null, "0.0.0.0", 8080, "KXQT"))
        assertNull(advertisedUrl(null, null, 8080, "KXQT"))
        assertNull(advertisedUrl("   ", "", 8080, "KXQT"))
    }

    @Test fun overrideWithoutPortGetsTheBoundPort() =
        assertEquals("http://192.168.1.50:8083/j/KXQT", advertisedUrl("192.168.1.50", null, 8083, "KXQT"))

    @Test fun picksFirstUsableIpv4() {
        val addrs = listOf("fe80::1", "127.0.0.1", "192.168.4.7", "10.0.0.2").map { InetAddress.getByName(it) }
        assertEquals("192.168.4.7", pickIpv4(addrs))
        assertNull(pickIpv4(listOf(InetAddress.getByName("::1"))))
    }
}
