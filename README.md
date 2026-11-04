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
- **條碼預覽**
  - 卡片底色一律是純白、文字是黑色，掃描器比較容易讀取。
  - 「在錢包中開啟」上方新增 Material 3 Expressive 風格的螢幕亮度調整條。它只調整預覽視窗本身的亮度，不會動到系統亮度設定，關閉預覽後螢幕就回到原本的亮度。

### 電源選單 (Power Menu)

- 會員卡標題字體放大、移除前面的條碼小圖示，原本的「Tap to show」改成顯示「主標題 • 副標題」。
- 卡片縮小 10%，改用一般捲動（不再一次只能滑一張），可以快速瀏覽多張卡片，滑動動畫也加快。
- 以下按鈕文字固定顯示繁體中文，不隨手機語言改變：
  - 關機
  - 重啟
  - 重啟SysUI
  - 在錢包中開啟

### 裝置控制 (Device Controls)

- 縮小卡片和裝置控制之間的間距。
- 降低按鈕高度（74dp），按鈕內容上下置中。
- 裝置名稱改到上方、和圖示同一列；狀態（例如「關閉」）移到下方。
- 圖示縮小到 22dp，避免在較大字體設定下被切到。

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
