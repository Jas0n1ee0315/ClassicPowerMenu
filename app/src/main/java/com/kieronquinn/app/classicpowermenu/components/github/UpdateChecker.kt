package com.kieronquinn.app.classicpowermenu.components.github

import android.content.Context
import android.os.Parcelable
import com.kieronquinn.app.classicpowermenu.BuildConfig
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.File

class UpdateChecker {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    companion object {
        // 改為檢查本 fork 的 Releases（原本是原作者 KieronQuinn/ClassicPowerMenu）。
        // 原作者的版本與本 fork 簽章不同、也沒有自訂功能，不應提示更新成原版。
        private const val REPO = "Jas0n1ee0315/ClassicPowerMenu"
        private const val BASE_URL = "https://api.github.com/repos/$REPO/"
        private const val RELEASES_URL = "https://github.com/$REPO/releases"

        /** 取出版本標籤結尾的修訂號，例如 v1.9.3-cardart-r3 → 3 */
        private val REVISION_REGEX = Regex("""-r(\d+)$""")

        /**
         *  判斷 GitHub 最新 Release 是否比目前版本新。
         *  兩邊都有「-rN」修訂號時比較數字（避免把舊版當成更新、提示降版）；
         *  否則沿用原本的規則：標籤不同就視為有更新。
         */
        fun isNewerRelease(remoteTag: String, localTag: String): Boolean {
            val remote = REVISION_REGEX.find(remoteTag)?.groupValues?.get(1)?.toIntOrNull()
            val local = REVISION_REGEX.find(localTag)?.groupValues?.get(1)?.toIntOrNull()
            if (remote != null && local != null) return remote > local
            return remoteTag != localTag
        }
    }

    fun getLatestRelease() = callbackFlow {
        withContext(Dispatchers.IO){
            getReleaseList()?.let { gitHubReleaseResponse ->
                val currentTag = gitHubReleaseResponse.tagName
                if (currentTag != null && isNewerRelease(currentTag, BuildConfig.TAG_NAME)) {
                    //New update available!
                    val asset =
                        gitHubReleaseResponse.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    val releaseUrl =
                        asset?.browserDownloadUrl?.replace("/download/", "/tag/")?.apply {
                            substring(0, lastIndexOf("/"))
                        }
                    val name = gitHubReleaseResponse.name ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val body = gitHubReleaseResponse.body ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val publishedAt = gitHubReleaseResponse.publishedAt ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    this@callbackFlow.trySend(
                        Update(
                            name,
                            body,
                            publishedAt,
                            asset?.browserDownloadUrl
                                ?: RELEASES_URL,
                            asset?.name ?: "ClassicPowerMenu.apk",
                            releaseUrl ?: RELEASES_URL
                        )
                    ).isSuccess
                }
            } ?: run {
                this@callbackFlow.trySend(null).isSuccess
            }
        }
        awaitClose {  }
    }

    fun clearCachedDownloads(context: Context){
        File(context.externalCacheDir, "updates").deleteRecursively()
    }

    private fun getReleaseList(): GitHubReleaseResponse? {
        val service: GitHubService = retrofit.create(GitHubService::class.java)
        runCatching {
            service.getReleaseList().execute().body()
        }.onSuccess {
            return it
        }.onFailure {
            return null
        }
        return null
    }

    interface GitHubService {
        @GET("releases/latest")
        fun getReleaseList(): Call<GitHubReleaseResponse>
    }

    @Parcelize
    data class Update(val name: String, val changelog: String, val timestamp: String, val assetUrl: String, val assetName: String, val releaseUrl: String): Parcelable

}