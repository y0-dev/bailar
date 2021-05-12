package com.circle.pegaso

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class StartService : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, /*BackgroundSoundService*/MediaPlaybackService::class.java)
        intent.action = "PLAYLIST"
        val id = this.intent.data!!.getQueryParameter("id")
        intent.putExtra("id", id!!.toInt())

        startService(intent)
        finish()
    }
}