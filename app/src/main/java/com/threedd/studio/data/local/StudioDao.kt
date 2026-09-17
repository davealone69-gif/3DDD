package com.threedd.studio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {

    @Query("SELECT * FROM avatar_designs ORDER BY updatedAtEpochMs DESC")
    fun observeDesigns(): Flow<List<AvatarDesignEntity>>

    @Query("SELECT * FROM avatar_designs WHERE id = :id LIMIT 1")
    suspend fun design(id: Long): AvatarDesignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesign(design: AvatarDesignEntity): Long

    @Query("DELETE FROM avatar_designs WHERE id = :id")
    suspend fun deleteDesign(id: Long)

    @Query("SELECT * FROM library_models ORDER BY importedAtEpochMs DESC")
    fun observeModels(): Flow<List<LibraryModelEntity>>

    @Query("SELECT * FROM library_models WHERE id = :id LIMIT 1")
    suspend fun model(id: String): LibraryModelEntity?

    @Upsert
    suspend fun upsertModel(model: LibraryModelEntity)

    @Query("DELETE FROM library_models WHERE id = :id")
    suspend fun deleteModel(id: String)
}
