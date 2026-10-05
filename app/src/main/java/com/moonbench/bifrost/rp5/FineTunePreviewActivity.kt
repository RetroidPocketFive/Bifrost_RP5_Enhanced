package com.moonbench.bifrost.rp5
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class FineTunePreviewActivity:AppCompatActivity(){
 private lateinit var editor:ThumbstickViewAreaEditorView;private lateinit var preview:ThumbstickLivePreview;private lateinit var store:FineTuneStore
 override fun onCreate(b:Bundle?){super.onCreate(b);store=FineTuneStore(this);val n=intent.getStringExtra(EXTRA_PROFILE_NAME);val p=n?.let(store::find)?:run{finish();return};preview=ThumbstickLivePreview(this,{editor.setPreviewFrame(it)},{l,r->editor.setSampledColors(l,r)});editor=ThumbstickViewAreaEditorView(this);editor.setRegions(p.leftViewArea,p.rightViewArea);editor.onPreviewRequested={l,r->preview.preview(l,r,p.colourMode)};setContentView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(TextView(this@FineTunePreviewActivity).apply{text="FINE TUNE PREVIEW • "+p.name;setTextColor(-1);textSize=16f;setPadding(12,12,12,12)});addView(editor,LinearLayout.LayoutParams(-1,0,1f));addView(Button(this@FineTunePreviewActivity).apply{text="EXIT";setOnClickListener{finish()}})})}
 override fun onResume(){super.onResume();preview.start();store.find(intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty())?.let{preview.preview(it.leftViewArea,it.rightViewArea,it.colourMode)}}
 override fun onPause(){preview.stop();super.onPause()}
 companion object{const val EXTRA_PROFILE_NAME="fine_tune_profile_name"}
}