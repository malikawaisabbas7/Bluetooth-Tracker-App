package com.example.bluetoothtracker.di

import android.content.Context
import androidx.room.Room
import com.example.bluetoothtracker.data.local.AppDatabase
import com.example.bluetoothtracker.data.local.DeviceDao
import com.example.bluetoothtracker.data.local.SightingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bluetooth_tracker.db")
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideSightingDao(db: AppDatabase): SightingDao = db.sightingDao()
}
