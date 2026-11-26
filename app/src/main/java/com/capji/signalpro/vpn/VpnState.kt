package com.capji.signalpro.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VpnError { ESTABLISH_FAILED, REVOKED }

data class VpnStatus(
    val running: Boolean = false,
    val providerName: String = "",
    val server: String = "",
    /** Counter only; query names are never stored or logged. */
    val answeredQueries: Long = 0,
    val wifiLockHeld: Boolean = false,
    val startedAtElapsed: Long = 0,
    val error: VpnError? = null,
)

/** In-process state shared between the tunnel service and the UI. */
object VpnState {
    private val mutable = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = mutable.asStateFlow()

    internal fun clearError() = mutable.update { it.copy(error = null) }

    internal fun update(block: (VpnStatus) -> VpnStatus) = mutable.update(block)
}
