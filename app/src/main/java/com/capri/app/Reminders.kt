 package com.capri.app

 import android.app.AlarmManager
 import android.app.Notification
 import android.app.NotificationChannel
 import android.app.NotificationManager
 import android.app.PendingIntent
 import android.content.BroadcastReceiver
 import android.content.Context
 import android.content.Intent
 import android.os.Build
 import android.os.Handler
 import android.os.Looper
 import org.json.JSONArray
 import org.json.JSONObject
 import java.text.SimpleDateFormat
 import java.util.Calendar
 import java.util.Date
 import java.util.Locale
 import java.util.concurrent.atomic.AtomicBoolean

 object Reminders {
     private const val ACTION = "com.capri.app.REMIND"
     private val nums = mapOf(
         "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "três" to 3, "tres" to 3,
         "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9,
         "dez" to 10, "quinze" to 15, "vinte" to 20, "trinta" to 30,
         "quarenta" to 40, "cinquenta" to 50
     )
     private val trigger = Regex(
         "^(?:por favor\\s+)?(?:me lembrar|me lembra|me lembre|lembrete|cria um lembrete|" +
             "cria lembrete|agenda um lembrete|marca um lembrete)\\s*"
     )
     private val half = Regex("(?<![\\p{L}\\p{N}])(?:daqui a|dentro de|em)\\s+meia hora")
     private val rel = Regex(
         "(?<![\\p{L}\\p{N}])(?:daqui a|daqui|dentro de|em)\\s+(\\d+|[a-zçê]+)\\s*(minutos?|min|horas?|h)(?![\\p{L}])"
     )
     private val abs = Regex(
         "(?<![\\p{L}\\p{N}])(?:(amanhã|amanha|hoje)\\s+)?(?:às|as|ás)\\s+(\\d{1,2})" +
             "(?:\\s*(?:[:h]|e)\\s*(\\d{1,2}))?\\s*(?:horas?|h)?(?:\\s+da\\s+(manhã|manha|tarde|noite))?"
     )

     private fun prefs(ctx: Context) = ctx.getSharedPreferences("capri_rem", Context.MODE_PRIVATE)

     private fun load(ctx: Context): JSONArray {
         return try {
             JSONArray(prefs(ctx).getString("list", "[]"))
         } catch (e: Exception) {
             JSONArray()
         }
     }

     private fun pending(ctx: Context, id: Int, text: String): PendingIntent {
         val i = Intent(ctx, ReminderReceiver::class.java)
             .setAction(ACTION)
             .putExtra("id", id)
             .putExtra("text", text)
         return PendingIntent.getBroadcast(
             ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
         )
     }

     private fun schedule(ctx: Context, id: Int, t: Long, text: String) {
         val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
         val pi = pending(ctx, id, text)
         try {
             if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                 am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
             } else {
                 am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
             }
         } catch (e: Exception) {
             try {
                 am.set(AlarmManager.RTC_WAKEUP, t, pi)
             } catch (x: Exception) {
             }
         }
     }

     private fun add(ctx: Context, t: Long, text: String) {
         val p = prefs(ctx)
         val id = p.getInt("next", 1)
         val arr = load(ctx)
         arr.put(JSONObject().put("id", id).put("t", t).put("text", text))
         p.edit().putInt("next", id + 1).putString("list", arr.toString()).apply()
         schedule(ctx, id, t, text)
     }

     fun remove(ctx: Context, id: Int) {
         val arr = load(ctx)
         val keep = JSONArray()
         for (i in 0 until arr.length()) {
             val o = arr.getJSONObject(i)
             if (o.getInt("id") != id) keep.put(o)
         }
         prefs(ctx).edit().putString("list", keep.toString()).apply()
     }

     fun describe(ctx: Context): String {
         val arr = load(ctx)
         val now = System.currentTimeMillis()
         val items = ArrayList<Pair<Long, String>>()
         for (i in 0 until arr.length()) {
             val o = arr.getJSONObject(i)
             if (o.getLong("t") > now) items.add(Pair(o.getLong("t"), o.getString("text")))
         }
         if (items.isEmpty()) return "Você não tem lembretes."
         items.sortBy { it.first }
         val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
         return items.joinToString("\n") { fmt.format(Date(it.first)) + " - " + it.second }
     }

     fun clearAll(ctx: Context) {
         val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
         val arr = load(ctx)
         for (i in 0 until arr.length()) {
             val o = arr.getJSONObject(i)
             try {
                 am.cancel(pending(ctx, o.getInt("id"), o.getString("text")))
             } catch (e: Exception) {
             }
         }
         prefs(ctx).edit().putString("list", "[]").apply()
     }

     fun rescheduleAll(ctx: Context) {
         val arr = load(ctx)
         val keep = JSONArray()
         val now = System.currentTimeMillis()
         for (i in 0 until arr.length()) {
             val o = arr.getJSONObject(i)
             if (o.getLong("t") > now) {
                 keep.put(o)
                 schedule(ctx, o.getInt("id"), o.getLong("t"), o.getString("text"))
             }
         }
         prefs(ctx).edit().putString("list", keep.toString()).apply()
     }

     fun handle(ctx: Context, low: String): String? {
         val lists = setOf(
             "lista lembretes", "lista os lembretes", "meus lembretes", "quais são meus lembretes",
             "quais sao meus lembretes", "mostra os lembretes", "mostra lembretes", "ver lembretes"
         )
         if (low in lists) return describe(ctx)
         val clears = setOf(
             "apaga os lembretes", "apaga lembretes", "cancela os lembretes", "cancela lembretes",
             "limpa os lembretes", "limpa lembretes", "apagar lembretes", "cancelar lembretes"
         )
         if (low in clears) {
             clearAll(ctx)
             return "Todos os lembretes foram apagados."
         }
         val m = trigger.find(low) ?: return null
         var rest = low.removePrefix(m.value).trim()
         var whenMs: Long? = null

         val h = half.find(rest)
         if (h != null) {
             whenMs = System.currentTimeMillis() + 30 * 60000L
             rest = rest.removeRange(h.range)
         } else {
             val r = rel.find(rest)
             if (r != null) {
                 val raw = r.groupValues[1]
                 val n = raw.toIntOrNull() ?: nums[raw]
                 if (n != null) {
                     val unit = r.groupValues[2]
                     val mult = if (unit.startsWith("h")) 60L else 1L
                     whenMs = System.currentTimeMillis() + n * mult * 60000L
                     rest = rest.removeRange(r.range)
                 }
             }
         }

         if (whenMs == null) {
             val a = abs.find(rest)
             if (a != null) {
                 var hour = a.groupValues[2].toInt()
                 val min = if (a.groupValues[3].isEmpty()) 0 else a.groupValues[3].toInt()
                 val period = a.groupValues[4]
                 if ((period == "tarde" || period == "noite") && hour < 12) hour += 12
                 if (hour > 23 || min > 59) {
                     return "Não entendi o horário. Fale por exemplo: me lembra às 15:30 de ligar para o João."
                 }
                 val c = Calendar.getInstance()
                 c.set(Calendar.HOUR_OF_DAY, hour)
                 c.set(Calendar.MINUTE, min)
                 c.set(Calendar.SECOND, 0)
                 c.set(Calendar.MILLISECOND, 0)
                 val day = a.groupValues[1]
                 if (day == "amanhã" || day == "amanha") {
                     c.add(Calendar.DAY_OF_MONTH, 1)
                 } else if (c.timeInMillis <= System.currentTimeMillis()) {
                     c.add(Calendar.DAY_OF_MONTH, 1)
                 }
                 whenMs = c.timeInMillis
                 rest = rest.removeRange(a.range)
             }
         }

         val t: Long = whenMs
             ?: return "Não entendi quando. Fale por exemplo: me lembra daqui a 10 minutos de beber água."

         var text = rest.trim().trim(',', '.', '!', '?').trim()
         text = text.replace(Regex("^(?:de|do|da|para|pra|que|sobre)\\s+"), "").trim()
         if (text.isEmpty()) text = "lembrete"
         add(ctx, t, text)
         val fmt = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
         return "Combinado! Lembrete \"" + text + "\" para " + fmt.format(Date(t)) + "."
     }
 }

 class ReminderReceiver : BroadcastReceiver() {
     override fun onReceive(ctx: Context, intent: Intent) {
         if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
             Reminders.rescheduleAll(ctx)
             return
         }
         val text = intent.getStringExtra("text") ?: return
         val id = intent.getIntExtra("id", 0)
         Reminders.remove(ctx, id)
         val msg = "Lembrete: " + text
         try {
             val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             nm.createNotificationChannel(
                 NotificationChannel("capri_rem", "Lembretes da Capri", NotificationManager.IMPORTANCE_HIGH)
             )
             nm.notify(
                 1000 + id,
                 Notification.Builder(ctx, "capri_rem")
                     .setSmallIcon(android.R.drawable.ic_dialog_info)
                     .setContentTitle("Capri")
                     .setContentText(msg)
                     .setAutoCancel(true)
                     .build()
             )
         } catch (e: Exception) {
         }
         Speaker.toCapri(ctx, "wide", msg)
         val pending = goAsync()
         val done = AtomicBoolean(false)
         val finish: () -> Unit = {
             if (done.compareAndSet(false, true)) pending.finish()
         }
         Speaker.say(ctx, msg) { finish() }
         Handler(Looper.getMainLooper()).postDelayed({ finish() }, 20000)
     }
 }
