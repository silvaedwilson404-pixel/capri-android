 package com.capri.app

 import android.app.Activity
 import android.content.Context
 import android.content.Intent
 import android.provider.Settings

 object Extras {
     private fun listenerOn(act: Activity): Boolean {
         val s = Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners")
             ?: return false
         return s.contains(act.packageName)
     }

     fun handle(act: Activity, q: String): String? {
         val low = q.lowercase().trim().trimEnd('.', '!', '?')
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
