package com.kieronquinn.app.classicpowermenu.model.power

/**
 *  電源選單下半部的內容區塊（依清單順序顯示）。
 *  原本只有 Cards（付款卡 + 會員卡同一個輪播）與 Controls，
 *  現在拆成「信用卡匣 → 會員卡匣 → 裝置控制」三段，各自獨立。
 */
sealed class PowerMenuContentItem(val itemType: PowerMenuContentItemType) {
    /** 信用卡匣：只放付款卡 */
    object PaymentCards : PowerMenuContentItem(PowerMenuContentItemType.PAYMENT_CARDS)
    /** 會員卡匣：只放會員卡 */
    object LoyaltyCards : PowerMenuContentItem(PowerMenuContentItemType.LOYALTY_CARDS)
    object Controls : PowerMenuContentItem(PowerMenuContentItemType.CONTROLS)
}

enum class PowerMenuContentItemType {
    PAYMENT_CARDS, LOYALTY_CARDS, CONTROLS
}
