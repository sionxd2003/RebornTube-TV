package com.reborntube.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PairingState {
    private val mutableStatus = MutableStateFlow(ReceiverStatus("Iniciando RebornTube TV…"))
    val status = mutableStatus.asStateFlow()

    internal fun update(status: ReceiverStatus) {
        mutableStatus.value = status
    }
}
