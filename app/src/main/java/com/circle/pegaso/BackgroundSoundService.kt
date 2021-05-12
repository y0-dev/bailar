package com.circle.pegaso

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class BackgroundSoundService : Service() {
    var player: MediaPlayer? = null
    private val list= arrayListOf<AudioModel>()
    lateinit var item: AudioModel
    private var itemi: Int = 0
    private var listi: Int = 0
    private lateinit var n: NotificationBuilder

    inner class LocalBinder : Binder() {
        fun getService() : BackgroundSoundService {
            return this@BackgroundSoundService
        }
    }
    private val localBinder = LocalBinder()

    interface Interfacing {
        fun askChange(item: AudioModel)
        fun askPlayPause(isPlaying: Boolean)
    }
    var listener : Interfacing? = null

    override fun onBind(intent: Intent): IBinder {
        return localBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        listener = null
        startForeground(127, n.buildNotification(item, player!!.isPlaying).build())
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        n = NotificationBuilder(this)
        getPlayList(0)

        item = list[itemi]
        player = MediaPlayer.create(this, Uri.fromFile(File(item.path)))
        player!!.setOnCompletionListener {
            if(itemi != list.size) itemi++
            else itemi = 0
            change()
        }

        startForeground(127, n.buildNotification(item, false).build())


    }

    /*https://stackoverflow.com/questions/30942054/media-session-compat-not-showing-lockscreen-controls-on-pre-lollipop

    public static ComponentName getMediaButtonReceiverComponent(Context context) {
        Intent queryIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        queryIntent.setPackage(context.getPackageName());
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryBroadcastReceivers(queryIntent, 0);
        if (resolveInfos.size() == 1) {
            ResolveInfo resolveInfo = resolveInfos.get(0);
            return new ComponentName(resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name);
        } else if (resolveInfos.size() > 1) {
            Log.w(TAG, "More than one BroadcastReceiver that handles "
                    + Intent.ACTION_MEDIA_BUTTON + " was found, returning null.");
        }
        return null;
    }

    private fun lockScreenControls() {
        // Use the media button APIs (if available) to register ourselves for media button
        // events
        MediaButtonHelper.registerMediaButtonEventReceiverCompat(getSystemService(Context.AUDIO_SERVICE) as AudioManager, mMediaButtonReceiverComponent)
        // Use the remote control APIs (if available) to set the playback state
        if (mRemoteControlClientCompat == null) {
            val intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            intent.setComponent(mMediaButtonReceiverComponent);
            mRemoteControlClientCompat = new RemoteControlClientCompat(PendingIntent.getBroadcast(this /*context*/,0 /*requestCode, ignored*/, intent /*intent*/, 0 /*flags*/));
            RemoteControlHelper.registerRemoteControlClient(mAudioManager,mRemoteControlClientCompat)
        }
        mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING)
        mRemoteControlClientCompat.setTransportControlFlags(
            RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
        RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
        RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
        RemoteControlClient.FLAG_KEY_MEDIA_STOP)

        //update remote controls
        mRemoteControlClientCompat.editMetadata(true)
            .putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, "NombreArtista")
            .putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, "Titulo Album")
            .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, nombreCancion)
            //.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION,playingItem.getDuration())
            .putBitmap(RemoteControlClientCompat.MetadataEditorCompat.METADATA_KEY_ARTWORK, getAlbumArt())
            .apply();
    }
}*/

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

    fun play() {
        player!!.start()
        startForeground(127, n.buildNotification(item, true).build())
        listener?.askPlayPause(true)
    }

    fun pause() {
        player!!.pause()
        startForeground(127, n.buildNotification(item, false).build())
        listener?.askPlayPause(false)
    }

    fun isPlaying() : Boolean {
        return player!!.isPlaying
    }

    fun next() {
        if(itemi != list.size) { itemi++; change() }
    }

    fun previous() {
        if(itemi != 0) { itemi--; change() }
    }

    fun playlist(listIndex: Int, itemIndex: Int) {
        if(listi != listIndex) getPlayList(listIndex)
        itemi = itemIndex
        change()
    }

    override fun onStart(intent: Intent?, startId: Int) {}
    override fun onLowMemory() {}
    override fun onDestroy() {
        player!!.stop()
        player!!.reset()
        player!!.release()
    }

    private fun change() {
        item = list[itemi]

        if (player!!.isPlaying) player!!.stop()
        player!!.reset()
        player!!.setDataSource(item.path)
        player!!.prepare()

        startForeground(127, n.buildNotification(item, true).build())

        listener?.askChange(item)
        player!!.start()
        listener?.askPlayPause(true)

        // DONE save item in preferences recent_played
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
}
