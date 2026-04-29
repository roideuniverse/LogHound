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

- `column { … }` — vertical layout
- `text(value)` — a string
- `list(items, key) { item -> … }` — a virtualized vertical list

That's the whole UI surface for now. Everything else (rows, text fields, modifiers, scrollbars,
selection, sub-tabs) gets added when a plugin asks for it.

---

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
