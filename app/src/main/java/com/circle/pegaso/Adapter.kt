package com.circle.pegaso

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.title_item.view.*

class Adapter(var items: Array<AudioModel>, private val listener: Interfacing, private val playlist: Int) : RecyclerView.Adapter<Adapter.ViewHolder>() {

    interface Interfacing {
        fun onItemClick(item: AudioModel)
        //fun onIconClick(item: AudioModel)
        fun askloadPlaylists(item: ArrayList<ListModel>)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val lineView = LayoutInflater.from(parent.context).inflate(R.layout.title_item, parent, false)
        return ViewHolder(lineView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], listener, playlist)
    }

    class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: AudioModel, listener: Interfacing, playlist: Int) {
            with(item) {
                itemView.title.text = name
                itemView.artist.text = artist
                if (album !== null) itemView.imageView.setImageURI(Uri.parse(album))
                if (playlist != 0) itemView.action.setImageResource(R.drawable.ic_playlist_remove)
                //itemView.action.setOnClickListener { listener.onIconClick() }

                if (playlist != 0) {
                    itemView.action.setImageResource(R.drawable.ic_playlist_remove)
                    itemView.action.setOnClickListener {
                        val playlistStr = view.context.applicationContext.getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).getString("playlists", "")
                        val type = object : TypeToken<ArrayList<ListModel>>() {}.type
                        val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistStr, type)
                         AlertDialog.Builder(view.context)
                            .setTitle(R.string.remove_item)
                            .setMessage(R.string.remove_item_des)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                playlists[playlist].musics.remove(item.id)
                                view.context.getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).edit().putString("playlists", Gson().toJson(playlists)).apply()
                                listener.askloadPlaylists(playlists)
                                Toast.makeText(view.context, item.name + " " + view.context.getString(R.string.removed), Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
                } else {
                    itemView.action.setOnClickListener {
                        val playlistStr = view.context.applicationContext.getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).getString("playlists", "")
                        val type = object : TypeToken<ArrayList<ListModel>>() {}.type
                        val playlists = Gson().fromJson<ArrayList<ListModel>>(playlistStr, type)
                        val titles = arrayListOf<String>()
                        val originalSelected = arrayListOf<Boolean>()
                        for (i in playlists) {
                            if (playlists.indexOf(i) != 0) {
                                titles.add(i.title)
                                originalSelected.add(i.musics.contains(item.id))
                            }
                        }
                        val selectedList = originalSelected.toBooleanArray()

                        val builder = AlertDialog.Builder(view.context)
                        builder.setTitle(R.string.add_item)
                        builder.setMultiChoiceItems(titles.toTypedArray(), selectedList) { _, which, isChecked -> selectedList[which] = isChecked }
                        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            for ((index, value) in selectedList.withIndex()) {
                                if (value && !originalSelected[index]) {
                                    Toast.makeText(view.context, item.name + " " + view.context.getString(R.string.added_to) + " " + titles[index], Toast.LENGTH_SHORT).show()
                                    playlists[index + 1].musics.add(item.id)
                                } else if (originalSelected[index] && !value) {//element unselected
                                    Toast.makeText(view.context, item.name + " " + view.context.getString(R.string.removed_from) + " " + titles[index], Toast.LENGTH_SHORT).show()
                                    playlists[index + 1].musics.remove(item.id)
                                }
                            }
                            view.context.getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).edit().putString("playlists", Gson().toJson(playlists)).apply()
                            listener.askloadPlaylists(playlists)
                        }
                        builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        builder.show()
                    }
                }
            }
            itemView.setOnClickListener { listener.onItemClick(item) }
        }
    }
}