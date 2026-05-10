# LogHound — Design Brief

A document for a UI/UX designer joining the project. Two upcoming features need
visual and interaction design: **multi-device support** and **session
management**. This brief captures what the app is today, the constraints any
new design must respect, and the user problems each feature solves.

---

## 1. What LogHound is

LogHound is a Compose Multiplatform desktop application that reads Android
`logcat` output, persists it to a local SQLite database, and lets developers
filter, search, and analyse it. It exists because Android Studio's built-in
Logcat panel falls over at GB-scale log volume; LogHound is built to handle
millions to tens of millions of lines without becoming unresponsive.

**Target users.** Android developers debugging high-volume apps, QA engineers
analysing test sessions, and backend/mobile teams correlating distributed
operations via log correlation IDs.

**Platforms.** macOS (primary), Linux, Windows. Native window chrome on each.

For the full functional specification, read [`behavior.spec.md`](behavior.spec.md).

---

## 2. Existing UI patterns (the design language)

The app today is a single window with a left sidebar, a tab bar across the top
of the content area, and a status bar at the bottom. Visual tokens live in a
shared `LogHoundDesign` object — every plugin uses the same colours, text
styles, and priority palette by default, so plugins look like they belong to
one app.

### Layout primitives in use

- **Sidebar** lists every registered plugin (Log Viewer, UUID Grouping, plus
  user-installed `.kts` script plugins). Click an entry to open it as a tab in
  the content area; click again to focus the existing tab.
- **Tab bar** runs across the top of the content area. Tabs are pills with a
  rounded white background when active, transparent when inactive, with a small
  ✕ close button.
- **Sub-tabs** appear inside some plugins (e.g. UUID Grouping's per-UUID
  drill-down). They use the same pill pattern but live inside a plugin's
  content area, not the top tab bar.
- **Toolbar / section** — a light-grey horizontal strip (`#F7F7F7`) with a
  search field, action buttons, and a status text. Sits below the tab bar.
- **Content list** — monospace 12sp rows with a subtle row divider, vertical
  scrollbar on the right, drag-select-to-copy via a Compose
  `SelectionContainer`.
- **Status bar** at the bottom of the window shows total ingested lines,
  filtered count, and ingestion status.
- **Native menu bar** — File / Edit / View / Help. Currently mostly skeleton.

### Typographic and colour tokens

- Default body text: 12 sp, near-black `#222222`, system sans-serif.
- Status / secondary: 12 sp, gray `#666666`.
- Log row: monospace 12 sp, `#222222`.
- Tab pill text: 13 sp, black.
- Search / text-field text: 13 sp, black.
- Button text: 13 sp, Material blue `#1976D2`.
- Dividers: `#CCCCCC` (heavy), `#EEEEEE` (between rows).
- Toolbar background: `#F7F7F7`. Tab strip background: `#F0F0F0`. Active tab:
  white.
- Priority colour palette (used in log lines, optional in plugins):
  Verbose `#666666`, Debug `#1976D2`, Info `#388E3C`, Warn `#F57C00`,
  Error `#D32F2F`, Fatal `#D32F2F` bold, Silent `#999999`.

These are deliberately understated — the UI is data-dense, not decorative.
Designs should respect that aesthetic; bold accents read as noise in a window
showing tens of thousands of log lines.

### Plugin-authored UI

A subset of plugins are authored as `.kts` scripts and use a small DSL with
~13 verbs (`column`, `row`, `text`, `list`, `tabs`, `clickable`, `textField`,
`button`, `centered`, `loading`, `divider`, `spacer`, `weight`). These plugins
inherit the design language by default. Designs for new features must be
expressible (or at least *compatible*) with the plugin DSL — anything that
forces every plugin to grow custom UI undermines the system. See
[`plugins.spec.md`](plugins.spec.md) for the full DSL.

---

## 3. Feature 1 — Multi-device support

### The user problem

Today LogHound assumes one ADB device. Real Android development is multi-device
all the time: a debug build running on a physical Pixel 8, an emulator running
the latest API for compat checks, a teammate's tablet over `adb connect`. The
user wants to:

1. See what's connected at a glance.
2. Scope their entire workflow ("I'm debugging Pixel 8 right now") so every
   plugin filters to that device automatically.
3. Compare two devices side by side when correlating a regression.
4. Have a phone they plug in to charge *not* clutter the UI.

