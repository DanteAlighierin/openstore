package dev.mi6e4ka.openstore.internal.installer

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class UninstallerEventFlow() {
    private val _events = Channel<Unit>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun trigger() {
        _events.trySend(Unit)
    }
}
