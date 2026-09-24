package com.moonbench.bifrost.rp5

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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

class Rp5CalibrationActivity : AppCompatActivity() {
    private lateinit var view: Rp5CalibrationView
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("bifrost_rp5_calibration", MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null

    private val permission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if(r.resultCode==Activity.RESULT_OK && r.data!=null) {
            MainActivity.mediaProjectionResultCode=r.resultCode
            MainActivity.mediaProjectionData=r.data
            capture(r.resultCode,r.data!!)
        } else status.text="Screen capture permission is required for live calibration."
    }

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val cal=load()
        view=Rp5CalibrationView { l,r -> save(l,r) }
        view.leftRegion=cal.left; view.rightRegion=cal.right
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val head=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,18,24,8) }
        status=TextView(this).apply { text="Live RP5 calibration"; setTextColor(Color.WHITE); textSize=18f }
        head.addView(status)
        head.addView(TextView(this).apply {
            text="Drag each square to move it. Drag near an edge to resize. Colours update from the live screen."
            setTextColor(Color.LTGRAY); textSize=13f
        })
        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
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
        root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(buttons)
        setContentView(root)

        if(MainActivity.mediaProjectionResultCode!=null && MainActivity.mediaProjectionData!=null)
            capture(MainActivity.mediaProjectionResultCode!!,MainActivity.mediaProjectionData!!)
        else {
            val m=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            permission.launch(m.createScreenCaptureIntent())
        }
    }

    private fun capture(code:Int,data:Intent) {
        stopCapture()
        val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=manager.getMediaProjection(code,data)
        val dm=DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        val w=dm.widthPixels; val h=dm.heightPixels
        reader=ImageReader.newInstance(w,h,android.graphics.PixelFormat.RGBA_8888,2)
        reader!!.setOnImageAvailableListener({ rr ->
            val image=rr.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane=image.planes[0]
                val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val pixels=IntArray(w*h)
                bitmap.getPixels(pixels,0,w,0,0,w,h)
                val sampled=ColorSampler.sample(pixels,w,h,view.leftRegion,view.rightRegion)
                runOnUiThread {
                    view.leftColor=sampled.left
                    view.rightColor=sampled.right
                    view.setFrame(bitmap.copy(Bitmap.Config.ARGB_8888,false))
                    status.text="LIVE   LEFT #%06X   RIGHT #%06X".format(sampled.left and 0xffffff,sampled.right and 0xffffff)
                    bitmap.recycle()
                }
            } finally { image.close() }
        },handler)
        display=projection!!.createVirtualDisplay("BifrostRP5Calibration",w,h,dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
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