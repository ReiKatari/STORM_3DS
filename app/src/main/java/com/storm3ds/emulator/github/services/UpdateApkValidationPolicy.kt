package com.storm3ds.emulator.github.services

import com.storm3ds.emulator.domain.model.Version

data class UpdateApkMetadata(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val signerDigests: Set<String>,
)

enum class UpdateApkRejectionReason {
    SIZE_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_CODE_NOT_NEWER,
    VERSION_NAME_MISMATCH,
    SIGNATURE_MISMATCH,
}

object UpdateApkValidationPolicy {
    const val EXPECTED_PACKAGE = "com.storm3ds.emulator"
    private val ALLOWED_PACKAGES = setOf("com.storm3ds.emulator")

    fun validate(
        candidate: UpdateApkMetadata,
        installed: UpdateApkMetadata,
        expectedVersion: Version,
        requireSemanticVersionMatch: Boolean,
        expectedSize: Long,
        actualSize: Long,
    ): UpdateApkRejectionReason? {
        if (expectedSize > 0L && expectedSize != actualSize) {
            return UpdateApkRejectionReason.SIZE_MISMATCH
        }
        if (candidate.packageName !in ALLOWED_PACKAGES || installed.packageName !in ALLOWED_PACKAGES || candidate.packageName != installed.packageName) {
            return UpdateApkRejectionReason.PACKAGE_MISMATCH
        }
        if (candidate.versionCode <= installed.versionCode) {
            return UpdateApkRejectionReason.VERSION_CODE_NOT_NEWER
        }
        if (requireSemanticVersionMatch) {
            val candidateVersion = Version.parseOrNull(candidate.versionName)
            if (candidateVersion == null || !candidateVersion.isSameArtifactVersion(expectedVersion)) {
                return UpdateApkRejectionReason.VERSION_NAME_MISMATCH
            }
        }
        if (candidate.signerDigests.intersect(installed.signerDigests).isEmpty()) {
            return UpdateApkRejectionReason.SIGNATURE_MISMATCH
        }
        return null
    }
}
