package com.moonbench.bifrost.rp5
import android.content.Context
import android.graphics.*
import android.view.*
class ThumbstickViewAreaEditorView(context: Context) : View(context) {
 private enum class Handle { LEFT, RIGHT, NONE }
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val lb=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=5f;color=Color.rgb(50,130,255)}
 private val rb=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=5f;color=Color.rgb(255,145,40)}
 private var frame:Bitmap?=null; private var active=Handle.NONE; private var lx=0f; private var ly=0f
 private var lc=Color.BLACK; private var rc=Color.BLACK
 var leftRegion=NormalizedRegion(.25f,.78f,.18f); private set
 var rightRegion=NormalizedRegion(.75f,.78f,.18f); private set
 var onPreviewRequested:((NormalizedRegion,NormalizedRegion)->Unit)?=null
 fun setPreviewFrame(b:Bitmap?){frame=b;invalidate()}
 fun setSampledColors(l:Int,r:Int){lc=l;rc=r;invalidate()}
 fun setRegions(l:NormalizedRegion,r:NormalizedRegion){leftRegion=l;rightRegion=r;onPreviewRequested?.invoke(l,r);invalidate()}
 fun reset()=setRegions(NormalizedRegion(.25f,.78f,.18f),NormalizedRegion(.75f,.78f,.18f))
 override fun onDraw(c:Canvas){frame?.let{if(!it.isRecycled)c.drawBitmap(it,null,RectF(0f,0f,width.toFloat(),height.toFloat()),p)}?:c.drawColor(Color.DKGRAY);draw(c,leftRegion,lb,lc,"LEFT");draw(c,rightRegion,rb,rc,"RIGHT")}
 private fun draw(c:Canvas,r:NormalizedRegion,border:Paint,color:Int,label:String){val b=r.bounds();val q=RectF(b.left*width,b.top*height,b.right*width,b.bottom*height);p.style=Paint.Style.FILL;p.color=(color and 0xFFFFFF) or 0x66000000;c.drawRect(q,p);c.drawRect(q,border);p.color=Color.WHITE;p.textSize=24f;c.drawText(label,q.left+8,q.top+28,p)}
 override fun onTouchEvent(e:MotionEvent):Boolean{val x=e.x/width.coerceAtLeast(1);val y=e.y/height.coerceAtLeast(1);when(e.actionMasked){MotionEvent.ACTION_DOWN->{active=when{contains(leftRegion,x,y)->Handle.LEFT;contains(rightRegion,x,y)->Handle.RIGHT;else->Handle.NONE};lx=x;ly=y;return active!=Handle.NONE};MotionEvent.ACTION_MOVE->{if(active==Handle.NONE)return false;val dx=x-lx;val dy=y-ly;if(active==Handle.LEFT)leftRegion=move(leftRegion,dx,dy)else rightRegion=move(rightRegion,dx,dy);lx=x;ly=y;onPreviewRequested?.invoke(leftRegion,rightRegion);invalidate();return true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{active=Handle.NONE;return true}};return true}
 private fun contains(r:NormalizedRegion,x:Float,y:Float):Boolean{val b=r.bounds();return x in b.left..b.right&&y in b.top..b.bottom}
 private fun move(r:NormalizedRegion,dx:Float,dy:Float):NormalizedRegion{val h=r.size/2f;return r.copy(centerX=(r.centerX+dx).coerceIn(h,1f-h),centerY=(r.centerY+dy).coerceIn(h,1f-h))}
}