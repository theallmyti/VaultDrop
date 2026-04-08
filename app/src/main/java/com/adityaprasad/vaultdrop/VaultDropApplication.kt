package com.adityaprasad.vaultdrop

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class VaultDropApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)
            
            // Background update of yt-dlp to avoid 403 errors when YouTube changes ciphers
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().updateYoutubeDL(this@VaultDropApplication, com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE)
                } catch (e: Exception) {
                    Log.e("VaultDropApp", "Failed to update YoutubeDL: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("VaultDropApp", "Failed to initialize YoutubeDL/FFmpeg: ${e.message}")
        }
    }
}
