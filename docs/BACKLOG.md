# LogHound Backlog

Lightweight tracker for things noted but not scheduled. Each entry: a one-line title,
the context that motivated it, and rough size. Promote items into GitHub issues when
they're ready to work on (drafts can live in `docs/issue-drafts/` first).

Conventions:
- **Size:** S (under an hour), M (afternoon), L (multi-session), XL (cross-cutting)
- **Tags** in italics — module, area, or theme

---

## Bugs

- **LogViewer scrollToItem race in tests** — `LogViewerE2eTest.typing_filter_…` fails
  with `performMeasureAndLayout called during measure layout` when `performTextInput`
  fires multiple filter changes back-to-back. Pre-existing; surfaced once the
  UuidGroupingE2eTest compile error was fixed. Three fix paths in the issue draft
  (requestScrollToItem / withFrameNanos / snapshotFlow). Real-app behavior unaffected.
  See [`docs/issue-drafts/log-viewer-scroll-race.md`](issue-drafts/log-viewer-scroll-race.md).
  *Size:* M *Tags:* compose-mp, log-viewer, tests

- **`LocalClipboardManager` deprecation** — `plugins/ui/log-viewer/.../PackageUidLookup.kt`
  uses the deprecated composition local; the suspending replacement is `LocalClipboard`.
  Build warning today, will be a hard error in some future Compose Multiplatform release.
  *Size:* S *Tags:* compose-mp, log-viewer

- **Plugin script-load failures are silent** — when a `.kts` plugin fails to compile
  under the script host (missing import, type error, runtime exception during
  the `plugin { }` builder), the host writes `[plugin-script] foo.kts failed to load:`
  to stderr and silently drops the plugin. Users running the bundled native app
  never see the terminal. Surface load failures as a visible "Plugin failed: foo"
  entry in the sidebar with the error text in the body.
  *Size:* M *Tags:* plugin-dsl, ux, error-surfacing

---

## Enhancements

- **UUID Grouping backfill: cut per-row allocation** — `UuidGroupingPlugin.backfill()`
  hydrates a full `LogEntry` per row scanned (id, timestamp, pid, tid, priority, tag,
  message, packageName). At 6M rows this is the dominant source of GC churn during
  the one-time scan (~800 MB working set observed). Cheap fix: add
  `selectIdAndMessageAfter(afterId, limit)` projecting only the two columns the regex
  needs, plus `WHERE message LIKE '%-%'` to skip rows that can't contain a UUID.
  Should drop per-row alloc by 60–70% with zero architectural change.
  *Size:* S *Tags:* uuid-grouping, performance, sql

- **UUID Grouping backfill: SQL-only via UDF + ATTACH** — bigger version of the above.
  Register a JDBC user-defined function on the SQLite connection that returns matched
  UUIDs from a message; ATTACH `logs.db` onto `uuid-grouping.db` so a single
  `INSERT INTO uuids SELECT …` can run server-side without hydrating any Kotlin
  objects. ~10× memory reduction, but introduces UDFs and cross-DB attaches that
  no other plugin uses today. Wait until a second consumer would benefit.
  *Size:* L *Tags:* uuid-grouping, performance, sql, architecture

- **Migrate Compose deps to `libs.versions.toml`** — replace `compose.runtime`,
  `compose.foundation`, `compose.material3`, `compose.ui`, and `compose.desktop.uiTestJUnit4`
  inline references with catalog entries. Avoids IDE warnings about string-style
  accessors, keeps Compose versions in one file. `compose.desktop.currentOs` stays
  as-is (it resolves the platform-specific desktop bundle at apply time).
  Catalog entries already added; build files not yet migrated.
  *Size:* S *Tags:* gradle, dx

- **DSL: `padding` modifier verb** — empty / loading states in detail tabs sit
  flush-left because the DSL has no per-element padding. Today scripts wrap in a
  `section { }` (which forces toolbar styling) or eat the alignment. A
  `padding(horizontal, vertical) { … }` wrapping verb would let scripts pad an
  arbitrary subtree without inheriting section's surface.
  *Size:* S *Tags:* plugin-dsl, ux

- **DSL: `loadOlder` callback for `list`** — built-in plugins fire a callback when
  the user scrolls within ~10 rows of the top, paginating older entries on demand.
  The DSL `list` verb has no equivalent. Adding it as a `list(items, key,
  onScrolledNearTop) { … }` parameter would let scripts replicate Log Viewer's
  scroll-up history fetch.
  *Size:* M *Tags:* plugin-dsl, scrolling

- **DSL: tail-following auto-scroll** — Log Viewer auto-scrolls to the latest entry
  when the user is parked at the bottom and a new entry arrives. The DSL `list`
  doesn't expose any scroll control, so script-based log viewers can't replicate
  this. Either add an `autoScrollToBottom` flag on `list`, or expose a
  `listController` analogous to `tabController` with `requestScrollToBottom()`,
  `requestScrollToItem(idx)`, and a `firstVisibleIndex` state.
  *Size:* M *Tags:* plugin-dsl, scrolling

- **Detail tab pagination in scripted plugins** — example UUID Grouping `.kts` queries
  `repo.query(filter = LogFilter(textSearch = uuid), limit = 500)` once per opened
  tab. Built-in plugin paginates older entries on scroll-up via `UuidDetailController`
  with `loadOlder()`. Once the DSL grows the `loadOlder` callback above, port the
  example script to use it.
  *Size:* S *Tags:* plugin-dsl, examples

