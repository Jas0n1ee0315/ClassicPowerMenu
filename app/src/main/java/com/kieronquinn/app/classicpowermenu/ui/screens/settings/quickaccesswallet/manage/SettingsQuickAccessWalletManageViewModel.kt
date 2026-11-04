package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.manage

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.components.navigation.ContainerNavigation
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.GoogleApiRepository
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.GoogleWalletRepository
import com.kieronquinn.app.classicpowermenu.components.settings.Settings
import com.kieronquinn.app.classicpowermenu.model.quickaccesswallet.WalletLoyaltyCardViewInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.ManualLoyaltyCard
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.ManualLoyaltyCardRepository

abstract class SettingsQuickAccessWalletManageViewModel: ViewModel() {

    abstract val state: MutableStateFlow<State>

    abstract fun onCardVisibilityClicked(card: RearrangeLoyaltyCard)
    abstract fun onCardOrderChanged(cards: List<RearrangeLoyaltyCard>)
    /** 儲存自訂主標題 / 副標題；null 或空白代表恢復原始文字 */
    abstract fun onCardEdited(card: RearrangeLoyaltyCard, issuerName: String?, title: String?)
    /** 手動會員卡：新增或更新（依 id） */
    abstract fun onManualCardSaved(card: ManualLoyaltyCard)
    /** 手動會員卡：刪除（滑動刪除），列表已由畫面先行移除 */
    abstract fun onManualCardDeleted(card: RearrangeLoyaltyCard)
    /** 手動會員卡：復原剛刪除的卡片 */
    abstract fun onManualCardRestored(card: ManualLoyaltyCard)
    /** 取得手動會員卡完整資料（編輯 / 刪除前備份用） */
    abstract fun getManualCard(id: String): ManualLoyaltyCard?
    abstract fun onBackPressed()
    abstract fun onSignInClicked()
    abstract fun onPageLoad()
    abstract fun syncLoyaltyCards()

    sealed class State {
        object Loading: State()
        object SignInRequired: State()
        data class Loaded(val cards: List<RearrangeLoyaltyCard>): State()
        data class Error(val type: ErrorType): State()
    }

    data class RearrangeLoyaltyCard(var visible: Boolean, val info: WalletLoyaltyCardViewInfo) {
        val id = info.id
        /** 是否為手動新增的會員卡（只有手動卡片可以滑動刪除、編輯卡號與條碼類型） */
        val isManual = ManualLoyaltyCardRepository.isManualId(id)
    }

    enum class ErrorType(@StringRes val contentRes: Int) {
        ERROR(R.string.settings_quick_access_wallet_manage_error_generic), NO_CARDS(R.string.settings_quick_access_wallet_manage_error_no_cards)
    }
}

class SettingsQuickAccessWalletManageViewModelImpl(private val walletRepository: GoogleWalletRepository, private val googleApiRepository: GoogleApiRepository, private val settings: Settings, private val containerNavigation: ContainerNavigation, private val loyaltyCardOverridesRepository: com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.LoyaltyCardOverridesRepository, private val manualLoyaltyCardRepository: ManualLoyaltyCardRepository): SettingsQuickAccessWalletManageViewModel() {

    override fun onManualCardSaved(card: ManualLoyaltyCard) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manualLoyaltyCardRepository.saveCard(card) }
            updateLoyaltyCards()
        }
    }

    override fun onManualCardDeleted(card: RearrangeLoyaltyCard) {
        if (!card.isManual) return // 防呆：Google 同步的卡片不可刪除
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manualLoyaltyCardRepository.deleteCard(card.id) }
            // 不重新載入列表：畫面已用動畫移除該項目，重新載入會打斷動畫。
            // 顯示 / 隱藏、排序設定中的 id 保留，復原時卡片會回到原本的位置與狀態。
        }
    }

    override fun onManualCardRestored(card: ManualLoyaltyCard) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manualLoyaltyCardRepository.saveCard(card) }
            updateLoyaltyCards()
        }
    }

    override fun getManualCard(id: String): ManualLoyaltyCard? = manualLoyaltyCardRepository.getCard(id)

    override val state = MutableStateFlow<State>(State.Loading)

    override fun onCardEdited(card: RearrangeLoyaltyCard, issuerName: String?, title: String?) {
        viewModelScope.launch {
            loyaltyCardOverridesRepository.setOverrides(card.id, issuerName, title)
            // 重新讀取列表，套用新的標題
            updateLoyaltyCards()
        }
    }

    override fun syncLoyaltyCards() {
        viewModelScope.launch {
            state.emit(State.Loading)
            walletRepository.syncValuables()
            updateLoyaltyCards()
        }
    }

    override fun onPageLoad() {
        viewModelScope.launch {
            if (!googleApiRepository.isSignedIn()) return@launch state.emit(State.SignInRequired)
            updateLoyaltyCards()
        }
    }

    private suspend fun updateLoyaltyCards() {
        val cards = walletRepository.getLoyaltyCards({ false }, true)?.map {
            it as WalletLoyaltyCardViewInfo
        }

        if (cards == null) return state.emit(State.Error(ErrorType.ERROR))
        if (cards.isEmpty()) return state.emit(State.Error(ErrorType.NO_CARDS))

        val order = settings.quickAccessWalletLoyaltyCardsOrder
        val hidden = settings.quickAccessWalletLoyaltyCardsHidden
        val ordered = cards.sortedBy {
            val index = order.indexOf(it.id)
            // 尚未排序過的手動新增卡片放在最後（Google 新卡維持原本行為）
            if (index == -1 && ManualLoyaltyCardRepository.isManualId(it.id)) Int.MAX_VALUE else index
        }.map {
            RearrangeLoyaltyCard(!hidden.contains(it.id), it)
        }
        state.emit(State.Loaded(ordered))
    }

    override fun onCardVisibilityClicked(card: RearrangeLoyaltyCard) {
        viewModelScope.launch {
            if(card.visible){
                settings.quickAccessWalletLoyaltyCardsHidden = settings.quickAccessWalletLoyaltyCardsHidden.toMutableSet().apply {
                    remove(card.id)
                }.toList()
            }else{
                settings.quickAccessWalletLoyaltyCardsHidden = settings.quickAccessWalletLoyaltyCardsHidden.toMutableSet().apply {
                    add(card.id)
                }.toList()
            }
        }
    }

    override fun onCardOrderChanged(cards: List<RearrangeLoyaltyCard>) {
        viewModelScope.launch {
            settings.quickAccessWalletLoyaltyCardsOrder = cards.map { it.id }
        }
    }

    override fun onSignInClicked() {
        viewModelScope.launch {
            containerNavigation.navigate(SettingsQuickAccessWalletManageFragmentDirections.actionSettingsQuickAccessWalletManageFragmentToSettingsQuickAccessWalletSignInToGoogle())
        }
    }

    override fun onBackPressed() {
        viewModelScope.launch {
            containerNavigation.navigateBack()
        }
    }

}