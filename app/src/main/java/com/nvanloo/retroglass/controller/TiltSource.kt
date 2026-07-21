package com.nvanloo.retroglass.controller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Where the phone is being held, as a 2D "light direction" for the controller's drop shadows.
 *
 * Deliberately **not** the gyroscope, despite that being the usual shorthand: a gyro reports
 * angular *velocity*, so integrating it to get a pose drifts within seconds and there is no
 * absolute reference to correct against. Gravity gives the tilt directly and never drifts,
 * which is what a fixed light source needs. [Sensor.TYPE_GRAVITY] is fused and already
 * smoothed; the raw accelerometer is the fallback on devices without it.
 *
 * Neutral is [NEUTRAL_PITCH_DEG] back from vertical, because that is roughly how a phone sits
 * in the hands while playing — so the shadows read as "straight down" in the pose you actually
 * hold, and shift as you move away from it.
 */
class TiltSource(
    private val context: Context,
    private val onChange: (x: Float, y: Float) -> Unit,
) : SensorEventListener {

    companion object {
        /** The resting pose: leaned back this far from upright counts as level. */
        const val NEUTRAL_PITCH_DEG = 20f

        /**
         * Tilt this far from neutral drives the light to its limit.
         *
         * Generous on purpose. At ±30° the pitch axis pinned to the clamp as soon as you
         * actually held the phone to play — a relaxed pose is 40–60° back, already outside
         * neutral±30 — so up/down looked frozen while left/right, which is naturally centred
         * on zero, worked fine.
         */
        private const val RANGE_DEG = 45f

        /** Exponential smoothing per sample — low enough that hand tremor does not shimmer. */
        private const val SMOOTHING = 0.12f

        /** Ignore sub-pixel movement so we are not invalidating the overlay every sample. */
        private const val EPSILON = 0.004f
    }

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var registered = false
    private var x = 0f
    private var y = 0f

    private fun sensor(): Sensor? =
        sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = sensor() != null

    fun start() {
        if (registered) return
        val s = sensor() ?: return
        // UI rate, not GAME: this drives a shadow, and the emulator wants the frame budget.
        sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensors.unregisterListener(this)
        registered = false
        x = 0f
        y = 0f
        onChange(0f, 0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]

        // Held upright in portrait gravity reads (0, g, 0); flat on a table it reads (0, 0, g).
        // So pitch is 0° when the screen plane is vertical and 90° when it faces the ceiling.
        val pitch = Math.toDegrees(atan2(gz.toDouble(), gy.toDouble())).toFloat()
        val roll = Math.toDegrees(
            atan2(gx.toDouble(), hypot(gy.toDouble(), gz.toDouble())),
        ).toFloat()

        val tiltX = (roll / RANGE_DEG).coerceIn(-1f, 1f)
        val tiltY = ((pitch - NEUTRAL_PITCH_DEG) / RANGE_DEG).coerceIn(-1f, 1f)

        // Sensor axes are welded to the device; the screen rotates underneath them, so a
        // landscape layout would otherwise get its shadows thrown 90° off.
        val (sx, sy) = when (rotation()) {
            Surface.ROTATION_90 -> -tiltY to tiltX
            Surface.ROTATION_180 -> -tiltX to -tiltY
            Surface.ROTATION_270 -> tiltY to -tiltX
            else -> tiltX to tiltY
        }

        val nx = x + (sx - x) * SMOOTHING
        val ny = y + (sy - y) * SMOOTHING
        if (abs(nx - x) < EPSILON && abs(ny - y) < EPSILON) return
        x = nx
        y = ny
        onChange(x, y)
    }

    @Suppress("DEPRECATION")
    private fun rotation(): Int =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
}
