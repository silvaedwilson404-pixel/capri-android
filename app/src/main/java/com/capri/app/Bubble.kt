 package com.capri.app

 import android.content.Context
 import android.graphics.Color
 import android.graphics.PixelFormat
 import android.graphics.Typeface
 import android.graphics.drawable.GradientDrawable
 import android.os.Handler
 import android.os.Looper
 import android.view.Gravity
 import android.view.View
 import android.view.WindowManager
 import android.widget.LinearLayout
 import android.widget.TextView

 object Bubble {
     private var view: View? = null
     private var manager: WindowManager? = null
     private val handler = Handler(Looper.getMainLooper())
     private val hider = Runnable { hide() }

     fun hide() {
         handler.removeCallbacks(hider)
         try {
             val v = view
             if (v != null) manager?.removeView(v)
         } catch (e: Exception) {
         }
         view = null
     }

     fun show(ctx: Context, text: String, capri: WindowManager.LayoutParams) {
         hide()
         val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
         val d = ctx.resources.displayMetrics.density
         val sw = ctx.resources.displayMetrics.widthPixels
         val sh = ctx.resources.displayMetrics.heightPixels
         val margin = (8 * d).toInt()
         val pad = (12 * d).toInt()
         val bw = Math.min(sw - 2 * margin, (260 * d).toInt())

         val box = LinearLayout(ctx)
         box.orientation = LinearLayout.VERTICAL
         box.setPadding(pad, pad, pad, pad)
         val bg = GradientDrawable()
         bg.setColor(Color.parseColor("#0f0b1a"))
         bg.setStroke((3 * d).toInt(), Color.parseColor("#7b3ff2"))
         bg.cornerRadius = 14 * d
         box.background = bg

         val tag = TextView(ctx)
         tag.text = "CAPRI"
         tag.setTextColor(Color.parseColor("#c9b8ff"))
         tag.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
         tag.textSize = 11f
         box.addView(tag)

         val body = TextView(ctx)
         body.text = text
         body.setTextColor(Color.WHITE)
         body.typeface = Typeface.MONOSPACE
         body.textSize = 14f
         body.setPadding(0, (4 * d).toInt(), 0, 0)
         box.addView(body)

         box.measure(
             View.MeasureSpec.makeMeasureSpec(bw, View.MeasureSpec.EXACTLY),
             View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
         )
         val bh = box.measuredHeight

         var x = capri.x + capri.width / 2 - bw / 2
         x = Math.max(margin, Math.min(x, sw - bw - margin))
         var y = capri.y - bh - margin
         if (y < margin) y = capri.y + capri.height + margin
         y = Math.max(margin, Math.min(y, sh - bh - margin))

         val lp = WindowManager.LayoutParams(
             bw,
             WindowManager.LayoutParams.WRAP_CONTENT,
             WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                 WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
             PixelFormat.TRANSLUCENT
         )
         lp.gravity = Gravity.TOP or Gravity.START
         lp.x = x
         lp.y = y

         box.setOnClickListener { hide() }
         try {
             wm.addView(box, lp)
             manager = wm
             view = box
             val ms = Math.min(20000, 4000 + text.length * 60).toLong()
             handler.postDelayed(hider, ms)
         } catch (e: Exception) {
         }
     }
 }
