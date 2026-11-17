 package com.capri.app

 import android.animation.Animator
 import android.animation.AnimatorListenerAdapter
 import android.animation.ValueAnimator
 import android.content.Context
 import android.graphics.Canvas
 import android.graphics.Color
 import android.graphics.Paint
 import android.graphics.Path
 import android.graphics.PixelFormat
 import android.view.Gravity
 import android.view.View
 import android.view.WindowManager

 class BeamView(
     ctx: Context,
     private val sx: Float,
     private val sy: Float,
     private val tx: Float,
     private val ty: Float
 ) : View(ctx) {
     private val d = ctx.resources.displayMetrics.density
     private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
     private val path = Path()
     private val loc = IntArray(2)
     private val variants = ArrayList<FloatArray>()

     var p = 0f
         set(value) {
             field = value
             invalidate()
         }

     init {
         val dx = tx - sx
         val dy = ty - sy
         val len = Math.max(1f, Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat())
         val nx = -dy / len
         val ny = dx / len
         val n = 12
         for (v in 0 until 3) {
             val arr = FloatArray((n + 1) * 2)
             for (i in 0..n) {
                 val f = i.toFloat() / n
                 var off = 0f
                 if (i != 0 && i != n) {
                     val sign = if (i % 2 == 0) 1f else -1f
                     off = sign * (14f + Math.random().toFloat() * 22f) * d
                 }
                 arr[i * 2] = sx + dx * f + nx * off
                 arr[i * 2 + 1] = sy + dy * f + ny * off
             }
             variants.add(arr)
         }
     }

     private fun setColor(hex: String, a: Int) {
         paint.color = Color.parseColor(hex)
         paint.alpha = a
     }

     private fun trail(c: Canvas, k: Float, a: Int) {
         val dx = tx - sx
         val dy = ty - sy
         val len = Math.max(1f, Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat())
         val step = 11f * d
         val count = (len * k / step).toInt()
         paint.style = Paint.Style.FILL
         for (i in 1..count) {
             val f = i * step / len
             val r = (1.2f + 2.2f * (1f - f)) * d
             setColor(if (i % 3 == 0) "#ffffff" else "#c9b8ff", a)
             c.drawCircle(sx + dx * f, sy + dy * f, r, paint)
         }
     }

     private fun star(c: Canvas, scale: Float, a: Int) {
         val big = 20f * d * scale
         val small = 5.5f * d * scale
         path.reset()
         for (i in 0 until 8) {
             val r = if (i % 2 == 0) big else small
             val ang = Math.toRadians((-90 + i * 45).toDouble())
             val x = tx + (Math.cos(ang) * r).toFloat()
             val y = ty + (Math.sin(ang) * r).toFloat()
             if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
         }
         path.close()
         paint.style = Paint.Style.FILL
         setColor("#ffffff", a)
         c.drawPath(path, paint)
         setColor("#ff2d6f", a)
         c.drawCircle(tx, ty, 3f * d * scale, paint)
     }

     private fun beam(c: Canvas, frame: Int, a: Int) {
         val v = variants[frame % variants.size]
         path.reset()
         path.moveTo(v[0], v[1])
         var i = 1
         while (i * 2 < v.size) {
             path.lineTo(v[i * 2], v[i * 2 + 1])
             i++
         }
         paint.style = Paint.Style.STROKE
         setColor("#7b3ff2", a)
         paint.strokeWidth = 14f * d
         c.drawPath(path, paint)
         setColor("#ff2d6f", a)
         paint.strokeWidth = 8f * d
         c.drawPath(path, paint)
         setColor("#ffffff", a)
         paint.strokeWidth = 3f * d
         c.drawPath(path, paint)
     }

     private fun burst(c: Canvas, k: Float, a: Int) {
         paint.style = Paint.Style.STROKE
         paint.strokeWidth = 3f * d
         paint.strokeCap = Paint.Cap.ROUND
         val colors = arrayOf("#ffffff", "#7b3ff2", "#ff2d6f")
         for (i in 0 until 18) {
             val ang = Math.PI * 2 * i / 18
             val r0 = 24f * d
             val len = (if (i % 2 == 0) 95f else 52f) * d * (0.4f + 0.6f * k)
             setColor(colors[i % 3], a)
             c.drawLine(
                 tx + (Math.cos(ang) * r0).toFloat(),
                 ty + (Math.sin(ang) * r0).toFloat(),
                 tx + (Math.cos(ang) * (r0 + len)).toFloat(),
                 ty + (Math.sin(ang) * (r0 + len)).toFloat(),
                 paint
             )
         }
         paint.strokeCap = Paint.Cap.BUTT
     }

     private fun ringAtSource(c: Canvas, k: Float, a: Int) {
         paint.style = Paint.Style.STROKE
         paint.strokeWidth = 2f * d
         setColor("#7b3ff2", a)
         c.drawCircle(sx, sy, (36f + 22f * k) * d, paint)
         setColor("#ff2d6f", a)
         c.drawCircle(sx, sy, (30f + 14f * k) * d, paint)
     }

     private fun cross(c: Canvas, k: Float, a: Int) {
         val kk = Math.min(1f, Math.max(0f, k))
         paint.style = Paint.Style.STROKE
         paint.strokeWidth = 2f * d
         setColor("#ff2d6f", a)
         c.drawCircle(tx, ty, 26f * d, paint)
         setColor("#7b3ff2", a)
         c.drawCircle(tx, ty, 32f * d, paint)
         setColor("#c9b8ff", a)
         val gap = 36f * d
         val arm = (30f + 40f * kk) * d
         c.drawLine(tx - gap - arm, ty, tx - gap, ty, paint)
         c.drawLine(tx + gap, ty, tx + gap + arm, ty, paint)
         c.drawLine(tx, ty - gap - arm, tx, ty - gap, paint)
         c.drawLine(tx, ty + gap, tx, ty + gap + arm, paint)
     }

     override fun onDraw(c: Canvas) {
         getLocationOnScreen(loc)
         c.save()
         c.translate(-loc[0].toFloat(), -loc[1].toFloat())
         val t = p
         val fade = if (t > 0.85f) 1f - (t - 0.85f) / 0.15f else 1f
         val a = Math.max(0, Math.min(255, (255 * fade).toInt()))
         if (t < 0.2f) {
             val k = t / 0.2f
             trail(c, k, a)
             star(c, 0.4f + 0.6f * k, a)
         } else if (t < 0.55f) {
             val k = (t - 0.2f) / 0.35f
             val frame = (t * 60).toInt()
             beam(c, frame, a)
             burst(c, k, a)
             trail(c, 1f, a / 3)
             star(c, 1.3f, a)
             ringAtSource(c, k, a)
         } else {
             trail(c, 1f, a / 2)
             star(c, 0.7f, a)
             cross(c, (t - 0.55f) / 0.3f, a)
         }
         c.restore()
     }
 }

 object Beam {
     private var view: BeamView? = null
     private var anim: ValueAnimator? = null
     private var wm: WindowManager? = null

     fun clear() {
         try {
             anim?.removeAllListeners()
             anim?.cancel()
         } catch (e: Exception) {
         }
         anim = null
         try {
             val v = view
             if (v != null) wm?.removeView(v)
         } catch (e: Exception) {
         }
         view = null
     }

     fun fire(ctx: Context, capri: View, where: String) {
         clear()
         val m = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
         val dm = ctx.resources.displayMetrics
         val w = dm.widthPixels.toFloat()
         val h = dm.heightPixels.toFloat()
         val loc = IntArray(2)
         capri.getLocationOnScreen(loc)
         val sx = loc[0] + capri.width * 0.5f
         val sy = loc[1] + capri.height * 0.3f

         var tx = 0f
         var ty = 0f
         val low = where.lowercase()
         if (low.contains("meio") || low.contains("centro")) {
             tx = w * 0.5f
             ty = h * 0.5f
         } else if (low.contains("cima") || low.contains("alto") || low.contains("topo")) {
             tx = w * 0.5f
             ty = h * 0.12f
         } else if (low.contains("baixo")) {
             tx = w * 0.5f
             ty = h * 0.85f
         } else if (low.contains("esquerda")) {
             tx = w * 0.12f
             ty = h * 0.5f
         } else if (low.contains("direita")) {
             tx = w * 0.88f
             ty = h * 0.5f
         } else {
             tx = w * (0.15f + 0.7f * Math.random().toFloat())
             ty = h * (0.15f + 0.7f * Math.random().toFloat())
         }
         val near = Math.hypot((tx - sx).toDouble(), (ty - sy).toDouble())
         if (near < 160 * dm.density) {
             tx = if (sx < w * 0.5f) w * 0.85f else w * 0.15f
             ty = h * 0.5f
         }

         val v = BeamView(ctx, sx, sy, tx, ty)
         val lp = WindowManager.LayoutParams(
             WindowManager.LayoutParams.MATCH_PARENT,
             WindowManager.LayoutParams.MATCH_PARENT,
             WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                 WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                 WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                 WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
             PixelFormat.TRANSLUCENT
         )
         lp.gravity = Gravity.TOP or Gravity.START
         try {
             m.addView(v, lp)
         } catch (e: Exception) {
             return
         }
         wm = m
         view = v
         val a = ValueAnimator.ofFloat(0f, 1f)
         a.duration = 2800
         a.addUpdateListener { v.p = it.animatedValue as Float }
         a.addListener(object : AnimatorListenerAdapter() {
             override fun onAnimationEnd(animation: Animator) {
                 clear()
             }
         })
         anim = a
         a.start()
     }
 }
