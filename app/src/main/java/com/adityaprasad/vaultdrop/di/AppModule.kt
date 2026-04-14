package com.adityaprasad.vaultdrop.di

import android.content.Context
import com.adityaprasad.vaultdrop.BuildConfig
import androidx.room.Room
import com.adityaprasad.vaultdrop.data.db.AppDatabase
import com.adityaprasad.vaultdrop.data.db.BookmarkDao
import com.adityaprasad.vaultdrop.data.db.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vaultdrop_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val dispatcher = Dispatcher().apply {
            // Keep network concurrency modest to reduce thermal spikes.
            maxRequests = 8
            maxRequestsPerHost = 4
        }

        // In-memory cookie jar so CSRF tokens and session cookies persist
        // across multiple requests (required for Instagram's GraphQL API)
        val cookieJar = object : CookieJar {
            private val store = mutableMapOf<String, MutableList<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                store.getOrPut(host) { mutableListOf() }.apply {
                    // Replace existing cookies with same name
                    for (newCookie in cookies) {
                        removeAll { it.name == newCookie.name }
                        add(newCookie)
                    }
                }
                // Also share cookies between i.instagram.com and www.instagram.com
                if (host.contains("instagram.com")) {
                    val otherHosts = listOf("www.instagram.com", "i.instagram.com", "instagram.com")
                    for (otherHost in otherHosts) {
                        if (otherHost != host) {
                            store.getOrPut(otherHost) { mutableListOf() }.apply {
                                for (newCookie in cookies) {
                                    removeAll { it.name == newCookie.name }
                                    add(newCookie)
                                }
                            }
                        }
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return store[url.host]?.filter { !it.expiresAt.let { exp -> exp < System.currentTimeMillis() } } ?: emptyList()
            }
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .cookieJar(cookieJar)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideConvexApiService(client: OkHttpClient): com.adityaprasad.vaultdrop.data.api.ConvexApiService {
        return retrofit2.Retrofit.Builder()
            .baseUrl(BuildConfig.CONVEX_BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.adityaprasad.vaultdrop.data.api.ConvexApiService::class.java)
    }
}
