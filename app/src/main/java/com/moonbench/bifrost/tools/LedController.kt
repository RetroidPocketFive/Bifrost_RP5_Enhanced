package com.moonbench.bifrost.tools

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.moonbench.bifrost.rp5.LedDriver
import com.moonbench.bifrost.rp5.LedFrame
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

class LedController : LedDriver {
    companion object {
        private const val TAG = "LedController"
    }

    private val pServerBinder: IBinder?
    private val lock = ReentrantLock()

    @Volatile
    private var frameSink: ((LedFrame) -> Unit)? = null

    private var lastLeftR = 0
    private var lastLeftG = 0
    private var lastLeftB = 0
    private var lastRightR = 0
    private var lastRightG = 0
    private var lastRightB = 0

    private var lastCommand: String? = null
    private var lastExecuteTime = 0L
    private val minExecuteInterval = 16L

    // Master brightness scale (0f..1f) multiplied into every RGB write. The LED
    // kernel ignores the 4th wire field, so "brightness" IS RGB magnitude —
    // scaling R/G/B here dims uniformly. Used for crossfades (see setMasterScale);
    // 1f in normal operation, so setLedColor is unaffected outside a fade.
    @Volatile private var masterScale: Float = 1f

    // Last unscaled colour + zone mask, so a fade can re-emit the current frame
    // even when the running animation writes slowly (or only once).
    private var lastR = 0
    private var lastG = 0
    private var lastB = 0
    private var lastLeftTop = false
    private var lastLeftBottom = false
    private var lastRightTop = false
    private var lastRightBottom = false

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PServerBinder", e)
            null
        }
    }

    /**
     * Routes animation frames into the RP5 scheduler when attached. The scheduler
     * eventually calls [write] on this same controller, which bypasses [frameSink]
     * and performs the single hardware transaction.
     */
    fun attachFrameSink(sink: (LedFrame) -> Unit) {
        frameSink = sink
    }

    fun detachFrameSink() {
        frameSink = null
    }

    override fun write(frame: LedFrame) {
        emitFrameDirect(frame)
    }

    override fun clear() {
        setLedColorDirect(0, 0, 0, 0, true, true, true, true)
    }

    private fun submitFrame(frame: LedFrame) {
        frameSink?.invoke(frame) ?: emitFrameDirect(frame)
    }

    private fun emitFrameDirect(frame: LedFrame) {
        setLedColorDualDirect(
            frame.left, frame.right,
            frame.leftTop, frame.leftBottom, frame.rightTop, frame.rightBottom
        )
    }

    fun setLedColor(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        val br = brightness.coerceIn(0, 255)
        if (pServerBinder == null) return

        lock.withLock {
            lastR = r; lastG = g; lastB = b
            lastLeftR = r; lastLeftG = g; lastLeftB = b
            lastRightR = r; lastRightG = g; lastRightB = b
            lastLeftTop = leftTop; lastLeftBottom = leftBottom
            lastRightTop = rightTop; lastRightBottom = rightBottom
        }

        submitFrame(LedFrame(
            left = (r shl 16) or (g shl 8) or b,
            right = (r shl 16) or (g shl 8) or b,
            leftTop = leftTop,
            leftBottom = leftBottom,
            rightTop = rightTop,
            rightBottom = rightBottom
        ))
    }

    /** Apply masterScale to (r,g,b) and write the selected zones. */
    private fun emit(
        r: Int, g: Int, b: Int, br: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        val s = masterScale
        val sr = (r * s).roundToInt().coerceIn(0, 255)
        val sg = (g * s).roundToInt().coerceIn(0, 255)
        val sb = (b * s).roundToInt().coerceIn(0, 255)

        val commandBuilder = StringBuilder(220)
        if (leftTop) {
            commandBuilder.append("echo 1-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (leftBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (rightTop) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 1-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }
        if (rightBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }

        if (commandBuilder.isNotEmpty()) {
            executeCommandDirect(commandBuilder.toString())
        }
    }

    /**
     * Write both sticks — left zones at the left colour, right zones at the right
     * colour — in a SINGLE transact, instead of the two setLedColor() takes when
     * the sticks differ. Halves the per-frame IPC and (since the writer process
     * runs a shell per command) the shell-forks on its little cores, which is the
     * residual stutter source under heavy load. Same masterScale + last-colour
     * bookkeeping as setLedColor so crossfades still work (left = the re-emit
     * baseline, matching the old "last call wins" behaviour).
     */
    fun setLedColorDual(
        leftR: Int, leftG: Int, leftB: Int,
        rightR: Int, rightG: Int, rightB: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        if (pServerBinder == null) return
        val lr = leftR.coerceIn(0, 255); val lg = leftG.coerceIn(0, 255); val lb = leftB.coerceIn(0, 255)
        val rr = rightR.coerceIn(0, 255); val rg = rightG.coerceIn(0, 255); val rb = rightB.coerceIn(0, 255)
        val br = brightness.coerceIn(0, 255)
        lock.withLock {
            lastR = lr; lastG = lg; lastB = lb
            lastLeftR = lr; lastLeftG = lg; lastLeftB = lb
            lastRightR = rr; lastRightG = rg; lastRightB = rb
            lastLeftTop = leftTop; lastLeftBottom = leftBottom
            lastRightTop = rightTop; lastRightBottom = rightBottom
        }

        submitFrame(LedFrame(
            left = (lr shl 16) or (lg shl 8) or lb,
            right = (rr shl 16) or (rg shl 8) or rb,
            leftTop = leftTop,
            leftBottom = leftBottom,
            rightTop = rightTop,
            rightBottom = rightBottom
        ))
    }

    /** Build one &&-joined command covering all selected zones (left zones use the
     *  left colour, right zones the right) and write it in a single transact. */
    private fun emitDual(
        lr: Int, lg: Int, lb: Int, rr: Int, rg: Int, rb: Int, br: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        val s = masterScale
        val slr = (lr * s).roundToInt().coerceIn(0, 255)
        val slg = (lg * s).roundToInt().coerceIn(0, 255)
        val slb = (lb * s).roundToInt().coerceIn(0, 255)
        val srr = (rr * s).roundToInt().coerceIn(0, 255)
        val srg = (rg * s).roundToInt().coerceIn(0, 255)
        val srb = (rb * s).roundToInt().coerceIn(0, 255)
        val cmd = StringBuilder(220)
        if (leftTop) cmd.append("echo 1-").append(slr).append(':').append(slg).append(':').append(slb).append(':').append(br)
            .append(" > /sys/class/sn3112l/led/brightness")
        if (leftBottom) { if (cmd.isNotEmpty()) cmd.append(" && "); cmd.append("echo 2-").append(slr).append(':').append(slg).append(':').append(slb).append(':').append(br)
            .append(" > /sys/class/sn3112l/led/brightness") }
        if (rightTop) { if (cmd.isNotEmpty()) cmd.append(" && "); cmd.append("echo 1-").append(srr).append(':').append(srg).append(':').append(srb).append(':').append(br)
            .append(" > /sys/class/sn3112r/led/brightness") }
        if (rightBottom) { if (cmd.isNotEmpty()) cmd.append(" && "); cmd.append("echo 2-").append(srr).append(':').append(srg).append(':').append(srb).append(':').append(br)
            .append(" > /sys/class/sn3112r/led/brightness") }
        if (cmd.isNotEmpty()) executeCommandDirect(cmd.toString())
    }

    /**
     * Set the master brightness scale (0f..1f) and immediately re-emit the last
     * colour at the new scale. Driving this from 1→0→1 around an animation swap
     * produces a dip-to-black crossfade that masks the hard cut, independent of
     * how fast the underlying animation renders.
     */
    fun setMasterScale(scale: Float) {
        masterScale = scale.coerceIn(0f, 1f)
        val lt: Boolean; val lb: Boolean; val rt: Boolean; val rb: Boolean
        val lr: Int; val lg: Int; val lbv: Int
        val rr: Int; val rg: Int; val rbv: Int
        lock.withLock {
            lt = lastLeftTop; lb = lastLeftBottom; rt = lastRightTop; rb = lastRightBottom
            lr = (lastLeftR * masterScale).roundToInt().coerceIn(0, 255)
            lg = (lastLeftG * masterScale).roundToInt().coerceIn(0, 255)
            lbv = (lastLeftB * masterScale).roundToInt().coerceIn(0, 255)
            rr = (lastRightR * masterScale).roundToInt().coerceIn(0, 255)
            rg = (lastRightG * masterScale).roundToInt().coerceIn(0, 255)
            rbv = (lastRightB * masterScale).roundToInt().coerceIn(0, 255)
        }
        if (lt || lb || rt || rb) submitFrame(
            LedFrame(
                left = (lr shl 16) or (lg shl 8) or lbv,
                right = (rr shl 16) or (rg shl 8) or rbv,
                leftTop = lt, leftBottom = lb, rightTop = rt, rightBottom = rb
            )
        )
    }

    /**
     * Drop the re-emit baseline to black (keeping zone selection). Called at the
     * crossfade midpoint so the fade-in spins up from black instead of briefly
     * re-showing the outgoing colour before the incoming animation's first frame.
     */
    fun resetFadeBaseline() {
        lock.withLock {
            lastR = 0; lastG = 0; lastB = 0
            lastLeftR = 0; lastLeftG = 0; lastLeftB = 0
            lastRightR = 0; lastRightG = 0; lastRightB = 0
        }
    }

    private fun setLedColorDirect(
        red: Int, green: Int, blue: Int, brightness: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        emit(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255),
            brightness.coerceIn(0, 255), leftTop, leftBottom, rightTop, rightBottom)
    }

    private fun setLedColorDualDirect(
        left: Int, right: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        emitDual(
            (left shr 16) and 0xFF, (left shr 8) and 0xFF, left and 0xFF,
            (right shr 16) and 0xFF, (right shr 8) and 0xFF, right and 0xFF,
            255, leftTop, leftBottom, rightTop, rightBottom
        )
    }

    fun setBrightness(brightness: Int) {
        val b = brightness.coerceIn(0, 255)
        val commands = listOf(
            "echo 1-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 1-0:0:0:$b > /sys/class/sn3112r/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112r/led/brightness"
        )
        val command = commands.joinToString(" && ")
        executeCommandDirect(command)
    }

    private fun executeCommandDirect(command: String) {
        lock.withLock {
            val now = System.currentTimeMillis()

            if (command == lastCommand && now - lastExecuteTime < minExecuteInterval) {
                return
            }

            lastCommand = command
            lastExecuteTime = now

            pServerBinder?.let { binder ->
                val data = Parcel.obtain()
                val reply = Parcel.obtain()

                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    Log.w(TAG, "LED transact failed", e)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }

    fun shutdown() {
    }
}