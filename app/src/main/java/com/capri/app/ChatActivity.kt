 package com.capri.app

 import android.app.Activity
 import android.content.ActivityNotFoundException
 import android.content.Context
 import android.content.Intent
 import android.os.Bundle
 import android.speech.RecognizerIntent
 import android.speech.tts.TextToSpeech
 import android.text.InputType
 import android.view.View
 import android.widget.Button
 import android.widget.EditText
 import android.widget.LinearLayout
 import android.widget.TextView
 import org.json.JSONArray
 import org.json.JSONObject
 import java.net.HttpURLConnection
 import java.net.URL
 import java.util.Locale

 class ChatActivity : Activity() {
     private val faces = listOf("calm", "laugh", "smug", "aside", "look", "many", "wide")
     private var tts: TextToSpeech? = null
     private lateinit var keyField: EditText
     private lateinit var ask: EditText
     private lateinit var out: TextView

     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)

         tts = TextToSpeech(this) { status ->
             if (status == TextToSpeech.SUCCESS) {
                 tts?.language = Locale("pt", "BR")
             }
         }

         val box = LinearLayout(this)
         box.orientation = LinearLayout.VERTICAL
         box.setPadding(40, 40, 40, 40)

         keyField = EditText(this)
         keyField.hint = "Cole a chave do Groq (só uma vez)"
         keyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
         if (prefs.getString("key", "") != "") keyField.visibility = View.GONE
         box.addView(keyField)

         ask = EditText(this)
         ask.hint = "Pergunte algo à Capri"
         box.addView(ask)

         val mic = Button(this)
         mic.text = "Falar"
         mic.setOnClickListener {
             try {
                 startActivityForResult(
                     Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                         .putExtra(
                             RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                             RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                         )
                         .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"),
                     7
                 )
             } catch (e: ActivityNotFoundException) {
                 out.text = "Seu celular não tem reconhecimento de voz."
             }
         }
         box.addView(mic)

         val send = Button(this)
         send.text = "Perguntar"
         send.setOnClickListener { sendQuestion() }
         box.addView(send)

         val clear = Button(this)
         clear.text = "Limpar memória"
         clear.setOnClickListener {
             getSharedPreferences("capri", Context.MODE_PRIVATE)
                 .edit().remove("hist").apply()
             out.text = "Memória apagada."
         }
         box.addView(clear)

         out = TextView(this)
         out.textSize = 16f
         out.setPadding(0, 24, 0, 0)
         box.addView(out)

         setContentView(box)
     }

     override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
         super.onActivityResult(requestCode, resultCode, data)
         if (requestCode == 7 && resultCode == RESULT_OK) {
             val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
             if (said != null) {
                 ask.setText(said)
                 sendQuestion()
             }
         }
     }

     private fun sendQuestion() {
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
         val typed = keyField.text.toString().trim()
         if (keyField.visibility == View.VISIBLE && typed.isNotEmpty()) {
             prefs.edit().putString("key", typed).apply()
             keyField.setText("")
             keyField.visibility = View.GONE
         }
         val key = prefs.getString("key", "") ?: ""
         val q = ask.text.toString().trim()
         if (key.isEmpty()) {
             out.text = "Falta colar a chave."
             return
         }
         if (q.isEmpty()) return
         val hist = try {
             JSONArray(prefs.getString("hist", "[]"))
         } catch (e: Exception) {
             JSONArray()
         }
         tts?.stop()
         out.text = "Pensando..."
         Thread {
             val answer = try {
                 call(key, hist, q)
             } catch (e: Exception) {
                 "[wide] Deu erro: " + e.message
             }
             val failed = answer.startsWith("[wide] Erro") || answer.startsWith("[wide] Deu erro")
             if (!failed) saveHistory(hist, q, answer)
             val m = Regex("^\\[(\\w+)\\]\\s*").find(answer)
             val tag = m?.groupValues?.get(1) ?: "calm"
             val face = if (tag in faces) tag else "calm"
             val text = answer.replace(Regex("^\\[\\w+\\]\\s*"), "")
             runOnUiThread {
                 out.text = text
                 tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "capri")
             }
             startService(
                 Intent(this, OverlayService::class.java)
                     .setAction("FACE")
                     .putExtra("face", face)
             )
         }.start()
     }

     private fun saveHistory(hist: JSONArray, q: String, a: String) {
         hist.put(JSONObject().put("role", "user").put("content", q))
         hist.put(JSONObject().put("role", "assistant").put("content", a))
         val trimmed = JSONArray()
         val start = Math.max(0, hist.length() - 12)
         for (i in start until hist.length()) trimmed.put(hist.get(i))
         getSharedPreferences("capri", Context.MODE_PRIVATE)
             .edit().putString("hist", trimmed.toString()).apply()
     }

     override fun onDestroy() {
         tts?.stop()
         tts?.shutdown()
         super.onDestroy()
     }

     private fun call(key: String, hist: JSONArray, q: String): String {
         val body = JSONObject()
         body.put("model", "openai/gpt-oss-20b")
         val msgs = JSONArray()
         msgs.put(
             JSONObject().put("role", "system").put(
                 "content",
                 "Você é Capri, uma assistente debochada e simpática. " +
                     "Você lembra do que já foi conversado. " +
                     "Responda em português, em no máximo 3 frases. " +
                     "Comece SEMPRE com uma tag de emoção entre colchetes, " +
                     "uma destas: [calm] [laugh] [smug] [aside] [look] [many] [wide]."
             )
         )
         for (i in 0 until hist.length()) msgs.put(hist.get(i))
         msgs.put(JSONObject().put("role", "user").put("content", q))
         body.put("messages", msgs)

         val c = URL("https://api.groq.com/openai/v1/chat/completions")
             .openConnection() as HttpURLConnection
         c.requestMethod = "POST"
         c.setRequestProperty("Content-Type", "application/json")
         c.setRequestProperty("Authorization", "Bearer " + key)
         c.setRequestProperty("User-Agent", "CapriApp/1.0")
         c.doOutput = true
         c.connectTimeout = 15000
         c.readTimeout = 30000
         c.outputStream.use { it.write(body.toString().toByteArray()) }

         val code = c.responseCode
         if (code !in 200..299) return "[wide] Erro " + code
         val txt = c.inputStream.bufferedReader().readText()
         return JSONObject(txt).getJSONArray("choices").getJSONObject(0)
             .getJSONObject("message").getString("content")
     }
 }
