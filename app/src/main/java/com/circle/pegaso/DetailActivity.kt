package com.circle.pegaso

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_detail.*
import java.io.File

class DetailActivity : AppCompatActivity() {
    private /*lateinit*/ var mp: MediaPlayer? = null
    private var totalTime: Int = 0
    private var audioList: List<AudioModel> = ArrayList<AudioModel>()
    private var i: Int = -1

    //https://github.com/mrolcsi/lyricsplayer.android
    //https://github.com/authorfu/LrcParser

    //var service: BackgroundSoundService? = null
    private /*lateinit*/ var mediaBrowser: MediaBrowserCompat? = null
    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if(metadata != null) {
                change(metadata)
            }
        }
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            playPause(state!!.state == PlaybackStateCompat.STATE_PLAYING)
            //TODO positionBar.progress = state.position.toInt()
        }
    }
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser!!.sessionToken.also { token ->
                val mediaController = MediaControllerCompat(this@DetailActivity, token)
                MediaControllerCompat.setMediaController(this@DetailActivity, mediaController)
                mediaController.registerCallback(controllerCallback)
                change(mediaController.metadata)
                playPause(mediaController.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                //TODO changemode()
            }
        }
        override fun onConnectionSuspended() {}
        override fun onConnectionFailed() {}
    }

    private var mode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        if(intent.hasExtra("id")) {
            i = intent.getIntExtra("id", -1)

            val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
            val audioListStr = prefs.getString("audioList", "")
            //val person = Gson().fromJson(audioList, AudioModel::class.java)
            val myType = object : TypeToken<List<AudioModel>>() {}.type
            audioList = Gson().fromJson<List<AudioModel>>(audioListStr, myType)

            val item = audioList[i]

            title = item.name
            name.text = item.name
            artist.text = item.artist
            if(item.album != null) cover.setImageURI(Uri.parse(item.album))

            mp = MediaPlayer.create(this, Uri.fromFile(File(item.path)))//Uri.parse(audioList[1].path) Couldn't open uri in attemptDataSource
            //mp.isLooping = true
            //mp.setVolume(0.5f, 0.5f)
            totalTime = mp!!.duration

            // Position Bar
            positionBar.max = totalTime
            positionBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mp!!.seekTo(progress)
                        }
                    }
                    override fun onStartTrackingTouch(p0: SeekBar?) {}
                    override fun onStopTrackingTouch(p0: SeekBar?) {}
                }
            )

            @SuppressLint("HandlerLeak")
            val handler = object : Handler() {
                override fun handleMessage(msg: Message) {
                    val currentPosition = msg.what
                    // Update positionBar
                    positionBar.progress = currentPosition
                }
            }

            // Thread
            Thread(Runnable {
                while (mp != null) {
                    try {
                        val msg = Message()
                        msg.what = mp!!.currentPosition
                        handler.sendMessage(msg)
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) { }
                }
            }).start()


            playBtnClick(null)

            mp!!.setOnCompletionListener {
                if (mode == 0) {
                    if (i != audioList.size) i++
                    else i = 0
                    change(audioList[i])
                    mp!!.reset()
                    mp!!.setDataSource(audioList[i].path)
                    mp!!.prepare()
                    mp!!.start()
                    positionBar.max = mp!!.duration
                } else if (mode == 1) {
                    mp!!.seekTo(0)
                    mp!!.start()
                } else {
                    i = ((Math.random() * (audioList.size + 1)).toInt())
                    change(audioList[i])
                    positionBar.max = totalTime
                    mp!!.reset()
                    mp!!.setDataSource(audioList[i].path)
                    mp!!.prepare()
                    mp!!.start()
                    positionBar.max = mp!!.duration
                }
            }
        }//else finish()
        else if(Intent.ACTION_VIEW == intent.action) {//Open an audio file with this app
            val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
            val audioListStr = prefs.getString("audioList", "")
            val myType = object : TypeToken<List<AudioModel>>() {}.type
            audioList = Gson().fromJson<List<AudioModel>>(audioListStr, myType)

            val data = intent.data
            if (data != null) {
                val scheme = data.scheme
                val filename = if ("file" == scheme || "content" == scheme) data.path else data.toString()

                var found = false
                for (x in audioList) {
                    if(x.path == filename) {
                        found = true
                        i = audioList.indexOf(x)
                    }
                }

                if(found) {
                    val item = audioList[i]
                    title = item.name
                    name.text = item.name
                    artist.text = item.artist
                    if(item.album != null) cover.setImageURI(Uri.parse(item.album))

                    mp = MediaPlayer.create(this, Uri.fromFile(File(item.path)))
                    totalTime = mp!!.duration

                    positionBar.max = totalTime
                    positionBar.setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean
                            ) {
                                if (fromUser) {
                                    mp!!.seekTo(progress)
                                }
                            }

                            override fun onStartTrackingTouch(p0: SeekBar?) {}
                            override fun onStopTrackingTouch(p0: SeekBar?) {}
                        }
                    )

                    @SuppressLint("HandlerLeak")
                    val handler = object : Handler() {
                        override fun handleMessage(msg: Message) {
                            val currentPosition = msg.what
                            // Update positionBar
                            positionBar.progress = currentPosition
                        }
                    }

                    // Thread
                    Thread(Runnable {
                        while (mp != null) {
                            try {
                                val msg = Message()
                                msg.what = mp!!.currentPosition
                                handler.sendMessage(msg)
                                Thread.sleep(1000)
                            } catch (e: InterruptedException) {
                            }
                        }
                    }).start()

                    playBtnClick(null)
                } else AlertDialog.Builder(this)
                    .setTitle("Not found")
                    .setMessage(filename + " not found" + data.path)
                    .show()
            }
        }
        //else bind to service and show service item and progress
        else{
            mediaBrowser = MediaBrowserCompat(this@DetailActivity, ComponentName(this@DetailActivity, MediaPlaybackService::class.java), connectionCallbacks, null)
            mediaBrowser!!.connect()

            /*val s = Intent(applicationContext, BackgroundSoundService::class.java)
            //service.action = "PLAY"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(s)
            else startService(s)*/

            //bindService()//service is null
            //totalTime = service!!.player!!.duration // KotlinNullPointerException

            // Position Bar
            /*positionBar.max = totalTime
            positionBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            service!!.player!!.seekTo(progress)
                        }
                    }
                    override fun onStartTrackingTouch(p0: SeekBar?) {}
                    override fun onStopTrackingTouch(p0: SeekBar?) {}
                }
            )

            @SuppressLint("HandlerLeak")
            val handler = object : Handler() {
                override fun handleMessage(msg: Message) {
                    val currentPosition = msg.what
                    // Update positionBar
                    positionBar.progress = currentPosition
                }
            }

            Thread(Runnable {
                while (service!!.player!! != null) {
                    try {
                        val msg = Message()
                        msg.what = service!!.player!!.currentPosition
                        handler.sendMessage(msg)
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                    }
                }
            }).start()*/
        }
    }

    /*private fun bindService() {
        val intent = Intent(applicationContext, BackgroundSoundService::class.java)//not same intent because of context?
        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val binder = service as BackgroundSoundService.LocalBinder
                this@DetailActivity.service = binder.getService()
                change(this@DetailActivity.service!!.item)
                this@DetailActivity.service!!.listener = object : BackgroundSoundService.Interfacing {
                        override fun askChange(item: AudioModel) {
                            change(item)
                        }
                        override fun askPlayPause(isPlaying: Boolean) {
                            //playPause(isPlaying)
                        }
                    }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        bindService(intent, connection, Context.BIND_ABOVE_CLIENT)
    }*/

    fun change(item: AudioModel) {
        title = item.name
        name.text = item.name
        artist.text = item.artist
        if(item.album != null) cover.setImageURI(Uri.parse(item.album))
        else cover.setImageResource(R.drawable.default_icon)
    }

    fun change(metadata: MediaMetadataCompat) {
        cover.setImageBitmap(metadata.description.iconBitmap)
        title = metadata.description.title
        name.text = metadata.description.title
        artist.text = metadata.description.subtitle
    }

    fun playPause(isPlaying: Boolean) {
        if(isPlaying) playBtn.setImageResource(R.drawable.ic_pause)
        else playBtn.setImageResource(R.drawable.ic_play)
    }

    fun playBtnClick(v: View?) {
        val mediaController = MediaControllerCompat.getMediaController(this@DetailActivity)
        if (mediaController != null) {
            if (mediaController.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController.transportControls.pause()
            } else {
                mediaController.transportControls.play()
            }
        }else{
        if (mp!!.isPlaying) {
            // Stop
            mp!!.pause()
            //playBtn.setBackgroundResource(android.R.drawable.ic_media_play)
            playBtn.setImageResource(R.drawable.ic_play)
        } else {
            // Start
            mp!!.start()
            //playBtn.setBackgroundResource(android.R.drawable.ic_media_pause)
            playBtn.setImageResource(R.drawable.ic_pause)
        }}
    }

    fun previous(v: View?) {
        if (MediaControllerCompat.getMediaController(this@DetailActivity) != null) {
            MediaControllerCompat.getMediaController(this@DetailActivity).transportControls.skipToPrevious()
        }else{
        if(i != 0) {
            i--
            change(i)
        }}
    }

    fun next(v: View?) {
        if (MediaControllerCompat.getMediaController(this@DetailActivity) != null) {
            MediaControllerCompat.getMediaController(this@DetailActivity).transportControls.skipToNext()
        }else{
        if(i != audioList.size) {
            i++
            change(i)
        }}
    }

    private fun change(i: Int) {
        try {
            val item = audioList[i]

            if (mp!!.isPlaying) mp!!.stop()
            else playBtn.setImageResource(R.drawable.ic_pause)
            mp!!.reset()
            mp!!.setDataSource(item.path)
            mp!!.prepare()

            title = item.name
            name.text = item.name
            artist.text = item.artist
            if (item.album == null)cover.setImageResource(R.drawable.default_icon)
            else cover.setImageURI(Uri.parse(item.album))

            mp!!.start()
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

    fun addtoPlaylist(v: View) {
        val id: String
        val name: String
        if (MediaControllerCompat.getMediaController(this@DetailActivity) != null) {
            val mc = MediaControllerCompat.getMediaController(this@DetailActivity).metadata.description
            id = mc.mediaId!!
            name = mc.title!!.toString()
        } else {
            id = audioList[this.i].id
            name = audioList[this.i].name
        }

        val playlistStr = applicationContext.getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).getString("playlists", "")
        val type = object : TypeToken<ArrayList<ListModel>>() {}.type
        val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistStr, type)
        val titles = arrayListOf<String>()
        val originalSelected = arrayListOf<Boolean>()
        for (i in playlists) {
            if (playlists.indexOf(i) != 0) {
                titles.add(i.title)
                originalSelected.add(i.musics.contains(id/*audioList[this.i].id*/))
            }
        }
        val selectedList = originalSelected.toBooleanArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.add_item)
        builder.setMultiChoiceItems(titles.toTypedArray(), selectedList) { _, which, isChecked -> selectedList[which] = isChecked }
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dialog.dismiss()
            for ((index, value) in selectedList.withIndex()) {
                if (value && !originalSelected[index]) {//element selected
                    Toast.makeText(this, name/*audioList[this.i].name*/ + " " + getString(R.string.added_to) + " " + titles[index], Toast.LENGTH_SHORT).show()
                    playlists[index + 1].musics.add(id/*audioList[this.i].id*/)
                } else if (originalSelected[index] && !value) {//element unselected
                    Toast.makeText(this, name + " " + getString(R.string.removed_from) + " " + titles[index], Toast.LENGTH_SHORT).show()
                    playlists[index + 1].musics.remove(id)
                }
            }
            getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).edit().putString("playlists", Gson().toJson(playlists)).apply()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    fun lyrics(v: View) {
        AlertDialog.Builder(this)
            .setTitle("Not yet implemented")
            .setMessage("Lyrics soon")
            .show()
    }

    fun mode(v: View) {
        mode = when (mode) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        val icons = arrayOf(R.drawable.ic_arrow_forward, R.drawable.ic_repeat, R.drawable.ic_shuffle)
        modeicon.setImageResource(icons[mode])

        val mc = MediaControllerCompat.getMediaController(this@DetailActivity)
        if (mc != null) {
            if(mode == 0 || mode == 1) mc.transportControls.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
            else if(mode == 2) mc.transportControls.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        if(mp != null && mp!!.isPlaying) mp!!.stop()
        if (MediaControllerCompat.getMediaController(this@DetailActivity) != null) {
            MediaControllerCompat.getMediaController(this@DetailActivity).unregisterCallback(controllerCallback)
        }
        if(mediaBrowser != null)mediaBrowser!!.disconnect()
        super.onDestroy()
    }
}
