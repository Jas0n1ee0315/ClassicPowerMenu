package com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.kieronquinn.app.classicpowermenu.model.quickaccesswallet.LoyaltyCard
import com.kieronquinn.app.classicpowermenu.utils.extensions.createEncryptedSharedPrefDestructively
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 *  手動新增的會員卡。
 *
 *  與 Google 同步的會員卡（[ValuablesDatabaseRepository]）完全分開存放：
 *  重新整理 / Google 端刪卡 / 登出清空資料庫，都不會影響這裡的卡片。
 *
 *  @param id       以 [ManualLoyaltyCardRepository.ID_PREFIX] 開頭，不會與 Google 卡片 ID 衝突
 *  @param name     會員卡名稱（對應電源選單的主標題 issuerName）
 *  @param subtitle 副標題（選填，對應 title；電源選單顯示「名稱 • 副標題」）
 *  @param number   會員卡號 = 條碼內容，也顯示在條碼下方
 *  @param type     條碼類型
 */
data class ManualLoyaltyCard(
    val id: String,
    val name: String,
    val subtitle: String,
    val number: String,
    val type: LoyaltyCard.RedemptionInfo.Type
) {

    /** 轉成與 Google 會員卡相同的 [LoyaltyCard]，預覽 / 直接顯示條碼 / 電源選單都能直接沿用 */
    fun toLoyaltyCard(): LoyaltyCard {
        return LoyaltyCard(
            valuableId = id,
            valuableImage = null, // 沒有 Google 卡面 logo → 使用預設的「首字圓形」卡面
            id = id,
            title = subtitle,
            issuerName = name,
            backgroundColor = DEFAULT_BACKGROUND_COLOR,
            redemptionInfo = LoyaltyCard.RedemptionInfo(
                identifier = id,
                _type = type.protoOrdinal,
                encodedValue = number,
                displayText = number,
                contentDescription = name
            )
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_SUBTITLE, subtitle)
        put(KEY_NUMBER, number)
        put(KEY_TYPE, type.name)
    }

    companion object {
        /** 手動卡片的預設卡面底色（深灰藍，白色文字對比足夠） */
        val DEFAULT_BACKGROUND_COLOR = Color.parseColor("#455A64")

        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_SUBTITLE = "subtitle"
        private const val KEY_NUMBER = "number"
        private const val KEY_TYPE = "type"

        fun fromJson(json: JSONObject): ManualLoyaltyCard? = runCatching {
            ManualLoyaltyCard(
                id = json.getString(KEY_ID),
                name = json.getString(KEY_NAME),
                subtitle = json.optString(KEY_SUBTITLE, ""),
                number = json.getString(KEY_NUMBER),
                type = LoyaltyCard.RedemptionInfo.Type.valueOf(json.getString(KEY_TYPE))
            )
        }.getOrNull()
    }

}

interface ManualLoyaltyCardRepository {

    companion object {
        /** 手動卡片 ID 前綴：用來判斷「可滑動刪除」「點擊一律開預覽」「隱藏在錢包中開啟」 */
        const val ID_PREFIX = "MANUAL_"

        fun isManualId(id: String?): Boolean = id?.startsWith(ID_PREFIX) == true

        fun newId(): String = ID_PREFIX + UUID.randomUUID().toString()
    }

    fun getCards(): List<ManualLoyaltyCard>
    fun getCard(id: String): ManualLoyaltyCard?

    /** 新增或更新（依 id）；新增的卡片放在最後 */
    fun saveCard(card: ManualLoyaltyCard)
    fun deleteCard(id: String)

}

/**
 *  以加密 SharedPreferences 存放（JSON 陣列），與 Google 同步用的 Room 資料庫分開。
 *  寫入使用 commit() 同步寫入，確保立即永久保存。
 */
class ManualLoyaltyCardRepositoryImpl(context: Context): ManualLoyaltyCardRepository {

    companion object {
        private const val PREFS_NAME = "manual_loyalty_cards"
        private const val KEY_CARDS = "cards"
    }

    private val prefs: SharedPreferences by lazy {
        context.createEncryptedSharedPrefDestructively(PREFS_NAME)
    }

    @Synchronized
    override fun getCards(): List<ManualLoyaltyCard> {
        val raw = prefs.getString(KEY_CARDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { ManualLoyaltyCard.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    override fun getCard(id: String): ManualLoyaltyCard? = getCards().find { it.id == id }

    @Synchronized
    override fun saveCard(card: ManualLoyaltyCard) {
        val cards = getCards().toMutableList()
        val index = cards.indexOfFirst { it.id == card.id }
        if (index >= 0) cards[index] = card else cards.add(card)
        write(cards)
    }

    @Synchronized
    override fun deleteCard(id: String) {
        write(getCards().filterNot { it.id == id })
    }

    private fun write(cards: List<ManualLoyaltyCard>) {
        val array = JSONArray().apply { cards.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_CARDS, array.toString()).commit()
    }

}
