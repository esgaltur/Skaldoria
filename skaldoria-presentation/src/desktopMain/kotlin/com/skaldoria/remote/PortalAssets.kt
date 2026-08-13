package com.skaldoria.remote

import com.skaldoria.BuildInfo
import java.nio.charset.StandardCharsets

/**
 * The two companion web portals, loaded from resources.
 *
 * F-10: these were 375 lines of HTML, CSS and JavaScript — 34% of `RemoteCompanionServer` —
 * embedded in a Kotlin raw string. As `.html` files they get syntax highlighting, formatting
 * and linting, and `PortalAssetsTest` can assert the security properties that previously
 * relied on a comment asking readers not to "simplify" the DOM builders:
 *
 *  - **SEC-1** — no `innerHTML` anywhere; every audience-supplied value is inserted with
 *    `textContent` and every generated row binds its handler with `addEventListener`, so a
 *    quote in a question id cannot break out into markup.
 *  - **SEC-2** — only the presenter portal knows about `X-Skaldoria-Token`, and it strips the
 *    credential from the address bar so a shared screenshot cannot leak it.
 *
 * Content is read once and cached: the portals are fixed at build time, and they are fetched
 * on every pairing.
 */
object PortalAssets {

    /** Placeholder substituted at load time. Deliberately not Kotlin interpolation — the
     *  files must remain valid, previewable HTML on their own. */
    private const val VERSION_PLACEHOLDER = "{{VERSION}}"

    private val presenter: String by lazy { load("portal/remote.html") }
    private val audience: String by lazy { load("portal/audience.html") }

    /** The speaker's remote. Served at `/` and `/remote`. */
    fun presenterHtml(): String = presenter

    /** The audience portal. Served at `/audience`. */
    fun audienceHtml(): String = audience

    private fun load(resourcePath: String): String {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(resourcePath)
            ?: PortalAssets::class.java.classLoader?.getResourceAsStream(resourcePath)
            ?: error(
                "Companion portal asset '$resourcePath' is missing from the packaged resources. " +
                    "The pairing flow cannot work without it."
            )

        return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            .replace(VERSION_PLACEHOLDER, BuildInfo.DISPLAY_VERSION)
    }
}
