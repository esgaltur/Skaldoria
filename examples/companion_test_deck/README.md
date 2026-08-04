# Companion Test Deck

A 17-slide deck for exercising the **wireless companion** — speaker remote, audience polls,
Q&A, and the parking lot — while also demoing every slide layout the app supports.

Open it in Skaldoria with **Open File or Project…** → select this folder (or `deck.mdpres`).

---

## Running the test

1. **Start the server.** Open the Remote & Audience dialog and press **Start Server**.
2. **Pair your own phone** with the **Speaker** QR. That link carries a session token — it
   drives the deck and shows speaker notes. Treat it like a password.
3. **Give the room the Audience link.** It carries no token: it can vote and ask questions,
   and it cannot advance slides or read your notes.
4. Work through the deck. Each slide's speaker note says what to try on that slide.

Both phones must be on the **same network** as the laptop. Guest wifi with client isolation
is the usual reason a phone can load nothing.

---

## What each slide exercises

| # | Slide | Layout | What to test |
|---|---|---|---|
| 1 | Skaldoria Companion | `HERO_TITLE` | Pairing before you begin |
| 2 | How To Test | `BULLET_LIST` | The two link scopes |
| 3 | Part One | `SECTION_HEADER` | Section divider |
| 4 | What The Remote Controls | `BULLET_LIST` | **Blackout / Whiteout / Timer** from the phone |
| 5 | Pairing Flow | `SPLIT_TEXT_CODE` | Text beside code |
| 6 | (token check) | `FULL_CODE` | Line highlighting via `[4, 7-10]` |
| 7 | Request Path | `DIAGRAM` | Mermaid **flowchart**, branching topology |
| 8 | Voting Round Trip | `DIAGRAM` | Mermaid **sequence**: `alt`/`else`, self-calls, activation bars |
| 9 | Endpoint Scopes | `DATA_TABLE` | Table rendering |
| 10 | (Dijkstra-ish quote) | `BIG_QUOTE` | Quote + attribution |
| 11 | 700 ms | `BIG_METRIC` | KPI slide |
| 12 | Pacing Maths | `MATH_FORMULA` | LaTeX block |
| 13 | How are you joining today? | `POLL` | **Poll — 3 options.** In-slide QR, live bars |
| 14 | Which companion feature…? | `POLL` | **Poll — 4 options**, longer labels |
| 15 | Ask Anything | `BULLET_LIST` | **Q&A submit / upvote / dismiss**, seeded parking lot |
| 16 | Media Slide | `SPLIT_TEXT_MEDIA` | Image loaded from `assets/` |
| 17 | Stop The Server | `HERO_TITLE` | Token invalidation |

---

## The interactive bits

### Polls (slides 13 and 14)

The audience portal shows the poll for whatever slide is **currently on screen**, so the room
can only vote while you are on that slide. Each slide renders its own QR and live tallies.

**One ballot per device.** Voting again *replaces* your previous choice rather than adding to
it, so totals do not inflate when someone refreshes. Try it: vote, reload the page, vote for
something else, and watch the total stay put.

### Q&A (slide 15)

Submit from the audience link with or without a name. Questions appear on the speaker phone,
where you can dismiss handled ones. Upvoting reorders them.

### Parking lot (slide 15)

Two items are authored directly in `slides/15_qa.md`:

```
<!-- parking-lot: [ ] Does the companion work across VLANs? | slide:15 -->
```

They load into the Parking Lot drawer on open. Deleting one **rewrites this file** and removes
the comment — the markdown is the only storage, so the change is permanent. Questions you
capture during the talk are appended to the deck the same way, each with a persisted `id:`.

> If you want to re-run the test from a clean slate, `git checkout` the `slides/` folder
> afterwards — the deck is designed to be written to.

---

## Security boundary worth checking

Slide 9 lists the endpoint scopes. Two things worth confirming by hand:

- Open the **audience** link and try `…/api/action?action=next` in the address bar — it should
  refuse (405, and 401 without a token), and the deck must not advance.
- Compare `/api/state` from both phones: the speaker's response includes `notes`, the
  audience's returns them empty.

After **Stop Server**, the old speaker link should stop working — the token is regenerated on
every start, so previously-shared QR codes go dead.

---

## Images

Slide 16 loads `assets/pairing.png` from beside the deck. Relative paths resolve against the
project root, absolute paths work, and `http(s)` URLs are fetched with a timeout and a size
cap. Rename the asset to see the failure state — it names the missing path rather than showing
a blank panel.

Cosmetic leftover: the footer pill on the sequence-diagram slide still reads
`ARCHITECTURE / FLOW DIAGRAM`, since it comes from the layout's display name rather than the
parsed diagram type.

---

## Guarded by tests

`CompanionDeckTest` keeps this deck honest: it asserts all 17 files resolve, every listed
layout is covered, both polls parse with the right question and option count, every slide has
a speaker note, the parking-lot items load, no directive comment leaks onto a slide, and both
mermaid diagrams parse. If someone edits a slide and breaks a directive, the build fails.
