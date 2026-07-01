package com.roideuniverse.loghound.core

interface DataPlugin {
    val id: String
    val name: String

    suspend fun run(repository: LogRepository)

    /**
     * Drop any derived state this plugin owns (e.g. its own SQLite file) so the next `run(...)`
     * starts fresh. Called by the host after the main `logs` store is wiped — either from a `File →
     * Clear logs…` action or from the Sessions plugin's "End & archive" flow — so that plugin
     * indexes don't end up referencing log IDs that no longer exist.
     *
     * Default is a no-op for plugins with no persistent state (logcat, file ingest, network
     * sources). Plugins that maintain their own derived data (UUID Grouping, future cross-stream
     * indexes) override to truncate their tables / delete their files.
     *
     * Safe to call when the plugin is idle or running; implementations should be re-entrant.
     */
    suspend fun clearStore() = Unit
}
