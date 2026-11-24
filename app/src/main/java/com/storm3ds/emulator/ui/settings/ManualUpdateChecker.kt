package com.storm3ds.emulator.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.storm3ds.emulator.BuildConfig
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.DownloadProgress
import com.storm3ds.emulator.domain.model.Version
import com.storm3ds.emulator.domain.model.appupdate.AppUpdate
import com.storm3ds.emulator.domain.services.UpdateInstallManager
import com.storm3ds.emulator.github.GitHubReleaseCandidate
import com.storm3ds.emulator.github.GitHubReleaseSelector
import com.storm3ds.emulator.github.GitHubUpdateChannel
import com.storm3ds.emulator.github.PREF_KEY_GITHUB_UPDATE_CHANNEL
import com.storm3ds.emulator.github.StormUpdateMirrorClient
import com.storm3ds.emulator.github.services.GitHubUpdateInstallManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

object ManualUpdateChecker {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UpdateEntryPoint {
        fun updateInstallManager(): UpdateInstallManager
        fun markwon(): Markwon
    }

    private val fallbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null

    internal fun unwrapActivity(context: Context?): Activity? {
        var current: Context? = context
        var hops = 0
        while (current is ContextWrapper && hops < 16) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
            hops++
        }
        return current as? Activity
    }

    private tailrec fun findLifecycleOwner(context: Context?): LifecycleOwner? = when (context) {
        is LifecycleOwner -> context
        is ContextWrapper -> findLifecycleOwner(context.baseContext)
        else -> null
    }

    fun checkNow(context: Context) {
        val appContext = context.applicationContext
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        defaultPrefs.edit()
            .remove("github_updates_skip_version")
            .remove("github_updates_last_check")
            .apply()

        runCatching {
            appContext.getSharedPreferences("preferences-github", Context.MODE_PRIVATE)
                .edit()
                .remove("github_updates_skip_version")
                .remove("github_updates_last_check")
                .apply()
        }

        Toast.makeText(appContext, R.string.v800_checking_updates, Toast.LENGTH_SHORT).show()

        val owner = findLifecycleOwner(context)
        val launchScope = owner?.lifecycleScope ?: fallbackScope

        activeCheckJob?.cancel()
        activeCheckJob = launchScope.launch {
            try {
                val currentVersionStr = (runCatching {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                }.getOrNull() ?: BuildConfig.VERSION_NAME).removePrefix("v").removePrefix("V")

                val fetchResult = StormUpdateMirrorClient.fetchReleasesWithStatus()
                if (!fetchResult.anySourceSucceeded && fetchResult.releases.isEmpty()) {
                    Toast.makeText(appContext, R.string.v800_update_error, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val activity = unwrapActivity(context)
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    return@launch
                }

                val currentVersion = Version.parseOrNull(currentVersionStr) ?: Version.fromString(BuildConfig.VERSION_NAME)
                val channel = GitHubUpdateChannel.fromPreference(
                    defaultPrefs.getString(PREF_KEY_GITHUB_UPDATE_CHANNEL, GitHubUpdateChannel.STABLE.preferenceValue)
                )
                val preferLegacyApk = !runCatching {
                    appContext.resources.getBoolean(R.bool.adrenotools_enabled)
                }.getOrDefault(true)

                val candidate = GitHubReleaseSelector.selectProduction(
                    releases = fetchResult.releases,
                    currentVersion = currentVersion,
                    channel = channel,
                    skippedVersion = null,
                    preferLegacyApk = preferLegacyApk,
                )

                if (candidate != null) {
                    showStyledUpdateDialog(activity, owner, candidate, currentVersionStr)
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.v800_update_up_to_date_title)
                        .setMessage(appContext.getString(R.string.v800_update_up_to_date_msg, currentVersionStr))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } catch (_: Throwable) {
                Toast.makeText(appContext, R.string.v800_update_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showStyledUpdateDialog(
        activity: Activity,
        owner: LifecycleOwner?,
        candidate: GitHubReleaseCandidate,
        currentVersionStr: String,
    ) {
        val appContext = activity.applicationContext
        val remoteVersion = candidate.release.tagName.removePrefix("v").removePrefix("V")
        val rawBody = candidate.release.body.ifBlank { candidate.release.name }
        val markwon = runCatching {
            EntryPointAccessors.fromApplication(appContext, UpdateEntryPoint::class.java).markwon()
        }.getOrNull()
        val renderedBody = markwon?.toMarkdown(rawBody) ?: rawBody

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_styled_update, null, false)
        val tvCurrent = dialogView.findViewById<TextView>(R.id.tv_current_version)
        val tvNew = dialogView.findViewById<TextView>(R.id.tv_new_version)
        val tvChangelog = dialogView.findViewById<TextView>(R.id.tv_changelog)
        val btnUpdate = dialogView.findViewById<Button>(R.id.btn_update_now)
        val btnLater = dialogView.findViewById<Button>(R.id.btn_update_later)
        val btnSkip = dialogView.findViewById<Button>(R.id.btn_skip_version)

        tvCurrent?.text = currentVersionStr
        tvNew?.text = remoteVersion
        tvChangelog?.text = renderedBody

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnUpdate?.setOnClickListener {
            dialog.dismiss()
            startVerifiedUpdateDownload(activity, owner, candidate)
        }
        btnLater?.setOnClickListener { dialog.dismiss() }
        btnSkip?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startVerifiedUpdateDownload(
        activity: Activity,
        owner: LifecycleOwner?,
        candidate: GitHubReleaseCandidate,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activeDownloadJob?.isActive == true) return
        val appContext = activity.applicationContext

        val appUpdate = AppUpdate(
            type = AppUpdate.Type.PRODUCTION,
            id = candidate.asset.id,
            downloadUri = Uri.parse(candidate.asset.url),
            newVersion = candidate.version,
            description = candidate.release.body,
            binarySize = candidate.asset.size,
            updateDate = candidate.publishedAt,
            releaseTag = candidate.release.tagName,
            sourceReleaseUrl = candidate.release.htmlUrl,
        )

        val installManager = runCatching {
            EntryPointAccessors.fromApplication(appContext, UpdateEntryPoint::class.java).updateInstallManager()
        }.getOrElse {
            GitHubUpdateInstallManager(appContext)
        }

        val progressView = LayoutInflater.from(activity).inflate(R.layout.dialog_layout_update_download_progress, null, false)
        val progressBar = progressView.findViewById<ProgressBar>(R.id.progress_bar_download_progress)
        val progressText = progressView.findViewById<TextView>(R.id.text_download_progress)

        var progressDialog: AlertDialog? = null
        progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.downloading_update)
            .setView(progressView)
            .setPositiveButton(R.string.move_to_background) { _, _ ->
                progressDialog = null
            }
            .setCancelable(false)
            .show()

        val scope = owner?.lifecycleScope ?: fallbackScope
        activeDownloadJob = scope.launch {
            try {
                installManager.downloadAndInstallUpdate(appUpdate).collectLatest { progress ->
                    when (progress) {
                        is DownloadProgress.DownloadUpdate -> {
                            val totalSize = progress.totalSize
                            if (totalSize > 0L) {
                                val pct = ((progress.downloadedBytes.toDouble() / totalSize) * 100.0).toInt().coerceIn(0, 100)
                                val downloadedMb = progress.downloadedBytes.toDouble() / (1024.0 * 1024.0)
                                val totalMb = totalSize.toDouble() / (1024.0 * 1024.0)
                                progressBar?.isIndeterminate = false
                                progressBar?.progress = pct
                                progressText?.text = appContext.getString(R.string.download_progress_sizes, downloadedMb, totalMb)
                            } else {
                                val downloadedMb = progress.downloadedBytes.toDouble() / (1024.0 * 1024.0)
                                progressBar?.isIndeterminate = true
                                progressText?.text = if (downloadedMb > 0.01) {
                                    String.format(java.util.Locale.US, "%.1f MB", downloadedMb)
                                } else {
                                    ""
                                }
                            }
                        }
                        is DownloadProgress.DownloadComplete -> {
                            runCatching { progressDialog?.dismiss() }
                            progressDialog = null
                        }
                        is DownloadProgress.DownloadFailed -> {
                            runCatching { progressDialog?.dismiss() }
                            progressDialog = null
                            Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                        }
                        is DownloadProgress.InstallPermissionRequired -> {
                            runCatching { progressDialog?.dismiss() }
                            progressDialog = null
                            Toast.makeText(appContext, R.string.update_install_permission_required, Toast.LENGTH_LONG).show()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.isFinishing && !activity.isDestroyed) {
                                if (owner != null) {
                                    val pendingFile = File(progress.apkPath)
                                    val resumeObserver = object : LifecycleEventObserver {
                                        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                                            if (event == Lifecycle.Event.ON_RESUME) {
                                                val concreteMgr = (installManager as? GitHubUpdateInstallManager)
                                                    ?: GitHubUpdateInstallManager(appContext)
                                                if (concreteMgr.canRequestPackageInstalls()) {
                                                    source.lifecycle.removeObserver(this)
                                                    if (!concreteMgr.openInstaller(pendingFile)) {
                                                        Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    owner.lifecycle.addObserver(resumeObserver)
                                }
                                runCatching {
                                    activity.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${appContext.packageName}"),
                                        )
                                    )
                                }.onFailure {
                                    Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                runCatching { progressDialog?.dismiss() }
                progressDialog = null
                Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
