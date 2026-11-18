package com.cosmic.opendex

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioManager
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cosmic.opendex.data.AppModel
import com.cosmic.opendex.data.AppRepository
import com.cosmic.opendex.desktop.AppGridAdapter
import com.cosmic.opendex.desktop.DesktopPreferences
import com.cosmic.opendex.desktop.VirtualDesktop
import com.cosmic.opendex.desktop.VirtualDesktopManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val TAG = "OpenDeX:Main"

    private lateinit var appRepository: AppRepository
    private lateinit var desktopPreferences: DesktopPreferences
    private lateinit var wallpaperManager: WallpaperManager
    private lateinit var displayDetectionManager: DisplayDetectionManager
    private lateinit var windowDecoratorManager: WindowDecoratorManager
    private lateinit var virtualDesktopManager: VirtualDesktopManager

    private lateinit var desktopAdapter: AppGridAdapter
    private lateinit var startMenuAdapter: AppGridAdapter
    private lateinit var runningTasksAdapter: RunningTasksAdapter

    private lateinit var desktopWallpaper: ImageView
    private lateinit var desktopIconGrid: RecyclerView
    private lateinit var startMenuCard: CardView
    private lateinit var startMenuButton: FrameLayout
    private lateinit var navBackButton: FrameLayout
    private lateinit var taskViewButton: FrameLayout
    private lateinit var taskViewCard: CardView
    private lateinit var virtualDesktopsRow: LinearLayout
    private lateinit var startMenuAppGrid: RecyclerView
    private lateinit var searchAppsInput: EditText
    private lateinit var allAppsCountText: TextView
    private lateinit var trayBatteryText: TextView
    private lateinit var trayDisplayIcon: ImageView
    private lateinit var trayKeyboardBtn: ImageView
    private lateinit var brightnessDimmer: View
    private lateinit var taskbar: LinearLayout
    private lateinit var systemTray: LinearLayout
    private lateinit var runningTasksList: RecyclerView

    // Tablet Control Center
    private lateinit var controlCenterCard: CardView
    private lateinit var ccDateText: TextView
    private lateinit var ccSettingsBtn: ImageView
    private lateinit var pillWifi: LinearLayout
    private lateinit var pillBluetooth: LinearLayout
    private lateinit var pillRotation: LinearLayout
    private lateinit var pillDisplay: LinearLayout
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var ccBatteryPercent: TextView
    private lateinit var ccPowerBtn: ImageView

    private val activeTasks = mutableListOf<ActiveTask>()
    private var allLoadedApps = listOf<AppModel>()
    private var currentBatteryPct: Int = 100

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
                    currentBatteryPct = (level * 100 / scale.toFloat()).toInt()
                    trayBatteryText.text = "$currentBatteryPct%"
                    if (::ccBatteryPercent.isInitialized) {
                        ccBatteryPercent.text = "Battery: $currentBatteryPct%"
                    }
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
        virtualDesktopManager = VirtualDesktopManager()

        bindViews()
        setupWindowInsets()
        setupWindowDecorators()
        setupDisplayDetection()
        setupAdapters()
        setupListeners()
        setupControlCenter()
        setupVirtualDesktops()
        wallpaperManager.applyWallpaper(desktopWallpaper)
        loadApplications()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.desktopRoot)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            taskbar.setPadding(
                taskbar.paddingLeft,
                taskbar.paddingTop,
                taskbar.paddingRight,
                systemBars.bottom.coerceAtLeast(0)
            )
            insets
        }
    }

    private fun bindViews() {
        desktopWallpaper = findViewById(R.id.desktopWallpaper)
        desktopIconGrid = findViewById(R.id.desktopIconGrid)
        startMenuCard = findViewById(R.id.startMenuCard)
        startMenuButton = findViewById(R.id.startMenuButton)
        navBackButton = findViewById(R.id.navBackButton)
        taskViewButton = findViewById(R.id.taskViewButton)
        taskViewCard = findViewById(R.id.taskViewCard)
        virtualDesktopsRow = findViewById(R.id.virtualDesktopsRow)
        startMenuAppGrid = findViewById(R.id.startMenuAppGrid)
        searchAppsInput = findViewById(R.id.searchAppsInput)
        allAppsCountText = findViewById(R.id.allAppsCountText)
        trayBatteryText = findViewById(R.id.trayBatteryText)
        trayDisplayIcon = findViewById(R.id.trayDisplayIcon)
        trayKeyboardBtn = findViewById(R.id.trayKeyboardBtn)
        brightnessDimmer = findViewById(R.id.brightnessDimmer)
        taskbar = findViewById(R.id.taskbar)
        systemTray = findViewById(R.id.systemTray)
        runningTasksList = findViewById(R.id.runningTasksList)

        // Control Center
        controlCenterCard = findViewById(R.id.controlCenterCard)
        ccDateText = findViewById(R.id.ccDateText)
        ccSettingsBtn = findViewById(R.id.ccSettingsBtn)
        pillWifi = findViewById(R.id.pillWifi)
        pillBluetooth = findViewById(R.id.pillBluetooth)
        pillRotation = findViewById(R.id.pillRotation)
        pillDisplay = findViewById(R.id.pillDisplay)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        ccBatteryPercent = findViewById(R.id.ccBatteryPercent)
        ccPowerBtn = findViewById(R.id.ccPowerBtn)
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

        if (displayDetectionManager.hasExternalDisplay()) {
            trayDisplayIcon.visibility = View.VISIBLE
        }

        trayDisplayIcon.setOnClickListener {
            showDisplayRoutingDialog()
        }
    }

    private fun setupAdapters() {
        // Windows Desktop Clean Column: Vertical strip of pinned core icons on left edge
        desktopIconGrid.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        desktopAdapter = AppGridAdapter(
            onAppClick = { app -> launchApplication(app) },
            onAppLongClick = { app, v -> showDesktopAppContextMenu(app, v) }
        )
        desktopIconGrid.adapter = desktopAdapter

        // Start Menu Drawer Grid: 4-column scrollable grid for all installed apps
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
            toggleControlCenter(false)
            toggleTaskView(false)
            toggleStartMenu()
        }

        navBackButton.setOnClickListener {
            handleBackNavigation()
        }

        taskViewButton.setOnClickListener {
            toggleStartMenu(false)
            toggleControlCenter(false)
            toggleTaskView()
        }

        // Tap wallpaper to close all cards
        desktopWallpaper.setOnClickListener {
            toggleStartMenu(false)
            toggleControlCenter(false)
            toggleTaskView(false)
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

        trayKeyboardBtn.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)
        }

        findViewById<View>(R.id.desktopSettingsBtn).setOnClickListener {
            showDesktopSettingsDialog()
        }
    }

    private fun setupControlCenter() {
        systemTray.setOnClickListener {
            toggleStartMenu(false)
            toggleTaskView(false)
            toggleControlCenter()
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = currentVolume

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 1. Hardware screen brightness
                    val lp = window.attributes
                    lp.screenBrightness = (progress / 100f).coerceIn(0.05f, 1.0f)
                    window.attributes = lp

                    // 2. Software Dimmer for Laptop screen / External monitor / TV!
                    val dimAlpha = ((100 - progress) / 100f * 0.75f).coerceIn(0f, 0.75f)
                    brightnessDimmer.alpha = dimAlpha
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        pillWifi.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open Wi-Fi settings", Toast.LENGTH_SHORT).show()
            }
        }

        pillBluetooth.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open Bluetooth settings", Toast.LENGTH_SHORT).show()
            }
        }

        pillRotation.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open Display settings", Toast.LENGTH_SHORT).show()
            }
        }

        pillDisplay.setOnClickListener {
            toggleControlCenter(false)
            showDisplayRoutingDialog()
        }

        ccSettingsBtn.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open Settings", Toast.LENGTH_SHORT).show()
            }
        }

        ccPowerBtn.setOnClickListener {
            showPowerDialog()
        }
    }

    private fun setupVirtualDesktops() {
        renderVirtualDesktops()
    }

    private fun toggleTaskView(forceState: Boolean? = null) {
        val shouldOpen = forceState ?: (taskViewCard.visibility != View.VISIBLE)
        if (shouldOpen) {
            renderVirtualDesktops()
            taskViewCard.visibility = View.VISIBLE
        } else {
            taskViewCard.visibility = View.GONE
        }
    }

    private fun renderVirtualDesktops() {
        virtualDesktopsRow.removeAllViews()
        val desktops = virtualDesktopManager.getDesktops()
        val current = virtualDesktopManager.getCurrentDesktop()

        for (desktop in desktops) {
            val btn = MaterialButton(this).apply {
                text = desktop.name
                textSize = 12f
                isAllCaps = false
                cornerRadius = (8 * resources.displayMetrics.density).toInt()
                val isSelected = (desktop.id == current.id)
                strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                strokeColor = ContextCompat.getColorStateList(
                    this@MainActivity,
                    if (isSelected) R.color.dex_accent_cyan else R.color.dex_taskbar_border
                )
                backgroundTintList = ContextCompat.getColorStateList(
                    this@MainActivity,
                    if (isSelected) R.color.dex_accent_glow else R.color.dex_card_bg
                )
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (isSelected) R.color.dex_accent_cyan else R.color.dex_text_primary
                    )
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (42 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                setOnClickListener {
                    virtualDesktopManager.switchDesktop(desktop.id - 1)
                    renderVirtualDesktops()
                    toggleTaskView(false)
                    Toast.makeText(this@MainActivity, "Switched to ${desktop.name}", Toast.LENGTH_SHORT).show()
                }
            }
            virtualDesktopsRow.addView(btn)
        }

        // Add "+ New Desktop" button
        val addBtn = MaterialButton(this).apply {
            text = "+ New Desktop"
            textSize = 12f
            isAllCaps = false
            cornerRadius = (8 * resources.displayMetrics.density).toInt()
            strokeWidth = (1f * resources.displayMetrics.density).toInt()
            strokeColor = ContextCompat.getColorStateList(this@MainActivity, R.color.dex_accent_cyan)
            backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.dex_surface_dark)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dex_accent_cyan))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (42 * resources.displayMetrics.density).toInt()
            )
            setOnClickListener {
                val newD = virtualDesktopManager.addDesktop()
                virtualDesktopManager.switchDesktop(newD.id - 1)
                renderVirtualDesktops()
                toggleTaskView(false)
                Toast.makeText(this@MainActivity, "Created ${newD.name}", Toast.LENGTH_SHORT).show()
            }
        }
        virtualDesktopsRow.addView(addBtn)
    }

    private fun toggleControlCenter(forceState: Boolean? = null) {
        val shouldOpen = forceState ?: (controlCenterCard.visibility != View.VISIBLE)
        if (shouldOpen) {
            val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            ccDateText.text = dateFormat.format(Date())
            ccBatteryPercent.text = "Battery: $currentBatteryPct%"

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            controlCenterCard.visibility = View.VISIBLE
        } else {
            controlCenterCard.visibility = View.GONE
        }
    }

    private fun showPowerDialog() {
        val options = arrayOf("Lock Screen", "Screen Sleep", "Open Settings", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Power Options")
            .setItems(options) { dialog, which ->
                when (which) {
                    0, 1 -> {
                        for (task in activeTasks) {
                            task.isMinimized = true
                        }
                        refreshRunningTasks()
                        windowDecoratorManager.removeAllDecorators()
                        toggleControlCenter(false)
                    }
                    2 -> {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun loadApplications() {
        lifecycleScope.launch {
            allLoadedApps = appRepository.loadInstalledApps()
            startMenuAdapter.submitList(allLoadedApps)
            allAppsCountText.text = "${allLoadedApps.size} Applications"

            // Desktop Pins: Keep only 5 core clean desktop icons on the left side
            val pinnedComponentNames = desktopPreferences.getPinnedApps().toMutableList()
            if (pinnedComponentNames.isEmpty()) {
                val defaultPins = allLoadedApps.take(5).map { it.componentNameString }
                desktopPreferences.savePinnedApps(defaultPins)
                pinnedComponentNames.addAll(defaultPins)
            }

            refreshDesktopPins(pinnedComponentNames)
            Log.i(TAG, "Loaded ${allLoadedApps.size} apps, ${pinnedComponentNames.size} pinned to left desktop column")
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
        dialogView.findViewById<View>(R.id.presetWallpaper4).setOnClickListener {
            wallpaperManager.applyPreset(4, desktopWallpaper)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.presetWallpaper5).setOnClickListener {
            wallpaperManager.applyPreset(5, desktopWallpaper)
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

    private fun handleBackNavigation() {
        if (taskViewCard.visibility == View.VISIBLE) {
            toggleTaskView(false)
        } else if (controlCenterCard.visibility == View.VISIBLE) {
            toggleControlCenter(false)
        } else if (startMenuCard.visibility == View.VISIBLE) {
            toggleStartMenu(false)
        } else {
            val nonMinimized = activeTasks.filter { !it.isMinimized }
            if (nonMinimized.isNotEmpty()) {
                val latest = nonMinimized.first()
                minimizeActiveTask(latest)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_META_LEFT ||
            keyCode == KeyEvent.KEYCODE_META_RIGHT ||
            keyCode == KeyEvent.KEYCODE_WINDOW
        ) {
            toggleControlCenter(false)
            toggleTaskView(false)
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
        handleBackNavigation()
    }
}
