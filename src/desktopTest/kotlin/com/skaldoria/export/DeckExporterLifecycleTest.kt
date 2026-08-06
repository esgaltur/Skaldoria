package com.skaldoria.export

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-20: the exporter's background scope must be releasable.
 *
 * `DeckExporter` held a private `CoroutineScope(Dispatchers.IO + SupervisorJob())` that
 * nothing ever cancelled, so an export still running at shutdown kept a non-daemon pool alive
 * with no way to ask it to stop. PRF-5 moved that work off the UI thread; it did not give the
 * work an owner.
 */
class DeckExporterLifecycleTest {

    @Test
    fun `the export scope is active before disposal`() {
        assertTrue(DeckExporter.isActive, "the exporter should be usable on a fresh process")
    }

    @Test
    fun `dispose cancels the export scope`() {
        DeckExporter.dispose()
        assertFalse(DeckExporter.isActive, "dispose must release the exporter's background work")
    }
}
