package me.magnum.melonds.github

import me.magnum.melonds.github.dtos.ReleaseDto
import retrofit2.http.GET

interface GitHubApi {
    @GET("/repos/WeirdWally-dev/melonDS-android/releases/latest")
    suspend fun getLatestRelease(): ReleaseDto

    @GET("/repos/WeirdWally-dev/melonDS-android/releases/tags/nightly-release")
    suspend fun getLatestNightlyRelease(): ReleaseDto
}