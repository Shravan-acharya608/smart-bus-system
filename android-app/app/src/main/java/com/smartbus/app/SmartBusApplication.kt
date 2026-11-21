package com.smartbus.app

import android.app.Application
import androidx.room.Room
import com.smartbus.app.data.local.AppDatabase
import com.smartbus.app.data.local.UserPreferences
import com.smartbus.app.data.remote.ApiClient
import com.smartbus.app.data.remote.ApiService
import com.smartbus.app.data.repository.AuthRepository
import com.smartbus.app.data.repository.ConductorRepository
import com.smartbus.app.data.repository.TransitRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class SmartBusApplication : Application() {

    lateinit var userPreferences: UserPreferences
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var apiService: ApiService
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var transitRepository: TransitRepository
        private set

    lateinit var conductorRepository: ConductorRepository
        private set

    override fun onCreate() {
        super.onCreate()

        userPreferences = UserPreferences(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "smartbus_local.db")
            .fallbackToDestructiveMigration()
            .build()

        apiClient = ApiClient(userPreferences)
        val initialUrl = runBlocking { userPreferences.baseUrlFlow.firstOrNull() } ?: BuildConfig.BASE_URL
        apiService = apiClient.createService(initialUrl)

        authRepository = AuthRepository(apiService, userPreferences)
        transitRepository = TransitRepository(apiService)
        conductorRepository = ConductorRepository(apiService, database.offlineEventDao())
    }

    fun updateBaseUrl(newUrl: String) {
        val normalized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        apiService = apiClient.createService(normalized)
        authRepository = AuthRepository(apiService, userPreferences)
        transitRepository = TransitRepository(apiService)
        conductorRepository = ConductorRepository(apiService, database.offlineEventDao())
    }
}
