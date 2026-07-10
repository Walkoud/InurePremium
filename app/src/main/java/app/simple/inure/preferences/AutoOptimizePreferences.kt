package app.simple.inure.preferences

object AutoOptimizePreferences {

    private const val AUTO_OPTIMIZE_ENABLED = "auto_optimize_enabled"
    private const val AUTO_OPTIMIZE_NOTIFICATION = "auto_optimize_notification"

    fun isAutoOptimizeEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(AUTO_OPTIMIZE_ENABLED, false)
    }

    fun setAutoOptimizeEnabled(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(AUTO_OPTIMIZE_ENABLED, value).apply()
    }

    fun isNotificationDismissed(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(AUTO_OPTIMIZE_NOTIFICATION, false)
    }

    fun setNotificationDismissed(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(AUTO_OPTIMIZE_NOTIFICATION, value).apply()
    }
}
