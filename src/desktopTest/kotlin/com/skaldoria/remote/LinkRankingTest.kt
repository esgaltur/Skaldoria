package com.skaldoria.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUD-09 / LNK-1 / LNK-2: which address the pairing QR advertises.
 *
 * See [ADR-005](../../../../../../docs/adr/005-companion-link-establishment.md). The companion
 * assumes a shared LAN, which fails under guest-wifi client isolation, on separate VLANs, or
 * where there is no usable network at all. The remedies — a laptop hotspot, USB tethering,
 * Bluetooth PAN — all produce an ordinary IP interface that the existing server already serves
 * on, because it binds the wildcard address.
 *
 * Two defects in the ranking heuristic blocked every one of them, and both are guarded here.
 */
class LinkRankingTest {

    @Test
    fun `LNK-1 a hotspot outranks a routed adapter the phone cannot reach`() {
        // The laptop hosts the hotspot, so its own ethernet still holds the default route.
        // Ranking on isRouted alone advertised that ethernet address — which the phone,
        // now attached to the hotspot, has no route to.
        val ranked = LinkRanking.rank(
            listOf(
                LinkRanking.Candidate("192.168.1.20", "Ethernet", isRouted = true),
                LinkRanking.Candidate("192.168.137.1", "Microsoft Wi-Fi Direct Virtual Adapter", isRouted = false)
            )
        )

        assertEquals(
            "192.168.137.1",
            ranked.first().address,
            "a deliberately created direct link must win over an unreachable routed one"
        )
    }

    @Test
    fun `LNK-2 a bluetooth PAN adapter carrying an address is offered, not denylisted`() {
        // "bluetooth" sits in VIRTUAL_ADAPTER_HINTS, so a PAN link — which carries IP and is
        // exactly how a phone tethers without wifi — was classified as a hypervisor artefact
        // and sorted last.
        val kind = LinkRanking.classify("Bluetooth Device (Personal Area Network)", isRouted = false)

        assertEquals(LinkKind.BLUETOOTH_PAN, kind)
        assertTrue(kind != LinkKind.VIRTUAL, "a PAN link is a usable transport, not a virtual adapter")
    }

    @Test
    fun `hypervisor adapters are still recognised as virtual`() {
        // The original defect this heuristic fixed — advertising a VirtualBox host-only
        // address no phone could reach — must not regress.
        for (name in listOf(
            "VirtualBox Host-Only Ethernet Adapter",
            "VMware Network Adapter VMnet8",
            "vEthernet (Default Switch)",
            "Docker Desktop Bridge"
        )) {
            assertEquals(LinkKind.VIRTUAL, LinkRanking.classify(name, isRouted = false), name)
        }
    }

    @Test
    fun `usb tethering adapters are recognised as direct links`() {
        for (name in listOf(
            "Remote NDIS based Internet Sharing Device",
            "Apple Mobile Device Ethernet"
        )) {
            assertEquals(LinkKind.TETHER, LinkRanking.classify(name, isRouted = false), name)
        }
    }

    @Test
    fun `an ordinary routed wifi adapter is still preferred over an unrouted one`() {
        // The common case must not regress: with no direct link present, the adapter holding
        // the default route is still the one a phone on the same wifi can reach.
        val ranked = LinkRanking.rank(
            listOf(
                LinkRanking.Candidate("10.0.0.5", "Ethernet 2", isRouted = false),
                LinkRanking.Candidate("192.168.1.20", "Wi-Fi", isRouted = true)
            )
        )

        assertEquals("192.168.1.20", ranked.first().address)
    }

    @Test
    fun `virtual adapters rank last but are not hidden`() {
        val ranked = LinkRanking.rank(
            listOf(
                LinkRanking.Candidate("192.168.56.1", "VirtualBox Host-Only Ethernet Adapter", isRouted = false),
                LinkRanking.Candidate("192.168.1.20", "Wi-Fi", isRouted = true)
            )
        )

        assertEquals(2, ranked.size, "a virtual adapter stays available as a last resort")
        assertEquals("192.168.56.1", ranked.last().address)
    }

    @Test
    fun `ranking is stable for candidates of the same kind`() {
        val ranked = LinkRanking.rank(
            listOf(
                LinkRanking.Candidate("192.168.1.30", "Wi-Fi", isRouted = false),
                LinkRanking.Candidate("192.168.1.20", "Ethernet", isRouted = false)
            )
        )
        assertEquals(listOf("192.168.1.20", "192.168.1.30"), ranked.map { it.address })
    }

    @Test
    fun `an empty candidate list ranks to nothing rather than throwing`() {
        assertEquals(emptyList(), LinkRanking.rank(emptyList()))
    }

    @Test
    fun `every link kind has a defined priority`() {
        // An unranked kind would sort arbitrarily, which is how a wrong address gets
        // advertised in the first place.
        val priorities = LinkKind.entries.map { LinkRanking.priorityOf(it) }
        assertEquals(LinkKind.entries.size, priorities.size)
        assertTrue(priorities.all { it >= 0 })
    }
}