### The design decision: device is **app-wide context**, not a Log-Viewer toggle

A device is a global scope, not a setting for one plugin. When the user picks
"Pixel 8," every plugin in the app should reflect that — Log Viewer's stream,
UUID Grouping's UUID counts, every script-loaded plugin. Otherwise every
plugin reinvents per-device UI and the user has to mentally re-scope at each
sidebar entry.

The chosen shape is a **status-bar device pill** as the single
device-management surface, plus a **Compose composition local
(`LocalActiveDevice`)** that plugins read for implicit scoping. Plugins can
opt out (UUID Grouping's master list might aggregate across devices for
analytical context) and plugins can layer per-device sub-tabs on top for
side-by-side comparison (Log Viewer is the obvious one). But device
*management* lives in one place.

### Components to design

#### 3.1 Status-bar device pill (always visible, primary surface)

Position: bottom of the window, alongside other pills (the session pill from
feature 2 lives next to it).

Idle state: the pill says how many devices are connected and which is the
active scope. Examples:

- 0 devices: `○ No devices`
- 1 device, set as active: `● Pixel 8`
- 2+ devices, all active (= "All devices" mode): `●● 2 devices: Pixel 8 · Emu Pix 7`
- 2+ devices, one active: `● Pixel 8 (+1 more)`

Each device gets an auto-assigned colour from a palette of ~6 tints; the dot
in the pill renders that colour. Same colour stays bound to the same device
across launches (persisted by serial).

Click the pill → a popover menu opens. The popover lists every detected
device, even ones that aren't pinned anywhere:

- Each device row shows: colour dot · editable name · "Set as active" radio
  (single-select, with an "All devices" option at top) · "Pin to Log Viewer"
  toggle · current connection state · "Disconnect" action.
- A "Refresh" button at the bottom runs `adb devices` immediately rather than
  waiting for the 30-second background poll.
- An empty state explains how to connect (`Plug in via USB, or run adb connect <host>`)
  with a Refresh button as the only affordance.

Open question for the designer: where should the popover anchor — above the
pill (popping up) or below, off-window? Above is conventional for status-bar
chrome but constrains height.

#### 3.2 Connection states (visible everywhere a device appears)

Same vocabulary used in the pill, in Log Viewer's per-device tabs, and in any
plugin's device representation:

- **Live** — receiving log lines in the last 5 seconds. Solid colour dot with
  a subtle pulse animation.
- **Idle** — connected but no recent log activity (60+ seconds quiet). Solid
  colour dot, no pulse.
- **Reconnecting** — `adb` dropped the connection; we're retrying. Amber
  spinner overlay on the colour dot. Tab borders go dashed.
- **Disconnected** — device is gone (USB unplugged, adb server died). Hollow
  colour dot, dashed border on tab pill. Last known data preserved; the user
  can dismiss to remove.

These need clear visual differentiation that survives at small sizes.

#### 3.3 Cross-device row stripe (mixed-stream views)

