package com.threedd.studio.di

import android.content.Context
import androidx.room.Room
import com.threedd.studio.data.local.StudioDao
import com.threedd.studio.data.local.StudioDatabase
import com.threedd.studio.data.settings.SettingsStore
import com.threedd.studio.content.AgeGate
import com.threedd.studio.export.ExportManager
import com.threedd.studio.render.StudioRenderer
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
    fun database(@ApplicationContext context: Context): StudioDatabase =
        Room.databaseBuilder(context, StudioDatabase::class.java, StudioDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun dao(db: StudioDatabase): StudioDao = db.dao()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context) = SettingsStore(context)

    @Provides
    @Singleton
    fun studioRenderer(@ApplicationContext context: Context) = StudioRenderer(context)

    @Provides
    @Singleton
    fun exportManager(@ApplicationContext context: Context) = ExportManager(context)

    @Provides
    @Singleton
    fun ageGate(@ApplicationContext context: Context, store: SettingsStore) = AgeGate(context, store)
}
