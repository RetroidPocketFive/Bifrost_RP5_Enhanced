package com.moonbench.bifrost.rp5
import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController

class LedCalibrationActivity:Activity(){
 private lateinit var view:LedCalibrationView
 private lateinit var preview:ThumbstickLivePreview
 private var displayId=android.view.Display.DEFAULT_DISPLAY
 private val led by lazy{LedController(this)}
 override fun onCreate(b:Bundle?){super.onCreate(b);send(LEDService.ACTION_CALIBRATION_ENTER);setContentView(build());preview=ThumbstickLivePreview(this,{view.setFrame(it)},{l,r->view.setColors(l,r)},displayId);view.onRegionChanged={l,r->preview.preview(l,r,ThumbstickColourMode.AVERAGE)};preview.start()}
 private fun build()=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF000000.toInt())
  addView(TextView(this@LedCalibrationActivity).apply{text="CALIBRATION — LED FUNCTION TEST";setTextColor(-1);textSize=18f;setPadding(16,16,16,8)})
  addView(TextView(this@LedCalibrationActivity).apply{text="Check LED hardware and colour response. Move BLUE to test the left thumb-stick and ORANGE to test the right thumb-stick. Both LEDs remain active and follow their own viewing area. Calibration does not create or change a Viewing Profile.";setTextColor(0xFFCCCCCC.toInt());setPadding(16,0,16,8)})
  val dm=getSystemService(DisplayManager::class.java);val ds=dm.displays;val spinner=Spinner(this@LedCalibrationActivity)
  spinner.adapter=ArrayAdapter(this@LedCalibrationActivity,android.R.layout.simple_spinner_dropdown_item,ds.map{"Capture Display "+it.displayId})
  spinner.setSelection(ds.indexOfFirst{it.displayId==displayId}.coerceAtLeast(0))
  spinner.setOnItemSelectedListener(object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){displayId=ds.getOrNull(pos)?.displayId?:android.view.Display.DEFAULT_DISPLAY};override fun onNothingSelected(p:AdapterView<*>?) {}})
  addView(spinner)
  addView(TextView(this@LedCalibrationActivity).apply{text="LED COLOUR MATCH — LIVE";setTextColor(0xFFCCCCCC.toInt());setPadding(16,4,16,8)})
  view=LedCalibrationView(this@LedCalibrationActivity);addView(view,LinearLayout.LayoutParams(-1,0,1f))
  val row=LinearLayout(this@LedCalibrationActivity).apply{gravity=Gravity.CENTER}
  listOf(0,25,50,75,100).forEach{v->addView(Button(this@LedCalibrationActivity).apply{text=if(v==0)"OFF" else "LED $v%";setOnClickListener{val b=(255*v/100);if(v==0)led.setLedColorDual(0,0,0,0,0,0,0)else led.setLedColorDual(b,b,b,b,b,b,v*255/100)}})}
  addView(TextView(this@LedCalibrationActivity).apply{text="LED BRIGHTNESS TEST";setTextColor(0xFFAAAAAA.toInt());gravity=Gravity.CENTER})
  addView(Button(this@LedCalibrationActivity).apply{text="EXIT / RESTORE NORMAL LED CONTROL";setOnClickListener{send(LEDService.ACTION_CALIBRATION_EXIT);finish()}})
 }
 private fun send(action:String){runCatching{startService(Intent(this,LEDService::class.java).setAction(action))}}
 override fun onResume(){super.onResume();if(::preview.isInitialized)preview.start()}
 override fun onPause(){if(::preview.isInitialized)preview.stop();super.onPause()}
 override fun onDestroy(){send(LEDService.ACTION_CALIBRATION_EXIT);super.onDestroy()}
}