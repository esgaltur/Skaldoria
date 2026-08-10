package com.skaldoria.remote

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Address selection for the companion QR.
 *
 * Detection used to return the first site-local IPv4 the JVM enumerated. On a developer
 * machine that is typically a hypervisor's host-only adapter — VirtualBox `192.168.56.1`,
 * Hyper-V `172.23.x`, VMware `169.254.x` — none of which a phone on the wifi can reach, so
 * the QR pointed somewhere unreachable.
 */
class NetworkAddressTest {

    @AfterTest
    fun clearOverride() {
        RemoteCompanionServer.preferredAddress = null
    }

    @Test
    fun `candidates exclude loopback and link-local addresses`() {
        val candidates = RemoteCompanionServer.availableAddresses()

        assertTrue(candidates.none { it.address.startsWith("127.") }, "loopback must not be offered")
        assertTrue(
            candidates.none { it.address.startsWith("169.254.") },
            "link-local (APIPA) means no DHCP lease — unreachable, so never offered"
        )
    }

    @Test
    fun `the routed adapter is ranked first`() {
        val candidates = RemoteCompanionServer.availableAddresses()
        val routed = candidates.filter { it.isRouted }

        if (routed.isNotEmpty()) {
            assertEquals(
                routed.first().address,
                candidates.first().address,
                "the adapter carrying the default route must win"
            )
            assertEquals(
                candidates.first().address,
                RemoteCompanionServer.getLocalIpAddress(),
                "and it must be the address actually advertised"
            )
        }
    }

    /** A hypervisor adapter may still be listed, but never ahead of a real one. */
    @Test
    fun `virtual adapters never outrank physical ones`() {
        val candidates = RemoteCompanionServer.availableAddresses()
        val firstVirtual = candidates.indexOfFirst { it.isLikelyVirtual }
        val lastPhysical = candidates.indexOfLast { !it.isLikelyVirtual }

        if (firstVirtual >= 0 && lastPhysical >= 0) {
            assertTrue(
                lastPhysical < firstVirtual,
                "every physical adapter must sort before every virtual one: " +
                    candidates.map { "${it.address}(virtual=${it.isLikelyVirtual})" }
            )
        }
    }

    @Test
    fun `a chosen address overrides detection and reaches both pairing urls`() {
        RemoteCompanionServer.preferredAddress = "10.1.2.3"

        assertEquals("10.1.2.3", RemoteCompanionServer.getLocalIpAddress())
        assertTrue(RemoteCompanionServer.presenterUrl().startsWith("http://10.1.2.3:"))
        assertTrue(RemoteCompanionServer.audienceUrl().startsWith("http://10.1.2.3:"))
    }

    @Test
    fun `clearing the override returns to detection`() {
        RemoteCompanionServer.preferredAddress = "10.1.2.3"
        RemoteCompanionServer.preferredAddress = null

        assertFalse(RemoteCompanionServer.getLocalIpAddress() == "10.1.2.3")
    }

    @Test
    fun `a blank override is ignored rather than producing a broken url`() {
        RemoteCompanionServer.preferredAddress = "   "
        assertFalse(RemoteCompanionServer.getLocalIpAddress().isBlank())
    }
}
