package com.kieronquinn.app.classicpowermenu.ui.screens.powermenu.main

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import android.service.quickaccesswallet.CPMQuickAccessWalletClientImpl
import android.util.Log
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.systemui.controls.ui.ControlsUiController
import com.android.systemui.plugin.globalactions.wallet.WalletPanelViewController
import com.android.systemui.plugins.GlobalActionsPanelPlugin
import com.android.systemui.plugins.loyaltycards.WalletLoyaltyCardCallback
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.kieronquinn.app.classicpowermenu.ClassicPowerMenu
import com.kieronquinn.app.classicpowermenu.IClassicPowerMenu
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.databinding.FragmentPowerMenuBinding
import com.kieronquinn.app.classicpowermenu.model.power.PowerMenuButton
import com.kieronquinn.app.classicpowermenu.model.power.PowerMenuContentItem
import com.kieronquinn.app.classicpowermenu.service.container.CPMServiceContainer
import com.kieronquinn.app.classicpowermenu.ui.base.BoundFragment
import com.kieronquinn.app.classicpowermenu.utils.extensions.animateToInvisible
import com.kieronquinn.app.classicpowermenu.utils.extensions.animateToVisible
import com.kieronquinn.app.classicpowermenu.utils.extensions.awaitPost
import com.kieronquinn.app.classicpowermenu.utils.extensions.changed
import com.kieronquinn.app.classicpowermenu.utils.extensions.isScrolled
import com.kieronquinn.app.classicpowermenu.utils.extensions.scrollPercentage
import com.kieronquinn.app.classicpowermenu.utils.extensions.sendDismissIntent
import com.kieronquinn.app.classicpowermenu.utils.extensions.setSecondaryAlpha
import com.kieronquinn.app.classicpowermenu.utils.extensions.whenResumed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.roundToInt

