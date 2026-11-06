 package com.capri.app

 import android.content.ContentUris
 import android.content.Context
 import android.media.AudioAttributes
 import android.media.MediaPlayer
 import android.net.Uri
 import android.os.PowerManager
 import android.provider.MediaStore

 object MusicPlayer {
     private var player: MediaPlayer? = null
     private var queue = ArrayList<Pair<Uri, String>>()
     private var index = 0
     private val skip = setOf(
         "a", "o", "as", "os", "uma", "um", "da", "de", "do",
         "música", "musica", "canção", "cancao", "faixa"
     )
     private val starts = listOf(
         "toca ", "tocar ", "toque ", "põe ", "poe ", "coloca ", "colocar ", "reproduz "
     )
     private val nexts = setOf(
         "próxima", "proxima", "próxima música", "proxima musica",
         "próximo", "proximo", "pula", "pular", "avança", "avancar"
     )
     private val prevs = setOf(
         "anterior", "música anterior", "musica anterior",
         "volta", "voltar", "volta a música", "volta a musica"
     )
     private val nows = setOf(
         "o que está tocando", "o que esta tocando", "o que tá tocando", "o que ta tocando",
         "que música é essa", "que musica e essa", "qual música é essa", "qual musica e essa"
     )
     private val shuffles = setOf(
         "aleatório", "aleatorio", "modo aleatório", "modo aleatorio",
         "embaralha", "embaralhar", "música aleatória", "musica aleatoria",
         "uma música aleatória", "uma musica aleatoria"
     )
     private val alls = setOf(
         "tudo", "todas", "tudo que eu tenho", "todas as músicas", "todas as musicas",
         "todas as minhas músicas", "todas as minhas musicas"
     )
     private val somePrefixes = listOf(
         "tudo de ", "tudo do ", "tudo da ",
         "todas de ", "todas do ", "todas da ",
         "todas as de ", "todas as do ", "todas as da "
     )

     private fun kind(low: String): Pair<String, String>? {
         if (low in nexts) return Pair("next", "")
         if (low in prevs) return Pair("prev", "")
         if (low in nows) return Pair("now", "")
         if (low in shuffles) return Pair("shuffle", "")
         val start = starts.firstOrNull { low.startsWith(it) } ?: return null
         val rest = low.removePrefix(start).trim()
         if (rest.contains("aleatóri") || rest.contains("aleatori") || rest.contains("embaralh")) {
             return Pair("shuffle", "")
         }
         if (rest in alls) return Pair("all", "")
         val p = somePrefixes.firstOrNull { rest.startsWith(it) }
         if (p != null) return Pair("some", rest.removePrefix(p).trim())
         return null
     }

     fun isListCommand(low: String): Boolean = kind(low) != null

     private fun query(ctx: Context, words: List<String>, minMs: Int): ArrayList<Pair<Uri, String>> {
         val conds = ArrayList<String>()
         val args = ArrayList<String>()
         for (w in words) {
             conds.add(
                 "(" + MediaStore.Audio.Media.TITLE + " LIKE ? OR " +
                     MediaStore.Audio.Media.ARTIST + " LIKE ? OR " +
                     MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?)"
             )
             args.add("%" + w + "%")
             args.add("%" + w + "%")
             args.add("%" + w + "%")
         }
         if (minMs > 0) {
             conds.add(MediaStore.Audio.Media.DURATION + " >= ?")
             args.add(minMs.toString())
         }
         val sel = if (conds.isEmpty()) null else conds.joinToString(" AND ")
         val list = ArrayList<Pair<Uri, String>>()
         val cursor = ctx.contentResolver.query(
             MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
             arrayOf(
                 MediaStore.Audio.Media._ID,
                 MediaStore.Audio.Media.TITLE,
                 MediaStore.Audio.Media.DISPLAY_NAME
             ),
             sel,
             if (args.isEmpty()) null else args.toTypedArray(),
             MediaStore.Audio.Media.TITLE
         )
         cursor?.use { c ->
             while (c.moveToNext()) {
                 val id = c.getLong(0)
                 val title = c.getString(1) ?: c.getString(2) ?: "música"
                 val uri = ContentUris.withAppendedId(
                     MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                 )
                 list.add(Pair(uri, title))
             }
         }
         return list
     }

     private fun releasePlayer() {
         try {
             player?.setOnCompletionListener(null)
         } catch (e: Exception) {
         }
         try {
             player?.stop()
         } catch (e: Exception) {
         }
         try {
             player?.release()
         } catch (e: Exception) {
         }
         player = null
     }

     private fun playCurrent(ctx: Context): String {
         var tries = 0
         while (index < queue.size && tries < 5) {
             val item = queue[index]
             var p: MediaPlayer? = null
             try {
                 releasePlayer()
                 p = MediaPlayer()
                 p.setAudioAttributes(
                     AudioAttributes.Builder()
                         .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                         .setUsage(AudioAttributes.USAGE_MEDIA)
                         .build()
                 )
                 p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
                 p.setDataSource(ctx, item.first)
                 p.setOnCompletionListener {
                     Thread { nextSong(ctx) }.start()
                 }
                 p.prepare()
                 p.start()
                 player = p
                 return "Tocando: " + item.second + " (" + (index + 1) + " de " + queue.size + ")"
             } catch (e: Exception) {
                 try {
                     p?.release()
                 } catch (x: Exception) {
                 }
                 index++
                 tries++
             }
         }
         stop()
         return "Não consegui tocar essa música."
     }

     private fun nextSong(ctx: Context) {
         if (index + 1 < queue.size) {
             index++
             playCurrent(ctx)
         } else {
             stop()
         }
     }

     private fun startList(ctx: Context, list: ArrayList<Pair<Uri, String>>, shuffle: Boolean): String {
         if (list.isEmpty()) return "Não achei nenhuma música."
         if (shuffle) list.shuffle()
         releasePlayer()
         queue = list
         index = 0
         return playCurrent(ctx)
     }

     private fun step(ctx: Context, d: Int): String {
         if (queue.isEmpty()) return "Não tem nada tocando."
         val n = index + d
         if (n < 0) return "Essa é a primeira da lista."
         if (n >= queue.size) return "Essa é a última da lista."
         index = n
         return playCurrent(ctx)
     }

     private fun nowPlaying(): String {
         if (queue.isEmpty() || player == null) return "Não tem nada tocando agora."
         return "Agora: " + queue[index].second + " (" + (index + 1) + " de " + queue.size + ")"
     }

     fun command(ctx: Context, low: String, done: (String) -> Unit) {
         val k = kind(low) ?: return
         Thread {
             try {
                 val msg = when (k.first) {
                     "next" -> step(ctx, 1)
                     "prev" -> step(ctx, -1)
                     "now" -> nowPlaying()
                     "all" -> startList(ctx, query(ctx, emptyList(), 30000), false)
                     "shuffle" -> startList(ctx, query(ctx, emptyList(), 30000), true)
                     else -> {
                         val words = k.second.split(" ").filter { it.isNotEmpty() && it !in skip }
                         startList(ctx, query(ctx, words, 0), false)
                     }
                 }
                 done(msg)
             } catch (e: Exception) {
                 done("Não consegui: " + e.message)
             }
         }.start()
     }

     fun play(ctx: Context, wanted: String, done: (String) -> Unit) {
         Thread {
             try {
                 val words = wanted.lowercase().split(" ")
                     .map { it.trim() }
                     .filter { it.isNotEmpty() && it !in skip }
                 if (words.isEmpty()) {
                     done("Diga o nome da música.")
                     return@Thread
                 }
                 val found = query(ctx, words, 0)
                 if (found.isEmpty()) {
                     done("Não achei \"" + wanted + "\" nos seus áudios.")
                     return@Thread
                 }
                 val one = ArrayList<Pair<Uri, String>>()
                 one.add(found[0])
                 done(startList(ctx, one, false))
             } catch (e: Exception) {
                 done("Não consegui tocar: " + e.message)
             }
         }.start()
     }

     fun pause() {
         try {
             if (player?.isPlaying == true) player?.pause()
         } catch (e: Exception) {
         }
     }

     fun resume() {
         try {
             player?.start()
         } catch (e: Exception) {
         }
     }

     fun stop() {
         releasePlayer()
         queue = ArrayList()
         index = 0
     }
 }
