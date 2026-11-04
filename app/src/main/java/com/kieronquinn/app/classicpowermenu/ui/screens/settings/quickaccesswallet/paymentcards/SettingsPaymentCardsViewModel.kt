package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.components.navigation.ContainerNavigation
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.PaymentCardCustomizationRepository
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.PaymentCardCustomizationRepository.PaymentCard
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.PaymentCardCustomizationRepository.PaymentCardsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 *  付款卡設定頁（Customize / Visibility 共用）的 ViewModel
 */
abstract class SettingsPaymentCardsViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    /** 第一次進入頁面時呼叫：向 Google Wallet 讀取付款卡（只會讀一次） */
    abstract fun load(activityContext: Context)

    /** Visibility：切換顯示 / 隱藏 */
    abstract fun onVisibilityToggled(item: Item)

    /** Customize：恢復原始卡面 */
    abstract fun onResetArtwork(item: Item)

    abstract fun onBackPressed()

    sealed class State {
        object Loading: State()
        data class Loaded(val items: List<Item>): State()
        data class Error(@StringRes val messageRes: Int): State()
    }

    data class Item(
        val card: PaymentCard,
        val visible: Boolean,
        /** 自訂卡面縮圖；null 代表使用原始卡面 */
        val customArtwork: Bitmap?
    ) {
        val id = card.id
    }

}

class SettingsPaymentCardsViewModelImpl(
    private val repository: PaymentCardCustomizationRepository,
    private val containerNavigation: ContainerNavigation
): SettingsPaymentCardsViewModel() {

    companion object {
        // 列表縮圖寬度（px），足夠清楚又不浪費記憶體
        private const val PREVIEW_WIDTH = 400
    }

    override val state = MutableStateFlow<State>(State.Loading)

    // 從 Google Wallet 讀到的卡片（讀一次後快取，設定變更時只重建列表，不重新向 Wallet 讀取）
    private var cards: List<PaymentCard>? = null
    private var loadStarted = false

    init {
        // 自訂卡面存檔 / 移除、顯示狀態變更後，重新整理列表
        viewModelScope.launch {
            repository.changes.collect {
                refreshItems()
            }
        }
    }

    override fun load(activityContext: Context) {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch {
            state.emit(State.Loading)
            when (val result = repository.loadPaymentCards(activityContext)) {
                is PaymentCardsResult.Success -> {
                    if (result.cards.isEmpty()) {
                        state.emit(State.Error(R.string.settings_quick_access_wallet_payment_error_no_cards))
                    } else {
                        cards = result.cards
                        refreshItems()
                    }
                }
                is PaymentCardsResult.Unavailable -> {
                    state.emit(State.Error(R.string.settings_quick_access_wallet_payment_error_unavailable))
                }
                is PaymentCardsResult.Error -> {
                    state.emit(State.Error(R.string.settings_quick_access_wallet_payment_error_generic))
                }
            }
        }
    }

    private suspend fun refreshItems() {
        val cards = cards ?: return
        val items = cards.map {
            Item(
                card = it,
                visible = !repository.isCardHidden(it.id),
                customArtwork = repository.loadCustomCardArtPreview(it.id, PREVIEW_WIDTH)
            )
        }
        state.emit(State.Loaded(items))
    }

    override fun onVisibilityToggled(item: Item) {
        // item.visible 是切換前的狀態：目前顯示 → 改為隱藏
        repository.setCardHidden(item.id, item.visible)
    }

    override fun onResetArtwork(item: Item) {
        viewModelScope.launch {
            repository.clearCustomCardArt(item.id)
        }
    }

    override fun onBackPressed() {
        viewModelScope.launch {
            containerNavigation.navigateBack()
        }
    }

}
