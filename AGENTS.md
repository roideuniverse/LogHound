# LogHound

LogHound is a fast, memory-efficient Android logcat viewer built with Compose Multiplatform for Desktop. It handles GB-scale logs without memory pressure, replacing Android Studio's Logcat which crashes under heavy log volume.

## Specs

**Read the specs before making any changes.** They are the source of truth.

- `specs/behavior.spec.md` — what the app does from the user's perspective
- `specs/coding.spec.md` — how the code is structured and constrained

Behavior spec describes *what*. Coding spec describes *how*. They do not overlap. If something conflicts, ask before proceeding.

## Architecture

- Everything visual is a plugin, including the core log viewer
- The core is headless: input layer → ingester → SQLite storage → plugin framework
- Plugins are compiled-in Kotlin modules, not runtime-loaded
- Memory usage must stay under 250MB regardless of log volume

## Project structure

Multi-module Gradle project:

- `core-api/` — domain module. Interfaces, data models, plugin API contract only. No implementations, no UI.
- `app/` — DI/wiring layer. Assembles implementations, registers plugins, owns the window shell and tab management.
- Where implementations live is TBD — decided as architecture takes shape.

## Key constraints

- core has no UI dependencies
- Plugins depend on core, never on each other
- All database queries must be paginated — no unbounded result sets
- Parser errors skip and continue, never crash
- Plugin exceptions are caught by core — a crashing plugin does not take down the app
- Ingester runs on a background thread, never blocks the UI
- Memory is proportional to viewport, not dataset size

## Build and run

- Language: Kotlin
- UI: Compose Multiplatform for Desktop
- Database: SQLite
- Build: Gradle with Kotlin DSL
- JDK: 17+

## Development approach

- **Specs are the source of truth.** The code is a build artifact of the spec, not the other way around. At any point in time, it must be possible to delete the entire codebase and regenerate it from the specs alone.
- Whenever a decision is made — architecture, behavior, constraint, edge case — update the relevant spec first, then write or update code. Never let the code diverge from the spec silently.
- If something exists in the code but not the spec, add it to the spec. If the spec changes, the spec is authoritative.
- Read the relevant spec sections before implementing a feature
- When the spec is ambiguous, make a reasonable choice and note the assumption in the spec
- Prioritize memory efficiency and reliability over speed
- Test with large datasets — the app must behave identically with 1,000 lines and 10,000,000 lines
