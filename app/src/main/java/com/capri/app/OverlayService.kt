 package com.capri.app

 import android.animation.ValueAnimator
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
 import android.webkit.JavascriptInterface
 import android.webkit.WebView

 class OverlayService : Service() {
     private val LARGURA = 180
     private val ALTURA = 310
     private var web: WebView? = null
     private var capriLp: WindowManager.LayoutParams? = null

     class Bridge(
         private val wm: WindowManager,
         private val lp: WindowManager.LayoutParams,
         private val v: View,
         private val d: Float,
         private val sw: Int
     ) {
         private var savedX = 0
         private var savedY = 0
         private var big = false

         @Volatile
         var asleep = false

         @JavascriptInterface
         fun zzz(value: Boolean) {
             asleep = value
         }

         @JavascriptInterface
         fun size(wdp: Int, hdp: Int, isBig: Boolean) {
             v.post {
                 if (isBig && !big) {
                     savedX = lp.x
                     savedY = lp.y
                     big = true
                 }
                 lp.width = (wdp * d).toInt()
                 lp.height = (hdp * d).toInt()
                 if (isBig) {
                     lp.x = Math.max(0, (sw - lp.width) / 2)
                 } else if (big) {
                     lp.x = savedX
                     lp.y = savedY
                     big = false
                 }
                 wm.updateViewLayout(v, lp)
             }
         }
     }

     override fun onBind(intent: Intent?): IBinder? = null

     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
         if (intent?.action == "STOP") {
             stopSelf()
             return START_NOT_STICKY
         }
         if (intent?.action == "FACE") {
             val face = intent.getStringExtra("face") ?: "calm"
             val talk = intent.getIntExtra("talk", 4000)
             val text = intent.getStringExtra("text") ?: ""
             val l = capriLp
             if (text.isNotEmpty() && l != null) {
                 Bubble.show(applicationContext, text, l)
             }
             web?.post {
                 web?.evaluateJavascript(
                     "setFace('" + face + "');setTimeout(function(){talk(" + talk + ",'" + face + "')},900)",
                     null
                 )
             }
             return START_STICKY
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

     private fun slideTo(
         wm: WindowManager,
         lp: WindowManager.LayoutParams,
         v: View,
         target: Int
     ) {
         val a = ValueAnimator.ofInt(lp.x, target)
         a.duration = 250
         a.addUpdateListener {
             lp.x = it.animatedValue as Int
             wm.updateViewLayout(v, lp)
         }
         a.start()
     }

     private fun showCapri() {
         val wm = getSystemService(WINDOW_SERVICE) as WindowManager
         val d = resources.displayMetrics.density
         val sw = resources.displayMetrics.widthPixels
         val lp = WindowManager.LayoutParams(
             (LARGURA * d).toInt(), (ALTURA * d).toInt(),
             WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                 WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
             PixelFormat.TRANSLUCENT
         )
         lp.gravity = Gravity.TOP or Gravity.START
         lp.x = 0
         lp.y = (200 * d).toInt()
         capriLp = lp

         val w = WebView(this)
         w.setBackgroundColor(Color.TRANSPARENT)
         w.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
         w.settings.javaScriptEnabled = true
         val bridge = Bridge(wm, lp, w, d, sw)
         w.addJavascriptInterface(bridge, "Native")

         var sx = 0
         var sy = 0
         var tx = 0f
         var ty = 0f
         var moved = false
         var peeking = false
         var peekSide = -1
         var wasAsleep = false
         w.setOnTouchListener { _, e ->
             when (e.action) {
                 MotionEvent.ACTION_DOWN -> {
                     sx = lp.x
                     sy = lp.y
                     tx = e.rawX
                     ty = e.rawY
                     moved = false
                     wasAsleep = bridge.asleep
                     w.evaluateJavascript("wake()", null)
                 }
                 MotionEvent.ACTION_MOVE -> {
                     val dx = (e.rawX - tx).toInt()
                     val dy = (e.rawY - ty).toInt()
                     if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                     if (moved) {
                         if (peeking) {
                             peeking = false
                             w.evaluateJavascript("unpeek()", null)
                         }
                         lp.x = sx + dx
                         lp.y = sy + dy
                         wm.updateViewLayout(w, lp)
                     }
                 }
                 MotionEvent.ACTION_UP -> {
                     if (!moved) {
                         if (wasAsleep) {
                             wasAsleep = false
                         } else if (peeking) {
                             val target = if (peekSide < 0) 0 else sw - lp.width
                             slideTo(wm, lp, w, target)
                             peeking = false
                             w.evaluateJavascript("unpeek()", null)
                         } else {
                             Bubble.hide()
                             startActivity(
                                 Intent(this, ChatActivity::class.java)
                                     .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                             )
                         }
                     } else {
                         val edge = (40 * d).toInt()
                         if (lp.x <= edge) {
                             peeking = true
                             peekSide = -1
                             slideTo(wm, lp, w, -(lp.width * 0.45).toInt())
                             w.evaluateJavascript("peek('L')", null)
                         } else if (lp.x + lp.width >= sw - edge) {
                             peeking = true
                             peekSide = 1
                             slideTo(wm, lp, w, sw - (lp.width * 0.55).toInt())
                             w.evaluateJavascript("peek('R')", null)
                         }
                     }
                 }
             }
             true
         }

         val base = "file:///android_asset/capri/"
         val html = """
 <body style="margin:0;background:transparent;overflow:hidden;height:100vh">
 <div id="b" style="position:absolute;left:0;top:0;width:100%;height:100%;transform-origin:50% 100%">
 <img src="capri-cutout.png" style="width:100%;height:100%;object-fit:contain">
 </div>
 <img id="r" src="face-calm.png" style="position:absolute;right:6px;bottom:10px;width:62px;height:62px;object-fit:cover;border-radius:50%;box-shadow:0 2px 10px rgba(0,0,0,.6);transition:opacity .25s">
 <div id="zz"><span>z</span><span>Z</span><span>Z</span></div>
 <img id="cu" src="intro-grab.webp" style="display:none;position:absolute;left:0;top:0;width:100%;height:100%;object-fit:contain">
 <style>
 #b{animation:pop .6s ease-out 1,idle 4s ease-in-out .6s infinite}
 @keyframes pop{0%{transform:translateY(60px) scale(.7);opacity:0}100%{transform:translateY(0) scale(1);opacity:1}}
 @keyframes idle{0%,100%{transform:translateY(0) rotate(-1.5deg)}50%{transform:translateY(-12px) rotate(1.5deg)}}
 #b.peekL{animation:peekL 3.5s ease-in-out infinite}
 @keyframes peekL{0%,100%{transform:rotate(3deg)}50%{transform:rotate(8deg) translateY(-6px)}}
 #b.peekR{animation:peekR 3.5s ease-in-out infinite}
 @keyframes peekR{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(-8deg) translateY(-6px)}}
 #b.sleep{animation:snore 4s ease-in-out infinite;filter:brightness(.6) saturate(.7)}
 @keyframes snore{0%,100%{transform:scale(.7,.62) rotate(-6deg)}50%{transform:scale(.72,.65) rotate(-6deg)}}
 #b.jolt{animation:jolt .5s ease-out 1}
 @keyframes jolt{0%{transform:scale(.8) rotate(-6deg)}40%{transform:translateY(-24px) scale(1.05)}1p00%{transform:scale(1)}}
 #zz{display:none;position:absolute;left:58%;top:38%;font:bold 22px monospace;color:#c9b8ff;pointer-events:none}
 #zz span{position:absolute;opacity:0;animation:zz 3s ease-in infinite}
 #zz span:nth-child(2){animation-delay:1s;left:10px}
 #zz span:nth-child(3){animation-delay:2s;left:22px}
 @keyframes zz{0%{transform:translate(0,0) scale(.6);opacity:0}30%{opacity:1}100%{transform:translate(18px,-60px) scale(1.3);opacity:0}}
 #b.laugh{animation:laugh .5s ease-in-out 4}
 @keyframes laugh{0%,100%{transform:translateY(0) rotate(0)}25%{transform:translateY(-16px) rotate(-4deg)}75%{transform:translateY(-16px) rotate(4deg)}}
 #b.jump{animation:jump .7s ease-out 1}
 @keyframes jump{0%{transform:scale(1)}30%{transform:translateY(-34px) scale(1.06,.96)}60%{transform:translateY(0) scale(.96,1.05)}100%{transform:scale(1)}}
 #b.smug{animation:smug 1.8s ease-in-out 1}
 @keyframes smug{0%,100%{transform:rotate(0) translateX(0)}40%,70%{transform:rotate(7deg) translateX(10px)}}
 #b.aside{animation:aside 1.6s ease-in-out 1}
 @keyframes aside{0%,100%{transform:translateX(0) rotate(0)}40%,70%{transform:translateX(-16px) rotate(-5deg)}}
 #b.look{animation:look 1.6s ease-in-out 1}
 @keyframes look{0%,100%{transform:scale(1)}40%,70%{transform:scale(1.08) translateY(-6px)}}
 #b.glitch{animation:glitch .5s steps(2) 3}
 @keyframes glitch{0%{transform:translateX(0);filter:none}25%{transform:translateX(-8px);filter:hue-rotate(90deg)}50%{transform:translateX(8px) skewX(4deg);filter:hue-rotate(200deg)}75%{transform:translateX(-4px);filter:hue-rotate(40deg)}100%{transform:translateX(0);filter:none}}
 </style>
 <script>
 var b=document.getElementById('b');
 var cu=document.getElementById('cu');
 var zz=document.getElementById('zz');
 var busy=false;
 var peeking=false;
 var peekCls='';
 var asleep=false;
 var sleepMs=60000;
 var sleepT=null;
 var map={laugh:'laugh',wide:'jump',smug:'smug',aside:'aside',look:'look',many:'glitch'};
 ['grab','mouth1','mouth2','mouth3','wave1','wave2','wave3','smear','happy'].forEach(function(n){new Image().src='intro-'+n+'.webp'});
 function arm(){
 clearTimeout(sleepT);
 sleepT=setTimeout(sleep,sleepMs);
 }
 function sleep(){
 if(busy){arm();return}
 asleep=true;
 Native.zzz(true);
 b.className='sleep';
 zz.style.display='block';
 r.style.opacity=0.35;
 }
 function wake(){
 arm();
 if(!asleep){return}
 asleep=false;
 Native.zzz(false);
 zz.style.display='none';
 r.style.opacity=1;
 b.className='jolt';
 setTimeout(function(){b.className=peeking?peekCls:''},500);
 }
 function peek(side){
 peeking=true;
 peekCls=(side==='L')?'peekL':'peekR';
 if(!asleep){b.className=peekCls}
 }
 function unpeek(){
 peeking=false;
 peekCls='';
 if(!asleep){b.className=''}
 }
 function react(n){
 var cls=map[n];
 if(!cls){return}
 if(busy||peeking||asleep){return}
 b.className='';
 void b.offsetWidth;
 b.className=cls;
 setTimeout(function(){if(!peeking&&!asleep){b.className=''}},2400);
 }
 function setFace(n){
 wake();
 r.style.opacity=0;
 setTimeout(function(){r.src='face-'+n+'.png';r.style.opacity=1},250);
 react(n);
 }
 function frame(n){cu.src='intro-'+n+'.webp'}
 function enter(){
 busy=true;
 frame('grab');
 b.style.display='none';
 r.style.display='none';
 cu.style.display='block';
 Native.size(280,158,true);
 }
 function leave(){
 Native.size(180,310,false);
 cu.style.display='none';
 b.style.display='block';
 r.style.display='block';
 busy=false;
 arm();
 }
 function greet(){
 if(busy){return}
 enter();
 var seq=[['smear',250],['wave1',450],['wave2',750],['wave1',1050],['wave2',1350],['wave3',1650],['grab',2300]];
 seq.forEach(function(s){setTimeout(function(){frame(s[0])},s[1])});
 setTimeout(leave,3000);
 }
 function talk(ms,face){
 if(busy){return}
 enter();
 var rest=(face==='laugh')?'happy':'grab';
 var opts=[rest,'mouth1','mouth2','mouth3'];
 var last='';
 var t=setInterval(function(){
 var n;
 do{n=opts[Math.floor(Math.random()*opts.length)]}while(n===last);
 last=n;
 frame(n);
 },140);
 setTimeout(function(){clearInterval(t);frame(rest);setTimeout(leave,350)},ms);
 }
 var pool=['many','wide','smug','aside','look','laugh'];
 setInterval(function(){react(pool[Math.floor(Math.random()*pool.length)])},9000);
 setTimeout(greet,1500);
 arm();
 </script>
 </body>
 """
         w.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
         wm.addView(w, lp)
         web = w
     }

     override fun onDestroy() {
         Bubble.hide()
         web?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
         web = null
         capriLp = null
         super.onDestroy()
     }
 }
