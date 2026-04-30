# LogHound — Plugin Spec

A plugin extends LogHound. It can read live and historical logs, do its own processing, and render
its own UI. A plugin is written as a single Kotlin file using a small DSL.

This spec is **iterative**. We add to it only when an actual plugin needs the capability — never
ahead of demand.

---

## Plugin shape

```kotlin
plugin {
    id = "my-grouper"
    name = "My Grouper"

    data { repo ->
        // Background work for the lifetime of the app.
    }

    ui {
        // What the plugin's tab renders.
        column {
            text("Hello, LogHound")
        }
    }
}
```

`id` is stable — used for the tab key and for the plugin's data directory. `name` is what the user
sees in the sidebar and the tab.

`data` and `ui` are both optional. A plugin with only `ui` is a pure view; a plugin with only
`data` is a background processor with no tab.

---

## Data side

`data { repo -> … }` runs once on app start, in a coroutine scoped to the app's lifetime.

Available inside the block:

- **`repo: LogRepository`** — same interface the built-in plugins use (`append`, `query`, `count`,
  `ingested`).
- **`pluginDataDir(): File`** — returns `~/.loghound/plugins/<id>/`. Created on first call. The
  plugin opens its own files inside (SQLite, JSON, anything it brings via `@file:DependsOn`).

If the block throws, the host logs the error and surfaces it in the plugin's tab. The rest of the
app keeps running.

---

## UI side

`ui { … }` describes the plugin's tab as a tree of **verbs**. Verbs are not `@Composable` — the
host renders them into Compose itself.

Verbs available today:

**Layout**
- `column { … }` — vertical stack
- `row { … }` — horizontal stack
- `section { … }` — toolbar-styled `Surface` with `theme.toolbarBackground` and inner padding;
  use it to group a search field, button, and status text
- `centered { … }` — fills the available space and centers its children; used for loading
  spinners and empty-state messages on detail tabs
- `weight(weight) { … }` — flex modifier inside a `row` or `column`; the wrapped subtree
  occupies a proportional share of the parent's free space
- `spacer(width = N, height = N)` — fixed-size gap, dp values

**Content**
- `text(value, style?)` — a string. Optional `style` for per-line styling (e.g. priority colors
  in a log row). Otherwise inherits from `theme.text` (or `theme.rowText` inside a list)
- `divider()` — themed horizontal rule
- `loading()` — circular progress spinner; pair with `centered` for a "loading…" panel
- `button(label, onClick)` — themed text button
- `textField(state, placeholder)` — single-line input bound to a `MutableState<String>`
- `clickable(onClick) { … }` — wraps its children so the subtree fires `onClick` when tapped

**Containers**
- `list(items, key) { item -> … }` — virtualized vertical list with auto-attached vertical
  scrollbar, drag-select-to-copy, and dividers between rows
- `tabs(controller, master, detail)` — host-managed tab strip; see below

The DSL grows when a real plugin asks for something that can't be expressed with these.

### Sub-tabs

`tabController(masterName)` returns a `TabController` that owns the open-tab list and the active
tab. Plugins manipulate it from data and UI blocks via `open(id, name)`, `close(id)`, and
`activate(id?)` (`null` = master).

The `tabs(controller, master, detail)` verb renders the controller as a tab strip plus content:

- `master: UiScope.() -> Unit` — content for the leftmost (always-present) tab.
- `detail: UiScope.(id) -> Unit` — content for whichever detail tab is active. Receives the open
  tab's `id`. Re-runs each time the active tab changes.

The host owns tab persistence within the session; nothing is durable across restarts unless the
plugin re-opens tabs from its own state.

---

## Theming

Every plugin renders against a `PluginTheme` — a flat record of colors and text styles (background,
dividers, row text, tab strip, text field, etc.) that the renderer reads via a Compose
`CompositionLocal`. `PluginTheme`'s defaults all read from a single `LogHoundDesign` design-token
object that built-in plugins also reference, so a script-loaded plugin and a hand-coded one share
the same palette and text styles unless either explicitly overrides.

`LogHoundDesign` exposes:

- `Colors.Background`, `Colors.Divider`, `Colors.RowDivider`, `Colors.ToolbarBackground`,
  `Colors.TabStripBackground`, `Colors.ActiveTabBackground`, `Colors.TextFieldBackground`,
  `Colors.TextFieldBorder`, `Colors.Placeholder`, `Colors.Secondary` (gray status), `Colors.Primary`
  (Material blue), `Colors.OnSurface` (near-black body)
- The Logcat priority palette: `Colors.PriorityVerbose / Debug / Info / Warn / Error / Fatal /
  Silent`
- Text styles: `Text.Status` (12sp gray), `Text.Row` (mono 12sp), `Text.Tab` (13sp black),
  `Text.TabClose` (11sp gray), `Text.Button` (13sp blue), `Text.Field` (13sp black)
- `colorFor(priority): Color` helper — returns the matching priority color, used by detail rows in
  log lists

The script host default-imports `LogHoundDesign`, so plugin authors can do
`text(line, style = LogHoundDesign.Text.Row.copy(color = LogHoundDesign.colorFor(entry.priority)))`
without imports.

Plugins that want different visuals override per-field:

```kotlin
plugin {
    id = "..."
    name = "..."
    theme {
        background = Color(0xFF101010)
        rowText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
    }
    ui { … }
}
```

The `theme { … }` builder copies the current theme, applies overrides, and replaces it. Authors can
also set `theme = PluginTheme(...)` directly if they prefer a copy-and-swap.

The theme covers every color and text style the renderer paints. Reaching pixel-parity with another
plugin means matching its theme, not adding new verbs.

## State between data and UI

`stateOf(initial)` returns a holder the data block writes to and the UI block reads from. Reads in
the UI block trigger recomposition when the value changes.

```kotlin
val items = stateOf<List<String>>(emptyList())

data { repo ->
    repo.ingested.collect { entry ->
        items.value = items.value + entry.message
    }
}

ui {
    list(items = items.value, key = { it }) { msg ->
        text(msg)
    }
}
```

---

## Where plugins live

**Today:** plugins are Kotlin source files in the repo, registered in `app/main.kt` like the
built-in plugins. The DSL is the API; loading from disk is not yet wired up.

**Later:** plugins drop into `~/.loghound/plugins/<id>.kts` and are loaded at app start by a
script host. Spec'd separately when we get there.

---

## Out of scope (for now)

- Loading `.kts` plugins from disk
- Sandboxing
- Hot reload
- Multiplatform plugins (DSL is JVM-only)
- A skill document for AI agents (only useful once disk-loading lands)

We will revisit each of these when a real need surfaces.
