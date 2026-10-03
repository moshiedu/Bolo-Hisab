package com.bolohisab.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.bolohisab.data.db.LearningDao
import com.bolohisab.data.db.LedgerDao
import com.bolohisab.data.db.LedgerDatabase
import com.bolohisab.data.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Clock
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "bolohisab_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LedgerDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(DatabaseKeyProvider(context).passphrase())
        return Room.databaseBuilder(context, LedgerDatabase::class.java, LedgerDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(
                LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3,
                LedgerDatabase.MIGRATION_3_4, LedgerDatabase.MIGRATION_4_5,
            )
            .build()
    }

    @Provides
    fun ledgerDao(db: LedgerDatabase): LedgerDao = db.ledgerDao()

    @Provides
    fun learningDao(db: LedgerDatabase): LearningDao = db.learningDao()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
