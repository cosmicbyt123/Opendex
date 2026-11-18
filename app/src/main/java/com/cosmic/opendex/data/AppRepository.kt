package com.cosmic.opendex.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val cachedApps = mutableListOf<AppModel>()

    suspend fun loadInstalledApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = packageManager.queryIntentActivities(launcherIntent, 0)
        val loadedApps = mutableListOf<AppModel>()

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            // Skip OpenDeX self to avoid recursive launcher inside launcher
            if (packageName == context.packageName) continue

            val activityName = resolveInfo.activityInfo.name
            val label = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)
            val isSystemApp = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            loadedApps.add(
                AppModel(
                    packageName = packageName,
                    activityName = activityName,
                    label = label,
                    icon = icon,
                    isSystemApp = isSystemApp
                )
            )
        }

        // Sort alphabetically by label
        loadedApps.sortBy { it.label.lowercase() }

        synchronized(cachedApps) {
            cachedApps.clear()
            cachedApps.addAll(loadedApps)
        }

        loadedApps
    }

    fun searchApps(query: String): List<AppModel> {
        if (query.isBlank()) return cachedApps
        val lower = query.lowercase().trim()
        return cachedApps.filter { it.label.lowercase().contains(lower) || it.packageName.lowercase().contains(lower) }
    }

    fun getCachedApps(): List<AppModel> = cachedApps.toList()
}
