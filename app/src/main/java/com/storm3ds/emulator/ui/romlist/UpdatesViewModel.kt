package com.storm3ds.emulator.ui.romlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.storm3ds.emulator.domain.model.DownloadProgress
import com.storm3ds.emulator.domain.model.appupdate.AppUpdate
import com.storm3ds.emulator.domain.repositories.UpdatesRepository
import com.storm3ds.emulator.domain.services.UpdateInstallManager
import javax.inject.Inject

sealed class ManualUpdateResult {
    object Checking : ManualUpdateResult()
    data class UpdateAvailable(val update: AppUpdate) : ManualUpdateResult()
    object UpToDate : ManualUpdateResult()
    data class Error(val message: String?) : ManualUpdateResult()
}

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updatesRepository: UpdatesRepository,
    private val updateInstallManager: UpdateInstallManager,
) : ViewModel() {

    private val _appUpdate = Channel<AppUpdate>(Channel.CONFLATED)
    val appUpdate = _appUpdate.receiveAsFlow()

    private val _updateDownloadProgressEvent = Channel<DownloadProgress>(Channel.CONFLATED)
    val updateDownloadProgressEvent = _updateDownloadProgressEvent.receiveAsFlow()

    private val _manualUpdateResult = MutableSharedFlow<ManualUpdateResult>(extraBufferCapacity = 4)
    val manualUpdateResult: SharedFlow<ManualUpdateResult> = _manualUpdateResult.asSharedFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        checkJob = viewModelScope.launch {
            updatesRepository.checkNewUpdate().onSuccess {
                if (it != null) {
                    _appUpdate.send(it)
                }
            }
        }
    }

    fun checkForUpdatesManually() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _manualUpdateResult.emit(ManualUpdateResult.Checking)
            val result = updatesRepository.checkNewUpdateManually()
            result.onSuccess { update ->
                if (update != null) {
                    _manualUpdateResult.emit(ManualUpdateResult.UpdateAvailable(update))
                    _appUpdate.send(update)
                } else {
                    _manualUpdateResult.emit(ManualUpdateResult.UpToDate)
                }
            }.onFailure { e ->
                _manualUpdateResult.emit(ManualUpdateResult.Error(e.message))
            }
        }
    }

    fun checkForUpdatesSilently() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            val result = updatesRepository.checkNewUpdate(ignorePreferences = false)
            result.onSuccess { update ->
                if (update != null) {
                    _appUpdate.send(update)
                }
            }
        }
    }

    fun downloadUpdate(update: AppUpdate) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                updateInstallManager.downloadAndInstallUpdate(update).collectLatest {
                    _updateDownloadProgressEvent.send(it)
                    if (it is DownloadProgress.DownloadComplete) {
                        updatesRepository.notifyUpdateDownloaded(update)
                    }
                }
            } catch (_: Throwable) {
                _updateDownloadProgressEvent.trySend(DownloadProgress.DownloadFailed)
            }
        }
    }

    fun skipUpdate(update: AppUpdate) {
        updatesRepository.skipUpdate(update)
    }
}
