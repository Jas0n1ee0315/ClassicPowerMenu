![Classic Power Menu](https://i.imgur.com/DonYhMA.png)

Classic Power Menu is a Power Menu Replacement for Android 11+, with the main aim being restoring power menu options (Device Controls & Quick Access Wallet) on Android 12.

**Classic Power Menu Requires root access to function, it is not possible without root**

> [!NOTE]
> **This is an unofficial, modified fork** of [KieronQuinn/ClassicPowerMenu](https://github.com/KieronQuinn/ClassicPowerMenu) 1.9.3 (version `1.9.3-cardart`). It is not an official release and is not endorsed by Kieron Quinn.
>
> **Payment card artwork customization is based on [ZetaCard](https://github.com/hxfuxyy/ZetaCard) by hxfuxyy.** The ported code has been modified and is not endorsed by hxfuxyy. It stays under the ZetaCard Source License 2.0 ([license text](app/src/main/java/com/kieronquinn/app/classicpowermenu/components/quickaccesswallet/paymentcards/zetacard/LICENSE-ZetaCard.txt)).

## 下載 / Download

請到 [Releases 頁面](https://github.com/Jas0n1ee0315/ClassicPowerMenu/releases/latest) 下載最新的 APK（debug 簽章；若已安裝官方版本，需先解除安裝才能安裝）。

> [!IMPORTANT]
> 從 `v1.9.3-cardart-r4` 起改用新的 debug 簽章，與 r3 以前的版本**簽章不同**，App 內的更新提示無法直接覆蓋安裝。請先解除安裝舊版再安裝新版（App 內資料會被清除），或以 root 管理器（例如 KernelSU 的「Bypass package signature check」）暫時略過簽章檢查後覆蓋安裝以保留資料。

## 本 Fork 新增功能 / Fork Additions

### 全介面繁體中文 (Traditional Chinese UI)

- 所有介面文字（設定、初次設定精靈、電源選單、錢包、裝置控制、常見問題、對話框按鈕）皆翻譯為台灣用語的繁體中文。
- 翻譯寫在預設的 `values/strings.xml`，並移除其他語系的翻譯檔，因此**不論手機系統語言為何都會強制顯示繁體中文**。

### 付款卡片 (Payment Cards)

- **自訂卡面 (Settings → Quick Access Wallet → Payment Cards → Customize)**
  - 選擇任一張信用卡，從相簿挑一張圖片，再用 ZetaCard 的裁切編輯器裁成卡片比例，就會取代系統提供的卡面。
  - 圖片存在 App 內部空間（`filesDir/payment_card_art/`），可以隨時還原成原本的卡面。
  - 使用自訂卡面的卡片不會套用「鎖定時模糊卡號」，因為自訂圖片上本來就沒有卡號。
- **顯示 / 隱藏 (Payment Cards → Visibility)**
  - 和會員卡的管理頁一樣，每張付款卡片都可以單獨開關，關掉的卡片不會出現在電源選單。
- **記住上次選取的卡片 (Payment Cards → Visibility 下方，預設開啟)**
  - 開啟時維持原本行為：在電源選單滑到 / 點選某張卡會把它設為 Google Pay 的預設卡，下次開啟電源選單會自動選在這張卡（列表模式也會捲動到這張卡）。
  - 關閉後，每次開啟電源選單都從排序的第一張卡開始。

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
  - 點卡匣標題可展開 / 收起，收合狀態會記住，下次開啟沿用。
  - 收起時標題為「信用卡匣」/「會員卡匣」；展開時信用卡標題只顯示卡名（去掉「Pay with」與發卡組織 + 虛擬卡末碼，例如「永豐 Sport」，字級 18sp），會員卡標題維持「主標題 • 副標題」。
  - 滑動信用卡仍會切換預設付款卡；滑動會員卡不影響付款卡。沒有卡片的卡匣會整個隱藏。
  - 卡匣標題列右側原本的「⋮」選單改為「大卡片 / 列表」檢視切換按鈕（見下方）。
- **大卡片 / 列表模式**：兩個卡匣各自可切換，選擇會記住。
  - 大卡片模式：原本的單張大卡輪播。
  - 列表模式：兩列、可左右捲動的小卡片，卡面下方顯示卡名；卡片少到一個畫面放得下時會自動置中。標題固定顯示「信用卡匣」/「會員卡匣」。
  - 信用卡：點選即設為預設付款卡，選中的卡以藍色外框框住（卡片 + 卡名）。
  - 會員卡：卡面一律顯示 logo（即使開啟「直接顯示條碼」），只顯示主標題；點選開啟條碼預覽（可調亮度）。
- **IntegrityAPI 按鈕**（設定 → 電源選項 → 編輯電源選項，名稱為「Integrity 檢測」）
  - 點擊開啟 [Play Integrity API Checker](https://play.google.com/store/apps/details?id=gr.nikolasspyr.integritycheck)，由使用者自行檢測與判讀。
  - 長按開啟指定 App：在「設定 → 電源選項 → 長按 IntegrityAPI 按鈕開啟的 App」填入套件名稱（例如 KernelSU Next 的 `com.rifsxd.ksunext`），留空則長按不做任何事。
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
- **狀態載入失敗時自動重新連線**（設定 → 裝置控制，預設關閉）：開啟電源選單後 3 秒與 8 秒各檢查一次，若同一個 App（例如 Home Assistant）至少 2 個且超過一半的控制項顯示「無法載入狀態」，就透過 Root 結束該 App 的程序（非強制停止，排程與鬧鐘會保留）並重新載入，解決切換網路後 App 與伺服器連線卡住的問題；60 秒內最多觸發一次。

### 更新檢查 (Update Check)

- App 啟動時與每天中午會檢查**本 fork** 的 [Releases](https://github.com/Jas0n1ee0315/ClassicPowerMenu/releases)（原本檢查原作者的 repo），有新版時跳出提示，可直接下載並覆蓋安裝。
- 以 `app/build.gradle` 的 `releaseTag`（例如 `v1.9.3-cardart-r3`）與最新 Release 標籤比較：兩者都有 `-rN` 時，只有編號較大才視為更新，不會提示降版。
- **發布新版前，務必先把 `releaseTag` 改成新的 Release 標籤再建置 APK。**

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
