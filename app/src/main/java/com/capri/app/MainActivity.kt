package com.capri.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(48, 48, 48, 48)
        box.setBackgroundColor(Color.parseColor("#1a1625"))

        val title = TextView(this)
        title.text = "Capri"
        title.textSize = 32f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        box.addView(title)

        val info = TextView(this)
        info.text = "Primeiro permita \"exibir sobre outros apps\", depois ligue a Capri."
        info.setTextColor(Color.LTGRAY)
        info.gravity = Gravity.CENTER
        info.setPadding(0, 24, 0, 48)
        box.addView(info)

        val perm = Button(this)
        perm.text = "1. Permitir sobre outros apps"
        perm.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        box.addView(perm)

        val start = Button(this)
        start.text = "2. Ligar a Capri"
        start.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
                finish()
            } else {
                info.text = "Falta a permissão do passo 1."
            }
        }
        box.addView(start)

        val stop = Button(this)
        stop.text = "Desligar a Capri"
        stop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
        box.addView(stop)

        setContentView(box)        
        val crash = java.io.File(filesDir, "crash.txt")
        if (crash.exists()) {
            val t = TextView(this)
            t.text = crash.readText().take(1200)
            t.setTextColor(Color.YELLOW)
            t.textSize = 9f
            box.addView(t)
        }
    }
}
