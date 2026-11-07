 package com.capri.app

 import android.app.Notification
 import android.content.Context
 import android.content.pm.ApplicationInfo
 import android.os.Bundle
 import android.service.notification.NotificationListenerService
 import android.service.notification.StatusBarNotification

 class NotifListener : NotificationListenerService() {
     private var lastText = ""
     private var lastAt = 0L
     private val skipCats = setOf(
         Notification.CATEGORY_CALL,
         Notification.CATEGORY_TRANSPORT,
         Notification.CATEGORY_PROGRESS,
         Notification.CATEGORY_SERVICE,
         Notification.CATEGORY_SYSTEM,
         Notification.CATEGORY_STATUS
     )

     private fun appName(ex: Bundle, pkg: String): String {
         try {
             val ai = ex.getParcelable<ApplicationInfo>("android.appInfo")
             if (ai != null) return packageManager.getApplicationLabel(ai).toString()
         } catch (e: Exception) {
         }
         return pkg.substringAfterLast('.')
     }

     override fun onNotificationPosted(sbn: StatusBarNotification?) {
         try {
             if (sbn == null) return
             val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
             if (!prefs.getBoolean("notif_read", false)) return
             if (sbn.packageName == packageName) return
             if (sbn.isOngoing) return
             val n = sbn.notification
             if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
             val cat = n.category
             if (cat != null && cat in skipCats) return
             val f = currentInterruptionFilter
             if (f > INTERRUPTION_FILTER_ALL) return

             val ex = n.extras
             val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
             val body = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
             if (title.isEmpty() && body.isEmpty()) return
             val app = appName(ex, sbn.packageName)
             val head = if (title.isNotEmpty()) "$app: $title." else "$app."
             val spoken = (head + " " + body).trim().take(200)

             val now = System.currentTimeMillis()
             if (spoken == lastText && now - lastAt < 15000) return
             lastText = spoken
             lastAt = now

             Speaker.toCapri(applicationContext, "look", spoken)
             Speaker.say(applicationContext, spoken)
         } catch (e: Exception) {
         }
     }
 }
