package com.moonbench.bifrost.rp5
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class Rp5CalibrationMenuActivity:Activity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;setContentView(build())}
 private fun build():View=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF080C18.toInt());setPadding(dp(20),dp(18),dp(20),dp(18))
  addView(TextView(this@Rp5CalibrationMenuActivity).apply{text="RP5 CALIBRATION";textSize=28f;setTextColor(0xFFF2F4FF.toInt());setTypeface(typeface,Typeface.BOLD)})
  addView(TextView(this@Rp5CalibrationMenuActivity).apply{text="Select the calibration or diagnostic test you want to run.";textSize=13f;setTextColor(0xFF8992AD.toInt());setPadding(0,dp(4),0,dp(16))})
  val c=LinearLayout(this@Rp5CalibrationMenuActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
  c.addView(option("SCREEN COLOR MATCH","Match the LED output to colours displayed on the RP5 screen. Choose Live or Still Image inside the test.",LedCalibrationActivity.MODE_SCREEN))
  c.addView(option("LED COLOR MATCH","Run controlled red, green, blue and white LED output tests for physical colour matching.",LedCalibrationActivity.MODE_LED_COLOR))
  c.addView(option("LED BRIGHTNESS","Run the LED brightness test through OFF, 25%, 50%, 75% and 100% white levels.",LedCalibrationActivity.MODE_LED_BRIGHTNESS))
  addView(c,LinearLayout.LayoutParams(-1,0,1f))
  addView(TextView(this@Rp5CalibrationMenuActivity).apply{text="CLOSE";gravity=Gravity.CENTER;textSize=14f;setTextColor(0xFFF2F4FF.toInt());setBackgroundColor(0xFF101629.toInt());setOnClickListener{finish()}},LinearLayout.LayoutParams(-1,dp(52)))
 }
 private fun option(t:String,d:String,m:String):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14));setBackgroundColor(0xFF101629.toInt());setOnClickListener{startActivity(Intent(this@Rp5CalibrationMenuActivity,LedCalibrationActivity::class.java).putExtra(LedCalibrationActivity.EXTRA_MODE,m))};addView(TextView(this@Rp5CalibrationMenuActivity).apply{text=t;textSize=18f;setTextColor(0xFFF2F4FF.toInt());setTypeface(typeface,Typeface.BOLD)});addView(TextView(this@Rp5CalibrationMenuActivity).apply{text=d;textSize=13f;setTextColor(0xFF8992AD.toInt());setPadding(0,dp(4),0,0)});layoutParams=LinearLayout.LayoutParams(-1,0,1f).apply{bottomMargin=dp(10)}}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}