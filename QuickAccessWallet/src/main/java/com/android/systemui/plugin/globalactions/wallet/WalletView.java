/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.plugin.globalactions.wallet;

import static com.android.systemui.plugin.globalactions.wallet.WalletCardCarousel.CARD_ANIM_ALPHA_DELAY;
import static com.android.systemui.plugin.globalactions.wallet.WalletCardCarousel.CARD_ANIM_ALPHA_DURATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import androidx.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.plugin.globalactions.wallet.WalletPopupMenu.OverflowItem;

import java.util.List;


/**
 * Contains the entire wallet view. Coordinates the label text with the currently selected card in
 * the contained carousel. Also capable of showing the lock screen error message.
 */
class WalletView extends FrameLayout implements WalletCardCarousel.OnCardScrollListener {

    private static final int CAROUSEL_IN_ANIMATION_DURATION = 300;
    private static final int CAROUSEL_OUT_ANIMATION_DURATION = 200;
    private static final int CARD_LABEL_ANIM_DELAY = 133;

    private final ViewGroup mCardCarouselContainer;
    private final WalletCardCarousel mCardCarousel;
    private final TextView mCardLabel;
    private final TextView mErrorView;
    private final ViewGroup mEmptyStateView;
    private final ImageView mOverflowButton;
    private final WalletPopupMenu mOverflowPopup;
    private final int mIconSizePx;
    private final Interpolator mInInterpolator;
    private final Interpolator mOutInterpolator;
    private final float mAnimationTranslationX;
    private CharSequence mCenterCardText;

    // ---- 展開 / 收起（卡匣拆分模式才啟用） ----
    /** 收合狀態變更（動畫結束後）通知，用來記錄狀態與高度 */
    interface OnCollapseChangedListener {
        void onCollapseChanged(boolean collapsed);
    }

    // ---- 檢視模式切換（大卡片 ↔ 列表） ----
    /** 檢視模式變更通知，用來記錄狀態並重新量測高度 */
    interface OnViewModeChangedListener {
        void onViewModeChanged(boolean listMode);
    }

    private static final int COLLAPSE_ANIMATION_DURATION = 250;
    /** 收起時的標題字級（與會員卡標題一致） */
    private static final float COLLAPSED_TITLE_TEXT_SIZE_SP = 18f;
    private final ImageView mViewModeButton;
    private final RecyclerView mCardGrid;
    private boolean mCollapsible;
    private boolean mCollapsed;
    private boolean mListMode;
    private boolean mViewModeEnabled;
    private CharSequence mCollapsedTitle;
    private OnCollapseChangedListener mCollapseListener;
    private OnViewModeChangedListener mViewModeListener;
    /** 最後一次置中的卡片：展開時用來還原標題 */
    @Nullable private WalletCardViewInfo mLastCenterCard;

    public WalletView(Context context) {
        this(context, null);
    }

