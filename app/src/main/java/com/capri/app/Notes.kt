 package com.capri.app

 import android.content.Context
 import org.json.JSONArray
 import org.json.JSONObject
 import java.text.Normalizer

 object Notes {
     private const val FILE = "capri_kb"
     private const val MAX_NOTES = 500
     private val stop = setOf(
         "que", "para", "com", "uma", "uns", "umas", "dos", "das", "nos", "nas",
         "por", "pra", "como", "mais", "mas", "ela", "ele", "seu", "sua", "meu", "minha",
         "esse", "essa", "isso", "este", "esta", "isto", "foi", "sao", "ser", "tem",
         "qual", "quais", "quem", "quando", "onde", "voce", "sobre"
     )

     private fun norm(s: String): String {
         val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
         return d.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
             .replace(Regex("[^a-z0-9 ]"), " ")
             .replace(Regex("\\s+"), " ")
             .trim()
     }

     private fun stems(s: String): Set<String> {
         val out = HashSet<String>()
         for (w in norm(s).split(" ")) {
             if (w.length < 3 || w in stop) continue
             var x = w
             if (x.length > 3 && x.endsWith("s")) x = x.dropLast(1)
             if (x.length > 6) x = x.substring(0, 6)
             out.add(x)
         }
         return out
     }

     private fun load(ctx: Context): JSONArray {
         return try {
             JSONArray(
                 ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("list", "[]")
             )
         } catch (e: Exception) {
             JSONArray()
         }
     }

     private fun save(ctx: Context, arr: JSONArray) {
         ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
             .edit().putString("list", arr.toString()).apply()
     }

     private fun add(ctx: Context, text: String): Boolean {
         val t = text.trim()
         if (t.isEmpty()) return false
         val arr = load(ctx)
         val key = norm(t)
         for (i in 0 until arr.length()) {
             if (norm(arr.getJSONObject(i).getString("text")) == key) return false
         }
         arr.put(JSONObject().put("text", t.take(400)).put("t", System.currentTimeMillis()))
         val keep = JSONArray()
         val from = Math.max(0, arr.length() - MAX_NOTES)
         for (i in from until arr.length()) keep.put(arr.get(i))
         save(ctx, keep)
         return true
     }

     private fun search(ctx: Context, question: String, max: Int): List<String> {
         val q = stems(question)
         if (q.isEmpty()) return emptyList()
         val arr = load(ctx)
         val scored = ArrayList<Pair<Int, String>>()
         for (i in arr.length() - 1 downTo 0) {
             val text = arr.getJSONObject(i).getString("text")
             val hit = q.intersect(stems(text)).size
             if (hit > 0) scored.add(Pair(hit, text))
         }
         scored.sortByDescending { it.first }
         return scored.take(max).map { it.second }
     }

     fun context(ctx: Context, question: String): String {
         val found = search(ctx, question, 5)
         if (found.isEmpty()) return ""
         return "Coisas que você aprendeu e que podem ajudar (use se fizer sentido): " +
             found.joinToString("; ") + ". "
     }

     private fun forget(ctx: Context, what: String): Int {
         val q = stems(what)
         if (q.isEmpty()) return 0
         val arr = load(ctx)
         val keep = JSONArray()
         var gone = 0
         for (i in 0 until arr.length()) {
             val o = arr.getJSONObject(i)
             if (q.intersect(stems(o.getString("text"))).size >= Math.min(q.size, 2)) {
                 gone++
             } else {
                 keep.put(o)
             }
         }
         if (gone > 0) save(ctx, keep)
         return gone
     }

     fun handle(ctx: Context, original: String, low: String): String? {
         val teach = listOf(
             "aprende que ", "aprenda que ", "aprende isso: ", "aprende: ",
             "ensina que ", "saiba que ", "fica sabendo que ", "fique sabendo que "
         )
         val tp = teach.firstOrNull { low.startsWith(it) }
         if (tp != null) {
             val body = original.trim().drop(tp.length).trim().trimEnd('.', '!', ' ')
             if (body.length < 3) {
                 return "O que você quer que eu aprenda? Fale por exemplo: aprende que o aniversário da Kátia é dia 5."
             }
             return if (add(ctx, body)) "Aprendi: " + body + "." else "Isso eu já sabia."
         }

         val askStarts = listOf(
             "o que você sabe sobre ", "o que voce sabe sobre ",
             "o que você aprendeu sobre ", "o que voce aprendeu sobre ",
             "o que você sabe de ", "o que voce sabe de "
         )
         val ap = askStarts.firstOrNull { low.startsWith(it) }
         if (ap != null) {
             val about = low.removePrefix(ap).trim()
             val found = search(ctx, about, 6)
             return if (found.isEmpty()) {
                 "Não aprendi nada sobre " + about + " ainda."
             } else {
                 "Sei isto:\n" + found.joinToString("\n") { "- " + it }
             }
         }

         val listWords = listOf(
             "o que você aprendeu", "o que voce aprendeu", "o que você sabe", "o que voce sabe",
             "meu caderno", "ver o caderno", "mostra o caderno"
         )
         if (low in listWords) {
             val arr = load(ctx)
             if (arr.length() == 0) return "O caderno está vazio. Ensine algo: aprende que ..."
             val sb = StringBuilder("Tenho " + arr.length() + " anotações. As últimas:\n")
             val from = Math.max(0, arr.length() - 8)
             for (i in arr.length() - 1 downTo from) {
                 sb.append("- ").append(arr.getJSONObject(i).getString("text")).append("\n")
             }
             return sb.toString().trim()
         }

         val forgetStarts = listOf(
             "esquece ", "esqueça ", "esqueca ", "apaga do caderno ",
             "apaga o que você sabe sobre ", "apaga o que voce sabe sobre "
         )
         val fp = forgetStarts.firstOrNull { low.startsWith(it) }
         if (fp != null) {
             val what = low.removePrefix(fp).trim()
             if (what.startsWith("tudo")) {
                 save(ctx, JSONArray())
                 return "Pronto, esqueci tudo que estava no caderno."
             }
             val n = forget(ctx, what)
             return if (n == 0) {
                 "Não achei nada sobre " + what + " no caderno."
             } else {
                 "Esqueci " + n + " anotação(ões) sobre " + what + "."
             }
         }
         return null
     }
 }
