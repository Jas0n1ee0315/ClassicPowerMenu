package com.android.systemui.plugins.cardcustomization

import android.graphics.drawable.Drawable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 *  付款卡自訂介面（由 app 模組實作，透過 Koin 注入）。
 *
 *  QuickAccessWallet 模組本身不能直接依賴 app 模組，所以比照 [com.android.systemui.plugins.cardblur.WalletCardBlur]
 *  的做法：這裡只定義介面，實作放在 app 模組並註冊到 Koin。
 */
interface WalletCardCustomizationProvider {

    /**
     *  使用者是否在「Visibility」設定中隱藏了這張付款卡
     */
    fun isCardHidden(cardId: String): Boolean

    /**
     *  取得使用者自訂的卡面；沒有自訂時回傳 null，維持 Google Wallet 原本的卡面。
     *  會在背景執行緒呼叫，可以同步讀檔。
     */
    fun getCustomCardDrawable(cardId: String): Drawable?

    /**
     *  這張卡是否有自訂卡面（用來決定要不要套用「鎖定時模糊卡號」）
     */
    fun hasCustomCardArt(cardId: String): Boolean

    /**
     *  是否記住上次選取的信用卡。
     *  true（預設）：開啟電源選單時自動選取 Google Pay 目前的預設卡。
     *  false：每次都從排序的第一張卡開始。
     */
    fun shouldRememberLastSelectedCard(): Boolean

    /**
     *  使用者在「顯示／隱藏」頁拖曳設定的付款卡順序（卡片 ID 清單，由上到下）。
     *  沒有設定過時回傳空清單，維持 Google Wallet 原本的順序。
     *  不在清單中的卡（例如之後新加入 Wallet 的卡）由呼叫端排在最後，並維持 Wallet 原本的相對順序。
     */
    fun getCardOrder(): List<String>

}

object WalletCardCustomization: KoinComponent {

    private val customizationProvider by inject<WalletCardCustomizationProvider>()

    fun getProvider(): WalletCardCustomizationProvider {
        return customizationProvider
    }

}
