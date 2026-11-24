package com.storm3ds.emulator.di

import android.content.Context
import android.content.SharedPreferences
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.repositories.UpdatesRepository
import com.storm3ds.emulator.domain.services.UpdateInstallManager
import com.storm3ds.emulator.github.GitHubApi
import com.storm3ds.emulator.github.repositories.BaseMirrorUpdatesRepository
import com.storm3ds.emulator.github.services.GitHubUpdateInstallManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {
    @Provides
    @Singleton
    fun provideGitHubApi(json: Json, okHttpClient: OkHttpClient): GitHubApi {
        val updaterClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "STORM-3DS-Updater")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                chain.proceed(request)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .client(updaterClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .baseUrl("https://api.github.com")
            .build()

        return retrofit.create(GitHubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdatesRepository(
        @ApplicationContext context: Context,
        gitHubApi: GitHubApi,
        sharedPreferences: SharedPreferences,
    ): UpdatesRepository {
        val statePreferences = context.getSharedPreferences("preferences-github", Context.MODE_PRIVATE)
        val defaultAdrenotools = runCatching {
            context.resources.getBoolean(R.bool.adrenotools_enabled)
        }.getOrDefault(true)
        return BaseMirrorUpdatesRepository(
            context = context,
            settingsPreferences = sharedPreferences,
            statePreferences = statePreferences,
            defaultAdrenotoolsEnabled = defaultAdrenotools,
            primaryFetcher = { gitHubApi.getReleases() },
        )
    }

    @Provides
    @Singleton
    fun provideUpdateInstallManager(@ApplicationContext context: Context): UpdateInstallManager {
        return GitHubUpdateInstallManager(context)
    }
}
