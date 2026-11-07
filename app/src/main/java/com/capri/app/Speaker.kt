 package com.capri.app

 import android.app.ActivityManager
 import android.content.Context
 import android.content.Intent
 import android.os.Handler
 import android.os.Looper
 import android.speech.tts.TextToSpeech
 import android.speech.tts.UtteranceProgressListener
 import java.util.Locale
 import java.util.concurrent.ConcurrentHashMap

 object Speaker {
     private var tts: TextToSpeech? = null
     private var ready = false
     private var counter = 0
     private val queue = ArrayList<Pair<String, (() -> Unit)?>>()
     private val callbacks = ConcurrentHashMap<String, () -> Unit>()
     private val main = Handler(Looper.getMainLooper())

     private fun finish(id: String?) {
         if (id == null) return
         val cb = callbacks.remove(id)
         if (cb != null) cb()
     }

     private fun flush() {
         while (queue.isNotEmpty()) {
             val item = queue.removeAt(0)
             counter++
             val id = "s" + counter
             val cb = item.second
             if (cb != null) callbacks[id] = cb
             tts?.speak(item.first, TextToSpeech.QUEUE_ADD, null, id)
         }
     }

     fun say(ctx: Context, text: String, done: (() -> Unit)? = null) {
         val app = ctx.applicationContext
         main.post {
             queue.add(Pair(text, done))
             if (tts == null) {
                 tts = TextToSpeech(app) { status ->
                     main.post {
                         if (status == TextToSpeech.SUCCESS) {
                             tts?.language = Locale("pt", "BR")
                             tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                 override fun onStart(utteranceId: String?) {}

                                 override fun onDone(utteranceId: String?) {
                                     finish(utteranceId)
                                 }

                                 override fun onError(utteranceId: String?) {
                                     finish(utteranceId)
                                 }
                             })
                             ready = true
                             flush()
                         } else {
                             for (item in queue) {
                                 val cb = item.second
                                 if (cb != null) cb()
                             }
                             queue.clear()
                             tts = null
                         }
                     }
                 }
             } else if (ready) {
                 flush()
             }
         }
     }

     fun capriOn(ctx: Context): Boolean {
         return try {
             val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
             am.getRunningServices(100).any {
                 it.service.className == OverlayService::class.java.name
             }
         } catch (e: Exception) {
             false
         }
     }

     fun toCapri(ctx: Context, face: String, text: String) {
         if (!capriOn(ctx)) return
         try {
             ctx.startService(
                 Intent(ctx, OverlayService::class.java)
                     .setAction("FACE")
                     .putExtra("face", face)
                     .putExtra("talk", Math.min(12000, 800 + text.length * 70))
                     .putExtra("text", text)
             )
         } catch (e: Exception) {
         }
     }
 }
