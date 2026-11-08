/*
 * 列表模式（2x2，橫向捲動）的卡片 adapter。
 *
 * - 付款卡：點選即選取（設為預設卡），被選取的卡片加上發光邊框（不放大）。
 * - 會員卡：點選開啟條碼預覽；卡面一律顯示 logo 卡面（即使開啟「直接顯示條碼」）。
 */
package com.android.systemui.plugin.globalactions.wallet;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.plugins.cardblur.WalletCardBlur;
import com.android.systemui.plugins.cardcustomization.WalletCardCustomization;

import java.util.ArrayList;
import java.util.List;

class WalletCardGridAdapter extends RecyclerView.Adapter<WalletCardGridAdapter.GridViewHolder> {

    interface OnGridCardTapListener {
        void onGridCardTapped(WalletCardViewInfo card);
    }

    private final Context mContext;
    private final boolean mIsLoyalty;
    private final int mCardWidthPx;
    private final int mCardHeightPx;
    private final float mCornerRadiusPx;
    private final int mAccentColor;
    private final OnGridCardTapListener mTapListener;

    private List<WalletCardViewInfo> mData = new ArrayList<>();
    private String mSelectedCardId;

    WalletCardGridAdapter(Context context, boolean isLoyalty, int cardWidthPx, int cardHeightPx,
            float cornerRadiusPx, int accentColor, OnGridCardTapListener tapListener) {
        mContext = context;
        mIsLoyalty = isLoyalty;
        mCardWidthPx = cardWidthPx;
        mCardHeightPx = cardHeightPx;
        mCornerRadiusPx = cornerRadiusPx;
        mAccentColor = accentColor;
        mTapListener = tapListener;
        setHasStableIds(true);
    }

    void setData(List<WalletCardViewInfo> data, String selectedCardId) {
        mData = data != null ? data : new ArrayList<>();
        mSelectedCardId = selectedCardId;
        notifyDataSetChanged();
    }

    void setSelectedCardId(String cardId) {
        if (mSelectedCardId != null && mSelectedCardId.equals(cardId)) return;
        mSelectedCardId = cardId;
        notifyDataSetChanged();
    }

    String getSelectedCardId() {
        return mSelectedCardId;
    }

    @Override
    public long getItemId(int position) {
        return mData.get(position).getCardId().hashCode();
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.wallet_card_grid_item, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        WalletCardViewInfo info = mData.get(position);

        ViewGroup.LayoutParams cardLp = holder.card.getLayoutParams();
        cardLp.width = mCardWidthPx;
        cardLp.height = mCardHeightPx;
        holder.card.setLayoutParams(cardLp);
        holder.card.setRadius(mCornerRadiusPx);

        // 卡面：付款卡沿用鎖定模糊邏輯；會員卡（與列表模式）一律 logo 卡面。
        // 注意：大卡片輪播與列表共用同一個 Drawable 物件，而 Drawable 只有一組 bounds，
        // 兩個不同大小的 ImageView 會互相覆寫 bounds → 切換模式後卡面被裁切。
        // 這裡一律使用獨立副本（共用 bitmap，各自的 bounds）。
        if (mIsLoyalty) {
            holder.image.setImageDrawable(copyOf(info.getListDrawable()));
        } else {
            String cardType = info.getCardId().substring(0, 1);
            if (WalletCardCustomization.INSTANCE.getProvider().hasCustomCardArt(info.getCardId())) {
                holder.image.setImageDrawable(copyOf(info.getListDrawable()));
            } else {
                holder.image.setImageDrawable(copyOf(
                        WalletCardBlur.INSTANCE.getWalletCardBlurProvider()
                                .applyBlurToCardDrawableIfNeeded(info.getListDrawable(), cardType)));
            }
        }

        // 列表標籤：會員卡只顯示主標題（getListText 覆寫），付款卡顯示卡名
        holder.label.setText(info.getListText());
        holder.label.setContentDescription(info.getContentDescription());

        // 付款卡：顯示選取狀態（發光邊框框住「卡片 + 卡名」，維持一般大小）
        boolean selected = !mIsLoyalty && info.getCardId().equals(mSelectedCardId);
        if (selected) {
            GradientDrawable border = new GradientDrawable();
            // 與卡面圓角同心（卡角半徑 + 卡到框的間距 4dp），曲率看起來一致
            border.setCornerRadius(mCornerRadiusPx + dp(4));
            border.setColor(Color.TRANSPARENT);
            border.setStroke(dp(2), mAccentColor);
            holder.content.setBackground(border);
        } else {
            holder.content.setBackground(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (mTapListener != null) mTapListener.onGridCardTapped(info);
        });
    }

    /** 建立 Drawable 的獨立副本（共用底層 bitmap，但有自己的 bounds / 狀態） */
    private Drawable copyOf(Drawable drawable) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        if (state == null) return drawable;
        return state.newDrawable(mContext.getResources()).mutate();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                mContext.getResources().getDisplayMetrics()));
    }

    static class GridViewHolder extends RecyclerView.ViewHolder {
        final ViewGroup content;
        final CardView card;
        final ImageView image;
        final TextView label;

        GridViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.grid_item_content);
            card = itemView.findViewById(R.id.grid_card);
            image = itemView.findViewById(R.id.grid_image);
            label = itemView.findViewById(R.id.grid_label);
        }
    }
}
