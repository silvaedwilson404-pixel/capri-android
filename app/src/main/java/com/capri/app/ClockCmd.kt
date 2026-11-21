 package com.capri.app

 import android.app.Activity
 import android.content.Intent
 import android.provider.AlarmClock
 import java.text.Normalizer
 import java.util.Calendar

 object ClockCmd {
     private val words = mapOf(
         "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4,
         "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10,
         "onze" to 11, "doze" to 12, "quinze" to 15, "vinte" to 20, "trinta" to 30,
         "quarenta" to 40, "cinquenta" to 50
     )
     private val starters = listOf(
         "timer", "temporizador", "alarme", "despertador", "me acorda", "me desperta",
         "poe ", "coloca ", "cria ", "crie ", "inicia ", "comeca ", "define ", "marca ",
         "programa ", "liga ", "faz ", "faca ", "quero ", "preciso ", "ativa ", "agenda ",
         "configura "
     )

     private fun norm(s: String): String {
         val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
         return d.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
             .replace(Regex("\\s+"), " ")
             .trim()
     }

     private fun two(x: Int): String = String.format("%02d", x)

     private fun parseTime(n: String): Pair<Int, Int>? {
         val s = Regex(
             "\\bas (um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez|onze|doze)\\b"
         ).replace(n) { "as " + words[it.groupValues[1]] }
         var h: Int? = null
         var m = 0
         if (s.contains("meio dia")) {
             h = 12
         } else if (s.contains("meia noite")) {
             h = 0
         } else {
             val a = Regex("\\b(\\d{1,2})\\s*[:h]\\s*(\\d{2})\\b").find(s)
             val b = Regex("\\b(\\d{1,2})\\s+e\\s+(meia|\\d{1,2})\\b").find(s)
             val c = Regex("\\b(\\d{1,2})\\s*(?:horas?|h)\\b").find(s)
             val d = Regex("\\bas\\s+(\\d{1,2})\\b").find(s)
             if (a != null) {
                 h = a.groupValues[1].toInt()
                 m = a.groupValues[2].toInt()
             } else if (b != null) {
                 h = b.groupValues[1].toInt()
                 m = if (b.groupValues[2] == "meia") 30 else b.groupValues[2].toInt()
             } else if (c != null) {
                 h = c.groupValues[1].toInt()
             } else if (d != null) {
                 h = d.groupValues[1].toInt()
             }
         }
         if (h == null) return null
         var hh: Int = h
         if (hh == 12 && s.contains("da noite")) {
             hh = 0
         } else if (hh < 12 && (s.contains("da tarde") || s.contains("da noite"))) {
             hh += 12
         }
         if (hh > 23 || m > 59) return null
         return Pair(hh, m)
     }

     private fun parseDays(n: String): ArrayList<Int> {
         val out = ArrayList<Int>()
         if (n.contains("todo dia") || n.contains("todos os dias") || n.contains("diariamente")) {
             out.addAll(
                 listOf(
                     Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                     Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                 )
             )
             return out
         }
         if (n.contains("dias uteis") || n.contains("dia util") || n.contains("segunda a sexta")) {
             out.addAll(
                 listOf(
                     Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                     Calendar.THURSDAY, Calendar.FRIDAY
                 )
             )
             return out
         }
         if (n.contains("fim de semana") || n.contains("fins de semana")) {
             out.add(Calendar.SATURDAY)
             out.add(Calendar.SUNDAY)
             return out
         }
         val names = listOf(
             "domingo" to Calendar.SUNDAY, "segunda" to Calendar.MONDAY,
             "terca" to Calendar.TUESDAY, "quarta" to Calendar.WEDNESDAY,
             "quinta" to Calendar.THURSDAY, "sexta" to Calendar.FRIDAY,
             "sabado" to Calendar.SATURDAY
         )
         for (p in names) {
             if (n.contains(p.first)) out.add(p.second)
         }
         return out
     }

     private fun amount(n: String, unit: String): Int {
         var total = 0
         for (m in Regex("(\\d+|[a-z]+)\\s*(?:" + unit + ")\\b").findAll(n)) {
             val v = m.groupValues[1].toIntOrNull() ?: words[m.groupValues[1]] ?: 0
             total += v
         }
         return total
     }

     private fun timerSeconds(n: String): Int {
         var sec = amount(n, "horas?|h") * 3600 +
             amount(n, "minutos?|min") * 60 +
             amount(n, "segundos?|seg")
         if (n.contains("meia hora") || n.contains("e meia")) sec += 1800
         return sec
     }

     private fun human(sec: Int): String {
         val h = sec / 3600
         val m = (sec % 3600) / 60
         val s = sec % 60
         val parts = ArrayList<String>()
         if (h > 0) parts.add(h.toString() + " h")
         if (m > 0) parts.add(m.toString() + " min")
         if (s > 0) parts.add(s.toString() + " s")
         return parts.joinToString(" ")
     }

     private fun showAlarms(act: Activity) {
         try {
             act.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
         } catch (e: Exception) {
         }
     }

     fun handle(act: Activity, low: String): String? {
         val n = norm(low)
         if (n.contains("bom dia")) return null
         if (starters.none { n.startsWith(it) }) return null

         val cancel = listOf("cancela", "apaga", "desliga", "remove", "tira", "exclui", "deleta")
         if (n.contains("alarme") && cancel.any { n.startsWith(it) }) {
             showAlarms(act)
             return "Eu não apago alarmes. Abri a lista do Relógio, é lá que você apaga."
         }
         val listStarts = listOf(
             "meus alarmes", "mostra os alarmes", "mostra alarmes", "ver alarmes",
             "ver os alarmes", "lista os alarmes", "quais alarmes", "abre os alarmes",
             "abrir os alarmes", "quais sao meus alarmes"
         )
         if (listStarts.any { n.startsWith(it) }) {
             showAlarms(act)
             return "Abrindo os alarmes."
         }

         if (n.contains("timer") || n.contains("temporizador")) {
             val sec = timerSeconds(n)
             if (sec < 1) return "Quanto tempo? Fale por exemplo: timer de 10 minutos."
             if (sec > 86400) return "O timer vai até 24 horas."
             return try {
                 act.startActivity(
                     Intent(AlarmClock.ACTION_SET_TIMER)
                         .putExtra(AlarmClock.EXTRA_LENGTH, sec)
                         .putExtra(AlarmClock.EXTRA_MESSAGE, "Capri")
                         .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                 )
                 "Timer de " + human(sec) + " pedido ao Relógio."
             } catch (e: Exception) {
                 "Não consegui falar com o Relógio: " + e.message
             }
         }

         val isAlarm = n.contains("alarme") || n.contains("despertador") ||
             n.contains("me acorda") || n.contains("me desperta")
         if (!isAlarm) return null
         val t = parseTime(n)
             ?: return "Que horas? Fale por exemplo: alarme às 7:30."
         val days = parseDays(n)
         if (days.isEmpty() && n.contains("amanha")) {
             val now = Calendar.getInstance()
             val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
             if (t.first * 60 + t.second > nowMin) {
                 return "O Relógio não aceita data. Como " + two(t.first) + ":" + two(t.second) +
                     " ainda não passou hoje, o alarme tocaria hoje, e não amanhã. " +
                     "Peça de novo depois desse horário, ou diga os dias da semana."
             }
         }
         return try {
             val i = Intent(AlarmClock.ACTION_SET_ALARM)
                 .putExtra(AlarmClock.EXTRA_HOUR, t.first)
                 .putExtra(AlarmClock.EXTRA_MINUTES, t.second)
                 .putExtra(AlarmClock.EXTRA_MESSAGE, "Capri")
                 .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
             if (days.isNotEmpty()) i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, days)
             act.startActivity(i)
             val extra = if (days.isNotEmpty()) " nos dias escolhidos" else ""
             "Alarme pedido ao Relógio para " + two(t.first) + ":" + two(t.second) + extra +
                 ". Confira em: meus alarmes."
         } catch (e: Exception) {
             "Não consegui falar com o Relógio: " + e.message
         }
     }
 }
