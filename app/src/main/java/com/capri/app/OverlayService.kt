package com.capri.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView

class OverlayService : Service() {
    private var web: WebView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, buildNotification())
        if (web == null) showCapri()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "capri")
            .setContentTitle("Capri está ativa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(Notification.Action.Builder(0, "Fechar", stop).build())
            .build()
    }

    private fun showCapri() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            (170 * d).toInt(), (300 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = (250 * d).toInt()

        val w = WebView(this)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        w.settings.javaScriptEnabled = true

        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        w.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x
                    sy = lp.y
                    tx = e.rawX
                    ty = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = sx + dx
                        lp.y = sy + dy
                        wm.updateViewLayout(w, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) w.evaluateJavascript("t()", null)
                }
            }
            true
        }

        val base = "https://raw.githubusercontent.com/mochiiwuff/capri-desktop/main/assets/capri/"
        val html = """
<body style="margin:0;background:transparent;overflow:hidden;height:100vh">
<img src="capri-cutout.png" style="position:absolute;left:0;top:0;width:100%;height:100%;object-fit:contain;animation:f 3s infinite alternate ease-in-out">
<img id="r" src="face-calm.png" style="position:absolute;right:4px;bottom:4px;width:56px;height:56px;object-fit:cover;border:2px solid #7b3ff2;border-radius:8px;transition:opacity .25s">
<style>@keyframes f{to{transform:translateY(-12px)}}</style>
<script>
var f=["calm","laugh","smug","aside","look","many","wide"],i=0;
function t(){i=(i+1)%7;r.style.opacity=0;setTimeout(function(){r.src="face-"+f[i]+".png";r.style.opacity=1},250)}
setInterval(t,4000);
</script>
</body>
"""
        w.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
        wm.addView(w, lp)
        web = w
    }

    override fun onDestroy() {
        web?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        web = null
        super.onDestroy()
    }
}
