package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.manage

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.kieronquinn.app.classicpowermenu.model.quickaccesswallet.LoyaltyCard.RedemptionInfo.Type

/**
 *  手動新增會員卡可選的條碼類型（下拉選單順序 = 常用程度）。
 *  只列出 ZXing 能產生的格式；TEXT_ONLY / UNSPECIFIED 等無法畫成條碼的類型不提供。
 */
object ManualBarcodeTypes {

    data class Option(val type: Type, val label: String, val format: BarcodeFormat) {
        /** 正方形 / 二維條碼：預覽時用正方形尺寸 */
        val isSquare = format == BarcodeFormat.QR_CODE || format == BarcodeFormat.AZTEC ||
                format == BarcodeFormat.DATA_MATRIX
    }

    val options = listOf(
        Option(Type.CODE_128, "Code 128", BarcodeFormat.CODE_128),       // 最多店家使用
        Option(Type.CODE_39, "Code 39", BarcodeFormat.CODE_39),          // 手機載具
        Option(Type.QR_CODE, "QR Code", BarcodeFormat.QR_CODE),
        Option(Type.EAN_13, "EAN-13", BarcodeFormat.EAN_13),             // 一般商品條碼
        Option(Type.EAN_8, "EAN-8", BarcodeFormat.EAN_8),
        Option(Type.UPC_A, "UPC-A", BarcodeFormat.UPC_A),
        Option(Type.UPC_E, "UPC-E", BarcodeFormat.UPC_E),
        Option(Type.ITF_14, "ITF-14", BarcodeFormat.ITF),
        Option(Type.CODABAR, "Codabar", BarcodeFormat.CODABAR),
        Option(Type.PDF_417, "PDF417", BarcodeFormat.PDF_417),
        Option(Type.AZTEC, "Aztec", BarcodeFormat.AZTEC),
        Option(Type.DATA_MATRIX, "Data Matrix", BarcodeFormat.DATA_MATRIX)
    )

    /** 新增時的預設類型 */
    val default = options.first()

    fun forType(type: Type?): Option = options.find { it.type == type } ?: default

    /**
     *  嘗試把卡號畫成條碼：成功回傳 Bitmap，卡號不符合該格式（例如 EAN-13 不是 12/13 位數字、
     *  檢查碼錯誤、Code 39 含不支援字元）時回傳 null。存檔前用它驗證，避免存到畫不出來的卡。
     */
    fun encode(number: String, option: Option, width: Int, height: Int): Bitmap? {
        if (number.isBlank() || width <= 0 || height <= 0) return null
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = MultiFormatWriter().encode(number, option.format, width, height, hints)
            BarcodeEncoder().createBitmap(matrix)
        }.getOrNull()
    }

}
