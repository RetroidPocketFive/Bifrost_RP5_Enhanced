package com.moonbench.bifrost.rp5
import android.graphics.*
import android.view.*
import android.content.Context
class LedCalibrationView(context:Context):View(context){
 var left=NormalizedRegion(.25f,.78f,.18f);var right=NormalizedRegion(.75f,.78f,.18f);private var frame:Bitmap?=null;private var lc=Color.BLACK;private var rc=Color.BLACK
 fun setFrame(b:Bitmap?){frame=b;invalidate()};fun setColors(l:Int,r:Int){lc=l;rc=r;invalidate()};fun setRegions(l:NormalizedRegion,r:NormalizedRegion){left=l;right=r;invalidate()}
 override fun onDraw(c:Canvas){frame?.let{if(!it.isRecycled)c.drawBitmap(it,null,RectF(0f,0f,width.toFloat(),height.toFloat()),Paint())}?:c.drawColor(Color.BLACK);draw(c,left,Color.rgb(50,130,255),lc,"LEFT");draw(c,right,Color.rgb(255,145,40),rc,"RIGHT")}
 private fun draw(c:Canvas,r:NormalizedRegion,border:Int,color:Int,label:String){val b=r.bounds();val q=RectF(b.left*width,b.top*height,b.right*width,b.bottom*height);val p=Paint(Paint.ANTI_ALIAS_FLAG);p.color=(color and 0xFFFFFF) or 0x66000000;c.drawRect(q,p);p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=border;c.drawRect(q,p);p.style=Paint.Style.FILL;p.color=Color.WHITE;p.textSize=24f;c.drawText(label,q.left+8,q.top+28,p)}
}