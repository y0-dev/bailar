package com.circle.pegaso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

//https://github.com/vpaliy/android-music-app/tree/master/app/src/main/java/com/vpaliy/mediaplayer/playback
//https://developer.android.com/guide/topics/media-apps/audio-app/building-a-mediabrowserservice
//https://github.com/googlearchive/android-MediaBrowserService/blob/master/Application/src/main/java/com/example/android/mediasession/service/notifications/MediaNotificationManager.java

private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"

class MediaPlaybackService : MediaBrowserServiceCompat() {

    private var mediaSession: MediaSessionCompat? = null
    private var player: MediaPlayer? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private val list= arrayListOf<AudioModel>()
    private var itemi: Int = 0
    private var listi: Int = 0
    private var mode: Int = 0
    private lateinit var afr: AudioFocusRequest
    private lateinit var NOW_PLAYING_CHANNEL: String

    private fun playlist(listIndex: Int, itemIndex: Int) {
        if(listi != listIndex) getPlayList(listIndex)
        itemi = itemIndex
        change()
    }

    //keep for Startservice playlist action
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if(intent.action != null) {
                when (intent.action) {
                    "PLAY" -> play()
                    "PAUSE" -> pause()
                    "NEXT" -> next()
                    "PREVIOUS" -> previous()
                    "STOP" -> { stopForeground(true); stopSelf() }
                    "PLAYLIST" -> playlist(intent.getIntExtra("id", 0), 0)
                    else -> print(intent.action)
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        NOW_PLAYING_CHANNEL = baseContext.packageName + ".NOW_PLAYING"
        if (shouldCreateNowPlayingChannel()) {
            createNowPlayingChannel()
        }

        getPlayList(0)

        //https://stackoverflow.com/questions/6577646/what-is-audio-focus-in-android-class-audiomanager
        //https://developer.android.com/guide/topics/media-apps/audio-focus
        val am: AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    mediaSession!!.controller.transportControls.stop()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    mediaSession!!.controller.transportControls.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    player!!.setVolume(0.5F, 0.5F)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    player!!.setVolume(1F, 1F)
                    if (!player!!.isPlaying) mediaSession!!.controller.transportControls.play()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(focusChangeListener, Handler())
                build()
            }
            val result: Int = am.requestAudioFocus(afr)
        }

