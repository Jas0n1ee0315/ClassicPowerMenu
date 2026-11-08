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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;

public interface WalletCardViewInfo {
    String getCardId();

    /**
     * Image of the card
     */
    @NonNull
    Drawable getCardDrawable();

    /**
     * Content description for the card
     */
    @Nullable
    CharSequence getContentDescription();

    /**
     * icon shown above card
     */
    @Nullable
    Drawable getIcon();

    /**
     * text shown above card
     */
    @NonNull
    CharSequence getText();

    @NonNull
    PendingIntent getPendingIntent();

    /**
     * Custom event handling for special cards
     * @return true if handled, false if it should be handled by the pending intent
     */
    default boolean onClick(){
        //Not handled by default
        return false;
    }

    /**
     * 卡片上方標題文字大小（sp）。預設 14sp（與 Wallet.TextAppearance 相同），會員卡可覆寫放大。
     */
    default float getTextSizeSp(){
        return 14f;
    }

    /**
     * 列表模式（2x2）顯示用的卡面。預設與 {@link #getCardDrawable()} 相同；
     * 會員卡即使開啟「直接顯示條碼」，列表模式仍覆寫為顯示 logo 卡面。
     */
    @NonNull
    default Drawable getListDrawable(){
        return getCardDrawable();
    }

    /**
     * 列表模式（2x2）卡片下方顯示的名稱。預設與 {@link #getText()} 相同；
     * 會員卡覆寫為只顯示主標題（不含副標題）。
     */
    @NonNull
    default CharSequence getListText(){
        return getText();
    }

}
