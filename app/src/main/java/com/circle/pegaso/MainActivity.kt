package com.circle.pegaso

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.database.Cursor
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebViewFragment
import android.widget.AdapterView
import android.widget.EditText
import android.widget.SimpleAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
import androidx.recyclerview.widget.LinearLayoutManager
import com.circle.pegaso.Adapter.Interfacing
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_ID = 1
    private val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)

        checkPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            //R.id.action_settings -> true
            R.id.action_search -> {
                startActivity(Intent(this, SearchActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun newPlaylist(view: View) {
        val name = EditText(this)
        name.hint = getString(R.string.name)
        name.setOnFocusChangeListener { _, _ ->
            name.post(Runnable {
                val inputMethodManager: InputMethodManager = this@MainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
            })
        }
        name.requestFocus()

        val builder = AlertDialog.Builder(this)
        with(builder)
        {
            setTitle(R.string.create)
            setView(name)
            setPositiveButton(android.R.string.ok) { _, _ -> newPlaylist(name.text.toString()) }
            setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            show()
        }
    }

    private fun newPlaylist(name: String) {
        if(name == "")
            Snackbar.make(listview, R.string.empty, Snackbar.LENGTH_LONG).show()
        else {
            val playlistStr = prefs.getString("playlists", "")
            val myType = object : TypeToken<ArrayList<ListModel>>() {}.type
            val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistStr, myType)

            var alreadyExists = false
            for (p in playlists) if (p.title.equals(name)) {
                alreadyExists = true
                break
            }
            if (alreadyExists)
                Snackbar.make(listview, R.string.exist, Snackbar.LENGTH_LONG).show()
            else {
                Snackbar.make(listview, R.string.created, Snackbar.LENGTH_SHORT).show()
                playlists.add(ListModel(name, arrayListOf()))
                prefs.edit().putString("playlists", Gson().toJson(playlists)).apply()
                loadPlaylists(playlists)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.circle.pegaso/play/list?id=" + (playlists.size - 1)), this, StartService::class.java)
                    val shortcutManager = getSystemService<ShortcutManager>(ShortcutManager::class.java)
                    val shortcut = ShortcutInfo.Builder(this, name)
                        .setShortLabel(getString(R.string.notification_play) + " " + name)
                        .setLongLabel(getString(R.string.notification_play) + " " + name)
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_playlist_play))
                        .setIntent(intent)
                        .build()
                    //shortcutManager!!.dynamicShortcuts = arrayListOf(shortcut)
                    shortcutManager!!.addDynamicShortcuts(arrayListOf(shortcut))
                }
            }
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSIONS_ID)
        else loadView()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        checkPermission()
    }

    override fun onResume() {
        super.onResume()
        val playlistStr = prefs.getString("playlists", "")
        if(playlistStr != "") {//when ask for permission denied
            val myType = object : TypeToken<ArrayList<ListModel>>() {}.type
            val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistStr, myType)
            loadPlaylists(playlists)
        }
    }

    private fun loadView() {
        val audioArray = getAllAudioFromDevice(this)

        val recentsStr = prefs.getString("recent_played", "")
        val recents = if(recentsStr == "") ListModel("Recent") else Gson().fromJson<ListModel>(recentsStr, object : TypeToken<ListModel>() {}.type)
        if(recents.musics.size != 0) {//TODO show something at first start?
            val list = arrayListOf<AudioModel>()
            for (x in recents.musics) {
                val e = audioArray.find { e -> e.id == x }
                if (e == null) {
                    recents.musics.remove(x)
                    prefs.edit().putString("recent_played", Gson().toJson(recents)).apply()
                } else list.add(e)
            }
            list.reverse()//make more recent first

            listview.layoutManager = LinearLayoutManager(this)
            listview.adapter = Adapter(list.toTypedArray(),//audioArray.toTypedArray().sliceArray(20)
                    object : Interfacing {
                        override fun onItemClick(item: AudioModel) {
                            /*val intent = Intent(applicationContext, DetailActivity::class.java)
                            intent.putExtra("id", audioArray.indexOf(item))
                           startActivity(intent)*/
                            MediaControllerCompat.getMediaController(this@MainActivity).addQueueItem(MediaMetadataCompat.Builder()
                                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "0").build().description, audioArray.indexOf(item))
                        }

                        override fun askloadPlaylists(item: ArrayList<ListModel>) {
                            loadPlaylists(item)
                        }
                    }, 0)
        }

        val items = arrayListOf<String>()
        for(x in audioArray)
            items.add(x.id)

        val playlistStr = prefs.getString("playlists", "")
        var playlists = arrayListOf<ListModel>()
        if(playlistStr == ""){
            playlists.add(ListModel(getString(R.string.all), items))
        } else {
            val myType = object : TypeToken<ArrayList<ListModel>>() {}.type
            playlists = Gson().fromJson(playlistStr, myType)
            playlists.get(0).musics = items
        }
        prefs.edit().putString("playlists", Gson().toJson(playlists)).apply()

        loadPlaylists(playlists)

        //startService(Intent(this, MediaPlaybackService::class.java))
    }

    private fun loadPlaylists(playlists: ArrayList<ListModel>) {
        val values = ArrayList<HashMap<String,String>>()
        for(i in playlists) values.add(hashMapOf("title" to i.title, "des" to i.musics.size.toString() + " " + getString(R.string.songs)))
        val adapter = SimpleAdapter(this, values, android.R.layout.simple_list_item_2, arrayOf("title", "des"), intArrayOf(android.R.id.text1, android.R.id.text2))

        playlistview.adapter = adapter
        playlistview.onItemClickListener = AdapterView.OnItemClickListener {
                _, _, i, _ ->
            val intent = Intent(applicationContext, ListActivity::class.java)
            intent.putExtra("id", i)
            startActivity(intent)
        }
    }

    //https://stackoverflow.com/questions/39461954/list-all-mp3-files-in-android
    private fun getAllAudioFromDevice(context: Context): ArrayList<AudioModel> {
        val audioList: ArrayList<AudioModel> = ArrayList()
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.AudioColumns.DATA,
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ALBUM_ID,
            MediaStore.Audio.AudioColumns._ID // ID changed when file moved or file name changed TODO find better ID
           /*MediaStore.Audio.Media.TITLE_KEY,MediaStore.Audio.AlbumColumns.ALBUM_ART//https://stackoverflow.com/questions/43367182/mediastore-function-cant-find-all-songs*/
        )
        val c: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        if (c != null) {
            while (c.moveToNext()) {
                val path: String = c.getString(0)

                var name = c.getStringOrNull(1)
                if(name == null) {
                    name = path.substring(path.lastIndexOf('/') + 1)//get basename by removing path
                    name =  name.substring(0, name.lastIndexOf('.'))//remove extension
                }

                var artist = c.getStringOrNull(2)
                if(artist == null) artist = "<unknown>"

                //get album cover
                var artpath: String? = null
                val cursor = contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                    MediaStore.Audio.Albums._ID + "=?", arrayOf(java.lang.String.valueOf(c.getLong(3))), null)
                if (cursor!!.moveToFirst()) artpath = cursor.getString(0) //cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART) https://stackoverflow.com/questions/9114086/optimizing-access-to-cursors-in-android-position-vs-column-names
                cursor.close()

                val audioModel = AudioModel(c.getString(4), path, name, artist, artpath)
                audioList.add(audioModel)
            }
            c.close()
        }

        //save audioList
        prefs.edit().putString("audioList", Gson().toJson(audioList)).apply()

        return audioList
    }
}
