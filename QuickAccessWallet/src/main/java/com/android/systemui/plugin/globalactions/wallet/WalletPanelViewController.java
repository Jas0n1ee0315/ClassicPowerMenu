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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quickaccesswallet.GetWalletCardsError;
import android.service.quickaccesswallet.GetWalletCardsRequest;
import android.service.quickaccesswallet.GetWalletCardsResponse;
import android.service.quickaccesswallet.QuickAccessWalletClient;
import android.service.quickaccesswallet.SelectWalletCardRequest;
import android.service.quickaccesswallet.WalletCard;
import android.service.quickaccesswallet.WalletServiceEvent;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.systemui.plugin.globalactions.wallet.WalletPopupMenu.OverflowItem;
import com.android.systemui.plugins.GlobalActionsPanelPlugin;
import com.android.systemui.plugins.activitystarter.WalletActivityStarter;
import com.android.systemui.plugins.activitystarter.WalletActivityStarterProvider;
import com.android.systemui.plugins.loyaltycards.WalletLoyaltyCardCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.android.systemui.plugins.cardcustomization.WalletCardCustomization;
import com.android.systemui.plugins.cardcustomization.WalletCardCustomizationProvider;

import kotlin.Unit;
import kotlin.reflect.KFunction;

public class WalletPanelViewController implements
        GlobalActionsPanelPlugin.PanelViewController,
        WalletCardCarousel.OnSelectionListener,
        QuickAccessWalletClient.OnWalletCardsRetrievedCallback,
        QuickAccessWalletClient.WalletServiceEventListener {

    private static final String TAG = "WalletPanelViewCtrl";
    private static final int MAX_CARDS = 10;
    private static final long SELECTION_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final String PREFS_WALLET_VIEW_HEIGHT = "wallet_view_height";
    private static final String PREFS_HAS_CARDS = "has_cards";
    private static final String SETTINGS_PKG = "com.android.settings";
    private static final String SETTINGS_ACTION = SETTINGS_PKG + ".GLOBAL_ACTIONS_PANEL_SETTINGS";
    private final Context mSysuiContext;
    private final Context mPluginContext;
    private final QuickAccessWalletClient mWalletClient;
    private final WalletView mWalletView;
    private final WalletCardCarousel mWalletCardCarousel;
    private final GlobalActionsPanelPlugin.Callbacks mPluginCallbacks;
    private final ExecutorService mExecutor;
    private final Handler mHandler;
    private final Runnable mSelectionRunnable = this::selectCard;
    private final SharedPreferences mPrefs;
    private boolean mIsDeviceLocked;
    private boolean mIsDismissed;
    private boolean mHasRegisteredListener;
    private String mDefaultCardId;
    private String mSelectedCardId;
    private final WalletLoyaltyCardCallback mWalletLoyaltyCardCallback;

    // ---- 卡匣拆分模式 ----
    /** 原本行為：付款卡 + 會員卡放在同一個輪播 */
    public static final int MODE_COMBINED = 0;
    /** 只放付款卡（信用卡匣）：標題顯示卡片名稱，可展開 / 收起 */
    public static final int MODE_PAYMENT_ONLY = 1;
    /** 只放會員卡（會員卡匣）：不向 Quick Access Wallet 服務取卡，可展開 / 收起 */
    public static final int MODE_LOYALTY_ONLY = 2;
    /** 收合狀態（依模式分開記錄，下次開電源選單沿用） */
    private static final String PREFS_COLLAPSED = "collapsed";
    /** 付款卡標題字級：與會員卡標題一致 */
    private static final float SPLIT_PAYMENT_TEXT_SIZE_SP = 18f;
    private final int mMode;

    private final WalletActivityStarter activityStarter = WalletActivityStarterProvider.INSTANCE.getActivityStarter();

    public WalletPanelViewController(
            Context sysuiContext,
            Context pluginContext,
            QuickAccessWalletClient walletClient,
            GlobalActionsPanelPlugin.Callbacks pluginCallbacks,
            boolean isDeviceLocked,
            WalletLoyaltyCardCallback walletLoyaltyCardCallback) {
        this(sysuiContext, pluginContext, walletClient, pluginCallbacks, isDeviceLocked,
                walletLoyaltyCardCallback, MODE_COMBINED);
    }

    /**
     * @param mode {@link #MODE_COMBINED} / {@link #MODE_PAYMENT_ONLY} / {@link #MODE_LOYALTY_ONLY}
     */
    public WalletPanelViewController(
            Context sysuiContext,
            Context pluginContext,
            QuickAccessWalletClient walletClient,
            GlobalActionsPanelPlugin.Callbacks pluginCallbacks,
            boolean isDeviceLocked,
            WalletLoyaltyCardCallback walletLoyaltyCardCallback,
            int mode) {
        mMode = mode;
        mSysuiContext = sysuiContext;
        mPluginContext = pluginContext;
        mWalletClient = walletClient;
        mPrefs = mSysuiContext.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        mPluginCallbacks = pluginCallbacks;
        mIsDeviceLocked = isDeviceLocked;
        mWalletLoyaltyCardCallback = walletLoyaltyCardCallback;
        mWalletView = new WalletView(pluginContext);
        mWalletView.setClipChildren(false);
        mWalletView.setMinimumHeight(getExpectedMinHeight());
        mWalletView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        mWalletCardCarousel = mWalletView.getCardCarousel();
        mWalletCardCarousel.setSelectionListener(this);
        mHandler = new Handler(Looper.myLooper());
        mExecutor = Executors.newSingleThreadExecutor();
        if (isSplitMode()) {
            // 拆分模式：沒有卡片時整個區塊隱藏，不預先顯示空狀態；
            // 標題列右側加上展開 / 收起箭頭，收合狀態記在 prefs
            boolean collapsed = mPrefs.getBoolean(prefKey(PREFS_COLLAPSED), false);
            CharSequence collapsedTitle = mPluginContext.getText(mMode == MODE_PAYMENT_ONLY
                    ? R.string.wallet_collapsed_title_payment
                    : R.string.wallet_collapsed_title_loyalty);
            if (mMode == MODE_LOYALTY_ONLY) {
                // 會員卡匣接在信用卡匣下方：上方間距縮小（信用卡匣維持原本 28dp，與電源按鈕保持距離）
                mWalletView.setTopMarginPx(mPluginContext.getResources()
                        .getDimensionPixelSize(R.dimen.wallet_split_section_top_margin));
            }
            mWalletView.setupCollapsible(collapsedTitle, collapsed, isCollapsed -> {
                mPrefs.edit().putBoolean(prefKey(PREFS_COLLAPSED), isCollapsed).apply();
                // 高度改變了，重新記錄，下次開啟時預留的高度才正確（避免下方區塊跳動）
                recordHeightOnNextLayout();
                // 通知外層 RecyclerView：這個卡匣的高度變了（由 adapter 呼叫 notifyItemChanged）
                if (mOnSectionSizeChangedListener != null) mOnSectionSizeChangedListener.run();
            });
        } else if (!mPrefs.getBoolean(PREFS_HAS_CARDS, false)) {
            // The empty state view is shown preemptively when cards were not returned last time
            // to decrease perceived latency.
            showEmptyStateView();
        }
    }

    /** 卡匣展開 / 收起造成高度改變時呼叫（由電源選單的 adapter 設定，用來 notifyItemChanged） */
    private Runnable mOnSectionSizeChangedListener;

    public void setOnSectionSizeChangedListener(Runnable listener) {
        mOnSectionSizeChangedListener = listener;
    }

    /** 發卡組織名稱（只在後面接著卡號時才會被截掉，避免誤砍卡名中剛好含有的字） */
    private static final String CARD_NETWORKS =
            // 銀聯 = 銀聯（用 Unicode 跳脫，避免原始碼編碼問題）
            "visa|master\\s?card|jcb|american express|amex|union\\s?pay|\\u9280\\u806F|discover|diners club|maestro";
    /** 「(發卡組織) ••1234 …」：圓點（• · ● ∙ *）+ 末 2~4 碼，發卡組織可有可無 */
    private static final java.util.regex.Pattern CARD_SUFFIX_WITH_DOTS = java.util.regex.Pattern.compile(
            "(?i)\\s*(?:(?:" + CARD_NETWORKS + ")\\s*)?[\\u2022\\u00B7\\u25CF\\u2219*]+\\s*\\d{2,4}.*$");
    /** 「發卡組織 1234 …」：沒有圓點時，必須有發卡組織才截，避免把卡名中的數字（如 2.0）當成卡號 */
    private static final java.util.regex.Pattern CARD_SUFFIX_NETWORK_DIGITS = java.util.regex.Pattern.compile(
            "(?i)\\s*(?:" + CARD_NETWORKS + ")\\s+\\d{4}\\b.*$");

    /**
     * 從 Google 錢包的卡片描述取出卡名。
     * 描述範例：「Pay with 永豐 Sport Mastercard ••4952」
     *   1. 去掉開頭的「Pay with」
     *   2. 去掉結尾的「發卡組織 + 虛擬卡末碼」（Mastercard ••4952）及之後的文字
     * → 「永豐 Sport」。若截完是空字串，退回只去掉「Pay with」的結果。
     */
    static String extractCardName(String description) {
        String withoutPrefix = description.replaceFirst("(?i)^\\s*pay with\\s+", "").trim();
        String name = CARD_SUFFIX_WITH_DOTS.matcher(withoutPrefix).replaceFirst("");
        name = CARD_SUFFIX_NETWORK_DIGITS.matcher(name).replaceFirst("").trim();
        return name.isEmpty() ? withoutPrefix : name;
    }

    private boolean isSplitMode() {
        return mMode != MODE_COMBINED;
    }

    /** 拆分模式的 prefs key 加上模式後綴，兩個卡匣各自記錄高度 / 收合狀態 */
    private String prefKey(String base) {
        return mMode == MODE_COMBINED ? base : base + "_" + mMode;
    }

    /**
     * Implements {@link GlobalActionsPanelPlugin.PanelViewController}. Returns the {@link View}
     * containing the Quick Access Wallet.
     */
    @Override
    public View getPanelContent() {
        return mWalletView;
    }

    /**
     * Implements {@link GlobalActionsPanelPlugin.PanelViewController}. Invoked when the view
     * containing the Quick Access Wallet is dismissed.
     */
    @Override
    public void onDismissed() {
        if (mIsDismissed) {
            return;
        }
        mIsDismissed = true;
        mSelectedCardId = null;
        mHandler.removeCallbacks(mSelectionRunnable);
        // 會員卡匣沒有連接 Quick Access Wallet 服務，不需通知
        if (mMode != MODE_LOYALTY_ONLY) {
            mWalletClient.notifyWalletDismissed();
            mWalletClient.removeWalletServiceEventListener(this);
        }
        mWalletView.animateDismissal();
    }

    /**
     * Implements {@link GlobalActionsPanelPlugin.PanelViewController}. Invoked when the device is
     * either locked or unlocked while the wallet is visible.
     */
    @Override
    public void onDeviceLockStateChanged(boolean deviceLocked) {
        if (mIsDismissed || mIsDeviceLocked == deviceLocked || !mIsDeviceLocked) {
            // Disregard repeat events and events after unlock
            return;
        }
        mIsDeviceLocked = deviceLocked;
        // Cards are re-queried because the wallet application may wish to change card art, icons,
        // text, or other attributes depending on the lock state of the device.
        queryWalletCards();
    }

    /**
     * Query wallet cards from the client and display them on screen.
     */
    public void queryWalletCards() {
        if (mIsDismissed) {
            return;
        }
        if (mMode == MODE_LOYALTY_ONLY) {
            queryLoyaltyCardsOnly();
            return;
        }
        if (!mHasRegisteredListener) {
            // Listener is registered even when device is locked. Should only be registered once.
            mWalletClient.addWalletServiceEventListener(this);
            mHasRegisteredListener = true;
        }
        if (mIsDeviceLocked && !mWalletClient.isWalletFeatureAvailableWhenDeviceLocked()) {
            mWalletView.hide();
            return;
        }
        mWalletView.show();
        mWalletView.hideErrorMessage();
        int cardWidthPx = mWalletCardCarousel.getCardWidthPx();
        int cardHeightPx = mWalletCardCarousel.getCardHeightPx();
        int iconSizePx = mWalletView.getIconSizePx();
        GetWalletCardsRequest request =
                new GetWalletCardsRequest(cardWidthPx, cardHeightPx, iconSizePx, MAX_CARDS);
        mWalletClient.getWalletCards(mExecutor, request, this);
    }

    /**
     * 會員卡匣：不向 Quick Access Wallet 服務取付款卡，直接透過會員卡 callback 取得會員卡。
     * 會員卡鎖定時能否開啟由卡片本身的 onClick（Access While Locked 設定）處理，這裡不隱藏。
     */
    private void queryLoyaltyCardsOnly() {
        mWalletView.show();
        mWalletView.hideErrorMessage();
        ArrayList<WalletCardViewInfo> data = new ArrayList<>();
        mWalletLoyaltyCardCallback.getMethod().invoke(data, () -> {
            // 不檢查 isAttachedToWindow：setupWalletView 內用 post，未附加時會等附加後才執行
            setupWalletView(data, 0);
        });
    }

    /**
     * Implements {@link QuickAccessWalletClient.OnWalletCardsRetrievedCallback}. Called when cards
     * are retrieved successfully from the service. This is called on {@link #mExecutor}.
     */
    @Override
    public void onWalletCardsRetrieved(GetWalletCardsResponse response) {
        if (mIsDismissed) {
            return;
        }
        List<WalletCard> walletCards = new ArrayList<>(response.getWalletCards());

        // 先記下 Google Wallet 原本選定（預設）的卡片 ID，過濾後再換算成新的索引
        String originalSelectedCardId = null;
        int originalSelectedIndex = response.getSelectedIndex();
        if (originalSelectedIndex >= 0 && originalSelectedIndex < walletCards.size()) {
            originalSelectedCardId = walletCards.get(originalSelectedIndex).getCardId();
        }

        WalletCardCustomizationProvider customization =
                WalletCardCustomization.INSTANCE.getProvider();

        walletCards.removeIf(new Predicate<WalletCard>() {
            @Override
            public boolean test(WalletCard walletCard) {
                // 原本的過濾：ID 長度 <= 1 的是佔位卡
                if (walletCard.getCardId().length() <= 1) return true;
                // 新增：使用者在 Visibility 設定中隱藏的付款卡
                return customization.isCardHidden(walletCard.getCardId());
            }
        });

        ArrayList<WalletCardViewInfo> data = new ArrayList<>(walletCards.size());
        for (WalletCard card : walletCards) {
            data.add(new QAWalletCardViewInfo(card, customization));
        }

        // 換算過濾後的選定索引；原本選定的卡被隱藏時退回第一張
        int selectedIndex = 0;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getCardId().equals(originalSelectedCardId)) {
                selectedIndex = i;
                break;
            }
        }
        final int finalSelectedIndex = selectedIndex;

        if(walletCards.isEmpty()){
            mDefaultCardId = null;
        }else {
            mDefaultCardId = data.get(selectedIndex).getCardId();
        }

        if (mMode == MODE_PAYMENT_ONLY) {
            // 信用卡匣：不接上會員卡；沒有付款卡時 setupWalletView 會隱藏整個區塊
            setupWalletView(data, finalSelectedIndex);
            return;
        }

        mWalletLoyaltyCardCallback.getMethod().invoke(data, () -> {
            if(data.isEmpty()) return;
            //Make sure we're still attached
            if(mWalletView.isAttachedToWindow()) {
                setupWalletView(data, finalSelectedIndex);
            }
        });
    }

    private void setupWalletView(List<WalletCardViewInfo> data, int selectedIndex){
        // Get on main thread for UI updates
        mWalletView.post(() -> {
            if (mIsDismissed) {
                return;
            }
            if (data.isEmpty()) {
                if (isSplitMode()) {
                    // 拆分模式：沒有卡片就整個卡匣不顯示（含標題列）
                    mWalletView.hide();
                    mWalletView.setMinimumHeight(0);
                    mPrefs.edit().putInt(prefKey(PREFS_WALLET_VIEW_HEIGHT), 0).apply();
                    return;
                }
                showEmptyStateView();
            } else {
                if (isSplitMode()) mWalletView.show();
                mWalletView.showCardCarousel(data, selectedIndex, getOverflowItems());
            }
            // The empty state view will not be shown preemptively next time if cards were returned
            mPrefs.edit().putBoolean(prefKey(PREFS_HAS_CARDS), !data.isEmpty()).apply();
            removeMinHeightAndRecordHeightOnLayout();
        });
    }

    /**
     * Implements {@link QuickAccessWalletClient.OnWalletCardsRetrievedCallback}. Called when there
     * is an error during card retrieval. This will be run on the {@link #mExecutor}.
     */
    @Override
    public void onWalletCardRetrievalError(GetWalletCardsError error) {
        mWalletView.post(() -> {
            if (mIsDismissed) {
                return;
            }
            mWalletView.showErrorMessage(error.getMessage());
        });
    }

    /**
     * Implements {@link QuickAccessWalletClient.WalletServiceEventListener}. Called when the wallet
     * application propagates an event, such as an NFC tap, to the quick access wallet view.
     */
    @Override
    public void onWalletServiceEvent(WalletServiceEvent event) {
        if (mIsDismissed) {
            return;
        }
        switch (event.getEventType()) {
            case WalletServiceEvent.TYPE_NFC_PAYMENT_STARTED:
                mPluginCallbacks.dismissGlobalActionsMenu();
                onDismissed();
                break;
            case 2: //TYPE_WALLET_CARDS_UPDATED = 2
                queryWalletCards();
                break;
            default:
                Log.w(TAG, "onWalletServiceEvent: Unknown event type");
        }
    }

    /**
     * Implements {@link WalletCardCarousel.OnSelectionListener}. Called when the user selects a
     * card from the carousel by scrolling to it.
     */
    @Override
    public void onCardSelected(WalletCardViewInfo card) {
        if (mIsDismissed) {
            return;
        }
        mSelectedCardId = card.getCardId();
        selectCard();
    }

    private void selectCard() {
        mHandler.removeCallbacks(mSelectionRunnable);
        String selectedCardId = mSelectedCardId;
        if (mIsDismissed || selectedCardId == null) {
            return;
        }
        // 會員卡匣：滑動會員卡不影響預設付款卡，也沒有連接服務
        if (mMode == MODE_LOYALTY_ONLY) {
            return;
        }
        //Don't send a selection event for a loyalty card
        if(!mSelectedCardId.startsWith("LOY")) {
            mWalletClient.selectWalletCard(new SelectWalletCardRequest(selectedCardId));
        }else if(mDefaultCardId != null){
            mWalletClient.selectWalletCard(new SelectWalletCardRequest(mDefaultCardId));
        }
        // Re-selecting the card keeps the connection bound so we continue to get service events
        // even if the user keeps it open for a long time.
        mHandler.postDelayed(mSelectionRunnable, SELECTION_DELAY_MILLIS);
    }

    /**
     * Implements {@link WalletCardCarousel.OnSelectionListener}. Called when the user clicks on a
     * card.
     */
    @Override
    public void onCardClicked(WalletCardViewInfo card) {
        if (mIsDismissed) {
            return;
        }
        if(!card.onClick()){
            activityStarter.runAfterKeyguardDismissed(() -> {
                PendingIntent pendingIntent = card.getPendingIntent();
                startPendingIntent(pendingIntent);
            });
        }
    }

    private OverflowItem[] getOverflowItems() {
        // 拆分模式：標題列右側改放展開 / 收起箭頭，不顯示「⋮」選單
        if (isSplitMode()) {
            return new OverflowItem[]{};
        }
        CharSequence walletLabel = mWalletClient.getShortcutShortLabel();
        Intent walletIntent = mWalletClient.createWalletIntent();
        CharSequence settingsLabel = mPluginContext.getString(R.string.settings);
        Intent settingsIntent = new Intent(SETTINGS_ACTION).setPackage(SETTINGS_PKG);
        OverflowItem settings = new OverflowItem(settingsLabel, () -> startIntent(settingsIntent));
        if (!TextUtils.isEmpty(walletLabel) && walletIntent != null) {
            OverflowItem wallet = new OverflowItem(walletLabel, () -> startIntent(walletIntent));
            return new OverflowItem[]{wallet };//, settings};
        } else {
            return new OverflowItem[]{};//settings};
        }
    }

    private void showEmptyStateView() {
        Drawable logo = mWalletClient.getLogo();
        CharSequence logoContentDesc = mWalletClient.getServiceLabel();
        CharSequence label = mWalletClient.getShortcutLongLabel();
        Intent intent = mWalletClient.createWalletIntent();
        if (logo == null
                || TextUtils.isEmpty(logoContentDesc)
                || TextUtils.isEmpty(label)
                || intent == null) {
            Log.w(TAG, "QuickAccessWalletService manifest entry mis-configured");
            // Issue is not likely to be resolved until manifest entries are enabled.
            // Hide wallet feature until then.
            mWalletView.hide();
            mPrefs.edit().putInt(PREFS_WALLET_VIEW_HEIGHT, 0).apply();
        } else {
            mWalletView.showEmptyStateView(logo, logoContentDesc, label, v -> startIntent(intent));
        }
    }

    private void startIntent(Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(mSysuiContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        startPendingIntent(pendingIntent);
    }

    private void startPendingIntent(PendingIntent pendingIntent) {
        mPluginCallbacks.startPendingIntentDismissingKeyguard(pendingIntent);
        mPluginCallbacks.dismissGlobalActionsMenu();
        onDismissed();
    }

    /**
     * The total view height depends on whether cards are shown or not. Since it is not known at
     * construction time whether cards will be available, the best we can do is set the height to
     * whatever it was the last time. Setting the height correctly ahead of time is important
     * because Home Controls are shown below the wallet and may be displayed before card data is
     * loaded, causing the home controls to jump down when card data arrives.
     */
    private int getExpectedMinHeight() {
        int expectedHeight = mPrefs.getInt(prefKey(PREFS_WALLET_VIEW_HEIGHT), -1);
        if (expectedHeight == -1) {
            Resources res = mPluginContext.getResources();
            expectedHeight = res.getDimensionPixelSize(R.dimen.min_wallet_empty_height);
        }
        return expectedHeight;
    }

    private void removeMinHeightAndRecordHeightOnLayout() {
        mWalletView.setMinimumHeight(0);
        recordHeightOnNextLayout();
    }

    /** 下一次版面配置完成時記錄卡匣高度（拆分模式依模式分開記錄） */
    private void recordHeightOnNextLayout() {
        mWalletView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mWalletView.removeOnLayoutChangeListener(this);
                mPrefs.edit().putInt(prefKey(PREFS_WALLET_VIEW_HEIGHT), bottom - top).apply();
            }
        });
        mWalletView.requestLayout();
    }

    private class QAWalletCardViewInfo implements WalletCardViewInfo {

        private final WalletCard mWalletCard;
        private final Drawable mCardDrawable;
        private final Drawable mIconDrawable;

        /**
         * Constructor is called on background executor, so it is safe to load drawables
         * synchronously.
         */
        QAWalletCardViewInfo(WalletCard walletCard, WalletCardCustomizationProvider customization) {
            mWalletCard = walletCard;
            // 有自訂卡面就用自訂的，否則使用 Google Wallet 提供的原始卡面
            Drawable customDrawable = customization.getCustomCardDrawable(walletCard.getCardId());
            mCardDrawable = customDrawable != null
                    ? customDrawable
                    : mWalletCard.getCardImage().loadDrawable(mPluginContext);
            Icon icon = mWalletCard.getCardIcon();
            mIconDrawable = icon == null ? null : icon.loadDrawable(mPluginContext);
        }

        @Override
        public String getCardId() {
            return mWalletCard.getCardId();
        }

        @Override
        public Drawable getCardDrawable() {
            return mCardDrawable;
        }

        @Override
        public CharSequence getContentDescription() {
            return mWalletCard.getContentDescription();
        }

        @Override
        public Drawable getIcon() {
            // 信用卡匣標題只顯示卡名（與會員卡一致，不顯示前方小圖示）
            if (mMode == MODE_PAYMENT_ONLY) return null;
            return mIconDrawable;
        }

        @Override
        public CharSequence getText() {
            // 信用卡匣：標題顯示卡片名稱（Google 錢包提供的卡片描述），取代「Hold to reader」；
            // 描述為空時退回原本的 cardLabel
            if (mMode == MODE_PAYMENT_ONLY) {
                CharSequence name = mWalletCard.getContentDescription();
                if (!TextUtils.isEmpty(name)) {
                    String cardName = extractCardName(name.toString());
                    if (!cardName.isEmpty()) return cardName;
                }
            }
            return mWalletCard.getCardLabel();
        }

        @Override
        public float getTextSizeSp() {
            return mMode == MODE_PAYMENT_ONLY ? SPLIT_PAYMENT_TEXT_SIZE_SP : 14f;
        }

        @Override
        public PendingIntent getPendingIntent() {
            return mWalletCard.getPendingIntent();
        }
    }
}
