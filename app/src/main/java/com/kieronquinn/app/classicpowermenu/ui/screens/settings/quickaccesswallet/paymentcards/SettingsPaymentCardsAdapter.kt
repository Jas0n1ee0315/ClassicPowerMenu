package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.databinding.ItemWalletPaymentCardCreditBinding
import com.kieronquinn.app.classicpowermenu.databinding.ItemWalletRearrangeCardBinding
import com.kieronquinn.monetcompat.core.MonetCompat

/**
 *  付款卡列表 Adapter，沿用會員卡管理頁的 item_wallet_rearrange_card 版面（隱藏拖曳把手）。
 *
 *  - [Mode.VISIBILITY]：右側為顯示 / 隱藏切換按鈕（與 Manage Account & Loyalty Cards 相同）
 *  - [Mode.CUSTOMIZE]：右側為編輯按鈕，點整列或按鈕都會開啟自訂選單；列表最後加上 ZetaCard 出處說明
 */
class SettingsPaymentCardsAdapter(
    context: Context,
    private val mode: Mode,
    var items: List<SettingsPaymentCardsViewModel.Item>,
    private val onVisibilityToggleClicked: (SettingsPaymentCardsViewModel.Item) -> Unit,
    private val onCustomizeClicked: (SettingsPaymentCardsViewModel.Item) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Mode { VISIBILITY, CUSTOMIZE }

    companion object {
        private const val VIEW_TYPE_CARD = 0
        private const val VIEW_TYPE_CREDIT = 1
    }

    init {
        setHasStableIds(true)
    }

    private val monet by lazy {
        MonetCompat.getInstance()
    }

    private val layoutInflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    private val showCredit = mode == Mode.CUSTOMIZE

    override fun getItemCount() = items.size + if (showCredit && items.isNotEmpty()) 1 else 0

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) VIEW_TYPE_CARD else VIEW_TYPE_CREDIT
    }

    override fun getItemId(position: Int): Long {
        return if (position < items.size) items[position].id.hashCode().toLong() else Long.MIN_VALUE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_CARD) {
            CardViewHolder(ItemWalletRearrangeCardBinding.inflate(layoutInflater, parent, false))
        } else {
            CreditViewHolder(ItemWalletPaymentCardCreditBinding.inflate(layoutInflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is CardViewHolder) return
        val item = items[position]
        val context = holder.itemView.context
        with(holder.binding) {
            // 付款卡不能排序（順序由 Google Wallet 決定），隱藏拖曳把手
            walletRearrangeCardDragHandle.isVisible = false

            walletRearrangeCardTitle.text = item.card.label.ifBlank {
                item.card.contentDescription ?: item.card.id
            }
            walletRearrangeCardVisibilityToggle.imageTintList =
                ColorStateList.valueOf(monet.getAccentColor(context))

            // 卡面：有自訂就顯示自訂卡面，否則顯示 Google Wallet 原始卡面
            walletRearrangeCardCard.clipToOutline = true
            if (item.customArtwork != null) {
                walletRearrangeCardCard.setImageBitmap(item.customArtwork)
            } else {
                walletRearrangeCardCard.setImageDrawable(item.card.originalDrawable)
            }

            when (mode) {
                Mode.VISIBILITY -> {
                    walletRearrangeCardText.text = item.card.contentDescription
                    walletRearrangeCardVisibilityToggle.setImageResource(
                        if (item.visible) R.drawable.ic_quick_access_wallet_visibility_visible
                        else R.drawable.ic_quick_access_wallet_visibility_invisible
                    )
                    // 隱藏中的卡片把卡面調淡，一眼就看得出來
                    walletRearrangeCardCard.alpha = if (item.visible) 1f else 0.4f
                    walletRearrangeCardVisibilityToggle.setOnClickListener {
                        onVisibilityToggleClicked(item)
                    }
                    root.setOnClickListener(null)
                    root.isClickable = false
                }
                Mode.CUSTOMIZE -> {
                    walletRearrangeCardText.setText(
                        if (item.customArtwork != null) R.string.settings_quick_access_wallet_payment_custom_artwork
                        else R.string.settings_quick_access_wallet_payment_original_artwork
                    )
                    walletRearrangeCardCard.alpha = 1f
                    walletRearrangeCardVisibilityToggle.setImageResource(R.drawable.ic_quick_access_wallet_edit)
                    val clickListener = View.OnClickListener { onCustomizeClicked(item) }
                    walletRearrangeCardVisibilityToggle.setOnClickListener(clickListener)
                    root.setOnClickListener(clickListener)
                }
            }
        }
    }

    class CardViewHolder(val binding: ItemWalletRearrangeCardBinding): RecyclerView.ViewHolder(binding.root)
    class CreditViewHolder(val binding: ItemWalletPaymentCardCreditBinding): RecyclerView.ViewHolder(binding.root)

}
