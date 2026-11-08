package com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.StrictMode
import android.util.Base64
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.android.systemui.plugin.globalactions.wallet.WalletCardViewInfo
import com.google.internal.tapandpay.v1.valuables.SyncValuablesRequestProto.SyncValuablesRequest
import com.google.internal.tapandpay.v1.valuables.SyncValuablesRequestProto.SyncValuablesRequest.SyncValuablesRequestInner.Request
import com.google.internal.tapandpay.v1.valuables.SyncValuablesResponseProto
import com.google.internal.tapandpay.v1.valuables.SyncValuablesResponseProto.SyncValuablesResponse.Inner.Valuables.Valuable
import com.google.internal.tapandpay.v1.valuables.CommonProto.GroupingInfo
import com.google.internal.tapandpay.v1.valuables.CommonProto.IssuerInfo
import com.google.internal.tapandpay.v1.valuables.CommonProto.RedemptionInfo
import com.google.internal.tapandpay.v1.valuables.ValuableWrapperProto
import com.google.internal.tapandpay.v1.valuables.ValuableWrapperProto.ValuableWrapper
import com.google.protobuf.ByteString
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.GooglePayConstants
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.GoogleWalletRepository.SyncValuablesResult
import com.kieronquinn.app.classicpowermenu.components.settings.EncryptedSettings
import com.kieronquinn.app.classicpowermenu.components.settings.Settings
import com.kieronquinn.app.classicpowermenu.model.quickaccesswallet.LoyaltyCard
import com.kieronquinn.app.classicpowermenu.model.quickaccesswallet.WalletLoyaltyCardViewInfo
import com.kieronquinn.app.classicpowermenu.model.quickaccesswallet.database.WalletValuable
import com.kieronquinn.app.classicpowermenu.utils.extensions.CONTENT_TYPE_PROTOBUF
import com.kieronquinn.app.classicpowermenu.utils.extensions.compress
import com.kieronquinn.app.classicpowermenu.utils.extensions.toColor
import com.kieronquinn.app.classicpowermenu.utils.extensions.toRequestBody
import com.kieronquinn.app.classicpowermenu.utils.room.EncryptedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.URL
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale


interface GoogleWalletRepository {

    suspend fun syncValuables(): SyncValuablesResult

    suspend fun getLoyaltyCards(onCardClicked: (LoyaltyCard) -> Boolean, ignoreSetting: Boolean = false): List<WalletCardViewInfo>?

    /**
     *  Current valuables from the database. Call [syncValuables] to trigger a sync.
     */
    fun getValuables(): Flow<List<LoyaltyCard>>


    enum class SyncValuablesResult {
        /**
         *  Sync was successful
         */
        SUCCESS,

        /**
         *  A non-fatal error, for example a network connection issue, occurred
         */
        ERROR,

        /**
         *  The request returned a bad auth response, and then token refresh also failed. Likely
         *  will require a re-log.
         */
        FATAL_ERROR
    }

}

