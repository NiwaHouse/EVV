# 実装マトリクス (Implementation Matrix)

| 機能モジュール | 対象ファイルパス | 主な役割 | 依存コンポーネント |
| :--- | :--- | :--- | :--- |
| **Main Activity** | `app/src/main/java/com/example/eev/MainActivity.kt` | エントリポイント、パーミッション管理、画面ルーティング | ViewModels, Compose UI |
| **Core Models** | `app/src/main/java/com/example/eev/core/model/*` | ドメインデータモデル (FileItem, SortOrder, Language, ViewMode, ThumbnailSize, AudibleMode) | なし |
| **Core Storage** | `app/src/main/java/com/example/eev/core/storage/FileStorageManager.kt` | ストレージ・MediaStoreからのファイル一覧および画像数メタデータ取得 | Android SAF / MediaStore |
| **Core DataStore** | `app/src/main/java/com/example/eev/core/datastore/EEVDataStore.kt` | DataStoreを使用した設定（漫画モード、翻訳ON/OFF、言語、ソート順、サムネイルサイズ、情報表示、オーディブル再生モード/無音時待機秒数/読み上げ速度）の永続化 | Jetpack DataStore |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/ExplorerScreen.kt` | ファイルエクスプローラUI、並び替えソートダイアログ・表示オプションダイアログ表示、戻るボタン、サムネサイズ・情報表示反映 | ExplorerViewModel, Coil |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/DisplayOptionDialog.kt` | サムネイルサイズ3段階（大・中・小）と情報表示ON/OFFの選択ダイアログ | ThumbnailSize |
| **Feature Explorer**| `app/src/main/java/com/example/eev/feature/explorer/ExplorerViewModel.kt` | ディレクトリ表示・並び順管理・スクロール位置保存・フォルダ移動履歴管理・サムネイルキャッシュ連携・重複ロード抑止・メタデータのバッチ反映 | FileStorageManager, EEVDataStore, FolderThumbnailCache |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerScreen.kt` | フルスクリーン表示、タップ/長押し/トリプルタップジェスチャ処理、オーディブル再生バッジ・読み上げ中ハイライト表示 | ViewerViewModel, ViewerTripleTapDialog, AudibleHighlightOverlay |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerTripleTapDialog.kt` | トリプルタップ時の操作選択モーダル（終了/次フォルダ/先頭画像） | なし |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/MangaCropHelper.kt` | 漫画モード（見開き右→左）のビットマップ分割・クロップ処理 | Android Graphics Bitmap |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerViewModel.kt` | 画像連続再生、漫画モード状態制御、翻訳トリガー、ビューワ独自ソート順の真実源管理（`loadImagesFromDirectory` 引数・フォルダまたぎ移動基準）、オーディブル再生の公開API（モード/待機秒数/速度変更、再生トグル、DL確認、ライフサイクル連動）と `AudibleSessionController` への委譲 | EEVDataStore, StorageManager, OcrEngine, TranslationEngine, TtsEngine, AudibleSessionController |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/ViewerBottomSheet.kt` | 画像ビューワ下端ボトムシート設定UI（ビューワ独自ソート選択、オーディブル2トグル・無音時待機秒数/読み上げ速度スライダー） | ViewerViewModel |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/OcrEngine.kt` | ML Kitによる画像内テキストおよび領域矩形(BoundingBox)検出 | Google ML Kit Text Recognition |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/TranslationEngine.kt` | ML Kitによるオンデバイス言語翻訳の実行 | Google ML Kit On-Device Translation |
| **Feature Translation** | `app/src/main/java/com/example/eev/feature/translation/TranslationOverlay.kt` | 検出座標に合わせた翻訳テキストのComposeオーバーレイ描画 | Jetpack Compose Graphics |
| **Feature Thumbnail**| `app/src/main/java/com/example/eev/feature/thumbnail/ThumbnailGenerator.kt` | フォルダ先頭画像の特定・Coil用パス提供 | FileStorageManager |
| **Feature Thumbnail**| `app/src/main/java/com/example/eev/feature/thumbnail/FolderThumbnailCache.kt` | フォルダサムネイルの軽量画像生成・cacheDir保存・LRU容量管理 | Android Graphics Bitmap, cacheDir |
| **Plugin Core**     | `app/src/main/java/com/example/eev/plugin/EEVPlugin.kt` | プラグイン拡張用の基本インターフェース定義 | Core Models |
| **Plugin Registry** | `app/src/main/java/com/example/eev/plugin/PluginRegistry.kt` | プラグイン登録および一括初期化管理 | EEVPlugin |
| **Settings**        | `app/src/main/java/com/example/eev/settings/SettingsScreen.kt` | 全体設定画面UI（漫画モード、翻訳設定、言語） | SettingsViewModel |
| **Core Models** | `app/src/main/java/com/example/eev/core/model/AudibleMode.kt` | オーディブル再生モードの永続化用 enum（`OFF` / `READ_RAW` / `READ_TRANSLATED`） | なし |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/AudiblePlaybackState.kt` | オーディブル再生の進行状態（読み上げ中項目インデックス、漫画モード左右、実行フラグ）の保持 | ViewerViewModel |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/AudiblePrefetchCache.kt` | 次画像の OCR・翻訳結果の先行取得とキャッシュ保持 | OcrEngine, TranslationEngine |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/AudibleHighlightOverlay.kt` | 読み上げ中の検出領域を枠線でハイライト描画 | Jetpack Compose Graphics |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/AudibleScriptProvider.kt` | 画像（漫画モード時はクロップ画像）単位の読み上げ台本生成。OCR→（必要時）翻訳→`OcrGroupMerger` 結合→漫画読み順ソートを行い、キャッシュ優先で提供する。次画像の先読み生成も担う | OcrEngine, TranslationEngine, OcrGroupMerger, AudiblePrefetchCache |
| **Feature Viewer**  | `app/src/main/java/com/example/eev/feature/viewer/AudibleSessionController.kt` | オーディブル再生状態機械の本体。セッションIDによる世代管理、字幕1件ずつの TTS 投入と読み上げ完了待ち、無音時待機、次項目/次画像/次フォルダへの自動送り、先読み起動、バックグラウンド移行時の停止と復帰を司る。UI状態は `stateProvider`/`stateUpdater` 経由で `ViewerViewModel` の `MutableStateFlow` を唯一の真実源として更新する | AudibleScriptProvider, TtsEngine, TranslationEngine, ViewerUiState |
| **Global Skill**    | `skills/gradle-japanese-path-build.md` | 日本語（マルチバイト）パス環境での Gradle ビルド失敗を回避する汎用手順（ASCII パスへコピー + Wrapper jar 直接起動） | Gradle Wrapper, Java |
