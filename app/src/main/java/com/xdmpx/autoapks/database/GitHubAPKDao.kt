package com.xdmpx.autoapks.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface GitHubAPKDao {
    @Query("SELECT * FROM githubapk")
    suspend fun getAll(): List<GitHubAPKEntity>

    @Query("SELECT * FROM githubapk WHERE id = :id")
    suspend fun get(id: Long): GitHubAPKEntity

    @Query("SELECT repository FROM githubapk")
    suspend fun getRepositories(): List<String>

    @Query("SELECT repository FROM githubapk WHERE repository = :repository")
    suspend fun getRepositoryByName(repository: String): String?

    @Insert
    suspend fun insert(githubApk: GitHubAPKEntity): Long

    @Insert
    suspend fun insertAll(vararg githubApks: GitHubAPKEntity)

    @Delete
    suspend fun delete(githubApk: GitHubAPKEntity)

    @Update
    suspend fun update(githubApk: GitHubAPKEntity)

    @Update
    suspend fun updateAll(vararg githubApks: GitHubAPKEntity)
}
