package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.databinding.FragmentSettingsQuickAccessWalletPaymentCardsBinding
import com.kieronquinn.app.classicpowermenu.ui.base.BoundFragment
import com.kieronquinn.app.classicpowermenu.ui.base.StandaloneFragment
import com.kieronquinn.app.classicpowermenu.utils.extensions.onApplyInsets
import com.kieronquinn.app.classicpowermenu.utils.extensions.whenResumed
import com.kieronquinn.monetcompat.extensions.views.applyMonet
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 *  付款卡設定頁的共用基底（版面、讀取狀態、Monet 配色、Insets）。
 *  子類別：[com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.visibility.SettingsPaymentCardsVisibilityFragment]
 *  與 [com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards.customize.SettingsPaymentCardsCustomizeFragment]
 */
abstract class SettingsPaymentCardsFragment: BoundFragment<FragmentSettingsQuickAccessWalletPaymentCardsBinding>(FragmentSettingsQuickAccessWalletPaymentCardsBinding::inflate), StandaloneFragment {

    abstract val mode: SettingsPaymentCardsAdapter.Mode
    abstract val titleRes: Int

    protected val viewModel by viewModel<SettingsPaymentCardsViewModel>()

    protected val adapter by lazy {
        SettingsPaymentCardsAdapter(
            requireContext(),
            mode,
            emptyList(),
            viewModel::onVisibilityToggled,
            ::onCustomizeClicked
        )
    }

    override val onBackPressed by lazy {
        return@lazy viewModel::onBackPressed
    }

    /** Customize 模式點擊卡片時呼叫（Visibility 模式不會用到） */
    open fun onCustomizeClicked(item: SettingsPaymentCardsViewModel.Item) {
        //no-op
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupState()
        setupMonet()
        setupInsets()
        // 需要 Activity Context 才能透過 root 服務綁定 Google Wallet 的服務
        viewModel.load(requireActivity())
    }

    private fun setupState() = whenResumed {
        viewModel.state.collect {
            handleState(it)
        }
    }

    private fun handleState(state: SettingsPaymentCardsViewModel.State) {
        when (state) {
            is SettingsPaymentCardsViewModel.State.Loading -> {
                binding.paymentCardsRecyclerview.isVisible = false
                binding.paymentCardsError.isVisible = false
                binding.paymentCardsLoading.isVisible = true
            }
            is SettingsPaymentCardsViewModel.State.Error -> {
                binding.paymentCardsRecyclerview.isVisible = false
                binding.paymentCardsLoading.isVisible = false
                binding.paymentCardsErrorText.setText(state.messageRes)
                binding.paymentCardsError.isVisible = true
            }
            is SettingsPaymentCardsViewModel.State.Loaded -> {
                binding.paymentCardsLoading.isVisible = false
                binding.paymentCardsError.isVisible = false
                binding.paymentCardsRecyclerview.isVisible = true
                adapter.items = state.items
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupToolbar() {
        with(binding.toolbar) {
            setTitle(titleRes)
            setupWithScrollableView(binding.paymentCardsRecyclerview)
            setNavigationOnClickListener {
                viewModel.onBackPressed()
            }
        }
    }

    private fun setupRecyclerView() = with(binding.paymentCardsRecyclerview) {
        layoutManager = LinearLayoutManager(context)
        adapter = this@SettingsPaymentCardsFragment.adapter
    }

    private fun setupMonet() {
        binding.root.setBackgroundColor(monet.getBackgroundColor(requireContext()))
        binding.paymentCardsLoadingBar.applyMonet()
    }

    private fun setupInsets() {
        binding.toolbar.onApplyInsets { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = topInset)
        }
        binding.paymentCardsRecyclerview.onApplyInsets { view, insets ->
            val bottomPadding = resources.getDimension(R.dimen.margin_16).toInt()
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottomPadding + bottomInset)
        }
    }

}
