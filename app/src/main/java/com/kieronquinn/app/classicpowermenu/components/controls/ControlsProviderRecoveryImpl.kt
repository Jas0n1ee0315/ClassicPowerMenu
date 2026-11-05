package com.kieronquinn.app.classicpowermenu.components.controls

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.systemui.util.extensions.ControlsProviderRecovery
import com.kieronquinn.app.classicpowermenu.components.settings.Settings
import com.kieronquinn.app.classicpowermenu.service.container.CPMServiceContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 *  [ControlsProviderRecovery] 的 App 端實作：讀取設定，並透過 Root 服務結束控制項 App 的程序。
 */
class ControlsProviderRecoveryImpl(
    private val settings: Settings,
    private val serviceContainer: CPMServiceContainer,
    private val scope: CoroutineScope
): ControlsProviderRecovery {

    companion object {
        private const val TAG = "ControlsRecovery"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override val isEnabled: Boolean
        get() = settings.deviceControlsAutoReconnect

    override fun killProviderProcesses(packageName: String, onComplete: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                serviceContainer.runWithService { it.killPackageProcesses(packageName) }
            }.onFailure {
                Log.e(TAG, "Failed to kill $packageName", it)
            }
            mainHandler.post(onComplete)
        }
    }

}
