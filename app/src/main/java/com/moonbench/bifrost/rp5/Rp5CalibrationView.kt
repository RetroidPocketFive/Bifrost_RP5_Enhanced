package com.moonbench.bifrost.rp5

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Rp5CalibrationView(context: Context, private val changed: (NormalizedRegion, NormalizedRegion) -> Unit) : View(context) {
    var leftRegion = NormalizedRegion(.25f, .78f, .18f); set(v) { field=v; invalidate() }
    var rightRegion = NormalizedRegion(.75f, .78f, .18f); set(v) { field=v; invalidate() }
    var leftColor = Color.WHITE; set(v) { field=v; invalidate() }
    var rightColor = Color.WHITE; set(v) { field=v; invalidate() }
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = 0
    private var resizing = false
    private var lastX = 0f
    private var lastY = 0f

    fun setFrame(value: Bitmap?) { bitmap?.recycle(); bitmap=value; invalidate() }

    fun sample(sampler: ColorSampler): SampledColors? {
        val b=bitmap ?: return null
        val pixels=IntArray(b.width*b.height)
        b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
        return sampler.sample(pixels,b.width,b.height,leftRegion,rightRegion)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val b=bitmap
        if (b != null) {
            val scale=min(width.toFloat()/b.width,height.toFloat()/b.height)
            val w=b.width*scale; val h=b.height*scale
            c.drawBitmap(b,null,RectF((width-w)/2f,(height-h)/2f,(width+w)/2f,(height+h)/2f),paint)
        } else c.drawColor(Color.BLACK)
        drawRegion(c,leftRegion,leftColor,"LEFT",1)
        drawRegion(c,rightRegion,rightColor,"RIGHT",2)
    }

    private fun drawRegion(c:Canvas,r:NormalizedRegion,color:Int,label:String,id:Int) {
        val s=r.size*min(width,height); val x=r.centerX*width; val y=r.centerY*height
        val left=x-s/2f; val top=y-s/2f
        paint.style=Paint.Style.FILL
        paint.color=Color.argb(if(active==id) 48 else 28,Color.red(color),Color.green(color),Color.blue(color))
        c.drawRect(left,top,left+s,top+s,paint)

        paint.style=Paint.Style.STROKE
        paint.strokeWidth=if(active==id)6f else 4f
        paint.color=Color.WHITE
        c.drawRect(left,top,left+s,top+s,paint)
        paint.strokeWidth=2f
        paint.color=color
        c.drawRect(left+2f,top+2f,left+s-2f,top+s-2f,paint)

        // Corner handles make resize affordance obvious on a touch screen.
        paint.style=Paint.Style.FILL
        paint.color=color
        val h=10f
        c.drawCircle(left,top,h/2f,paint)
        c.drawCircle(left+s,top,h/2f,paint)
        c.drawCircle(left,top+s,h/2f,paint)
        c.drawCircle(left+s,top+s,h/2f,paint)

        paint.color=Color.argb(205,0,0,0)
        c.drawRoundRect(RectF(left,top,left+92f,top+34f),8f,8f,paint)
        paint.color=Color.WHITE
        paint.textSize=15f
        c.drawText(label,left+9f,top+23f,paint)
    }

    override fun onTouchEvent(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active=when { hit(leftRegion,e.x,e.y)->1; hit(rightRegion,e.x,e.y)->2; else->0 }
                if(active>0) {
                    val r=if(active==1)leftRegion else rightRegion
                    val s=r.size*min(width,height)
                    resizing=abs(e.x-r.centerX*width)>s*.38f || abs(e.y-r.centerY*height)>s*.38f
                    lastX=e.x; lastY=e.y
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(active==0)return true
                val dx=(e.x-lastX)/width.coerceAtLeast(1); val dy=(e.y-lastY)/height.coerceAtLeast(1)
                val old=if(active==1)leftRegion else rightRegion
                val next=if(resizing) old.copy(size=(old.size+(dx+dy)/2f).coerceIn(.05f,.5f))
                else old.copy(centerX=(old.centerX+dx).coerceIn(.05f,.95f),centerY=(old.centerY+dy).coerceIn(.05f,.95f))
                if(active==1)leftRegion=next else rightRegion=next
                changed(leftRegion,rightRegion); lastX=e.x; lastY=e.y; return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { active=0; resizing=false; invalidate(); return true }
        }
        return true
    }

    private fun hit(r:NormalizedRegion,x:Float,y:Float):Boolean {
        val s=r.size*min(width,height); val cx=r.centerX*width; val cy=r.centerY*height
        return x>=cx-s/2f && x<=cx+s/2f && y>=cy-s/2f && y<=cy+s/2f
    }
}