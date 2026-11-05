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
     private val skip = setOf(
         "a", "o", "as", "os", "uma", "um", "da", "de", "do",
         "música", "musica", "canção", "cancao", "faixa"
     )

     private fun find(ctx: Context, wanted: String): Pair<Uri, String>? {
         val words = wanted.lowercase().split(" ")
             .map { it.trim() }
             .filter { it.isNotEmpty() && it !in skip }
         if (words.isEmpty()) return null
         val cond = words.joinToString(" AND ") {
             "(" + MediaStore.Audio.Media.TITLE + " LIKE ? OR " +
                 MediaStore.Audio.Media.ARTIST + " LIKE ? OR " +
                 MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?)"
         }
         val args = ArrayList<String>()
         for (w in words) {
             args.add("%" + w + "%")
             args.add("%" + w + "%")
             args.add("%" + w + "%")
         }
         val cursor = ctx.contentResolver.query(
             MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
             arrayOf(
                 MediaStore.Audio.Media._ID,
                 MediaStore.Audio.Media.TITLE,
                 MediaStore.Audio.Media.DISPLAY_NAME
             ),
             cond,
             args.toTypedArray(),
             MediaStore.Audio.Media.TITLE
         )
         cursor?.use { c ->
             if (c.moveToFirst()) {
                 val id = c.getLong(0)
                 val title = c.getString(1) ?: c.getString(2) ?: "música"
                 val uri = ContentUris.withAppendedId(
                     MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                 )
                 return Pair(uri, title)
             }
         }
         return null
     }

     fun play(ctx: Context, wanted: String, done: (String) -> Unit) {
         Thread {
             try {
                 val found = find(ctx, wanted)
                 if (found == null) {
                     done("Não achei \"" + wanted + "\" nos seus áudios.")
                     return@Thread
                 }
                 stop()
                 val p = MediaPlayer()
                 p.setAudioAttributes(
                     AudioAttributes.Builder()
                         .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                         .setUsage(AudioAttributes.USAGE_MEDIA)
                         .build()
                 )
                 p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
                 p.setDataSource(ctx, found.first)
                 p.setOnCompletionListener { stop() }
                 p.prepare()
                 p.start()
                 player = p
                 done("Tocando: " + found.second)
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
 }
