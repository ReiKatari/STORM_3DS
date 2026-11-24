package com.storm3ds.emulator.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.util.Linkify
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.noties.markwon.Markwon
import io.noties.markwon.image.picasso.PicassoImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.serialization.json.Json
import com.storm3ds.emulator.common.DirectoryAccessValidator
import com.storm3ds.emulator.common.PermissionHandler
import com.storm3ds.emulator.common.UriPermissionManager
import com.storm3ds.emulator.common.network.SharedOkHttpClient
import com.storm3ds.emulator.common.uridelegates.CompositeUriHandler
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.impl.dtos.input.ControllerConfigurationDto
import com.storm3ds.emulator.impl.dtos.input.InputConfigDto
import com.storm3ds.emulator.utils.ControllerConfigurationDtoTypeAdapter
import com.storm3ds.emulator.utils.InputAssignmentDtoTypeAdapter
import com.storm3ds.emulator.impl.system.AppForegroundStateObserver
import com.storm3ds.emulator.impl.system.AppForegroundStateTracker
import com.storm3ds.emulator.utils.UriTypeHierarchyAdapter
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
                .registerTypeHierarchyAdapter(Uri::class.java, UriTypeHierarchyAdapter())
                .registerTypeAdapter(ControllerConfigurationDto::class.java, ControllerConfigurationDtoTypeAdapter())
                .registerTypeAdapter(InputConfigDto.AssignmentDto::class.java, InputAssignmentDtoTypeAdapter())
                .create()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return SharedOkHttpClient.instance
    }

    @Provides
    @Singleton
    fun provideUriHandler(@ApplicationContext context: Context): UriHandler {
        return CompositeUriHandler(context)
    }

    @Provides
    @Singleton
    fun providePicasso(@ApplicationContext context: Context): Picasso {
        return Picasso.Builder(context).build()
    }

    @Provides
    @Singleton
    fun provideMarkwon(@ApplicationContext context: Context, picasso: Picasso): Markwon {
        return Markwon.builder(context)
            .usePlugin(PicassoImagesPlugin.create(picasso))
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS, true))
            .build()
    }

    @Provides
    @Singleton
    fun provideUriPermissionManager(@ApplicationContext context: Context): UriPermissionManager {
        return UriPermissionManager(context)
    }

    @Provides
    @Singleton
    fun providesDirectoryAccessValidator(@ApplicationContext context: Context): DirectoryAccessValidator {
        return DirectoryAccessValidator(context)
    }

    @Provides
    @Singleton
    fun providePermissionHandler(@ApplicationContext context: Context): PermissionHandler {
        return PermissionHandler(context)
    }

    @Provides
    @Singleton
    fun provideAppForegroundStateTracker(): AppForegroundStateTracker {
        return AppForegroundStateTracker()
    }

    @Provides
    fun provideAppForegroundStateObserver(appForegroundStateTracker: AppForegroundStateTracker): AppForegroundStateObserver {
        return appForegroundStateTracker
    }
}
