package com.bolohisab.voice.di

import android.content.Context
import com.bolohisab.voice.AsrModelInstaller
import com.bolohisab.voice.SherpaSpeechRecognizer
import com.bolohisab.voice.SpeechRecognizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    @Provides
    @Singleton
    fun speechRecognizer(@ApplicationContext context: Context): SpeechRecognizer = SherpaSpeechRecognizer(context)

    @Provides
    @Singleton
    fun modelInstaller(@ApplicationContext context: Context): AsrModelInstaller = AsrModelInstaller(context)
}
