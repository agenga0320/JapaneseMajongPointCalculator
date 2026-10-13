# 日本麻雀点数計算機（MahjongScore）

日本麻將半莊計分 Android App（Kotlin + Jetpack Compose）。

## 功能

- **新半莊**：輸入 4 位玩家（依起家順），設定配給原點／返點／馬／切上滿貫／擊飛／西入
- **對局畫面**：顯示 東1局〜南4局、本場、供託、各家座風／親／點數／名次
- **和了輸入**：榮和／自摸、放銃者、和了者（支援雙響・三響）、翻數＋符數或役滿倍數、本局立直者 → 即時預覽點數變動
- **手牌輸入・自動判定役與符**：在和了畫面或點數速查按「輸入手牌」，從牌選擇盤點選手牌，副露（吃／碰／明槓／暗槓）另外輸入；設定場風、自風、立直・一發・海底等狀況與寶牌數 → 即時顯示役一覽、符的明細與點數，按「套用」填回
- **流局輸入**：聽牌者（自動計算不聽罰符 3000）、立直者、途中流局
- **自動推進**：親和了／親聽牌 → 連莊＋1本場；否則輪莊。南4局結束或擊飛時自動結束
- **復原上一局**、手動結束半莊
- **結算**：剩餘供託歸第1名，pt =（點數 − 返點）/1000 ＋ 馬 ＋ オカ（第1名）
- **歷史紀錄**：保存每場半莊的結算與逐局紀錄，可刪除
- **中文／日本語切換**：首頁右上角切換，設定會保存；首次啟動依手機語言決定。役名、對局紀錄也會跟著切換
- **點數速查**：任意翻符的榮和／自摸點數，以及 1〜4 翻的完整點數表

## 規則細節

- 雙響時，本場與供託由放銃者下家起最近的和了者取得（頭跳ね）
- 同分時，起家較近者名次較前
- 西入（可在新半莊設定開啟）：南4局結束時無人達返點 → 進入西場；西場中任何一局結束時有人達返點即結束，最多打到西4局（不北入）
- 南4局親連莊時會繼續，可隨時按「結束半莊」

## 資料保存

沒有後端。所有資料（進行中的半莊、歷史紀錄）以 JSON 存在手機本機的 SharedPreferences；解除安裝 App 會一併刪除。
網路只用於檢查更新（讀取 GitHub Releases 的最新版本），不會上傳任何資料。

## 下載

到 [Releases](https://github.com/agenga0320/JapaneseMajongPointCalculator/releases/latest) 下載最新的 APK 安裝。

## 更新檢查

- App 啟動時會查詢 GitHub 最新的 Release，若 tag（如 `v1.1.0`）比目前版本新，就顯示更新對話框
- 「前往下載」開啟 Release 頁面；「忽略此版本」之後不再提示該版本，直到更新的版本發布；「稍後」只關閉這一次
- 首頁底部的「檢查更新」可手動檢查（已忽略的版本也會顯示）

### 發布新版本

1. 修改 `app/build.gradle.kts` 的 `versionName`（與 `versionCode` + 1）
2. 建置正式簽名的 APK：`gradlew assembleRelease`（輸出 `app/build/outputs/apk/release/app-release.apk`）
3. 建立 tag 為 `v{versionName}` 的 GitHub Release 並附上 APK：
   `gh release create v1.1.0 app/build/outputs/apk/release/app-release.apk#JapaneseMahjongPointCalculator-v1.1.0.apk --title v1.1.0 --notes "更新內容"`

### 正式簽名金鑰

release 版以專案根目錄的 `keystore.properties`（不進版控）簽名：

```properties
storeFile=~/.keystores/japanese-mahjong-release.jks
storePassword=...
keyAlias=release
keyPassword=...
```

**金鑰檔與密碼務必備份。** 遺失後就無法再發布能覆蓋安裝的更新（使用者必須解除安裝舊版，資料會遺失）。
沒有 `keystore.properties` 時，`assembleRelease` 會產生未簽名的 APK。

## 建置

1. 用 Android Studio（Ladybug 以上）開啟本資料夾，等待 Gradle Sync 完成
2. 連接手機或開啟模擬器，按 Run ▶

> 專案路徑含中文（使用者名稱）時，`gradle.properties` 已加入 `android.overridePathCheck=true`。
> 若仍建置失敗，請將資料夾移到純英文路徑（例如 `C:\dev\MahjongScore`）再開啟。

分數計算的單元測試在 `app/src/test/.../ScoringTest.kt`。