class GoogleWalletRepositoryImpl(
    private val context: Context,
    private val settings: Settings,
    private val encryptedSettings: EncryptedSettings,
    private val googleApiRepository: GoogleApiRepository,
    private val valuablesDatabaseRepository: ValuablesDatabaseRepository,
    private val loyaltyCardOverridesRepository: LoyaltyCardOverridesRepository,
    /** 手動新增的會員卡（獨立存放，不受 Google 同步影響） */
    private val manualLoyaltyCardRepository: ManualLoyaltyCardRepository
): GoogleWalletRepository {

    companion object {
        private const val HEADER_REQUEST = "ExoBBA8FCxQMCAcJAwYOCg0QEhYYHA=="
    }

    private val isGooglePayInstalled by lazy {
        GooglePayConstants.isGooglePayInstalled(context.packageManager)
    }

    private val googleSansMedium by lazy {
        ResourcesCompat.getFont(context, R.font.google_sans_text_medium)!!
    }

    /** 目前是否鎖定（需要解鎖才能使用） */
    private fun isDeviceLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return keyguardManager.isDeviceLocked
    }

    init {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
    }

    override suspend fun syncValuables() = withContext(Dispatchers.IO) {
        syncValuablesInternal()
    }

    override suspend fun getLoyaltyCards(onCardClicked: (LoyaltyCard) -> Boolean, ignoreSetting: Boolean): List<WalletCardViewInfo>? {
        if((!settings.quickAccessWalletShowLoyaltyCards && !ignoreSetting) || !isGooglePayInstalled) return null
        // 實驗性「直接顯示條碼」：每次開啟電源選單建立卡片時讀取設定與鎖定狀態。
        // 鎖定中且不允許鎖定時存取 → 仍顯示原本的 logo 卡面，避免繞過「需先解鎖」的保護。
        val showCodeDirectly = settings.quickAccessWalletShowCodeDirectly &&
            (settings.quickAccessWalletAccessWhileLocked || !isDeviceLocked())
        // Google 同步的會員卡 + 手動新增的會員卡（顯示 / 隱藏、排序之後由呼叫端依卡片 ID 套用）
        val manualCards = withContext(Dispatchers.IO) {
            manualLoyaltyCardRepository.getCards().map { it.toLoyaltyCard() }
        }
        return (getValuables().first() + manualCards).map {
            WalletLoyaltyCardViewInfo(
                context, it, googleSansMedium, onCardClicked,
                showCodeDirectly = showCodeDirectly,
                useDummyCode = settings.developerContentCreatorMode
            )
        }
    }

    override fun getValuables(): Flow<List<LoyaltyCard>> {
        return valuablesDatabaseRepository.getWalletValuables().map { it.mapNotNull { it.toValuable() } }
    }

    private fun WalletValuable.toValuable(): LoyaltyCard? {
        val parts = ValuableWrapper.parseFrom(valuable.bytes).toCardParts() ?: return null

        val imageBytes = image?.bytes
        val bitmap = if (imageBytes != null) BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) else null
        // Google 伺服器給的原始標題（通用卡 / 優惠已換算成「主標題 + 副標題」）
        val originalTitle = parts.title
        val originalIssuerName = parts.issuerName
        val redemption = parts.redemptionInfo
        // 每次讀取時才套用使用者自訂標題（存在獨立設定檔，同步不會覆蓋）
        val loyaltyCard = LoyaltyCard(
            id,
            bitmap,
            parts.issuerInfo.id,
            loyaltyCardOverridesRepository.getTitle(id) ?: originalTitle,
            loyaltyCardOverridesRepository.getIssuerName(id) ?: originalIssuerName,
            parts.backgroundColor.toColor(),
            LoyaltyCard.RedemptionInfo(
                redemption.identifier,
                redemption.barcode.typeValue,
                redemption.barcode.encodedValue,
                redemption.barcode.displayText,
                redemption.barcode.displayText),
            originalTitle = originalTitle,
            originalIssuerName = originalIssuerName)
        return loyaltyCard
    }

    /**
     *  會員卡、通用卡（例如自行拍照新增的卡）、優惠（例如小北百貨）共用的欄位。
     *  三者在 proto 裡都是同樣的 IssuerInfo / RedemptionInfo / GroupingInfo。
     */
    private class CardParts(
        val issuerInfo: IssuerInfo,
        val redemptionInfo: RedemptionInfo,
        val groupingInfo: GroupingInfo,
        val isActive: Boolean,
        /** 主標題（發卡商名稱） */
        val issuerName: String,
        /** 副標題 */
        val title: String
    ) {
        val backgroundColor: com.google.type.Color
            get() = if (groupingInfo.hasBackgroundColor()) groupingInfo.backgroundColor
                else issuerInfo.backgroundColor
        val imageUri: String
            get() = groupingInfo.groupingImage.uri
    }

    private fun ValuableWrapper.toCardParts(): CardParts? = when (valuableCase) {
        ValuableWrapper.ValuableCase.LOYALTY_CARD -> loyaltyCard.run {
            CardParts(issuerInfo, redemptionInfo, groupingInfo, metadata.isActive,
                issuerInfo.issuerName, issuerInfo.title)
        }
        // 通用卡：名稱放在 title、issuer_name 是空的；副標題（例如「會員卡」、持卡人姓名）在 grouping_subtitle
        ValuableWrapper.ValuableCase.GENERIC_CARD -> genericCard.run {
            CardParts(issuerInfo, redemptionInfo, groupingInfo, metadata.isActive,
                issuerInfo.issuerName.ifBlank { groupingInfo.groupingTitle.ifBlank { issuerInfo.title } },
                groupingInfo.groupingSubtitle.ifBlank { issuerInfo.title })
        }
        ValuableWrapper.ValuableCase.SENSITIVE_GENERIC_PASS -> sensitiveGenericPass.run {
            CardParts(issuerInfo, redemptionInfo, groupingInfo, metadata.isActive,
                issuerInfo.issuerName.ifBlank { groupingInfo.groupingTitle.ifBlank { issuerInfo.title } },
                groupingInfo.groupingSubtitle.ifBlank { issuerInfo.title })
        }
        ValuableWrapper.ValuableCase.OFFER -> offer.run {
            CardParts(issuerInfo, redemptionInfo, groupingInfo, metadata.isActive,
                issuerInfo.issuerName.ifBlank { groupingInfo.groupingTitle.ifBlank { issuerInfo.title } },
                groupingInfo.groupingSubtitle.ifBlank { issuerInfo.title.ifBlank { provider } })
        }
        else -> null
    }

    private val service = Retrofit.Builder()
        .baseUrl(GoogleWalletService.BASE_URL)
        .build()
        .create(GoogleWalletService::class.java)


    private fun createSyncValuablesRequest(currentValuables: List<Valuable>): SyncValuablesRequest {
        val request = SyncValuablesRequest.SyncValuablesRequestInner.newBuilder()
            .setRequest(buildRequest(currentValuables))
            .build()
        return SyncValuablesRequest.newBuilder()
            .setRequest(request)
            .build()
    }

    private fun buildRequest(currentValuables: List<Valuable>): Request {
        val cachedValuables = currentValuables.map {
            Request.CachedValuable.newBuilder()
                .setId(it.id)
                .setHash(it.hash)
                .build()
        }
        return Request.newBuilder()
            .setHeader(ByteString.copyFrom(Base64.decode(HEADER_REQUEST, Base64.DEFAULT)))
            .setTimezone(ZoneId.systemDefault().getDisplayName(
                TextStyle.FULL_STANDALONE, Locale.ENGLISH
            ))
            .addAllCachedValuable(cachedValuables)
            .build()
    }

    private fun GoogleWalletService.syncValuables(
        token: String,
        currentValuables: List<Valuable>
    ): SyncValuablesResponse {
        val requestBody = createSyncValuablesRequest(currentValuables).toByteArray()
            .toRequestBody(CONTENT_TYPE_PROTOBUF)
        return try {
            val response = syncValuables("Bearer $token", body = requestBody).execute()
            when(response.code()){
                200 -> {
                    response.body()?.let { body ->
                        SyncValuablesResponse.Success(body)
                    } ?: SyncValuablesResponse.GenericError
                }
                401 -> SyncValuablesResponse.BadAuthentication
                else -> SyncValuablesResponse.GenericError
            }
        }catch (e: Exception){
            SyncValuablesResponse.GenericError
        }
    }

    private suspend fun syncValuablesInternal(isRetry: Boolean = false): SyncValuablesResult {
        val token = getToken() ?: return SyncValuablesResult.FATAL_ERROR
        val result = loadValuables(token)
        return when {
            result == SyncValuablesResult.FATAL_ERROR && !isRetry -> {
                encryptedSettings.walletToken = ""
                syncValuablesInternal(true)
            }
            else -> result
        }
    }

    private suspend fun getToken(): String? {
        val token = encryptedSettings.walletToken
        if(token.isNotEmpty()) return token
        return googleApiRepository.getToken(GoogleApiRepository.Scope.WALLET)?.also {
            encryptedSettings.walletToken = it
        }
    }

    private suspend fun getCurrentValuables(): List<Valuable> {
        return valuablesDatabaseRepository.getWalletValuables().first().map {
            Valuable.newBuilder()
                .setId(it.id)
                .setHash(it.hash)
                .build()
        }
    }

    private suspend fun loadValuables(token: String): SyncValuablesResult {
        val valuables = service.run {
            syncValuables(token, getCurrentValuables())
        }
        return when(valuables) {
            is SyncValuablesResponse.Success -> {
                val bytes = valuables.body.bytes()
                val response = SyncValuablesResponseProto.SyncValuablesResponse.parseFrom(bytes)
                commitValuables(response)
                SyncValuablesResult.SUCCESS
            }
            is SyncValuablesResponse.GenericError -> SyncValuablesResult.ERROR
            is SyncValuablesResponse.BadAuthentication -> SyncValuablesResult.FATAL_ERROR
        }
    }

    private suspend fun commitValuables(response: SyncValuablesResponseProto.SyncValuablesResponse) {
        val valuableList = response.inner.valuables.valuableList
        valuableList.forEach {
            // 會員卡 + 有效的通用卡 / 優惠（Google 錢包網頁版會把它們列在一起）
            val parts = it.valuable.toCardParts()
            if(it.hash != 0L && parts != null && parts.isValidCard(it.valuable.valuableCase)) {

                var image: Bitmap? = null
                if (parts.imageUri.isNotEmpty()) {
                    val url = URL(parts.imageUri)
                    image = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                }

                val valuable = WalletValuable(
                    it.id,
                    it.hash,
                    EncryptedValue(it.valuable.toByteArray()),
                    if (image != null) image.compress()?.let { compressed -> EncryptedValue(compressed) } else null
                )
                valuablesDatabaseRepository.addWalletValuable(valuable)
            }else{
                valuablesDatabaseRepository.deleteWalletValuable(it.id)
            }
        }
    }

    private fun CardParts.isValidCard(case: ValuableWrapper.ValuableCase): Boolean {
        if(issuerName.isBlank()) return false
        // 會員卡維持原本行為；通用卡 / 優惠只收有效（未封存、未過期）且有條碼的
        if(case == ValuableWrapper.ValuableCase.LOYALTY_CARD) return true
        return isActive && redemptionInfo.barcode.encodedValue.isNotEmpty()
    }

    sealed class SyncValuablesResponse {
        data class Success(val body: ResponseBody): SyncValuablesResponse()
        object BadAuthentication: SyncValuablesResponse()
        object GenericError: SyncValuablesResponse()
    }

}

interface GoogleWalletService {

    companion object {
        const val BASE_URL = "https://pay-users-pa.googleapis.com/"
    }

    @POST("g/valuables/syncvaluables")
    fun syncValuables(
        @Header("Authorization") authorization: String,
        @Body body: RequestBody
    ): Call<ResponseBody>

}