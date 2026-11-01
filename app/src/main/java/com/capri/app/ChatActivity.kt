package com.capri.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

class ChatActivity : Activity() {
    private val faces = listOf("calm", "laugh", "smug", "aside", "look", "many", "wide")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 40, 40, 40)

        val keyField = EditText(this)
        keyField.hint = "Cole a chave do Groq (só uma vez)"
        keyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        if (prefs.getString("key", "") != "") keyField.visibility = View.GONE
        box.addView(keyField)

        val ask = EditText(this)
        ask.hint = "Pergunte algo à Capri"
        box.addView(ask)

        val send = Button(this)
        send.text = "Perguntar"
        box.addView(send)

        val out = TextView(this)
        out.textSize = 16f
        out.setPadding(0, 24, 0, 0)
        box.addView(out)

        send.setOnClickListener {
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
                return@setOnClickListener
            }
            if (q.isEmpty()) return@setOnClickListener
            out.text = "Pensando..."
            Thread {
                val answer = try {
                    call(key, q)
                } catch (e: Exception) {
                    "[wide] Deu erro: " + e.message
                }
                val m = Regex("^\\[(\\w+)\\]\\s*").find(answer)
                val tag = m?.groupValues?.get(1) ?: "calm"
                val face = if (tag in faces) tag else "calm"
                val text = answer.replace(Regex("^\\[\\w+\\]\\s*"), "")
                runOnUiThread { out.text = text }
                startService(
                    Intent(this, OverlayService::class.java)
                        .setAction("FACE")
                        .putExtra("face", face)
                )
            }.start()
        }
        setContentView(box)
    }

    private fun call(key: String, q: String): String {
        val body = JSONObject()
        body.put("model", "openai/gpt-oss-20b")
        val msgs = JSONArray()
        msgs.put(
            JSONObject().put("role", "system").put(
                "content",
                "Você é Capri, uma assistente debochada e simpática. " +
                    "Responda em português, em no máximo 3 frases. " +
                    "Comece SEMPRE com uma tag de emoção entre colchetes, " +
                    "uma destas: [calm] [laugh] [smug] [aside] [look] [many] [wide]."
            )
        )
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
