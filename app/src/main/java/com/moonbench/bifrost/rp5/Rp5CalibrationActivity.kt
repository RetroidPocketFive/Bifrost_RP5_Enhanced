package com.moonbench.bifrost.rp5

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.MainActivity
import com.moonbench.bifrost.services.LEDService

class Rp5CalibrationActivity : AppCompatActivity() {
    private lateinit var view: Rp5CalibrationView
    private lateinit var status: TextView
    private lateinit var testButton: MaterialButton
    private val prefs by lazy { getSharedPreferences("bifrost_rp5_calibration", MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())
    // RP5 calibration defaults to center-weighted sampling for stable stick-area colour matching.
    private val sampler = ColorSampler(SamplingMethod.CENTER_WEIGHTED)
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var liveMode=false

    private val permission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if(r.resultCode==Activity.RESULT_OK && r.data!=null) {
            MainActivity.mediaProjectionResultCode=r.resultCode
            MainActivity.mediaProjectionData=r.data
            startLiveCapture(r.resultCode,r.data!!)
        } else status.text="Screen capture permission is required for live calibration."
    }

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val cal=load()
        view=Rp5CalibrationView(this) { l,r -> save(l,r); resampleCurrentFrame() }
        view.leftRegion=cal.left; view.rightRegion=cal.right
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val head=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,18,24,8) }
        status=TextView(this).apply { text="RP5 LED CALIBRATION"; setTextColor(Color.WHITE); textSize=18f }
        head.addView(status)
        head.addView(TextView(this).apply {
            text="Place the squares over the physical thumb-stick areas. Move or resize them until the sampled colours look right."
            setTextColor(Color.LTGRAY); textSize=13f
        })
        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        testButton=MaterialButton(this).apply {
            text="Test LEDs"
            setOnClickListener { testPhysicalLeds() }
        }
        buttons.addView(testButton,LinearLayout.LayoutParams(0,56,1f))
        buttons.addView(MaterialButton(this).apply {
            text="Reset"
            setOnClickListener {
                view.leftRegion=NormalizedRegion(.25f,.78f,.18f)
                view.rightRegion=NormalizedRegion(.75f,.78f,.18f)
                save(view.leftRegion,view.rightRegion)
            }
        },LinearLayout.LayoutParams(0,56,1f))
        buttons.addView(MaterialButton(this).apply {
            text="Save & Done"
            setOnClickListener { save(view.leftRegion,view.rightRegion); finish() }
        },LinearLayout.LayoutParams(0,56,1f))
        root.addView(head)
        val sources=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        sources.addView(MaterialButton(this).apply { text="LIVE CALIBRATION"; setOnClickListener { chooseLive() } },LinearLayout.LayoutParams(0,56,1f))
        sources.addView(MaterialButton(this).apply { text="STILL IMAGE"; setOnClickListener { stillPicker.launch("image/*") } },LinearLayout.LayoutParams(0,56,1f))
        root.addView(sources)
        root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(buttons)
        testButton.isEnabled=false
        setContentView(root)

        status.text="Choose LIVE CALIBRATION or STILL IMAGE to begin."
    }

    private val stillPicker=registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri==null)return@registerForActivityResult
        val bitmap=runCatching { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if(bitmap==null){ status.text="Could not open that image."; return@registerForActivityResult }
        stopCapture(); liveMode=false; view.setFrame(bitmap); sampleBitmap(bitmap)
        status.text="STILL IMAGE CALIBRATION   LEFT #%06X   RIGHT #%06X".format(view.leftColor and 0xffffff,view.rightColor and 0xffffff)
    }

    private fun chooseLive(){
        liveMode=true
        val code=MainActivity.mediaProjectionResultCode
        val data=MainActivity.mediaProjectionData
        if(code!=null && data!=null) runCatching { startLiveCapture(code,data) }.onFailure { requestLivePermission() } else requestLivePermission()
    }

    private fun requestLivePermission(){
        val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        permission.launch(manager.createScreenCaptureIntent())
    }

    private fun startLiveCapture(code:Int,data:Intent){
        stopCapture()
        val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=manager.getMediaProjection(code,data)
        val dm=DisplayMetrics()
        @Suppress("DEPRECATION") (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        val captureWidth=480
        val captureHeight=(dm.heightPixels.toFloat()/dm.widthPixels.toFloat()*captureWidth).toInt().coerceAtLeast(1)
        reader=ImageReader.newInstance(captureWidth,captureHeight,android.graphics.PixelFormat.RGBA_8888,2)
        reader!!.setOnImageAvailableListener({ rr ->
            val image=rr.acquireLatestImage() ?: return@setOnImageAvailableListener
            try{
                val plane=image.planes[0]
                val paddedWidth=captureWidth+(plane.rowStride-plane.pixelStride*captureWidth)/plane.pixelStride
                val bitmap=Bitmap.createBitmap(paddedWidth,captureHeight,Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val cropped=if(paddedWidth!=captureWidth)Bitmap.createBitmap(bitmap,0,0,captureWidth,captureHeight)else bitmap
                if(cropped!==bitmap)bitmap.recycle()
                sampleBitmap(cropped)
                runOnUiThread{ view.setFrame(cropped); status.text="LIVE CALIBRATION   LEFT #%06X   RIGHT #%06X".format(view.leftColor and 0xffffff,view.rightColor and 0xffffff); testButton.isEnabled=true }
            }finally{image.close()}
        },handler)
        display=projection!!.createVirtualDisplay("BifrostRP5Calibration",captureWidth,captureHeight,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
        status.text="LIVE CALIBRATION"
    }

    private fun sampleBitmap(bitmap:Bitmap){
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val sampled=sampler.sample(pixels,bitmap.width,bitmap.height,view.leftRegion,view.rightRegion)
        view.leftColor=sampled.left; view.rightColor=sampled.right; testButton.isEnabled=true
    }

    private fun resampleCurrentFrame(){
        val sampled=view.sample(sampler) ?: return
        view.leftColor=sampled.left; view.rightColor=sampled.right
        status.text=if(liveMode) "LIVE CALIBRATION" else "STILL IMAGE CALIBRATION"
    }

    private fun testPhysicalLeds() {
        val intent = Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, view.leftColor)
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, view.rightColor)
            putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, 1500L)
        }
        runCatching {
            startService(intent)
            status.text = "LED TEST   LEFT #%06X   RIGHT #%06X".format(
                view.leftColor and 0xffffff,
                view.rightColor and 0xffffff
            )
        }.onFailure {
            status.text = "LED test failed to start: ${it.message ?: "unknown error"}"
        }
    }

    private fun load()=Rp5Calibration(
        NormalizedRegion(prefs.getFloat("lx",.25f),prefs.getFloat("ly",.78f),prefs.getFloat("ls",.18f)),
        NormalizedRegion(prefs.getFloat("rx",.75f),prefs.getFloat("ry",.78f),prefs.getFloat("rs",.18f))
    )

    private fun save(l:NormalizedRegion,r:NormalizedRegion)=prefs.edit()
        .putFloat("lx",l.centerX).putFloat("ly",l.centerY).putFloat("ls",l.size)
        .putFloat("rx",r.centerX).putFloat("ry",r.centerY).putFloat("rs",r.size).apply()

    private fun stopCapture(){ display?.release();display=null;reader?.close();reader=null;projection?.stop();projection=null }
    override fun onDestroy(){stopCapture();super.onDestroy()}
}