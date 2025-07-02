package com.example.scrollfree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import android.util.Log
import android.content.Intent



class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optional: Only needed if you have a layout like a settings screen
        // setContentView(R.layout.activity_main)

        val serviceIntent = Intent(this, BlinkDetectionService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        Log.d("ScrollFree", "Started BlinkDetectionService from MainActivity")
    }
}