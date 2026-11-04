package com.kieronquinn.app.classicpowermenu.model.quickaccesswallet

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import com.android.systemui.plugin.globalactions.wallet.WalletCardViewInfo
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.GooglePayConstants
import com.kieronquinn.app.classicpowermenu.utils.extensions.drawCenteredText
import com.kieronquinn.app.classicpowermenu.utils.extensions.getRoundedBitmap

/**
 *  Bridge between [LoyaltyCard] and [WalletCardViewInfo]. Generates a Google Pay-esque card
 *  image for the drawable, and shows relevant info for the other values
 */
class WalletLoyaltyCardViewInfo(
    private val context: Context,
    private val loyaltyCard: LoyaltyCard,
    private val labelTypeface: Typeface,
    private val onClick: (LoyaltyCard) -> Boolean,
    /** 實驗性「Display Card Code Directly」：卡面直接畫條碼（白底），取代 logo 卡面 */
    private val showCodeDirectly: Boolean = false,
    /** Content Creator Mode：條碼卡面改用假的 QR 碼，避免截圖外流真實條碼 */
    private val useDummyCode: Boolean = false
): WalletCardViewInfo {

    private companion object {
        private const val CARD_DRAWABLE_WIDTH = 745
        private const val CARD_DRAWABLE_HEIGHT = 468
        private const val CARD_LOGO_SIZE = 300
        private const val CARD_TEXT_SIZE = 123f

        // ---- 條碼卡面版面（單位：卡面 bitmap 像素，745 x 468） ----
        /** 一維條碼左右留白（quiet zone，至少約 10 個模組寬，掃描器才認得出起訖） */
        private const val CODE_BARCODE_SIDE_MARGIN = 56
        /** 一維條碼高度 */
        private const val CODE_BARCODE_HEIGHT = 260
        /** QR / Aztec 等正方形條碼邊長 */
        private const val CODE_SQUARE_SIZE = 350
        /** 條碼與下方號碼文字的間距 */
        private const val CODE_TEXT_GAP = 18
        /** 條碼下方號碼文字大小 */
        private const val CODE_TEXT_SIZE = 38f
        /** Content Creator Mode 用的假 QR 內容（與條碼預覽頁相同） */
        private const val DUMMY_CODE_CONTENTS = "https://youtu.be/rTga41r3a4s"
    }

    // 開啟直接顯示條碼時先嘗試畫條碼卡面；格式不支援或產生失敗時退回原本的 logo 卡面
    private val cachedCardDrawable = (if (showCodeDirectly) createCodeCardDrawable(context) else null)
        ?: createCardDrawable(context)
    val id = loyaltyCard.valuableId

    override fun getCardId(): String {
        return "LOY" + loyaltyCard.valuableId
    }

    /**
     *  卡片上方標題：「主標題 • 副標題」（原為「主標題 • Tap to show」）。
     *  副標題空白或與主標題相同時只顯示主標題。
     */
    override fun getText(): CharSequence {
        val subtitle = loyaltyCard.title.trim()
        return if (subtitle.isEmpty() || subtitle == loyaltyCard.issuerName) {
            loyaltyCard.issuerName
        } else {
            "${loyaltyCard.issuerName} • $subtitle"
        }
    }

    /** 會員卡標題放大（一般付款卡為 14sp） */
    override fun getTextSizeSp(): Float {
        return 18f
    }

    override fun getCardDrawable(): Drawable {
        return cachedCardDrawable
    }

    override fun getContentDescription(): CharSequence {
        return loyaltyCard.redemptionInfo.contentDescription
    }

    /** 不顯示標題前的條碼 / QR 小圖示 */
    override fun getIcon(): Drawable? {
        return null
    }

    override fun getPendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(String.format(GooglePayConstants.WALLET_DEEP_LINK_VALUABLE, loyaltyCard.valuableId))
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onClick(): Boolean {
        return onClick.invoke(loyaltyCard)
    }

    fun getLoyaltyCard() = loyaltyCard

    private fun createCardDrawable(context: Context): Drawable {
        val bitmap = Bitmap.createBitmap(CARD_DRAWABLE_WIDTH, CARD_DRAWABLE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val logoBitmap = loyaltyCard.valuableImage?.let {
            Bitmap.createScaledBitmap(it, CARD_LOGO_SIZE, CARD_LOGO_SIZE, true).getRoundedBitmap()
        }
        //Background
        paint.isAntiAlias = true
        paint.color = loyaltyCard.backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, CARD_DRAWABLE_WIDTH.toFloat(), CARD_DRAWABLE_HEIGHT.toFloat(), paint)
        //Logo
        if(logoBitmap != null){
            canvas.drawBitmap(logoBitmap, (CARD_DRAWABLE_WIDTH / 2f) - (CARD_LOGO_SIZE / 2f), (CARD_DRAWABLE_HEIGHT / 2f) - (CARD_LOGO_SIZE / 2f), paint)
        }else{
            val foregroundColor = ContextCompat.getColor(context, R.color.quick_access_wallet_generic_foreground)
            paint.color = foregroundColor
            canvas.drawCircle((CARD_DRAWABLE_WIDTH / 2f), (CARD_DRAWABLE_HEIGHT / 2f), (CARD_LOGO_SIZE / 2f), paint)
            paint.textSize = CARD_TEXT_SIZE
            paint.typeface = labelTypeface
            paint.color = ContextCompat.getColor(context, R.color.quick_access_wallet_generic_text)
            canvas.drawCenteredText(Rect(0, 0, CARD_DRAWABLE_WIDTH, CARD_DRAWABLE_HEIGHT), paint, loyaltyCard.issuerName.substring(0, 1))
        }
        //Outline circle
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle((CARD_DRAWABLE_WIDTH / 2f), (CARD_DRAWABLE_HEIGHT / 2f), (CARD_LOGO_SIZE / 2f), paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     *  畫「直接顯示條碼」的卡面：純白底 + 條碼（+ 下方號碼）。
     *  - 一維條碼：橫向撐滿（左右保留 quiet zone），條碼 + 號碼整組上下置中
     *  - QR / Aztec：正方形置中，下方有號碼時一併顯示
     *  卡面尺寸與原本相同（745 x 468 ≈ 輪播的 700:440 比例），輪播用 fitXY 縮放不會變形。
     *  不調整螢幕亮度（只是一張圖片）；需要更亮時點卡片仍會開啟原本的全亮預覽。
     *
     *  @return 條碼卡面；格式不支援或 ZXing 產生失敗時回傳 null（呼叫端改用 logo 卡面）
     */
    private fun createCodeCardDrawable(context: Context): Drawable? {
        val contents: String
        val format: com.google.zxing.BarcodeFormat
        val isSquare: Boolean
        val label: String
        if (useDummyCode) {
            contents = DUMMY_CODE_CONTENTS
            format = com.google.zxing.BarcodeFormat.QR_CODE
            isSquare = true
            label = ""
        } else {
            contents = loyaltyCard.redemptionInfo.encodedValue
            format = loyaltyCard.getTypeAsBarcodeFormat() ?: return null
            isSquare = loyaltyCard.isCodeSquare()
            label = loyaltyCard.redemptionInfo.displayText.trim()
        }
        if (contents.isBlank()) return null

        // 條碼本體的像素尺寸
        val codeWidth = if (isSquare) CODE_SQUARE_SIZE else CARD_DRAWABLE_WIDTH - CODE_BARCODE_SIDE_MARGIN * 2
        val codeHeight = if (isSquare) CODE_SQUARE_SIZE else CODE_BARCODE_HEIGHT

        // MARGIN = 0：留白由本函式自己控制（ZXing 預設會再外加一圈白邊，讓條碼變小）
        val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 0)
        val codeBitmap = runCatching {
            val matrix = com.google.zxing.MultiFormatWriter().encode(contents, format, codeWidth, codeHeight, hints)
            com.journeyapps.barcodescanner.BarcodeEncoder().createBitmap(matrix)
        }.getOrNull() ?: return null

        val bitmap = Bitmap.createBitmap(CARD_DRAWABLE_WIDTH, CARD_DRAWABLE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = CODE_TEXT_SIZE
            typeface = labelTypeface
            textAlign = Paint.Align.CENTER
        }
        val hasLabel = label.isNotEmpty()
        val textHeight = if (hasLabel) (textPaint.descent() - textPaint.ascent()) else 0f
        // 條碼 + 間距 + 文字 這一整組在卡面中上下置中
        val groupHeight = codeBitmap.height + if (hasLabel) CODE_TEXT_GAP + textHeight else 0f
        val codeTop = (CARD_DRAWABLE_HEIGHT - groupHeight) / 2f
        val codeLeft = (CARD_DRAWABLE_WIDTH - codeBitmap.width) / 2f

        // 關閉濾波：條碼線條保持銳利，不被抗鋸齒糊掉
        canvas.drawBitmap(codeBitmap, codeLeft, codeTop, Paint().apply { isFilterBitmap = false })
        codeBitmap.recycle()

        if (hasLabel) {
            // 文字太長時縮小字級，避免超出卡面
            val maxTextWidth = CARD_DRAWABLE_WIDTH - CODE_BARCODE_SIDE_MARGIN * 2f
            val measured = textPaint.measureText(label)
            if (measured > maxTextWidth) textPaint.textSize = CODE_TEXT_SIZE * maxTextWidth / measured
            val baseline = codeTop + codeHeight + CODE_TEXT_GAP - textPaint.ascent()
            canvas.drawText(label, CARD_DRAWABLE_WIDTH / 2f, baseline, textPaint)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun getCardIcon(context: Context): Drawable {
        val resource = when(loyaltyCard.redemptionInfo.type) {
            LoyaltyCard.RedemptionInfo.Type.QR_CODE, LoyaltyCard.RedemptionInfo.Type.AZTEC -> R.drawable.ic_card_type_qr
            else -> R.drawable.ic_card_type_barcode
        }
        return ContextCompat.getDrawable(context, resource)!!
    }

}