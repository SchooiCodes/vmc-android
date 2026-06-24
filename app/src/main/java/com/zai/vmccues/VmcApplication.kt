package com.zai.vmccues

import android.app.Application
import com.zai.vmccues.data.SettingsRepository
import com.zai.vmccues.gate.ActivityRecognitionProvider

/**
 * Process-wide singletons. Several components created in different lifecycles
 * (the Service, the Receiver, the TileService) all need to reach the same
 * [SettingsRepository] and [ActivityRecognitionProvider] instances, so we
 * stash them on the Application.
 */
class VmcApplication : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var activityRecognition: ActivityRecognitionProvider
        private set

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
