# Architecture Decision Record (ADR)
## ADR-005: Companion Connectivity Without a Shared LAN

### Status
**Partially implemented** (2026-08-06)

| Phase | Item | State |
| :--- | :--- | :--- |
| 0 | Verify BLE clicker (`AUD-08`) | ⬜ Needs hardware — likely already works, see below |
| 1 | Fix ranking, LNK-A/LNK-B (`AUD-09`) | ✅ **Shipped** — `LinkRanking` + `LinkKind`, extracted pure and guarded by `LinkRankingTest` |
| 2 | Guide the user (`AUD-10`) | ⬜ Not started |
| 3 | Wi-Fi credential QR (`AUD-11`) | ⬜ Not started |
| 4 | Automate hotspot creation (`AUD-12`) | ⬜ Optional, deliberately last |

> Phase 1 removed the two defects that blocked every option below a shared LAN, so a hotspot,
> USB tether or Bluetooth PAN link is now ranked and advertised correctly **once the user has
> established it manually**. Phase 2 — telling the user how, and refusing to show a QR that
> cannot work (LNK-3) — is the remaining gap between "supported" and "usable".
>
> One implementation finding worth carrying forward: Windows names its hotspot adapter
> *"Microsoft Wi-Fi Direct **Virtual** Adapter"*, so the direct-link classification must run
> **before** the virtual denylist or the fix cancels itself. Guarded.

> This ADR addresses the case the companion
> cannot serve today — presenter and audience have no shared network — and concludes that the
> answer is **not a new transport**. The correct abstraction is *how the phone acquires an IP
> route to the laptop*, which leaves [ADR-001](./ADR_COMPANION_SERVER_ARCHITECTURE.md) entirely
> intact and requires no new dependency.

---

### Contents

