 package com.capri.app

 import android.content.Context
 import android.graphics.Bitmap
 import android.graphics.BitmapFactory
 import android.graphics.Matrix
 import android.net.Uri
 import android.util.Base64
 import org.json.JSONArray
 import org.json.JSONObject
 import java.io.ByteArrayOutputStream
 import java.net.HttpURLConnection
 import java.net.URL

 object Vision {
     private const val MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
     private const val MAX_SIDE = 1280

     private fun loadScaled(ctx: Context, uri: Uri): Bitmap? {
         val bounds = BitmapFactory.Options()
         bounds.inJustDecodeBounds = true
         ctx.contentResolver.openInputStream(uri)?.use {
             BitmapFactory.decodeStream(it, null, bounds)
         }
         var sample = 1
         while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) {
             sample *= 2
         }
         val opts = BitmapFactory.Options()
         opts.inSampleSize = sample
         var bmp = ctx.contentResolver.openInputStream(uri)?.use {
             BitmapFactory.decodeStream(it, null, opts)
         } ?: return null

         var deg = 0
         try {
             ctx.contentResolver.openInputStream(uri)?.use {
                 val ex = android.media.ExifInterface(it)
                 deg = when (ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                     6 -> 90
                     3 -> 180
                     8 -> 270
                     else -> 0
                 }
             }
         } catch (e: Exception) {
         }

         val big = Math.max(bmp.width, bmp.height)
         val scale = if (big > MAX_SIDE) MAX_SIDE.toFloat() / big else 1f
         if (scale < 1f || deg != 0) {
             val m = Matrix()
             m.postScale(scale, scale)
             if (deg != 0) m.postRotate(deg.toFloat())
             val nb = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
             if (nb != bmp) bmp.recycle()
             bmp = nb
         }
         return bmp
     }

     fun ask(
         ctx: Context,
         uri: Uri,
         key: String,
         desc: String,
         facts: String,
         question: String,
         done: (String) -> Unit
     ) {
         Thread {
             val answer = try {
                 request(ctx, uri, key, desc, facts, question)
             } catch (e: Throwable) {
                 "[wide] Deu erro: " + e.message
             }
             done(answer)
         }.start()
     }

     private fun request(
         ctx: Context,
         uri: Uri,
         key: String,
         desc: String,
         facts: String,
         question: String
     ): String {
         val bmp = loadScaled(ctx, uri) ?: return "[wide] Deu erro: não consegui abrir a foto."
         val bos = ByteArrayOutputStream()
         bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
         val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
         bmp.recycle()

         val known = if (facts.isEmpty()) "" else
             "Fatos que você sabe sobre o usuário: " + facts.replace("\n", "; ") + ". "
         val system = "Você é Capri, uma assistente " + desc + ". " + known +
             "Você está olhando uma foto que o usuário mostrou. " +
             "Responda em português, em no máximo 4 frases. " +
             "Se ele pedir para ler o texto da imagem, transcreva o texto com fidelidade, em até 8 frases. " +
             "Comece SEMPRE com uma tag de emoção entre colchetes, " +
             "uma destas: [calm] [laugh] [smug] [aside] [look] [many] [wide]."

         val content = JSONArray()
         content.put(JSONObject().put("type", "text").put("text", question))
         content.put(
             JSONObject().put("type", "image_url").put(
                 "image_url",
                 JSONObject().put("url", "data:image/jpeg;base64," + b64)
             )
         )
         val msgs = JSONArray()
         msgs.put(JSONObject().put("role", "system").put("content", system))
         msgs.put(JSONObject().put("role", "user").put("content", content))
         val body = JSONObject()
         body.put("model", MODEL)
         body.put("messages", msgs)
         body.put("max_tokens", 600)

         val c = URL("https://api.groq.com/openai/v1/chat/completions")
             .openConnection() as HttpURLConnection
         c.requestMethod = "POST"
         c.setRequestProperty("Content-Type", "application/json")
         c.setRequestProperty("Authorization", "Bearer " + key)
         c.setRequestProperty("User-Agent", "CapriApp/1.3")
         c.doOutput = true
         c.connectTimeout = 20000
         c.readTimeout = 60000
         c.outputStream.use { it.write(body.toString().toByteArray()) }

         val code = c.responseCode
         if (code !in 200..299) {
             val err = try {
                 c.errorStream?.bufferedReader()?.readText() ?: ""
             } catch (e: Exception) {
                 ""
             }
             return "[wide] Erro " + code + ": " + err.take(140)
         }
         val txt = c.inputStream.bufferedReader().readText()
         return JSONObject(txt).getJSONArray("choices").getJSONObject(0)
             .getJSONObject("message").getString("content")
     }
 }
