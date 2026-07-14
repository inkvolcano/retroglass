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
import com.nvanloo.retroglass.controller.ControllerView
import com.nvanloo.retroglass.controller.LayoutStore
import com.nvanloo.retroglass.model.Console
import com.nvanloo.retroglass.model.RomLibrary
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
    private lateinit var layoutStore: LayoutStore
    private lateinit var inputConfig: com.nvanloo.retroglass.controller.InputConfig
    private var bindingCaptureDevice: String? = null
    private var bindingCapture: ((Int) -> Unit)? = null
    private var videoScale: Float = 1.0f
    private var videoScaleLocal: Float = 0.62f
    private var fastForward = false

    private lateinit var rootLayout: FrameLayout
    private lateinit var gameContainer: FrameLayout
    private lateinit var controllerView: ControllerView
    private lateinit var editBar: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var phoneErrorText: TextView
    private lateinit var loadingText: TextView
    private lateinit var pauseOverlay: TextView
    private var firstFrameSeen = false
    private var pausedByDisconnect = false

    private val extendedMode: Boolean get() = presentation != null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = applyDisplayMode("displayAdded $displayId")
        override fun onDisplayRemoved(displayId: Int) = applyDisplayMode("displayRemoved $displayId")
        override fun onDisplayChanged(displayId: Int) = applyDisplayMode("displayChanged $displayId")
    }

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
        controllerView.visibility = if (phonePort() >= 0) View.VISIBLE else View.GONE
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
            val capture = bindingCapture
            if (capture != null && bindingCaptureDevice == key && event.action == KeyEvent.ACTION_DOWN &&
                androidToRetroKey(event.keyCode) != null
            ) {
                bindingCapture = null
                bindingCaptureDevice = null
                capture(event.keyCode)
                return true
            }
            val port = portFor(key, event.device)
            if (port < 0) return true
            val retroKey = inputConfig.bindings(key)[event.keyCode] ?: androidToRetroKey(event.keyCode)
            if (retroKey != null && event.action != KeyEvent.ACTION_MULTIPLE) {
                v.sendKeyEvent(event.action, retroKey, port)
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
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val lx = event.getAxisValue(MotionEvent.AXIS_X)
            val ly = event.getAxisValue(MotionEvent.AXIS_Y)
            if (inputConfig.leftStickAsDpad(key)) {
                val dx = if (lx > 0.5f) 1f else if (lx < -0.5f) -1f else hatX
                val dy = if (ly > 0.5f) 1f else if (ly < -0.5f) -1f else hatY
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dx, dy, port)
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, port)
            } else {
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatX, hatY, port)
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, lx, ly, port)
            }
            v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ), port)
            return true
        }
        return super.onGenericMotionEvent(event)
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
        inputConfig = com.nvanloo.retroglass.controller.InputConfig(this)
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
            override fun handleOnBackPressed() = showMenu()
        })

        applyDisplayMode("onCreate")
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

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        rootLayout = FrameLayout(this)
        gameContainer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        controllerView = ControllerView(this).apply {
            setConsole(console)
            listener = inputListener
            onLayoutEdited = { }
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
        editBar = buildEditBar()

        rootLayout.addView(gameContainer, matchParent())
        rootLayout.addView(controllerView, matchParent())
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
        rootLayout.addView(pauseOverlay, matchParent())
        setContentView(rootLayout)
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
            shader = shaderForIndex(layoutStore.shaderIndex())
            sramFile().takeIf { it.exists() }?.let { saveRAMState = it.readBytes() }
        }
        val view = GLRetroView(this, data)
        lifecycle.addObserver(view)

        view.getGLRetroEvents()
            .onEach { event ->
                if (event is GLRetroView.GLRetroEvents.FrameRendered && !firstFrameSeen) {
                    firstFrameSeen = true
                    runOnUiThread {
                        loadingText.visibility = View.GONE
                        autoLoadState()
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
        val external = findExternalDisplay()
        val currentPresentationDisplayId = presentation?.display?.displayId
        Log.i(
            TAG,
            "applyDisplayMode($reason): external=${external?.displayId} " +
                "presentationOn=$currentPresentationDisplayId",
        )

        when {
            // Nothing external: game belongs on the phone.
            external == null -> {
                // Coming off the glasses: bring the game back and auto-pause so it
                // doesn't keep running unwatched.
                if (presentation != null) {
                    moveGameToPhone(view)
                    pauseForDisconnect()
                }
            }
            // External present and game not yet on it: move it there.
            presentation == null -> {
                moveGameToPresentation(view, external)
                resumeFromPause()
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

    private fun isPortrait(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    /**
     * Positions the game and controller for the current display + orientation:
     *  - glasses: game on the external display, controller fills the phone
     *  - portrait, no glasses: game as a screen across the top, controller below it
     *  - landscape, no glasses: game centered/scaled with the controller as an overlay
     */
    private fun arrangeLayout() {
        if (extendedMode) {
            gameContainer.visibility = View.GONE
            controllerView.overlayMode = false
            setRegion(controllerView, fraction = 1f, top = false)
            applyVideoTransform()
            return
        }
        gameContainer.visibility = View.VISIBLE
        if (isPortrait()) {
            controllerView.overlayMode = false
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
            setRegion(gameContainer, fraction = 1f, top = false)
            setRegion(controllerView, fraction = 1f, top = false)
            applyVideoTransform()
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
    private fun applyVideoTransform() {
        val v = retroView ?: return
        val parent = v.parent as? FrameLayout ?: return
        val scale = when {
            extendedMode -> videoScale
            isPortrait() -> 1.0f
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

    /** Display.getType() is not public API; on failure assume internal (safer). */
    private fun isInternalDisplay(display: Display): Boolean = runCatching {
        val type = Display::class.java.getMethod("getType").invoke(display) as Int
        type == 1 // Display.TYPE_INTERNAL
    }.getOrDefault(true)

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
            // "cbuttons" is the N64 C-cluster, which the core reads as the right analog.
            val source = if (id == "stick_r" || id == "cbuttons") {
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
            } else {
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT
            }
            retroView?.sendMotionEvent(source, x, y, phonePort().coerceAtLeast(0))
        }

        override fun onMenu() = showMenu()
    }

    // ------------------------------------------------------------ menu

    private fun showMenu() {
        if (controllerView.editMode) return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.menu_save_state) to { saveState() }
        actions += getString(R.string.menu_load_state) to { loadState() }
        actions += getString(
            if (fastForward) R.string.menu_fast_forward_on else R.string.menu_fast_forward_off,
        ) to { toggleFastForward() }
        actions += getString(R.string.menu_screen_size) to { showScreenSizeDialog() }
        actions += getString(R.string.menu_video_filter) to { showVideoFilterPicker() }
        if ((retroView?.getAvailableDisks() ?: 0) > 1) {
            actions += getString(R.string.menu_swap_disc) to { showDiscSwap() }
        }
        actions += getString(R.string.menu_choose_layout) to { showLayoutPicker() }
        actions += getString(R.string.menu_edit_layout) to { setEditMode(true) }
        actions += getString(
            if (layoutStore.rumbleEnabled()) R.string.menu_rumble_on else R.string.menu_rumble_off,
        ) to { layoutStore.setRumbleEnabled(!layoutStore.rumbleEnabled()) }
        actions += getString(R.string.menu_controllers) to { showControllers() }
        actions += getString(R.string.menu_reset) to { retroView?.reset(); Unit }
        actions += getString(R.string.menu_exit) to { exitGame() }

        val names = actions.map { it.first }.toTypedArray()
        menuDialog?.dismiss()
        menuDialog = AlertDialog.Builder(this)
            .setTitle(romFile.nameWithoutExtension)
            .setItems(names) { _, which -> actions[which].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDiscSwap() {
        val v = retroView ?: return
        val count = v.getAvailableDisks()
        if (count <= 1) return
        val current = v.getCurrentDisk()
        val names = (0 until count).map {
            getString(R.string.disc_n, it + 1) + if (it == current) " ✓" else ""
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_swap_disc)
            .setSingleChoiceItems(names, current) { dialog, which ->
                v.changeDisk(which)
                Toast.makeText(this, getString(R.string.disc_switched, which + 1), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

        val labels = items.map { "${it.name}  —  ${portLabel(portFor(it.key, it.device))}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_controllers)
            .setItems(labels) { _, which ->
                val c = items[which]
                showControllerOptions(c.key, c.name, c.device, c.key != PHONE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showControllerOptions(key: String, name: String, device: InputDevice?, isGamepad: Boolean) {
        val opts = mutableListOf(getString(R.string.ctrl_set_player))
        if (isGamepad) {
            opts += getString(R.string.ctrl_remap)
            opts += getString(
                if (inputConfig.leftStickAsDpad(key)) R.string.ctrl_stick_dpad_on else R.string.ctrl_stick_dpad_off,
            )
            opts += getString(R.string.ctrl_reset_map)
        }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(opts.toTypedArray()) { _, which ->
                when (opts[which]) {
                    getString(R.string.ctrl_set_player) -> showPlayerPicker(key, name, device)
                    getString(R.string.ctrl_remap) -> showRemap(key, name)
                    getString(R.string.ctrl_reset_map) -> {
                        inputConfig.clearBindings(key)
                        Toast.makeText(this, R.string.ctrl_map_reset, Toast.LENGTH_SHORT).show()
                    }
                    else -> inputConfig.setLeftStickAsDpad(key, !inputConfig.leftStickAsDpad(key))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            .show()
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
            .show()
    }

    private fun shaderForIndex(i: Int): ShaderConfig = when (i) {
        1 -> ShaderConfig.CRT
        2 -> ShaderConfig.LCD
        3 -> ShaderConfig.Sharp
        else -> ShaderConfig.Default
    }

    private fun showVideoFilterPicker() {
        val names = arrayOf(
            getString(R.string.filter_off),
            getString(R.string.filter_crt),
            getString(R.string.filter_lcd),
            getString(R.string.filter_sharp),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_video_filter)
            .setSingleChoiceItems(names, layoutStore.shaderIndex()) { dialog, which ->
                layoutStore.setShaderIndex(which)
                retroView?.shader = shaderForIndex(which)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val names = presets.map { it.name }.toTypedArray()
        val current = presets.indexOfFirst { it.id == controllerView.currentPresetId() }
            .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_choose_layout)
            .setSingleChoiceItems(names, current) { dialog, which ->
                controllerView.setPreset(presets[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Adds a labelled slider to [parent]; [onChange] receives the raw progress. */
    private fun addSlider(
        parent: LinearLayout,
        maxProgress: Int,
        initialProgress: Int,
        labelFor: (Int) -> String,
        onChange: (Int) -> Unit,
    ) {
        val label = TextView(this).apply { text = labelFor(initialProgress); textSize = 14f }
        val seek = SeekBar(this).apply {
            max = maxProgress
            progress = initialProgress.coerceIn(0, maxProgress)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                label.text = labelFor(p)
                onChange(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        parent.addView(label)
        parent.addView(seek)
    }

    private fun showScreenSizeDialog() {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val portrait = isPortrait() && !extendedMode
        val editingExternal = extendedMode

        // --- Size ---
        if (portrait) {
            addSlider(
                container, maxProgress = 45,
                initialProgress = ((layoutStore.portraitScreenFraction() - 0.25f) * 100f).toInt(),
                labelFor = { getString(R.string.screen_height_value, (25 + it)) },
            ) { p ->
                layoutStore.setPortraitScreenFraction(0.25f + p / 100f)
                arrangeLayout()
            }
        } else {
            val minScale = if (editingExternal) 0.4f else 0.3f
            val range = ((1.0f - minScale) * 100f).toInt()
            val cur = if (editingExternal) videoScale else videoScaleLocal
            addSlider(
                container, maxProgress = range,
                initialProgress = ((cur - minScale) * 100f).toInt(),
                labelFor = { getString(R.string.screen_size_value, ((minScale + it / 100f) * 100).toInt()) },
            ) { p ->
                val value = minScale + p / 100f
                if (editingExternal) videoScale = value else videoScaleLocal = value
                if (editingExternal) layoutStore.setVideoScale(value) else layoutStore.setLocalVideoScale(value)
                applyVideoTransform()
            }
        }

        // --- Rotation ---
        val rotateBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.screen_rotate, layoutStore.videoRotation())
            setOnClickListener {
                layoutStore.setVideoRotation(layoutStore.videoRotation() + 90)
                text = getString(R.string.screen_rotate, layoutStore.videoRotation())
                applyVideoTransform()
            }
        }
        container.addView(rotateBtn)

        // --- Position ---
        addSlider(
            container, maxProgress = 100,
            initialProgress = ((layoutStore.videoOffsetX() + 0.5f) * 100f).toInt(),
            labelFor = { getString(R.string.screen_pos_h) },
        ) { p ->
            layoutStore.setVideoOffset(p / 100f - 0.5f, layoutStore.videoOffsetY())
            applyVideoTransform()
        }
        addSlider(
            container, maxProgress = 100,
            initialProgress = ((layoutStore.videoOffsetY() + 0.5f) * 100f).toInt(),
            labelFor = { getString(R.string.screen_pos_v) },
        ) { p ->
            layoutStore.setVideoOffset(layoutStore.videoOffsetX(), p / 100f - 0.5f)
            applyVideoTransform()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.menu_screen_size)
            .setView(container)
            .setPositiveButton(R.string.edit_done, null)
            .setNeutralButton(R.string.screen_reset) { _, _ ->
                layoutStore.setVideoRotation(0)
                layoutStore.setVideoOffset(0f, 0f)
                arrangeLayout()
            }
            .show()
    }

    private fun setEditMode(enabled: Boolean) {
        controllerView.editMode = enabled
        editBar.visibility = if (enabled) View.VISIBLE else View.GONE
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
            .show()
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
            .show()
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
    }

    override fun onPause() {
        displayManager.unregisterDisplayListener(displayListener)
        mediaRouter.removeCallback(mediaRouterCallback)
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
        persistSram()
        autoSaveState()
        super.onPause()
    }

    override fun onDestroy() {
        menuDialog?.dismiss()
        menuDialog = null
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
