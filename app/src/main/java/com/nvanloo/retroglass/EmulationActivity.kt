package com.nvanloo.retroglass

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.MotionEvent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nvanloo.retroglass.controller.CompanionView
import com.nvanloo.retroglass.controller.ControllerView
import com.nvanloo.retroglass.controller.LayoutStore
import com.nvanloo.retroglass.model.Console
import com.nvanloo.retroglass.model.RomLibrary
import com.nvanloo.retroglass.ui.GameMenuView
import com.nvanloo.retroglass.ui.MenuTheme
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

/**
 * Runs the game.
 *
 * There is exactly ONE [GLRetroView] for the life of the activity (LibretroDroid's
 * native emulator is a process-wide singleton, so it must never be torn down and
 * recreated while a game is running). The game view is created once, attached to the
 * phone, and then *reparented* between two homes as displays come and go:
 *
 *  - LOCAL: no external display. The game view fills the phone and the controller is
 *    a translucent overlay on top of it.
 *  - EXTENDED: an external display (USB-C glasses, HDMI, cast) is connected. The game
 *    view is moved into a [GamePresentation] on that display and the phone shows only
 *    the controller, full-screen and opaque.
 *
 * Moving a GLSurfaceView across windows destroys and recreates just its EGL surface;
 * the emulator's state lives in native memory and is untouched, so gameplay continues
 * seamlessly across plug/unplug with no save-state juggling.
 */
class EmulationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_CONSOLE = "console"
        private const val TAG = "RetroGlass"

        fun launch(context: Context, romPath: String, console: Console) {
            context.startActivity(Intent(context, EmulationActivity::class.java).apply {
                putExtra(EXTRA_ROM_PATH, romPath)
                putExtra(EXTRA_CONSOLE, console.name)
            })
        }
    }

    private lateinit var console: Console
    private lateinit var romFile: File
    private lateinit var displayManager: DisplayManager
    private lateinit var mediaRouter: android.media.MediaRouter
    private lateinit var inputManager: InputManager

    private var retroView: GLRetroView? = null
    private var presentation: GamePresentation? = null
    private var menuDialog: AlertDialog? = null
    private lateinit var gameMenu: GameMenuView
    private lateinit var layoutStore: LayoutStore
    private lateinit var inputConfig: com.nvanloo.retroglass.controller.InputConfig
    private lateinit var coreOptions: com.nvanloo.retroglass.controller.CoreOptions
    private lateinit var cheats: com.nvanloo.retroglass.controller.Cheats
    private val gameKey: String get() = romFile.absolutePath
    private val consoleKey: String get() = console.name
    private var bindingCaptureDevice: String? = null
    private var bindingCapture: ((Int) -> Unit)? = null
    private var videoScale: Float = 1.0f
    private var videoScaleLocal: Float = 0.62f
    private var fastForward = false

    private lateinit var rootLayout: FrameLayout
    private lateinit var gameContainer: FrameLayout
    private lateinit var screenBezel: com.nvanloo.retroglass.controller.ScreenBezelView
    private lateinit var controllerView: ControllerView
    private lateinit var companionView: CompanionView
    private lateinit var editBar: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var phoneErrorText: TextView
    private lateinit var loadingText: TextView
    private lateinit var pauseOverlay: TextView
    private lateinit var floatingMenu: TextView
    private lateinit var fpsView: TextView
    private lateinit var bezelView: android.widget.ImageView
    private var firstFrameSeen = false
    private var pausedByDisconnect = false

    private var frameCounter = 0
    private var lastFps = 0
    private val playStartMs = android.os.SystemClock.elapsedRealtime()
    private var gyroRegistered = false
    private var gyroAimX = 0f
    private var gyroAimY = 0f

    /** Physical gamepad keys currently held, for detecting the menu hotkey (L1+R1+Select). */
    private val pressedGamepadKeys = mutableSetOf<Int>()
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideFloatingMenuRunnable = Runnable {
        if (phoneIsDisplay()) floatingMenu.visibility = View.GONE
    }

    private val extendedMode: Boolean get() = presentation != null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            applyDisplayMode("displayAdded $displayId")
            maybeHintUnavailableExternal(displayId)
        }
        override fun onDisplayRemoved(displayId: Int) = applyDisplayMode("displayRemoved $displayId")
        override fun onDisplayChanged(displayId: Int) = applyDisplayMode("displayChanged $displayId")
    }

    /** External displays the user chose "Keep on phone" for (cleared when they unplug). */
    private val declinedDisplays = mutableSetOf<Int>()
    private var externalPromptDialog: AlertDialog? = null
    private var hintedUnavailableExternal = false

    private val mediaRouterCallback = object : android.media.MediaRouter.SimpleCallback() {
        override fun onRoutePresentationDisplayChanged(
            router: android.media.MediaRouter,
            route: android.media.MediaRouter.RouteInfo,
        ) = applyDisplayMode("routePresentationDisplayChanged")
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateForGamepad(true)
        override fun onInputDeviceRemoved(deviceId: Int) = updateForGamepad(false)
        override fun onInputDeviceChanged(deviceId: Int) = updateForGamepad(false)
    }

    private var gamepadHintShown = false

    private fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
        val sources = InputDevice.getDevice(id)?.sources ?: 0
        sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    /** Shows the touch pad only when the phone is assigned to a player port. */
    private fun updateForGamepad(announce: Boolean) {
        arrangeLayout()
        if (hasGamepad() && announce && !gamepadHintShown) {
            gamepadHintShown = true
            Toast.makeText(this, R.string.gamepad_connected, Toast.LENGTH_LONG).show()
        }
        if (!hasGamepad()) gamepadHintShown = false
    }

    private fun isGamepadEvent(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun deviceKey(device: InputDevice?): String = device?.descriptor ?: "gamepad"

    private fun connectedGamepads(): List<InputDevice> {
        val result = mutableListOf<InputDevice>()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (isGamepadEvent(d.sources)) result.add(d)
        }
        return result.sortedBy { if (it.controllerNumber <= 0) 99 else it.controllerNumber }
    }

    /** Default port when the user hasn't assigned one: phone is P1 unless a pad is present. */
    private fun defaultPort(deviceKey: String, device: InputDevice?): Int =
        if (deviceKey == com.nvanloo.retroglass.controller.InputConfig.PHONE) {
            if (hasGamepad()) com.nvanloo.retroglass.controller.InputConfig.PORT_OFF else 0
        } else {
            ((device?.controllerNumber ?: 1) - 1).coerceAtLeast(0)
        }

    private fun portFor(deviceKey: String, device: InputDevice?): Int =
        inputConfig.storedPort(deviceKey) ?: defaultPort(deviceKey, device)

    private fun phonePort(): Int = portFor(com.nvanloo.retroglass.controller.InputConfig.PHONE, null)

    /** Number of distinct player ports that have an input source assigned (phone + each pad).
     *  Drives whether we arm multitap: >2 means a 3rd/4th controller needs it. */
    private fun activePortCount(): Int {
        val ports = mutableSetOf<Int>()
        phonePort().takeIf { it >= 0 }?.let { ports.add(it) }
        connectedGamepads().forEach { d ->
            portFor(deviceKey(d), d).takeIf { it >= 0 }?.let { ports.add(it) }
        }
        return ports.size
    }

    /** Android face buttons sit opposite RetroPad's; swap A/B and X/Y (as LibretroDroid does). */
    private fun androidToRetroKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> keyCode
        else -> null
    }

    // Forward physical gamepad input to the emulator (which may live in a Presentation on
    // the external display), honoring the per-controller port assignment and remap.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val v = retroView
        if (v != null && isGamepadEvent(event.source) && event.keyCode != KeyEvent.KEYCODE_BACK) {
            val key = deviceKey(event.device)
            // Track held buttons and open the in-game menu on the L1+R1+Select hotkey. This
            // works in every mode (touch, gamepad-on-phone, external display), so a player
            // on a Bluetooth pad can reach the menu with no external screen or touch needed.
            when (event.action) {
                KeyEvent.ACTION_DOWN -> pressedGamepadKeys.add(event.keyCode)
                KeyEvent.ACTION_UP -> pressedGamepadKeys.remove(event.keyCode)
            }
            if (event.action == KeyEvent.ACTION_DOWN && isMenuHotkey()) {
                openMenuFromGamepad(key, event.device)
                return true
            }
            val capture = bindingCapture
            if (capture != null && bindingCaptureDevice == key && event.action == KeyEvent.ACTION_DOWN &&
                androidToRetroKey(event.keyCode) != null
            ) {
                bindingCapture = null
                bindingCaptureDevice = null
                capture(event.keyCode)
                if (dashboardActive()) refreshCompanion()
                return true
            }
            val port = portFor(key, event.device)
            if (port < 0) return true
            val retroKey = inputConfig.bindings(key)[event.keyCode] ?: androidToRetroKey(event.keyCode)
            if (retroKey != null && event.action != KeyEvent.ACTION_MULTIPLE) {
                v.sendKeyEvent(event.action, retroKey, port)
                if (dashboardActive()) companionView.inputButton(retroKey, event.action == KeyEvent.ACTION_DOWN)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val v = retroView
        if (v != null && isGamepadEvent(event.source)) {
            val key = deviceKey(event.device)
            val port = portFor(key, event.device)
            if (port < 0) return true
            val dead = inputConfig.deadzone(key)
            val sens = inputConfig.sensitivity(key)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val lx = tuneAxis(event.getAxisValue(MotionEvent.AXIS_X), dead, sens)
            val ly = tuneAxis(event.getAxisValue(MotionEvent.AXIS_Y), dead, sens)
            val rx = tuneAxis(event.getAxisValue(MotionEvent.AXIS_Z), dead, sens)
            val ry = tuneAxis(event.getAxisValue(MotionEvent.AXIS_RZ), dead, sens)
            val dash = dashboardActive()
            if (inputConfig.leftStickAsDpad(key)) {
                val dx = if (lx > 0.5f) 1f else if (lx < -0.5f) -1f else hatX
                val dy = if (ly > 0.5f) 1f else if (ly < -0.5f) -1f else hatY
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dx, dy, port)
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, port)
                if (dash) { companionView.inputHat(dx, dy); companionView.inputStick("stick_l", 0f, 0f) }
            } else {
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatX, hatY, port)
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, lx, ly, port)
                if (dash) { companionView.inputHat(hatX, hatY); companionView.inputStick("stick_l", lx, ly) }
            }
            v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, rx, ry, port)
            if (dash) companionView.inputStick("stick_r", rx, ry)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    /** Applies a dead zone (rescaled so motion starts smoothly past it) and sensitivity. */
    private fun tuneAxis(raw: Float, deadzone: Float, sensitivity: Float): Float {
        val mag = kotlin.math.abs(raw)
        if (mag <= deadzone) return 0f
        val scaled = (mag - deadzone) / (1f - deadzone) * sensitivity
        return (if (raw < 0) -scaled else scaled).coerceIn(-1f, 1f)
    }

    /** Orientation follows the library's screen-mode choice (Auto/External follow the sensor). */
    private fun applyScreenOrientation() {
        requestedOrientation = when (layoutStore.screenMode()) {
            LayoutStore.SCREEN_INT_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            LayoutStore.SCREEN_INT_LANDSCAPE,
            LayoutStore.SCREEN_FULLSCREEN -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    /** Whether the current screen mode is allowed to send the game to an external display. */
    private fun modeUsesExternal(): Boolean = layoutStore.screenMode().let {
        it == LayoutStore.SCREEN_AUTO || it == LayoutStore.SCREEN_EXTERNAL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val romPath = intent.getStringExtra(EXTRA_ROM_PATH)
        val consoleName = intent.getStringExtra(EXTRA_CONSOLE)
        if (romPath == null || consoleName == null) {
            finish(); return
        }
        console = Console.valueOf(consoleName)
        romFile = File(romPath)
        if (!romFile.exists()) {
            Toast.makeText(this, R.string.rom_missing, Toast.LENGTH_LONG).show()
            finish(); return
        }

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        mediaRouter = getSystemService(Context.MEDIA_ROUTER_SERVICE) as android.media.MediaRouter
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        layoutStore = LayoutStore(this)
        applyScreenOrientation()
        inputConfig = com.nvanloo.retroglass.controller.InputConfig(this)
        coreOptions = com.nvanloo.retroglass.controller.CoreOptions(this)
        cheats = com.nvanloo.retroglass.controller.Cheats(this)
        videoScale = layoutStore.videoScale()
        videoScaleLocal = layoutStore.localVideoScale()

        buildUi()

        // Create the emulator once, hosted on the phone. It begins rendering here on
        // the primary display (known-good); applyDisplayMode() then moves it to an
        // external display if one is present.
        val view = createRetroView()
        retroView = view
        gameContainer.addView(view, matchParent())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back walks the menu's own stack first, so a sub-screen returns to the root
                // rather than dropping straight out to the game.
                if (!gameMenu.onBack()) showMenu()
            }
        })

        applyDisplayMode("onCreate")
        applyBezel()
        startFpsTicker()
    }

    private val pickBezelImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val dest = File(filesDir, "bezel.png")
            contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            layoutStore.setBezelImagePath(dest.absolutePath)
            layoutStore.setBezelMode(3)
            applyBezel()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
        arrangeLayout()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(40)
        }
    }

    // ---------------------------------------------------------- FPS overlay

    private fun startFpsTicker() {
        val tick = object : Runnable {
            override fun run() {
                lastFps = frameCounter
                // The counter also feeds the companion dashboard's stats; keep the game overlay
                // hidden while the dashboard is up (it would draw over the empty game container).
                // The menu is a full-screen surface; fpsView.bringToFront() would punch through it.
                if (layoutStore.fpsOverlay() && !dashboardActive() &&
                    !(::gameMenu.isInitialized && gameMenu.isOpen)
                ) {
                    fpsView.visibility = View.VISIBLE
                    fpsView.text = getString(R.string.fps_value, frameCounter)
                    fpsView.bringToFront()
                } else {
                    fpsView.visibility = View.GONE
                }
                if (dashboardActive()) pushCompanionStats()
                frameCounter = 0
                uiHandler.postDelayed(this, 1000)
            }
        }
        uiHandler.postDelayed(tick, 1000)
    }

    // ------------------------------------------------------------- bezel

    /** Paints the background behind the game: none / dark / gradient / a custom image. */
    private fun applyBezel() {
        when (layoutStore.bezelMode()) {
            0 -> { // None — transparent (shows the black window behind)
                bezelView.visibility = View.GONE
            }
            2 -> { // Gradient
                bezelView.setImageDrawable(null)
                bezelView.background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#1B1B2E"), Color.parseColor("#05050A")),
                )
                bezelView.visibility = View.VISIBLE
            }
            3 -> { // Custom image
                val path = layoutStore.bezelImagePath()
                val bmp = path?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() }
                if (bmp != null) {
                    bezelView.background = null
                    bezelView.setImageBitmap(bmp)
                    bezelView.visibility = View.VISIBLE
                } else {
                    bezelView.setBackgroundColor(Color.BLACK)
                    bezelView.visibility = View.VISIBLE
                }
            }
            1 -> { // Dark
                bezelView.setImageDrawable(null)
                bezelView.setBackgroundColor(Color.BLACK)
                bezelView.visibility = View.VISIBLE
            }
            else -> Unit // Console shell — handled below, since it needs the rim too
        }
        // In shell mode the plain background is the console's plastic; the rim is separate.
        val shell = layoutStore.bezelMode() == LayoutStore.BEZEL_BODY
        if (shell) {
            bezelView.setImageDrawable(null)
            bezelView.setBackgroundColor(console.bodyColor)
            bezelView.visibility = View.VISIBLE
        }
        screenBezel.visibility = if (shell) View.VISIBLE else View.GONE
        updateScreenBezel()
        // The GL surface clears to its own colour before drawing, so wherever it covers the
        // window - the whole screen in landscape - the letterbox bars are the only background
        // you actually see. Recolouring them at the source fixes every layout at once.
        retroView?.setLetterboxColor(if (shell) console.bodyColor else Color.BLACK)
    }

    // --------------------------------------------------------- gyro aiming

    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager }

    /**
     * Tilt-reactive shadows on the touch controls. Registered only while the pad is actually
     * on screen — in glasses mode the phone is a dashboard or a blank pad on a surface, so
     * there is nothing to light and no reason to spend the samples or the battery.
     */
    private val tiltSource by lazy {
        com.nvanloo.retroglass.controller.TiltSource(this) { x, y ->
            controllerView.setLight(x, y)
            val (lx, ly, strength) = controllerView.currentLight()
            screenBezel.setLight(lx, ly, strength)
        }
    }

    private fun updateTiltShadows() {
        val want = layoutStore.tiltShadows() &&
            controllerView.visibility == View.VISIBLE &&
            !controllerView.editMode &&
            !pausedByDisconnect &&
            tiltSource.isAvailable
        controllerView.tiltBezel = want
        if (want) tiltSource.start() else tiltSource.stop()
    }

    private val gyroListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            val v = retroView ?: return
            val sens = layoutStore.gyroSensitivity()
            // Angular velocity (rad/s): rotating the phone deflects the right stick like a
            // rate-controlled cursor; hold still and it recenters.
            gyroAimX = (-event.values[1] * sens).coerceIn(-1f, 1f)
            gyroAimY = (-event.values[0] * sens).coerceIn(-1f, 1f)
            val port = phonePort().coerceAtLeast(0)
            v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, gyroAimX, gyroAimY, port)
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private fun updateGyroAndTilt() {
        updateGyro()
        updateTiltShadows()
    }

    private fun updateGyro() {
        val want = layoutStore.gyroAim() && !pausedByDisconnect
        val gyro = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
        if (want && !gyroRegistered && gyro != null) {
            sensorManager.registerListener(gyroListener, gyro, android.hardware.SensorManager.SENSOR_DELAY_GAME)
            gyroRegistered = true
        } else if (!want && gyroRegistered) {
            sensorManager.unregisterListener(gyroListener)
            gyroRegistered = false
        }
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        rootLayout = FrameLayout(this)
        // Transparent so the bezel view behind it frames a shrunk picture (bezel mode
        // "Dark" repaints the usual black letterbox).
        gameContainer = FrameLayout(this)
        controllerView = ControllerView(this).apply {
            setConsole(console)
            listener = inputListener
            onLayoutEdited = { }
            turboIds = layoutStore.turboButtons(console)
        }
        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            visibility = View.GONE
        }
        phoneErrorText = TextView(this).apply {
            setTextColor(Color.parseColor("#FFCDD2"))
            setBackgroundColor(Color.parseColor("#CC5A1A1A"))
            textSize = 15f
            gravity = Gravity.CENTER
            val p = (18 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        loadingText = TextView(this).apply {
            text = getString(R.string.loading, romFile.nameWithoutExtension)
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        pauseOverlay = TextView(this).apply {
            text = getString(R.string.paused_disconnect)
            setTextColor(Color.WHITE)
            // Translucent so the small, centered, frozen game stays visible behind it.
            setBackgroundColor(Color.parseColor("#88000000"))
            textSize = 17f
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val p = (28 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            isClickable = true
            visibility = View.GONE
            setOnClickListener { resumeFromPause() }
        }
        floatingMenu = TextView(this).apply {
            text = "≡"  // ≡
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#66000000"))
            textSize = 24f
            gravity = Gravity.CENTER
            val p = (10 * resources.displayMetrics.density).toInt()
            setPadding(p + p / 2, p, p + p / 2, p)
            isClickable = true
            visibility = View.GONE
            setOnClickListener { showMenu() }
        }
        fpsView = TextView(this).apply {
            setTextColor(Color.parseColor("#B6FF6A"))
            setBackgroundColor(Color.parseColor("#66000000"))
            textSize = 12f
            val p = (5 * resources.displayMetrics.density).toInt()
            setPadding(p + p, p, p + p, p)
            visibility = View.GONE
        }
        bezelView = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        companionView = CompanionView(this).apply {
            visibility = View.GONE
            bindConsole(console)
            onSaveState = { saveState() }
            onLoadState = { loadState() }
            onFastForward = { toggleFastForward(); pushCompanionStats() }
            onScreenshot = { takeScreenshot() }
            onOpenMenu = { showMenu() }
            onRemap = { remapActivePad() }
            onUseTouchPad = {
                layoutStore.setPhonePanelMode(LayoutStore.PHONE_PANEL_CONTROLLER)
                arrangeLayout()
            }
        }
        editBar = buildEditBar()

        // Bezel/background sits behind the game so it frames a shrunk picture.
        rootLayout.addView(bezelView, matchParent())
        // The screen's moulded lip. Above the game, not behind it: the GL surface covers its
        // own view and clears first, so anything underneath is invisible - in landscape that is
        // the whole window. The shell colour is set on the GL clear instead.
        screenBezel = com.nvanloo.retroglass.controller.ScreenBezelView(this).apply {
            visibility = View.GONE
        }
        rootLayout.addView(gameContainer, matchParent())
        rootLayout.addView(screenBezel, matchParent())
        gameContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateScreenBezel()
        }
        rootLayout.addView(controllerView, matchParent())
        rootLayout.addView(companionView, matchParent())
        rootLayout.addView(
            fpsView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = 8 },
        )
        rootLayout.addView(
            floatingMenu,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply { topMargin = 16; leftMargin = 16 },
        )
        rootLayout.addView(
            statusText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = 12 },
        )
        rootLayout.addView(
            editBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { topMargin = 16; rightMargin = 16 },
        )
        rootLayout.addView(
            loadingText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        rootLayout.addView(
            phoneErrorText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        // The menu sits above everything but the pause overlay - a display disconnect has to
        // stay visible even with the menu open, so pauseOverlay is added last. Its own
        // background is transparent so a filter screen's preview window shows the live game.
        gameMenu = GameMenuView(this).apply {
            visibility = View.GONE
            consoleTint = console.accentColor
            consoleName = console.displayName
        }
        rootLayout.addView(gameMenu, matchParent())
        rootLayout.addView(pauseOverlay, matchParent())
        applyCutoutInsets()
        setContentView(rootLayout)
    }

    /**
     * Pushes the top overlays clear of a display cutout. We draw edge-to-edge behind the
     * status bar, so on a phone with a centred punch-hole the FPS readout (TOP|CENTER) lands
     * squarely under the camera and the "≡" button can clip the corner on a notched device.
     * The cutout inset is the only one that matters here — the overlays are meant to sit over
     * the game, so system-bar insets are deliberately ignored.
     */
    private fun applyCutoutInsets() {
        val baseTop = mapOf(
            fpsView to 8, floatingMenu to 16, editBar to 16,
        )
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            for ((view, base) in baseTop) {
                val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
                val want = base + cutout
                if (lp.topMargin != want) {
                    lp.topMargin = want
                    view.layoutParams = lp
                }
            }
            insets
        }
    }

    private fun buildEditBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        fun makeButton(textRes: Int, onClick: () -> Unit): Button =
            Button(this).apply {
                setText(textRes)
                setOnClickListener { onClick() }
            }
        bar.addView(makeButton(R.string.edit_done) {
            controllerView.saveLayout()
            setEditMode(false)
            Toast.makeText(this, R.string.layout_saved, Toast.LENGTH_SHORT).show()
        })
        bar.addView(makeButton(R.string.edit_reset) {
            controllerView.resetLayout()
        })
        bar.addView(makeButton(R.string.edit_cancel) {
            controllerView.cancelEdits()
            setEditMode(false)
        })
        return bar
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ------------------------------------------------------- emulator setup

    private fun createRetroView(): GLRetroView {
        val data = GLRetroViewData(this).apply {
            coreFilePath = File(applicationInfo.nativeLibraryDir, console.coreLibName).absolutePath
            gameFilePath = romFile.absolutePath
            systemDirectory = RomLibrary.systemDir(this@EmulationActivity).absolutePath
            savesDirectory = RomLibrary.savesDir(this@EmulationActivity).absolutePath
            preferLowLatencyAudio = true
            rumbleEventsEnabled = true
            shader = currentShaderConfig()
            sramFile().takeIf { it.exists() }?.let { saveRAMState = it.readBytes() }
            // User core-option overrides, then the system's forced variables (which win —
            // e.g. atari800_system selects 5200 vs 8-bit computer for the shared core).
            val merged = LinkedHashMap<String, String>()
            coreOptions.overrides(consoleKey).forEach { (k, v) -> merged[k] = v }
            console.forcedCoreVariables.forEach { (k, v) -> merged[k] = v }
            // With 3+ controllers assigned, arm the core's multitap (option-based cores only;
            // device-based multitaps are enabled in applyMultitap() after the core loads).
            if (activePortCount() > 2) {
                console.multitapCoreVariables.forEach { (k, v) -> merged[k] = v }
            }
            if (merged.isNotEmpty()) {
                variables = merged.map { com.swordfish.libretrodroid.Variable(it.key, it.value, "") }.toTypedArray()
            }
        }
        val view = GLRetroView(this, data)
        lifecycle.addObserver(view)

        view.getGLRetroEvents()
            .onEach { event ->
                if (event is GLRetroView.GLRetroEvents.FrameRendered) {
                    frameCounter++
                    if (!firstFrameSeen) {
                        firstFrameSeen = true
                        runOnUiThread {
                            loadingText.visibility = View.GONE
                            autoLoadState()
                            applyCheats()
                            applyControllerTypes()
                            applyMultitap()
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)

        view.getRumbleEvents()
            .onEach { if (layoutStore.rumbleEnabled()) vibrate() }
            .launchIn(lifecycleScope)

        view.getGLRetroErrors()
            .onEach { code ->
                Log.e(TAG, "GLRetroError code=$code")
                runOnUiThread { showEmulatorError(code) }
            }
            .launchIn(lifecycleScope)
        return view
    }

    private fun errorMessage(code: Int): String {
        val reason = when (code) {
            GLRetroView.ERROR_LOAD_LIBRARY -> getString(R.string.err_load_core)
            GLRetroView.ERROR_LOAD_GAME -> getString(R.string.err_load_game)
            GLRetroView.ERROR_GL_NOT_COMPATIBLE -> getString(R.string.err_gl)
            GLRetroView.ERROR_SERIALIZATION -> getString(R.string.err_serialization)
            else -> getString(R.string.err_generic)
        }
        return getString(R.string.emulator_error_full, romFile.name, reason)
    }

    /** Surfaces an emulator error on whichever screen the game is shown on, plus a toast. */
    private fun showEmulatorError(code: Int) {
        val message = errorMessage(code)
        loadingText.visibility = View.GONE
        presentation?.showError(message)
        if (!extendedMode) {
            phoneErrorText.text = message
            phoneErrorText.visibility = View.VISIBLE
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------- display hot-plugging

    /**
     * Reconciles which display the single game view lives on with the displays that
     * are currently connected. Safe to call repeatedly; it only acts on real change.
     */
    private fun applyDisplayMode(reason: String) {
        val view = retroView ?: return
        val candidate = findExternalDisplay()
        // Internal / Fullscreen modes keep the game on the phone even if glasses are plugged in.
        val external = if (modeUsesExternal()) candidate else null
        val currentPresentationDisplayId = presentation?.display?.displayId
        Log.i(
            TAG,
            "applyDisplayMode($reason): candidate=${candidate?.displayId} " +
                "external=${external?.displayId} presentationOn=$currentPresentationDisplayId",
        )

        when {
            // Nothing external usable: game belongs on the phone.
            external == null -> {
                // Coming off the glasses: bring the game back and auto-pause so it
                // doesn't keep running unwatched.
                if (presentation != null) {
                    moveGameToPhone(view)
                    pauseForDisconnect()
                }
                if (candidate == null) {
                    // Truly unplugged: forget declines and drop a stale prompt.
                    declinedDisplays.clear()
                    externalPromptDialog?.dismiss()
                } else if (!isStartingUp()) {
                    // A display is there but the screen mode is set to internal:
                    // still offer the move (accepting switches the mode to Auto).
                    promptMoveToExternal(candidate, switchModeOnAccept = true)
                }
            }
            // External present and game not yet on it: move it there. Automatic while
            // the activity is starting up (glasses already attached at launch); a
            // mid-game hotplug asks the user first instead of yanking the game away.
            presentation == null -> {
                if (isStartingUp()) {
                    moveGameToPresentation(view, external)
                    resumeFromPause()
                } else {
                    promptMoveToExternal(external, switchModeOnAccept = false)
                }
            }
            // External changed identity (e.g. unplug/replug enumerates a new id):
            // rehome onto the new display.
            external.displayId != currentPresentationDisplayId -> {
                moveGameToPhone(view)
                moveGameToPresentation(view, external)
            }
            // Already on the right external display: nothing to do.
        }
        arrangeLayout()
    }

    /** Launch window in which an already-attached display is adopted without asking. */
    private fun isStartingUp(): Boolean =
        android.os.SystemClock.elapsedRealtime() - playStartMs < 4000

    /** Asks whether to move the running game onto [display] (mid-game hotplug). */
    private fun promptMoveToExternal(display: Display, switchModeOnAccept: Boolean) {
        val view = retroView ?: return
        if (display.displayId in declinedDisplays) return
        if (externalPromptDialog?.isShowing == true) return
        externalPromptDialog = AlertDialog.Builder(this)
            .setTitle(R.string.external_connected_title)
            .setMessage(getString(R.string.external_connected_msg, display.name ?: ""))
            .setPositiveButton(R.string.external_move) { _, _ ->
                if (switchModeOnAccept) {
                    layoutStore.setScreenMode(LayoutStore.SCREEN_AUTO)
                    applyScreenOrientation()
                }
                // Re-resolve: the display id can change between prompt and accept.
                val target = findExternalDisplay() ?: display
                if (presentation == null) {
                    moveGameToPresentation(view, target)
                    resumeFromPause()
                }
                applyDisplayMode("userAcceptedExternal")
            }
            .setNegativeButton(R.string.external_stay) { _, _ ->
                declinedDisplays.add(display.displayId)
            }
            .setOnCancelListener { declinedDisplays.add(display.displayId) }
            .show().gamepadNavigable()
    }

    /** One-time hint when a non-internal display appeared but can't be used for output
     *  (typically Samsung DeX or mirroring holding it). */
    private fun maybeHintUnavailableExternal(displayId: Int) {
        if (hintedUnavailableExternal) return
        val display = displayManager.getDisplay(displayId) ?: return
        if (isInternalDisplay(display)) return
        if (findExternalDisplay() == null) {
            hintedUnavailableExternal = true
            Toast.makeText(this, R.string.external_unavailable_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun isPortrait(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    /**
     * Positions the game and controller for the current display + orientation:
     *  - glasses: game on the external display, controller fills the phone
     *  - portrait, no glasses: game as a screen across the top, controller below it
     *  - landscape, no glasses: game centered/scaled with the controller as an overlay
     */
    /** True when there's no external display and the phone itself is not a player
     *  (e.g. one or two Bluetooth pads drive the game). The phone becomes the
     *  display: the game fills the screen, no touch controller, a floating menu
     *  button lets the user open the menu / exit. */
    private fun phoneIsDisplay(): Boolean {
        if (extendedMode) return false
        if (phonePort() < 0) return true
        // Fullscreen mode: game fills the phone and a physical gamepad drives it.
        return layoutStore.screenMode() == LayoutStore.SCREEN_FULLSCREEN && connectedGamepads().isNotEmpty()
    }

    /** Shows the floating ≡ button, then schedules it to fade out after a few idle seconds. */
    private fun revealFloatingMenu() {
        if (!phoneIsDisplay()) return
        floatingMenu.visibility = View.VISIBLE
        floatingMenu.bringToFront()
        uiHandler.removeCallbacks(hideFloatingMenuRunnable)
        uiHandler.postDelayed(hideFloatingMenuRunnable, 3000)
    }

    // A tap anywhere brings the floating menu button back (phone-as-display mode only).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && phoneIsDisplay()) revealFloatingMenu()
        return super.dispatchTouchEvent(ev)
    }

    /** Opens the in-game menu from a gamepad, releasing the hotkey buttons so nothing sticks. */
    private fun openMenuFromGamepad(key: String, device: InputDevice?) {
        val v = retroView ?: return
        val port = portFor(key, device).coerceAtLeast(0)
        // The hotkey buttons' key-ups will go to the menu dialog, not the game, so release
        // them on the emulator now to avoid stuck inputs when the menu closes.
        inputConfig.menuHotkey().forEach {
            androidToRetroKey(it)?.let { rk -> v.sendKeyEvent(KeyEvent.ACTION_UP, rk, port) }
        }
        pressedGamepadKeys.clear()
        showMenu()
    }

    private fun isMenuHotkey(): Boolean {
        val combo = inputConfig.menuHotkey()
        return combo.isNotEmpty() && pressedGamepadKeys.containsAll(combo)
    }

    /**
     * Makes an AlertDialog fully operable from a gamepad: the D-pad already moves the
     * selection (Android handles that for focusable lists/buttons), so we only translate
     * the face buttons — A → confirm (D-pad centre), B → back. Select/Start are left alone
     * so releasing the menu hotkey doesn't dismiss the dialog.
     */
    private fun AlertDialog.gamepadNavigable(): AlertDialog {
        setOnKeyListener { d, keyCode, ev ->
            val mapped = when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                else -> return@setOnKeyListener false
            }
            (d as? android.app.Dialog)?.window?.decorView?.dispatchKeyEvent(KeyEvent(ev.action, mapped))
            true
        }
        return this
    }

    /** In external-display mode, whether the phone shows the companion dashboard instead of the
     *  touch pad. Auto = there's a physical pad and the phone isn't a player (so the pad is P1). */
    private fun dashboardActive(): Boolean {
        if (!extendedMode) return false
        return when (layoutStore.phonePanelMode()) {
            LayoutStore.PHONE_PANEL_DASHBOARD -> true
            LayoutStore.PHONE_PANEL_CONTROLLER -> false
            else -> hasGamepad() && phonePort() < 0
        }
    }

    private fun arrangeLayout() {
        if (!::controllerView.isInitialized || !::gameContainer.isInitialized ||
            !::companionView.isInitialized
        ) {
            return
        }
        if (!dashboardActive()) companionView.visibility = View.GONE
        // The pad may have just appeared or gone; keep the tilt sensor in step with it.
        controllerView.post { updateTiltShadows() }
        if (extendedMode) {
            floatingMenu.visibility = View.GONE
            gameContainer.visibility = View.GONE
            if (dashboardActive()) {
                controllerView.visibility = View.GONE
                companionView.visibility = View.VISIBLE
                companionView.bringToFront()
                refreshCompanion()
                applyVideoTransform()
                return
            }
            controllerView.visibility = View.VISIBLE
            controllerView.overlayMode = false
            // The landscape full-pad rearrangement only fits a landscape-held phone; in
            // portrait keep the portrait-authored layout spread over the full screen.
            controllerView.layoutMode =
                if (isPortrait()) ControllerView.LAYOUT_PORTRAIT else ControllerView.LAYOUT_FULLPAD
            setRegion(controllerView, fraction = 1f, top = false)
            applyVideoTransform()
            return
        }
        if (phoneIsDisplay()) {
            // Phone is the TV: game fills the screen, touch pad hidden, floating menu
            // that fades out when there's no touch activity.
            controllerView.visibility = View.GONE
            gameContainer.visibility = View.VISIBLE
            setRegion(gameContainer, fraction = 1f, top = false)
            revealFloatingMenu()
            applyVideoTransform()
            return
        }
        uiHandler.removeCallbacks(hideFloatingMenuRunnable)
        controllerView.visibility = View.VISIBLE
        floatingMenu.visibility = View.GONE
        gameContainer.visibility = View.VISIBLE
        if (isPortrait()) {
            controllerView.overlayMode = false
            controllerView.layoutMode = ControllerView.LAYOUT_PORTRAIT
            rootLayout.post {
                val h = rootLayout.height
                if (h == 0) return@post
                val frac = layoutStore.portraitScreenFraction()
                setRegionPx(gameContainer, (h * frac).toInt(), top = true)
                setRegionPx(controllerView, (h * (1f - frac)).toInt(), top = false)
                applyVideoTransform()
            }
        } else {
            controllerView.overlayMode = true
            controllerView.layoutMode = ControllerView.LAYOUT_FRAME
            setRegion(controllerView, fraction = 1f, top = false)
            if (layoutStore.screenMode() == LayoutStore.SCREEN_INT_LANDSCAPE) {
                // Separate landscape: the game sits in a centred window with the pad framing it,
                // rather than the pad overlaying a full-bleed picture.
                rootLayout.post {
                    val w = rootLayout.width
                    if (w == 0) return@post
                    val lp = (gameContainer.layoutParams as? FrameLayout.LayoutParams)
                        ?: FrameLayout.LayoutParams(0, 0)
                    lp.width = (w * 0.60f).toInt()
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.gravity = Gravity.CENTER
                    gameContainer.layoutParams = lp
                    applyVideoTransform()
                }
            } else {
                setRegion(gameContainer, fraction = 1f, top = false)
                applyVideoTransform()
            }
        }
    }

    private fun setRegion(v: View, fraction: Float, top: Boolean) {
        val lp = (v.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(0, 0)
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        lp.gravity = if (top) Gravity.TOP else Gravity.NO_GRAVITY
        v.layoutParams = lp
    }

    private fun setRegionPx(v: View, heightPx: Int, top: Boolean) {
        val lp = (v.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(0, 0)
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = heightPx
        lp.gravity = if (top) Gravity.TOP else Gravity.BOTTOM
        v.layoutParams = lp
    }

    /**
     * Applies size, rotation and position to the game view within its host. In
     * portrait the game fills the top screen region; on glasses / landscape it is
     * scaled and can be shrunk, rotated (0/90/180/270) and nudged around.
     */
    /**
     * Puts the screen bezel around the picture's real edges.
     *
     * The game view is usually bigger than the picture — the renderer letterboxes inside it —
     * so the rim has to be laid out from the core's aspect ratio, not from the view's bounds.
     * A quarter-turn swaps the picture's proportions with it.
     */
    private fun updateScreenBezel() {
        if (!::screenBezel.isInitialized) return
        val v = retroView ?: return
        if (v.width == 0 || v.height == 0) return
        val raw = runCatching { v.aspectRatio() }.getOrDefault(0f)
        if (raw <= 0f) return
        val rot = layoutStore.videoRotation()
        val aspect = if (rot == 90 || rot == 270) 1f / raw else raw

        var pw = v.width.toFloat()
        var ph = pw / aspect
        if (ph > v.height) {
            ph = v.height.toFloat()
            pw = ph * aspect
        }
        val here = IntArray(2)
        val mine = IntArray(2)
        v.getLocationInWindow(here)
        screenBezel.getLocationInWindow(mine)
        val cx = (here[0] - mine[0]) + v.width / 2f
        val cy = (here[1] - mine[1]) + v.height / 2f
        screenBezel.setPicture(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
        Log.i(TAG, "screen bezel: aspect=$aspect view=${v.width}x${v.height} picture=${pw.toInt()}x${ph.toInt()}")
    }

    private fun applyVideoTransform() {
        val v = retroView ?: return
        val parent = v.parent as? FrameLayout ?: return
        val scale = when {
            extendedMode -> videoScale
            phoneIsDisplay() -> videoScale
            isPortrait() -> 1.0f
            // Separate landscape sizes the container itself, so the game fills it.
            layoutStore.screenMode() == LayoutStore.SCREEN_INT_LANDSCAPE -> 1.0f
            else -> videoScaleLocal
        }
        val rot = layoutStore.videoRotation()
        val offX = layoutStore.videoOffsetX()
        val offY = layoutStore.videoOffsetY()
        parent.post {
            val pw = parent.width
            val ph = parent.height
            if (pw == 0 || ph == 0) return@post
            val lp = (v.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(0, 0)
            // Swap the box for quarter turns so the rotated picture keeps its aspect.
            if (rot == 90 || rot == 270) {
                lp.width = (ph * scale).toInt()
                lp.height = (pw * scale).toInt()
            } else {
                lp.width = (pw * scale).toInt()
                lp.height = (ph * scale).toInt()
            }
            lp.gravity = Gravity.CENTER
            v.layoutParams = lp
            v.rotation = rot.toFloat()
            v.translationX = offX * pw
            v.translationY = offY * ph
        }
    }

    // ------------------------------------------------- companion dashboard

    /** The physical pad the dashboard mirrors: the first connected pad assigned to a real port,
     *  else just the first connected pad. */
    private fun activePad(): InputDevice? =
        connectedGamepads().firstOrNull { portFor(deviceKey(it), it) >= 0 }
            ?: connectedGamepads().firstOrNull()

    /** Refreshes the dashboard's static content (title, pad name, button mapping, stats). */
    private fun refreshCompanion() {
        if (!::companionView.isInitialized) return
        val pad = activePad()
        companionView.setHeader(
            "${romFile.nameWithoutExtension}  ·  ${console.displayName}",
            pad?.name,
        )
        companionView.setMapping(companionMappingRows(pad))
        pushCompanionStats()
    }

    /** RetroPad button -> the physical key bound to it (or "(default)"), for the shown pad. */
    private fun companionMappingRows(pad: InputDevice?): List<Pair<String, String>> {
        val key = pad?.let { deviceKey(it) } ?: return emptyList()
        return com.nvanloo.retroglass.controller.InputConfig.RETRO_BUTTONS.map { (name, retroKey) ->
            val phys = inputConfig.physicalFor(key, retroKey)
            val physName = phys?.let {
                KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_")
            } ?: getString(R.string.remap_default)
            name to physName
        }
    }

    private fun pushCompanionStats() {
        if (!dashboardActive()) return
        val secs = (android.os.SystemClock.elapsedRealtime() - playStartMs) / 1000
        val frameMs = if (lastFps > 0) 1000f / lastFps else 0f
        companionView.updateStats(lastFps, frameMs, secs, fastForward)
    }

    private fun remapActivePad() {
        val pad = activePad()
        if (pad == null) {
            Toast.makeText(this, R.string.companion_no_pad, Toast.LENGTH_SHORT).show()
            return
        }
        showRemap(deviceKey(pad), pad.name)
    }

    private fun showPhonePanelPicker() {
        ensureMenu()
        gameMenu.pushSelect(menuTitle(R.string.menu_phone_panel),
            listOf(
                getString(R.string.phone_panel_auto),
                getString(R.string.phone_panel_controller),
                getString(R.string.phone_panel_dashboard),
            ),
            layoutStore.phonePanelMode(),
        ) { layoutStore.setPhonePanelMode(it); arrangeLayout() }
    }

    private fun moveGameToPresentation(view: GLRetroView, display: Display) {
        val pres = GamePresentation(this, display)
        try {
            pres.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "Presentation.show failed; display gone", e)
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        pres.attach(view)
        presentation = pres
        phoneErrorText.visibility = View.GONE
        statusText.text = getString(R.string.playing_on_external, romFile.nameWithoutExtension)
        statusText.visibility = View.VISIBLE
        Log.i(TAG, "Game moved to external display ${display.displayId}")
    }

    private fun pauseForDisconnect() {
        if (pausedByDisconnect) return
        pausedByDisconnect = true
        pauseOverlay.visibility = View.VISIBLE
        pauseOverlay.bringToFront()
        // Let the game re-render once at the small centered size, then freeze it so the
        // paused frame shows as the small screen rather than a stale full-size frame.
        rootLayout.postDelayed({ if (pausedByDisconnect) retroView?.onPause() }, 250)
    }

    private fun resumeFromPause() {
        if (!pausedByDisconnect) return
        pausedByDisconnect = false
        pauseOverlay.visibility = View.GONE
        retroView?.onResume()
    }

    private fun moveGameToPhone(view: GLRetroView) {
        (view.parent as? ViewGroup)?.removeView(view)
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        gameContainer.visibility = View.VISIBLE
        gameContainer.addView(view, matchParent())
        statusText.visibility = View.GONE
        Log.i(TAG, "Game moved to phone")
    }

    /**
     * A display we should present the game on. Primary source is MediaRouter's
     * live-video route. The DisplayManager category alone is not enough: foldables
     * like the Galaxy Z Flip mark their internal cover screen with FLAG_PRESENTATION,
     * which must not count as an external display.
     */
    private fun findExternalDisplay(): Display? {
        mediaRouter.getSelectedRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
            ?.presentationDisplay?.let { if (!isInternalDisplay(it)) return it }
        return displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { !isInternalDisplay(it) }
    }

    /** Display.getType() is not public API. If reflection is blocked (non-SDK policy),
     *  fall back to a heuristic rather than "everything is internal" — that default
     *  silently disabled external-display detection altogether. Built-in panels
     *  enumerate first at boot (0 = main, 1 = the Flip's cover screen); hotplugged
     *  displays get higher ids. */
    private fun isInternalDisplay(display: Display): Boolean = runCatching {
        val type = Display::class.java.getMethod("getType").invoke(display) as Int
        type == 1 // Display.TYPE_INTERNAL
    }.getOrElse { display.displayId <= 1 }

    // ------------------------------------------------------------ input

    private val inputListener = object : ControllerView.Listener {
        override fun onButton(keyCode: Int, pressed: Boolean) {
            retroView?.sendKeyEvent(
                if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
                keyCode,
                phonePort().coerceAtLeast(0),
            )
        }

        override fun onDpad(x: Float, y: Float) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, x, y, phonePort().coerceAtLeast(0))
        }

        override fun onStick(id: String, x: Float, y: Float) {
            // "cbuttons" (N64 C-cluster) and "intvkp" (Intellivision keypad 1-9) both drive
            // the right analog, which those cores read for the C-buttons / numeric disc.
            val source = if (id == "stick_r" || id == "cbuttons" || id == "intvkp") {
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
            } else {
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT
            }
            retroView?.sendMotionEvent(source, x, y, phonePort().coerceAtLeast(0))
        }

        override fun onMenu() = showMenu()
    }

    // ------------------------------------------------------------ menu

    // ------------------------------------------------- in-game menu (overlay)

    private fun isLandscape() =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /** Opens the custom in-game menu. See [GameMenuView] and docs/menu-design-brief.md. */
    private fun showMenu() {
        if (controllerView.editMode) return
        menuDialog?.dismiss()
        gameMenu.consoleTint = console.accentColor
        gameMenu.consoleName = console.displayName
        gameMenu.rootStatus = getString(R.string.menu_console_running, console.displayName)
        gameMenu.open { menuRootScreen() }
    }

    /** The single filter name shown on the "Filters & look" row, or the chain length. */
    private fun filterSummary(): String {
        val combo = layoutStore.comboFilters(console)
        if (combo.isNotEmpty()) return getString(R.string.menu_combo_count, combo.size)
        val idx = layoutStore.shaderIndex(console)
        return if (idx == 0) getString(R.string.menu_filters_none) else filterName(idx)
    }

    private fun menuRootScreen(): View = with(gameMenu) {
        val save = actionTile(
            getString(R.string.menu_save_short), getString(R.string.menu_state_sub),
        ) { saveState(); gameMenu.close() }
        val load = actionTile(
            getString(R.string.menu_load_short), getString(R.string.menu_state_sub),
        ) { loadState(); gameMenu.close() }
        val ff = toggleRow(getString(R.string.menu_fast_forward), fastForward) { toggleFastForward() }
        val filters = navRow("▷", getString(R.string.menu_filters_look), filterSummary()) {
            push(menuTitle(R.string.menu_filters_look)) { menuVideoScreen() }
        }
        val controls = navRow("◎", getString(R.string.menu_controls_input)) {
            push(menuTitle(R.string.menu_controls_input)) { menuControlsScreen() }
        }
        val changedCount = coreOptions.overrides(consoleKey).size
        val core = navRow(
            "⚙", getString(R.string.menu_core_options),
            if (changedCount > 0) getString(R.string.menu_core_changed_count, changedCount) else null,
        ) { showCoreOptions() }
        val cheats = navRow("✦", getString(R.string.menu_cheats)) { showCheats() }
        val shot = bigButton(getString(R.string.menu_screenshot)) { takeScreenshot() }
        val exit = bigButton(getString(R.string.menu_exit), danger = true) { exitGame() }

        // Landscape gets the design's four category columns instead of a scrolling list.
        if (isLandscape()) {
            return columns(
                columnOf(getString(R.string.menu_group_play), save, load, ff),
                columnOf(getString(R.string.menu_group_video), filters, shot),
                columnOf(getString(R.string.menu_group_controls), controls, cheats),
                columnOf(getString(R.string.menu_group_system), core, exit),
            )
        }
        body {
            addView(group(getString(R.string.menu_group_play)))
            addView(pair(save, load))
            addView(ff)
            addView(group(getString(R.string.menu_group_settings)))
            addView(filters)
            addView(controls)
            addView(core)
            addView(cheats)
            addView(spacer())
            addView(pair(shot, exit))
        }
    }

    private fun menuVideoScreen(): View = with(gameMenu) {
        body {
            addView(navRow(null, getString(R.string.menu_video_filter), filterSummary()) {
                showVideoFilterPicker()
            })
            addView(navRow(null, getString(R.string.menu_combine_filters)) {
                push(menuTitle(R.string.menu_combine_filters)) { menuChainScreen() }
            })
            addView(navRow(null, getString(R.string.menu_filter_settings)) {
                push(menuTitle(R.string.menu_filter_settings)) { menuFilterSettingsScreen() }
            })
            addView(navRow(null, getString(R.string.menu_upscale_factor), upscaleLabel()) {
                showUpscaleFactorPicker()
            })
            addView(navRow(null, getString(R.string.menu_filter_presets)) { showFilterPresets() })
            addView(navRow(null, getString(R.string.menu_screen_size)) { showScreenSizeDialog() })
            addView(navRow(null, getString(R.string.menu_res_boost)) { toggleInternalResolution() })
            addView(navRow(null, getString(R.string.menu_display_extras)) { showDisplayExtras() })
        }
    }

    private fun upscaleLabel(): String {
        val stored = layoutStore.upscaleFactor()
        return if (stored == LayoutStore.UPSCALE_AUTO) "×${autoUpscale()}" else "×$stored"
    }

    private fun menuControlsScreen(): View = with(gameMenu) {
        body {
            if (controllerView.visibility == View.VISIBLE) {
                addView(navRow(null, getString(R.string.menu_choose_layout)) { showLayoutPicker() })
                addView(navRow(null, getString(R.string.menu_edit_layout)) {
                    gameMenu.close(); setEditMode(true)
                })
                addView(navRow(null, getString(R.string.menu_turbo)) { showTurboConfig() })
            }
            if (extendedMode) {
                addView(navRow(null, getString(R.string.menu_phone_panel)) { showPhonePanelPicker() })
            }
            addView(toggleRow(getString(R.string.menu_rumble_label), layoutStore.rumbleEnabled()) {
                layoutStore.setRumbleEnabled(it)
            })
            addView(navRow(null, getString(R.string.menu_controllers)) { showControllers() })
            if (hasControllerTypeChoices()) {
                addView(navRow(null, getString(R.string.menu_controller_type)) { showControllerTypes() })
            }
            if ((retroView?.getAvailableDisks() ?: 0) > 1) {
                addView(navRow(null, getString(R.string.menu_swap_disc)) { showDiscSwap() })
            }
            addView(navRow(null, getString(R.string.menu_reset)) { retroView?.reset(); gameMenu.close() })
        }
    }

    /**
     * Sliders over a live window onto the running game. The preview is a transparent gap, not a
     * capture, so dragging a slider changes what you are looking at in real time.
     */
    private fun menuFilterSettingsScreen(): View = with(gameMenu) {
        fun live(key: String, label: Int) = slider(
            getString(label), param(key), format = { "${(it * 100).toInt()}%" },
        ) {
            layoutStore.setFilterParam(key, it)
            retroView?.shader = currentShaderConfig()
        }
        LinearLayout(this@EmulationActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(previewWindow(96f, getString(R.string.menu_live_caption, filterSummary())))
            addView(body(padSides = 18f) {
                addView(slider(
                    getString(R.string.menu_slider_sharpness), layoutStore.filterSharpness(),
                    format = { "${(it * 100).toInt()}%" },
                ) {
                    layoutStore.setFilterSharpness(it)
                    retroView?.shader = currentShaderConfig()
                })
                addView(live("scanline", R.string.menu_slider_scanline))
                addView(live("bloom", R.string.menu_slider_bloom))
                addView(live("ntsc", R.string.menu_slider_ntsc))
                addView(live("lcdgrid", R.string.menu_slider_lcdgrid))
                addView(live("curve", R.string.menu_slider_curve))
                addView(pair(
                    bigButton(getString(R.string.menu_reset_params)) {
                        paramDefaults.forEach { (k, v) -> layoutStore.setFilterParam(k, v) }
                        retroView?.shader = currentShaderConfig()
                        gameMenu.refresh()
                    },
                    bigButton(getString(R.string.menu_save_look), tint = true) { showFilterPresets() },
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /**
     * "Combine filters" as the signal chain it actually is. The ordinals are the point: the
     * composer runs stages in [comboOrder], so a checklist would misrepresent it.
     */
    private fun menuChainScreen(): View = with(gameMenu) {
        val combo = layoutStore.comboFilters(console).toMutableList()
        fun apply() {
            layoutStore.setComboFilters(console, comboOrder.filter { it in combo })
            retroView?.shader = currentShaderConfig()
            gameMenu.refresh()
        }
        body(padSides = 16f) {
            addView(TextView(this@EmulationActivity).apply {
                text = getString(R.string.menu_chain_hint)
                setTextColor(MenuTheme.GROUP)
                textSize = 11f
            })
            addView(TextView(this@EmulationActivity).apply {
                text = getString(R.string.menu_chain_in)
                setTextColor(MenuTheme.GROUP)
                textSize = 11f
            })
            var n = 0
            for (token in comboOrder) {
                if (token !in combo) continue
                n++
                addView(pipelineRow(n, comboLabel(token), true) { combo.remove(token); apply() })
            }
            addView(TextView(this@EmulationActivity).apply {
                text = getString(R.string.menu_chain_out)
                setTextColor(MenuTheme.GROUP)
                textSize = 11f
            })
            addView(group(getString(R.string.menu_chain_off)))
            for (token in comboOrder) {
                if (token in combo) continue
                addView(addRow(comboLabel(token)) { combo.add(token); apply() })
            }
        }
    }

    private fun showDiscSwap() {
        val v = retroView ?: return
        val count = v.getAvailableDisks()
        if (count <= 1) return
        val current = v.getCurrentDisk()
        val names = (0 until count).map { getString(R.string.disc_n, it + 1) }.toTypedArray()
        ensureMenu()
        gameMenu.pushSelect(menuTitle(R.string.menu_swap_disc), names.toList(), current) { which ->
            v.changeDisk(which)
            Toast.makeText(this, getString(R.string.disc_switched, which + 1), Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------ controllers

    private fun portLabel(port: Int): String =
        if (port < 0) getString(R.string.player_off) else getString(R.string.player_n, port + 1)

    /** Lists the phone plus each connected gamepad; tap one to configure it. */
    private fun showControllers() {
        data class Ctrl(val key: String, val name: String, val device: InputDevice?)
        val PHONE = com.nvanloo.retroglass.controller.InputConfig.PHONE
        val items = mutableListOf(Ctrl(PHONE, getString(R.string.ctrl_phone), null))
        connectedGamepads().forEach { items.add(Ctrl(deviceKey(it), it.name, it)) }

        ensureMenu()
        gameMenu.push(menuTitle(R.string.menu_controllers)) {
            with(gameMenu) {
                body {
                    for (c in items) {
                        addView(navRow(null, c.name, portLabel(portFor(c.key, c.device)), valueIsLive = false) {
                            showControllerOptions(c.key, c.name, c.device, c.key != PHONE)
                        })
                    }
                    addView(group(getString(R.string.menu_hotkey_title)))
                    addView(navRow(null, getString(R.string.menu_hotkey_title),
                        com.nvanloo.retroglass.controller.InputConfig.MENU_HOTKEY_PRESETS
                            .firstOrNull { it.second == inputConfig.menuHotkey() }?.first) {
                        showMenuHotkeyPicker()
                    })
                }
            }
        }
    }

    private fun showMenuHotkeyPicker() {
        val presets = com.nvanloo.retroglass.controller.InputConfig.MENU_HOTKEY_PRESETS
        val current = inputConfig.menuHotkey()
        val checked = presets.indexOfFirst { it.second == current }
        val labels = presets.map { it.first }.toTypedArray()
        ensureMenu()
        gameMenu.pushSelect(menuTitle(R.string.menu_hotkey_title), labels.toList(), checked) { which ->
            inputConfig.setMenuHotkey(presets[which].second)
            Toast.makeText(this, getString(R.string.menu_hotkey_set, labels[which]), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showControllerOptions(key: String, name: String, device: InputDevice?, isGamepad: Boolean) {
        val opts = mutableListOf(getString(R.string.ctrl_set_player))
        if (isGamepad) {
            opts += getString(R.string.ctrl_remap)
            opts += getString(
                if (inputConfig.leftStickAsDpad(key)) R.string.ctrl_stick_dpad_on else R.string.ctrl_stick_dpad_off,
            )
            opts += getString(R.string.ctrl_stick_tuning)
            opts += getString(R.string.ctrl_reset_map)
        }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(opts.toTypedArray()) { _, which ->
                when (opts[which]) {
                    getString(R.string.ctrl_set_player) -> showPlayerPicker(key, name, device)
                    getString(R.string.ctrl_remap) -> showRemap(key, name)
                    getString(R.string.ctrl_stick_tuning) -> showStickTuning(key, name)
                    getString(R.string.ctrl_reset_map) -> {
                        inputConfig.clearBindings(key)
                        Toast.makeText(this, R.string.ctrl_map_reset, Toast.LENGTH_SHORT).show()
                    }
                    else -> inputConfig.setLeftStickAsDpad(key, !inputConfig.leftStickAsDpad(key))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    /** Dead-zone and sensitivity sliders for a gamepad's analog sticks. */
    private fun showStickTuning(key: String, name: String) {
        ensureMenu()
        gameMenu.push(name) {
            with(gameMenu) {
                body(padSides = 18f) {
                    // Dead zone 0..0.5, sensitivity 0.5..2.0, each printing its own unit.
                    addView(slider(
                        getString(R.string.ctrl_deadzone),
                        (inputConfig.deadzone(key) / 0.5f).coerceIn(0f, 1f),
                        format = { "${(it * 50).toInt()}%" },
                    ) { inputConfig.setDeadzone(key, it * 0.5f) })
                    addView(slider(
                        getString(R.string.ctrl_sensitivity),
                        ((inputConfig.sensitivity(key) - 0.5f) / 1.5f).coerceIn(0f, 1f),
                        format = { "%.2f×".format(0.5f + it * 1.5f) },
                    ) { inputConfig.setSensitivity(key, 0.5f + it * 1.5f) })
                }
            }
        }
    }

    private fun showPlayerPicker(key: String, name: String, device: InputDevice?) {
        val ports = listOf(0, 1, 2, 3, com.nvanloo.retroglass.controller.InputConfig.PORT_OFF)
        val labels = ports.map { portLabel(it) }.toTypedArray()
        val current = ports.indexOf(portFor(key, device)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(name)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                inputConfig.setPort(key, ports[which])
                updateForGamepad(false)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    private fun showRemap(key: String, name: String) {
        val buttons = com.nvanloo.retroglass.controller.InputConfig.RETRO_BUTTONS
        fun labelFor(index: Int): String {
            val (bName, retroKey) = buttons[index]
            val phys = inputConfig.physicalFor(key, retroKey)
            val physName = phys?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") } ?: getString(R.string.remap_default)
            return "$bName  →  $physName"
        }
        val labels = buttons.indices.map { labelFor(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remap_title, name))
            .setItems(labels) { _, which ->
                val (bName, retroKey) = buttons[which]
                val prompt = Toast.makeText(this, getString(R.string.remap_press, bName), Toast.LENGTH_LONG)
                prompt.show()
                bindingCaptureDevice = key
                bindingCapture = { physicalKey ->
                    inputConfig.bind(key, physicalKey, retroKey)
                    prompt.cancel()
                    Toast.makeText(this, getString(R.string.remap_done, bName), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                bindingCapture = null; bindingCaptureDevice = null
            }
            .show().gamepadNavigable()
    }

    private fun shaderForIndex(i: Int): ShaderConfig = when (i) {
        1 -> ShaderConfig.CRT
        2 -> ShaderConfig.LCD
        3 -> ShaderConfig.Sharp
        // CUT2 is LibretroDroid's edge-smoothing upscaler (the filter Lemuroid ships as its
        // default) — cleans up low-res pixels on a big external display without CRT/LCD look.
        4 -> ShaderConfig.CUT2()
        // Our fork's custom end-phase chains (work on every system, 2D and 3D).
        5 -> com.nvanloo.retroglass.video.Anime4KShaders.upscaleCnnX2S()
        6 -> com.nvanloo.retroglass.video.RetroShaders.casSharpen(layoutStore.filterSharpness())
        7 -> com.nvanloo.retroglass.video.FsrShaders.fsr1(layoutStore.filterSharpness(), upscale())
        8 -> com.nvanloo.retroglass.video.CrtLottesShaders.crtLottes()
        9 -> com.nvanloo.retroglass.video.SabrShaders.sabr(upscale())
        10 -> com.nvanloo.retroglass.video.RetroShaders.dedither()
        11 -> com.nvanloo.retroglass.video.LanczosShaders.lanczos(upscale())
        12 -> com.nvanloo.retroglass.video.PixelAaShaders.pixelAa(upscale())
        13 -> com.nvanloo.retroglass.video.NtscShaders.ntsc(bleed = ntscBleed())
        14 -> com.nvanloo.retroglass.video.RetroShaders.bloom(intensity = bloomAmount())
        15 -> com.nvanloo.retroglass.video.RetroShaders.lcdGrid(depth = lcdGridDepth())
        16 -> com.nvanloo.retroglass.video.RetroShaders.curvature(curve = curveAmount())
        else -> ShaderConfig.Default
    }

    // Composable filter building blocks, in the order they stack.
    //
    // Anime4K MUST come first: its depth-to-space pass adds a residual on top of the
    // ORIGINAL frame (mainTexture), so anything placed before it would be discarded
    // silently. With it first, a de-dither/NTSC pre-pass still runs — just after the
    // upscale rather than before it (see nagAboutAnime4kOrder).
    // Everything else is signal -> scale -> look.
    private val comboOrder =
        listOf(
            "anime4k", "dedither", "ntsc", "fsr1", "sabr", "lanczos", "pixelaa",
            "cas", "crt", "lcdgrid", "bloom", "curve", "grade",
        )

    /** Anime4K forces itself to the front, which changes what a pre-pass does. Say so. */
    private fun nagAboutAnime4kOrder(chosen: List<String>) {
        if ("anime4k" in chosen && chosen.any { it == "dedither" || it == "ntsc" }) {
            Toast.makeText(this, R.string.combo_anime4k_order, Toast.LENGTH_LONG).show()
        }
    }

    private fun comboLabel(token: String): String = when (token) {
        "dedither" -> getString(R.string.filter_dedither)
        "ntsc" -> getString(R.string.filter_ntsc)
        "anime4k" -> getString(R.string.filter_anime4k)
        "fsr1" -> getString(R.string.filter_fsr1)
        "sabr" -> getString(R.string.filter_sabr)
        "lanczos" -> getString(R.string.filter_lanczos)
        "pixelaa" -> getString(R.string.filter_pixelaa)
        "cas" -> getString(R.string.filter_cas)
        "crt" -> getString(R.string.combo_crt)
        "lcdgrid" -> getString(R.string.filter_lcdgrid)
        "bloom" -> getString(R.string.filter_bloom)
        "curve" -> getString(R.string.filter_curve)
        "grade" -> getString(R.string.combo_grade)
        else -> token
    }

    private fun comboBuilder(token: String): com.nvanloo.retroglass.video.FilterStack.Builder? = when (token) {
        "dedither" -> com.nvanloo.retroglass.video.RetroShaders.deditherStage()
        "ntsc" -> com.nvanloo.retroglass.video.NtscShaders.stage(bleed = ntscBleed())
        "anime4k" -> com.nvanloo.retroglass.video.Anime4KShaders.stage()
        "fsr1" -> com.nvanloo.retroglass.video.FsrShaders.stage(layoutStore.filterSharpness(), upscale())
        "sabr" -> com.nvanloo.retroglass.video.SabrShaders.stage(upscale())
        "lanczos" -> com.nvanloo.retroglass.video.LanczosShaders.stage(upscale())
        "pixelaa" -> com.nvanloo.retroglass.video.PixelAaShaders.stage(upscale())
        "cas" -> com.nvanloo.retroglass.video.RetroShaders.casStage(layoutStore.filterSharpness())
        "crt" -> com.nvanloo.retroglass.video.RetroShaders.crtStage(scanDepth = scanlineDepth())
        "lcdgrid" -> com.nvanloo.retroglass.video.RetroShaders.lcdGridStage(depth = lcdGridDepth())
        "bloom" -> com.nvanloo.retroglass.video.RetroShaders.bloomStage(intensity = bloomAmount())
        "curve" -> com.nvanloo.retroglass.video.RetroShaders.curvatureStage(curve = curveAmount())
        "grade" -> com.nvanloo.retroglass.video.RetroShaders.gradeStage()
        else -> null
    }

    // Systems whose games are polygonal 3D — they want a spatial upscaler (FSR1); 2D
    // cartridge/pixel systems want the pattern scaler (SABR).
    private val consoles3D = setOf(
        Console.PSX, Console.PS2, Console.N64, Console.DREAMCAST, Console.SATURN,
        Console.PSP, Console.THREEDO, Console.NAOMI, Console.ATOMISWAVE,
    )

    // Small LCD panels: crisp pixels plus the dot-matrix grid reads most like the real thing.
    private val handhelds = setOf(
        Console.GAMEBOY, Console.GBA, Console.GAMEGEAR, Console.NGP,
        Console.WONDERSWAN, Console.LYNX, Console.POKEMONMINI,
    )

    /** The best filter chain for a console (the §6 per-system recipe): PS1 de-dithers before
     *  the upscale, other 3D systems get FSR1, 2D pixel-art gets SABR. */
    private fun recommendedCombo(c: Console): List<String> = when {
        c == Console.PSX -> listOf("dedither", "fsr1")
        c in consoles3D -> listOf("fsr1")
        c in handhelds -> listOf("pixelaa", "lcdgrid")
        else -> listOf("sabr")
    }

    /** The active filter: a stacked combo if one is set, otherwise the single filter. */
    private fun currentShaderConfig(): ShaderConfig {
        val combo = layoutStore.comboFilters(console)
        if (combo.isNotEmpty()) {
            val builders = comboOrder.filter { it in combo }.mapNotNull { comboBuilder(it) }
            if (builders.isNotEmpty()) return com.nvanloo.retroglass.video.FilterStack.compose(builders)
        }
        return shaderForIndex(layoutStore.shaderIndex(console))
    }

    // Rough GPU weight of each block (passes x how much area it renders), so the chaining
    // menu can warn before someone stacks two upscalers and a CRT on a 4x framebuffer.
    private fun comboCost(token: String): Int = when (token) {
        "anime4k" -> 8   // 6 passes, two of them at 2x
        "fsr1" -> 4      // EASU + RCAS at 2x
        "sabr" -> 3      // 21 taps at 2x
        "lanczos" -> 3   // 16 taps at 2x
        "crt", "bloom", "ntsc", "dedither" -> 2
        else -> 1
    }

    /** Display name of a single-filter index (see [shaderForIndex]). */
    private fun filterName(i: Int): String = getString(
        when (i) {
            1 -> R.string.filter_crt
            2 -> R.string.filter_lcd
            3 -> R.string.filter_sharp
            4 -> R.string.filter_upscale
            5 -> R.string.filter_anime4k
            6 -> R.string.filter_cas
            7 -> R.string.filter_fsr1
            8 -> R.string.filter_crtlottes
            9 -> R.string.filter_sabr
            10 -> R.string.filter_dedither
            11 -> R.string.filter_lanczos
            12 -> R.string.filter_pixelaa
            13 -> R.string.filter_ntsc
            14 -> R.string.filter_bloom
            15 -> R.string.filter_lcdgrid
            16 -> R.string.filter_curve
            else -> R.string.filter_off
        }
    )

    // The filter set grouped by what it does, so the (now long) list stays browsable.
    private val filterCategories: List<Pair<Int, List<Int>>> = listOf(
        R.string.filtercat_scale to listOf(3, 4, 11, 12, 6),
        R.string.filtercat_upscale to listOf(7, 9, 5),
        R.string.filtercat_crt to listOf(1, 8, 2, 15, 16),
        R.string.filtercat_signal to listOf(13, 10, 14),
    )

    private fun applySingleFilter(index: Int) {
        layoutStore.setShaderIndex(console, index)
        layoutStore.setComboFilters(console, emptyList()) // a single filter overrides any combo
        retroView?.shader = shaderForIndex(index)
    }

    /** Second level: the filters inside one category. */
    private fun showFilterCategory(titleRes: Int, indices: List<Int>) {
        gameMenu.pushSelect(
            getString(titleRes),
            indices.map { filterName(it) },
            indices.indexOf(layoutStore.shaderIndex(console)),
        ) { applySingleFilter(indices[it]) }
    }

    // Tunable look-filter parameters. The UI keeps everything on a 0..1 slider and each
    // accessor maps it onto that filter's useful range (defaults reproduce the tuned values).
    // One source of truth for the slider defaults — reading them anywhere else with a
    // guessed fallback silently bakes the wrong value into saved looks.
    private val paramDefaults = mapOf(
        "bloom" to 0.40f, "scanline" to 0.47f, "ntsc" to 0.50f,
        "lcdgrid" to 0.55f, "curve" to 0.50f,
    )

    private fun param(key: String) = layoutStore.filterParam(key, paramDefaults[key] ?: 0.5f)

    /**
     * Upscale factor the resampling scalers render at (2..4).
     *
     * On Auto it is derived from how tall the game actually appears on screen versus what the
     * system renders: a 240p NES game on a 1080p panel wants ~4×, a 480p Dreamcast game only
     * ~2×. Without this the scaler reconstructs well below the panel and hardware bilinear
     * blurs away the difference.
     */
    private fun upscale(): Float {
        val stored = layoutStore.upscaleFactor()
        if (stored != LayoutStore.UPSCALE_AUTO) return stored.toFloat()
        return autoUpscale().toFloat()
    }

    /** The factor Auto resolves to right now, 2..4. */
    private fun autoUpscale(): Int {
        val viewH = (retroView?.height ?: 0).takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        val src = console.nativeHeight.coerceAtLeast(1)
        return Math.round(viewH.toFloat() / src).coerceIn(2, 4)
    }

    private fun bloomAmount() = param("bloom") * 0.8f
    private fun scanlineDepth() = param("scanline") * 0.6f
    private fun ntscBleed() = param("ntsc") * 2.0f
    private fun lcdGridDepth() = param("lcdgrid") * 0.6f
    private fun curveAmount() = param("curve") * 2.0f

    /** How far the scalers render before the final blit. Higher = sharper, costs fill-rate. */
    private fun showUpscaleFactorPicker() {
        ensureMenu()
        val stored = layoutStore.upscaleFactor()
        gameMenu.pushSelect(menuTitle(R.string.menu_upscale_factor),
            listOf(
                getString(R.string.upscale_auto, autoUpscale(), console.displayName),
                getString(R.string.upscale_2x), getString(R.string.upscale_3x), getString(R.string.upscale_4x),
            ),
            if (stored == LayoutStore.UPSCALE_AUTO) 0 else stored - 1,
        ) { which ->
            layoutStore.setUpscaleFactor(if (which == 0) LayoutStore.UPSCALE_AUTO else which + 1)
            retroView?.shader = currentShaderConfig()
        }
    }

    // ------------------------------------------------------------ filter presets
    // A "look" is the whole filter state: the single filter, the chain, and every slider.
    // Serialised as key=value; pairs so new tunables can be added without breaking old saves.

    private fun currentLookBlob(): String {
        val parts = mutableListOf<String>()
        parts += "idx=" + layoutStore.shaderIndex(console)
        parts += "combo=" + layoutStore.comboFilters(console).joinToString(",")
        parts += "sharp=" + layoutStore.filterSharpness()
        paramDefaults.keys.forEach { parts += "$it=" + param(it) }
        return parts.joinToString(";")
    }

    private fun applyLookBlob(blob: String) {
        for (kv in blob.split(";")) {
            val i = kv.indexOf('=')
            if (i <= 0) continue
            val key = kv.substring(0, i)
            val value = kv.substring(i + 1)
            when (key) {
                "idx" -> value.toIntOrNull()?.let { layoutStore.setShaderIndex(console, it) }
                "combo" -> layoutStore.setComboFilters(
                    console, value.split(",").filter { it.isNotBlank() }
                )
                "sharp" -> value.toFloatOrNull()?.let { layoutStore.setFilterSharpness(it) }
                in paramDefaults -> value.toFloatOrNull()?.let { layoutStore.setFilterParam(key, it) }
            }
        }
        retroView?.shader = currentShaderConfig()
    }

    private fun promptSavePreset() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.preset_name_hint)
            setPadding(56, 40, 56, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.preset_save)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                layoutStore.savePreset(name, currentLookBlob())
                Toast.makeText(this, getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    private fun promptDeletePreset() {
        val names = layoutStore.presetNames()
        if (names.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.preset_delete)
            .setItems(names.toTypedArray()) { _, which -> layoutStore.deletePreset(names[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    /** Saved looks: apply one, save the current state, or delete. */
    private fun showFilterPresets() {
        val names = layoutStore.presetNames()
        ensureMenu()
        gameMenu.push(menuTitle(R.string.menu_filter_presets)) {
            with(gameMenu) {
                body {
                    for (name in names) {
                        addView(navRow(null, name) {
                            layoutStore.loadPreset(name)?.let {
                                applyLookBlob(it)
                                Toast.makeText(this@EmulationActivity,
                                    getString(R.string.preset_applied, name), Toast.LENGTH_SHORT).show()
                            }
                            pop()
                        })
                    }
                    addView(bigButton(getString(R.string.preset_save), tint = true) { promptSavePreset() })
                    if (names.isNotEmpty()) {
                        addView(bigButton(getString(R.string.preset_delete), danger = true) { promptDeletePreset() })
                    }
                }
            }
        }
    }

    /** Opens the menu first when a picker is reached from a hotkey rather than the menu. */
    private fun ensureMenu() { if (!gameMenu.isOpen) showMenu() }

    /**
     * A screen's heading, from the label of the row that opens it.
     *
     * Rows say "Filter settings…" and "Core options (system settings)" because they have to
     * announce what tapping them does. A heading is already the answer to that, so it drops the
     * trailing ellipsis and the qualifier in brackets.
     */
    private fun menuTitle(res: Int): String =
        getString(res).substringBefore(" (").trimEnd('…', '.', ' ')

    private fun showVideoFilterPicker() {
        ensureMenu()
        gameMenu.push(menuTitle(R.string.menu_video_filter)) { menuFilterPickerScreen() }
    }

    private fun menuFilterPickerScreen(): View = with(gameMenu) {
        val active = layoutStore.shaderIndex(console)
        val chained = layoutStore.comboFilters(console).isNotEmpty()
        body {
            addView(selectRow(filterName(0), !chained && active == 0) {
                applySingleFilter(0); pop()
            })
            for ((titleRes, indices) in filterCategories) {
                // Show which filter inside a category is the live one, so you can see where
                // the current look came from without opening every category.
                val here = !chained && active in indices
                addView(navRow(null, getString(titleRes), if (here) filterName(active) else null) {
                    showFilterCategory(titleRes, indices)
                })
            }
            addView(navRow(null, getString(R.string.menu_combine_filters),
                if (chained) getString(R.string.menu_combo_count, layoutStore.comboFilters(console).size) else null) {
                push(menuTitle(R.string.menu_combine_filters)) { menuChainScreen() }
            })
            addView(bigButton(getString(R.string.filter_recommended, console.displayName), tint = true) {
                applyRecommended(); pop()
            })
        }
    }

    /** One-tap best chain for this system (de-dither+FSR1 / FSR1 / SABR). */
    private fun applyRecommended() {
        layoutStore.setComboFilters(console, recommendedCombo(console))
        retroView?.shader = currentShaderConfig()
        Toast.makeText(this, R.string.filter_recommended_applied, Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------- internal resolution boost
    // Shader upscalers reconstruct detail; raising the core's own render resolution
    // recovers *real* detail, and is the single biggest quality win on 3D systems. The
    // right option key differs per core, so pick whichever one this core actually exposes.
    // label -> (key, "boosted" value, native value)
    private val resolutionOptions = listOf(
        Triple("pcsx_rearmed_neon_enhancement_enable", "enabled", "disabled"),
        Triple("ppsspp_internal_resolution", "960x544", "480x272"),
        Triple("mupen64plus-43screensize", "640x480", "320x240"),
        Triple("flycast_internal_resolution", "1280x960", "640x480"),
        Triple("reicast_internal_resolution", "1280x960", "640x480"),
    )

    /** The resolution option this core exposes, if any. */
    private fun resolutionOption(): Triple<String, String, String>? {
        val keys = retroView?.getVariables()?.mapNotNull { it.key }?.toSet() ?: return null
        return resolutionOptions.firstOrNull { it.first in keys }
    }

    /** One tap: render the game internally at 2x, then let the shader chain scale that. */
    private fun toggleInternalResolution() {
        val opt = resolutionOption()
        if (opt == null) {
            Toast.makeText(this, R.string.res_boost_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val (key, boosted, native) = opt
        val current = coreOptions.override(consoleKey, key)
        val target = if (current == boosted) native else boosted
        applyCoreOption(key, target)
        Toast.makeText(
            this,
            getString(if (target == boosted) R.string.res_boost_on else R.string.res_boost_off),
            Toast.LENGTH_LONG,
        ).show()
    }

    // ---------------------------------------------------------- core options

    /**
     * A core option's human title and its choices, both parsed out of the libretro
     * `description` field, which the core formats as `"RDP Plugin; gliden64|angrylion|parallel"`.
     * That means every option can be a pick-list — the old dialog made you type the raw value
     * ("angrylion") by hand, which is how the ugliest surface in the app got that way.
     * The first choice is the core's own default.
     */
    private data class CoreOpt(val key: String, val title: String, val choices: List<String>)

    private fun parseCoreOpt(key: String, description: String): CoreOpt {
        val semi = description.indexOf(';')
        if (semi < 0) return CoreOpt(key, description.ifBlank { key }, emptyList())
        val title = description.substring(0, semi).trim().ifBlank { key }
        val choices = description.substring(semi + 1).trim()
            .split('|').map { it.trim() }.filter { it.isNotEmpty() }
        return CoreOpt(key, title, choices)
    }

    /** Lists the core's live options (real keys/values from the core), each editable. */
    private fun showCoreOptions() {
        val view = retroView ?: return
        val vars = view.getVariables()
        if (vars.isEmpty()) {
            Toast.makeText(this, R.string.core_options_none, Toast.LENGTH_LONG).show()
            return
        }
        if (!gameMenu.isOpen) showMenu()
        gameMenu.push(menuTitle(R.string.menu_core_options)) { menuCoreOptionsScreen(vars) }
    }

    private fun menuCoreOptionsScreen(
        vars: Array<com.swordfish.libretrodroid.Variable>,
    ): View = with(gameMenu) {
        val opts = vars.map { parseCoreOpt(it.key ?: "", it.description ?: "") }
        val liveValue = vars.associate { (it.key ?: "") to (it.value ?: "") }
        val list = LinearLayout(this@EmulationActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun populate(query: String) {
            list.removeAllViews()
            val stored = coreOptions.overrides(consoleKey)
            val q = query.trim().lowercase()
            val matches = opts.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.key.contains(q) }
            val changed = matches.filter { stored.containsKey(it.key) }
            val rest = matches.filterNot { stored.containsKey(it.key) }

            fun addRow(o: CoreOpt, isChanged: Boolean) {
                val current = stored[o.key] ?: liveValue[o.key] ?: o.choices.firstOrNull() ?: ""
                list.addView(
                    valueRow(o.title, valueLabel(o.key, current), isChanged) {
                        push(o.title) { menuCoreValueScreen(o, current) }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = (9 * resources.displayMetrics.density).toInt() },
                )
            }
            if (changed.isNotEmpty()) {
                list.addView(group(
                    getString(R.string.menu_core_changed),
                    getString(R.string.menu_core_changed_count, changed.size),
                    trailingLive = true,
                ))
                changed.forEach { addRow(it, true) }
            }
            list.addView(group(
                getString(R.string.menu_core_all),
                getString(R.string.menu_core_option_count, rest.size),
            ))
            rest.forEach { addRow(it, false) }
        }

        populate("")
        body(padSides = 16f) {
            addView(searchField(getString(R.string.menu_core_search, opts.size)) { populate(it) })
            addView(list)
            addView(bigButton(getString(R.string.core_option_reset_all), danger = true) {
                coreOptions.clear(consoleKey)
                Toast.makeText(this@EmulationActivity, R.string.core_options_note, Toast.LENGTH_LONG).show()
                populate("")
            })
        }
    }

    /** One option's choices as a pick-list, with a text fallback for free-form options. */
    private fun menuCoreValueScreen(opt: CoreOpt, current: String): View = with(gameMenu) {
        // A curated list wins when we have one (it carries friendlier labels than the raw values).
        val known = com.nvanloo.retroglass.controller.CoreOptions.KNOWN_VALUES[opt.key]
        val pairs: List<Pair<String, String>> = known
            ?: opt.choices.map { it to it }
        body {
            if (pairs.isEmpty()) {
                addView(navRow(null, getString(R.string.menu_core_type_value), current) {
                    editCoreOption(opt.key, opt.title)
                })
            } else {
                for ((label, value) in pairs) {
                    addView(selectRow(label, value == current) {
                        applyCoreOption(opt.key, value)
                        pop()
                    })
                }
            }
        }
    }

    /** Human label for a stored value (uses the curated list when the value is known). */
    private fun valueLabel(key: String, value: String): String =
        com.nvanloo.retroglass.controller.CoreOptions.KNOWN_VALUES[key]
            ?.firstOrNull { it.second == value }?.first ?: value

    private fun editCoreOption(key: String, description: String) {
        val known = com.nvanloo.retroglass.controller.CoreOptions.KNOWN_VALUES[key]
        if (known != null) {
            val labels = known.map { it.first }.toTypedArray()
            val current = coreOptions.override(consoleKey, key)
            val checked = known.indexOfFirst { it.second == current }
            AlertDialog.Builder(this)
                .setTitle(description.ifBlank { key })
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    applyCoreOption(key, known[which].second)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show().gamepadNavigable()
        } else {
            val input = android.widget.EditText(this).apply {
                setText(coreOptions.override(consoleKey, key) ?: "")
                hint = getString(R.string.core_option_enter, key)
            }
            AlertDialog.Builder(this)
                .setTitle(description.ifBlank { key })
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val v = input.text.toString().trim()
                    if (v.isNotEmpty()) applyCoreOption(key, v)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show().gamepadNavigable()
        }
    }

    private fun applyCoreOption(key: String, value: String) {
        coreOptions.setOverride(consoleKey, key, value)
        retroView?.updateVariables(com.swordfish.libretrodroid.Variable(key, value, ""))
        Toast.makeText(this, R.string.core_options_note, Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------- cheats

    private fun applyCheats() {
        val view = retroView ?: return
        cheats.list(gameKey).forEachIndexed { i, c ->
            runCatching { view.setCheat(i, c.enabled, c.code) }
        }
    }

    // ---------------------------------------------------------- controller type

    /** Re-applies any saved controller-type choices (e.g. PS1 analog) to their ports. */
    private fun applyControllerTypes() {
        val view = retroView ?: return
        val ports = view.getControllers()
        for (port in ports.indices) {
            val typeId = inputConfig.controllerType(consoleKey, port) ?: continue
            if (ports[port].any { it.id == typeId }) {
                runCatching { view.setControllerType(port, typeId) }
            }
        }
    }

    private val multitapKeywords = listOf(
        "multitap", "multi tap", "multi-tap", "four score", "fourscore",
        "4-way", "4 way", "teamplayer", "team player", "j-cart", "jcart", "5-player", "multi5",
    )

    /** When 3+ controllers are assigned, arm the console's multitap so ports 3/4 reach the game.
     *  Cores that expose the multitap as a port device (SNES/NES/Genesis/Saturn/PC Engine) list
     *  it in getControllers(); we match it by description and select it. Option-based cores
     *  (PS1's pcsx_rearmed) were already handled via forced variables at load. */
    private fun applyMultitap() {
        val view = retroView ?: return
        val count = activePortCount()
        if (count <= 2) return
        var armed = console.multitapCoreVariables.isNotEmpty()
        val ports = view.getControllers()
        for (port in ports.indices) {
            val tap = ports[port].firstOrNull { t ->
                val d = (t.description ?: "").lowercase()
                multitapKeywords.any { d.contains(it) }
            } ?: continue
            if (runCatching { view.setControllerType(port, tap.id) }.isSuccess) armed = true
        }
        if (armed) {
            Toast.makeText(this, getString(R.string.multitap_armed, count), Toast.LENGTH_SHORT).show()
        }
    }

    /** True when at least one port offers more than one controller type worth choosing. */
    private fun hasControllerTypeChoices(): Boolean =
        retroView?.getControllers()?.any { it.size > 1 } == true

    private fun showControllerTypes() {
        val view = retroView ?: return
        val ports = view.getControllers()
        val selectablePorts = ports.indices.filter { ports[it].size > 1 }
        if (selectablePorts.isEmpty()) {
            Toast.makeText(this, R.string.ctype_none, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectablePorts.size == 1) {
            showControllerTypeForPort(selectablePorts.first())
            return
        }
        ensureMenu()
        gameMenu.pushActions(menuTitle(R.string.ctype_title),
            selectablePorts.map { port ->
                getString(R.string.player_n, port + 1) to { showControllerTypeForPort(port) }
            },
        )
    }

    private fun showControllerTypeForPort(port: Int) {
        val view = retroView ?: return
        val types = view.getControllers().getOrNull(port) ?: return
        val labels = types.map { it.description ?: "Type ${it.id}" }.toTypedArray()
        val currentId = inputConfig.controllerType(consoleKey, port)
        val checked = types.indexOfFirst { it.id == currentId }
        ensureMenu()
        gameMenu.pushSelect(getString(R.string.ctype_port_title, port + 1), labels.toList(), checked) { which ->
            val id = types[which].id
            inputConfig.setControllerType(consoleKey, port, id)
            runCatching { view.setControllerType(port, id) }
            Toast.makeText(this, getString(R.string.ctype_set, labels[which]), Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------- display & extras

    private fun showDisplayExtras() {
        ensureMenu()
        gameMenu.push(menuTitle(R.string.menu_display_extras)) {
            with(gameMenu) {
                body {
                    addView(toggleRow(getString(R.string.menu_fps_label), layoutStore.fpsOverlay()) {
                        layoutStore.setFpsOverlay(it)
                    })
                    addView(toggleRow(getString(R.string.menu_gyro_label), layoutStore.gyroAim()) {
                        toggleGyro(); gameMenu.refresh()
                    })
                    if (tiltSource.isAvailable) {
                        addView(toggleRow(
                            getString(R.string.menu_tilt_shadows), layoutStore.tiltShadows(),
                        ) { on ->
                            layoutStore.setTiltShadows(on)
                            updateTiltShadows()
                        })
                    }
                    if (layoutStore.gyroAim()) {
                        addView(slider(
                            getString(R.string.menu_gyro_sensitivity),
                            // 0.2..3.0 mapped onto the slider's 0..1
                            ((layoutStore.gyroSensitivity() - 0.2f) / 2.8f).coerceIn(0f, 1f),
                            format = { "%.1f×".format(0.2f + it * 2.8f) },
                        ) { layoutStore.setGyroSensitivity(0.2f + it * 2.8f) })
                    }
                    addView(navRow(null, getString(R.string.menu_bezel), bezelLabel()) { showBezelPicker() })
                }
            }
        }
    }

    private fun bezelLabel(): String = getString(when (layoutStore.bezelMode()) {
        0 -> R.string.bezel_none
        1 -> R.string.bezel_dark
        2 -> R.string.bezel_gradient
        3 -> R.string.bezel_custom
        else -> R.string.bezel_body
    })

    private fun toggleGyro() {
        val enabling = !layoutStore.gyroAim()
        layoutStore.setGyroAim(enabling)
        updateGyro()
        if (enabling && sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) == null) {
            Toast.makeText(this, R.string.gyro_unavailable, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                if (enabling) R.string.gyro_enabled else R.string.gyro_disabled,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun showBezelPicker() {
        ensureMenu()
        val labels = arrayOf(
            getString(R.string.bezel_none),
            getString(R.string.bezel_dark),
            getString(R.string.bezel_gradient),
            getString(R.string.bezel_custom),
            getString(R.string.bezel_body),
        )
        gameMenu.pushSelect(menuTitle(R.string.menu_bezel), labels.toList(), layoutStore.bezelMode()) { which ->
            if (which == 3) {
                gameMenu.close()
                pickBezelImage.launch(arrayOf("image/*"))
            } else {
                layoutStore.setBezelMode(which)
                applyBezel()
            }
        }
    }

    private fun showCheats() {
        val list = cheats.list(gameKey)
        val labels = list.map { (if (it.enabled) "☑ " else "☐ ") + it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cheats_title, console.displayName))
            .apply {
                if (list.isEmpty()) setMessage(R.string.cheats_none)
                else setItems(labels) { _, which -> toggleCheat(which) }
            }
            .setPositiveButton(R.string.cheat_add) { _, _ -> showAddCheat() }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    private fun toggleCheat(index: Int) {
        val list = cheats.list(gameKey)
        val c = list.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setMessage(c.code)
            .setPositiveButton(if (c.enabled) "Disable" else "Enable") { _, _ ->
                cheats.setEnabled(gameKey, index, !c.enabled)
                reapplyCheats()
                showCheats()
            }
            .setNeutralButton(R.string.cheat_delete) { _, _ ->
                cheats.removeAt(gameKey, index)
                reapplyCheats()
                showCheats()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showCheats() }
            .show().gamepadNavigable()
    }

    /** Cheats can't be individually cleared, so reset and re-apply the whole enabled set. */
    private fun reapplyCheats() {
        val view = retroView ?: return
        // setCheat with empty code + disabled clears a slot; clear a generous range then re-add.
        for (i in 0 until 64) runCatching { view.setCheat(i, false, "") }
        applyCheats()
    }

    private fun showAddCheat() {
        val density = resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val name = android.widget.EditText(this).apply { hint = getString(R.string.cheat_name) }
        val code = android.widget.EditText(this).apply {
            hint = getString(R.string.cheat_code)
            minLines = 2
            setHorizontallyScrolling(false)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(name)
            addView(code)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.cheat_add)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val codeText = code.text.toString().trim()
                if (codeText.isEmpty()) {
                    Toast.makeText(this, R.string.cheat_need_code, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val nameText = name.text.toString().trim().ifEmpty { "Cheat ${cheats.list(gameKey).size + 1}" }
                cheats.add(gameKey, com.nvanloo.retroglass.controller.Cheat(nameText, codeText, true))
                reapplyCheats()
                showCheats()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showCheats() }
            .show().gamepadNavigable()
    }

    // ---------------------------------------------------------- screenshot

    private fun takeScreenshot() {
        val view = retroView ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            view.width.coerceAtLeast(1), view.height.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        android.view.PixelCopy.request(view, bitmap, { result ->
            if (result == android.view.PixelCopy.SUCCESS && saveBitmapToPictures(bitmap)) {
                runOnUiThread { Toast.makeText(this, R.string.screenshot_saved, Toast.LENGTH_SHORT).show() }
            } else {
                runOnUiThread { Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show() }
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private fun saveBitmapToPictures(bitmap: android.graphics.Bitmap): Boolean = runCatching {
        val name = romFile.nameWithoutExtension + "-" + System.currentTimeMillis() + ".png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/RetroGlass",
                )
            }
        }
        val uri = contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ) ?: return false
        contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        true
    }.getOrDefault(false)

    /** Lets the user mark buttons as turbo/autofire (held = rapid repeat). */
    private fun showTurboConfig() {
        val buttons = controllerView.toggleableButtons()
        if (buttons.isEmpty()) {
            Toast.makeText(this, R.string.turbo_none, Toast.LENGTH_SHORT).show()
            return
        }
        ensureMenu()
        gameMenu.push(menuTitle(R.string.menu_turbo)) {
            with(gameMenu) {
                body {
                    for ((id, label) in buttons) {
                        addView(toggleRow(label, id in layoutStore.turboButtons(console)) { on ->
                            layoutStore.setTurbo(console, id, on)
                            // Applied immediately - the old dialog only committed on OK, so a
                            // back-gesture silently dropped the change.
                            controllerView.turboIds = layoutStore.turboButtons(console)
                        })
                    }
                }
            }
        }
    }

    private fun toggleFastForward() {
        fastForward = !fastForward
        retroView?.frameSpeed = if (fastForward) 2 else 1
        Toast.makeText(
            this,
            getString(if (fastForward) R.string.fast_forward_enabled else R.string.fast_forward_disabled),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showLayoutPicker() {
        val presets = controllerView.availablePresets()
        val current = presets.indexOfFirst { it.id == controllerView.currentPresetId() }
            .coerceAtLeast(0)

        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val previewW = dp(76f)
        val previewH = (previewW * com.nvanloo.retroglass.controller.LayoutPreview.ASPECT).toInt()

        val list = android.widget.ListView(this).apply {
            choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            divider = null
            dividerHeight = 0
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = presets.size
            override fun getItem(position: Int) = presets[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(this@EmulationActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                    addView(android.widget.ImageView(this@EmulationActivity).apply {
                        id = android.R.id.icon
                        layoutParams = LinearLayout.LayoutParams(previewW, previewH)
                        setBackgroundColor(Color.parseColor("#11000000"))
                    })
                    addView(TextView(this@EmulationActivity).apply {
                        id = android.R.id.text1
                        textSize = 17f
                        setPadding(dp(16f), 0, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                }
                val preset = presets[position]
                val img = row.findViewById<android.widget.ImageView>(android.R.id.icon)
                img.setImageBitmap(
                    com.nvanloo.retroglass.controller.LayoutPreview.render(
                        console, preset.controls, previewW, previewH,
                    )
                )
                val label = row.findViewById<TextView>(android.R.id.text1)
                label.text = if (position == current) "${preset.name}  ✓" else preset.name
                return row
            }
        }
        list.adapter = adapter
        list.setItemChecked(current, true)
        list.setSelection(current)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.menu_choose_layout)
            .setView(list)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, which, _ ->
            controllerView.setPreset(presets[which].id)
            dialog.dismiss()
        }
        dialog.show()
        dialog.gamepadNavigable()
    }

    private fun showScreenSizeDialog() {
        ensureMenu()
        gameMenu.push(menuTitle(R.string.menu_screen_size)) { menuScreenSizeScreen() }
    }

    /**
     * Size, rotation and nudge for the picture. Portrait splits the screen with the pad so it
     * gets a height share; every other mode scales the picture inside its own box.
     */
    private fun menuScreenSizeScreen(): View = with(gameMenu) {
        val portrait = isPortrait() && !extendedMode && !phoneIsDisplay()
        val editingExternal = extendedMode || phoneIsDisplay()
        body(padSides = 18f) {
            // Each slider prints its own unit: a bare 0..1 position next to a label that
            // already quotes a percentage reads as two different numbers for one control.
            if (portrait) {
                val frac = layoutStore.portraitScreenFraction()
                addView(slider(
                    getString(R.string.screen_height_label),
                    ((frac - 0.25f) / 0.45f).coerceIn(0f, 1f),
                    format = { "${(25 + it * 45).toInt()}%" },
                ) {
                    layoutStore.setPortraitScreenFraction(0.25f + it * 0.45f)
                    arrangeLayout()
                    updateScreenBezel()
                })
            } else {
                val minScale = if (editingExternal) 0.4f else 0.3f
                val cur = if (editingExternal) videoScale else videoScaleLocal
                addView(slider(
                    getString(R.string.screen_size_label),
                    ((cur - minScale) / (1f - minScale)).coerceIn(0f, 1f),
                    format = { "${((minScale + it * (1f - minScale)) * 100).toInt()}%" },
                ) {
                    val value = minScale + it * (1f - minScale)
                    if (editingExternal) videoScale = value else videoScaleLocal = value
                    if (editingExternal) layoutStore.setVideoScale(value)
                    else layoutStore.setLocalVideoScale(value)
                    applyVideoTransform()
                    updateScreenBezel()
                })
            }
            addView(navRow(
                null, getString(R.string.screen_rotate, layoutStore.videoRotation()),
                null, chevron = false,
            ) {
                layoutStore.setVideoRotation(layoutStore.videoRotation() + 90)
                applyVideoTransform()
                updateScreenBezel()
                gameMenu.refresh()
            })
            addView(slider(
                getString(R.string.screen_pos_h), layoutStore.videoOffsetX() + 0.5f,
                format = { "${((it - 0.5f) * 100).toInt()}" },
            ) {
                layoutStore.setVideoOffset(it - 0.5f, layoutStore.videoOffsetY())
                applyVideoTransform()
                updateScreenBezel()
            })
            addView(slider(
                getString(R.string.screen_pos_v), layoutStore.videoOffsetY() + 0.5f,
                format = { "${((it - 0.5f) * 100).toInt()}" },
            ) {
                layoutStore.setVideoOffset(layoutStore.videoOffsetX(), it - 0.5f)
                applyVideoTransform()
                updateScreenBezel()
            })
        }
    }


    private fun setEditMode(enabled: Boolean) {
        controllerView.editMode = enabled
        editBar.visibility = if (enabled) View.VISIBLE else View.GONE
        updateTiltShadows()
    }

    private val manualSlots = listOf(1, 2, 3, 4)

    /** Slot 0 is the auto-save written on exit; 1..4 are manual. */
    private fun stateFile(slot: Int) = File(
        RomLibrary.statesDir(this),
        romFile.name + if (slot == 0) ".auto.state" else ".slot$slot.state",
    )

    private fun sramFile() = File(RomLibrary.savesDir(this), romFile.name + ".srm")

    private fun slotLabel(slot: Int): String {
        val used = if (stateFile(slot).exists()) getString(R.string.slot_used) else getString(R.string.slot_empty)
        return getString(R.string.slot_n, slot, used)
    }

    private fun saveState() {
        val names = manualSlots.map { slotLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_save_state)
            .setItems(names) { _, which -> doSaveState(manualSlots[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    private fun doSaveState(slot: Int) {
        runCatching {
            retroView?.serializeState()?.let { stateFile(slot).writeBytes(it) }
        }.onSuccess {
            Toast.makeText(this, R.string.state_saved, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, R.string.state_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadState() {
        val slots = buildList {
            addAll(manualSlots.filter { stateFile(it).exists() })
            if (stateFile(0).exists()) add(0)
        }
        if (slots.isEmpty()) {
            Toast.makeText(this, R.string.no_state, Toast.LENGTH_SHORT).show()
            return
        }
        val names = slots.map {
            if (it == 0) getString(R.string.slot_auto) else getString(R.string.slot_n, it, "")
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_load_state)
            .setItems(names) { _, which -> doLoadState(slots[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    private fun doLoadState(slot: Int) {
        runCatching { retroView?.unserializeState(stateFile(slot).readBytes()) }
        Toast.makeText(this, R.string.state_loaded, Toast.LENGTH_SHORT).show()
    }

    /** Resumes from the auto-save written when the game was last exited. */
    private fun autoLoadState() {
        val f = stateFile(0)
        if (f.exists()) runCatching { retroView?.unserializeState(f.readBytes()) }
    }

    private fun autoSaveState() {
        runCatching {
            retroView?.serializeState(false)?.let { if (it.isNotEmpty()) stateFile(0).writeBytes(it) }
        }
    }

    private fun persistSram() {
        runCatching {
            val sram = retroView?.serializeSRAM() ?: return
            if (sram.isNotEmpty()) sramFile().writeBytes(sram)
        }
    }

    private fun exitGame() {
        persistSram()
        autoSaveState()
        finish()
    }

    // ------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        displayManager.registerDisplayListener(displayListener, null)
        mediaRouter.addCallback(
            android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mediaRouterCallback,
        )
        inputManager.registerInputDeviceListener(inputDeviceListener, null)
        updateForGamepad(false)
        // The topology may have changed while we were paused.
        applyDisplayMode("onResume")
        // Keep a disconnect-pause in effect even after the lifecycle auto-resumed the view.
        if (pausedByDisconnect) retroView?.onPause()
        updateGyroAndTilt()
    }

    override fun onPause() {
        displayManager.unregisterDisplayListener(displayListener)
        mediaRouter.removeCallback(mediaRouterCallback)
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
        if (gyroRegistered) {
            sensorManager.unregisterListener(gyroListener)
            gyroRegistered = false
        }
        tiltSource.stop()
        persistSram()
        autoSaveState()
        super.onPause()
    }

    override fun onDestroy() {
        menuDialog?.dismiss()
        menuDialog = null
        externalPromptDialog?.dismiss()
        externalPromptDialog = null
        uiHandler.removeCallbacksAndMessages(null)
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ presentation

    @SuppressLint("NewApi")
    private class GamePresentation(context: Context, display: Display) :
        Presentation(context, display) {

        private lateinit var container: FrameLayout
        private lateinit var errorView: TextView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            container = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
            errorView = TextView(context).apply {
                setTextColor(Color.parseColor("#FFCDD2"))
                setBackgroundColor(Color.parseColor("#CC5A1A1A"))
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(64, 48, 64, 48)
                visibility = View.GONE
            }
            container.addView(
                errorView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            setContentView(container)
        }

        fun attach(view: View) {
            container.addView(
                view,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        fun showError(message: String) {
            errorView.text = message
            errorView.visibility = View.VISIBLE
            errorView.bringToFront()
        }
    }
}
