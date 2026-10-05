package com.moonbench.bifrost.rp5
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class FineTuneManagerActivity:AppCompatActivity(){
 private lateinit var store:FineTuneStore;private lateinit var list:LinearLayout
 override fun onCreate(b:Bundle?){super.onCreate(b);store=FineTuneStore(this);setContentView(build());refresh()}
 override fun onResume(){super.onResume();if(::list.isInitialized)refresh()}
 private fun build()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24);setBackgroundColor(0xFF101010.toInt());
  addView(TextView(this@FineTuneManagerActivity).apply{text="THUMBSTICK FINE TUNE";textSize=22f;setTextColor(0xFFFFFFFF.toInt())})
  addView(TextView(this@FineTuneManagerActivity).apply{text="Saved View Area profiles, colour modes and LED calibration.";setTextColor(0xFFCCCCCC.toInt());setPadding(0,8,0,16)})
  addView(Button(this@FineTuneManagerActivity).apply{text="NEW FINE TUNE";setOnClickListener{openEditor(null)}})
  addView(Button(this@FineTuneManagerActivity).apply{text="LED CALIBRATION / BRIGHTNESS TEST";setOnClickListener{startActivity(Intent(this@FineTuneManagerActivity,LedCalibrationActivity::class.java))}})
  list=LinearLayout(this@FineTuneManagerActivity).apply{orientation=LinearLayout.VERTICAL};addView(list,LinearLayout.LayoutParams(-1,0,1f))
  addView(Button(this@FineTuneManagerActivity).apply{text="EXIT";setOnClickListener{finish()}})
 }
 private fun refresh(){list.removeAllViews();val ps=store.list();if(ps.isEmpty()){list.addView(TextView(this).apply{text="No Fine Tune profiles saved yet.";gravity=Gravity.CENTER;setTextColor(0xFFCCCCCC.toInt());textSize=16f});return};ps.forEach{p->val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12);setBackgroundColor(0xFF202020.toInt());addView(TextView(this@FineTuneManagerActivity).apply{text=p.name+"  •  "+mode(p.colourMode);setTextColor(0xFFFFFFFF.toInt());textSize=17f});val a=LinearLayout(this@FineTuneManagerActivity).apply{addView(btn("SELECT"){store.setCurrent(p.name);refresh()});addView(btn("PREVIEW"){startActivity(Intent(this@FineTuneManagerActivity,FineTunePreviewActivity::class.java).putExtra(FineTunePreviewActivity.EXTRA_PROFILE_NAME,p.name))});addView(btn("EDIT"){openEditor(p.name)});addView(btn("PROFILE"){bind(p)});addView(btn("DELETE"){confirmDelete(p.name)})};addView(a)};list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=8})}}
 private fun btn(s:String,a:()->Unit)=Button(this).apply{text=s;textSize=10f;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
 private fun mode(m:ThumbstickColourMode)=when(m){ThumbstickColourMode.FIFTY_FIFTY->"50/50";ThumbstickColourMode.AVERAGE->"Average";ThumbstickColourMode.CLOSE_MATCH->"Close Match";ThumbstickColourMode.MOST_DOMINANT->"Most Dominant";ThumbstickColourMode.DOMINANT_PRIME->"Dominant Prime"}
 private fun openEditor(n:String?){startActivity(Intent(this,ThumbstickViewAreaEditorActivity::class.java).putExtra(ThumbstickViewAreaEditorActivity.EXTRA_PROFILE_NAME,n))}
 private fun loadPresetNames():List<String>{val raw=getSharedPreferences("bifrost_prefs",MODE_PRIVATE).getString("presets_json",null)?:return emptyList();return runCatching{val a=org.json.JSONArray(raw);buildList{for(i in 0 until a.length())a.optJSONObject(i)?.optString("name")?.takeIf{it.isNotBlank()}?.let(::add)}}.getOrDefault(emptyList())}
 private fun bind(p:FineTuneProfile){val names=loadPresetNames();if(names.isEmpty()){Toast.makeText(this,"No Bifrost profiles found",Toast.LENGTH_SHORT).show();return};val map=store.getProfileBindings().toMutableMap();val checks=names.map{map[it]?.equals(p.name,true)==true}.toBooleanArray();AlertDialog.Builder(this).setTitle("Profiles using "+p.name).setMultiChoiceItems(names.toTypedArray(),checks){_,i,c->checks[i]=c}.setNegativeButton("CANCEL",null).setPositiveButton("SAVE"){_,_->names.forEachIndexed{i,n->if(checks[i])map[n]=p.name else if(map[n]?.equals(p.name,true)==true)map.remove(n)};names.forEach{store.setProfileBinding(it,map[it])};refresh()}.show()}
 private fun confirmDelete(n:String){AlertDialog.Builder(this).setTitle("Delete Fine Tune?").setMessage(n).setNegativeButton("CANCEL",null).setPositiveButton("DELETE"){_,_->store.delete(n);refresh()}.show()}
}