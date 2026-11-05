package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.manage

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.databinding.FragmentSettingsQuickAccessWalletManageBinding
import com.kieronquinn.app.classicpowermenu.ui.base.BoundFragment
import com.kieronquinn.app.classicpowermenu.ui.base.StandaloneFragment
import com.kieronquinn.app.classicpowermenu.utils.extensions.isDarkMode
import com.kieronquinn.app.classicpowermenu.utils.extensions.onApplyInsets
import com.kieronquinn.app.classicpowermenu.utils.extensions.whenResumed
import com.kieronquinn.monetcompat.extensions.views.applyMonet
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Collections
import androidx.core.widget.doAfterTextChanged
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.ManualLoyaltyCard
import com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.loyaltycards.ManualLoyaltyCardRepository
import com.kieronquinn.app.classicpowermenu.databinding.DialogLoyaltyCardAddBinding

class SettingsQuickAccessWalletManageFragment: BoundFragment<FragmentSettingsQuickAccessWalletManageBinding>(FragmentSettingsQuickAccessWalletManageBinding::inflate), StandaloneFragment {

    private val adapter by lazy {
        SettingsQuickAccessWalletManageAdapter(requireContext(), emptyList(), viewModel::onCardVisibilityClicked, ::showEditDialog){
            itemTouchHelper.startDrag(it)
        }
    }

