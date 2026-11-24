package com.codein.ide.di

import android.content.Context
import androidx.room.Room
import com.codein.ide.data.CodeinDatabase
import com.codein.ide.data.OpenTabDao
import com.codein.ide.data.ProjectDao
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
    fun provideDatabase(@ApplicationContext context: Context): CodeinDatabase =
        Room.databaseBuilder(context, CodeinDatabase::class.java, "codein.db").fallbackToDestructiveMigration(true).build()

    @Provides
    fun provideProjectDao(db: CodeinDatabase): ProjectDao = db.projects()

    @Provides
    fun provideOpenTabDao(db: CodeinDatabase): OpenTabDao = db.tabs()
}
