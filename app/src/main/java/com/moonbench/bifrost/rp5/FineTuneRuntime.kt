package com.moonbench.bifrost.rp5
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.moonbench.bifrost.services.BifrostAccessibilityService
import com.moonbench.bifrost.tools.LedController
import java.util.concurrent.Executor
class FineTuneRuntime(private val context:Context, private val profile:FineTuneProfile, private val displayId:Int=Display.DEFAULT_DISPLAY, private val brightness:()->Int={255}) {
 private val h=Handler(Looper.getMainLooper());private val led=LedController(context);private var running=false;private var bitmap:Bitmap?=null
 private val capture=object:AccessibilityService.TakeScreenshotCallback{
  override fun onSuccess(s:AccessibilityService.ScreenshotResult){try{val hw=Bitmap.wrapHardwareBuffer(s.hardwareBuffer,s.colorSpace);val sw=hw?.copy(Bitmap.Config.ARGB_8888,false);hw?.recycle();if(sw!=null){bitmap=sw;sample(sw)}}finally{s.hardwareBuffer.close();schedule()}}
  override fun onFailure(errorCode:Int){schedule(500L)}
 }
 fun start(){if(!running){running=true;request()}}
 fun stop(){running=false;h.removeCallbacksAndMessages(null);bitmap=null}
 private fun request(){if(!running)return;val s=BifrostAccessibilityService.instance;if(s==null||!BifrostAccessibilityService.isEnabled(s)){schedule(500L);return};runCatching{s.takeScreenshot(displayId,Executor{q->h.post(q)},capture)}.onFailure{schedule(500L)}}
 private fun schedule(delay:Long=160L){if(running)h.postDelayed(::request,delay)}
 private fun sample(b:Bitmap){val p=IntArray(b.width*b.height);b.getPixels(p,0,b.width,0,0,b.width,b.height);val m=when(profile.colourMode){ThumbstickColourMode.MOST_DOMINANT,ThumbstickColourMode.DOMINANT_PRIME->SamplingMethod.DOMINANT;ThumbstickColourMode.CLOSE_MATCH->SamplingMethod.CENTER_WEIGHTED;else->SamplingMethod.AVERAGE};val c=ColorSampler(m).sample(p,b.width,b.height,profile.leftViewArea,profile.rightViewArea);val x=ThumbstickColourProcessor.process(c.left,c.right,profile.colourMode);led.setLedColorDual(Color.red(x.left),Color.green(x.left),Color.blue(x.left),Color.red(x.right),Color.green(x.right),Color.blue(x.right),brightness())}
}