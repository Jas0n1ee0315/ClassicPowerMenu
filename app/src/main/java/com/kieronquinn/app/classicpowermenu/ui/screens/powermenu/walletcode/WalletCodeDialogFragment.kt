package com.kieronquinn.app.classicpowermenu.ui.screens.powermenu.walletcode

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.android.systemui.util.extensions.ControlsActivityStarter
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.GooglePayConstants
import com.kieronquinn.app.classicpowermenu.databinding.FragmentDialogWalletCodeBinding
import com.kieronquinn.app.classicpowermenu.ui.base.BaseDialogFragment
import com.kieronquinn.app.classicpowermenu.utils.extensions.getRoundedBitmap
import org.koin.android.ext.android.inject

import com.google.zxing.BarcodeFormat
import com.kieronquinn.app.classicpowermenu.components.settings.Settings
import com.kieronquinn.app.classicpowermenu.utils.extensions.awaitPost
import com.kieronquinn.app.classicpowermenu.utils.extensions.whenResumed

class WalletCodeDialogFragment: BaseDialogFragment<FragmentDialogWalletCodeBinding>(FragmentDialogWalletCodeBinding::inflate) {

    private val navArgs by navArgs<WalletCodeDialogFragmentArgs>()
    private val activityStarter by inject<ControlsActivityStarter>()
    private val settings by inject<Settings>()

    private val card by lazy {
        navArgs.loyaltyCard
    }

    // 「Open in Google Wallet」按鈕仍使用會員卡底色，文字顏色依底色深淺決定
    private val textColor by lazy {
        if(card.isBackgroundDark()) Color.WHITE
        else Color.BLACK
    }

    // 條碼預覽卡片一律使用純白底色（避免彩色底干擾掃描器），卡片上的文字固定為黑色
    private val cardBackgroundColor = Color.WHITE
    private val cardTextColor = Color.BLACK

    private val googleSansMedium by lazy {
        ResourcesCompat.getFont(requireContext(), R.font.google_sans_text_medium)
    }

    private val isContentCreatorMode by lazy {
        settings.developerContentCreatorMode
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.WalletCardCodeDialog)
        dialog.setCanceledOnTouchOutside(false)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.let {
            it.attributes.run {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                screenBrightness = 1f
            }
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClose()
        setupTapOutside()
        setupContainer()
        setupTitle()
        setupLogo()
        setupLabel()
        setupCard()
        setupCode()
        setupCardText()
        setupFab()
        setupBrightness()
    }

    /**
     *  亮度調整條：只修改這個預覽視窗的 screenBrightness（視窗層級覆寫），
     *  不會改到系統亮度設定，視窗關閉後系統自動回到預覽前的亮度。
     */
    private fun setupBrightness() = with(binding.walletCodeBrightness) {
        setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val accent = androidx.compose.ui.graphics.Color(card.backgroundColor)
        val onAccent = androidx.compose.ui.graphics.Color(textColor)
        setContent {
            BrightnessSlider(
                accentColor = accent,
                onAccentColor = onAccent,
                onBrightnessChanged = ::setWindowBrightness
            )
        }
    }

    private fun setWindowBrightness(brightness: Float) {
        val window = dialog?.window ?: return
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
        }
    }

    private fun setupClose() = with(binding.walletCodeClose) {
        setOnClickListener {
            dismiss()
        }
    }

    private fun setupTapOutside() = with(binding.root){
        setOnLongClickListener {
            dismiss()
            true
        }
    }

    private fun setupContainer() = with(binding.walletCodeCodeContainer) {
        updateLayoutParams<ConstraintLayout.LayoutParams> {
            if(isCodeSquare()){
                val qrSize = resources.getDimension(R.dimen.wallet_code_dialog_qr_size).toInt()
                width = qrSize
                height = qrSize
            }else{
                height = resources.getDimension(R.dimen.wallet_code_dialog_barcode_height).toInt()
            }
        }
    }

    private fun setupTitle() = with(binding.walletCodeTitle) {
        text = card.issuerName
        typeface = googleSansMedium
        setTextColor(cardTextColor)
    }

    private fun setupLogo() = with(binding.walletCodeLogo) {
        card.valuableImage?.let {
            setImageBitmap(it.getRoundedBitmap())
        } ?: run {
            setupLogoLetter()
        }
        (foreground as? GradientDrawable)?.setStroke(resources.getDimension(R.dimen.wallet_code_dialog_logo_outline).toInt(), cardTextColor)
    }

    private fun setupLogoLetter() = with(binding.walletCodeLogoLetter) {
        text = card.issuerName.substring(0, 1)
        typeface = googleSansMedium
        setTextColor(cardTextColor)
    }

    private fun setupLabel() = with(binding.walletCodeLabel) {
        text = card.title
        typeface = googleSansMedium
        setTextColor(cardTextColor)
    }

    private fun setupCard() = with(binding.walletCodeCard) {
        // 原為 card.backgroundColor：改成純白，條碼周圍不再有彩色底
        backgroundTintList = ColorStateList.valueOf(cardBackgroundColor)
        // 分隔線原本是白色，白底上改為黑色（保留 layout 的 0.5 透明度）
        binding.walletCodeDivider.setBackgroundColor(cardTextColor)
    }

    private fun setupCardText() = with(binding.walletCodeCardText) {
        text = getCodeLabel()
        typeface = googleSansMedium
        setTextColor(cardTextColor)
    }

    private fun setupFab() = with(binding.walletCodeOpenInPay) {
        // 手動新增的會員卡在 Google 錢包中不存在，隱藏「在錢包中開啟」
        if (com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards
                .ManualLoyaltyCardRepository.isManualId(card.valuableId)) {
            visibility = View.GONE
            return@with
        }
        backgroundTintList = ColorStateList.valueOf(card.backgroundColor)
        setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(String.format(GooglePayConstants.WALLET_DEEP_LINK_VALUABLE, card.valuableId))
            }
            startActivity(intent)
        }
        typeface = googleSansMedium
        setTextColor(textColor)
        iconTint = ColorStateList.valueOf(textColor)
    }

    private fun setupCode() = with(binding.walletCodeCodeImage) {
        whenResumed {
            awaitPost()
            generateCode(measuredWidth, measuredHeight)?.let {
                setImageBitmap(it)
            } ?: run {
                binding.walletCodeCodeContainer.isVisible = false
            }
        }
    }

    private fun generateCode(width: Int, height: Int): Bitmap? {
        runCatching {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(getCodeContents(), getCodeFormat(), width, height)
            val barcodeEncoder = BarcodeEncoder()
            return barcodeEncoder.createBitmap(bitMatrix)
        }
        return null
    }

    private fun isCodeSquare(): Boolean {
        return if(isContentCreatorMode){
            true
        }else card.isCodeSquare()
    }

    private fun getCodeLabel(): String {
        return if(isContentCreatorMode){
            getString(R.string.content_creator_mode_warning)
        }else card.redemptionInfo.displayText
    }

    private fun getCodeFormat(): BarcodeFormat? {
        return if(isContentCreatorMode){
            BarcodeFormat.QR_CODE
        }else card.getTypeAsBarcodeFormat()
    }

    private fun getCodeContents(): String {
        return if(isContentCreatorMode){
            "https://youtu.be/rTga41r3a4s"
        }else card.redemptionInfo.encodedValue
    }

}