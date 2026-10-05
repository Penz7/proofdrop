package com.penz7.proofdrop.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [OrderEntity::class, EvidenceEntity::class, DeviceEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ProofDropDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun deviceDao(): DeviceDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ProofDropDatabase =
        Room.databaseBuilder(context, ProofDropDatabase::class.java, "proofdrop.db")
            // Pre-release: the local DB is a cache of the server, so a schema change just rebuilds it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun orderDao(db: ProofDropDatabase) = db.orderDao()
    @Provides fun evidenceDao(db: ProofDropDatabase) = db.evidenceDao()
    @Provides fun deviceDao(db: ProofDropDatabase) = db.deviceDao()
}
