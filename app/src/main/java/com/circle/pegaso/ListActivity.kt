package com.circle.pegaso

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_list.*
import kotlinx.android.synthetic.main.title_item.view.*


class ListActivity : AppCompatActivity() {
    private lateinit var playlist: ListModel
    private var i: Int = -1
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        if(intent.hasExtra("id")) {
            i = intent.getIntExtra("id", -1)

            val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
            val playlistsStr = prefs.getString("playlists", "")
            val myType = object : TypeToken<ArrayList<ListModel>>() {}.type
            val audioListStr = prefs.getString("audioList", "")
            val type = object : TypeToken<ArrayList<AudioModel>>() {}.type
            val audioList = Gson().fromJson<ArrayList<AudioModel>>(audioListStr, type)
            playlist = Gson().fromJson<ArrayList<ListModel>>(playlistsStr, myType).get(i)

            val list = arrayListOf<AudioModel>()
            for(x in playlist.musics){
                val e = audioList.find { e -> e.id == x }
                if(e != null){
                    list.add(e)
                }else {
                    playlist.musics.remove(x)
                    val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistsStr, myType)
                    playlists[i] = playlist
                    prefs.edit().putString("playlists", Gson().toJson(playlists)).apply()
                }
            }

            title = playlist.title

            listv.layoutManager = LinearLayoutManager(this)
            adapter = Adapter(list.toTypedArray(),
                object : Adapter.Interfacing {
                    override fun onItemClick(item: AudioModel) {
                        /*val intent = Intent(applicationContext, DetailActivity::class.java)
                        intent.putExtra("id", playlist.musics.indexOf(item.id))
                        startActivity(intent)*/
                        //val fragment = supportFragmentManager.findFragmentById(R.id.fragment) as PlayerFragment
                        //fragment.service!!.playlist(i, playlist.musics.indexOf(item.id))
                        MediaControllerCompat.getMediaController(this@ListActivity).addQueueItem(
                            MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, i.toString()).build().description, playlist.musics.indexOf(item.id))
                        //.sendCommand("j", bundleOf({"", ""}), "")
                        //https://stackoverflow.com/questions/59075231/what-are-some-examples-of-mediacontroller-commands
                    }

                    override fun askloadPlaylists(item: ArrayList<ListModel>) {
                        var e: AudioModel? = null
                        for(x in list)
                            if(!item[i].musics.contains(x.id)) e = x
                        if(e != null) list.remove(e)
                        adapter.items = list.toTypedArray()
                        adapter.notifyDataSetChanged()
                    }
                }, i)
            listv.adapter = adapter

            //https://medium.com/@zackcosborn/step-by-step-recyclerview-swipe-to-delete-and-undo-7bbae1fce27e
            val itemTouchHelper = ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT/* or ItemTouchHelper.RIGHT*/) {
                override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                    val background = if(i==0) ColorDrawable(Color.parseColor("#0a8043")/*GREEN*/) else ColorDrawable(Color.parseColor("#db4336")/*RED*/)
                    //background.setBounds(0, viewHolder.itemView.top,   viewHolder.itemView.left + dX.toInt(), viewHolder.itemView.bottom)

                    val icon = ContextCompat.getDrawable(this@ListActivity, if(i==0) R.drawable.ic_playlist_add else R.drawable.ic_playlist_remove)//android.R.drawable.ic_menu_delete
                    val itemView = viewHolder.itemView
                    val iconMargin: Int = (itemView.height - icon!!.intrinsicHeight) / 2
                    val iconTop: Int = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                    val iconBottom = iconTop + icon.intrinsicHeight

                    if (dX > 0) { // Swiping to the right
                        val iconLeft: Int = itemView.left + iconMargin + icon.intrinsicWidth
                        val iconRight: Int = itemView.left + iconMargin
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    } else if (dX < 0) { // Swiping to the left // TODO icon does not appears on left swipe
                        val iconLeft: Int = itemView.right - iconMargin - icon.intrinsicWidth
                        val iconRight: Int = itemView.right - iconMargin
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    } else { // view is unSwiped
                        background.setBounds(0, 0, 0, 0)
                    }

                    //icon!!.setBounds(viewHolder.itemView.right - 15, viewHolder.itemView.top, viewHolder.itemView.right - 15, viewHolder.itemView.top + icon.intrinsicHeight)
                    background.draw(c)
                    icon.draw(c)

                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean { return false }
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                    /*list.removeAt(viewHolder.adapterPosition)
                    adapter.items = list.toTypedArray()
                    adapter.notifyDataSetChanged()*/
                    viewHolder.itemView.action.performClick()
                    //adapter.notifyItemRemoved(viewHolder.adapterPosition)
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                }
            })

            itemTouchHelper.attachToRecyclerView(listv)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if(i != 0) menuInflater.inflate(R.menu.menu_playlist, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_remove -> {
                val builder = AlertDialog.Builder(this)
                with(builder)
                {
                    setTitle(R.string.remove)
                    setMessage(R.string.remove_des)
                    setPositiveButton(android.R.string.ok) { _, _ -> removePlaylist() }
                    setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
                    show()
                }
                true
            }
            R.id.action_edit -> {
                val name = EditText(this)
                name.hint = getString(R.string.name)
                name.setText(title)
                name.setOnFocusChangeListener { _, _ ->
                    name.post{
                        val inputMethodManager: InputMethodManager = this@ListActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        inputMethodManager.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                name.requestFocus()

                val builder = AlertDialog.Builder(this)
                with(builder)
                {
                    setTitle(R.string.edit)
                    setView(name)
                    setPositiveButton(android.R.string.ok) { _, _ -> editPlaylist(name.text.toString()) }
                    setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
                    show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun removePlaylist(){
        val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
        val playlistsStr = prefs.getString("playlists", "")
        val myType = object : TypeToken<ArrayList<ListModel>>() {}.type
        val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistsStr, myType)
        playlists.remove(playlist)
        prefs.edit().putString("playlists", Gson().toJson(playlists)).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = getSystemService<ShortcutManager>(ShortcutManager::class.java)
            shortcutManager!!.removeDynamicShortcuts(arrayListOf(playlist.title))
        }
        finish()
    }

    private fun editPlaylist(name: String) {
        if(name == "")
            Snackbar.make(listv, R.string.empty, Snackbar.LENGTH_LONG).show()
        else {
            val prefs = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE)
            val playlistStr = prefs.getString("playlists", "")
            val myType = object : TypeToken<ArrayList<ListModel>>() {}.type
            val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistStr, myType)

            var alreadyExists = false
            for (p in playlists) if (p.title.equals(name)) {
                alreadyExists = true
                break
            }
            if (alreadyExists)
                Snackbar.make(listv, R.string.exist, Snackbar.LENGTH_LONG).show()
            else {
                Snackbar.make(listv, R.string.edited, Snackbar.LENGTH_SHORT).show()
                playlists[i].title = name
                prefs.edit().putString("playlists", Gson().toJson(playlists)).apply()
                title = name
            }
        }
    }
}
