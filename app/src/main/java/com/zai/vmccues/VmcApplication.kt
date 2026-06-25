package com.zai.vmccues

import android.app.Application
import android.content.Intent
import com.zai.vmccues.data.SettingsRepository
import com.zai.vmccues.gate.ActivityRecognitionProvider

class VmcApplication : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var activityRecognition: ActivityRecognitionProvider
        private set

    /** Stored MediaProjection result from MainActivity. */
    @Volatile var screenProjectionResultCode: Int = 0
    @Volatile var screenProjectionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        activityRecognition = ActivityRecognitionProvider(this)
    }

    companion object {
        @Volatile private var instance: VmcApplication? = null
        fun get(): VmcApplication = instance!!
    }
}
