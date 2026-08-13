package com.skaldoria.remote

import com.skaldoria.PresentationStateTestBase
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * F-07: the companion's access-control policy, asserted **structurally**.
 *
 * The policy used to be membership in two separate sets (`PRESENTER_ENDPOINTS`,
 * `WRITE_ENDPOINTS`) checked against a third `when (path)` dispatch. Adding an endpoint took
 * three coordinated edits, and *nothing failed if one was forgotten*: a write endpoint left
 * out of `WRITE_ENDPOINTS` was silently exempt from both POST-only (SEC-3) and rate limiting
 * (SEC-5); left out of `PRESENTER_ENDPOINTS` it was silently unauthenticated (SEC-2).
 *
 * These tests iterate the route table rather than naming paths, so a new route is covered
 * the moment it is declared — the point of making the table data. `RemoteCompanionServerTest`
 * keeps the by-name tests for the specific routes that carry the invariants.
 */
class RouteTableSecurityTest : PresentationStateTestBase() {

    private fun open(url: String, method: String = "GET", token: String? = null): HttpURLConnection =
        (java.net.URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 3000
            readTimeout = 3000
            if (token != null) setRequestProperty("X-Skaldoria-Token", token)
        }

    private fun <T> withServer(block: (String) -> T): T {
        val state = presentationState()
        RemoteCompanionServer.start(state, preferredPort = 18777)
        return try {
            block("http://127.0.0.1:${RemoteCompanionServer.currentPort}")
        } finally {
            RemoteCompanionServer.stop()
            state.dispose()
        }
    }

    @Test
    fun `the table declares routes`() {
        assertTrue(RemoteCompanionServer.routes.isNotEmpty())
    }

    @Test
    fun `no path is declared twice`() {
        val paths = RemoteCompanionServer.routes.map { it.path }
        assertEquals(paths.distinct().size, paths.size, "a duplicate path makes dispatch order-dependent: $paths")
    }

    /** SEC-2 — presenter scope means the session token is mandatory. */
    @Test
    fun `every presenter route rejects a request with no token`() {
        withServer { base ->
            val presenterRoutes = RemoteCompanionServer.routes.filter { it.scope == RouteScope.PRESENTER }
            assertTrue(presenterRoutes.isNotEmpty(), "fixture: expected at least one presenter route")

            presenterRoutes.forEach { route ->
                val response = open("$base${route.path}", route.method.name).responseCode
                assertEquals(401, response, "SEC-2: ${route.path} accepted an unauthenticated request")
            }
        }
    }

    /** SEC-2 — presenter routes accept the token, so the check is real and not a blanket deny. */
    @Test
    fun `every presenter route accepts the session token`() {
        withServer { base ->
            val token = RemoteCompanionServer.presenterUrl().substringAfter("?t=")
            RemoteCompanionServer.routes
                .filter { it.scope == RouteScope.PRESENTER }
                .forEach { route ->
                    val response = open("$base${route.path}", route.method.name, token).responseCode
                    assertNotEquals(401, response, "${route.path} rejected a valid token")
                    assertNotEquals(404, response, "${route.path} is declared but not reachable")
                }
        }
    }

    /** SEC-3 — a cross-origin page can only issue a GET, so mutating routes must refuse it. */
    @Test
    fun `every mutating route rejects GET`() {
        withServer { base ->
            val mutating = RemoteCompanionServer.routes.filter { it.method == HttpMethod.POST }
            assertTrue(mutating.isNotEmpty(), "fixture: expected at least one mutating route")

            mutating.forEach { route ->
                val response = open("$base${route.path}", "GET").responseCode
                assertEquals(405, response, "SEC-3: ${route.path} accepted a drive-by GET")
            }
        }
    }

    /** Reads must stay open: the audience portal holds no token by design. */
    @Test
    fun `every public route answers without a token`() {
        withServer { base ->
            RemoteCompanionServer.routes
                .filter { it.scope == RouteScope.PUBLIC }
                .forEach { route ->
                    val response = open("$base${route.path}", route.method.name).responseCode
                    assertEquals(200, response, "${route.path} should be reachable without a token")
                }
        }
    }

    /** Every declared route is wired to a handler — none 404s. */
    @Test
    fun `every route is reachable`() {
        withServer { base ->
            val token = RemoteCompanionServer.presenterUrl().substringAfter("?t=")
            RemoteCompanionServer.routes.forEach { route ->
                val response = open("$base${route.path}", route.method.name, token).responseCode
                assertNotEquals(404, response, "${route.path} is declared but dispatch does not know it")
            }
        }
    }

    /** An undeclared path is still a 404 — the table is the whole surface. */
    @Test
    fun `an undeclared path is not found`() {
        withServer { base ->
            assertEquals(404, open("$base/api/definitely-not-a-route").responseCode)
        }
    }

    /**
     * The property the old two-set arrangement could not express: audience scope means
     * "mutating, but no token". Losing this distinction is how a write endpoint would end up
     * silently unauthenticated.
     */
    @Test
    fun `audience routes mutate without a token but are still POST-only`() {
        val audienceRoutes = RemoteCompanionServer.routes.filter { it.scope == RouteScope.AUDIENCE }
        assertTrue(audienceRoutes.isNotEmpty(), "fixture: expected audience-writable routes")
        audienceRoutes.forEach {
            assertEquals(HttpMethod.POST, it.method, "${it.path}: audience routes mutate, so they must be POST")
        }
    }
}
