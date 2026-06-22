package com.carmind.voicejournal.shared.di

import android.content.Context
import androidx.room.Room
import com.carmind.voicejournal.BuildConfig
import com.carmind.voicejournal.core.journal.JournalDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JournalDatabase {
        return Room.databaseBuilder(context, JournalDatabase::class.java, "journal.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDao(db: JournalDatabase) = db.dao()

    @Provides
    @Named("anthropic_key")
    fun provideAnthropicKey() = BuildConfig.ANTHROPIC_API_KEY

    @Provides
    @Named("ollama_url")
    fun provideOllamaUrl() = BuildConfig.OLLAMA_BASE_URL

    @Provides
    @Named("carmind_url")
    fun provideCarMindUrl() = "http://192.168.43.1:8080"
}
