 package com.capri.app

 import android.Manifest
 import android.app.Notification
 import android.app.NotificationChannel
 import android.app.NotificationManager
 import android.app.PendingIntent
 import android.app.Service
 import android.content.Context
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.content.pm.ServiceInfo
 import android.os.Build
 import android.os.Bundle
 import android.os.Handler
 import android.os.IBinder
 import android.os.Looper
 import android.speech.RecognitionListener
 import android.speech.RecognizerIntent
 import android.speech.SpeechRecognizer
 import android.widget.Toast
 import java.io.File
 import java.util.concurrent.CountDownLatch
 import java.util.concurrent.TimeUnit

 class ConvService : Service() {
     private val main = Handler(Looper.getMainLooper())
     private var rec: SpeechRecognizer? = null
     private var silent = 0
     private var errors = 0
     private var active = false

     @Volatile
     private var endAfter = false

     private val faces = listOf("calm", "laugh", "smug", "aside", "look", "many", "wide")
     private val byes = listOf(
         "tchau", "encerrar conversa", "encerra a conversa", "fim da conversa",
         "chega de conversar", "para de conversar", "parar conversa"
     )
     private val mStops = listOf(
         "para", "parar", "pare", "para a música", "para a musica",
         "parar a música", "parar a musica", "desliga a música", "desliga a musica"
     )
     private val mPauses = listOf("pausa", "pausar", "pausa a música", "pausa a musica")
     private val mResumes = listOf(
         "continua", "continuar", "continua a música", "continua a musica", "retoma"
     )
     private val mPlays = listOf(
         "toca ", "tocar ", "toque ", "põe ", "poe ", "coloca ", "colocar ", "reproduz "
     )
     private val onlyWindow = listOf(
         "abre ", "abrir ", "abra ", "alarme", "timer", "temporizador", "me acorda",
         "põe um alarme", "poe um alarme", "coloca um alarme", "tira uma foto", "olha essa foto"
     )

     companion object {
         @Volatile
         var running = false

         fun start(ctx: Context) {
             val i = Intent(ctx, ConvService::class.java)
             if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
         }
     }

     override fun onBind(intent: Intent?): IBinder? = null

     private fun note(text: String) {
         try {
             File(filesDir, "crash.txt").writeText(java.util.Date().toString() + "\nConv: " + text)
         } catch (e: Exception) {
         }
     }

      private fun buildNotification(info: String = "Fale normalmente. Diga tchau para encerrar."): Notification {
         val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
         nm.createNotificationChannel(
             NotificationChannel("capri_conv", "Capri conversando", NotificationManager.IMPORTANCE_LOW)
         )
         val stop = PendingIntent.getService(
             this, 5,
             Intent(this, ConvService::class.java).setAction("STOP_CONV"),
             PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
         )
         return Notification.Builder(this, "capri_conv")
             .setContentTitle("Capri conversando")
             .setContentText(info)
             .setSmallIcon(android.R.drawable.ic_btn_speak_now)
             .addAction(Notification.Action.Builder(0, "Encerrar", stop).build())
             .build()
     }

     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
         if (intent?.action == "STOP_CONV") {
             finish("Conversa encerrada.")
             return START_NOT_STICKY
         }
         val n = buildNotification()
         try {
             if (Build.VERSION.SDK_INT >= 29) {
                 startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
             } else {
                 startForeground(3, n)
             }
         } catch (e: Exception) {
             note("startForeground: " + e.message)
             stopSelf()
             return START_NOT_STICKY
         }
         if (active) return START_NOT_STICKY
         if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
             finish("Sem permissão de microfone.")
             return START_NOT_STICKY
         }
         if (!SpeechRecognizer.isRecognitionAvailable(this)) {
             finish("Este celular não tem reconhecimento de voz disponível.")
             return START_NOT_STICKY
         }
         active = true
         running = true
         silent = 0
         errors = 0
         endAfter = false
         WakeService.pause()
         Speaker.say(this, "Pode falar.") { main.post { listen() } }
         return START_NOT_STICKY
     }

     private fun listen() {             
      private fun status(text: String, color: String, notif: Boolean = true) {        
       status("", "", false)  
       status("OUVINDO", "#ff2d6f")
         try {
             if (Speaker.capriOn(this)) {
                 startService(
                     Intent(this, OverlayService::class.java)
                         .setAction("STATUS")
                         .putExtra("text", text)
                         .putExtra("color", color)
                 )
             }
         } catch (e: Exception) {
         }
         if (notif) {
             try {
                 val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                 nm.notify(
                     3,
                     buildNotification(if (text.isEmpty()) "Fale normalmente. Diga tchau para encerrar." else text)
                 )
             } catch (e: Exception) {
             }
         }
     }
         if (!active) return
         try {
             rec?.destroy()
         } catch (e: Exception) {
         }
         val r = SpeechRecognizer.createSpeechRecognizer(this)
         rec = r
         r.setRecognitionListener(object : RecognitionListener {
             override fun onReadyForSpeech(params: Bundle?) {}

             override fun onBeginningOfSpeech() {}

             override fun onRmsChanged(rmsdB: Float) {}

             override fun onBufferReceived(buffer: ByteArray?) {}

             override fun onEndOfSpeech() {}

             override fun onPartialResults(partialResults: Bundle?) {}

             override fun onEvent(eventType: Int, params: Bundle?) {}

             override fun onError(error: Int) {
                 recError(error)
             }

             override fun onResults(results: Bundle?) {
                 val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                 if (said.isNullOrBlank()) recError(SpeechRecognizer.ERROR_NO_MATCH) else heard(said)
             }
         })
         val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
         i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
         i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
         i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
         i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
         i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
         try {
             r.startListening(i)
         } catch (e: Exception) {
             note("startListening: " + e.message)
             finish("Não consegui ouvir: " + e.message)
         }
     }

     private fun recError(code: Int) {
         if (!active) return
         if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
             silent++
             if (silent >= 3) {
                 finish("Encerrei a conversa por silêncio.")
                 return
             }
             main.postDelayed({ listen() }, 400)
             return
         }
         if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
             note("erro 9: o Android não deixou ouvir em segundo plano")
             finish("O Android não deixou ouvir em segundo plano (erro 9).")
             return
         }
         errors++
         note("erro de reconhecimento " + code)
         if (errors >= 4) {
             finish("Parei de ouvir (erro " + code + ").")
             return
         }
         main.postDelayed({ listen() }, 900)
     }

     private fun heard(said: String) {
         silent = 0
         errors = 0
         val low = said.lowercase().trim().trimEnd('.', '!', '?')
         if (byes.any { low.contains(it) }) {
             finish("Até mais!", true)
             return
         }
         endAfter = false
         Thread {        
          status("PENSANDO", "#7b3ff2")
             val answer = reply(said, low)
             main.post { speak(answer) }
         }.start()
     }

     private fun waitMusic(block: ((String) -> Unit) -> Unit): String {
         val latch = CountDownLatch(1)
         var msg = "Não consegui tocar."
         block { m ->
             msg = m
             latch.countDown()
         }
         latch.await(10, TimeUnit.SECONDS)
         return msg
     }

     private fun music(low: String): String? {
         if (MusicPlayer.isListCommand(low)) {
             endAfter = true
             return waitMusic { done -> MusicPlayer.command(applicationContext, low, done) }
         }
         if (low in mStops) {
             MusicPlayer.stop()
             return "Música parada."
         }
         if (low in mPauses) {
             MusicPlayer.pause()
             return "Música pausada."
         }
         if (low in mResumes) {
             MusicPlayer.resume()
             return "Continuando a música."
         }
         val s = mPlays.firstOrNull { low.startsWith(it) } ?: return null
         endAfter = true
         val wanted = low.removePrefix(s).trim()
         return waitMusic { done -> MusicPlayer.play(applicationContext, wanted, done) }
     }

     private fun reply(said: String, low: String): String {
         val kb = Notes.handle(this, said, low)
         if (kb != null) return kb
         val rem = Reminders.handle(this, low)
         if (rem != null) return rem
         val gm = GoodMorning.handle(this, low)
         if (gm != null) return gm
         if (low.startsWith("teleporta") || low.startsWith("teleporte")) {
             startService(Intent(this, OverlayService::class.java).setAction("TELEPORT"))
             return "Zzzt!"
         }
         val beamVerbs = listOf("lan", "dispar", "atir", "solt", "mand", "apont")
         if (low.startsWith("raio") || (low.contains("raio") && beamVerbs.any { low.startsWith(it) })) {
             startService(
                 Intent(this, OverlayService::class.java).setAction("BEAM").putExtra("where", low)
             )
             return "Pow!"
         }
         val mu = music(low)
         if (mu != null) return mu
         if (onlyWindow.any { low.startsWith(it) }) {
             return "Isso eu só faço na janelinha. Toque em mim e peça lá."
         }
         return try {
             Brain.ask(this, said)
         } catch (e: Throwable) {
             "[wide] Deu erro: " + e.message
         }
     }

     private fun speak(answer: String) {
         if (!active) return
         val m = Regex("^\\[(\\w+)\\]\\s*").find(answer)
         val tag = m?.groupValues?.get(1) ?: "calm"
         val face = if (tag in faces) tag else "calm"
         val text = answer.replace(Regex("^\\[\\w+\\]\\s*"), "").trim()
         Speaker.toCapri(this, face, text)      
         status("FALANDO", "#1fae6b")
         val end = endAfter
         Speaker.say(this, text) {
             main.post {
                 if (active) {
                     if (end) finish("") else listen()
                 }
             }
         }
     }

     private fun finish(msg: String, speakIt: Boolean = false) {
         val was = active
         active = false
         running = false
         main.removeCallbacksAndMessages(null)
         try {
             rec?.destroy()
         } catch (e: Exception) {
         }
         rec = null
         if (msg.isNotEmpty()) {
             if (speakIt) {
                 Speaker.say(this, msg)
             } else {
                 Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
             }
         }
         if (was) WakeService.resume()
         stopSelf()
     }

     override fun onDestroy() {
         val was = active
         active = false
         running = false
         main.removeCallbacksAndMessages(null)
         try {
             rec?.destroy()
         } catch (e: Exception) {
         }
         rec = null
         if (was) WakeService.resume()
         super.onDestroy()
     }
 }
