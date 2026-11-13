 package com.capri.app

 import android.Manifest
 import android.app.Activity
 import android.content.Context
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.os.Build
 import android.provider.Settings

 object Extras {
     private fun listenerOn(act: Activity): Boolean {
         val s = Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners")
             ?: return false
         return s.contains(act.packageName)
     }

     private fun wake(act: Activity, low: String): String? {
         if (!low.contains("palavra de ativa")) return null
         val prefs = act.getSharedPreferences("capri", Context.MODE_PRIVATE)
         val off = listOf("desliga", "desativa", "para ", "pare ", "cancela")
         if (off.any { low.startsWith(it) }) {
             prefs.edit().putBoolean("wake_on", false).apply()
             act.startService(Intent(act, WakeService::class.java).setAction("STOP_WAKE"))
             return "Palavra de ativação desligada."
         }
         if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
             act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 11)
             return "Permita o microfone e peça de novo."
         }
         prefs.edit().putBoolean("wake_on", true).apply()
         val i = Intent(act, WakeService::class.java)
         if (Build.VERSION.SDK_INT >= 26) act.startForegroundService(i) else act.startService(i)
         return "Ligada. Na primeira vez ela baixa o modelo de voz, com internet. Depois é só dizer Capri."
     }

     fun handle(act: Activity, q: String): String? {
         val low = q.lowercase().trim().trimEnd('.', '!', '?')
         val w = wake(act, low)         
      if (low.contains("o que você ouviu") || low.contains("o que voce ouviu")) {
             val f = java.io.File(act.filesDir, "heard.txt")
             return if (f.exists()) "Ouvi por último:\n" + f.readText().takeLast(600) else "Ainda não ouvi nada."
         }
         if (w != null) return w
         val rem = Reminders.handle(act, low)       
      val photoWords = listOf(
             "olha essa foto", "olha a foto", "olha esta foto", "olha essa imagem",
             "tira uma foto", "tirar uma foto", "lê essa foto", "le essa foto",
             "lê o texto da foto", "lê o texto dessa foto", "le o texto dessa foto",
             "analisa a foto", "analisa essa foto"
         )
         if (act is ChatActivity && photoWords.any { low.startsWith(it) }) {
             act.pickPhoto()
             return "Escolha a foto, ou tire uma agora."
         }
         if (rem != null) return rem
         val gm = GoodMorning.handle(act, low)
         if (gm != null) return gm
         if (!low.contains("notifica")) return null
         val prefs = act.getSharedPreferences("capri", Context.MODE_PRIVATE)
         val off = listOf(
             "para ", "pare ", "parar ", "desliga", "desativa", "chega de",
             "não leia", "nao leia", "silencia"
         )
         if (off.any { low.startsWith(it) }) {
             prefs.edit().putBoolean("notif_read", false).apply()
             return "Pronto, parei de ler as notificações."
         }
         val on = listOf("lê ", "le ", "ler ", "leia ", "liga", "ativa", "começa a ler", "comece a ler")
         if (on.any { low.startsWith(it) }) {
             prefs.edit().putBoolean("notif_read", true).apply()
             if (!listenerOn(act)) {
                 try {
                     act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                 } catch (e: Exception) {
                 }
                 return "Ative a Capri em Acesso às notificações e peça de novo."
             }
             return "Certo, vou ler as notificações que chegarem."
         }
         return null
     }
 }
