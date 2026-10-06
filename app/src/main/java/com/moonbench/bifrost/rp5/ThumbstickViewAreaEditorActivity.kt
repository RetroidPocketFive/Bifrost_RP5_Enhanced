package com.moonbench.bifrost.rp5
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ThumbstickViewAreaEditorActivity:AppCompatActivity(){
 private lateinit var editor:ThumbstickViewAreaEditorView
 private lateinit var preview:ThumbstickLivePreview
 private lateinit var store:FineTuneStore
 private var name:String?=null;private var mode=ThumbstickColourMode.AVERAGE;private var captureMode=0
 override fun onCreate(b:Bundle?){super.onCreate(b);store=FineTuneStore(this);name=intent.getStringExtra(EXTRA_PROFILE_NAME);setContentView(build());store.find(name.orEmpty())?.let{mode=it.colourMode;editor.setRegions(it.leftViewArea,it.rightViewArea)};editor.onPreviewRequested={l,r->preview.preview(l,r,mode)};chooseCaptureSource()}
 private fun build()=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF000000.toInt())
  addView(TextView(this@ThumbstickViewAreaEditorActivity).apply{text="SAMPLE AREA — VIEWING PROFILE";textSize=18f;setTextColor(-1);setPadding(12,10,12,4)})
  addView(TextView(this@ThumbstickViewAreaEditorActivity).apply{text="Set the reusable screen viewing areas for Ambient. Move the BLUE left area and ORANGE right area. Each thumb-stick LED previews the colour sampled from its own area.";textSize=11f;setTextColor(0xFFCCCCCC.toInt());setPadding(12,0,12,6)})
  val modeSpinner=Spinner(this@ThumbstickViewAreaEditorActivity)
  val modes=listOf(ThumbstickColourMode.FIFTY_FIFTY to "50/50",ThumbstickColourMode.AVERAGE to "Average Colour",ThumbstickColourMode.CLOSE_MATCH to "Close Match Colour",ThumbstickColourMode.MOST_DOMINANT to "Most Dominant Colour",ThumbstickColourMode.DOMINANT_PRIME to "Dominant Prime Colour")
  modeSpinner.adapter=ArrayAdapter(this@ThumbstickViewAreaEditorActivity,android.R.layout.simple_spinner_dropdown_item,modes.map{it.second})
  modeSpinner.setSelection(modes.indexOfFirst{it.first==mode}.coerceAtLeast(0))
  var spinnerReady=false
  modeSpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){mode=modes[pos].first;if(spinnerReady&&::editor.isInitialized)preview.preview(editor.leftRegion,editor.rightRegion,mode);spinnerReady=true};override fun onNothingSelected(p:AdapterView<*>?){}}
  addView(TextView(this@ThumbstickViewAreaEditorActivity).apply{text="COLOUR SAMPLING METHOD";textSize=10f;setTextColor(0xFFAAAAAA.toInt());setPadding(12,4,12,0)})
  addView(modeSpinner,LinearLayout.LayoutParams(-1,-2))
  editor=ThumbstickViewAreaEditorView(this@ThumbstickViewAreaEditorActivity);addView(editor,LinearLayout.LayoutParams(-1,0,1f))
  addView(LinearLayout(this@ThumbstickViewAreaEditorActivity).apply{
   addView(Button(this@ThumbstickViewAreaEditorActivity).apply{text="SAVE VIEWING PROFILE";setOnClickListener{save()}})
   addView(Button(this@ThumbstickViewAreaEditorActivity).apply{text="RESET AREAS";setOnClickListener{editor.reset()}})
   addView(Button(this@ThumbstickViewAreaEditorActivity).apply{text="EXIT";setOnClickListener{finish()}})
  })
 }
 private fun chooseCaptureSource(){AlertDialog.Builder(this).setTitle("CREATE VIEWING PROFILE — CAPTURE SOURCE").setMessage("Choose the screen image used to define the Sample Area. Live Capture continuously updates the screen image. Still Capture freezes one captured frame for positioning the areas.").setItems(arrayOf("LIVE CAPTURE","STILL CAPTURE")){_,which->captureMode=which;if(::preview.isInitialized)preview.stop();preview=ThumbstickLivePreview(this,{editor.setPreviewFrame(it)},{l,r->editor.setSampledColors(l,r)});if(which==0)preview.start() else preview.captureOnce();preview.preview(editor.leftRegion,editor.rightRegion,mode)}.setNegativeButton("CANCEL"){_,_->finish()}.show()}
 override fun onResume(){super.onResume();if(::preview.isInitialized&&captureMode==0)preview.start()}
 override fun onPause(){if(::preview.isInitialized)preview.stop();super.onPause()}
 private fun save(){val input=EditText(this).apply{hint="Viewing profile name";setText(name.orEmpty())};AlertDialog.Builder(this).setTitle("SAVE VIEWING PROFILE").setMessage("This saves the Sample Area as a reusable Ambient Viewing Profile. Fine Tune can select this profile for Ambient screen matching.").setView(input).setNegativeButton("CANCEL",null).setPositiveButton("SAVE"){_,_->val n=input.text.toString().trim();if(n.isNotEmpty()){store.save(FineTuneProfile(n,editor.leftRegion,editor.rightRegion,mode));name=n;finish()}}.show()}
 companion object{const val EXTRA_PROFILE_NAME="fine_tune_profile_name"}
}