- [Context](#context)
- [The reframe: abstract the link, not the radio](#the-reframe-abstract-the-link-not-the-radio)
- [Why Bluetooth-as-a-transport is the wrong answer](#why-bluetooth-as-a-transport-is-the-wrong-answer)
- [Why Bluetooth PAN is a *right* answer](#why-bluetooth-pan-is-a-right-answer)
- [The link ladder](#the-link-ladder)
- [Two audiences, two answers](#two-audiences-two-answers)
- [What the code already does right](#what-the-code-already-does-right)
- [Two defects that block every option below shared LAN](#two-defects-that-block-every-option-below-shared-lan)
- [Decision](#decision)
- [Plan](#plan)
- [Invariants introduced](#invariants-introduced)
- [Guards](#guards)
- [Risks and open questions](#risks-and-open-questions)
- [Rejected](#rejected)

---

### Context

The companion server binds to the local network and serves two web portals — a presenter
console and an audience portal for polls and Q&A — paired by QR code. `QUALITY_BASELINE.md`
states its threat model explicitly: *"a shared conference network"*.

That assumption is the feature's single point of failure, and it fails routinely:

- **Guest Wi-Fi client isolation.** Most corporate and hotel guest networks forbid
  station-to-station traffic. Laptop and phone are on the same SSID and cannot reach each other.
- **Enterprise AP client isolation** — the same, deliberately, as policy.
- **Separate VLANs** for staff and guest devices.
- **No usable network at all** — a client site that will not issue credentials, an offsite
  venue, a room with no coverage.

In every one of these the deck presents fine and the entire companion feature — polls, Q&A,
remote control, parking lot capture — is unavailable, with no fallback. Nothing about the
server is wrong; it simply has nowhere to listen that the phone can reach.

The question raised was whether Bluetooth is the answer.

---

### The reframe: abstract the link, not the radio

The instinct to reach for Bluetooth treats the problem as *"we need a different transport."*
That framing is what makes the problem look expensive, because it implies replacing HTTP, the
portals, the pairing flow and the security model.

The problem is narrower than that. The companion needs exactly one thing:

> **An IP route between the phone and the laptop.**

Everything above that — the HTTP/1.1 server, both portals, the session token (SEC-2), the
parser limits (SEC-7), the QR pairing, the rate limiting (SEC-5) — is transport-agnostic and
already works. It does not care whether the packets travelled over Wi-Fi infrastructure, a
laptop-hosted access point, a USB cable, or a Bluetooth PAN link.

So the design question is not *"which radio do we speak?"* but *"which ways can a phone get an
IP route to this laptop, and how do we help the user establish one and then advertise the right
address?"*

Framed that way, **every viable option requires zero changes to the server**, because the OS
owns the radio and Java only ever sees a `NetworkInterface`. The work is entirely in link
establishment, address selection and user guidance.

---

### Why Bluetooth-as-a-transport is the wrong answer

Speaking Bluetooth *from Java, as an application protocol* is not viable here, for four
independent reasons — any one of which is disqualifying.

**1. The JVM has no Bluetooth API.** `java.base` gives sockets, not radios. The options are
JSR-82 via BlueCove — effectively unmaintained for over a decade, with no BLE support at all —
or per-platform native bindings: WinRT/Win32 on Windows, CoreBluetooth via Objective-C bridging
on macOS, BlueZ over D-Bus on Linux. That is three native integrations to build and maintain
forever.

**2. It would be the largest portability regression available to this project.** ADR-001's
decision rests specifically on a `java.base`-only server and JPMS portability across six
installer formats, and the NFR table limits runtime dependencies to Compose, coroutines and the
markdown parser. Native Bluetooth bindings breach both, in exchange for a capability the OS
already provides for free (see the next section).

**3. Browsers cannot speak it.** The companion's entire value proposition is that an audience
member scans a code and gets a web page — no app install, no store, no trust decision. Browsers
cannot open Bluetooth Classic/RFCOMM sockets at all. Web Bluetooth is Chrome-only, GATT-only,
requires a user gesture per device, and is absent from iOS Safari. Delivering the audience
portal over Bluetooth would mean shipping a native mobile app: a second product, with two app
stores, review cycles and release trains.

**4. The topology does not fit.** Bluetooth Classic piconets cap around seven active devices;
BLE is similar in practice and offers a few KB/s. The stated NFR is 200+ concurrent poll
voters. This is off by a factor of thirty on connections alone.

**This analysis is accepted and is not the interesting part.** The interesting part is that
rejecting Bluetooth-as-a-transport does not reject Bluetooth.

---

### Why Bluetooth PAN is a *right* answer

Bluetooth has a profile that carries **IP over Bluetooth**: PAN (Personal Area Network,
NAP/PANU). Phone-side this is ordinary "Bluetooth tethering", available on Android and widely
supported on desktop OSes.

When a phone tethers over Bluetooth PAN:

- The **operating system** owns the radio, the pairing and the profile.
- A **network interface appears** on the laptop with an IPv4 address.
- `java.net` sees a perfectly ordinary interface.
- **The existing companion server works unchanged** — no JNI, no BlueCove, no Web Bluetooth, no
  new dependency, no change to ADR-001.

This is the distinction the "Bluetooth is the wrong transport" analysis misses: it is right
about Bluetooth as an *application transport* and wrong to conclude that Bluetooth has no role.
As a *link layer* it is not only viable, it is nearly free — because the project never touches
it.

The same reasoning covers USB tethering, which is the same idea over a cable and is strictly
better where it is available.

> **Caveat, and it is a real one.** PAN inherits Bluetooth's throughput and topology limits.
> It is entirely adequate for a **presenter remote** — one device, a few small JSON polls per
> second — and entirely inadequate for an **audience portal**. See
> [Two audiences, two answers](#two-audiences-two-answers).

---

### The link ladder

Ranked by preference, with the honest cost of each.

| # | Link | Who provides it | Server change | Audience-capable | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Shared LAN** | Venue | none — works today | ✅ | The happy path. Fails on client isolation. |
| 2 | **SoftAP** — laptop hosts the network | Laptop OS | **none** | ✅ | Phone joins a network the laptop creates. No venue infrastructure involved. **The primary answer.** |
| 3 | **USB tethering** | Phone + cable | **none** | ❌ (1 device) | Zero-config, immune to RF conditions, no pairing. Best presenter-remote fallback. |
| 4 | **Bluetooth PAN** | Phone OS | **none** | ❌ (~7 devices) | Cable-free version of 3. Lower throughput. |
| 5 | **BLE HID clicker** | Off-the-shelf hardware | **none — likely works today** | ❌ (no UI) | Not a companion link at all; the OS delivers keystrokes. |
| 6 | Wi-Fi Direct | Laptop OS | none | partial | Largely subsumed by SoftAP on desktop; messier and less portable. |
| 7 | Internet relay | A server we run | large | ✅ | Rejected — see [Rejected](#rejected). |

The striking column is **"Server change"**. Options 1–6 all read *none*. That is the whole
argument of this ADR: the companion does not need a new transport, it needs help getting onto
one of these links and then advertising the correct address.

#### Option 5 deserves immediate attention

A £20 BLE presenter clicker pairs at the OS level and emits `PageUp` / `PageDown` as ordinary
keystrokes. `FullscreenDeck.kt:93` and `:97` already consume both:

```kotlin
event.key in listOf(Key.Spacebar, Key.DirectionRight, Key.DirectionDown, Key.PageDown) -> state.next()
event.key in listOf(Key.DirectionLeft, Key.DirectionUp, Key.PageUp)                    -> state.prev()
```

So off-the-shelf clickers most likely work **today, with no code at all**. This should be
tested and documented before anything else in this ADR is built — it may satisfy a large share
of the "I just want to advance slides without the network" need for zero effort.

---

### Two audiences, two answers

Conflating these is what makes the problem look unsolvable. They have different device counts
and different constraints.

| | Presenter remote | Audience portal |
| :--- | :--- | :--- |
| Devices | 1 | up to 200+ (NFR) |
| Install allowed | yes — it is the speaker's own phone | **no** — must be a URL in a browser |
| Data volume | tiny | polls and Q&A from many devices |
| Viable links | SoftAP, USB, BT PAN, BLE clicker | **SoftAP or shared LAN only** |

**The honest conclusion: there is no non-Wi-Fi link that can serve the audience.** Bluetooth
PAN and USB tethering are single-device links. Only SoftAP removes the venue-network dependency
while still serving many browsers.

This is why SoftAP is the primary decision and the rest are fallbacks, and it is worth stating
plainly rather than implying that every gap is closable.

---

### What the code already does right

Three existing properties mean most of this is already built.

**1. The server binds to the wildcard address.** `RemoteCompanionServer.kt:129`:

```kotlin
ss.bind(InetSocketAddress(portCandidate))
```

No `InetAddress` is supplied, so the socket listens on **every** interface. The moment a
SoftAP, tether or PAN interface appears, **the companion is already serving on it.** No code
change is required to *serve* any link on the ladder.

**2. Address selection is already a ranked list with a manual override.**
`availableAddresses()` returns ranked `NetworkCandidate`s, `preferredAddress` overrides the
heuristic, and the pairing dialog already lets the speaker pick
(`RemotePairingDialog.kt:214`). The mechanism to advertise an unusual address exists.

**3. The QR generator encodes arbitrary text.** `QrCodeGenerator.encode(text)` takes any
string. The standard Wi-Fi provisioning payload —

```
WIFI:T:WPA;S:<ssid>;P:<password>;;
```

— which both iOS and Android join natively from the camera, is **already generatable today**,
with no change to the generator. A two-step pairing flow (scan to join the hotspot, scan to open
the portal) is almost entirely existing capability.

---

### Two defects that block every option below shared LAN

Both are in the ranking heuristic, and both were found by reading it against these scenarios.
Neither is hypothetical.

#### LNK-A — the routed-address heuristic actively deprioritises SoftAP

`routedAddress()` (`RemoteCompanionServer.kt:254`) connects a UDP socket toward `8.8.8.8` to
discover which interface holds the default route, and `availableAddresses()` sorts
`isRouted` first.

A SoftAP or tether interface **carries no default route.** So:

- the hotspot address gets `isRouted = false` and sorts *behind*
- the laptop's own Wi-Fi or Ethernet, which is still routed and sorts **first** — and which the
  phone, now attached to the hotspot, cannot reach.

The QR would advertise an unreachable address. This is precisely the class of bug the ranking
was introduced to fix — `NetworkAddressTest` documents the VirtualBox `192.168.56.1` incident —
recurring in a new scenario. The comment at `:252` states the assumption honestly: *"almost
always the adapter a phone on the same wifi can reach."* Under SoftAP, that assumption inverts.

#### LNK-B — `"bluetooth"` is on the virtual-adapter denylist

`VIRTUAL_ADAPTER_HINTS` (`RemoteCompanionServer.kt:242`) contains `"bluetooth"`. A Bluetooth
PAN interface — typically displayed as *"Bluetooth Device (Personal Area Network)"* — matches,
is flagged `isLikelyVirtual = true`, and is sorted last as a hypervisor artefact.

So the single heuristic that would need to change to support Bluetooth PAN is already written,
and it currently rules it out. The denylist is correct for its original purpose (a Bluetooth
adapter with no PAN link is not reachable) and wrong once PAN is a supported link; it needs to
discriminate on *whether the interface carries a usable address*, not on its name.

The tether-adapter names for USB (`Remote NDIS…`, `Apple Mobile Device Ethernet`) and for
Windows Mobile Hotspot should be checked against the same list before Phase 1 is sized.

---

### Decision

**1. Do not add a Bluetooth transport.** Accepted in full, for the four reasons above.

**2. Treat the problem as link establishment, not transport.** The companion gains no new
protocol. It gains the ability to (a) recognise a non-LAN link, (b) rank and advertise it
correctly, and (c) tell the user how to create one.

**3. SoftAP is the primary answer**, because it is the only option that serves the audience
portal without venue infrastructure.

**4. USB tethering and Bluetooth PAN are supported as presenter-remote fallbacks**, at
essentially zero cost, because supporting them means *not excluding* them from address ranking.

**5. Verify the BLE clicker path first.** It may already work and cost nothing.

**6. ADR-001 stands, untouched.** No new dependency, no new native code, `java.base` only for
Phases 1–3. This is the reason to prefer this framing over a transport abstraction: the
hand-rolled server's portability argument is preserved rather than reopened.

---

### Plan

| Phase | Work | Size | Depends |
| :--- | :--- | :--- | :--- |
| **0** | **Verify the BLE clicker.** Pair an off-the-shelf clicker, confirm `PageUp`/`PageDown` drive the deck, document it in the user guide. Possibly zero code. | S | — |
| **1** | **Fix ranking (LNK-A, LNK-B).** Detect hotspot/tether/PAN interfaces; stop ranking on `isRouted` alone; discriminate Bluetooth PAN by whether it carries a usable address rather than by name. Label link kind in the pairing dialog. | M | — |
| **2** | **Guide the user.** Per-platform instructions for enabling a hotspot or tethering, shown in the pairing dialog when no reachable candidate is found. Detect the "isolated network" case and say so, instead of showing a QR that cannot work. | M | 1 |
| **3** | **Wi-Fi credential QR.** Emit `WIFI:T:WPA;S:…;P:…;;` so the phone joins the hotspot by camera, then chains to the portal QR. Uses the existing generator unchanged. | S | 2 |
| **4** | **Automate hotspot creation** (optional). Per-platform: `nmcli device wifi hotspot` on Linux is clean; Windows needs WinRT tethering APIs or the deprecated `netsh wlan hostednetwork`; macOS Internet Sharing scripts poorly. **Only if Phases 1–3 prove insufficient** — this is where platform-specific code and its maintenance cost would finally enter. | L | 2 |

Phases 0–3 contain **no platform-specific code and no new dependency.** They are guidance,
detection and ranking. That is deliberate: it delivers the capability while keeping the
portability argument that ADR-001 rests on completely intact. Phase 4 is the only step that
would compromise it, and it is explicitly last and explicitly optional.

---

### Invariants introduced

| ID | Area | Invariant |
| :--- | :--- | :--- |
| **LNK-1** | Companion | The advertised address is one the *client* can reach, never merely one the host can route from. Default-route presence is a hint, not a ranking authority. |
| **LNK-2** | Companion | An interface is judged by whether it carries a usable address, not by its display name. Name hints may only break ties. |
| **LNK-3** | Companion | When no reachable candidate exists, the pairing UI says so and offers a remedy. It never shows a QR that cannot work. |
| **LNK-4** | Companion | Link kind changes nothing above IP. The server, both portals and every `SEC-*` control are transport-agnostic and stay that way. |

---

### Guards

| Invariant | Guard |
| :--- | :--- |
| LNK-1 | `NetworkAddressTest` — a candidate with no default route but a usable private address is not ranked below an unreachable routed one under a simulated SoftAP topology. |
| LNK-2 | `NetworkAddressTest` — an interface named `Bluetooth Device (Personal Area Network)` carrying a valid IPv4 is offered, not denylisted. |
| LNK-3 | A pairing-dialog test — with zero candidates, the QR is not rendered and remediation text is. |
| LNK-4 | Existing `RemoteCompanionServerTest` is sufficient and must keep passing unchanged; if a change here requires touching it, the abstraction has leaked. |

`availableAddresses()` reads the live machine, so these need the interface enumeration behind a
seam to be testable — which is `F-01`-shaped work and pairs naturally with the hermetic-test
item already in [`REFACTORING_BACKLOG.md`](./REFACTORING_BACKLOG.md).

---

### Risks and open questions

**Single-radio laptops lose internet.** Hosting a SoftAP on many laptops takes the Wi-Fi radio
out of client mode, so the presenter loses their own connectivity while presenting. Some
adapters support concurrent AP+STA via a virtual adapter; this **needs verification per
platform** and must be stated plainly in the guidance, because a speaker who loses their
connection mid-talk will consider it a bug.

**SoftAP is a network the laptop creates.** It must be WPA2-protected with a generated
password, not open. The existing `SEC-*` controls continue to apply and are unaffected —
SEC-2's token model in particular assumes an untrusted local network already, which is exactly
the posture a SoftAP needs.

**Captive-portal interference.** Some phones detect "no internet" on the hotspot and silently
switch back to cellular, breaking the link. Mitigations exist but are fiddly; this is the most
likely source of field failures and should be tested early in Phase 2.

**Adapter naming is not a stable contract.** Phase 1 depends on recognising hotspot and tether
interfaces across three OSes and many drivers. LNK-2 exists precisely so that recognition
degrades to a tie-breaker rather than a gate.

**Phase 4 reopens ADR-001 if taken.** Automating hotspot creation is per-platform native
integration. It is not obviously worth it; Phases 1–3 may well be enough, since enabling a
hotspot is a handful of taps the user does once.

---

### Rejected

| Option | Why not |
| :--- | :--- |
| **Bluetooth as an application transport** | Four independent blockers; see above. |
| **A native companion mobile app** | Would unlock BLE, and destroy the feature's premise. The audience portal works because it is a URL — no install, no store, no trust decision, no release train. Two app stores is a second product. |
| **Internet relay / cloud rendezvous** | Solves the isolation case, but inverts the product: it introduces a server we operate, an account model, a privacy surface, and a hard internet dependency for a tool whose selling point is zero-dependency local-first operation. It also fails the exact scenario it is meant to fix — a venue with no usable network. |
| **QR-encoded poll pointing at a public URL** | Same objection: reintroduces an internet dependency to work around a missing local one. |
| **Ad-hoc / IBSS Wi-Fi** | Deprecated across modern mobile OSes; iOS will not join ad-hoc networks. SoftAP is the supported successor. |
