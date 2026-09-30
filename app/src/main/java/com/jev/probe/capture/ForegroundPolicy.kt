package com.jev.probe.capture

data class AppWindow(val pkg: String, val layer: Int, val focused: Boolean)
object ForegroundPolicy {
    fun resolve(windows: List<AppWindow>, previous: String?): String? =
        windows.filter { it.pkg.isNotBlank() }.let { apps ->
            apps.filter { it.focused }.maxByOrNull { it.layer }?.pkg
                ?: apps.maxByOrNull { it.layer }?.pkg ?: previous
        }
}
