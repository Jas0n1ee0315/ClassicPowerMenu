package com.kieronquinn.app.classicpowermenu.ui.screens.settings.devicecontrols

import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.model.settings.SettingsItem
import com.kieronquinn.app.classicpowermenu.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.classicpowermenu.ui.base.BackAvailable
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.generic.SettingsGenericFragment
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.switched.SettingsSwitchedFragment
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsDeviceControlsFragment: SettingsSwitchedFragment(), BackAvailable, AutoExpandOnRotate {

    private val viewModel by viewModel<SettingsDeviceControlsViewModel>()

    override val switchText by lazy {
        getString(R.string.settings_device_controls_switch)
    }

    override val onSwitchClicked by lazy {
        return@lazy viewModel::onSwitchClicked
    }

    override val switchChecked by lazy {
        viewModel.deviceControlsShow
    }

    override val switchStartState by lazy {
        viewModel.deviceControlsShowInitialState
    }

    override val adapterEnabled by lazy {
        viewModel.deviceControlsShow
    }

    override val adapterEnabledInitialState by lazy {
        viewModel.deviceControlsShowInitialState
    }

    override val applyStretchOverscrollCompat = false

    override val items by lazy {
        listOf(
            SettingsItem.SwitchSetting(
                R.drawable.ic_power_options_hide_when_locked,
                getString(R.string.settings_device_controls_access_while_locked),
                getText(R.string.settings_device_controls_access_while_locked_desc),
                viewModel::allowWhileLocked
            ),
            // 狀態載入失敗時自動重新連線（結束控制項 App 程序後重新訂閱）
            SettingsItem.SwitchSetting(
                R.drawable.ic_reboot,
                getString(R.string.settings_device_controls_auto_reconnect),
                getText(R.string.settings_device_controls_auto_reconnect_desc),
                viewModel::autoReconnect
            )
        )
    }

}