        player = MediaPlayer.create(this, Uri.fromFile(File(list[itemi].path)))
        player!!.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        player!!.setOnBufferingUpdateListener { _: MediaPlayer, i: Int ->
            stateBuilder.setBufferedPosition(i.toLong())
            //mediaSession!!.setPlaybackState(stateBuilder.build())
        }
        player!!.setOnCompletionListener {
            when (mode) {
                0 -> {
                    if (itemi != list.size - 1) itemi++
                    else itemi = 0
                    change()
                }
                1 -> {
                    player!!.seekTo(0)
                    play()
                }
                else -> {
                    itemi = ((Math.random() * (list.size /*+ 1*/)).toInt())
                    change()
                }
            }
        }

        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, TAG).apply {

            // Enable callbacks from MediaButtons and TransportControls
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SEEK_TO
                        or PlaybackStateCompat.ACTION_SET_REPEAT_MODE or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                        or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE
                        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            setPlaybackState(stateBuilder.build())

            // MySessionCallback() has methods that handle callbacks from a media controller
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    //Log.d(TAG, "onMediaButtonEvent called: $mediaButtonIntent")
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onPause() {
                    //Log.d(TAG, "onPause called (media button pressed)")
                    pause()
                    super.onPause()
                }

                override fun onPlay() {
                    //Log.d(TAG, "onPlay called (media button pressed)")
                    play()
                    super.onPlay()
                }

                override fun onStop() {
                    //Log.d(TAG, "onStop called (media button pressed)")
                    //TODO not stopping
                    //this@MediaPlaybackService.mediaSession!!.release()
                    this@MediaPlaybackService.stopSelf()
                    super.onStop()
                }

                override fun onSkipToPrevious() {
                    previous()
                    super.onSkipToPrevious()
                }

                override fun onSkipToNext() {
                    next()
                    super.onSkipToNext()
                }

                override fun onSeekTo(pos: Long) {
                    player!!.seekTo(pos.toInt())
                    super.onSeekTo(pos)
                }

                override fun onSetRepeatMode(repeatMode: Int) {
                    mode = if(mode == 1) 0 else 1
                    setMeta()
                    super.onSetRepeatMode(repeatMode)
                }

                override fun onSetShuffleMode(shuffleMode: Int) {
                    mode = 2
                    setMeta()
                    super.onSetShuffleMode(shuffleMode)
                }

                override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) {
                    playlist(description!!.title.toString().toInt(), index)
                    super.onAddQueueItem(description, index)
                }
            })
            isActive = true

            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)
        }

        setMeta()
    }

    override fun onDestroy() {
        mediaSession!!.release()
        player!!.stop()
        player!!.reset()
        player!!.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(afr)
        }
        stopForeground(true)
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        return if (clientPackageName != packageName) {
            MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null)
        } else MediaBrowserServiceCompat.BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentMediaId: String, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        if (MY_EMPTY_MEDIA_ROOT_ID == parentMediaId) {
            result.sendResult(null)
            return
        }

        // Assume for example that the music catalog is already loaded/cached.
        var mediaItems = emptyList<MediaBrowserCompat.MediaItem>()

        // Check if this is the root menu:
        if (MY_MEDIA_ROOT_ID == parentMediaId) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...

            /*val list = arrayListOf<MediaBrowserCompat.MediaItem>()
            for(x in audioList) {
                list.add(MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(x.id)
                        .setTitle(x.name)
                        .setSubtitle(x.artist)
                        //.setDescription(getString(R.string.notification_channel))
                        //.setExtras(songDuration)
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            mediaItems = list.toList()*/

        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
        }
        result.sendResult(mediaItems)
    }

    private fun play() {
        player!!.start()
        setMeta()
    }

    private fun pause() {
        player!!.pause()
        setMeta()
    }

    private fun next() {
        if(itemi != list.size) { itemi++; change() }
    }

    private fun previous() {
        if(itemi != 0) { itemi--; change() }
    }

    private fun change() {
        val item = list[itemi]

        if (player!!.isPlaying) player!!.stop()
        player!!.reset()
        player!!.setDataSource(item.path)
        player!!.prepare()

        player!!.start()
        setMeta()

        // save item in preferences recent_played
        val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
        val recentsStr = prefs.getString("recent_played", "")
        val recents: ListModel
        if(recentsStr == "") {
            recents = ListModel("Recent")
            recents.musics.add(list[itemi].id)
        } else {
            recents = Gson().fromJson<ListModel>(recentsStr, object : TypeToken<ListModel>() {}.type)
            if(recents.musics.contains(list[itemi].id)) recents.musics.remove(list[itemi].id)
            recents.musics.add(list[itemi].id)
            if (recents.musics.size > 5) recents.musics.removeAt(0)
        }
        prefs.edit().putString("recent_played", Gson().toJson(recents)).apply()
    }

    private fun setMeta() {
        val item = list[itemi]
        val largeIconBitmap = if(item.album == null) BitmapFactory.decodeResource(baseContext.resources, R.drawable.default_icon) else BitmapFactory.decodeFile(item.album)
        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, item.id)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, item.name)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, item.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, getString(R.string.notification_channel))
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIconBitmap)
        if(player!!.isPlaying) meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player!!.duration.toLong())
        val description = meta.build().description
        mediaSession!!.setMetadata(meta.build())

        stateBuilder.setState(if(player!!.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, player!!.currentPosition.toLong(), 1.0.toFloat())
        mediaSession!!.setPlaybackState(stateBuilder.build())

        val builder = NotificationCompat.Builder(this, baseContext.packageName + ".NOW_PLAYING").apply {
            // Add the metadata for the currently playing track
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)

            // Enable launching the player by clicking the notification
            setContentIntent(mediaSession!!.controller.sessionActivity)

            // Stop the service when the notification is swiped away
            setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_STOP))

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            // Be careful about the color
            setSmallIcon(R.drawable.ic_notification)
            color = ContextCompat.getColor(baseContext, R.color.colorAccent)

            // developer.android.com/guide/topics/media-apps/mediabuttons
            // Add a previous button
            addAction(NotificationCompat.Action(R.drawable.ic_previous, getString(R.string.notification_skip_to_previous), MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))

            if(player!!.isPlaying){
                // Add a pause button
                addAction(NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.notification_pause), MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_PAUSE)))
            } else {
                // Add a play button
                addAction(NotificationCompat.Action(R.drawable.ic_play, getString(R.string.notification_play), MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_PLAY)))//ACTION_PLAY_PAUSE
            }

            // Add a next button
            addAction(NotificationCompat.Action(R.drawable.ic_next, getString(R.string.notification_skip_to_next), MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))

            /*when (mode) {
                0 -> {
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                    val keyCode = PlaybackStateCompat.toKeyCode(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)
                    intent.setComponent(MediaButtonReceiver.getMediaButtonReceiverComponent(this@MediaPlaybackService))
                    intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    addAction(R.drawable.ic_shuffle, "no", PendingIntent.getBroadcast(this@MediaPlaybackService, keyCode, intent, 0))
                    //addAction(NotificationCompat.Action(R.drawable.ic_shuffle, "no", MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, MediaButtonReceiver.getMediaButtonReceiverComponent(this@MediaPlaybackService), PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)))
                }
                1 -> {
                    addAction(NotificationCompat.Action(R.drawable.ic_repeat, "no", MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_SET_REPEAT_MODE)))
                }
                else -> {
                    addAction(NotificationCompat.Action(R.drawable.ic_arrow_forward, "no", MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_SET_REPEAT_MODE)))
                }
            }*/

            // Add a close button
            addAction(NotificationCompat.Action(R.drawable.ic_close, getString(R.string.notification_close), MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_STOP)))

            // Take advantage of MediaStyle features
            setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession!!.sessionToken)
                .setShowActionsInCompactView(1, 2, 3)//set default action(s) index in not expanded view and lock screen

                // Add a cancel button
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(baseContext, PlaybackStateCompat.ACTION_STOP))
            )

            // Remove time of creation
            setShowWhen(false)

        }

        // Display the notification and place the service in the foreground
        startForeground(127, builder.build())
    }

    private fun getPlayList(i: Int) {
        listi = i
        val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
        val playlistsStr = prefs.getString("playlists", "")
        val audioListStr = prefs.getString("audioList", "")
        val audioList = Gson().fromJson<ArrayList<AudioModel>>(audioListStr, object : TypeToken<ArrayList<AudioModel>>() {}.type)
        val playlist = Gson().fromJson<ArrayList<ListModel>>(playlistsStr, object : TypeToken<ArrayList<ListModel>>() {}.type)[listi]

        list.clear()
        for(x in playlist.musics) {
            val e = audioList.find { e -> e.id == x }
            if(e != null) list.add(e)
        }
    }

    companion object {
        private val TAG = MediaPlaybackService::class.java.simpleName
    }

    private fun shouldCreateNowPlayingChannel() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nowPlayingChannelExists() = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(NOW_PLAYING_CHANNEL) != null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNowPlayingChannel() {
        val notificationChannel = NotificationChannel(NOW_PLAYING_CHANNEL,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = getString(R.string.notification_channel_description)
            }

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(notificationChannel)
    }
}