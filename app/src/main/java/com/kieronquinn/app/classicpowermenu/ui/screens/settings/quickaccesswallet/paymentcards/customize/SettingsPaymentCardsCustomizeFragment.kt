package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.customize

import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.SettingsPaymentCardsAdapter
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.SettingsPaymentCardsFragment
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.SettingsPaymentCardsViewModel

/**
 *  Payment Cards → Customize：替換付款卡卡面
 *
 *  點卡片 → 選單（選擇圖片 / 恢復原始卡面）→ 系統相簿挑選器 → [CardArtCropActivity] 裁切存檔
 */
class SettingsPaymentCardsCustomizeFragment: SettingsPaymentCardsFragment() {

    companion object {
        private const val STATE_PENDING_CARD_ID = "pending_card_id"
    }

    override val mode = SettingsPaymentCardsAdapter.Mode.CUSTOMIZE
    override val titleRes = R.string.settings_quick_access_wallet_payment_customize

    // 正在替哪張卡挑照片（挑選器回來時要知道是哪張卡；畫面重建時從 savedInstanceState 還原）
    private var pendingCardId: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cardId = pendingCardId ?: return@registerForActivityResult
        pendingCardId = null
        if (uri == null) return@registerForActivityResult
        startActivity(CardArtCropActivity.createIntent(requireContext(), cardId, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCardId = savedInstanceState?.getString(STATE_PENDING_CARD_ID)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_CARD_ID, pendingCardId)
    }

    override fun onCustomizeClicked(item: SettingsPaymentCardsViewModel.Item) {
        val hasCustomArtwork = item.customArtwork != null
        if (!hasCustomArtwork) {
            // 還沒有自訂卡面，直接開相簿
            launchImagePicker(item)
            return
        }
        val options = arrayOf(
            getString(R.string.settings_quick_access_wallet_payment_customize_choose),
            getString(R.string.settings_quick_access_wallet_payment_customize_reset)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.card.label.ifBlank { item.card.contentDescription ?: "" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchImagePicker(item)
                    1 -> viewModel.onResetArtwork(item)
                }
            }
            .show()
    }

    private fun launchImagePicker(item: SettingsPaymentCardsViewModel.Item) {
        pendingCardId = item.id
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

}
