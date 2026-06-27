package com.example.airlinereservationsapp

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class GlobeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = GlobeRenderer(context)

    private var lastX = 0f
    private var lastY = 0f

    @Volatile private var rotateActive = false
    private var rotateThread: Thread? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                stopRotation()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                renderer.rotationY += dx * 0.4f
                renderer.rotationX += dy * 0.4f
                renderer.rotationX = renderer.rotationX.coerceIn(-85f, 85f)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                scheduleAutoRotate(delayMs = 3000)
            }
        }
        return true
    }

    fun startAutoRotate() {
        stopRotation()
        launchRotateThread(delayMs = 0)
    }

    fun pauseRotation() {
        stopRotation()
    }

    fun resumeRotation() {
        if (!rotateActive) {
            launchRotateThread(delayMs = 0)
        }
    }

    private fun stopRotation() {
        rotateActive = false
        rotateThread?.interrupt()
        rotateThread?.join(200)
        rotateThread = null
    }

    private fun scheduleAutoRotate(delayMs: Long) {
        stopRotation()
        launchRotateThread(delayMs)
    }

    private fun launchRotateThread(delayMs: Long) {
        rotateActive = true
        rotateThread = Thread {
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                while (rotateActive) {
                    renderer.rotationY += 0.2f
                    Thread.sleep(16)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.also { it.start() }
    }

    fun focusOnRoute(fromLat: Float, fromLon: Float, toLat: Float, toLon: Float) {
        val midLon = (fromLon + toLon) / 2f  // ~12.8 pentru OTP-LHR
        val midLat = (fromLat + toLat) / 2f  // ~48
        renderer.rotationY = midLon           // fara 180, fara minus
        renderer.rotationX = -midLat * 0.7f
        renderer.zoom = -2.2f
        renderer.setRoute(fromLat, fromLon, toLat, toLon)
        scheduleAutoRotate(delayMs = 1000)
    }

    override fun onDetachedFromWindow() {
        stopRotation()
        super.onDetachedFromWindow()
    }
}