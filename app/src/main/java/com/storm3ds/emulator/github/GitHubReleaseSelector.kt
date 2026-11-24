package com.storm3ds.emulator.github

import com.storm3ds.emulator.domain.model.Version
import com.storm3ds.emulator.github.dtos.AssetDto
import com.storm3ds.emulator.github.dtos.ReleaseDto
import kotlin.time.Instant

data class GitHubReleaseCandidate(
    val release: ReleaseDto,
    val asset: AssetDto,
    val version: Version,
    val publishedAt: Instant,
)

object GitHubReleaseSelector {
    fun selectProduction(
        releases: List<ReleaseDto>,
        currentVersion: Version,
        channel: GitHubUpdateChannel,
        skippedVersion: Version?,
        preferLegacyApk: Boolean = false,
    ): GitHubReleaseCandidate? {
        return releases.asSequence()
            .filter(::belongsToConfiguredRepository)
            .filterNot { it.draft }
            .filter { channel == GitHubUpdateChannel.STABLE_AND_PRERELEASE || !it.prerelease }
            .mapNotNull { release ->
                val cleanTag = release.tagName.trim().removePrefix("v").removePrefix("V")
                val version = Version.parseOrNull(cleanTag) ?: Version.parseOrNull(release.tagName) ?: return@mapNotNull null
                if (version.type == Version.ReleaseType.NIGHTLY || version <= currentVersion) {
                    return@mapNotNull null
                }
                if (skippedVersion != null && version == skippedVersion) {
                    return@mapNotNull null
                }
                val matchingAssets = release.assets.filter(::isProductionApk)
                val asset = matchingAssets.firstOrNull { isLegacyApk(it) == preferLegacyApk }
                    ?: matchingAssets.firstOrNull()
                    ?: return@mapNotNull null
                val published = parsePublishedAt(release) ?: Instant.fromEpochMilliseconds(0L)
                GitHubReleaseCandidate(release, asset, version, published)
            }
            .maxWithOrNull(compareBy<GitHubReleaseCandidate> { it.version }.thenBy { it.publishedAt })
    }

    fun selectNightly(releases: List<ReleaseDto>): GitHubReleaseCandidate? {
        return releases.asSequence()
            .filter(::belongsToConfiguredRepository)
            .filterNot { it.draft }
            .mapNotNull { release ->
                val asset = release.assets.firstOrNull(::isNightlyApk) ?: return@mapNotNull null
                val published = parsePublishedAt(release) ?: return@mapNotNull null
                GitHubReleaseCandidate(release, asset, Version.Nightly, published)
            }
            .maxByOrNull { it.publishedAt }
    }

    fun isProductionApk(asset: AssetDto): Boolean {
        val lowerName = asset.name.lowercase()
        return isRepositoryDownload(asset) &&
            (asset.contentType == APK_CONTENT_TYPE || asset.contentType == "application/octet-stream" || asset.contentType.isEmpty()) &&
            lowerName.endsWith(".apk") &&
            ("storm" in lowerName || "storm_3ds" in lowerName || "app" in lowerName) &&
            "nightly" !in lowerName
    }

    fun isNightlyApk(asset: AssetDto): Boolean {
        val lowerName = asset.name.lowercase()
        return isRepositoryDownload(asset) &&
            (asset.contentType == APK_CONTENT_TYPE || asset.contentType == "application/octet-stream" || asset.contentType.isEmpty()) &&
            lowerName.endsWith(".apk") &&
            ("storm" in lowerName || "storm_3ds" in lowerName || "app" in lowerName) &&
            "nightly" in lowerName
    }

    private fun isLegacyApk(asset: AssetDto): Boolean {
        return "legacy" in asset.name.lowercase()
    }

    private fun belongsToConfiguredRepository(release: ReleaseDto): Boolean {
        return release.htmlUrl.startsWith("${GITHUB_RELEASE_URL_PREFIX}tag/", ignoreCase = true) ||
            release.htmlUrl.startsWith(STORM_GAMES_WORLD_RELEASE_URL_PREFIX, ignoreCase = true) ||
            release.htmlUrl.startsWith(STORM_GAMES_WORLD_ALT_RELEASE_URL_PREFIX, ignoreCase = true)
    }

    private fun isRepositoryDownload(asset: AssetDto): Boolean {
        return asset.id > 0 &&
            asset.size >= 0 &&
            (asset.url.startsWith("${GITHUB_RELEASE_URL_PREFIX}download/", ignoreCase = true) ||
                asset.url.startsWith(STORM_GAMES_WORLD_DOWNLOAD_URL_PREFIX, ignoreCase = true) ||
                asset.url.startsWith(STORM_GAMES_WORLD_ALT_DOWNLOAD_URL_PREFIX, ignoreCase = true))
    }

    private fun parsePublishedAt(release: ReleaseDto): Instant? {
        return (release.publishedAt ?: release.createdAt)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
}
