package com.skaldoria.remote

/**
 * How a phone is attached to this machine.
 *
 * See [ADR-005](../../../../../../docs/ADR_COMPANION_LINK_ESTABLISHMENT.md): the companion
 * needs one thing, an IP route to the phone, and does not care which radio carried it. A
 * hotspot, a USB cable and a Bluetooth PAN all present as ordinary network interfaces that the
 * existing server already serves on, because it binds the wildcard address.
 */
enum class LinkKind {
    /** The laptop is hosting the network the phone joined. */
    HOTSPOT,

    /** The phone is sharing its connection over USB. */
    TETHER,

    /** The phone is sharing its connection over Bluetooth PAN — IP over Bluetooth. */
    BLUETOOTH_PAN,

    /** An ordinary adapter holding the default route. */
    ROUTED_LAN,

    /** A real adapter with an address but no default route. */
    ORDINARY,

    /** A hypervisor, tunnel or container adapter — routable for the host, not for a phone. */
    VIRTUAL
}

/**
 * Ranks the addresses the companion could advertise.
 *
 * Pure and separated from interface enumeration so it is unit-testable: `availableAddresses()`
 * reads the live machine, which no test can control, and this is exactly the logic that has
 * now advertised an unreachable address twice.
 */
object LinkRanking {

    /** One address the companion could advertise. */
    data class Candidate(
        val address: String,
        val interfaceName: String,
        val isRouted: Boolean
    )

    /**
     * Names that mark a hypervisor, tunnel or container adapter.
     *
     * `NetworkInterface.isVirtual` cannot be used: it reports whether the interface is a
     * *sub-interface*, and is false for VirtualBox, VMware and Hyper-V — which is why those
     * were being advertised.
     *
     * LNK-2: `"bluetooth"` is deliberately **not** on this list. A Bluetooth adapter that
     * carries an IPv4 address is a PAN link, which is a usable transport; denylisting it by
     * name ruled out the one Bluetooth mode that actually works here.
     */
    private val VIRTUAL_HINTS = listOf(
        "virtualbox", "vmware", "vmnet", "hyper-v", "vethernet", "docker",
        "wsl", "loopback", "tunnel", "tap-", "npcap", "vpn"
    )

    /** Names that mark a phone sharing its connection over a cable. */
    private val TETHER_HINTS = listOf(
        "remote ndis", "rndis", "apple mobile device", "usb ethernet", "usb rndis"
    )

    /** Names that mark this machine hosting the network. */
    private val HOTSPOT_HINTS = listOf(
        "wi-fi direct", "wifi direct", "hosted network", "mobile hotspot", "softap", "ap mode"
    )

    private val BLUETOOTH_HINTS = listOf("bluetooth", "pan0", "bnep")

    /**
     * Classifies an interface that already carries a usable IPv4 address.
     *
     * Order matters: the direct-link hints are tested before the virtual denylist, because
     * a Wi-Fi Direct hotspot adapter carries the word "Virtual" in its Windows display name
     * and would otherwise be discarded as a hypervisor artefact.
     */
    fun classify(interfaceName: String, isRouted: Boolean): LinkKind {
        val name = interfaceName.lowercase()
        return when {
            HOTSPOT_HINTS.any { name.contains(it) } -> LinkKind.HOTSPOT
            TETHER_HINTS.any { name.contains(it) } -> LinkKind.TETHER
            BLUETOOTH_HINTS.any { name.contains(it) } -> LinkKind.BLUETOOTH_PAN
            VIRTUAL_HINTS.any { name.contains(it) } -> LinkKind.VIRTUAL
            isRouted -> LinkKind.ROUTED_LAN
            else -> LinkKind.ORDINARY
        }
    }

    /**
     * Sort weight, lower first.
     *
     * LNK-1: a direct link outranks even the adapter holding the default route. A hotspot,
     * tether or PAN link exists only because the user deliberately created it, and whatever is
     * on the other end is reachable *by construction* — whereas the routed adapter is merely
     * the one this machine reaches the internet through, which says nothing about whether a
     * phone can reach it. Ranking on the default route alone advertised an address the phone
     * had no path to the moment a hotspot was in play.
     */
    fun priorityOf(kind: LinkKind): Int = when (kind) {
        LinkKind.HOTSPOT -> 0
        LinkKind.TETHER -> 1
        LinkKind.BLUETOOTH_PAN -> 2
        LinkKind.ROUTED_LAN -> 3
        LinkKind.ORDINARY -> 4
        LinkKind.VIRTUAL -> 5
    }

    /**
     * Best candidate first.
     *
     * A virtual adapter is ranked last rather than removed: on a machine with nothing else it
     * is the only thing to offer, and hiding it would leave the pairing dialog empty with no
     * explanation.
     */
    fun rank(candidates: List<Candidate>): List<Candidate> =
        candidates.sortedWith(
            compareBy<Candidate> { priorityOf(classify(it.interfaceName, it.isRouted)) }
                .thenBy { it.address }
        )
}
