/*
 * 本檔案移植自 ZetaCard（https://github.com/hxfuxyy/ZetaCard）by hxfuxyy，
 * 經由 ZetaCardNext 修改版取得，並為 ClassicPowerMenu 調整套件名稱。
 * 此為修改版本，非 ZetaCard 官方發行，亦未經 hxfuxyy 背書。
 * 原始授權：ZetaCard Source License 2.0（全文見本資料夾 LICENSE-ZetaCard.txt）。
 */

package com.kieronquinn.app.classicpowermenu.components.quickaccesswallet.paymentcards.zetacard;

public final class ImageLimits {
    public static final int OUTPUT_WIDTH = 1400;
    public static final int OUTPUT_HEIGHT = 880;
    public static final int MAX_EDIT_SIDE = 3000;
    public static final long MAX_SOURCE_PIXELS = 100_000_000L;
    public static final int MAX_STOCK_SIDE = 4096;

    private ImageLimits() { }

    public static boolean isDecodable(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_SOURCE_PIXELS;
    }

    public static int sampleSize(int width, int height, int maxSide) {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1;
        int largest = Math.max(width, height);
        int sample = 1;
        while (largest / sample > maxSide && sample < (1 << 12)) sample <<= 1;
        return sample;
    }
}
