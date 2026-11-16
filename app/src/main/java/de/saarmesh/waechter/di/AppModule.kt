package de.saarmesh.waechter.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.saarmesh.waechter.BuildConfig
import de.saarmesh.waechter.data.local.AppDatabase
import de.saarmesh.waechter.data.remote.AusfallApi
import de.saarmesh.waechter.data.remote.AusfallParser
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun okHttp(@ApplicationContext ctx: Context): OkHttpClient = OkHttpClient.Builder()
        // HTTP-Cache: der Server liefert ETag/Last-Modified, unveraenderte Daten kosten nur ein 304.
        .cache(Cache(File(ctx.cacheDir, "http"), 10L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "SaarMesh-Repeater-Waechter/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient): AusfallApi = Retrofit.Builder()
        .baseUrl(BuildConfig.DEFAULT_SERVER_URL + "/") // wird durch volle @Url ersetzt
        .client(client)
        .addConverterFactory(AusfallParser.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AusfallApi::class.java)

    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "waechter.db").build()
}
