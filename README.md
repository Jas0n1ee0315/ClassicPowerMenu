![Classic Power Menu](https://i.imgur.com/DonYhMA.png)

Classic Power Menu is a Power Menu Replacement for Android 11+, with the main aim being restoring power menu options (Device Controls & Quick Access Wallet) on Android 12.

**Classic Power Menu Requires root access to function, it is not possible without root**

> [!NOTE]
> **This is an unofficial, modified fork** of [KieronQuinn/ClassicPowerMenu](https://github.com/KieronQuinn/ClassicPowerMenu) 1.9.3 (version `1.9.3-cardart`). It is not an official release and is not endorsed by Kieron Quinn.
>
> **Payment card artwork customization is based on [ZetaCard](https://github.com/hxfuxyy/ZetaCard) by hxfuxyy.** The ported code has been modified and is not endorsed by hxfuxyy. It stays under the ZetaCard Source License 2.0 ([license text](app/src/main/java/com/kieronquinn/app/classicpowermenu/components/quickaccesswallet/paymentcards/zetacard/LICENSE-ZetaCard.txt)).

## 本 Fork 新增功能 / Fork Additions

### 付款卡片 (Payment Cards)

- **自訂卡面 (Settings → Quick Access Wallet → Payment Cards → Customize)**
  - 選擇任一張信用卡，從相簿挑一張圖片，再用 ZetaCard 的裁切編輯器裁成卡片比例，就會取代系統提供的卡面。
  - 圖片存在 App 內部空間（`filesDir/payment_card_art/`），可以隨時還原成原本的卡面。
  - 使用自訂卡面的卡片不會套用「鎖定時模糊卡號」，因為自訂圖片上本來就沒有卡號。
- **顯示 / 隱藏 (Payment Cards → Visibility)**
  - 和會員卡的管理頁一樣，每張付款卡片都可以單獨開關，關掉的卡片不會出現在電源選單。

### 會員卡 (Loyalty Cards)

- **編輯標題 (Manage Account & Loyalty Cards)**
  - 每張卡片的顯示 / 隱藏開關右側多了一個鉛筆按鈕，可以修改主標題（發卡商名稱）和副標題。
  - 修改後永久保存在獨立的設定檔，從 Google 伺服器同步新資料也不會被覆蓋；清空欄位或按「還原」就回到原本的名稱。
- **手動新增會員卡 (Manage Account & Loyalty Cards → 標題列「+」)**
  - 輸入會員卡名稱、副標題（選填）、會員卡號與條碼類型，視窗內即時預覽條碼；卡號不符合所選格式時無法存檔。
  - 支援 Code 128、Code 39、QR Code、EAN-13、EAN-8、UPC-A、UPC-E、ITF-14、Codabar、PDF417、Aztec、Data Matrix。
  - 存在獨立的加密設定檔，與 Google 同步的資料分開：重新整理、Google 端刪卡、登出都不會移除。
  - 與 Google 會員卡一樣可以顯示 / 隱藏、排序；按鉛筆可修改名稱、卡號與條碼類型。
  - 只有手動新增的卡片可以左右滑動刪除，刪除後可按「Undo」復原。
  - 電源選單中點手動卡片一律開啟條碼預覽（不受「Show Loyalty Card Preview」影響），預覽畫面不顯示「在錢包中開啟」。
- **條碼預覽**
  - 卡片底色一律是純白、文字是黑色，掃描器比較容易讀取。
  - 「在錢包中開啟」上方新增 Material 3 Expressive 風格的螢幕亮度調整條。它只調整預覽視窗本身的亮度，不會動到系統亮度設定，關閉預覽後螢幕就回到原本的亮度。
- **直接顯示條碼 (Display Card Code Directly，實驗性)**
  - 位於「Show Loyalty Card Preview」下方，預設關閉。
  - 開啟後，電源選單中的會員卡卡面改為白底條碼 / QR 碼（一維條碼下方附號碼），不調整螢幕亮度；點卡片仍會開啟預覽或 Google 錢包。
  - 若關閉「Allow Loyalty Card access while locked」，鎖定時仍顯示原本的 logo 卡面；Content Creator Mode 下顯示假的 QR 碼；條碼格式不支援時自動退回原本卡面。

### 電源選單 (Power Menu)

- 卡片拆成兩個獨立區塊，順序為：電源選項 → 信用卡匣 → 會員卡匣 → 裝置控制。
  - 兩個卡匣標題列右側都有展開 / 收起箭頭（點標題也可切換），收合狀態會記住，下次開啟沿用。
  - 收起時標題為「信用卡匣」/「會員卡匣」；展開時信用卡標題只顯示卡名（去掉「Pay with」與發卡組織 + 虛擬卡末碼，例如「永豐 Sport」，字級 18sp），會員卡標題維持「主標題 • 副標題」。
  - 滑動信用卡仍會切換預設付款卡；滑動會員卡不影響付款卡。沒有卡片的卡匣會整個隱藏。
  - 卡匣標題列原本的「⋮」選單改為箭頭。
- 會員卡標題字體放大、移除前面的條碼小圖示，原本的「Tap to show」改成顯示「主標題 • 副標題」。
- 卡片縮小 10%，改用一般捲動（不再一次只能滑一張），可以快速瀏覽多張卡片，滑動動畫也加快。
- 以下按鈕文字固定顯示繁體中文，不隨手機語言改變：
  - 關機
  - 重啟
  - 重啟SysUI
  - 在錢包中開啟

### 裝置控制 (Device Controls)

- 縮小卡片和裝置控制之間的間距（卡片底部到下一個標題約 25dp，與會員卡匣上方間距一致）。
- 降低按鈕高度（69dp），按鈕內容上下置中。
- 裝置名稱改到上方、狀態（例如「關閉」）移到下方，兩者左緣對齊。
- 圖示在按鈕內垂直置中（23.1dp），和右方文字間隔 10.5dp，視覺上與按鈕左框到圖示的距離一致。
- 電源選單不顯示區域（副標題，例如「主臥」）；新增裝置頁仍會顯示，方便辨識。
- 沒有狀態文字的按鈕（例如場景）會隱藏狀態列，裝置名稱單獨垂直置中。
- 新增 / 排序裝置頁：勾選框固定靠右、上下置中，空白的狀態與副標題不佔高度。

### 建置 (Build)

- 新增 Jetpack Compose（Kotlin Compose plugin 2.3.20、Compose BOM 2026.09.00），用於裁切編輯器和亮度調整條。
- `compileSdk` 升到 37。
- 用 `jvmTarget = 11` 取代 `jvmToolchain(11)`，不必另外安裝 JDK 11，Android Studio 內建的 JBR 就能建置：

```bash
./gradlew :app:assembleDebug
```

---

**[Please read the FAQ before downloading or creating an issue](https://github.com/KieronQuinn/ClassicPowerMenu/blob/main/app/src/main/assets/faq.md)**

[![Crowdin](https://badges.crowdin.net/classicpowermenu/localized.svg)](https://crowdin.com/project/classicpowermenu)

## Features

- Up to 10 power options in the Power Menu, including the non-stock options of Screenshot, Reboot Recovery and Reboot Bootloader
    - Fully customisable, including rearranging and hiding buttons you don't use

- Quick Access Wallet in the Power Menu, accessible while locked or unlocked
    - Optional blurring of card numbers while locked
    - Optional showing of loyalty cards, restoring a previous feature from Android 10 which was removed. Loyalty cards' codes can be shown while locked and without needing to open Google Wallet, if you wish.
    
- Device Controls in the Power Menu, accessible while locked or unlocked
    - Optional ability to interact with controls while locked, without requiring authentication
    
- Material You inspired theme, using Monet colours and background blur on the Power Menu

- Xposed is not required, interception of the power button is done via Accessibility Service, with only normal root required
    - An optional Xposed module is included, which can react quicker than the Accessibility Service

## Screenshots

![Classic Power Menu](https://i.imgur.com/rhm4bqvl.png) ![Settings](https://i.imgur.com/rAG0htEl.png)

![Loyalty Cards in Pay](https://i.imgur.com/eKOI84El.png) ![Showing a Card](https://i.imgur.com/PR6uqUnl.png)

## Download

[Download the latest release from the Releases page](https://github.com/KieronQuinn/ClassicPowerMenu/releases)

### Content Creators

If you are making a video or article about Classic Power Menu, you will want to hide your card numbers. Enable the developer options in Classic Power Menu by triple tapping the "About" option, and enable Content Creator Mode. This will always blur your card numbers and will show a fake QR code for loyalty cards, keeping your data safe.
