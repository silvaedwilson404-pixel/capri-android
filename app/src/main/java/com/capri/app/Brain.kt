 package com.capri.app

 import android.content.Context
 import org.json.JSONArray
 import org.json.JSONObject
 import java.net.HttpURLConnection
 import java.net.URL

 object Brain {
     private val presetNames = listOf("Debochada", "Fofa", "Séria", "Pirata")
     private val presetDescs = listOf(
         "debochada e simpática, com humor ácido",
         "fofa e carinhosa, fala com muito afeto",
         "séria, direta e profissional",
         "um pirata exagerado, que fala com gírias de pirata"
     )

     private fun personaDesc(ctx: Context): String {
         val prefs = ctx.getSharedPreferences("capri", Context.MODE_PRIVATE)
         val name = prefs.getString("persona_name", presetNames[0]) ?: presetNames[0]
         val p = presetNames.indexOf(name)
         if (p >= 0) return presetDescs[p]
         try {
             val arr = JSONArray(prefs.getString("personas", "[]"))
             for (i in 0 until arr.length()) {
                 val o = arr.getJSONObject(i)
                 if (o.getString("name") == name) return o.getString("desc")
             }
         } catch (e: Exception) {
         }
         return presetDescs[0]
     }

     fun ask(ctx: Context, q: String): String {
         val prefs = ctx.getSharedPreferences("capri", Context.MODE_PRIVATE)
         val key = prefs.getString("key", "") ?: ""
         if (key.isEmpty()) return "[wide] Erro: falta colar a chave do Groq na janelinha."

         val rem = Regex(
             "^(lembre|lembra|anote|anota|guarde|guarda)\\s+que\\s+(.+)$",
             RegexOption.IGNORE_CASE
         ).find(q.trim())
         if (rem != null) {
             val fact = rem.groupValues[2].trim().trimEnd('.')
             val cur = prefs.getString("facts", "") ?: ""
             val upd = if (cur.isEmpty()) fact else cur + "\n" + fact
             prefs.edit().putString("facts", upd.takeLast(2000)).apply()
         }
         val facts = prefs.getString("facts", "") ?: ""
         val hist = try {
             JSONArray(prefs.getString("hist", "[]"))
         } catch (e: Exception) {
             JSONArray()
         }

         val known = if (facts.isEmpty()) "" else
             "Fatos que você sabe sobre o usuário, use quando fizer sentido: " +
                 facts.replace("\n", "; ") + ". "
         val system = "Você é Capri, uma assistente " + personaDesc(ctx) + ". " +
             known +
             Notes.context(ctx, q) +
             "Você lembra do que já foi conversado. " +
             "Você está numa conversa falada, então responda em português, " +
             "em no máximo 3 frases curtas, sem listas. " +
             "Comece SEMPRE com uma tag de emoção entre colchetes, " +
             "uma destas: [calm] [laugh] [smug] [aside] [look] [many] [wide]."

         val msgs = JSONArray()
         msgs.put(JSONObject().put("role", "system").put("content", system))
         for (i in 0 until hist.length()) msgs.put(hist.get(i))
         msgs.put(JSONObject().put("role", "user").put("content", q))
         val body = JSONObject()
         body.put("model", "openai/gpt-oss-20b")
         body.put("messages", msgs)

         val c = URL("https://api.groq.com/openai/v1/chat/completions")
             .openConnection() as HttpURLConnection
         c.requestMethod = "POST"
         c.setRequestProperty("Content-Type", "application/json")
         c.setRequestProperty("Authorization", "Bearer " + key)
         c.setRequestProperty("User-Agent", "CapriApp/1.3")
         c.doOutput = true
         c.connectTimeout = 15000
         c.readTimeout = 30000
         c.outputStream.use { it.write(body.toString().toByteArray()) }

         val code = c.responseCode
         if (code !in 200..299) return "[wide] Erro " + code
         val txt = c.inputStream.bufferedReader().readText()
         val answer = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
             .getJSONObject("message").getString("content")

         hist.put(JSONObject().put("role", "user").put("content", q))
         hist.put(JSONObject().put("role", "assistant").put("content", answer))
         val trimmed = JSONArray()
         val start = Math.max(0, hist.length() - 12)
         for (i in start until hist.length()) trimmed.put(hist.get(i))
         prefs.edit().putString("hist", trimmed.toString()).apply()
         return answer
     }
 }
