package com.cosmic.opendex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cosmic.opendex.data.AppModel
import com.cosmic.opendex.data.AppRepository
import com.cosmic.opendex.desktop.AppGridAdapter
import com.cosmic.opendex.window.FreeformManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "OpenDeX:Main"

    private lateinit var appRepository: AppRepository
    private lateinit var desktopAdapter: AppGridAdapter
    private lateinit var startMenuAdapter: AppGridAdapter

    private lateinit var desktopIconGrid: RecyclerView
    private lateinit var startMenuCard: CardView
    private lateinit var startMenuButton: FrameLayout
    private lateinit var startMenuAppGrid: RecyclerView
    private lateinit var searchAppsInput: EditText
    private lateinit var allAppsCountText: TextView
    private lateinit var trayBatteryText: TextView

    private lateinit var displayManager: DisplayManager

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
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        bindViews()
        setupAdapters()
        setupListeners()
        loadApplications()
    }

    private fun bindViews() {
        desktopIconGrid = findViewById(R.id.desktopIconGrid)
        startMenuCard = findViewById(R.id.startMenuCard)
        startMenuButton = findViewById(R.id.startMenuButton)
        startMenuAppGrid = findViewById(R.id.startMenuAppGrid)
        searchAppsInput = findViewById(R.id.searchAppsInput)
        allAppsCountText = findViewById(R.id.allAppsCountText)
        trayBatteryText = findViewById(R.id.trayBatteryText)
    }

    private fun setupAdapters() {
        // Desktop Canvas: 6-column grid for 16:9 landscape screens
        val desktopColumns = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
        desktopIconGrid.layoutManager = GridLayoutManager(this, desktopColumns)

        desktopAdapter = AppGridAdapter(
            onAppClick = { app -> launchApplication(app) },
            onAppLongClick = { app, v -> showAppContextMenu(app, v) }
        )
        desktopIconGrid.adapter = desktopAdapter

        // Start Menu Drawer: 4-column scrollable grid
        startMenuAppGrid.layoutManager = GridLayoutManager(this, 4)
        startMenuAdapter = AppGridAdapter(
            onAppClick = { app ->
                toggleStartMenu(false)
                launchApplication(app)
            },
            onAppLongClick = { app, v -> showAppContextMenu(app, v) }
        )
        startMenuAppGrid.adapter = startMenuAdapter
    }

    private fun setupListeners() {
        // Start Menu Button Toggle
        startMenuButton.setOnClickListener {
            toggleStartMenu()
        }

        // Close start menu when tapping desktop canvas
        findViewById<View>(R.id.desktopWallpaper).setOnClickListener {
            toggleStartMenu(false)
        }

        // Search Input Filter
        searchAppsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val filtered = appRepository.searchApps(query)
                startMenuAdapter.submitList(filtered)
            }
        })

        // Settings Button
        findViewById<View>(R.id.desktopSettingsBtn).setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadApplications() {
        lifecycleScope.launch {
            val apps = appRepository.loadInstalledApps()
            // Desktop initially gets top installed apps
            desktopAdapter.submitList(apps.take(24))
            startMenuAdapter.submitList(apps)
            allAppsCountText.text = "${apps.size} Applications"
            Log.i(TAG, "Loaded ${apps.size} installed applications into OpenDeX")
        }
    }

    private fun launchApplication(app: AppModel) {
        val displayId = try {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        } catch (e: Exception) {
            0
        }

        val success = FreeformManager.launchAppInFreeform(
            context = this,
            app = app,
            targetDisplayId = displayId
        )

        if (!success) {
            Toast.makeText(this, "Launching ${app.label}...", Toast.LENGTH_SHORT).show()
        }
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

    private fun showAppContextMenu(app: AppModel, anchor: View): Boolean {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Launch in Freeform Window")
        popup.menu.add(0, 2, 1, "App Info")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    launchApplication(app)
                    true
                }
                2 -> {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Physical Keyboard Win/Super Key opens Start Menu
        if (keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT || keyCode == KeyEvent.KEYCODE_WINDOW) {
            toggleStartMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (startMenuCard.visibility == View.VISIBLE) {
            toggleStartMenu(false)
        } else {
            // In a desktop environment, Back on the desktop does not kill the desktop shell
        }
    }
}
