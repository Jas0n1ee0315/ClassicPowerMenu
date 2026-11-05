package com.android.systemui.util.extensions

/**
 *  裝置控制「狀態載入失敗時自動重新連線」的橋接介面。
 *
 *  Controls 模組無法直接使用 App 的設定與 Root 服務，因此由 App 端實作後透過 Koin 注入
 *  （與 [ControlsActivityStarter] 相同的做法）。
 */
interface ControlsProviderRecovery {

    /** 使用者是否開啟「狀態載入失敗時自動重新連線」 */
    val isEnabled: Boolean

    /**
     *  結束控制項 App（例如 Home Assistant）的所有程序，丟掉卡住的伺服器連線。
     *  於背景執行，完成後在主執行緒呼叫 [onComplete]（失敗也會呼叫，讓畫面繼續重新訂閱）。
     */
    fun killProviderProcesses(packageName: String, onComplete: () -> Unit)

}
