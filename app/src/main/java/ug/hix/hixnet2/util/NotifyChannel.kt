package ug.hix.hixnet2.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat.getSystemService
import ug.hix.hixnet2.R

class NotifyChannel {
    companion object{
        const val CHANNEL_ID = "ug.hix.hixnet2"
        const val name = "ug.hix.hixnet2"
        const val descriptionText = "HixNet2 Mesh"
        const val importance = NotificationManager.IMPORTANCE_DEFAULT

        fun createNotificationChannel(mContext: Context) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                // Register the channel with the system
                val notificationManager: NotificationManager =
                    mContext.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

    }
}