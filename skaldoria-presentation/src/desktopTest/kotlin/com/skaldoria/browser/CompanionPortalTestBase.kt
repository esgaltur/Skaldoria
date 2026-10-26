package com.skaldoria.browser

import com.skaldoria.remote.RemoteCompanionServer
import com.skaldoria.state.PresentationState
import kotlin.test.AfterTest

/**
 * Serves a deck over the real [RemoteCompanionServer] for a browser test to drive.
 *
 * The server is a process-wide singleton, so it is stopped after every test — which also resets
 * the per-client rate limiter (SEC-5): every browser page reaches the server from 127.0.0.1 and
 * would otherwise share one write budget across the whole class.
 */
abstract class CompanionPortalTestBase : BrowserTestBase() {

    protected lateinit var baseUrl: String
        private set

    /** Starts the companion over [state] and returns it, ready for the portals to poll. */
    protected fun serve(state: PresentationState): PresentationState {
        RemoteCompanionServer.start(state, preferredPort = PORT)
        baseUrl = "http://127.0.0.1:${RemoteCompanionServer.currentPort}"
        return state
    }

    /** The pairing link the speaker's QR carries, pointed at loopback. */
    protected fun presenterPairingUrl(): String =
        "$baseUrl/remote?t=${RemoteCompanionServer.presenterUrl().substringAfter("?t=")}"

    protected fun audienceUrl(): String = "$baseUrl/audience"

    @AfterTest
    fun stopCompanion() {
        RemoteCompanionServer.stop()
    }

    private companion object {
        /** Clear of `RemoteCompanionServerTest`'s 18888 so a lingering socket cannot collide. */
        const val PORT = 19888
    }
}
