package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.visibility

import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.SettingsPaymentCardsAdapter
import com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.SettingsPaymentCardsFragment

/**
 *  Payment Cards → Visibility：逐張切換付款卡在電源選單錢包中顯示 / 隱藏
 */
class SettingsPaymentCardsVisibilityFragment: SettingsPaymentCardsFragment() {

    override val mode = SettingsPaymentCardsAdapter.Mode.VISIBILITY
    override val titleRes = R.string.settings_quick_access_wallet_payment_visibility

}
