package com.skaldoria.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-19: the seam between the UI-free command registry and Compose's key types.
 *
 * `AppCommandsTest` deliberately knows nothing about Compose, so it cannot notice that a
 * declared key name has no `Key` behind it — such a binding compiles, reads correctly in the
 * registry, and simply never fires. That gap is what this covers.
 */
class KeyBindingsTest {

    @Test
    fun `every declared key name maps to a real key`() {
        val unmapped = KeyBindings.declaredKeyNames() - KeyBindings.knownKeyNames()
        assertTrue(
            unmapped.isEmpty(),
            "these bindings can never fire, because no Compose Key is mapped: $unmapped"
        )
    }

    @Test
    fun `no key is mapped that nothing binds`() {
        val unused = KeyBindings.knownKeyNames() - KeyBindings.declaredKeyNames()
        assertEquals(emptySet(), unused, "mapped but bound to no command: $unused")
    }
}
