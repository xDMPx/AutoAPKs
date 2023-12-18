package com.xdmpx.autoapks.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GitHubAPKEntity::class], version = 1)
abstract class GitHubAPKDatabase : RoomDatabase() {

    abstract val gitHubAPKDatabase: GitHubAPKDao

    companion object {
        @Volatile
        private var INSTANCE: GitHubAPKDatabase? = null

        fun getInstance(context: Context): GitHubAPKDatabase {
            synchronized(this) {
                return INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, GitHubAPKDatabase::class.java, "APKsDatabase"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
