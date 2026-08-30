# 実装マトリクス (Implementation Matrix)

| 機能モジュール | 対象ファイルパス | 主な役割 | 依存コンポーネント |
| :--- | :--- | :--- | :--- |
| **Main Activity** | `app/src/main/java/com/example/eev/MainActivity.kt` | エントリポイント、パーミッション管理、画面ルーティング | ViewModels, Compose UI |
| **Core Models** | `app/src/main/java/com/example/eev/core/model/*` | ドメインデータモデル (FileItem, SortOrder, Language) | なし |
| **Core Storage** | `app/src/main/java/com/example/eev/core/storage/FileStorageManager.kt` | ストレージ・MediaStoreからのファイル一覧および画像数メタデータ取得 | Android SAF / MediaStore |
| **Core DataStore** | `app/src/main/java/com/example/eev/core/datastore/EEVDataStore.kt` | DataStoreを使用した設定（漫画モード、翻訳ON/OFF、言語、ソート順）の永続化 | Jetpack DataStore |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/ExplorerScreen.kt` | ファイルエクスプローラUI、並び替えソートダイアログ表示 | ExplorerViewModel, Coil |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/ExplorerViewModel.kt` | ディレクトリ表示・並び順管理ロジック | FileStorageManager, EEVDataStore |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerScreen.kt` | フルスクリーン表示、タップ/長押し/ダブルタップジェスチャ処理 | ViewerViewModel |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/MangaCropHelper.kt` | 漫画モード（見開き右→左）のビットマップ分割・クロップ処理 | Android Graphics Bitmap |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerViewModel.kt` | 画像連続再生、漫画モード状態制御、翻訳トリガー | EEVDataStore, StorageManager |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerBottomSheet.kt` | 画像ビューワ下端ボトムシート設定UI | ViewerViewModel |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/OcrEngine.kt` | ML Kitによる画像内テキストおよび領域矩形(BoundingBox)検出 | Google ML Kit Text Recognition |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/TranslationEngine.kt` | ML Kitによるオンデバイス言語翻訳の実行 | Google ML Kit On-Device Translation |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/TranslationOverlay.kt` | 検出座標に合わせた翻訳テキストのComposeオーバーレイ描画 | Jetpack Compose Graphics |
| **Feature Thumbnail**| `app/src/main/java/com/example/eev/feature/thumbnail/ThumbnailGenerator.kt` | フォルダ先頭画像の特定・Coil用パス提供 | FileStorageManager |
| **Plugin Core**     | `app/src/main/java/com/example/eev/plugin/EEVPlugin.kt` | プラグイン拡張用の基本インターフェース定義 | Core Models |
| **Plugin Registry** | `app/src/main/java/com/example/eev/plugin/PluginRegistry.kt` | プラグイン登録および一括初期化管理 | EEVPlugin |
| **Settings**        | `app/src/main/java/com/example/eev/settings/SettingsScreen.kt` | 全体設定画面UI（漫画モード、翻訳設定、言語） | SettingsViewModel |
