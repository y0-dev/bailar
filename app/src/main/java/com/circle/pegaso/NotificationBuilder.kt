package com.circle.pegaso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class NotificationBuilder(private val context: Context) {
    private val platformNotificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val NOW_PLAYING_CHANNEL: String = context.packageName + ".NOW_PLAYING"

    fun buildNotification(item: AudioModel, isPlaying: Boolean): NotificationCompat.Builder {
        if (shouldCreateNowPlayingChannel()) {
            createNowPlayingChannel()
        }

        val builder = NotificationCompat.Builder(context, NOW_PLAYING_CHANNEL)

        val intent = Intent(context, BackgroundSoundService::class.java)

        intent.action = "STOP"
        builder.addAction(CreateAction(R.drawable.ic_close, context.getString(R.string.notification_close), intent))
        val stopPendingIntent = PendingIntent.getService(context, 127, intent, PendingIntent.FLAG_ONE_SHOT)

        /*intent.action = "PREVIOUS"
        builder.addAction(CreateAction(R.drawable.ic_previous, context.getString(R.string.notification_skip_to_previous), intent))*/
        // TODO 3 actions max => use setCustomContentView(RemoteViews)

        if(isPlaying){
            intent.action = "PAUSE"
            builder.addAction(CreateAction(R.drawable.ic_pause, context.getString(R.string.notification_pause), intent))
        } else {
            intent.action = "PLAY"
            builder.addAction(CreateAction(R.drawable.ic_play, context.getString(R.string.notification_play), intent))
        }

        intent.action = "NEXT"
        builder.addAction(CreateAction(R.drawable.ic_next, context.getString(R.string.notification_skip_to_next), intent))

        val largeIconBitmap = if(item.album == null) BitmapFactory.decodeResource(context.resources, R.drawable.default_icon) else BitmapFactory.decodeFile(item.album)

        return builder.setContentIntent(PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT))
            .setContentText(item.name)
            .setContentTitle(item.artist)
            .setDeleteIntent(stopPendingIntent)
            .setLargeIcon(largeIconBitmap)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH) //expanded by default, not working on lock screen
            //.build()
    }

    private fun shouldCreateNowPlayingChannel() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nowPlayingChannelExists() = platformNotificationManager.getNotificationChannel(NOW_PLAYING_CHANNEL) != null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNowPlayingChannel() {
        val notificationChannel = NotificationChannel(NOW_PLAYING_CHANNEL,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = context.getString(R.string.notification_channel_description)
            }

        platformNotificationManager.createNotificationChannel(notificationChannel)
    }

    private fun CreateAction(r: Int, txt: String, intent: Intent): NotificationCompat.Action {
        val pendingIntent: PendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            pendingIntent = PendingIntent.getForegroundService(context, 127, intent, PendingIntent.FLAG_ONE_SHOT)
        else
            pendingIntent = PendingIntent.getService(context, 127, intent, PendingIntent.FLAG_ONE_SHOT)
        return NotificationCompat.Action(r, txt, pendingIntent)
    }
}