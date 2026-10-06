package com.moonbench.bifrost.rp5
import android.content.Context
import android.graphics.*
import android.view.*

class LedCalibrationView(context:Context):View(context){
 var left=NormalizedRegion(.25f,.78f,.18f);var right=NormalizedRegion(.75f,.78f,.18f);private var frame:Bitmap?=null;private var lc=Color.BLACK;private var rc=Color.BLACK;private var active=0;private var lastX=0f;private var lastY=0f
 var onRegionChanged:((NormalizedRegion,NormalizedRegion)->Unit)?=null
 fun setFrame(b:Bitmap?){frame=b;invalidate()};fun setColors(l:Int,r:Int){lc=l;rc=r;invalidate()};fun setRegions(l:NormalizedRegion,r:NormalizedRegion){left=l;right=r;invalidate()}
 override fun onDraw(c:Canvas){frame?.let{if(!it.isRecycled)c.drawBitmap(it,null,RectF(0f,0f,width.toFloat(),height.toFloat()),Paint())}?:c.drawColor(Color.BLACK);draw(c,left,Color.rgb(50,130,255),lc,"LEFT");draw(c,right,Color.rgb(255,145,40),rc,"RIGHT")}
 private fun draw(c:Canvas,r:NormalizedRegion,border:Int,color:Int,label:String){val b=r.bounds();val q=RectF(b.left*width,b.top*height,b.right*width,b.bottom*height);val p=Paint(Paint.ANTI_ALIAS_FLAG);p.color=(color and 0xFFFFFF) or 0x66000000;c.drawRect(q,p);p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=border;c.drawRect(q,p);p.style=Paint.Style.FILL;p.color=Color.WHITE;p.textSize=24f;c.drawText(label,q.left+8,q.top+28,p)}
 override fun onTouchEvent(e:MotionEvent):Boolean{val x=e.x/width.coerceAtLeast(1);val y=e.y/height.coerceAtLeast(1);when(e.actionMasked){MotionEvent.ACTION_DOWN->{active=when{contains(left,x,y)->1;contains(right,x,y)->2;else->0};lastX=x;lastY=y;return active!=0};MotionEvent.ACTION_MOVE->{if(active==0)return false;val dx=x-lastX;val dy=y-lastY;if(active==1)left=move(left,dx,dy)else right=move(right,dx,dy);lastX=x;lastY=y;onRegionChanged?.invoke(left,right);invalidate();return true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{active=0;return true}};return true}
 private fun contains(r:NormalizedRegion,x:Float,y:Float):Boolean{val b=r.bounds();return x in b.left..b.right&&y in b.top..b.bottom}
 private fun move(r:NormalizedRegion,dx:Float,dy:Float):NormalizedRegion{val h=r.size/2f;return r.copy(centerX=(r.centerX+dx).coerceIn(h,1f-h),centerY=(r.centerY+dy).coerceIn(h,1f-h))}
}