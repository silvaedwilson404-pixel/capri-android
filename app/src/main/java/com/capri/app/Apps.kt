 package com.capri.app

 import android.app.Activity
 import android.content.Intent
 import java.text.Normalizer

 object Apps {
     private val fillers = setOf(
         "o", "a", "os", "as", "app", "apps", "aplicativo", "do", "da", "de",
         "pra", "para", "mim", "favor", "por"
     )

     private class Item(val pkg: String, val label: String, val ln: String)

     private fun norm(s: String): String {
         val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
         return d.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
             .replace(Regex("[^a-z0-9 ]"), " ")
             .replace(Regex("\\s+"), " ")
             .trim()
     }

     private fun dist(a: String, b: String): Int {
         val dp = Array(a.length + 1) { IntArray(b.length + 1) }
         for (i in 0..a.length) dp[i][0] = i
         for (j in 0..b.length) dp[0][j] = j
         for (i in 1..a.length) {
             for (j in 1..b.length) {
                 val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                 dp[i][j] = Math.min(
                     Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                     dp[i - 1][j - 1] + cost
                 )
             }
         }
         return dp[a.length][b.length]
     }

     fun open(act: Activity, spoken: String): String {
         val q = norm(spoken).split(" ")
             .filter { it.isNotEmpty() && it !in fillers }
             .joinToString(" ")
         if (q.isEmpty()) return "Qual aplicativo você quer abrir?"
         val pm = act.packageManager
         val list = pm.queryIntentActivities(
             Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
         )
         val items = ArrayList<Item>()
         for (ri in list) {
             val pkg = ri.activityInfo.packageName
             if (pkg == act.packageName) continue
             val label = ri.loadLabel(pm).toString()
             val ln = norm(label)
             if (ln.isNotEmpty()) items.add(Item(pkg, label, ln))
         }
         if (items.isEmpty()) return "Não consegui ver a lista de aplicativos."

         val words = q.split(" ")
         var best: Item? = null
         var bestScore = 0
         for (it in items) {
             var score = 0
             if (it.ln == q) {
                 score = 4
             } else if (it.ln.startsWith(q)) {
                 score = 3
             } else if (it.ln.contains(q)) {
                 score = 2
             } else if (words.all { w -> it.ln.contains(w) }) {
                 score = 1
             }
             val b = best
             if (score > bestScore || (score > 0 && score == bestScore && b != null && it.ln.length < b.ln.length)) {
                 best = it
                 bestScore = score
             }
         }

         if (best == null && q.length >= 4) {
             var bestD = 99
             for (it in items) {
                 val dd = dist(q, it.ln)
                 if (dd <= Math.max(1, it.ln.length / 4) && dd < bestD) {
                     best = it
                     bestD = dd
                 }
             }
         }

         val chosen = best ?: return "Não achei nenhum app chamado " + spoken.trim() + "."
         val intent = pm.getLaunchIntentForPackage(chosen.pkg)
             ?: return "Não consegui abrir " + chosen.label + "."
         intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
         return try {
             act.startActivity(intent)
             "Abrindo " + chosen.label + "."
         } catch (e: Exception) {
             "Não consegui abrir " + chosen.label + "."
         }
     }
 }
