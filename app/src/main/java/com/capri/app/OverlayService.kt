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
     private val LARGURA = 220
     private val ALTURA = 380
     private var web: WebView? = null

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

         val w = WebView(this)
         w.setBackgroundColor(Color.TRANSPARENT)
         w.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
         w.settings.javaScriptEnabled = true
         w.addJavascriptInterface(Bridge(wm, lp, w, d, sw), "Native")

         var sx = 0
         var sy = 0
         var tx = 0f
         var ty = 0f
         var moved = false
         var peeking = false
         var peekSide = -1
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
                         if (peeking) {
                             val target = if (peekSide < 0) 0 else sw - lp.width
                             slideTo(wm, lp, w, target)
                             peeking = false
                             w.evaluateJavascript("unpeek()", null)
                         } else {
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
 <img id="r" src="face-calm.png" style="position:absolute;right:6px;bottom:10px;width:76px;height:76px;object-fit:cover;border-radius:50%;box-shadow:0 2px 10px rgba(0,0,0,.6);transition:opacity .25s">
 <img id="cu" src="intro-grab.webp" style="display:none;position:absolute;left:0;top:0;width:100%;height:100%;object-fit:contain">
 <style>
 #b{animation:pop .6s ease-out 1,idle 4s ease-in-out .6s infinite}
 @keyframes pop{0%{transform:translateY(60px) scale(.7);opacity:0}100%{transform:translateY(0) scale(1);opacity:1}}
 @keyframes idle{0%,100%{transform:translateY(0) rotate(-1.5deg)}50%{transform:translateY(-12px) rotate(1.5deg)}}
 #b.peekL{animation:peekL 3.5s ease-in-out infinite}
 @keyframes peekL{0%,100%{transform:rotate(3deg)}50%{transform:rotate(8deg) translateY(-6px)}}
 #b.peekR{animation:peekR 3.5s ease-in-out infinite}
 @keyframes peekR{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(-8deg) translateY(-6px)}}
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
 var busy=false;
 var peeking=false;
 var map={laugh:'laugh',wide:'jump',smug:'smug',aside:'aside',look:'look',many:'glitch'};
 ['grab','mouth1','mouth2','mouth3','wave1','wave2','wave3','smear','happy'].forEach(function(n){new Image().src='intro-'+n+'.webp'});
 function peek(side){
 peeking=true;
 b.className=(side==='L')?'peekL':'peekR';
 }
 function unpeek(){
 peeking=false;
 b.className='';
 }
 function react(n){
 var cls=map[n];
 if(!cls){return}
 if(busy||peeking){return}
 b.className='';
 void b.offsetWidth;
 b.className=cls;
 setTimeout(function(){if(!peeking){b.className=''}},2400);
 }
 function setFace(n){
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
 Native.size(340,191,true);
 }
 function leave(){
 Native.size(220,380,false);
 cu.style.display='none';
 b.style.display='block';
 r.style.display='block';
 busy=false;
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
