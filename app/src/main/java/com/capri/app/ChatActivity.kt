 package com.capri.app

 import android.app.Activity
 import android.app.AlertDialog
 import android.content.ActivityNotFoundException
 import android.content.Context
 import android.content.Intent
 import android.graphics.Bitmap
 import android.graphics.BitmapFactory
 import android.graphics.Color
 import android.graphics.Typeface
 import android.graphics.drawable.GradientDrawable
 import android.os.Bundle
 import android.speech.RecognizerIntent
 import android.speech.tts.TextToSpeech
 import android.text.InputType
 import android.view.Gravity
 import android.view.View
 import android.view.ViewGroup
 import android.view.Window
 import android.widget.AdapterView
 import android.widget.ArrayAdapter
 import android.widget.Button
 import android.widget.EditText
 import android.widget.FrameLayout
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
     private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
     private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
     private val PURPLE = Color.parseColor("#7b3ff2")
     private val FACE_BASE = "https://raw.githubusercontent.com/mochiiwuff/capri-desktop/main/assets/capri/"

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
     private val faceCache = HashMap<String, Bitmap>()
     private var tts: TextToSpeech? = null
     private lateinit var keyField: EditText
     private lateinit var ask: EditText
     private lateinit var out: TextView
     private lateinit var chip: TextView
     private lateinit var faceView: ImageView
     private lateinit var personaLabel: TextView
     private lateinit var spinner: Spinner
     private lateinit var adapter: ArrayAdapter<String>

     private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

     private fun styledButton(label: String, filled: Boolean): Button {
         val b = Button(this)
         b.text = label
         b.isAllCaps = true
         b.setTextColor(Color.WHITE)
         b.typeface = Typeface.MONOSPACE
         b.textSize = 13f
         val g = GradientDrawable()
         g.cornerRadius = dp(8).toFloat()
         if (filled) {
             g.setColor(PURPLE)
         } else {
             g.setColor(Color.parseColor("#1a1428"))
             g.setStroke(dp(2), PURPLE)
         }
         b.background = g
         b.stateListAnimator = null
         b.setPadding(dp(12), dp(10), dp(12), dp(10))
         return b
     }

     private fun styledEdit(hint: String): EditText {
         val e = EditText(this)
         e.hint = hint
         e.setTextColor(Color.WHITE)
         e.setHintTextColor(Color.parseColor("#8a7fa8"))
         e.typeface = Typeface.MONOSPACE
         e.textSize = 14f
         val g = GradientDrawable()
         g.setColor(Color.parseColor("#1a1428"))
         g.setStroke(dp(2), Color.parseColor("#4a2fa0"))
         g.cornerRadius = dp(8).toFloat()
         e.background = g
         e.setPadding(dp(12), dp(10), dp(12), dp(10))
         return e
     }

     private fun params(top: Int): LinearLayout.LayoutParams {
         val p = LinearLayout.LayoutParams(MATCH, WRAP)
         p.topMargin = dp(top)
         return p
     }

     private fun row(a: View, b: View): LinearLayout {
         val r = LinearLayout(this)
         r.orientation = LinearLayout.HORIZONTAL
         val p1 = LinearLayout.LayoutParams(0, WRAP, 1f)
         p1.rightMargin = dp(4)
         val p2 = LinearLayout.LayoutParams(0, WRAP, 1f)
         p2.leftMargin = dp(4)
         r.addView(a, p1)
         r.addView(b, p2)
         return r
     }

     private fun setStatus(text: String, bg: String, fg: String) {
         runOnUiThread {
             chip.text = text
             chip.setTextColor(Color.parseColor(fg))
             val g = GradientDrawable()
             g.setColor(Color.parseColor(bg))
             g.cornerRadius = dp(4).toFloat()
             chip.background = g
         }
     }

     private fun statusReady() = setStatus("PRONTA", "#2a2140", "#c9b8ff")
     private fun statusListening() = setStatus("OUVINDO", "#ff2d6f", "#ffffff")
     private fun statusThinking() = setStatus("PENSANDO", "#7b3ff2", "#ffffff")
     private fun statusDone() = setStatus("RESPONDEU", "#2bff88", "#0f0b1a")
     private fun statusError() = setStatus("ERRO", "#ff2d6f", "#ffffff")

     private fun showFace(name: String) {
         Thread {
             try {
                 val bmp = faceCache[name] ?: run {
                     val b = URL(FACE_BASE + "face-" + name + ".png").openStream()
                         .use { BitmapFactory.decodeStream(it) }
                     if (b != null) faceCache[name] = b
                     b
                 }
                 if (bmp != null) runOnUiThread { faceView.setImageBitmap(bmp) }
             } catch (e: Exception) {
             }
         }.start()
     }

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
         lay.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
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
         statusListening()
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
             statusError()
         }
     }

     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         requestWindowFeature(Window.FEATURE_NO_TITLE)
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)

         tts = TextToSpeech(this) { status ->
             if (status == TextToSpeech.SUCCESS) {
                 tts?.language = Locale("pt", "BR")
             }
         }

         reload()

         val card = GradientDrawable()
         card.setColor(Color.parseColor("#0f0b1a"))
         card.setStroke(dp(3), PURPLE)
         card.cornerRadius = dp(14).toFloat()
         window.setBackgroundDrawable(card)
         window.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), WRAP)

         val box = LinearLayout(this)
         box.orientation = LinearLayout.VERTICAL
         box.setPadding(dp(16), dp(16), dp(16), dp(16))

         val head = LinearLayout(this)
         head.orientation = LinearLayout.HORIZONTAL
         head.gravity = Gravity.CENTER_VERTICAL

         val frame = FrameLayout(this)
         frame.setBackgroundColor(PURPLE)
         frame.setPadding(dp(2), dp(2), dp(2), dp(2))
         faceView = ImageView(this)
         faceView.scaleType = ImageView.ScaleType.CENTER_CROP
         frame.addView(faceView, FrameLayout.LayoutParams(dp(64), dp(64)))
         head.addView(frame, LinearLayout.LayoutParams(WRAP, WRAP))

         val nameRow = LinearLayout(this)
         nameRow.orientation = LinearLayout.HORIZONTAL
         nameRow.gravity = Gravity.CENTER_VERTICAL
         nameRow.setPadding(dp(12), 0, 0, 0)
         val nameLabel = TextView(this)
         nameLabel.text = "CAPRI"
         nameLabel.setTextColor(Color.WHITE)
         nameLabel.typeface = Typeface.MONOSPACE
         nameLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
         nameLabel.textSize = 16f
         personaLabel = TextView(this)
         personaLabel.setTextColor(Color.parseColor("#8a7fa8"))
         personaLabel.typeface = Typeface.MONOSPACE
         personaLabel.textSize = 12f
         personaLabel.setPadding(dp(8), 0, 0, 0)
         chip = TextView(this)
         chip.typeface = Typeface.MONOSPACE
         chip.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
         chip.textSize = 11f
         chip.setPadding(dp(8), dp(3), dp(8), dp(3))
         nameRow.addView(nameLabel, LinearLayout.LayoutParams(WRAP, WRAP))
         nameRow.addView(personaLabel, LinearLayout.LayoutParams(0, WRAP, 1f))
         nameRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP))
         head.addView(nameRow, LinearLayout.LayoutParams(0, WRAP, 1f))
         box.addView(head, params(0))

         val bodyRow = LinearLayout(this)
         bodyRow.orientation = LinearLayout.HORIZONTAL
         val bar = View(this)
         bar.setBackgroundColor(PURPLE)
         out = TextView(this)
         out.text = "Toque em Falar ou escreva sua pergunta."
         out.setTextColor(Color.parseColor("#d8d0f0"))
         out.typeface = Typeface.MONOSPACE
         out.textSize = 14f
         out.setPadding(dp(10), dp(2), 0, dp(2))
         bodyRow.addView(bar, LinearLayout.LayoutParams(dp(3), MATCH))
         bodyRow.addView(out, LinearLayout.LayoutParams(0, WRAP, 1f))
         box.addView(bodyRow, params(14))

         keyField = styledEdit("Cole a chave do Groq (só uma vez)")
         keyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
         if (prefs.getString("key", "") != "") keyField.visibility = View.GONE
         box.addView(keyField, params(14))

         spinner = Spinner(this)
         adapter = object : ArrayAdapter<String>(
             this@ChatActivity, android.R.layout.simple_spinner_item, names
         ) {
             override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                 val v = super.getView(position, convertView, parent)
                 val t = v as TextView
                 t.setTextColor(Color.WHITE)
                 t.typeface = Typeface.MONOSPACE
                 return v
             }

             override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                 val v = super.getDropDownView(position, convertView, parent)
                 val t = v as TextView
                 t.setTextColor(Color.WHITE)
                 t.typeface = Typeface.MONOSPACE
                 t.setBackgroundColor(Color.parseColor("#1a1428"))
                 t.setPadding(dp(14), dp(12), dp(14), dp(12))
                 return v
             }
         }
         adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
         spinner.adapter = adapter
         val sg = GradientDrawable()
         sg.setColor(Color.parseColor("#1a1428"))
         sg.setStroke(dp(2), Color.parseColor("#4a2fa0"))
         sg.cornerRadius = dp(8).toFloat()
         spinner.background = sg
         val saved = prefs.getString("persona_name", presetNames[0]) ?: presetNames[0]
         val savedIdx = names.indexOf(saved)
         personaLabel.text = "/ " + (if (savedIdx >= 0) saved else presetNames[0]).uppercase()
         spinner.setSelection(if (savedIdx >= 0) savedIdx else 0)
         spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
             override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                 val chosen = names.getOrNull(pos) ?: return
                 personaLabel.text = "/ " + chosen.uppercase()
                 val current = prefs.getString("persona_name", presetNames[0])
                 if (chosen != current) {
                     prefs.edit().putString("persona_name", chosen).remove("hist").apply()
                 }
             }

             override fun onNothingSelected(p: AdapterView<*>?) {}
         }
         box.addView(spinner, params(10))

         ask = styledEdit("Pergunte algo à Capri")
         box.addView(ask, params(10))

         val mic = styledButton("Falar", true)
         mic.setOnClickListener { listen() }
         val send = styledButton("Perguntar", true)
         send.setOnClickListener { sendQuestion() }
         box.addView(row(mic, send), params(10))

         val facts = styledButton("Fatos sobre mim", false)
         facts.setOnClickListener { editFacts() }
         val voice = styledButton("Voz", false)
         voice.setOnClickListener { chooseVoice() }
         box.addView(row(facts, voice), params(8))

         val add = styledButton("Nova personalidade", false)
         add.setOnClickListener { newPersona() }
         val del = styledButton("Apagar", false)
         del.setOnClickListener { deletePersona() }
         box.addView(row(add, del), params(8))

         val clear = styledButton("Limpar memória", false)
         clear.setOnClickListener {
             getSharedPreferences("capri", Context.MODE_PRIVATE)
                 .edit().remove("hist").apply()
             out.text = "Memória da conversa apagada. Os fatos sobre você continuam."
         }
         box.addView(clear, params(8))

         val scroll = ScrollView(this)
         scroll.addView(box)
         setContentView(scroll)

         statusReady()
         showFace("calm")

         if (prefs.getString("key", "") != "" && savedInstanceState == null) {
             scroll.postDelayed({ listen() }, 300)
         }
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
                     out.text = "Personalidade salva: " + n + ". Toque em Voz para escolher como ela fala."
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
         if (requestCode == 7) {
             if (resultCode == RESULT_OK) {
                 val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                 if (said != null) {
                     ask.setText(said)
                     sendQuestion()
                     return
                 }
             }
             statusReady()
         }
     }

     private fun sendQuestion() {
         val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
         val typed = keyField.text.toString().trim()
         if (keyField.visibility == View.VISIBLE && typed.isNotEmpty()
