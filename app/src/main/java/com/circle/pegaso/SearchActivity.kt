package com.circle.pegaso

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_search.*

class SearchActivity : AppCompatActivity() {
    private lateinit var adapter: Adapter
    private lateinit var audioList: ArrayList<AudioModel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val audioListStr = getSharedPreferences("MY_PREFS_NAME", Context.MODE_PRIVATE).getString("audioList", "")
        audioList = Gson().fromJson<ArrayList<AudioModel>>(audioListStr, object : TypeToken<ArrayList<AudioModel>>() {}.type)

        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        (search).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isIconifiedByDefault = false // Do not iconify the widget; expand it by default

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }
                //Execute searches while typing
                override fun onQueryTextChange(query: String?): Boolean {
                    if (query!!.isNotEmpty()) {
                        val searchIntent = Intent(applicationContext, SearchActivity::class.java)
                        searchIntent.action = Intent.ACTION_SEARCH
                        searchIntent.putExtra(SearchManager.QUERY, query)
                        startActivity(searchIntent)
                    }
                    return false
                }
            })

            requestFocus()
        }

        listv.layoutManager = LinearLayoutManager(this)
        adapter = Adapter(audioList.toTypedArray(),
            object : Adapter.Interfacing {
                override fun onItemClick(item: AudioModel) {
                    val intent = Intent(applicationContext, DetailActivity::class.java)
                    intent.putExtra("id", audioList.indexOf(item))
                    startActivity(intent)
                }
                override fun askloadPlaylists(item: ArrayList<ListModel>) {}
            }, 0)
        listv.adapter = adapter

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                search.setQuery(query, false)//fix voice submit without typing
                query(query)
            }
        }
    }

    private fun query(query: String) {
        val list = audioList.filter { e -> e.name.toLowerCase().contains(query.toLowerCase()) || e.artist.toLowerCase().contains(query.toLowerCase()) }
        adapter.items = list.toTypedArray()
        adapter.notifyDataSetChanged()
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
}
