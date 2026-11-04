 package com.capri.app

 import android.app.Application
 import java.io.File

 class CrashApp : Application() {
     override fun onCreate() {
         super.onCreate()
         val old = Thread.getDefaultUncaughtExceptionHandler()
         Thread.setDefaultUncaughtExceptionHandler { t, e ->
             try {
                 File(filesDir, "crash.txt").writeText(android.util.Log.getStackTraceString(e))
             } catch (x: Exception) {
             }
             old?.uncaughtException(t, e)
         }
     }
 }
