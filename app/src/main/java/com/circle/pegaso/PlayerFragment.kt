package com.circle.pegaso

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.fragment_player.view.*


class PlayerFragment : Fragment() {
    private lateinit var albumv: ImageView
    private lateinit var titlev: TextView
    private lateinit var artistv: TextView
    private lateinit var playBtnv: ImageButton

    var service: /*BackgroundSoundService*/MediaPlaybackService? = null
    private lateinit var connection: ServiceConnection

    private /*lateinit*/ var mediaBrowser: MediaBrowserCompat? = null

    //https://stackoverflow.com/questions/10000400/mediaplayer-progressbar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        albumv = view.album
        titlev = view.title
        artistv = view.artist
        playBtnv = view.playBtn

        /*playBtnv.setOnClickListener {
            if(service!!.isPlaying()) service!!.pause()
            else service!!.play()
        }*/
        view.previousBtn.setOnClickListener { MediaControllerCompat.getMediaController(this@PlayerFragment.requireActivity()).transportControls.skipToPrevious()/*service!!.previous()*/ }
        view.nextBtn.setOnClickListener { MediaControllerCompat.getMediaController(this@PlayerFragment.requireActivity()).transportControls.skipToNext()/*service!!.next()*/ }

        view.setOnClickListener {
            if(mediaBrowser != null) {
                val intent = Intent(context, DetailActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val audioListStr = context!!.getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).getString("audioList", "")
        if(audioListStr != "") {
            val audioList = Gson().fromJson<ArrayList<AudioModel>>(
                audioListStr,
                object : TypeToken<ArrayList<AudioModel>>() {}.type
            )
            if (audioList.size > 0) {
                if (!isMyServiceRunning(/*BackgroundSoundService*/MediaPlaybackService::class.java)) {
                    val service =
                        Intent(context, /*BackgroundSoundService*/MediaPlaybackService::class.java)
                    //service.action = "PLAY"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context!!.startForegroundService(
                        service
                    )
                    else context!!.startService(service)
                }
                bindService()
            } else {
                playBtnv.isEnabled = false
            }
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context!!.getSystemService(ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
//https://developer.android.com/guide/topics/media-apps/audio-app/building-a-mediabrowser-client
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // Get the token for the MediaSession
            mediaBrowser!!.sessionToken.also { token ->
                // Create a MediaControllerCompat
                val mediaController = MediaControllerCompat(this@PlayerFragment.requireActivity()/*context!!*/, token)

                // Save the controller
                MediaControllerCompat.setMediaController(this@PlayerFragment.requireActivity(), mediaController)
            }

            // Finish building the UI
            buildTransportControls()
            //Log.d("bitch", "connection ok")
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            //Log.d("bitch", "connection suspended")
            playBtnv.isEnabled = false
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
            //Log.d("bitch", "connection refused")
        }
    }

    fun buildTransportControls() {
        playBtnv.apply {
            setOnClickListener {
                val mediaController = MediaControllerCompat.getMediaController(this@PlayerFragment.requireActivity())
                val pbState = mediaController.playbackState.state
                if (pbState == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.transportControls.pause()
                } else {
                    mediaController.transportControls.play()
                }
            }
        }

        val mediaController = MediaControllerCompat.getMediaController(this@PlayerFragment.requireActivity())
        // Display the initial state
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        change(metadata)
        playPause(pbState.state == PlaybackStateCompat.STATE_PLAYING)

        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback)
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if(metadata != null) {
                change(metadata)
            }
        }
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            playPause(state!!.state == PlaybackStateCompat.STATE_PLAYING)
        }
    }

    fun change(metadata: MediaMetadataCompat) {
        albumv.setImageBitmap(metadata.description.iconBitmap)
        titlev.text = metadata.description.title
        artistv.text = metadata.description.subtitle
    }

    private fun bindService() {
        mediaBrowser = MediaBrowserCompat(this@PlayerFragment.requireActivity()/*context*/, ComponentName(/*context!!*/this@PlayerFragment.requireActivity(), MediaPlaybackService::class.java), connectionCallbacks, null)
        //Log.d("bitch", mediaBrowser.toString())
        mediaBrowser!!.connect()
    }

/*    private fun bindService() {
        val intent = Intent(context, BackgroundSoundService::class.java)
        connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val binder = service as BackgroundSoundService.LocalBinder
                this@PlayerFragment.service = binder.getService()
                change(this@PlayerFragment.service!!.item)
                playPause(this@PlayerFragment.service!!.isPlaying())
                this@PlayerFragment.service!!.listener =
                    object : BackgroundSoundService.Interfacing {
                        override fun askChange(item: AudioModel) {
                            change(item)
                        }

                        override fun askPlayPause(isPlaying: Boolean) {
                            playPause(isPlaying)
                        }
                    }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                playBtnv.isEnabled = false
            }
        }
        context?.bindService(intent, connection, Context.BIND_ABOVE_CLIENT)
    }*/

    fun playPause(isPlaying: Boolean) {
        if(isPlaying) playBtnv.setImageResource(R.drawable.ic_pause)
        else playBtnv.setImageResource(R.drawable.ic_play)
    }

    fun change(item: AudioModel) {
        if (item.album !== null) albumv.setImageURI(Uri.parse(item.album))
        else albumv.setImageResource(R.drawable.default_icon)
        titlev.text = item.name
        artistv.text = item.artist
    }

    override fun onPause() {
        /*if (service != null) {
            context?.unbindService(connection)
            service = null
        }*/
        super.onPause()
    }

    override fun onResume() {
        //bindService()//need to unregister callbacks first
        super.onResume()
    }

    override fun onDestroy() {
        if (service != null) context?.unbindService(connection)
        if (MediaControllerCompat.getMediaController(requireActivity()) != null) {
            MediaControllerCompat.getMediaController(requireActivity()).unregisterCallback(controllerCallback)
        }
        if(mediaBrowser != null) mediaBrowser!!.disconnect()
        super.onDestroy()
    }
}
