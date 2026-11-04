/*
 * 本檔案移植自 ZetaCard（https://github.com/hxfuxyy/ZetaCard）by hxfuxyy 的 MainViewModel，
 * 只取出裁切編輯器需要的資料類別。此為修改版本，非 ZetaCard 官方發行，亦未經 hxfuxyy 背書。
 * 原始授權：ZetaCard Source License 2.0（全文見本資料夾 LICENSE-ZetaCard.txt）。
 */

package com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard

import android.graphics.Bitmap

/** 照片套用到卡面的方式：裁切 / 拉伸 */
enum class PhotoFit { CROP, STRETCH }

/** 裁切請求：id 為付款卡 ID，image 為已解碼的原始照片 */
data class CropRequest(val id: String, val image: Bitmap) {
    val size: PixelSize get() = PixelSize(image.width, image.height)
}