    public WalletView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.wallet_view, this);
        mCardCarouselContainer = requireViewById(R.id.card_carousel_container);
        mCardCarousel = requireViewById(R.id.card_carousel);
        mCardCarousel.setCardScrollListener(this);
        mCardLabel = requireViewById(R.id.card_label);
        mOverflowButton = requireViewById(R.id.menu_btn);
        mOverflowPopup = new WalletPopupMenu(context, mOverflowButton);
        mOverflowButton.setOnClickListener(v -> mOverflowPopup.show());
        mErrorView = requireViewById(R.id.error_view);
        mEmptyStateView = requireViewById(R.id.empty_state);
        mIconSizePx = getResources().getDimensionPixelSize(R.dimen.icon_size);
        mInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        mOutInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_cubic);
        mAnimationTranslationX = mCardCarousel.getCardWidthPx() / 4f;
        mViewModeButton = requireViewById(R.id.view_mode_btn);
        mCardGrid = requireViewById(R.id.card_grid);
    }

    /**
     * 啟用展開 / 收起：標題列右側顯示箭頭，點箭頭或標題切換。
     * 收起時隱藏卡片輪播、標題顯示 collapsedTitle；展開時標題回到目前置中卡片的文字。
     *
     * @param collapsedTitle 收起時的標題（例如「信用卡匣」）
     * @param collapsed      初始是否收起（上次的狀態）
     */
    void setupCollapsible(CharSequence collapsedTitle, boolean collapsed,
            OnCollapseChangedListener listener) {
        mCollapsible = true;
        mCollapsedTitle = collapsedTitle;
        mCollapseListener = listener;
        // 收合改由點擊標題觸發（原本的上下箭頭已移除）
        mCardLabel.setOnClickListener(v -> setCollapsed(!mCollapsed, true));
        setCollapsed(collapsed, false);
    }

    /**
     * 啟用檢視模式切換（大卡片 ↔ 列表）：標題列右側顯示切換圖示。
     * @param listMode 初始是否為列表模式
     */
    void setupViewMode(boolean listMode, OnViewModeChangedListener listener) {
        mViewModeEnabled = true;
        mListMode = listMode;
        mViewModeListener = listener;
        mViewModeButton.setOnClickListener(v -> setListMode(!mListMode, true));
        updateViewModeButton();
        updateContentVisibility();
        // 初始若為列表模式，標題也要顯示卡匣名稱
        updateLabelForCollapseState();
    }

    boolean isListMode() {
        return mListMode;
    }

    RecyclerView getCardGrid() {
        return mCardGrid;
    }

    private void setListMode(boolean listMode, boolean notify) {
        mListMode = listMode;
        updateViewModeButton();
        updateContentVisibility();
        // 列表模式標題固定顯示卡匣名稱（非單張卡片名）
        updateLabelForCollapseState();
        // 切回大卡片時輪播剛從 GONE 變回 VISIBLE，需要先完成一次量測，
        // 再通知外層重新量測高度，否則外層會沿用舊高度造成卡面被裁切 / 多出空白
        post(() -> {
            if (!mListMode) mCardCarousel.requestLayout();
            forceRelayoutUpTree();
            if (notify && mViewModeListener != null) mViewModeListener.onViewModeChanged(listMode);
        });
    }

    private void updateViewModeButton() {
        // 大卡片模式時顯示「切到列表」圖示；列表模式時顯示「切回大卡片」圖示
        mViewModeButton.setImageResource(mListMode
                ? R.drawable.ic_wallet_view_card
                : R.drawable.ic_wallet_view_grid);
    }

    /** 依收合 / 檢視模式決定輪播、列表與切換鈕的顯示 */
    private void updateContentVisibility() {
        boolean showContent = !mCollapsible || !mCollapsed;
        mCardCarousel.setVisibility(showContent && !mListMode ? VISIBLE : GONE);
        mCardGrid.setVisibility(showContent && mListMode ? VISIBLE : GONE);
        // 切換鈕只在展開時顯示
        mViewModeButton.setVisibility(mViewModeEnabled && showContent ? VISIBLE : GONE);
    }

    private void setCollapsed(boolean collapsed, boolean animate) {
        // 取消上一次的淡入，避免快速連點時透明度停在半途
        mCardCarousel.animate().cancel();
        mCardCarousel.setAlpha(1f);
        mCollapsed = collapsed;
        updateLabelForCollapseState();

        // 第三版做法：不在卡匣內部自己做高度動畫。
        // 卡匣放在電源選單的 RecyclerView（wrap_content，位於 CoordinatorLayout 中）裡，
        // 從項目內部改高度時，RecyclerView 不一定會重新量測該項目 → 展開後高度沒長、卡片疊到下方區塊。
        // 改成：直接切換內容的顯示 / 隱藏，再透過 listener 通知外層 adapter 呼叫
        // notifyItemChanged(position, payload)；這是 RecyclerView 標準的「項目大小改變」流程。
        updateContentVisibility();
        if (!animate) {
            return;
        }
        if (!collapsed && !mListMode) {
            // 展開：卡片淡入（下方區塊的移動由 RecyclerView 的 ItemAnimator 處理）
            mCardCarousel.setAlpha(0f);
            mCardCarousel.animate().alpha(1f).setDuration(COLLAPSE_ANIMATION_DURATION).start();
        }
        forceRelayoutUpTree();
        if (mCollapseListener != null) mCollapseListener.onCollapseChanged(collapsed);
    }

    /**
     * 修正「收起後再展開，卡匣與下方區塊重疊」：
     * 卡匣放在電源選單的 RecyclerView 裡，RecyclerView 重新排版時只會重新量測「有標記需要排版」的項目，
     * 否則沿用舊的量測高度（量測快取）。高度動畫期間標記沒有可靠地傳到項目根 View，
     * 項目高度停在收起時的值，而卡片因 clipChildren=false 仍畫在外面 → 疊到下一個區塊上。
     * 這裡把自己到最上層的每一層都 forceLayout()，確保 RecyclerView 重新量測此項目。
     *
     * 注意：不能只在自己身上 requestLayout()——requestLayout 往上傳遞時，遇到「已標記需要排版」的父層就會停止，
     * 而上面的迴圈已經把每一層都標記了，結果 ViewRootImpl 收不到通知、根本不會排版（第一版修正失敗的原因）。
     * 所以改由最上層的 root view 呼叫 requestLayout()，它的父層是 ViewRootImpl，一定會排程下一次排版。
     */
    private void forceRelayoutUpTree() {
        View view = this;
        View root = this;
        while (view != null) {
            view.forceLayout();
            root = view;
            android.view.ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        root.requestLayout();
    }

    /** 設定卡匣上方的間距（卡片輪播容器的 marginTop），用於調整卡匣與上一個區塊的距離 */
    void setTopMarginPx(int topMarginPx) {
        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) mCardCarouselContainer.getLayoutParams();
        lp.topMargin = topMarginPx;
        mCardCarouselContainer.setLayoutParams(lp);
    }

    /** 依收合狀態更新標題：收起 → 卡匣名稱；展開 → 目前置中卡片的文字 */
    private void updateLabelForCollapseState() {
        mCardLabel.setAlpha(1f);
        // 收合時或列表模式時：標題固定顯示卡匣名稱（「信用卡匣」/「會員卡匣」），不顯示單張卡片名
        if (mCollapsible && (mCollapsed || mListMode)) {
            mCenterCardText = null; // 回到大卡片模式後讓 onCardScroll 重新套用卡片標題
            mCardLabel.setText(mCollapsedTitle);
            mCardLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, COLLAPSED_TITLE_TEXT_SIZE_SP);
            mCardLabel.setCompoundDrawablesRelative(null, null, null, null);
        } else if (mLastCenterCard != null) {
            mCenterCardText = null;
            applyCenterCardLabel(mLastCenterCard);
        }
    }

    /** 把卡片的標題文字、字級、小圖示套用到標題列 */
    private void applyCenterCardLabel(WalletCardViewInfo centerCard) {
        CharSequence centerCardText = centerCard.getText();
        if (TextUtils.equals(mCenterCardText, centerCardText)) return;
        mCenterCardText = centerCardText;
        mCardLabel.setText(centerCardText);
        // 依卡片類型套用標題字級（會員卡 / 信用卡匣較大）
        mCardLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, centerCard.getTextSizeSp());
        Drawable icon = centerCard.getIcon();
        if (icon != null) {
            icon.setBounds(0, 0, mIconSizePx, mIconSizePx);
        }
        mCardLabel.setCompoundDrawablesRelative(icon, null, null, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Forward touch events to card carousel to allow for swiping outside carousel bounds.
        return mCardCarousel.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public void onCardScroll(WalletCardViewInfo centerCard, WalletCardViewInfo nextCard,
            float percentDistanceFromCenter) {
        mLastCenterCard = centerCard;
        // 收起或列表模式時標題固定為卡匣名稱，不跟著卡片變動
        if (mCollapsible && (mCollapsed || mListMode)) return;
        CharSequence centerCardText = centerCard.getText();
        applyCenterCardLabel(centerCard);
        if (TextUtils.equals(centerCardText, nextCard.getText())) {
            mCardLabel.setAlpha(1f);
        } else {
            mCardLabel.setAlpha(percentDistanceFromCenter);
        }
    }

    void showCardCarousel(
            List<WalletCardViewInfo> data, int selectedIndex, OverflowItem[] menuItems) {
        boolean shouldAnimate = mCardCarousel.setData(data, selectedIndex);
        mOverflowPopup.setMenuItems(menuItems);
        mOverflowButton.setVisibility(menuItems.length == 0 ? GONE : VISIBLE);
        mCardCarouselContainer.setVisibility(VISIBLE);
        mErrorView.setVisibility(GONE);
        // 依目前檢視模式顯示輪播或列表
        updateContentVisibility();
        if (shouldAnimate) {
            // If the empty state is visible, animate it away and delay the card carousel animation
            int emptyStateAnimDelay = 0;
            if (mEmptyStateView.getVisibility() == VISIBLE) {
                emptyStateAnimDelay = CARD_ANIM_ALPHA_DURATION;
                mEmptyStateView.animate()
                        .alpha(0)
                        .setDuration(emptyStateAnimDelay)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mEmptyStateView.setVisibility(GONE);
                            }
                        })
                        .start();
            }

            // 收起狀態下標題已是卡匣名稱，不做淡入（也避免停在半透明）
            if (!(mCollapsible && mCollapsed)) mCardLabel.setAlpha(0f);
            mCardLabel.animate().alpha(1f)
                    .setStartDelay(CARD_LABEL_ANIM_DELAY + emptyStateAnimDelay)
                    .setDuration(CARD_ANIM_ALPHA_DURATION)
                    .start();
            mOverflowButton.setAlpha(0f);
            mOverflowButton.animate().alpha(1f)
                    .setStartDelay(CARD_LABEL_ANIM_DELAY + emptyStateAnimDelay)
                    .setDuration(CARD_ANIM_ALPHA_DURATION)
                    .start();
            mCardCarousel.setExtraAnimationDelay(emptyStateAnimDelay);
            mCardCarousel.setTranslationX(mAnimationTranslationX);
            mCardCarousel.animate().translationX(0)
                    .setInterpolator(mInInterpolator)
                    .setDuration(CAROUSEL_IN_ANIMATION_DURATION)
                    .setStartDelay(emptyStateAnimDelay)
                    .start();
        }
    }

    void animateDismissal() {
        if (mCardCarouselContainer.getVisibility() != VISIBLE) {
            return;
        }
        mOverflowPopup.dismiss();
        mCardCarousel.animate().translationX(mAnimationTranslationX)
                .setInterpolator(mOutInterpolator)
                .setDuration(CAROUSEL_OUT_ANIMATION_DURATION)
                .start();
        mCardCarouselContainer.animate()
                .alpha(0f)
                .setDuration(CARD_ANIM_ALPHA_DURATION)
                .setStartDelay(CARD_ANIM_ALPHA_DELAY)
                .start();
    }

    void showEmptyStateView(Drawable logo, CharSequence logoContentDescription, CharSequence label,
            OnClickListener clickListener) {
        mEmptyStateView.setVisibility(VISIBLE);
        mErrorView.setVisibility(GONE);
        mCardCarouselContainer.setVisibility(GONE);
        ImageView logoView = mEmptyStateView.requireViewById(R.id.icon);
        logoView.setImageDrawable(logo);
        logoView.setContentDescription(logoContentDescription);
        mEmptyStateView.<TextView>requireViewById(R.id.title).setText(label);
        mEmptyStateView.setOnClickListener(clickListener);
    }

    void showDeviceLockedMessage() {
        showErrorMessage(getResources().getText(R.string.error_user_locked));
    }

    void showErrorMessage(@Nullable CharSequence message) {
        if (TextUtils.isEmpty(message)) {
            message = getResources().getText(R.string.error_generic);
        }
        mErrorView.setText(message);
        mErrorView.setVisibility(VISIBLE);
        mCardCarouselContainer.setVisibility(GONE);
        mEmptyStateView.setVisibility(GONE);
    }

    void hide() {
        setVisibility(GONE);
    }

    void show() {
        setVisibility(VISIBLE);
    }

    void hideErrorMessage() {
        mErrorView.setVisibility(GONE);
    }

    int getIconSizePx() {
        return mIconSizePx;
    }

    WalletCardCarousel getCardCarousel() {
        return mCardCarousel;
    }

    TextView getErrorView() {
        return mErrorView;
    }

    ViewGroup getEmptyStateView() {
        return mEmptyStateView;
    }

    ViewGroup getCardCarouselContainer() {
        return mCardCarouselContainer;
    }

    View getOverflowIcon() {
        return mOverflowButton;
    }

    ListPopupWindow getOverflowPopup() {
        return mOverflowPopup;
    }
}
