package com.storm3ds.emulator.github

import com.storm3ds.emulator.github.dtos.ReleaseDto
import retrofit2.http.GET

interface GitHubApi {
    @GET("/repos/ReiKatari/STORM_3DS/releases?per_page=100")
    suspend fun getReleases(): List<ReleaseDto>
}
