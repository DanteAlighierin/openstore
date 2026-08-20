package dev.mi6e4ka.openstore.internal.installer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

sealed interface DownloadEvent {
    val requestId: String

    data class Progress(
        override val requestId: String,
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadEvent

    data class Success(
        override val requestId: String,
        val packageName: String,
        val files: List<File>
    ) : DownloadEvent

    data class Error(
        override val requestId: String,
        val throwable: Throwable?
    ) : DownloadEvent

    data class Canceled(
        override val requestId: String
    ) : DownloadEvent

    data class HashMismatch(
        override val requestId: String,
        val files: List<File>,
        val expectedHash: String?,
        val actualHash: String?
    ) : DownloadEvent
}

class DownloadEventFlow {
    private val _events = MutableSharedFlow<DownloadEvent>(
        replay = 1,
        extraBufferCapacity = 8
    )
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun tryEmit(event: DownloadEvent): Boolean {
        return _events.tryEmit(event)
    }
}
