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
 import java.text.SimpleDateFormat
 import java.util.Calendar
 import java.util.Date
 import java.util.Locale
 import java.util.concurrent.atomic.AtomicBoolean

 object GoodMorning {
     private const val CODE = 900001
     private val setRe = Regex(
         "(?:às|as|ás)\\s+(\\d{1,2})(?:\\s*[:h]\\s*(\\d{1,2})|\\s+e\\s+(meia))?" +
             "(?:\\s*h(?:oras?)?)?(?:\\s+da\\s+(manhã|manha|tarde|noite))?"
     )
     private val offs = listOf(
         "desliga", "desativa", "cancela", "para ", "pare ", "chega de", "apaga", "remove", "tira"
     )
     private val verbs = listOf(
         "ativa", "liga", "programa", "agenda", "cria", "configura", "quero", "me d", "me acorda"
     )

     private fun prefs(ctx: Context) =
         ctx.getSharedPreferences("capri_morning", Context.MODE_PRIVATE)

     private fun pending(ctx: Context): PendingIntent {
         val i = Intent(ctx, MorningReceiver::class.java).setAction("com.capri.app.MORNING")
         return PendingIntent.getBroadcast(
             ctx, CODE, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
         )
     }

     private fun nextTime(hour: Int, min: Int): Long {
         val c = Calendar.getInstance()
         c.set(Calendar.HOUR_OF_DAY, hour)
         c.set(Calendar.MINUTE, min)
         c.set(Calendar.SECOND, 0)
         c.set(Calendar.MILLISECOND, 0)
         if (c.timeInMillis <= System.currentTimeMillis() + 60000L) {
             c.add(Calendar.DAY_OF_MONTH, 1)
         }
         return c.timeInMillis
     }

     private fun hhmm(h: Int, m: Int): String = String.format("%02d:%02d", h, m)

     fun schedule(ctx: Context) {
         val p = prefs(ctx)
         if (!p.getBoolean("on", false)) return
         val t = nextTime(p.getInt("h", 7), p.getInt("m", 0))
         val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
         val pi = pending(ctx)
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

     private fun cancel(ctx: Context) {
         try {
             val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
             am.cancel(pending(ctx))
         } catch (e: Exception) {
         }
     }

     fun handle(ctx: Context, low: String): String? {
         if (!low.contains("bom dia")) return null
         val auto = low.contains("autom") || low.contains("todo dia") ||
             low.contains("todos os dias")
         val p = prefs(ctx)
         if (offs.any { low.startsWith(it) }) {
             p.edit().putBoolean("on", false).apply()
             cancel(ctx)
             return "Bom dia automático desligado."
         }
         val m = setRe.find(low)
         if (m != null && (auto || verbs.any { low.startsWith(it) })) {
             var hour = m.groupValues[1].toInt()
             val min = if (m.groupValues[2].isNotEmpty()) {
                 m.groupValues[2].toInt()
             } else if (m.groupValues[3].isNotEmpty()) {
                 30
             } else {
                 0
             }
             val period = m.groupValues[4]
             if ((period == "tarde" || period == "noite") && hour < 12) hour += 12
             if (hour > 23 || min > 59) {
                 return "Não entendi o horário. Fale por exemplo: bom dia automático às 7:30."
             }
             p.edit().putBoolean("on", true).putInt("h", hour).putInt("m", min).apply()
             cancel(ctx)
             schedule(ctx)
             return "Combinado! Todo dia às " + hhmm(hour, min) + " eu te dou bom dia."
         }
         if (auto) {
             return if (p.getBoolean("on", false)) {
                 "O bom dia automático está ligado para as " +
                     hhmm(p.getInt("h", 7), p.getInt("m", 0)) +
                     ". Para mudar, fale: bom dia automático às 8:00."
             } else {
                 "O bom dia automático está desligado. Fale: bom dia automático às 7:30."
             }
         }
         return null
     }

     fun speech(ctx: Context): String {
         val facts = ctx.getSharedPreferences("capri", Context.MODE_PRIVATE)
             .getString("facts", "") ?: ""
         val nm = Regex(
             "(?:nome é|nome e|me chamo)\\s+([\\p{L}]+)", RegexOption.IGNORE_CASE
         ).find(facts)
         var name = nm?.groupValues?.get(1) ?: ""
         if (name.isNotEmpty()) name = name.substring(0, 1).uppercase() + name.substring(1)
         val day = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
         val sb = StringBuilder("Bom dia")
         if (name.isNotEmpty()) sb.append(", ").append(name)
         sb.append("! Hoje é ").append(day.format(Date())).append(". ")

         val end = Calendar.getInstance()
         end.set(Calendar.HOUR_OF_DAY, 23)
         end.set(Calendar.MINUTE, 59)
         end.set(Calendar.SECOND, 59)
         val now = System.currentTimeMillis()
         val items = ArrayList<Pair<Long, String>>()
         try {
             val arr = JSONArray(
                 ctx.getSharedPreferences("capri_rem", Context.MODE_PRIVATE)
                     .getString("list", "[]")
             )
             for (i in 0 until arr.length()) {
                 val o = arr.getJSONObject(i)
                 val t = o.getLong("t")
                 if (t > now && t <= end.timeInMillis) items.add(Pair(t, o.getString("text")))
             }
         } catch (e: Exception) {
         }
         items.sortBy { it.first }
         val hm = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
         if (items.isEmpty()) {
             sb.append("Você não tem lembretes para hoje.")
         } else if (items.size == 1) {
             sb.append("Você tem um lembrete hoje: às ")
                 .append(hm.format(Date(items[0].first))).append(", ")
                 .append(items[0].second).append(".")
         } else {
             sb.append("Você tem ").append(items.size).append(" lembretes hoje: ")
             sb.append(
                 items.take(5).joinToString("; ") {
                     "às " + hm.format(Date(it.first)) + ", " + it.second
                 }
             )
             sb.append(".")
         }
         return sb.toString()
     }
 }

 class MorningReceiver : BroadcastReceiver() {
     override fun onReceive(ctx: Context, intent: Intent) {
         if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
             GoodMorning.schedule(ctx)
             return
         }
         val p = ctx.getSharedPreferences("capri_morning", Context.MODE_PRIVATE)
         if (!p.getBoolean("on", false)) return
         GoodMorning.schedule(ctx)
         val msg = GoodMorning.speech(ctx)
         try {
             val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             nm.createNotificationChannel(
                 NotificationChannel("capri_morning", "Bom dia da Capri", NotificationManager.IMPORTANCE_DEFAULT)
             )
             nm.notify(
                 900001,
                 Notification.Builder(ctx, "capri_morning")
                     .setSmallIcon(android.R.drawable.ic_dialog_info)
                     .setContentTitle("Capri")
                     .setContentText(msg)
                     .setStyle(Notification.BigTextStyle().bigText(msg))
                     .setAutoCancel(true)
                     .build()
             )
         } catch (e: Exception) {
         }
         Speaker.toCapri(ctx, "laugh", msg)
         val pend = goAsync()
         val done = AtomicBoolean(false)
         val finish: () -> Unit = {
             if (done.compareAndSet(false, true)) pend.finish()
         }
         Speaker.say(ctx, msg) { finish() }
         Handler(Looper.getMainLooper()).postDelayed({ finish() }, 9000)
     }
 }
