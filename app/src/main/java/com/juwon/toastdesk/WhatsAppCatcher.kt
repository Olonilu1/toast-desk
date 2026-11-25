package com.juwon.toastdesk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppCatcher : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val body = extractBody(extras) ?: return
        if (body.length < 2) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val stored = getSharedPreferences("toastdesk.catcher", Context.MODE_PRIVATE)
        if (body == stored.getString("last", "") && !stored.getBoolean("pending", false)) return
        stored.edit()
            .putString("last", body)
            .putString("lastTitle", title)
            .putLong("lastAt", System.currentTimeMillis())
            .putBoolean("pending", true)
            .apply()
        postCatch(title, body)
    }

    private fun extractBody(extras: Bundle): String? {
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) {
            val joined = lines.joinToString("\n") { cs -> cs?.toString() ?: "" }.trim()
            if (joined.isNotBlank()) return joined
        }
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        if (!big.isNullOrBlank()) return big
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (!text.isNullOrBlank()) return text
        return null
    }

    private fun postCatch(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("catch", "Caught messages", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val label = if (title.isNotBlank() && !body.startsWith("$title:")) "$title: " else ""
        val n = Notification.Builder(this, "catch")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Toast Desk caught a message")
            .setContentText((label + body).take(140))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }
}
