package com.skaldoria.remote

import com.skaldoria.state.PresentationState
import java.io.OutputStream

/** The HTTP methods this server understands. */
enum class HttpMethod { GET, POST }

/**
 * Who may call a route, and therefore what the server enforces before dispatching.
 *
 * The ordering of privilege is [PUBLIC] < [AUDIENCE] < [PRESENTER]. Only [PRESENTER]
 * requires the session token; [AUDIENCE] exists to name the routes that *mutate state
 * without one* — the phones in the room have no credential by design (see ADR-001). That
 * distinction had no representation at all in the previous two-set arrangement, which is
 * precisely how a write endpoint could end up unauthenticated by omission.
 */
enum class RouteScope {
    /** Readable by anything on the LAN. Never mutates. */
    PUBLIC,

    /** Mutates state, deliberately without a token. Rate-limited (SEC-5). */
    AUDIENCE,

    /** Drives the deck or moderates. Session token required (SEC-2). */
    PRESENTER
}

/** Everything a handler needs, so handlers do not each re-derive it. */
class RequestContext(
    val params: Map<String, String>,
    val state: PresentationState,
    val output: OutputStream,
    /** Stable per-device identity, for one-ballot-per-device and rate limiting (SEC-5). */
    val clientKey: String,
    /** Whether a valid session token accompanied the request. */
    val authorized: Boolean
)

/**
 * One endpoint, with its access-control policy attached.
 *
 * SEC-8: this replaces `PRESENTER_ENDPOINTS`, `WRITE_ENDPOINTS` and the `when (path)` dispatch
 * — three places that had to be edited together and **failed silently when they were not**.
 * A route now cannot exist without declaring its method and scope, so the policy is derived
 * rather than remembered, and `RouteTableSecurityTest` can assert over the whole table
 * instead of enumerating known paths.
 *
 * @property mutating derived from [method]: anything that is not a read changes state, and
 *   therefore must be POST-only (SEC-3) and rate-limited (SEC-5).
 */
data class Route(
    val path: String,
    val method: HttpMethod,
    val scope: RouteScope,
    val handler: (RequestContext) -> Unit
) {
    val mutating: Boolean get() = method != HttpMethod.GET

    /** SEC-2: only presenter scope carries a credential. */
    val requiresToken: Boolean get() = scope == RouteScope.PRESENTER
}