class PowerMenuFragment :
    BoundFragment<FragmentPowerMenuBinding>(FragmentPowerMenuBinding::inflate),
    GlobalActionsPanelPlugin.Callbacks {

    private val serviceContainer by inject<CPMServiceContainer>()
    private val controlsUiController by inject<ControlsUiController>()

    private val viewModel by viewModel<PowerMenuViewModel>()

    private val keyguardManager by lazy {
        requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private val powerMenuApplication by lazy {
        requireActivity().application as ClassicPowerMenu
    }

    private val powerMenuButtons by lazy {
        viewModel.loadPowerMenuButtons(requireContext()).toMutableList()
    }

    private val contentAdapter by lazy {
        PowerMenuContentAdapter(
            requireContext(),
            getItems(),
            this::dismissGlobalActionsMenu,
            null,
            null,
            null
        )
    }

    /** 內容順序：信用卡匣 → 會員卡匣 → 裝置控制（電源選項在上方的 AppBar） */
    private fun getItems(): List<PowerMenuContentItem> {
        return ArrayList<PowerMenuContentItem>().apply {
            if(viewModel.showQuickAccessWallet) {
                add(PowerMenuContentItem.PaymentCards)
                // 會員卡功能關閉或沒有可顯示的會員卡時，會員卡匣會自行隱藏
                add(PowerMenuContentItem.LoyaltyCards)
            }
            if(viewModel.showControls) add(PowerMenuContentItem.Controls)
        }
    }

    private val buttonsAdapter by lazy {
        PowerMenuButtonsAdapter(
            requireContext(),
            emptyList<PowerMenuButton>().toMutableList(),
            viewModel.monetEnabled)
    }

    private val buttonsLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.CENTER
        }
    }

    private val appBarBackground by lazy {
        combine(
            binding.powerMenuAppbar.scrollPercentage,
            binding.powerMenuContentRecyclerview.isScrolled,
            binding.powerMenuButtonsRecyclerview.adapter!!.changed.debounce(50)
        ) { scrollPercentage: Float, isScrolled: Boolean, _ ->
            val isMultiLine = buttonsLayoutManager.flexLines.size > 1
            if(isMultiLine){
                AppBarBackgroundState.Alpha(scrollPercentage)
            }else{
                if(isScrolled){
                    AppBarBackgroundState.AnimateToVisible
                }else{
                    AppBarBackgroundState.AnimateToInvisible
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        powerMenuApplication.loadOrReloadControlsModule(requireContext())
        view.setupBackground()
        setupButtonsRecyclerView()
        setupContentRecyclerView()
        setupButtons()
        setupService()
        setupAppbar()
        setupSecondaryAlpha()
        setupInsets(view)
        changeDefaultPaymentMethod()
    }

    override fun onStop() {
        super.onStop()
        contentAdapter.controlsUiController?.hide()
    }

    private fun changeDefaultPaymentMethod() {
        if (viewModel.showQuickAccessWallet &&
            viewModel.quickAccessWalletAutoSwitchService &&
            viewModel.quickAccessWalletSelectedAutoSwitchService != "") {
            try {
                Settings.Secure.putString(powerMenuApplication.contentResolver, "nfc_payment_default_component", viewModel.quickAccessWalletSelectedAutoSwitchService)
            } catch (e: Exception) {
                Log.e("CPM", "Failed to apply default payment service", e)
            }
        }
    }

    private fun setupInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val cutout = insets.displayCutout
            view.updatePadding(left = cutout?.safeInsetLeft ?: 0, right = cutout?.safeInsetRight ?: 0)
            insets
        }
    }

    private fun View.setupBackground() {
        background = if(viewModel.useSolidBackground){
            ColorDrawable(
                monet.getBackgroundColorSecondary(context, true)
                    ?: monet.getBackgroundColor(context, true)
            )
        }else{
            ColorDrawable(
                ColorUtils.setAlphaComponent(
                    monet.getBackgroundColor(context, true),
                    128
                )
            )
        }
    }

    private fun setupButtonsRecyclerView() = with(binding.powerMenuButtonsRecyclerview) {
        layoutManager = buttonsLayoutManager
        adapter = buttonsAdapter
    }

    private fun setupContentRecyclerView() = with(binding.powerMenuContentRecyclerview) {
        layoutManager = LinearLayoutManager(requireContext()).apply {
            // 卡匣可展開 / 收起，項目高度會在綁定後改變；關閉量測快取，每次排版都重新量測項目
            // （只有 3 個項目，成本可忽略），避免沿用舊高度造成卡匣與下方區塊重疊
            isMeasurementCacheEnabled = false
        }
        adapter = contentAdapter
        val bottomPadding = resources.getDimension(R.dimen.margin_16)
        ViewCompat.setOnApplyWindowInsetsListener(this){ view, insets ->
            val bottomNavInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomCutoutInset = insets.displayCutout?.safeInsetBottom ?: 0
            view.updatePadding(bottom = bottomCutoutInset + bottomNavInset + bottomPadding.toInt())
            insets
        }
    }

    private fun setupService() = whenResumed {
        serviceContainer.runWithService {
            setupWallet(it)
            setupControls()
        }
    }

    private var appBarBackgroundAnimation: ValueAnimator? = null
    private fun setupAppbar() = whenResumed {
        //Start alpha is 0
        binding.powerMenuAppbar.background.alpha = 0
        binding.powerMenuAppbar.backgroundTintList = ColorStateList.valueOf(monet.getBackgroundColor(requireContext()))
        ViewCompat.setOnApplyWindowInsetsListener(binding.powerMenuAppbar){ view, insets ->
            val topInset = insets.displayCutout?.safeInsetTop ?: 0
            view.updatePadding(top = topInset)
            insets
        }
        launch {
            binding.powerMenuAppbar.awaitPost()
            binding.powerMenuAppbar.setExpanded(!viewModel.powerOptionsOpenCollapsed, false)
        }
        appBarBackground.collect {
            appBarBackgroundAnimation?.cancel()
            appBarBackgroundAnimation = null
            when(it){
                is AppBarBackgroundState.AnimateToVisible -> appBarBackgroundAnimation = binding.powerMenuAppbar.background.animateToVisible()
                is AppBarBackgroundState.AnimateToInvisible -> appBarBackgroundAnimation = binding.powerMenuAppbar.background.animateToInvisible()
                is AppBarBackgroundState.Alpha -> binding.powerMenuAppbar.background.alpha = ((it.alpha) * 255).safeRoundToInt()
            }
        }
    }

    private fun Float.safeRoundToInt(): Int {
        if(isNaN()) return 0
        return roundToInt()
    }

    private fun setupSecondaryAlpha() = whenResumed {
        binding.powerMenuAppbar.scrollPercentage.collect {
            buttonsLayoutManager.setSecondaryAlpha(1 - it)
        }
    }

    private fun setupWallet(service: IClassicPowerMenu) {
        val quickAccessWalletClient =
            CPMQuickAccessWalletClientImpl(requireActivity().baseContext, service)
        val isDeviceLocked = keyguardManager.isDeviceLocked
        val loyaltyCallback = WalletLoyaltyCardCallback(viewModel::addLoyaltyCardsToWallet)
        // 信用卡匣：向 Quick Access Wallet 服務取付款卡，不接會員卡
        contentAdapter.paymentWalletController = WalletPanelViewController(
            requireContext(),
            requireContext(),
            quickAccessWalletClient,
            this,
            isDeviceLocked,
            loyaltyCallback,
            WalletPanelViewController.MODE_PAYMENT_ONLY
        )
        // 會員卡匣：只透過 callback 取會員卡，不呼叫付款服務
        contentAdapter.loyaltyWalletController = WalletPanelViewController(
            requireContext(),
            requireContext(),
            quickAccessWalletClient,
            this,
            isDeviceLocked,
            loyaltyCallback,
            WalletPanelViewController.MODE_LOYALTY_ONLY
        )
        contentAdapter.notifyItemChangeOfType(PowerMenuContentItem.PaymentCards)
        contentAdapter.notifyItemChangeOfType(PowerMenuContentItem.LoyaltyCards)
    }

    private fun setupControls() {
        contentAdapter.controlsUiController = controlsUiController
        contentAdapter.notifyItemChangeOfType(PowerMenuContentItem.Controls)
    }

    private fun setupButtons() = whenResumed {
        buttonsAdapter.items = powerMenuButtons.filter { it.shouldShow() }.toMutableList()
        buttonsAdapter.notifyDataSetChanged()
    }

    override fun dismissGlobalActionsMenu() {
        sendDismiss()
    }

    private fun sendDismiss() = whenResumed {
        serviceContainer.runWithService {
            it.sendDismissIntent(requireContext())
        }
    }

    private sealed class AppBarBackgroundState {
        data class Alpha(val alpha: Float): AppBarBackgroundState()
        object AnimateToInvisible: AppBarBackgroundState()
        object AnimateToVisible: AppBarBackgroundState()
    }



}