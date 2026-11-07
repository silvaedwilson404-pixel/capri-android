 package com.capri.app

 import android.app.Activity
 import android.app.AlertDialog
 import android.content.ActivityNotFoundException
 import android.content.Context
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.graphics.BitmapFactory
 import android.graphics.Color
 import android.graphics.Typeface
 import android.graphics.drawable.GradientDrawable
 import android.os.Build
 import android.os.Bundle
 import android.speech.RecognizerIntent
 import android.speech.tts.TextToSpeech
 import android.text.InputType
 import android.view.Gravity
 import android.view.View
 import android.view.Window
 import android.widget.AdapterView
 import android.widget.ArrayAdapter
 import android.widget.Button
 import android.widget.EditText
 import android.widget.ImageView
 import android.widget.LinearLayout
 import android.widget.ScrollView
 import android.widget.Spinner
 import android.widget.TextView
 import org.json.JSONArray
 import org.json.JSONObject
 import java.net.HttpURLConnection
 import java.net.URL
 import java.util.Locale

 class ChatActivity : Activity() {
     private val faces = listOf("calm", "laugh", "smug", "aside", "look", "many", "wide")
     private val presetNames = listOf("Debochada", "Fofa", "Séria", "Pirata")
     private val presetDescs = listOf(
         "debochada e simpática, com humor ácido",
         "fofa e carinhosa, fala com muito afeto",
         "séria, direta e profissional",
         "um pirata exagerado, que fala com gírias de pirata"
     )
     private val presetStyle = listOf(6, 1, 2, 5)
     private val styleNames = listOf(
         "Normal", "Aguda", "Grave", "Rápida", "Lenta", "Rouca", "Debochada", "Robô"
     )
     private val stylePitch = listOf(1.0f, 1.15f, 0.9f, 1.0f, 0.95f, 0.85f, 1.05f, 0.7f)
     private val styleRate = listOf(1.0f, 1.0f, 0.95f, 1.15f, 0.85f, 0.9f, 1.05f, 0.9f)
     private val names = ArrayList<String>()
     private val descs = ArrayList<String>()
     private var tts: TextToSpeech? = null
     private lateinit var keyField: EditText
     private lateinit var ask: EditText
     private lateinit var out: TextView
     private lateinit var spinner: Spinner
     private lateinit var faceView: ImageView
     private val chip: TextView by lazy { TextView(this) }
     private lateinit var adapter: ArrayAdapter<String>

     private fun customList(): JSONArray {
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
         return try {
             JSONArray(prefs.getString("personas", "[]"))
         } catch (e: Exception) {
             JSONArray()
         }
     }

     private fun reload() {
         names.clear()
         descs.clear()
         names.addAll(presetNames)
         descs.addAll(presetDescs)
         val arr = customList()
         for (i in 0 until arr.length()) {
             val o = arr.getJSONObject(i)
             names.add(o.getString("name"))
             descs.add(o.getString("desc"))
         }
     }

     private fun styleOf(name: String): Int {
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
         val p = presetNames.indexOf(name)
         val def = if (p >= 0) presetStyle[p] else 0
         val s = prefs.getInt("style_" + name, def)
         return if (s in styleNames.indices) s else 0
     }

     private fun applyVoice(name: String) {
         val s = styleOf(name)
         tts?.setPitch(stylePitch[s])
         tts?.setSpeechRate(styleRate[s])
     }

     private fun getFacts(): String {
         return getSharedPreferences("capri", Context.MODE_PRIVATE)
             .getString("facts", "") ?: ""
     }

     private fun saveFacts(text: String) {
         getSharedPreferences("capri", Context.MODE_PRIVATE)
             .edit().putString("facts", text.trim()).apply()
     }

     private fun editFacts() {
         val input = EditText(this)
         input.setText(getFacts())
         input.hint = "Um fato por linha. Ex: Meu nome é Ed"
         input.minLines = 6
         input.gravity = Gravity.TOP
         input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
         val lay = LinearLayout(this)
         lay.setPadding(48, 24, 48, 0)
         lay.addView(
             input,
             LinearLayout.LayoutParams(
                 LinearLayout.LayoutParams.MATCH_PARENT,
                 LinearLayout.LayoutParams.WRAP_CONTENT
             )
         )
         AlertDialog.Builder(this)
             .setTitle("Fatos sobre mim")
             .setView(lay)
             .setPositiveButton("Salvar") { _, _ ->
                 saveFacts(input.text.toString())
                 out.text = "Fatos salvos."
             }
             .setNegativeButton("Cancelar", null)
             .show()
     }

     private fun listen() {
         setStatus("OUVINDO", "#ff2d6f")
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

     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         requestWindowFeature(Window.FEATURE_NO_TITLE)
         val card = GradientDrawable()
         card.setColor(Color.parseColor("#0f0b1a"))
         card.setStroke(6, Color.parseColor("#7b3ff2"))
         card.cornerRadius = 36f
         window.setBackgroundDrawable(card)
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)

         tts = TextToSpeech(this) { status ->
             if (status == TextToSpeech.SUCCESS) {
                 tts?.language = Locale("pt", "BR")
             }
         }

         reload()

         val box = LinearLayout(this)
         box.orientation = LinearLayout.VERTICAL
         box.setPadding(40, 40, 40, 40)

         val head = LinearLayout(this)
         head.orientation = LinearLayout.HORIZONTAL
         head.gravity = Gravity.CENTER_VERTICAL
         faceView = ImageView(this)
         faceView.scaleType = ImageView.ScaleType.CENTER_CROP
         head.addView(faceView, LinearLayout.LayoutParams(160, 160))
         val title = TextView(this)
         title.text = "CAPRI"
         title.setTextColor(Color.WHITE)
         title.typeface = Typeface.MONOSPACE
         title.textSize = 18f
         title.setPadding(24, 0, 0, 0)
         head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
         chip.typeface = Typeface.MONOSPACE
         chip.textSize = 12f
         chip.setPadding(20, 6, 20, 6)
         head.addView(chip)
         box.addView(head)
         setStatus("PRONTA", "#4a2fa0")
         showFace("calm")

         keyField = EditText(this)
         keyField.hint = "Cole a chave do Groq (só uma vez)"
         keyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
         if (prefs.getString("key", "") != "") keyField.visibility = View.GONE
         box.addView(keyField)

         spinner = Spinner(this)
         adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
         adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
         spinner.adapter = adapter
         val saved = prefs.getString("persona_name", presetNames[0]) ?: presetNames[0]
         val savedIdx = names.indexOf(saved)
         spinner.setSelection(if (savedIdx >= 0) savedIdx else 0)
         spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
             override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                 val chosen = names.getOrNull(pos) ?: return
                 val current = prefs.getString("persona_name", presetNames[0])
                 if (chosen != current) {
                     prefs.edit().putString("persona_name", chosen).remove("hist").apply()
                 }
             }

             override fun onNothingSelected(p: AdapterView<*>?) {}
         }
         box.addView(spinner)

         ask = EditText(this)
         ask.hint = "Pergunte algo à Capri"
         box.addView(ask)

         val mic = Button(this)
         mic.text = "Falar"
         mic.setOnClickListener { listen() }
         box.addView(mic)

         val send = Button(this)
         send.text = "Perguntar"
         send.setOnClickListener { sendQuestion() }
         box.addView(send)

         val facts = Button(this)
         facts.text = "Fatos sobre mim"
         facts.setOnClickListener { editFacts() }
         box.addView(facts)

         val voice = Button(this)
         voice.text = "Voz desta personalidade"
         voice.setOnClickListener { chooseVoice() }
         box.addView(voice)

         val add = Button(this)
         add.text = "Nova personalidade"
         add.setOnClickListener { newPersona() }
         box.addView(add)

         val del = Button(this)
         del.text = "Apagar personalidade"
         del.setOnClickListener { deletePersona() }
         box.addView(del)

         val clear = Button(this)
         clear.text = "Limpar memória"
         clear.setOnClickListener {
             getSharedPreferences("capri", Context.MODE_PRIVATE)
                 .edit().remove("hist").apply()
             out.text = "Memória da conversa apagada. Os fatos sobre você continuam."
         }
         box.addView(clear)

         out = TextView(this)
         out.textSize = 16f
         out.setPadding(0, 24, 0, 0)
         box.addView(out)

         for (i in 0 until box.childCount) {
             val v = box.getChildAt(i)
             if (v is Button) {
                 styleButton(v)
             } else if (v is EditText) {
                 v.setTextColor(Color.WHITE)
                 v.setHintTextColor(Color.parseColor("#8a7fa8"))
                 v.typeface = Typeface.MONOSPACE
             }
         }

         val scroll = ScrollView(this)
         scroll.addView(box)
         setContentView(scroll)

         if (prefs.getString("key", "") != "" && savedInstanceState == null) {
             scroll.postDelayed({ listen() }, 300)
         }
     }

     private fun setStatus(text: String, bg: String) {
         runOnUiThread {
             try {
                 chip.text = text
                 chip.setTextColor(Color.WHITE)
                 val g = GradientDrawable()
                 g.setColor(Color.parseColor(bg))
                 g.cornerRadius = 12f
                 chip.background = g
             } catch (e: Exception) {
             }
         }
     }

     private fun showFace(name: String) {
         Thread {
             try {
                                 val bmp = try {
                     assets.open("capri/face-" + name + ".png").use { BitmapFactory.decodeStream(it) }
                 } catch (e: Exception) {
                     BitmapFactory.decodeStream(URL("https://raw.githubusercontent.com/mochiiwuff/capri-desktop/main/assets/capri/face-" + name + ".png").openStream())
                                 }
                 runOnUiThread {
                     try {
                         if (::faceView.isInitialized) faceView.setImageBitmap(bmp)
                     } catch (e: Exception) {
                     }
                 }
             } catch (e: Exception) {
             }
         }.start()
     }

     private fun styleButton(b: Button) {
         b.setTextColor(Color.WHITE)
         b.typeface = Typeface.MONOSPACE
         b.isAllCaps = true
         b.textSize = 13f
         val g = GradientDrawable()
         g.setColor(Color.parseColor("#1a1428"))
         g.setStroke(4, Color.parseColor("#7b3ff2"))
         g.cornerRadius = 24f
         b.background = g
         b.stateListAnimator = null
     }

     private fun chooseVoice() {
         val name = names.getOrNull(spinner.selectedItemPosition) ?: return
         val cur = styleOf(name)
         AlertDialog.Builder(this)
             .setTitle("Voz de " + name)
             .setSingleChoiceItems(styleNames.toTypedArray(), cur) { d, which ->
                 getSharedPreferences("capri", Context.MODE_PRIVATE)
                     .edit().putInt("style_" + name, which).apply()
                 applyVoice(name)
                 tts?.speak("Oi, é assim que eu falo.", TextToSpeech.QUEUE_FLUSH, null, "demo")
                 d.dismiss()
             }
             .setNegativeButton("Fechar", null)
             .show()
     }

     private fun newPersona() {
         val lay = LinearLayout(this)
         lay.orientation = LinearLayout.VERTICAL
         lay.setPadding(48, 24, 48, 0)
         val nameIn = EditText(this)
         nameIn.hint = "Nome (ex: Vovó)"
         lay.addView(nameIn)
         val descIn = EditText(this)
         descIn.hint = "Como ela é? (ex: uma vovó carinhosa que dá conselhos)"
         lay.addView(descIn)
         AlertDialog.Builder(this)
             .setTitle("Nova personalidade")
             .setView(lay)
             .setPositiveButton("Salvar") { _, _ ->
                 val n = nameIn.text.toString().trim()
                 val d = descIn.text.toString().trim()
                 if (n.isNotEmpty() && d.isNotEmpty()) {
                     val arr = customList()
                     arr.put(JSONObject().put("name", n).put("desc", d))
                     getSharedPreferences("capri", Context.MODE_PRIVATE).edit()
                         .putString("personas", arr.toString())
                         .putString("persona_name", n)
                         .remove("hist")
                         .apply()
                     reload()
                     adapter.notifyDataSetChanged()
                     spinner.setSelection(names.indexOf(n))
                     out.text = "Personalidade salva: " + n + ". Toque em Voz desta personalidade para escolher como ela fala."
                 }
             }
             .setNegativeButton("Cancelar", null)
             .show()
     }

     private fun deletePersona() {
         val idx = spinner.selectedItemPosition
         if (idx < presetNames.size) {
             out.text = "As personalidades padrão não podem ser apagadas."
             return
         }
         val gone = names[idx]
         val arr = customList()
         val keep = JSONArray()
         for (i in 0 until arr.length()) {
             if (i != idx - presetNames.size) keep.put(arr.get(i))
         }
         getSharedPreferences("capri", Context.MODE_PRIVATE).edit()
             .putString("personas", keep.toString())
             .putString("persona_name", presetNames[0])
             .remove("style_" + gone)
             .remove("hist")
             .apply()
         reload()
         adapter.notifyDataSetChanged()
         spinner.setSelection(0)
         out.text = "Personalidade apagada."
     }

     override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
         super.onActivityResult(requestCode, resultCode, data)
         setStatus("PRONTA", "#4a2fa0")
         if (requestCode == 7 && resultCode == RESULT_OK) {
             val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
             if (said != null) {
                 ask.setText(said)
                 sendQuestion()
             }
         }
     }

     private fun handleMusic(q: String): Boolean {
         val low = q.lowercase().trim().trimEnd('.', '!', '?')         
               if (MusicPlayer.isListCommand(low)) {
             val p0 = if (Build.VERSION.SDK_INT >= 33) {
                 "android.permission.READ_MEDIA_AUDIO"
             } else {
                 "android.permission.READ_EXTERNAL_STORAGE"
             }
             if (checkSelfPermission(p0) != PackageManager.PERMISSION_GRANTED) {
                 requestPermissions(arrayOf(p0), 9)
                 out.text = "Permita o acesso aos áudios e peça de novo."
                 return true
             }
             tts?.stop()
             out.text = "Procurando..."
             setStatus("PENSANDO", "#7b3ff2")
             MusicPlayer.command(applicationContext, low) { msg ->
                 runOnUiThread {
                     out.text = msg
                     setStatus("RESPONDEU", "#1fae6b")
                 }
             }
             return true
         }
         val stops = listOf(
             "para", "parar", "pare", "para a música", "para a musica",
             "parar a música", "parar a musica", "desliga a música", "desliga a musica"
         )
         val pauses = listOf("pausa", "pausar", "pausa a música", "pausa a musica")
         val resumes = listOf("continua", "continuar", "continua a música", "continua a musica", "retoma")
         if (low in stops) {
             MusicPlayer.stop()
             out.text = "Música parada."
             setStatus("PRONTA", "#4a2fa0")
             return true
         }
         if (low in pauses) {
             MusicPlayer.pause()
             out.text = "Música pausada."
             setStatus("PRONTA", "#4a2fa0")
             return true
         }
         if (low in resumes) {
             MusicPlayer.resume()
             out.text = "Continuando a música."
             setStatus("PRONTA", "#4a2fa0")
             return true
         }
         val starts = listOf("toca ", "tocar ", "toque ", "põe ", "poe ", "coloca ", "colocar ", "reproduz ")
         val start = starts.firstOrNull { low.startsWith(it) } ?: return false
         val wanted = low.removePrefix(start).trim()
         val perm = if (Build.VERSION.SDK_INT >= 33) {
             "android.permission.READ_MEDIA_AUDIO"
         } else {
             "android.permission.READ_EXTERNAL_STORAGE"
         }
         if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
             requestPermissions(arrayOf(perm), 9)
             out.text = "Permita o acesso aos áudios e peça de novo."
             return true
         }
         tts?.stop()
         out.text = "Procurando..."
         setStatus("PENSANDO", "#7b3ff2")
         MusicPlayer.play(applicationContext, wanted) { msg ->
             runOnUiThread {
                 out.text = msg
                 setStatus("RESPONDEU", "#1fae6b")
             }
         }
         return true
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
         if (handleMusic(q)) return         
      val extra = Extras.handle(this, q)
         if (extra != null) {
             out.text = extra
             setStatus("RESPONDEU", "#1fae6b")
             return
         }
         if (key.isEmpty()) {
             out.text = "Falta colar a chave."
             return
         }
         if (q.isEmpty()) return

         val rem = Regex(
             "^(lembre|lembra|anote|anota|guarde|guarda)\\s+que\\s+(.+)$",
             RegexOption.IGNORE_CASE
         ).find(q)
         if (rem != null) {
             val fact = rem.groupValues[2].trim().trimEnd('.')
             val cur = getFacts()
             val upd = if (cur.isEmpty()) fact else cur + "\n" + fact
             saveFacts(upd.takeLast(2000))
         }
         val facts = getFacts()

         val hist = try {
             JSONArray(prefs.getString("hist", "[]"))
         } catch (e: Exception) {
             JSONArray()
         }
         val pname = names.getOrNull(spinner.selectedItemPosition) ?: presetNames[0]
         val desc = descs.getOrNull(spinner.selectedItemPosition) ?: presetDescs[0]
         tts?.stop()
         out.text = "Pensando..."
         setStatus("PENSANDO", "#7b3ff2")
         Thread {
             val answer = try {
                 call(key, hist, q, desc, facts)
             } catch (e: Exception) {
                 "[wide] Deu erro: " + e.message
             }
             val failed = answer.startsWith("[wide] Erro") || answer.startsWith("[wide] Deu erro")
             if (!failed) saveHistory(hist, q, answer)
             val m = Regex("^\\[(\\w+)\\]\\s*").find(answer)
             val tag = m?.groupValues?.get(1) ?: "calm"
             val face = if (tag in faces) tag else "calm"
             val text = answer.replace(Regex("^\\[\\w+\\]\\s*"), "")
             if (failed) setStatus("ERRO", "#ff2d6f") else setStatus("RESPONDEU", "#1fae6b")
             showFace(face)
             runOnUiThread {
                 out.text = text
                 applyVoice(pname)
                 tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "capri")
             }
             startService(
                 Intent(this, OverlayService::class.java)                     
                     .setAction("FACE")
                     .putExtra("face", face)
                     .putExtra("talk", Math.min(12000, 800 + text.length * 70))                     
                     .putExtra("text", text)
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
     private fun call(key: String, hist: JSONArray, q: String, desc: String, facts: String): String {
         val body = JSONObject()
         body.put("model", "openai/gpt-oss-20b")
         val msgs = JSONArray()
         val known = if (facts.isEmpty()) "" else
             "Fatos que você sabe sobre o usuário, use quando fizer sentido: " +
                 facts.replace("\n", "; ") + ". "
         msgs.put(
             JSONObject().put("role", "system").put(
                 "content",
                 "Você é Capri, uma assistente " + desc + ". " +
                     known +
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
