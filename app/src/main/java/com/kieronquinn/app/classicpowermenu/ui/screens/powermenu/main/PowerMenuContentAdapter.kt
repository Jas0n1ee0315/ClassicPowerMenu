package com.kieronquinn.app.classicpowermenu.ui.screens.powermenu.main

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.android.systemui.controls.ui.ControlsUiController
import com.android.systemui.plugin.globalactions.wallet.WalletPanelViewController
import com.kieronquinn.app.classicpowermenu.databinding.ItemPowerMenuCardsBinding
import com.kieronquinn.app.classicpowermenu.databinding.ItemPowerMenuControlsBinding
import com.kieronquinn.app.classicpowermenu.model.power.PowerMenuContentItem
import com.kieronquinn.app.classicpowermenu.model.power.PowerMenuContentItemType

/**
 *  @param paymentWalletController 信用卡匣（只放付款卡）
 *  @param loyaltyWalletController 會員卡匣（只放會員卡）
 */
class PowerMenuContentAdapter(
    context: Context,
    private var items: List<PowerMenuContentItem>,
    val dismissGlobalActionsMenu: () -> Unit,
    var paymentWalletController: WalletPanelViewController?,
    var loyaltyWalletController: WalletPanelViewController?,
    var controlsUiController: ControlsUiController?
): RecyclerView.Adapter<PowerMenuContentAdapter.ViewHolder>() {

    companion object {
        /** 卡匣高度改變（展開 / 收起）的 payload：只重新量測，不重新綁定 */
        private const val PAYLOAD_SECTION_SIZE_CHANGED = "section_size_changed"
    }

    fun notifyItemChangeOfType(type: PowerMenuContentItem){
        val index = items.indexOf(type)
        if(index == -1) return
        notifyItemChanged(index)
    }

    private val layoutInflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return items[position].itemType.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(PowerMenuContentItemType.values()[viewType]){
            // 信用卡匣與會員卡匣共用同一個容器版面，但為不同 view type，避免 ViewHolder 被互相回收
            PowerMenuContentItemType.PAYMENT_CARDS -> ViewHolder.PaymentCards(
                ItemPowerMenuCardsBinding.inflate(
                    layoutInflater,
                    parent,
                    false
                )
            )
            PowerMenuContentItemType.LOYALTY_CARDS -> ViewHolder.LoyaltyCards(
                ItemPowerMenuCardsBinding.inflate(
                    layoutInflater,
                    parent,
                    false
                )
            )
            PowerMenuContentItemType.CONTROLS -> ViewHolder.Controls(
                ItemPowerMenuControlsBinding.inflate(
                    layoutInflater,
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when(holder){
            is ViewHolder.PaymentCards ->
                setupCards(holder.binding, paymentWalletController, PowerMenuContentItem.PaymentCards)
            is ViewHolder.LoyaltyCards ->
                setupCards(holder.binding, loyaltyWalletController, PowerMenuContentItem.LoyaltyCards)
            is ViewHolder.Controls -> holder.setupControls()
        }
    }

    /**
     *  帶 payload 的重新綁定：卡匣展開 / 收起只需要 RecyclerView 重新量測項目高度，
     *  不重新查詢卡片、不重建 View，所以直接略過。
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SECTION_SIZE_CHANGED)) return
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun setupCards(
        binding: ItemPowerMenuCardsBinding,
        controller: WalletPanelViewController?,
        item: PowerMenuContentItem
    ) =
        with(binding.itemPowerMenuCardsContainer) {
            removeAllViews()
            controller?.let {
                // 卡匣展開 / 收起 → 通知 RecyclerView 這個項目大小改變，重新量測並讓下方區塊平滑移動
                it.setOnSectionSizeChangedListener {
                    val index = items.indexOf(item)
                    if (index != -1) notifyItemChanged(index, PAYLOAD_SECTION_SIZE_CHANGED)
                }
                it.queryWalletCards()
                // 重新綁定時卡匣 View 可能還掛在舊容器上，先拆下再加入
                (it.panelContent.parent as? ViewGroup)?.removeView(it.panelContent)
                addView(it.panelContent)
            }
        }

    private fun ViewHolder.Controls.setupControls() = with(binding.itemPowerMenuControlsContainer) {
        removeAllViews()
        controlsUiController?.hide()
        controlsUiController?.show(this) {
            dismissGlobalActionsMenu()
        }
    }

    sealed class ViewHolder(open val binding: ViewBinding): RecyclerView.ViewHolder(binding.root){
        data class PaymentCards(override val binding: ItemPowerMenuCardsBinding): ViewHolder(binding)
        data class LoyaltyCards(override val binding: ItemPowerMenuCardsBinding): ViewHolder(binding)
        data class Controls(override val binding: ItemPowerMenuControlsBinding): ViewHolder(binding)
    }

}