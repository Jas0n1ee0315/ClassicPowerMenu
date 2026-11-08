package com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.quickaccesswallet.CPMQuickAccessWalletClientImpl
import android.service.quickaccesswallet.GetWalletCardsError
import android.service.quickaccesswallet.GetWalletCardsRequest
import android.service.quickaccesswallet.GetWalletCardsResponse
import android.service.quickaccesswallet.QuickAccessWalletClient
import android.util.Log
import com.android.systemui.plugins.cardcustomization.WalletCardCustomizationProvider
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.ImageProcessor
import com.kieronquinn.app.classicpowermenu.service.container.CPMServiceContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 *  付款卡（Payment Cards）的自訂設定：
 *  1. Visibility：隱藏 / 顯示個別付款卡
 *  2. Customize：替換卡面圖片（裁切邏輯沿用 ZetaCard）
 *
 *  資料都存在 CPM 自己的 app 內部空間：
 *  - 隱藏清單：SharedPreferences `payment_card_customization`
 *  - 自訂卡面：`filesDir/payment_card_art/{SHA-256(cardId)}.png`
 */
interface PaymentCardCustomizationRepository: WalletCardCustomizationProvider {

    /** 有設定變更時發出事件（讓設定頁面重新整理） */
    val changes: SharedFlow<Unit>

    /** 從 Google Wallet（Quick Access Wallet 服務）讀取目前的付款卡清單 */
    suspend fun loadPaymentCards(activityContext: Context): PaymentCardsResult

    fun setCardHidden(cardId: String, hidden: Boolean)

    /** 儲存自訂卡面（已裁切成 ZetaCard 的輸出尺寸 1400x880） */
    suspend fun saveCustomCardArt(cardId: String, bitmap: Bitmap)

    /** 移除自訂卡面，恢復 Google Wallet 原始卡面 */
    suspend fun clearCustomCardArt(cardId: String)

    /** 讀取自訂卡面縮圖（設定頁列表用） */
    suspend fun loadCustomCardArtPreview(cardId: String, targetWidth: Int): Bitmap?

    data class PaymentCard(
        val id: String,
        val label: CharSequence,
        val contentDescription: CharSequence?,
        val originalDrawable: Drawable?
    )

    sealed class PaymentCardsResult {
        data class Success(val cards: List<PaymentCard>): PaymentCardsResult()
        object Unavailable: PaymentCardsResult()
        data class Error(val message: CharSequence?): PaymentCardsResult()
    }

}

