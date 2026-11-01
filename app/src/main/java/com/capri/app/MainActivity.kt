package com.capri.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w = WebView(this)
        w.setBackgroundColor(Color.parseColor("#1a1625"))
        w.settings.javaScriptEnabled = true
        setContentView(w)
        val base = "https://raw.githubusercontent.com/mochiiwuff/capri-desktop/main/assets/capri/"
        val html = """
<body style="background:#1a1625;margin:0;height:100vh;overflow:hidden;display:flex;justify-content:center;align-items:flex-end">
<img src="capri-cutout.png" style="height:88vh;max-width:100%;object-fit:contain;animation:f 3s infinite alternate ease-in-out">
<div style="position:absolute;bottom:3vh;left:3vw;display:flex;gap:12px;align-items:center;background:#0f0b1a;border:3px solid #7b3ff2;border-radius:10px;padding:10px;color:#fff;font:bold 18px monospace">
<img id="r" src="face-calm.png" style="height:112px;width:112px;object-fit:cover;transition:opacity .25s">
<span>CAPRI<br>toque em mim</span></div>
<style>@keyframes f{to{transform:translateY(-3vh)}}</style>
<script>
var f=["calm","laugh","smug","aside","look","many","wide"],i=0;
function t(){i=(i+1)%7;r.style.opacity=0;setTimeout(function(){r.src="face-"+f[i]+".png";r.style.opacity=1},250)}
document.body.onclick=t;setInterval(t,4000);
</script>
</body>
"""
        w.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
    }
}
