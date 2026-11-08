package com.kieronquinn.app.classicpowermenu.ui.screens.settings.poweroptions

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.model.settings.SettingsItem
import com.kieronquinn.app.classicpowermenu.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.classicpowermenu.ui.base.BackAvailable
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.generic.SettingsGenericFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsPowerOptionsFragment : SettingsGenericFragment(), BackAvailable, AutoExpandOnRotate {

    private val viewModel by viewModel<SettingsPowerOptionsViewModel>()
    override val applyStretchOverscrollCompat = false

    /** 長按 IntegrityAPI 按鈕開啟的 App：摘要顯示目前設定的套件名稱 */
    private val integrityLongPressItem by lazy {
        SettingsItem.Setting(
            R.drawable.ic_integrity,
            getString(R.string.settings_power_options_integrity_long_press),
            getIntegrityLongPressSummary(),
            tapAction = this::showIntegrityPackageDialog
        )
    }

    override val items by lazy {
        listOf(
            SettingsItem.Setting(
                R.drawable.ic_power_options_rearrange,
                getString(R.string.settings_power_options_rearrange),
                getText(R.string.settings_power_options_rearrange_desc),
                tapAction = viewModel::onRearrangeClicked
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_power_options_hide_when_locked,
                getString(R.string.settings_power_options_hide_when_locked),
                getText(R.string.settings_power_options_hide_when_locked_desc),
                viewModel::hideWhenLocked
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_power_options_open_collapsed,
                getString(R.string.settings_power_options_open_collapsed),
                getText(R.string.settings_power_options_open_collapsed_desc),
                viewModel::openCollapsed
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_power_options_allow_rotation,
                getString(R.string.settings_power_options_allow_rotate),
                getText(R.string.settings_power_options_allow_rotate_desc),
                viewModel::allowRotation,
                tapAction = this::refreshItems
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_power_options_allow_full_rotation,
                getString(R.string.settings_power_options_allow_rotate_full),
                getText(R.string.settings_power_options_allow_rotate_full_desc),
                viewModel::allowFullRotation,
                enabled = { viewModel.allowRotation }
            ),
            integrityLongPressItem
        )
    }

    private fun refreshItems(isChecked: Boolean): Boolean {
        recyclerView.invoke().adapter?.notifyDataSetChanged()
        return true
    }

    private fun getIntegrityLongPressSummary(): CharSequence {
        val pkg = viewModel.integrityLongPressPackage
        val current = pkg.ifBlank { getString(R.string.settings_power_options_integrity_long_press_not_set) }
        return getString(R.string.settings_power_options_integrity_long_press_desc) + "\n" + current
    }

    private fun showIntegrityPackageDialog() {
        val editText = EditText(requireContext()).apply {
            setText(viewModel.integrityLongPressPackage)
            hint = "com.example.app"
            setSingleLine()
            val pad = resources.getDimensionPixelSize(R.dimen.margin_16)
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_power_options_integrity_long_press)
            .setMessage(R.string.settings_power_options_integrity_long_press_desc)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.integrityLongPressPackage = editText.text.toString().trim()
                integrityLongPressItem.content = getIntegrityLongPressSummary()
                recyclerView.invoke().adapter?.notifyDataSetChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

}