- **Hello DSL plugin: convert to `.kts` example or delete** — `app/.../HelloDsl.kt`
  was the proof-of-concept demo. Now that we have a real script-loaded plugin, the
  in-tree Hello DSL is sidebar clutter. Either delete (UUID Grouping script is the
  better demo) or move it to `examples/plugins/hello.kts` as a minimal starter
  template that authors can copy.
  *Size:* S *Tags:* plugin-dsl, cleanup

- **Window state persistence** — sidebar-visible toggle, currently active tab,
  window size and position all reset on launch. A small SQLite or JSON file in
  `~/.loghound/state.json` would persist these. Avoid heavy state libraries; a
  10-line saved-on-close struct is enough.
  *Size:* S *Tags:* app, ux

- **Plugin enable/disable toggle** — a user can't temporarily disable a loaded
  `.kts` plugin without removing the file. A right-click menu on the sidebar
  entry → "Disable" → re-enabled later → makes triage easier when one plugin
  misbehaves. Can be disk-state in the same `~/.loghound/state.json` as window
  state.
  *Size:* M *Tags:* plugin-dsl, ux

- **Logcat disconnect notification** — `LogcatDataPlugin` retries silently on
  device disconnect (USB unplug, adb server restart). User has no visual signal
  that the live stream stopped. Status bar text or a sidebar dot would help.
  Behaviour is already in `behavior.spec.md`; it's just not wired up.
  *Size:* M *Tags:* logcat, ux

- **CI on pull requests** — no `.github/workflows/` today. A minimal job that
  runs `:plugin-dsl:test :app:jvmTest --tests "…PluginScriptHostTest" :core-impl:test`
  on every PR would catch regressions in the DSL contract and the script-host
  loader without requiring a UI runner.
  *Size:* S *Tags:* ci, dx

- **In-app "Clear logs" menu item** — `File → Clear logs…` with a confirmation
  dialog. Deletes `~/.loghound/logs.db` (+ `-shm` / `-wal`) and offers a checkbox
  for "also wipe plugin state" that recursively clears `~/.loghound/plugins/*.db`
  (preserving `.kts` files). Should close the existing `LogDataStore` connection
  cleanly first, then reopen on a fresh schema. Today's workaround is
  `rm -f ~/.loghound/logs.db*` while the app is closed.
  *Size:* S *Tags:* app, ux

---

## Features

- **Plugin author skill doc** — once `.kts` drop-in stabilises, ship a single-page
  Markdown skill doc that an AI agent can be given to author plugins. Should
  list every verb with one-line example, the `pluginDataDir()` convention, the
  `@file:DependsOn` setup for SQLite/Exposed plugins, and a worked clone of UUID
  Grouping. Defer until the verb surface stops changing weekly.
  *Size:* M *Tags:* plugin-dsl, docs

- **Plugin sandboxing** — today a `.kts` plugin runs with full JVM authority
  (file I/O, network, process spawning). Worth revisiting when there's any
  scenario where a plugin author isn't fully trusted by the user.
  *Size:* XL *Tags:* plugin-dsl, security

- **Hot reload of `.kts` plugins** — drop a file, see it without relaunch.
  Requires watching the directory and re-evaluating changed scripts safely
  (cancel old `data { }` coroutine, swap UI content). Nice-to-have; cold start
  is fast enough that a relaunch is barely noticeable today.
  *Size:* L *Tags:* plugin-dsl, dx

- **Multi-device support** — connect to more than one ADB device and label
  log streams per source. Touches `plugins/data/logcat`, schema (add
  `device_id` column), and Log Viewer filter syntax (`device:…`).
  *Size:* L *Tags:* logcat, schema

- **Detachable tabs into floating windows** — `behavior.spec.md` lists this as
  a future plugin shell capability: any tab can be detached by right-click or
  drag-out, and re-docked by closing the floating window. Touches `app/App.kt`
  and the tab management infrastructure.
  *Size:* L *Tags:* app, plugin-shell

- **Export filtered logs to file** — `behavior.spec.md` declares this as a
  Log Viewer "not yet implemented" action. Should write the currently visible
  filter result to a `.log` file in user-chosen location. Probably file picker
  via Compose Multiplatform's file dialog wrappers.
  *Size:* M *Tags:* log-viewer, export

- **Performance benchmark at scale targets** — `behavior.spec.md` declares
  ≥ 100K UUIDs / ≥ 50M lines for UUID Grouping and "tens of millions of lines
  without behavior changes" for the app overall. We have no automated check
  that we still hit those bars after each refactor. Add a benchmark suite that
  ingests a synthetic 50M-line corpus, measures backfill time, peak heap, and
  steady-state memory; record in CI artefacts.
  *Size:* L *Tags:* tests, performance

---

## Decided "no" (for context)

- **Bake Exposed into the host SDK** — would ship ~3–4 MB of JARs in the desktop
  bundle and pin a framework choice on plugin authors. Plugins use
  `@file:DependsOn` for whatever ORM they want; host stays neutral.
  Recorded in [`specs/plugins.spec.md`](../specs/plugins.spec.md).
