package com.threedd.studio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AvatarDesignEntity::class, LibraryModelEntity::class],
    version = 1,
    exportSchema = true
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun dao(): StudioDao

    companion object {
        const val NAME = "3doubled.db"
    }
}