When a view shows logs from multiple devices interleaved (the master "All
devices" view in Log Viewer, or any plugin showing aggregate state), every
row gets:

- A 2–3 px coloured vertical bar at the leading edge of the row, in the
  device's colour.
- A small `[Device Name]` chip prefixing the row content, before the
  timestamp.

Both are needed: the colour stripe is fast visual scanning, the chip is
belt-and-braces for colour-blind users and for very long lines that scroll
the colour stripe off-screen.

In single-device views (when scope is set to one device, or in a per-device
sub-tab), neither stripe nor chip — they're redundant.

#### 3.4 Per-plugin per-device sub-tabs (Log Viewer's choice)

Log Viewer wants a side-by-side comparison affordance; UUID Grouping
probably doesn't. The DSL exposes a `tabs(controller, master, detail)`
verb plugins can use to host their own sub-tab strip. For multi-device:

- A master "All devices" sub-tab is always present (default landing).
- Each pinned device gets its own sub-tab. Only *pinned* devices get tabs —
  plugging a phone in to charge doesn't clutter the strip.
- Sub-tab labels: `[colour dot] Pixel 8` (or compact mode: `[dot] 3hLA…`).
- Active sub-tab gets a white pill background; inactive ones are
  transparent. Same pattern as the top tab bar.
- Cmd+1, Cmd+2, … switches sub-tabs by index. Cmd+0 returns to "All
  devices."

When 5+ device tabs are pinned, the strip enters **compact mode** —
labels collapse to colour dot + 4-char serial prefix, full name on hover.

#### 3.5 Filter integration

The filter bar (`tag:Activity level:W ...`) gains a `device:<name>` clause.
Inside a per-device sub-tab the clause is *implicit*; the user's explicit
filter ANDs on top. Autocomplete on the active connected device list,
quoted values for spaces (`device:"Pixel 8"`).

### What we want from the designer for multi-device

High-fidelity mockups for these scenarios:

1. Empty state — no devices, the pill in the status bar, the popover when
   clicked.
2. Single-device, live — pill, Log Viewer's master view, master sub-tab
   active.
3. Two devices, one pinned, "All devices" active — pill, Log Viewer's
   master view with row stripes + chips.
4. Two devices, both pinned, the second is active — pill, Log Viewer with
   the second sub-tab selected (no stripe in the scoped view).
5. Reconnecting state — pill showing one live + one reconnecting, the
   pinned reconnecting tab with its dashed border + spinner overlay.
6. Compact mode — 6 pinned devices, the tab strip in collapsed form.
7. The popover menu in detail — connected, disconnected, and renamed
   devices.

---

## 4. Feature 2 — Sessions

### The user problem

Logs accumulate forever. After a few days of debugging, the database has
millions of lines from unrelated work and the live view is noisy. The user
wants to:

1. End the current debugging session — "this is what I was working on," save
   it, and start a clean slate.
2. Browse old sessions on demand without having them clutter the live view.
3. Compare across sessions ("did this happen before my fix?").
4. Share a session with a teammate (zip file).

The chosen design: **live `logs.db` only ever contains the current session.**
Ending a session snapshots the current state to disk and resets to empty.
Past sessions are browseable on explicit demand, never mixed into the live
view. Live ingest never stops — `adb` keeps writing to the live DB even
while the user browses an archive; they just aren't looking at it.

### Components to design

#### 4.1 Status-bar session pill (always visible, primary surface)

Sits next to the device pill from feature 1. Format:
`⏱ <session name> · <duration> · <line count> [▾]`. Examples:

- `⏱ untitled · 3 min · 1,247 lines`
- `⏱ bug-repro · 12 min · 1,247,331 lines`
- `⏱ New session · 0 lines` (first launch)

Click `[▾]` → a popover opens with:

- Current session details: name (editable inline), started timestamp,
  duration, line count, size on disk, list of contributing devices.
- A primary `End & Start New Session` button.
- A "Recent archives" list (3–5 most recent) with date / line count.
- A `Browse all archives…` link that opens the Sessions sidebar plugin.

#### 4.2 Sessions sidebar plugin (full management surface)

A new entry in the sidebar called **Sessions**. Its content area has two
sections:

##### Current section

The active session, in a card-like format:

- Name (editable, large)
- Started: timestamp + relative ("12 min ago")
- Lines / size on disk
- Contributing devices (chips with colour dots, from feature 1)
- Two primary actions side by side: `End & Archive` (default) and
  `Cancel & Reset` (escape hatch — drop the live DB without archiving,
  for when the user just wants to start over).

##### Archived sessions section

A searchable, sortable list. Each row:

- Name (large) and a one-line description if present
- Date range: `2026-05-04 22:18 → 23:47` and duration `1h 29m`
- Lines, size, device count
- Per-row actions on hover or always-visible: `Open`, `Rename`, `Export…`,
  `Delete`. "Export" zips the archive directory for sharing/backup.

Empty state: "Your first ended session will appear here."

#### 4.3 Archive sub-tabs (read-only viewing)

Clicking `Open` on an archive opens a read-only sub-tab inside the Sessions
plugin. The sub-tab reuses the Log Viewer UI but binds to the archive's
`logs.db` instead of the live one. Visual differentiation:

- A banner across the top: `⚠ Viewing archive · <name> · <date range> · <line count>`
  in a slightly tinted strip (suggested: very pale amber).
- Log row text rendered in italic or with slight desaturation, to signal
  "frozen, not live."
- The vertical scrollbar still works; filter still works; selection +
  copy still works. The user can't *append* — there's no live ingest into
  the archive.

Multiple archives can be open in parallel as sibling sub-tabs (same
`tabController` pattern as UUID Grouping's drill-down).

#### 4.4 "End & Start New" confirm dialog

Modal dialog when the user clicks `End & Start New Session`:

- Heading: "End current session?"
- Preview: how much will be archived (`1,247,331 lines · 187 MB`).
- A name field, pre-filled with the current session's name (or
  `2026-05-05 14:32` if untitled). User edits if they care; this is the
  one moment the name actually matters.
- Optional one-line description field.
- Reminder text: "ADB keeps streaming. The new session starts immediately."
- Buttons: `Cancel` (secondary), `End & New` (primary).

#### 4.5 First-launch / empty state

When no sessions have been ended yet:

- Pill: `⏱ New session · 0 lines`.
- Sessions plugin's Current section: "This is your first session. Start
  streaming logs and end the session when you're ready to archive."
- Sessions plugin's Archived list: "None yet."

### What we want from the designer for sessions

High-fidelity mockups for these scenarios:

1. First launch — pill + Sessions plugin's empty state.
2. Active session, mid-debug — pill expanded, popover showing details + recent
   archives + End button.
3. Sessions plugin, current + 4 archived sessions — full management view.
4. End & Start New confirm dialog.
5. Loaded archive in a sub-tab — banner + dimmed log rows.

---

## 5. How multi-device and sessions compose

The two features are **orthogonal**:

- An archive can contain logs from multiple devices; the archive's metadata
  shows "2 devices" and the archive viewer applies device filtering /
  sub-tabs the same way the live viewer does.
- A live session can span multiple devices joining and leaving; logs from
  every device that connected during the session are part of that session's
  archive when it ends.
- The two pills (device + session) sit next to each other in the status bar.
  They never need to overlap or share state.

The status bar at the bottom now has three things, left-to-right:

1. **Device pill** — `● 2 devices: Pixel 8 · Emu Pix 7 [▾]`
2. **Session pill** — `⏱ bug-repro · 12 min · 1.2M lines [▾]`
3. **Activity indicator** — total ingested lines + ingestion status
   ("Streaming…", "Idle", "Disconnected").

The designer should figure out the right rhythm and spacing between them
without making the status bar feel cluttered.

---

## 6. Constraints summary

- **Data-dense.** The app shows huge volumes of monospace text. Designs
  should not compete with that for attention; chrome stays understated.
- **Plugin-aware.** Built-in and `.kts`-authored plugins share the design
  language. New features should be expressible (or at least compatible)
  with the DSL — anything that forces every plugin to grow custom UI is a
  red flag. The 13 DSL verbs and the `LogHoundDesign` token list constrain
  what plugin UI can render today; new tokens or verbs we add should be
  motivated by something the designer wants to express.
- **macOS first, Linux/Windows compatible.** Native menu bar on macOS;
  in-window menu bar on the others. Window chrome is platform-native.
- **No animation that blocks input.** Pulses on the live indicator and the
  reconnecting spinner are fine; full-frame transitions when switching
  tabs are not — the user is fast-clicking through devices.
- **Colourblind-safe.** Every colour-coded affordance has a text or shape
  equivalent (the device chip alongside the colour stripe, the connection-
  state shape difference alongside the dot colour).
- **Light theme today.** Dark theme is in scope to design *for*, but isn't
  shipping in the same slice. Provide both palettes if you can; we'll
  ship light first.

---

## 7. Deliverables we'd love

- Hi-fi mockups for every scenario listed under each feature.
- A short "design rationale" doc for the choices that aren't obvious from
  the brief.
- Source files (Figma preferred) so we can iterate.
- A device colour palette of 6 well-differentiated tints that work for
  colour-blind users (we currently have a placeholder; an actual designed
  palette is welcome).

If you want to push back on any of the decisions in this doc — especially
"global pill drives every plugin" vs alternatives — that's exactly what
we'd hope a designer would do. The brief is a starting point, not a
finished spec.

---

## 8. Where to read for context

- [`behavior.spec.md`](behavior.spec.md) — what the app does today, end to end.
- [`plugins.spec.md`](plugins.spec.md) — the DSL surface and visual tokens.
- [`coding.spec.md`](coding.spec.md) — module structure (skim only).
- [GitHub issues #26 (multi-device) and #21 (sessions)](https://github.com/roideuniverse/LogHound/issues)
  — the current state of the spec for those features. This brief is the
  latest design framing; the issues hold implementation notes.