    /**
     *  編輯會員卡主標題（issuerName）/ 副標題（title）的對話框。
     *  欄位留空 = 恢復 Google 原本的文字（提示文字顯示原始值）。
     */
    private fun showEditDialog(item: SettingsQuickAccessWalletManageViewModel.RearrangeLoyaltyCard) {
        // 手動新增的卡片：開啟新增視窗（可改名稱、副標題、卡號、條碼類型）
        if (item.isManual) {
            viewModel.getManualCard(item.id)?.let { showManualCardDialog(it) }
            return
        }
        val card = item.info.getLoyaltyCard()
        val dialogBinding = com.kieronquinn.app.classicpowermenu.databinding.DialogLoyaltyCardEditBinding.inflate(layoutInflater)
        with(dialogBinding) {
            loyaltyCardEditIssuerNameLayout.placeholderText = card.originalIssuerName
            loyaltyCardEditTitleLayout.placeholderText = card.originalTitle
            loyaltyCardEditIssuerNameLayout.helperText = getString(R.string.settings_quick_access_wallet_manage_edit_original, card.originalIssuerName)
            loyaltyCardEditTitleLayout.helperText = getString(R.string.settings_quick_access_wallet_manage_edit_original, card.originalTitle)
            // 只有自訂過的欄位才預先填入，未自訂的保持空白（= 使用原始文字）
            loyaltyCardEditIssuerName.setText(card.issuerName.takeIf { it != card.originalIssuerName })
            loyaltyCardEditTitle.setText(card.title.takeIf { it != card.originalTitle })
            // App 主題的 colorPrimary 在深色模式下太暗，欄位標籤 / 外框改用 Monet 強調色與主要文字色
            val accent = ColorStateList.valueOf(monet.getAccentColor(requireContext()))
            val textPrimary = ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(root, android.R.attr.textColorPrimary)
            )
            listOf(loyaltyCardEditIssuerNameLayout, loyaltyCardEditTitleLayout).forEach {
                it.defaultHintTextColor = textPrimary
                it.hintTextColor = accent
                it.setBoxStrokeColorStateList(accent)
            }
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_quick_access_wallet_manage_edit)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.onCardEdited(
                    item,
                    dialogBinding.loyaltyCardEditIssuerName.text?.toString(),
                    dialogBinding.loyaltyCardEditTitle.text?.toString()
                )
            }
            .setNeutralButton(R.string.settings_quick_access_wallet_manage_edit_reset) { _, _ ->
                viewModel.onCardEdited(item, null, null)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
            .apply {
                // 按鈕文字原本是很暗的 colorPrimary，在深色對話框上幾乎看不見，改用 Monet 強調色
                val accent = monet.getAccentColor(requireContext())
                listOf(
                    android.content.DialogInterface.BUTTON_POSITIVE,
                    android.content.DialogInterface.BUTTON_NEGATIVE,
                    android.content.DialogInterface.BUTTON_NEUTRAL
                ).forEach { getButton(it)?.setTextColor(accent) }
            }
    }

    /**
     *  手動新增 / 編輯會員卡的對話框。
     *  @param existing null = 新增；否則為編輯（帶入原資料，存檔時沿用同一個 id）
     */
    private fun showManualCardDialog(existing: ManualLoyaltyCard? = null) {
        val dialogBinding = DialogLoyaltyCardAddBinding.inflate(layoutInflater)
        var selected = ManualBarcodeTypes.forType(existing?.type)
        with(dialogBinding) {
            // 欄位顏色：與「編輯標題」對話框相同，使用 Monet 強調色（深色模式下才看得清楚）
            val accent = ColorStateList.valueOf(monet.getAccentColor(requireContext()))
            val textPrimary = ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(root, android.R.attr.textColorPrimary)
            )
            listOf(loyaltyCardAddNameLayout, loyaltyCardAddSubtitleLayout,
                loyaltyCardAddNumberLayout, loyaltyCardAddTypeLayout).forEach {
                it.defaultHintTextColor = textPrimary
                it.hintTextColor = accent
                it.setBoxStrokeColorStateList(accent)
            }
            existing?.let {
                loyaltyCardAddName.setText(it.name)
                loyaltyCardAddSubtitle.setText(it.subtitle)
                loyaltyCardAddNumber.setText(it.number)
            }
            // 條碼類型下拉選單
            val labels = ManualBarcodeTypes.options.map { it.label }
            loyaltyCardAddType.setSimpleItems(labels.toTypedArray())
            loyaltyCardAddType.setText(selected.label, false)
            loyaltyCardAddType.setOnItemClickListener { _, _, position, _ ->
                selected = ManualBarcodeTypes.options[position]
                updateManualCardPreview(this, selected)
            }
            loyaltyCardAddNumber.doAfterTextChanged {
                loyaltyCardAddNumberLayout.error = null
                updateManualCardPreview(this, selected)
            }
            loyaltyCardAddName.doAfterTextChanged { loyaltyCardAddNameLayout.error = null }
            // 預覽容器量好寬度後再畫第一次
            loyaltyCardAddPreviewContainer.post { updateManualCardPreview(this, selected) }
        }
        val title = if (existing == null) R.string.settings_quick_access_wallet_manage_add
            else R.string.settings_quick_access_wallet_manage_add_edit
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            // 先不給 listener：改在 show() 後覆寫，驗證失敗時不關閉視窗
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
        val accent = monet.getAccentColor(requireContext())
        listOf(android.content.DialogInterface.BUTTON_POSITIVE, android.content.DialogInterface.BUTTON_NEGATIVE)
            .forEach { dialog.getButton(it)?.setTextColor(accent) }
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            with(dialogBinding) {
                val name = loyaltyCardAddName.text?.toString()?.trim().orEmpty()
                val subtitle = loyaltyCardAddSubtitle.text?.toString()?.trim().orEmpty()
                val number = loyaltyCardAddNumber.text?.toString()?.trim().orEmpty()
                var valid = true
                if (name.isEmpty()) {
                    loyaltyCardAddNameLayout.error = getString(R.string.settings_quick_access_wallet_manage_add_required)
                    valid = false
                }
                if (number.isEmpty()) {
                    loyaltyCardAddNumberLayout.error = getString(R.string.settings_quick_access_wallet_manage_add_required)
                    valid = false
                } else if (ManualBarcodeTypes.encode(number, selected, 400, 200) == null) {
                    // 卡號無法用此條碼類型產生（格式 / 檢查碼 / 字元不符）
                    loyaltyCardAddNumberLayout.error =
                        getString(R.string.settings_quick_access_wallet_manage_add_invalid, selected.label)
                    valid = false
                }
                if (!valid) return@with
                viewModel.onManualCardSaved(
                    ManualLoyaltyCard(
                        id = existing?.id ?: ManualLoyaltyCardRepository.newId(),
                        name = name,
                        subtitle = subtitle,
                        number = number,
                        type = selected.type
                    )
                )
                dialog.dismiss()
            }
        }
    }

    /** 依目前卡號與條碼類型更新預覽；畫不出來（空白 / 格式不符）時隱藏預覽 */
    private fun updateManualCardPreview(binding: DialogLoyaltyCardAddBinding, option: ManualBarcodeTypes.Option) {
        val container = binding.loyaltyCardAddPreviewContainer
        val number = binding.loyaltyCardAddNumber.text?.toString()?.trim().orEmpty()
        val innerWidth = container.width - container.paddingLeft - container.paddingRight
        val innerHeight = resources.getDimensionPixelSize(R.dimen.margin_16) * 7 // 約 112dp
        val width = if (option.isSquare) innerHeight else innerWidth
        val bitmap = ManualBarcodeTypes.encode(number, option, width, innerHeight)
        binding.loyaltyCardAddPreview.setImageBitmap(bitmap)
        container.isVisible = bitmap != null
    }

    private fun showUndoDeleteSnackbar(card: ManualLoyaltyCard) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            getString(R.string.settings_quick_access_wallet_manage_add_deleted, card.name),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).setAction(R.string.settings_quick_access_wallet_manage_add_undo) {
            viewModel.onManualCardRestored(card)
        }.setActionTextColor(monet.getAccentColor(requireContext())).show()
    }

    private val itemTouchHelper by lazy {
        ItemTouchHelper(ItemTouchCallback())
    }

    private val viewModel by viewModel<SettingsQuickAccessWalletManageViewModel>()

    override val onBackPressed by lazy {
        return@lazy viewModel::onBackPressed
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupState()
        setupMonet()
        setupInsets()
        setUpClickListeners()
        viewModel.onPageLoad()
    }

    private fun setUpClickListeners() {
        binding.signInButton.setOnClickListener {
            viewModel.onSignInClicked()
        }

        binding.syncButton.setOnClickListener {
            viewModel.syncLoyaltyCards()
        }
    }

    private fun setupMonet(){
        binding.root.setBackgroundColor(monet.getBackgroundColor(requireContext()))
        binding.loadingBar.applyMonet()
        binding.syncButton.applyMonet()
        binding.signInButton.applyMonet()
        binding.signInButton.setTextColor(monet.getPrimaryColor(requireContext()))
        binding.syncButton.drawable.setTint(Color.WHITE)
        binding.signInInfoCard.run {
            val background = monet.getPrimaryColor(context, !context.isDarkMode)
            backgroundTintList = ColorStateList.valueOf(background)
        }
    }

    private fun setupState() = whenResumed {
        viewModel.state.collect {
            handleState(it)
        }
    }

    private fun handleState(state: SettingsQuickAccessWalletManageViewModel.State){
        when(state){
            is SettingsQuickAccessWalletManageViewModel.State.Loading -> {
                binding.recyclerview.isVisible = false
                binding.error.isVisible = false
                binding.signIn.isVisible = false
                binding.syncButtonContainer.isVisible = false

                binding.loading.isVisible = true
            }
            is SettingsQuickAccessWalletManageViewModel.State.SignInRequired -> {
                binding.recyclerview.isVisible = false
                binding.error.isVisible = false
                binding.loading.isVisible = false
                binding.syncButtonContainer.isVisible = false

                binding.signIn.isVisible = true
            }
            is SettingsQuickAccessWalletManageViewModel.State.Error -> {
                binding.recyclerview.isVisible = false
                binding.loading.isVisible = false
                binding.signIn.isVisible = false

                binding.error.isVisible = true
                binding.errorText.setText(state.type.contentRes)

                when (state.type) {
                    SettingsQuickAccessWalletManageViewModel.ErrorType.ERROR -> binding.syncButtonContainer.isVisible = false
                    SettingsQuickAccessWalletManageViewModel.ErrorType.NO_CARDS -> binding.syncButtonContainer.isVisible = true
                }
            }
            is SettingsQuickAccessWalletManageViewModel.State.Loaded -> {
                binding.error.isVisible = false
                binding.loading.isVisible = false
                binding.signIn.isVisible = false

                binding.recyclerview.isVisible = true
                binding.syncButtonContainer.isVisible = true
                // 存成可變清單（拖曳排序 / 滑動刪除會直接修改），並通知列表重繪（新增 / 編輯 / 復原後才會更新）
                adapter.cards = state.cards.toMutableList()
                adapter.notifyDataSetChanged()

                binding.syncButtonContainer.bringToFront()
            }
        }
    }

    private fun setupToolbar(){
        with(binding.toolbar){
            setupWithScrollableView(binding.recyclerview)
            setNavigationOnClickListener {
                viewModel.onBackPressed()
            }
            // 標題列最右側「+」：手動新增會員卡
            menu.add(R.string.settings_quick_access_wallet_manage_add).apply {
                setIcon(R.drawable.ic_loyalty_card_add)
                icon?.setTint(com.google.android.material.color.MaterialColors.getColor(
                    this@with, android.R.attr.textColorPrimary))
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                setOnMenuItemClickListener {
                    showManualCardDialog()
                    true
                }
            }
        }
    }

    private fun setupRecyclerView() = with(binding.recyclerview) {
        layoutManager = LinearLayoutManager(context)
        adapter = this@SettingsQuickAccessWalletManageFragment.adapter
        itemTouchHelper.attachToRecyclerView(this)
    }

    private fun setupInsets(){
        binding.toolbar.onApplyInsets { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = topInset)
        }
        binding.signIn.onApplyInsets { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = topInset)
        }
        binding.recyclerview.onApplyInsets { view, insets ->
            val bottomPadding = resources.getDimension(R.dimen.margin_16).toInt()
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottomPadding + bottomInset)
        }
        binding.syncButtonContainer.onApplyInsets { view, insets ->
            val bottomPadding = resources.getDimension(R.dimen.margin_16).toInt()
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottomPadding + bottomInset)
        }
    }

    private inner class ItemTouchCallback: ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            Collections.swap(adapter.cards, fromPosition, toPosition)
            adapter.notifyItemMoved(fromPosition, toPosition)
            adapter.notifyItemChanged(fromPosition)
            adapter.notifyItemChanged(toPosition)
            viewModel.onCardOrderChanged(adapter.cards)
            return true
        }

        /** 只有手動新增的會員卡可以左右滑動刪除；Google 同步的卡片不能滑 */
        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val position = viewHolder.bindingAdapterPosition
            val item = adapter.cards.getOrNull(position) ?: return 0
            return if (item.isManual) ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT else 0
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val item = adapter.cards.getOrNull(position) ?: return
            if (!item.isManual) {
                adapter.notifyItemChanged(position)
                return
            }
            // 先備份完整資料（復原用），再從列表移除並刪除
            val backup = viewModel.getManualCard(item.id)
            adapter.cards = adapter.cards.toMutableList().apply { removeAt(position) }
            adapter.notifyItemRemoved(position)
            viewModel.onManualCardDeleted(item)
            backup?.let { showUndoDeleteSnackbar(it) }
        }

        override fun isLongPressDragEnabled() = false

    }

}