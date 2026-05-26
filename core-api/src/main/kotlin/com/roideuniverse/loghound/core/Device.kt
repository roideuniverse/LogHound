package com.roideuniverse.loghound.core

/**
 * Stable identity for a log source. For `adb` devices this is the serial
 * (`emulator-5554`, `R5CT123ABCD`); for file ingest it's a synthetic prefixed
 * id (e.g. `file:bug-repro.log`).
 *
 * Modeled as a value class to give the type system the win without paying for
 * a boxed string at runtime — log entries carry this on every row.
 */
@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * A log source as the rest of the app sees it. The [id] is the stable key
 * (`adb devices` serial or `file:...` synthetic); the [label] is the
 * human-friendly name a user has assigned, falling back to [id]'s value
 * when no rename exists.
 *
 * Labels live in `~/.loghound/state.json` keyed by id (multi-device issue #26
 * spec) so renames survive disconnect/reconnect.
 */
data class Device(
    val id: DeviceId,
    val label: String = id.value,
)
