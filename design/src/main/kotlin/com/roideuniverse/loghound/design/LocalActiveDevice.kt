package com.roideuniverse.loghound.design

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.roideuniverse.loghound.core.DeviceId

/**
 * The currently-active device scope, exposed to every plugin's `content()` via the host. `null`
 * means "All devices" — the default.
 *
 * Plugins that want their UI to scope to the active device read this and fold it into their
 * `LogFilter.deviceId` (the [LogFilter] takes precedence if explicitly set). Plugins that
 * intentionally aggregate across devices (e.g. UUID Grouping's master list) simply ignore this
 * local.
 *
 * The host (`App.kt`) wires this to a `MutableState<DeviceId?>` that the status-bar device pill
 * mutates on user action.
 *
 * Lives in `:design` rather than `:plugin-dsl` so built-in plugins (which don't depend on
 * plugin-dsl) and DSL plugins (which already pull in design transitively via `LogHoundDesign`) can
 * both reach it.
 */
val LocalActiveDevice: ProvidableCompositionLocal<DeviceId?> = compositionLocalOf { null }
