package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.customize

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.PaymentCardCustomizationRepository
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.CropEditor
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.CropGeometry
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.CropRequest
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.ImageLimits
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.ImageProcessor
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.ImageTooLargeException
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.Pan
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.PhotoFit
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard.PixelSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 *  自訂卡面的裁切畫面（Compose），直接使用 ZetaCard 的 [CropEditor]。
 *
 *  流程：設定頁選好照片 → 開啟本 Activity（帶入卡片 ID 與照片 Uri）
 *  → 解碼照片 → 使用者裁切 / 拉伸 → 輸出 1400x880 PNG 存檔 → RESULT_OK 返回
 */
class CardArtCropActivity: ComponentActivity() {

    companion object {
        private const val TAG = "CPMCardArtCrop"
        private const val EXTRA_CARD_ID = "card_id"

        fun createIntent(context: Context, cardId: String, imageUri: Uri): Intent {
            return Intent(context, CardArtCropActivity::class.java).apply {
                putExtra(EXTRA_CARD_ID, cardId)
                data = imageUri
                // 讓本 Activity 也能讀取相簿挑選器給的 Uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private val repository by inject<PaymentCardCustomizationRepository>()

    // 目前的裁切請求；null 代表照片還在解碼或正在存檔（顯示讀取中）
    private var cropRequest by mutableStateOf<CropRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardId = intent.getStringExtra(EXTRA_CARD_ID)
        val uri = intent.data
        if (cardId == null || uri == null) {
            finish()
            return
        }
        setContent {
            // 配色跟隨系統 Material You 動態色彩（CPM 本身也是 Monet 配色）
            val context = LocalContext.current
            val colorScheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val request = cropRequest
                    if (request == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        CropEditor(
                            request = request,
                            onCancel = { finish() },
                            onSave = { fit, zoom, pan, viewport -> applyCrop(request, fit, zoom, pan, viewport) }
                        )
                    }
                }
            }
        }
        // 照片 Bitmap 不保存在 savedInstanceState（太大），畫面重建時重新解碼即可
        decodeImage(cardId, uri)
    }

    private fun decodeImage(cardId: String, uri: Uri) = lifecycleScope.launch {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeForEditing(contentResolver, uri)
            }
            cropRequest = CropRequest(cardId, bitmap)
        } catch (e: ImageTooLargeException) {
            showErrorAndFinish("This image is too large")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image", e)
            showErrorAndFinish("Could not open this image")
        }
    }

    /**
     *  與 ZetaCard 的 MainViewModel.applyCrop 相同的輸出邏輯
     */
    private fun applyCrop(request: CropRequest, fit: PhotoFit, zoom: Float, pan: Pan, viewport: PixelSize) {
        cropRequest = null
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (fit == PhotoFit.CROP) {
                        val output = PixelSize(ImageLimits.OUTPUT_WIDTH, ImageLimits.OUTPUT_HEIGHT)
                        ImageProcessor.renderCrop(request.image, CropGeometry.cropTransform(request.size, viewport, zoom, pan, output))
                    } else {
                        ImageProcessor.renderStretch(request.image)
                    }
                }
                repository.saveCustomCardArt(request.id, result)
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save card art", e)
                showErrorAndFinish("Could not save the card art")
            }
        }
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

}
