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
  orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF080C18.toInt());setPadding(16,12,16,12)
  addView(TextView(this@LedCalibrationActivity).apply{text=when(mode){MODE_LED_COLOR->"LED COLOR MATCH";MODE_LED_BRIGHTNESS->"LED BRIGHTNESS";else->"SCREEN COLOR MATCH"};textSize=22f;setTextColor(0xFFF2F4FF.toInt());setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(4,4,4,10)})
  if(mode==MODE_SCREEN){
   val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   controls.addView(TextView(this@LedCalibrationActivity).apply{text="CAPTURE DISPLAY:";setTextColor(0xFF8992AD.toInt());textSize=11f})
   val spinner=Spinner(this@LedCalibrationActivity);displaySpinner=spinner;controls.addView(spinner,LinearLayout.LayoutParams(0,-2,1f));addView(controls);populateDisplays()
  }
  if(mode==MODE_SCREEN){
   view=LedCalibrationView(this@LedCalibrationActivity);view.onRegionsChanged={l,r->if(runningLive)preview.preview(l,r,ThumbstickColourMode.AVERAGE)};addView(view,LinearLayout.LayoutParams(-1,0,1f))
   addView(LinearLayout(this@LedCalibrationActivity).apply{orientation=LinearLayout.HORIZONTAL;addView(Button(this@LedCalibrationActivity).apply{text="LIVE";setOnClickListener{startLive()}});addView(Button(this@LedCalibrationActivity).apply{text="STILL IMAGE";setOnClickListener{pickStill()}});addView(Button(this@LedCalibrationActivity).apply{text="RESET";setOnClickListener{view.resetRegions();view.setFrame(null);stopLive()}});addView(Button(this@LedCalibrationActivity).apply{text="CLOSE";setOnClickListener{finish()}})})
  } else if(mode==MODE_LED_COLOR){
   addView(TextView(this@LedCalibrationActivity).apply{text="Controlled red, green, blue and white LED output for physical colour matching.";textSize=14f;setTextColor(0xFF8992AD.toInt());setPadding(6,8,6,18)})
   addView(TextView(this@LedCalibrationActivity).apply{text="LED COLOUR MATCH";textSize=28f;gravity=Gravity.CENTER;setTextColor(0xFFF2F4FF.toInt());setBackgroundColor(0xFF101629.toInt())},LinearLayout.LayoutParams(-1,0,1f))
   addView(LinearLayout(this@LedCalibrationActivity).apply{orientation=LinearLayout.HORIZONTAL;addView(Button(this@LedCalibrationActivity).apply{text="TEST COLOR";setOnClickListener{colourMatchTest()}});addView(Button(this@LedCalibrationActivity).apply{text="CLOSE";setOnClickListener{finish()}})})
  } else {
   addView(TextView(this@LedCalibrationActivity).apply{text="Test LED white output at OFF, 25%, 50%, 75% and 100%.";textSize=14f;setTextColor(0xFF8992AD.toInt());setPadding(6,8,6,18)})
   addView(TextView(this@LedCalibrationActivity).apply{text="LED BRIGHTNESS";textSize=28f;gravity=Gravity.CENTER;setTextColor(0xFFF2F4FF.toInt());setBackgroundColor(0xFF101629.toInt())},LinearLayout.LayoutParams(-1,0,1f))
   addView(LinearLayout(this@LedCalibrationActivity).apply{orientation=LinearLayout.HORIZONTAL;addView(Button(this@LedCalibrationActivity).apply{text="TEST BRIGHTNESS";setOnClickListener{brightnessTest()}});addView(Button(this@LedCalibrationActivity).apply{text="CLOSE";setOnClickListener{finish()}})})
  }
 } private fun startLive(){stopLive();runningLive=true;preview=ThumbstickLivePreview(this,{view.setFrame(it);statusNoop()},{l,r->view.setColors(l,r)},displayId);preview.start();val(l,r)=view.currentRegions();preview.preview(l,r,ThumbstickColourMode.AVERAGE)}
 private fun stopLive(){if(::preview.isInitialized)preview.stop();runningLive=false}
 private fun pickStill(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},PICK_IMAGE)}
 private fun brightnessTest(){val levels=intArrayOf(0,64,128,192,255);brightnessTestIndex=(brightnessTestIndex+1)%levels.size;val b=levels[brightnessTestIndex];led.setLedColorDual(255,255,255,255,255,255,b)}
 private fun colourMatchTest(){testIndex=(testIndex+1)%4;val c=intArrayOf(android.graphics.Color.RED,android.graphics.Color.GREEN,android.graphics.Color.BLUE,android.graphics.Color.WHITE)[testIndex];led.setLedColorDual(android.graphics.Color.red(c),android.graphics.Color.green(c),android.graphics.Color.blue(c),android.graphics.Color.red(c),android.graphics.Color.green(c),android.graphics.Color.blue(c),brightnessLevel)}
 private fun statusNoop(){}
 private fun populateDisplays(){val dm=getSystemService(DisplayManager::class.java);val ds=dm.displays;displaySpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,ds.map{"Capture Display "+it.displayId});displaySpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){displayId=ds.getOrNull(pos)?.displayId?:android.view.Display.DEFAULT_DISPLAY};override fun onNothingSelected(p:AdapterView<*>?) {}}}
 private fun send(action:String){runCatching{startService(Intent(this,LEDService::class.java).setAction(action))}}
 override fun onResume(){super.onResume();if(::preview.isInitialized)preview.start()}
 override fun onPause(){if(::preview.isInitialized)preview.stop();super.onPause()}
 override fun onDestroy(){send(LEDService.ACTION_CALIBRATION_EXIT);super.onDestroy()}
}