class PaymentCardCustomizationRepositoryImpl(
    private val context: Context,
    private val serviceContainer: CPMServiceContainer,
    private val settings: com.kieronquinn.app.classicpowermenu.components.settings.Settings
): PaymentCardCustomizationRepository {

    companion object {
        private const val TAG = "CPMPaymentCards"
        private const val PREFS_NAME = "payment_card_customization"
        private const val KEY_HIDDEN_CARDS = "hidden_payment_cards"
        private const val ART_DIRECTORY = "payment_card_art"

        // 向 Google Wallet 要求的卡面尺寸（與 ZetaCard 的卡片比例 700:440 一致）
        private const val REQUEST_CARD_WIDTH = 700
        private const val REQUEST_CARD_HEIGHT = 440
        private const val REQUEST_ICON_SIZE = 64
        private const val REQUEST_MAX_CARDS = 20

        // 讀取卡片的逾時（服務沒回應時避免設定頁一直轉圈）
        private const val LOAD_TIMEOUT_MS = 15_000L
    }

    private val sharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val artDirectory by lazy {
        File(context.filesDir, ART_DIRECTORY)
    }

    // 電源選單每次開啟都會讀卡面，這裡做一層記憶體快取，key 為卡片 ID
    private val drawableCache = HashMap<String, Drawable>()

    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ---------- WalletCardCustomizationProvider（給 QuickAccessWallet 模組呼叫） ----------

    override fun isCardHidden(cardId: String): Boolean {
        return getHiddenCards().contains(cardId)
    }

    override fun hasCustomCardArt(cardId: String): Boolean {
        return getArtFile(cardId).isFile
    }

    override fun shouldRememberLastSelectedCard(): Boolean {
        return settings.quickAccessWalletRememberLastCard
    }

    override fun getCustomCardDrawable(cardId: String): Drawable? {
        synchronized(drawableCache) {
            drawableCache[cardId]?.let { return it }
        }
        val file = getArtFile(cardId)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        val drawable = BitmapDrawable(context.resources, bitmap)
        synchronized(drawableCache) {
            drawableCache[cardId] = drawable
        }
        return drawable
    }

    // ---------- 設定頁用 ----------

    override fun setCardHidden(cardId: String, hidden: Boolean) {
        val current = getHiddenCards().toMutableSet()
        if (hidden) current.add(cardId) else current.remove(cardId)
        // 注意：getStringSet 回傳的 Set 不可直接修改，這裡一律寫入新的 Set
        sharedPreferences.edit().putStringSet(KEY_HIDDEN_CARDS, HashSet(current)).apply()
        changes.tryEmit(Unit)
    }

    override suspend fun saveCustomCardArt(cardId: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        ImageProcessor.writePng(getArtFile(cardId), bitmap)
        synchronized(drawableCache) { drawableCache.remove(cardId) }
        changes.tryEmit(Unit)
        Unit
    }

    override suspend fun clearCustomCardArt(cardId: String) = withContext(Dispatchers.IO) {
        getArtFile(cardId).delete()
        synchronized(drawableCache) { drawableCache.remove(cardId) }
        changes.tryEmit(Unit)
        Unit
    }

    override suspend fun loadCustomCardArtPreview(cardId: String, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        ImageProcessor.decodePreview(getArtFile(cardId), targetWidth)
    }

    override suspend fun loadPaymentCards(activityContext: Context): PaymentCardCustomizationRepository.PaymentCardsResult {
        return try {
            // 需要 CPM 的 root 服務代為綁定 Google Wallet 的 Quick Access Wallet 服務
            val service = serviceContainer.runWithService { it }
            val client = withContext(Dispatchers.Main) {
                CPMQuickAccessWalletClientImpl(activityContext, service)
            }
            if (!client.isWalletServiceAvailable) {
                return PaymentCardCustomizationRepository.PaymentCardsResult.Unavailable
            }
            val result = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                client.getWalletCardsSuspend()
            } ?: PaymentCardCustomizationRepository.PaymentCardsResult.Error(null)
            withContext(Dispatchers.Main) {
                client.disconnect()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load payment cards", e)
            PaymentCardCustomizationRepository.PaymentCardsResult.Error(e.message)
        }
    }

    private suspend fun QuickAccessWalletClient.getWalletCardsSuspend() = suspendCancellableCoroutine<PaymentCardCustomizationRepository.PaymentCardsResult> { continuation ->
        val request = GetWalletCardsRequest(REQUEST_CARD_WIDTH, REQUEST_CARD_HEIGHT, REQUEST_ICON_SIZE, REQUEST_MAX_CARDS)
        getWalletCards(context.mainExecutor, request, object: QuickAccessWalletClient.OnWalletCardsRetrievedCallback {
            override fun onWalletCardsRetrieved(response: GetWalletCardsResponse) {
                // 與電源選單一致：ID 長度 <= 1 的是佔位卡，不列出
                val cards = response.walletCards.filter { it.cardId.length > 1 }.map {
                    PaymentCardCustomizationRepository.PaymentCard(
                        id = it.cardId,
                        label = it.cardLabel ?: "",
                        contentDescription = it.contentDescription,
                        originalDrawable = it.cardImage?.loadDrawable(context)
                    )
                }
                if (continuation.isActive) {
                    continuation.resume(PaymentCardCustomizationRepository.PaymentCardsResult.Success(cards))
                }
            }

            override fun onWalletCardRetrievalError(error: GetWalletCardsError) {
                if (continuation.isActive) {
                    continuation.resume(PaymentCardCustomizationRepository.PaymentCardsResult.Error(error.message))
                }
            }
        })
    }

    private fun getHiddenCards(): Set<String> {
        return sharedPreferences.getStringSet(KEY_HIDDEN_CARDS, emptySet()) ?: emptySet()
    }

    /**
     *  卡片 ID 可能含有不適合當檔名的字元，所以用 SHA-256 雜湊當檔名
     */
    private fun getArtFile(cardId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(cardId.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(artDirectory, "$name.png")
    }

}
