package com.kieronquinn.app.classicpowermenu.ui.screens.settings.quickaccesswallet.paymentcards

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.plugin.globalactions.wallet.WalletPanelViewController
import com.kieronquinn.app.classicpowermenu.R
import com.kieronquinn.app.classicpowermenu.databinding.ItemWalletPaymentCardCreditBinding
import com.kieronquinn.app.classicpowermenu.databinding.ItemWalletRearrangeCardBinding
import com.kieronquinn.monetcompat.core.MonetCompat

/**
 *  付款卡列表 Adapter，沿用會員卡管理頁的 item_wallet_rearrange_card 版面。
 *
 *  - [Mode.VISIBILITY]：右側為顯示 / 隱藏切換按鈕，左側拖曳把手可長按拖曳排序（與 Manage Account & Loyalty Cards 相同）
 *  - [Mode.CUSTOMIZE]：右側為編輯按鈕，點整列或按鈕都會開啟自訂選單；列表最後加上 ZetaCard 出處說明
 */
class SettingsPaymentCardsAdapter(
    context: Context,
    private val mode: Mode,
    var items: MutableList<SettingsPaymentCardsViewModel.Item>,
    private val onVisibilityToggleClicked: (SettingsPaymentCardsViewModel.Item) -> Unit,
    private val onCustomizeClicked: (SettingsPaymentCardsViewModel.Item) -> Unit,
    /** Visibility 模式：長按拖曳把手時呼叫，由 Fragment 交給 ItemTouchHelper 開始拖曳 */
    private val onDragHandleLongPressed: (RecyclerView.ViewHolder) -> Unit = {}
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
            // 只有 Visibility 模式可以拖曳排序；自訂卡面頁隱藏拖曳把手
            walletRearrangeCardDragHandle.isVisible = mode == Mode.VISIBILITY

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
                    // 只顯示卡片名稱：cardLabel 固定是「Hold to reader」沒有辨識度，
                    // 卡名從描述「Pay with 永豐 Sport Mastercard ••4952」擷取成「永豐 Sport」（與電源選單列表模式相同邏輯）
                    walletRearrangeCardTitle.text = getCardName(item)
                    // 卡名較長時允許換到第二行，不要一下子就被截斷
                    walletRearrangeCardTitle.maxLines = 2
                    walletRearrangeCardText.isVisible = false
                    walletRearrangeCardVisibilityToggle.setImageResource(
                        if (item.visible) R.drawable.ic_quick_access_wallet_visibility_visible
                        else R.drawable.ic_quick_access_wallet_visibility_invisible
                    )
                    // 隱藏中的卡片把卡面調淡，一眼就看得出來
                    walletRearrangeCardCard.alpha = if (item.visible) 1f else 0.4f
                    walletRearrangeCardVisibilityToggle.setOnClickListener {
                        onVisibilityToggleClicked(item)
                    }
                    walletRearrangeCardDragHandle.imageTintList =
                        ColorStateList.valueOf(monet.getAccentColor(context))
                    walletRearrangeCardDragHandle.setOnLongClickListener {
                        onDragHandleLongPressed(holder)
                        true
                    }
                    root.setOnClickListener(null)
                    root.isClickable = false
                }
                Mode.CUSTOMIZE -> {
                    // ViewHolder 可能被 VISIBILITY 模式重用的設定還原（同一個 Adapter 實際只會有一種模式，保險起見）
                    walletRearrangeCardTitle.maxLines = 1
                    walletRearrangeCardText.isVisible = true
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

    /**
     *  取得卡片名稱：優先從描述擷取卡名；沒有描述時退回 cardLabel，再退回卡片 ID
     */
    private fun getCardName(item: SettingsPaymentCardsViewModel.Item): CharSequence {
        val description = item.card.contentDescription?.toString()
        if (!description.isNullOrBlank()) {
            return WalletPanelViewController.extractCardName(description)
        }
        return item.card.label.ifBlank { item.card.id }
    }

    class CardViewHolder(val binding: ItemWalletRearrangeCardBinding): RecyclerView.ViewHolder(binding.root)
    class CreditViewHolder(val binding: ItemWalletPaymentCardCreditBinding): RecyclerView.ViewHolder(binding.root)

}
