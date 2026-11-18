package com.cosmic.opendex

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cosmic.opendex.data.AppModel
import com.cosmic.opendex.data.AppRepository
import com.cosmic.opendex.desktop.AppGridAdapter
import com.cosmic.opendex.desktop.DesktopPreferences
import com.cosmic.opendex.desktop.WallpaperManager
import com.cosmic.opendex.display.DisplayDetectionManager
import com.cosmic.opendex.display.DisplayInfoModel
import com.cosmic.opendex.window.ActiveTask
import com.cosmic.opendex.window.FreeformManager
import com.cosmic.opendex.window.RunningTasksAdapter
import com.cosmic.opendex.window.SnapPosition
import com.cosmic.opendex.window.WindowDecoratorManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "OpenDeX:Main"

    private lateinit var appRepository: AppRepository
    private lateinit var desktopPreferences: DesktopPreferences
    private lateinit var wallpaperManager: WallpaperManager
    private lateinit var displayDetectionManager: DisplayDetectionManager
    private lateinit var windowDecoratorManager: WindowDecoratorManager

    private lateinit var desktopAdapter: AppGridAdapter
    private lateinit var startMenuAdapter: AppGridAdapter
    private lateinit var runningTasksAdapter: RunningTasksAdapter

    private lateinit var desktopWallpaper: ImageView
    private lateinit var desktopIconGrid: RecyclerView
    private lateinit var startMenuCard: CardView
    private lateinit var startMenuButton: FrameLayout
    private lateinit var startMenuAppGrid: RecyclerView
    private lateinit var searchAppsInput: EditText
    private lateinit var allAppsCountText: TextView
    private lateinit var trayBatteryText: TextView
    private lateinit var trayDisplayIcon: ImageView
    private lateinit var taskbar: LinearLayout
    private lateinit var runningTasksList: RecyclerView

    private val activeTasks = mutableListOf<ActiveTask>()
    private var allLoadedApps = listOf<AppModel>()

    private val wallpaperPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            lifecycleScope.launch {
                val success = wallpaperManager.saveCustomWallpaper(selectedUri, desktopWallpaper)
                if (success) {
                    Toast.makeText(this@MainActivity, "Custom wallpaper applied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load custom wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val batteryPct = (level * 100 / scale.toFloat()).toInt()
                    trayBatteryText.text = "$batteryPct%"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appRepository = AppRepository(this)
        desktopPreferences = DesktopPreferences(this)
        wallpaperManager = WallpaperManager(this)

        bindViews()
        setupWindowDecorators()
        setupDisplayDetection()
        setupAdapters()
        setupListeners()
        wallpaperManager.applyWallpaper(desktopWallpaper)
        loadApplications()
    }

    private fun bindViews() {
        desktopWallpaper = findViewById(R.id.desktopWallpaper)
        desktopIconGrid = findViewById(R.id.desktopIconGrid)
        startMenuCard = findViewById(R.id.startMenuCard)
        startMenuButton = findViewById(R.id.startMenuButton)
        startMenuAppGrid = findViewById(R.id.startMenuAppGrid)
        searchAppsInput = findViewById(R.id.searchAppsInput)
        allAppsCountText = findViewById(R.id.allAppsCountText)
        trayBatteryText = findViewById(R.id.trayBatteryText)
        trayDisplayIcon = findViewById(R.id.trayDisplayIcon)
        taskbar = findViewById(R.id.taskbar)
        runningTasksList = findViewById(R.id.runningTasksList)
    }

    private fun setupWindowDecorators() {
        windowDecoratorManager = WindowDecoratorManager(
            context = this,
            onSnap = { task, snapPosition ->
                snapActiveTask(task, snapPosition)
            },
            onMinimize = { task ->
                minimizeActiveTask(task)
            },
            onMaximizeToggle = { task ->
                toggleMaximizeActiveTask(task)
            },
            onClose = { task ->
                closeActiveTask(task)
            },
            onBoundsMoved = { task, newBounds ->
                task.bounds = newBounds
                task.snapPosition = SnapPosition.NONE
                task.isMaximized = false
                FreeformManager.launchAppInFreeform(
                    context = this,
                    app = task.app,
                    targetDisplayId = displayDetectionManager.activeTargetDisplayId,
                    customBounds = newBounds
                )
                refreshRunningTasks()
            }
        )
    }

    private fun setupDisplayDetection() {
        displayDetectionManager = DisplayDetectionManager(
            context = this,
            onDisplayConnected = { info ->
                Toast.makeText(
                    this,
                    "External Display: ${info.name}\n${info.resolutionString}",
                    Toast.LENGTH_LONG
                ).show()
                trayDisplayIcon.visibility = View.VISIBLE
            },
            onDisplayDisconnected = { displayId ->
                Toast.makeText(this, "External Display disconnected (#$displayId)", Toast.LENGTH_SHORT).show()
                if (!displayDetectionManager.hasExternalDisplay()) {
                    trayDisplayIcon.visibility = View.GONE
                }
            },
            onDisplayChanged = { info ->
                Log.d(TAG, "Display #${info.displayId} configuration changed: ${info.resolutionString}")
            }
        )

        // Check if external displays are already connected
        if (displayDetectionManager.hasExternalDisplay()) {
            trayDisplayIcon.visibility = View.VISIBLE
        }

        trayDisplayIcon.setOnClickListener {
            showDisplayRoutingDialog()
        }
    }

    private fun setupAdapters() {
        // Desktop Grid (Landscape canvas: 6 columns, Portrait: 4 columns)
        val desktopColumns = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
        desktopIconGrid.layoutManager = GridLayoutManager(this, desktopColumns)

        desktopAdapter = AppGridAdapter(
            onAppClick = { app -> launchApplication(app) },
            onAppLongClick = { app, v -> showDesktopAppContextMenu(app, v) }
        )
        desktopIconGrid.adapter = desktopAdapter

        // Start Menu Drawer Grid
        startMenuAppGrid.layoutManager = GridLayoutManager(this, 4)
        startMenuAdapter = AppGridAdapter(
            onAppClick = { app ->
                toggleStartMenu(false)
                launchApplication(app)
            },
            onAppLongClick = { app, v -> showStartMenuAppContextMenu(app, v) }
        )
        startMenuAppGrid.adapter = startMenuAdapter

        // Taskbar Running Tasks
        runningTasksList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        runningTasksAdapter = RunningTasksAdapter(
            onTaskClick = { task ->
                if (task.isMinimized) {
                    restoreActiveTask(task)
                } else {
                    minimizeActiveTask(task)
                }
            },
            onTaskClose = { task ->
                closeActiveTask(task)
            },
            onTaskLongClick = { task, v ->
                showRunningTaskContextMenu(task, v)
            }
        )
        runningTasksList.adapter = runningTasksAdapter
    }

    private fun setupListeners() {
        startMenuButton.setOnClickListener {
            toggleStartMenu()
        }

        // Tap wallpaper to close start menu
        desktopWallpaper.setOnClickListener {
            toggleStartMenu(false)
        }

        // Right-click or long-press on wallpaper opens desktop context menu
        desktopWallpaper.setOnLongClickListener {
            showDesktopBackgroundContextMenu(it)
            true
        }

        desktopWallpaper.setOnGenericMotionListener { v, event ->
            if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                event.buttonState == MotionEvent.BUTTON_SECONDARY
            ) {
                showDesktopBackgroundContextMenu(v)
                true
            } else {
                false
            }
        }

        searchAppsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val filtered = appRepository.searchApps(query)
                startMenuAdapter.submitList(filtered)
            }
        })

        findViewById<View>(R.id.desktopSettingsBtn).setOnClickListener {
            showDesktopSettingsDialog()
        }
    }

    private fun loadApplications() {
        lifecycleScope.launch {
            allLoadedApps = appRepository.loadInstalledApps()
            startMenuAdapter.submitList(allLoadedApps)
            allAppsCountText.text = "${allLoadedApps.size} Applications"

            // Load or initialize desktop pins
            val pinnedComponentNames = desktopPreferences.getPinnedApps().toMutableList()
            if (pinnedComponentNames.isEmpty()) {
                // First run: automatically pin top core applications
                val defaultPins = allLoadedApps.take(16).map { it.componentNameString }
                desktopPreferences.savePinnedApps(defaultPins)
                pinnedComponentNames.addAll(defaultPins)
            }

            refreshDesktopPins(pinnedComponentNames)
            Log.i(TAG, "Loaded ${allLoadedApps.size} apps, ${pinnedComponentNames.size} pinned to desktop")
        }
    }

    private fun refreshDesktopPins(pinnedComponents: List<String>? = null) {
        val pins = pinnedComponents ?: desktopPreferences.getPinnedApps()
        val pinnedAppModels = pins.mapNotNull { cmp ->
            allLoadedApps.find { it.componentNameString == cmp }
        }
        desktopAdapter.submitList(pinnedAppModels)
    }

    fun launchApplication(app: AppModel, snap: SnapPosition = SnapPosition.CENTER_FLOAT) {
        val displayId = displayDetectionManager.activeTargetDisplayId
        val taskbarHeight = taskbar.height.coerceAtLeast(100)

        val bounds = if (snap != SnapPosition.NONE && snap != SnapPosition.CENTER_FLOAT) {
            FreeformManager.calculateSnapBounds(this, snap, taskbarHeight, displayId)
        } else {
            FreeformManager.calculateDefaultWindowBounds(this, displayId, taskbarHeight)
        }

        val success = FreeformManager.launchAppInFreeform(
            context = this,
            app = app,
            targetDisplayId = displayId,
            customBounds = bounds
        )

        if (success) {
            var existing = activeTasks.find { it.id == app.componentNameString }
            if (existing != null) {
                existing.bounds = bounds
                existing.snapPosition = snap
                existing.isMinimized = false
                existing.isMaximized = (snap == SnapPosition.FULLSCREEN)
                existing.lastActiveTime = System.currentTimeMillis()
            } else {
                existing = ActiveTask(
                    app = app,
                    bounds = bounds,
                    snapPosition = snap,
                    isMinimized = false,
                    isMaximized = (snap == SnapPosition.FULLSCREEN)
                )
                activeTasks.add(0, existing)
            }

            refreshRunningTasks()
            windowDecoratorManager.showOrUpdateDecorator(existing)
        }
    }

    private fun snapActiveTask(task: ActiveTask, snapPosition: SnapPosition) {
        val taskbarHeight = taskbar.height.coerceAtLeast(100)
        val displayId = displayDetectionManager.activeTargetDisplayId
        val newBounds = FreeformManager.snapApp(this, task.app, snapPosition, taskbarHeight, displayId)

        if (snapPosition != SnapPosition.FULLSCREEN) {
            task.lastRestoredBounds = Rect(newBounds)
        }
        task.bounds = newBounds
        task.snapPosition = snapPosition
        task.isMaximized = (snapPosition == SnapPosition.FULLSCREEN)
        task.isMinimized = false

        refreshRunningTasks()
        windowDecoratorManager.showOrUpdateDecorator(task)
    }

    private fun toggleMaximizeActiveTask(task: ActiveTask) {
        if (task.isMaximized) {
            val restored = task.lastRestoredBounds ?: FreeformManager.calculateDefaultWindowBounds(
                this,
                displayDetectionManager.activeTargetDisplayId,
                taskbar.height
            )
            FreeformManager.launchAppInFreeform(
                context = this,
                app = task.app,
                targetDisplayId = displayDetectionManager.activeTargetDisplayId,
                customBounds = restored
            )
            task.bounds = restored
            task.isMaximized = false
            task.snapPosition = SnapPosition.CENTER_FLOAT
        } else {
            task.lastRestoredBounds = Rect(task.bounds)
            snapActiveTask(task, SnapPosition.FULLSCREEN)
        }
        refreshRunningTasks()
        windowDecoratorManager.showOrUpdateDecorator(task)
    }

    private fun minimizeActiveTask(task: ActiveTask) {
        task.isMinimized = true
        windowDecoratorManager.hideDecorator(task.id)
        refreshRunningTasks()

        // Bring OpenDeX home desktop back to the top
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun restoreActiveTask(task: ActiveTask) {
        task.isMinimized = false
        FreeformManager.launchAppInFreeform(
            context = this,
            app = task.app,
            targetDisplayId = displayDetectionManager.activeTargetDisplayId,
            customBounds = task.bounds
        )
        refreshRunningTasks()
        windowDecoratorManager.showOrUpdateDecorator(task)
    }

    private fun closeActiveTask(task: ActiveTask) {
        windowDecoratorManager.removeDecorator(task.id)
        activeTasks.removeAll { it.id == task.id }
        refreshRunningTasks()
    }

    private fun refreshRunningTasks() {
        runningTasksAdapter.submitList(activeTasks.toList())
    }

    private fun toggleStartMenu(forceState: Boolean? = null) {
        val shouldOpen = forceState ?: (startMenuCard.visibility != View.VISIBLE)
        if (shouldOpen) {
            startMenuCard.visibility = View.VISIBLE
            searchAppsInput.text.clear()
            searchAppsInput.requestFocus()
        } else {
            startMenuCard.visibility = View.GONE
            searchAppsInput.clearFocus()
        }
    }

    private fun showStartMenuAppContextMenu(app: AppModel, anchor: View): Boolean {
        val popup = PopupMenu(this, anchor)
        val isPinned = desktopPreferences.isPinned(app.componentNameString)

        popup.menu.add(0, 1, 0, if (isPinned) getString(R.string.unpin_from_desktop) else getString(R.string.pin_to_desktop))
        popup.menu.add(0, 2, 1, "Launch Floating Window")
        popup.menu.add(0, 3, 2, getString(R.string.snap_left))
        popup.menu.add(0, 4, 3, getString(R.string.snap_right))
        popup.menu.add(0, 5, 4, getString(R.string.app_info))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (isPinned) {
                        desktopPreferences.unpinApp(app.componentNameString)
                        Toast.makeText(this, "Unpinned ${app.label} from Desktop", Toast.LENGTH_SHORT).show()
                    } else {
                        desktopPreferences.pinApp(app.componentNameString)
                        Toast.makeText(this, "Pinned ${app.label} to Desktop", Toast.LENGTH_SHORT).show()
                    }
                    refreshDesktopPins()
                    true
                }
                2 -> {
                    toggleStartMenu(false)
                    launchApplication(app, SnapPosition.CENTER_FLOAT)
                    true
                }
                3 -> {
                    toggleStartMenu(false)
                    launchApplication(app, SnapPosition.LEFT_HALF)
                    true
                }
                4 -> {
                    toggleStartMenu(false)
                    launchApplication(app, SnapPosition.RIGHT_HALF)
                    true
                }
                5 -> {
                    openAppInfo(app)
                    true
                }
                else -> false
            }
        }
        popup.show()
        return true
    }

    private fun showDesktopAppContextMenu(app: AppModel, anchor: View): Boolean {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.unpin_from_desktop))
        popup.menu.add(0, 2, 1, "Launch Floating Window")
        popup.menu.add(0, 3, 2, getString(R.string.snap_left))
        popup.menu.add(0, 4, 3, getString(R.string.snap_right))
        popup.menu.add(0, 5, 4, getString(R.string.app_info))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    desktopPreferences.unpinApp(app.componentNameString)
                    refreshDesktopPins()
                    Toast.makeText(this, "Unpinned ${app.label}", Toast.LENGTH_SHORT).show()
                    true
                }
                2 -> {
                    launchApplication(app, SnapPosition.CENTER_FLOAT)
                    true
                }
                3 -> {
                    launchApplication(app, SnapPosition.LEFT_HALF)
                    true
                }
                4 -> {
                    launchApplication(app, SnapPosition.RIGHT_HALF)
                    true
                }
                5 -> {
                    openAppInfo(app)
                    true
                }
                else -> false
            }
        }
        popup.show()
        return true
    }

    private fun showRunningTaskContextMenu(task: ActiveTask, anchor: View): Boolean {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.snap_left))
        popup.menu.add(0, 2, 1, getString(R.string.snap_right))
        popup.menu.add(0, 3, 2, if (task.isMaximized) getString(R.string.restore) else getString(R.string.maximize))
        popup.menu.add(0, 4, 3, "Center Floating")
        popup.menu.add(0, 5, 4, getString(R.string.minimize))
        popup.menu.add(0, 6, 5, getString(R.string.close))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { snapActiveTask(task, SnapPosition.LEFT_HALF); true }
                2 -> { snapActiveTask(task, SnapPosition.RIGHT_HALF); true }
                3 -> { toggleMaximizeActiveTask(task); true }
                4 -> { snapActiveTask(task, SnapPosition.CENTER_FLOAT); true }
                5 -> { minimizeActiveTask(task); true }
                6 -> { closeActiveTask(task); true }
                else -> false
            }
        }
        popup.show()
        return true
    }

    private fun showDesktopBackgroundContextMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.change_wallpaper))
        popup.menu.add(0, 2, 1, getString(R.string.desktop_settings))
        popup.menu.add(0, 3, 2, "Refresh Applications")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showWallpaperPickerDialog()
                    true
                }
                2 -> {
                    showDisplayRoutingDialog()
                    true
                }
                3 -> {
                    loadApplications()
                    Toast.makeText(this, "Refreshed applications", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showWallpaperPickerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wallpaper_picker, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.presetWallpaper0).setOnClickListener {
            wallpaperManager.applyPreset(0, desktopWallpaper)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.presetWallpaper1).setOnClickListener {
            wallpaperManager.applyPreset(1, desktopWallpaper)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.presetWallpaper2).setOnClickListener {
            wallpaperManager.applyPreset(2, desktopWallpaper)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.presetWallpaper3).setOnClickListener {
            wallpaperManager.applyPreset(3, desktopWallpaper)
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnPickCustomWallpaper).setOnClickListener {
            dialog.dismiss()
            wallpaperPickerLauncher.launch("image/*")
        }

        dialog.show()
    }

    private fun showDisplayRoutingDialog() {
        val displays = displayDetectionManager.getConnectedDisplays()
        val items = displays.map {
            "${it.name} [ID: ${it.displayId}]\n${it.resolutionString}" +
                    if (it.displayId == displayDetectionManager.activeTargetDisplayId) "  ★ (Active Target)" else ""
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.desktop_settings))
            .setItems(items) { _, which ->
                val selected = displays[which]
                displayDetectionManager.setTargetDisplay(selected.displayId)
                Toast.makeText(
                    this,
                    "Freeform apps will target: ${selected.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton("System Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open Display Settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Overlay Permission") { _, _ ->
                if (!windowDecoratorManager.canDrawOverlays()) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Window overlay permission already granted!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDesktopSettingsDialog() {
        showDisplayRoutingDialog()
    }

    private fun openAppInfo(app: AppModel) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open App Info", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Physical Keyboard Win/Super Key opens Start Menu
        if (keyCode == KeyEvent.KEYCODE_META_LEFT ||
            keyCode == KeyEvent.KEYCODE_META_RIGHT ||
            keyCode == KeyEvent.KEYCODE_WINDOW
        ) {
            toggleStartMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        displayDetectionManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Battery receiver already unregistered")
        }
        displayDetectionManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        windowDecoratorManager.removeAllDecorators()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (startMenuCard.visibility == View.VISIBLE) {
            toggleStartMenu(false)
        } else {
            // Desktop environment keeps desktop alive on back press
        }
    }
}
