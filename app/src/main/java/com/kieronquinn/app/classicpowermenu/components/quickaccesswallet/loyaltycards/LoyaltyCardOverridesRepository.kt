package com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards

import android.content.Context

/**
 *  會員卡自訂標題（使用者在 Manage Account & Loyalty Cards 中編輯的主標題 / 副標題）。
 *
 *  刻意存在獨立的 SharedPreferences（`loyalty_card_overrides`），不寫進會員卡資料庫：
 *  從 Google 伺服器同步（syncValuables）只會更新資料庫，因此自訂標題永遠不會被覆蓋。
 *  只有使用者在編輯視窗清空欄位才會清除（登出帳號也會保留，重新登入同一帳號後即恢復）。
 *
 *  key 使用會員卡的 valuableId（與隱藏 / 排序設定相同的 ID，同步後不會改變）。
 */
interface LoyaltyCardOverridesRepository {

    /** 自訂主標題（畫面上的 E-Invoice 那一行），沒有自訂時回傳 null */
    fun getIssuerName(valuableId: String): String?

    /** 自訂副標題（畫面上的 Loyalty program 那一行），沒有自訂時回傳 null */
    fun getTitle(valuableId: String): String?

    /** 儲存自訂標題；傳入 null 或空白代表恢復 Google 原本的文字 */
    fun setOverrides(valuableId: String, issuerName: String?, title: String?)

}

class LoyaltyCardOverridesRepositoryImpl(context: Context): LoyaltyCardOverridesRepository {

    companion object {
        private const val PREFS_NAME = "loyalty_card_overrides"
        private const val KEY_PREFIX_ISSUER_NAME = "issuer_name_"
        private const val KEY_PREFIX_TITLE = "title_"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getIssuerName(valuableId: String): String? {
        return sharedPreferences.getString(KEY_PREFIX_ISSUER_NAME + valuableId, null)
    }

    override fun getTitle(valuableId: String): String? {
        return sharedPreferences.getString(KEY_PREFIX_TITLE + valuableId, null)
    }

    override fun setOverrides(valuableId: String, issuerName: String?, title: String?) {
        // commit()（同步寫入）確保存檔後立即落地，避免 App 被系統砍掉時遺失
        sharedPreferences.edit().apply {
            putOrRemove(KEY_PREFIX_ISSUER_NAME + valuableId, issuerName)
            putOrRemove(KEY_PREFIX_TITLE + valuableId, title)
        }.commit()
    }

    private fun android.content.SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) remove(key) else putString(key, trimmed)
    }

}
