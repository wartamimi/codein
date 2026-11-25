package com.codein.ide.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A project known to Codein. Projects always live under the app's private storage. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val path: String,
    val name: String,
    val packageName: String,
    val language: String,
    val template: String,
    val minSdk: Int,
    val targetSdk: Int,
    val createdAt: Long,
    val lastOpenedAt: Long,
)

/** Open editor tabs per project, restored when the workspace reopens. */
@Entity(tableName = "open_tabs", primaryKeys = ["projectPath", "relativePath"])
data class OpenTabEntity(
    val projectPath: String,
    val relativePath: String,
    val position: Int,
    val active: Boolean,
    val cursorLine: Int,
    val cursorColumn: Int,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE path = :path")
    suspend fun find(path: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET lastOpenedAt = :time WHERE path = :path")
    suspend fun touch(path: String, time: Long)

    @Query("DELETE FROM projects WHERE path = :path")
    suspend fun delete(path: String)
}

@Dao
interface OpenTabDao {
    @Query("SELECT * FROM open_tabs WHERE projectPath = :projectPath ORDER BY position")
    suspend fun tabsFor(projectPath: String): List<OpenTabEntity>

    @Query("DELETE FROM open_tabs WHERE projectPath = :projectPath")
    suspend fun clear(projectPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<OpenTabEntity>)
}

@Database(entities = [ProjectEntity::class, OpenTabEntity::class], version = 1, exportSchema = false)
abstract class CodeinDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun tabs(): OpenTabDao
}
