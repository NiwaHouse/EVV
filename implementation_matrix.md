# 実装マトリクス (Implementation Matrix)

| 機能モジュール | 対象ファイルパス | 主な役割 | 依存コンポーネント |
| :--- | :--- | :--- | :--- |
| **Main Activity** | `app/src/main/java/com/example/eev/MainActivity.kt` | エントリポイント、パーミッション管理、画面ルーティング | ViewModels, Compose UI |
| **Core Models** | `app/src/main/java/com/example/eev/core/model/*` | ドメインデータモデル (FileItem, SortOrder, Language, ViewMode, ThumbnailSize) | なし |
| **Core Storage** | `app/src/main/java/com/example/eev/core/storage/FileStorageManager.kt` | ストレージ・MediaStoreからのファイル一覧および画像数メタデータ取得 | Android SAF / MediaStore |
| **Core DataStore** | `app/src/main/java/com/example/eev/core/datastore/EEVDataStore.kt` | DataStoreを使用した設定（漫画モード、翻訳ON/OFF、言語、ソート順、サムネイルサイズ、情報表示）の永続化 | Jetpack DataStore |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/ExplorerScreen.kt` | ファイルエクスプローラUI、並び替えソートダイアログ・表示オプションダイアログ表示、戻るボタン、サムネサイズ・情報表示反映 | ExplorerViewModel, Coil |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/DisplayOptionDialog.kt` | サムネイルサイズ3段階（大・中・小）と情報表示ON/OFFの選択ダイアログ | ThumbnailSize |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/ExplorerViewModel.kt` | ディレクトリ表示・並び順管理・スクロール位置保存・フォルダ移動履歴管理・サムネイルキャッシュ連携・重複ロード抑止・メタデータのバッチ反映 | FileStorageManager, EEVDataStore, FolderThumbnailCache |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerScreen.kt` | フルスクリーン表示、タップ/長押し/トリプルタップジェスチャ処理 | ViewerViewModel, ViewerTripleTapDialog |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerTripleTapDialog.kt` | トリプルタップ時の操作選択モーダル（終了/次フォルダ/先頭画像） | なし |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/MangaCropHelper.kt` | 漫画モード（見開き右→左）のビットマップ分割・クロップ処理 | Android Graphics Bitmap |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerViewModel.kt` | 画像連続再生、漫画モード状態制御、翻訳トリガー、ビューワ独自ソート順の真実源管理（`loadImagesFromDirectory` 引数・フォルダまたぎ移動基準） | EEVDataStore, StorageManager |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerBottomSheet.kt` | 画像ビューワ下端ボトムシート設定UI（ビューワ独自ソート選択） | ViewerViewModel |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/OcrEngine.kt` | ML Kitによる画像内テキストおよび領域矩形(BoundingBox)検出 | Google ML Kit Text Recognition |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/TranslationEngine.kt` | ML Kitによるオンデバイス言語翻訳の実行 | Google ML Kit On-Device Translation |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/TranslationOverlay.kt` | 検出座標に合わせた翻訳テキストのComposeオーバーレイ描画 | Jetpack Compose Graphics |
| **Feature Thumbnail**| `app/src/main/java/com/example/eev/feature/thumbnail/ThumbnailGenerator.kt` | フォルダ先頭画像の特定・Coil用パス提供 | FileStorageManager |
| **Feature Thumbnail**| `app/src/main/java/com/example/eev/feature/thumbnail/FolderThumbnailCache.kt` | フォルダサムネイルの軽量画像生成・cacheDir保存・LRU容量管理 | Android Graphics Bitmap, cacheDir |
| **Plugin Core**     | `app/src/main/java/com/example/eev/plugin/EEVPlugin.kt` | プラグイン拡張用の基本インターフェース定義 | Core Models |
| **Plugin Registry** | `app/src/main/java/com/example/eev/plugin/PluginRegistry.kt` | プラグイン登録および一括初期化管理 | EEVPlugin |
| **Settings**        | `app/src/main/java/com/example/eev/settings/SettingsScreen.kt` | 全体設定画面UI（漫画モード、翻訳設定、言語） | SettingsViewModel |
| **Global Skill**    | `skills/gradle-japanese-path-build.md` | 日本語（マルチバイト）パス環境での Gradle ビルド失敗を回避する汎用手順（ASCII パスへコピー + Wrapper jar 直接起動） | Gradle Wrapper, Java |
