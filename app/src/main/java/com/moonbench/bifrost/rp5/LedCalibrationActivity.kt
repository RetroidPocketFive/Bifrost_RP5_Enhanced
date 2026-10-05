package com.moonbench.bifrost.rp5
import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController
class LedCalibrationActivity:Activity(){
 private lateinit var view:LedCalibrationView;private lateinit var spinner:Spinner;private val led by lazy{LedController(this)};private var displayId=android.view.Display.DEFAULT_DISPLAY
 override fun onCreate(b:Bundle?){super.onCreate(b);send(LEDService.ACTION_CALIBRATION_ENTER);setContentView(build())}
 private fun build()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF000000.toInt());
  addView(TextView(this@LedCalibrationActivity).apply{text="LED CALIBRATION / BRIGHTNESS TEST";setTextColor(-1);textSize=18f;setPadding(16,16,16,8)})
  spinner=Spinner(this@LedCalibrationActivity);val dm=getSystemService(DisplayManager::class.java);val ds=dm.displays;val labels=ds.map{"Display "+it.displayId};spinner.adapter=ArrayAdapter(this@LedCalibrationActivity,android.R.layout.simple_spinner_dropdown_item,labels);spinner.setSelection(ds.indexOfFirst{it.displayId==displayId}.coerceAtLeast(0));spinner.setOnItemSelectedListener(object:android.widget.AdapterView.OnItemSelectedListener{override fun onItemSelected(p:android.widget.AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){displayId=ds.getOrNull(pos)?.displayId?:android.view.Display.DEFAULT_DISPLAY};override fun onNothingSelected(p:android.widget.AdapterView<*>?) {}});addView(spinner)
  addView(TextView(this@LedCalibrationActivity).apply{text="Capture display: select the screen whose image will be sampled.";setTextColor(0xFFCCCCCC.toInt());setPadding(16,4,16,8)})
  view=LedCalibrationView(this@LedCalibrationActivity);addView(view,LinearLayout.LayoutParams(-1,0,1f))
  val row=LinearLayout(this@LedCalibrationActivity).apply{gravity=Gravity.CENTER};listOf(0,25,50,75,100).forEach{v->addView(Button(this@LedCalibrationActivity).apply{text="LED $v%";setOnClickListener{val b=(255*v/100);led.setLedColor(b,b,b,v*255/100,true,true,true,true)}})};addView(row)
  addView(Button(this@LedCalibrationActivity).apply{text="EXIT / RESTORE";setOnClickListener{send(LEDService.ACTION_CALIBRATION_EXIT);finish()}})
 }
 private fun send(action:String){runCatching{startService(Intent(this,LEDService::class.java).setAction(action))}}
 override fun onDestroy(){send(LEDService.ACTION_CALIBRATION_EXIT);super.onDestroy()}
}