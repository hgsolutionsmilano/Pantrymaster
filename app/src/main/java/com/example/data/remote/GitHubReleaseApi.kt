package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "browser_download_url")
    val browserDownloadUrl: String = "",
    @Json(name = "content_type")
    val contentType: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name")
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @Json(name = "html_url")
    val htmlUrl: String = "",
    @Json(name = "published_at")
    val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList()
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubReleaseDto>
}
