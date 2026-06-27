package com.example.airlinereservationsapp

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program = 0
    private var routeProgram = 0
    private var starProgram = 0

    private var dayTextureId = 0
    private var nightTextureId = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private lateinit var normalBuffer: FloatBuffer
    private lateinit var starBuffer: FloatBuffer

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    var rotationY = 0f
    var rotationX = 15f
    var zoom = -2.8f

    var showRoute = false
    var routeStartLat = 0f
    var routeStartLon = 0f
    var routeEndLat = 0f
    var routeEndLon = 0f
    private lateinit var routeBuffer: FloatBuffer
    private lateinit var routeGlowBuffer: FloatBuffer
    private var routePointCount = 0
    private var arrowBuffer: FloatBuffer? = null

    data class Airplane(val lat: Float, val lon: Float, val heading: Float, val callsign: String)
    var airplanes = listOf<Airplane>()

    private val STACKS = 64
    private val SLICES = 64
    private val STAR_COUNT = 1000

    // TOT CODUL foloseste Z negat — consistent peste tot
    private fun latLonToXYZ(lat: Float, lon: Float, radius: Float = 1.025f): FloatArray {
        val latR = Math.toRadians(lat.toDouble())
        val lonR = Math.toRadians(lon.toDouble())
        val x = (cos(latR) * cos(lonR) * radius).toFloat()
        val y = (sin(latR) * radius).toFloat()
        val z = -(cos(latR) * sin(lonR) * radius).toFloat()
        return floatArrayOf(x, y, z)
    }

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        attribute vec3 aNormal;
        varying vec2 vTexCoord;
        varying float vDiffuse;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
            vec3 worldNormal = normalize(mat3(uModelMatrix) * aNormal);
            vec3 sunDir = normalize(vec3(1.0, 0.3, 0.5));
            vDiffuse = max(dot(worldNormal, sunDir), 0.0);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uDayTexture;
        uniform sampler2D uNightTexture;
        varying vec2 vTexCoord;
        varying float vDiffuse;
        void main() {
            vec4 dayColor = texture2D(uDayTexture, vTexCoord);
            vec4 nightColor = texture2D(uNightTexture, vTexCoord);
            float blend = smoothstep(0.0, 0.35, vDiffuse);
            vec4 color = mix(nightColor * 0.9, dayColor, blend);
            gl_FragColor = color;
        }
    """.trimIndent()

    private val simpleVertexShader = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()

    private val colorFragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """.trimIndent()

    private val starVertexShader = """
        attribute vec4 aPosition;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            gl_PointSize = 2.0;
        }
    """.trimIndent()

    private val starFragmentShader = """
        precision mediump float;
        void main() {
            gl_FragColor = vec4(1.0, 1.0, 1.0, 0.8);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0.01f, 0.06f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        buildSphere()
        buildStars()
        buildRoute()

        program = buildProgram(vertexShaderCode, fragmentShaderCode)
        routeProgram = buildProgram(simpleVertexShader, colorFragmentShader)
        starProgram = buildProgram(starVertexShader, starFragmentShader)

        dayTextureId = loadTexture(R.drawable.earth_day)
        nightTextureId = loadTexture(R.drawable.earth_night)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, zoom, 0f, 0f, 0f, 0f, 1f, 0f)

        val starMVP = FloatArray(16)
        val identityModel = FloatArray(16)
        Matrix.setIdentityM(identityModel, 0)
        val tempStar = FloatArray(16)
        Matrix.multiplyMM(tempStar, 0, viewMatrix, 0, identityModel, 0)
        Matrix.multiplyMM(starMVP, 0, projectionMatrix, 0, tempStar, 0)
        drawStars(starMVP)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        drawGlobe()
        if (showRoute) drawRoute()
    }

    private fun buildSphere() {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        for (stack in 0..STACKS) {
            val phi = Math.PI * stack / STACKS
            for (slice in 0..SLICES) {
                val theta = 2.0 * Math.PI * slice / SLICES
                val x = (sin(phi) * cos(theta)).toFloat()
                val y = cos(phi).toFloat()
                val z = (sin(phi) * sin(theta)).toFloat()
                vertices.addAll(listOf(x, y, z))
                normals.addAll(listOf(x, y, z))
                val u = (1.0f - slice.toFloat() / SLICES + 0.5f) % 1.0f
                texCoords.addAll(listOf(u, stack.toFloat() / STACKS))
            }
        }

        for (stack in 0 until STACKS) {
            for (slice in 0 until SLICES) {
                val first = (stack * (SLICES + 1) + slice).toShort()
                val second = (first + SLICES + 1).toShort()
                indices.addAll(listOf(
                    first, second, (first + 1).toShort(),
                    second, (second + 1).toShort(), (first + 1).toShort()
                ))
            }
        }

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(vertices.toFloatArray()); position(0) }
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(texCoords.toFloatArray()); position(0) }
        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(normals.toFloatArray()); position(0) }
        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(indices.toShortArray()); position(0) }
    }

    private fun buildStars() {
        val stars = mutableListOf<Float>()
        val random = Random(42)
        for (i in 0 until STAR_COUNT) {
            val theta = random.nextFloat() * 2f * Math.PI.toFloat()
            val phi = random.nextFloat() * Math.PI.toFloat()
            val r = 8f
            stars.add(r * sin(phi) * cos(theta))
            stars.add(r * cos(phi))
            stars.add(r * sin(phi) * sin(theta))
        }
        starBuffer = ByteBuffer.allocateDirect(stars.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(stars.toFloatArray()); position(0) }
    }

    private fun buildRoute() {
        if (!showRoute) return

        val points = mutableListOf<Float>()
        val glowPoints = mutableListOf<Float>()
        val steps = 120

        val lat1 = Math.toRadians(routeStartLat.toDouble())
        val lon1 = Math.toRadians(routeStartLon.toDouble())
        val lat2 = Math.toRadians(routeEndLat.toDouble())
        val lon2 = Math.toRadians(routeEndLon.toDouble())

        // Z negat — consistent cu latLonToXYZ
        val x1 = cos(lat1) * cos(lon1)
        val y1 = sin(lat1)
        val z1 = -cos(lat1) * sin(lon1)
        val x2 = cos(lat2) * cos(lon2)
        val y2 = sin(lat2)
        val z2 = -cos(lat2) * sin(lon2)

        val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
        val d = acos(dot)
        if (d < 0.0001) return

        val pathPoints = mutableListOf<FloatArray>()

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val a = sin((1 - t) * d) / sin(d)
            val b = sin(t * d) / sin(d)

            val x = (a * x1 + b * x2).toFloat()
            val y = (a * y1 + b * y2).toFloat()
            val z = (a * z1 + b * z2).toFloat()
            val len = sqrt(x * x + y * y + z * z).toFloat()

            val scale = 1.03f / len
            points.add(x * scale); points.add(y * scale); points.add(z * scale)
            pathPoints.add(floatArrayOf(x * scale, y * scale, z * scale))

            val glowScale = 1.038f / len
            glowPoints.add(x * glowScale); glowPoints.add(y * glowScale); glowPoints.add(z * glowScale)
        }

        routePointCount = steps + 1

        routeBuffer = ByteBuffer.allocateDirect(points.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(points.toFloatArray()); position(0) }
        routeGlowBuffer = ByteBuffer.allocateDirect(glowPoints.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(glowPoints.toFloatArray()); position(0) }

        // Construim sageata la destinatie
        buildArrow(pathPoints)
    }

    private fun buildArrow(pathPoints: List<FloatArray>) {
        if (pathPoints.size < 2) return

        // Ultimele 2 puncte ale rutei — directia sagetii
        val pPrev = pathPoints[pathPoints.size - 2]
        val pEnd = pathPoints[pathPoints.size - 1]

        // Vectorul directie al rutei la final
        val dx = pEnd[0] - pPrev[0]
        val dy = pEnd[1] - pPrev[1]
        val dz = pEnd[2] - pPrev[2]
        val dlen = sqrt(dx * dx + dy * dy + dz * dz)
        val fx = dx / dlen
        val fy = dy / dlen
        val fz = dz / dlen

        // Normala la suprafata sferei in punctul final
        val nlen = sqrt(pEnd[0] * pEnd[0] + pEnd[1] * pEnd[1] + pEnd[2] * pEnd[2])
        val nx = pEnd[0] / nlen
        val ny = pEnd[1] / nlen
        val nz = pEnd[2] / nlen

        // Vectorul perpendicular pe directie si pe normala (dreapta-stanga)
        val rx = fy * nz - fz * ny
        val ry = fz * nx - fx * nz
        val rz = fx * ny - fy * nx
        val rlen = sqrt(rx * rx + ry * ry + rz * rz)
        val rightX = rx / rlen
        val rightY = ry / rlen
        val rightZ = rz / rlen

        val size = 0.025f  // marimea sagetii

        // Varful sagetii — in directia rutei
        val tipX = pEnd[0] + fx * size
        val tipY = pEnd[1] + fy * size
        val tipZ = pEnd[2] + fz * size

        // Baza stanga si dreapta
        val baseScale = 0.85f
        val baseX = pEnd[0] - fx * size * baseScale
        val baseY = pEnd[1] - fy * size * baseScale
        val baseZ = pEnd[2] - fz * size * baseScale

        val leftX = baseX + rightX * size * 0.7f
        val leftY = baseY + rightY * size * 0.7f
        val leftZ = baseZ + rightZ * size * 0.7f

        val rightBX = baseX - rightX * size * 0.7f
        val rightBY = baseY - rightY * size * 0.7f
        val rightBZ = baseZ - rightZ * size * 0.7f

        val arrowVerts = floatArrayOf(
            tipX, tipY, tipZ,
            leftX, leftY, leftZ,
            rightBX, rightBY, rightBZ
        )

        arrowBuffer = ByteBuffer.allocateDirect(arrowVerts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(arrowVerts); position(0) }
    }

    private fun drawGlobe() {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModelMatrix"), 1, false, modelMatrix, 0)

        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dayTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uDayTexture"), 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nightTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uNightTexture"), 1)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexBuffer.capacity(), GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private fun drawStars(mvp: FloatArray) {
        GLES20.glUseProgram(starProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(starProgram, "uMVPMatrix"), 1, false, mvp, 0)
        val posHandle = GLES20.glGetAttribLocation(starProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, starBuffer)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, STAR_COUNT)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawRoute() {
        if (!::routeBuffer.isInitialized || routePointCount == 0) return

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(routeProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(routeProgram, "uMVPMatrix"), 1, false, mvpMatrix, 0)

        val posHandle = GLES20.glGetAttribLocation(routeProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)

        // glow cyan
        GLES20.glUniform4f(GLES20.glGetUniformLocation(routeProgram, "uColor"), 0.0f, 0.85f, 0.9f, 0.25f)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, routeGlowBuffer)
        GLES20.glLineWidth(8f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, routePointCount)

        // linia alba
        GLES20.glUniform4f(GLES20.glGetUniformLocation(routeProgram, "uColor"), 1.0f, 1.0f, 1.0f, 0.95f)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, routeBuffer)
        GLES20.glLineWidth(2.5f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, routePointCount)

        // sageata la destinatie
        arrowBuffer?.let { ab ->
            ab.position(0)
            // glow sageata
            GLES20.glUniform4f(GLES20.glGetUniformLocation(routeProgram, "uColor"), 0.0f, 0.85f, 0.9f, 0.4f)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, ab)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 3)

            // umplere alba
            GLES20.glUniform4f(GLES20.glGetUniformLocation(routeProgram, "uColor"), 1.0f, 1.0f, 1.0f, 0.95f)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, ab)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        }

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun isNearRoute(planeLat: Float, planeLon: Float): Boolean {
        val margin = 8f
        val minLat = minOf(routeStartLat, routeEndLat) - margin
        val maxLat = maxOf(routeStartLat, routeEndLat) + margin
        val minLon = minOf(routeStartLon, routeEndLon) - margin
        val maxLon = maxOf(routeStartLon, routeEndLon) + margin
        return planeLat in minLat..maxLat && planeLon in minLon..maxLon
    }

    private fun loadTexture(resourceId: Int): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return textureIds[0]
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, vertexSrc)
            GLES20.glCompileShader(it)
        }
        val fragShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, fragmentSrc)
            GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertShader)
            GLES20.glAttachShader(it, fragShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun setRoute(fromLat: Float, fromLon: Float, toLat: Float, toLon: Float) {
        routeStartLat = fromLat
        routeStartLon = fromLon
        routeEndLat = toLat
        routeEndLon = toLon
        showRoute = true
        buildRoute()
    }
}