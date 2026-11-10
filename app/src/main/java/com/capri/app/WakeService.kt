 package com.capri.app

 import android.app.Notification
 import android.app.NotificationChannel
 import android.app.NotificationManager
 import android.app.PendingIntent
 import android.app.Service
 import android.content.Context
 import android.content.Intent
 import android.content.pm.ServiceInfo
 import android.os.Build
 import android.os.Handler
 import android.os.IBinder
 import android.os.Looper
 import android.widget.Toast
 import org.json.JSONObject
 import org.vosk.Model
 import org.vosk.Recognizer
 import org.vosk.android.RecognitionListener
 import org.vosk.android.SpeechService
 import java.io.File
 import java.net.HttpURLConnection
 import java.net.URL
 import java.util.zip.ZipInputStream

 class WakeService : Service() {
     private var model: Model? = null
     private var speech: SpeechService? = null
     private var recognizer: Recognizer? = null
     private var listening = false
     private var started = false
     private var failMsg = ""
     private var lastCode = 0
     private val main = Handler(Looper.getMainLooper())
     private val resumeRun = Runnable { resumeNow() }
     private val trigger = Regex("\\b(capri|cápri|kapri|capre|cabri|capry)\\b")
     private val urls = listOf(
         "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
         "https://huggingface.co/rhasspy/vosk-models/resolve/main/pt/vosk-model-small-pt-0.3.zip"
     )

     private val listener = object : RecognitionListener {
         override fun onPartialResult(hypothesis: String?) {
             heard(hypothesis)
         }

         override fun onResult(hypothesis: String?) {
             heard(hypothesis)
         }

         override fun onFinalResult(hypothesis: String?) {}

         override fun onError(exception: Exception?) {
             say("Erro na escuta: " + exception?.message)
         }

         override fun onTimeout() {}
     }

     companion object {
         private var instance: WakeService? = null

         fun resume() {
             val s = instance ?: return
             s.main.post { s.resumeNow() }
         }
     }

     override fun onBind(intent: Intent?): IBinder? = null

     override fun onCreate() {
         super.onCreate()
         instance = this
     }

     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
         if (intent?.action == "STOP_WAKE") {
             getSharedPreferences("capri", Context.MODE_PRIVATE)
                 .edit().putBoolean("wake_on", false).apply()
             stopSelf()
             return START_NOT_STICKY
         }
         val n = buildNotification()
         if (Build.VERSION.SDK_INT >= 29) {
             startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
         } else {
             startForeground(2, n)
         }
         if (!started) {
             started = true
             loadModel()
         } else {
             resumeNow()
         }
         return START_NOT_STICKY
     }

     private fun say(text: String) {
         main.post {
             Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
         }
     }

     private fun note(text: String) {
         try {
             File(filesDir, "crash.txt").writeText(java.util.Date().toString() + "\nWake: " + text)
         } catch (e: Exception) {
         }
     }

     private fun buildNotification(): Notification {
         val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
         nm.createNotificationChannel(
             NotificationChannel("capri_wake", "Capri ouvindo", NotificationManager.IMPORTANCE_LOW)
         )
         val stop = PendingIntent.getService(
             this, 2,
             Intent(this, WakeService::class.java).setAction("STOP_WAKE"),
             PendingIntent.FLAG_IMMUTABLE
         )
         return Notification.Builder(this, "capri_wake")
             .setContentTitle("Capri está ouvindo")
             .setContentText("Diga Capri para me chamar")
             .setSmallIcon(android.R.drawable.ic_btn_speak_now)
             .addAction(Notification.Action.Builder(0, "Parar", stop).build())
             .build()
     }

     private fun open(url: String): HttpURLConnection? {
         var u = url
         for (i in 0 until 6) {
             val c = URL(u).openConnection() as HttpURLConnection
             c.connectTimeout = 20000
             c.readTimeout = 60000
             c.instanceFollowRedirects = false
             c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) CapriApp/1.3")
             val code = c.responseCode
             if (code in 300..399) {
                 val loc = c.getHeaderField("Location")
                 c.disconnect()
                 if (loc == null) return null
                 u = URL(URL(u), loc).toString()
                 continue
             }
             lastCode = code
             return if (code in 200..299) c else null
         }
         return null
     }

     private fun unzip(c: HttpURLConnection, root: File) {
         val rootPath = root.canonicalPath
         ZipInputStream(c.inputStream.buffered()).use { zin ->
             var entry = zin.nextEntry
             while (entry != null) {
                 val dest = File(root, entry.name)
                 if (!dest.canonicalPath.startsWith(rootPath)) {
                     throw SecurityException("arquivo inválido no zip")
                 }
                 if (entry.isDirectory) {
                     dest.mkdirs()
                 } else {
                     dest.parentFile?.mkdirs()
                     dest.outputStream().use { zin.copyTo(it) }
                 }
                 zin.closeEntry()
                 entry = zin.nextEntry
             }
         }
     }

     private fun isModel(d: File): Boolean {
          return d.isDirectory && (File(d, "am").isDirectory || File(d, "conf").isDirectory || File(d, "final.mdl").exists())
     }

     private fun findModel(dir: File, depth: Int = 0): File? {
         if (isModel(dir)) return dir
         if (depth >= 3) return null
         val kids = dir.listFiles() ?: return null
         for (k in kids) {
             if (k.isDirectory) {
                 val f = findModel(k, depth + 1)
                 if (f != null) return f
             }
         }
         return null
     }

     private fun describe(root: File): String {
         val sb = StringBuilder()
         root.listFiles()?.take(8)?.forEach { f ->
             sb.append(f.name)
             if (f.isDirectory) {
                 sb.append("/[")
                 sb.append(f.list()?.take(8)?.joinToString(",") ?: "")
                 sb.append("]")
             }
             sb.append("; ")
         }
         return sb.toString()
     }

     private fun ensureModel(): File? {
         val root = File(filesDir, "vosk")
         val ok = File(root, "model.ok")
         if (ok.exists()) {
             val found = findModel(root)
             if (found != null) return found
         }
         say("Baixando o modelo de voz (uns 31 MB)...")
         for (url in urls) {
             try {
                 root.deleteRecursively()
                 root.mkdirs()
                 val c = open(url)
                 if (c == null) {
                     failMsg = "HTTP " + lastCode + " em " + url
                     continue
                 }
                 unzip(c, root)
                 val found = findModel(root)
                 if (found != null) {
                     ok.writeText("ok")
                     return found
                 }
                 failMsg = "zip sem modelo. Conteúdo: " + describe(root)
             } catch (e: Exception) {
                 failMsg = e.javaClass.simpleName + " " + e.message + " em " + url
             }
         }
         return null
     }

     private fun loadModel() {
         Thread {
             try {
                 val dir = ensureModel()
                 if (dir == null) {
                     note(failMsg)
                     say("Não consegui preparar o modelo de voz. Abra o app Capri pelo ícone e veja o texto amarelo.")
                     main.post { stopSelf() }
                     return@Thread
                 }
                 model = Model(dir.absolutePath)
                 say("Modelo pronto. Pode dizer Capri.")
                 main.post { resumeNow() }
             } catch (e: Throwable) {
                 note("Erro ao carregar o modelo: " + e.javaClass.simpleName + " " + e.message)
                 say("Erro ao carregar o modelo. Abra o app Capri pelo ícone e veja o texto amarelo.")
                 main.post { stopSelf() }
             }
         }.start()
     }

     private fun resumeNow() {
         val m = model
         if (listening || m == null) return
         try {
             val r = Recognizer(m, 16000.0f)
             val s = SpeechService(r, 16000.0f)
             s.startListening(listener)
             recognizer = r
             speech = s
             listening = true
         } catch (e: Throwable) {
             say("Não consegui ouvir: " + e.message)
         }
     }

     private fun pauseNow() {
         try {
             speech?.stop()
         } catch (e: Throwable) {
         }
         try {
             speech?.shutdown()
         } catch (e: Throwable) {
         }
         speech = null
         try {
             recognizer?.close()
         } catch (e: Throwable) {
         }
         recognizer = null
         listening = false
     }

     private fun heard(h: String?) {
         if (h == null) return
         val text = try {
             val o = JSONObject(h)
             o.optString("partial", o.optString("text", ""))
         } catch (e: Exception) {
             ""
         }
         if (trigger.containsMatchIn(text.lowercase())) onWake()
     }

     private fun onWake() {
         main.post {
             if (!listening) return@post
             pauseNow()
             try {
                 startActivity(
                     Intent(this, ChatActivity::class.java)
                         .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                 )
             } catch (e: Exception) {
             }
             main.removeCallbacks(resumeRun)
             main.postDelayed(resumeRun, 90000)
         }
     }

     override fun onDestroy() {
         main.removeCallbacks(resumeRun)
         pauseNow()
         try {
             model?.close()
         } catch (e: Throwable) {
         }
         model = null
         instance = null
         super.onDestroy()
     }
 }
