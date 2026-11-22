# 実装マトリクス (Implementation Matrix - EEVN)

| 機能モジュール | 対象ファイルパス | 主な役割 | 依存コンポーネント |
| :--- | :--- | :--- | :--- |
| **Native Module** | `modules/eev-storage/expo-module.config.json` | Expo Autolinking 設定（Android モジュール宣言） | Expo Modules Core |
| **Native Module** | `modules/eev-storage/android/build.gradle` | Kotlin モジュールの Android ビルド構成 | Expo Modules Core, Kotlin |
| **Native Module** | `modules/eev-storage/android/src/main/java/expo/modules/eevstorage/EevStorageModule.kt` | 極薄 Kotlin DSL 公開モジュール（全ファイルアクセス権確認/要求、`EevStorageOps` への委譲） | Expo Modules Core, `EevStorageOps` |
| **Native Module** | `modules/eev-storage/android/src/main/java/expo/modules/eevstorage/EevStorageOps.kt` | ストレージ走査、メタデータ算出、最大辺256px永続JPEGサムネ生成、ファイル/フォルダ再帰削除の実処理 | Android SDK, `android.graphics.BitmapFactory` |
| **Native Bridge** | `modules/eev-storage/index.ts` | JS/TS からの `EevStorage` 呼び出しラッパー（`try-catch` フォールバック付き） | `expo-modules-core` |
| **Core Models** | `src/core/models/types.ts` | 共通ドメインモデル・列挙型（`SortOrder`, `ViewMode`, `ThumbnailSize`, `FileItem`, `FavoriteFolder`, `BookshelfCategory`, `Screen`） | なし |
| **Core Storage** | `src/core/storage/AppSettingsStore.ts` | AsyncStorage によるアプリ設定・お気に入りフォルダパスの永続読み書きと購読機構 | `@react-native-async-storage/async-storage`, `types.ts` |
| **Core Storage** | `src/core/storage/FolderMetadataDb.ts` | `folder_cache.db`（`folder_cache_items`）による通常フォルダメタデータの2段階キャッシュ読み書き・削除 | `expo-sqlite`, `types.ts` |
| **Core Storage** | `src/core/storage/SortUtils.ts` | フォルダ・ファイル個別ソート、自然順文字列比較、およびカスタム昇順/降順ソート | `types.ts` |
| **Core UI** | `src/core/ui/AppTheme.ts` | カラーパレット・定数および共有ボタンスタイル補助 | なし |
| **Core UI** | `src/core/ui/AppBackHandler.ts` | React Native `BackHandler` による全画面システムバック制御 | `react-native`, `types.ts` |
| **Feature Bookshelf** | `src/features/bookshelf/BookshelfDb.ts` | `bookshelf.db`（フォルダキャッシュ、カスタム並び順、カテゴリ定義、複数カテゴリ紐付け）の SQLite CRUD | `expo-sqlite`, `types.ts` |
| **Feature Bookshelf** | `src/features/bookshelf/useBookshelfViewModel.ts` | 本棚の2段階即時ロード、カテゴリCRUD・複数選択AND絞り込み・長押しカテゴリ割当、カスタム順並べ替え・永続化 | `BookshelfDb`, `AppSettingsStore`, `EevStorage`, `SortUtils` |
| **Feature Bookshelf** | `src/features/bookshelf/BookshelfCategoryDialogs.tsx` | ヘッダー絞り込みチップバー、カテゴリ登録管理ダイアログ、フォルダ長押し統合ダイアログ | `types.ts` |
| **Feature Bookshelf** | `src/features/bookshelf/BookshelfItemViews.tsx` | 本棚フォルダのリスト行・グリッドカード（永続サムネ、`.trans_image.db` バッジ、カテゴリバッジ、並べ替え/解除操作） | `expo-image`, `types.ts` |
| **Feature Bookshelf** | `src/features/bookshelf/BookshelfScreen.tsx` | 本棚画面UI（ヘッダー、検索、カテゴリ絞り込みバー、リスト/グリッド、各種ダイアログ統合） | `useBookshelfViewModel`, `BookshelfCategoryDialogs`, `BookshelfItemViews`, `ExplorerDialogs` |
| **Feature Explorer** | `src/features/explorer/useExplorerViewModel.ts` | フォルダ遷移履歴、通常フォルダ永続サムネ＋`folder_cache.db` 2段階ロード、本棚未登録フォルダ抽出（サブフォルダ・本棚子孫保護）＆一括削除 | `EevStorage`, `FolderMetadataDb`, `AppSettingsStore`, `SortUtils` |
| **Feature Explorer** | `src/features/explorer/ExplorerDialogs.tsx` | ソート設定・表示オプション・長押し操作メニューダイアログ群 | `types.ts`, `DeleteNonFavoriteFoldersDialog.tsx` |
| **Feature Explorer** | `src/features/explorer/DeleteNonFavoriteFoldersDialog.tsx` | 個別削除確認および本棚未登録フォルダ一括削除確認ダイアログ（件数・対象一覧表示） | `types.ts` |
| **Feature Explorer** | `src/features/explorer/ExplorerItemViews.tsx` | パンくずバー・リスト行・グリッドセル（永続サムネ優先参照、`.trans_image.db` バッジ表示） | `expo-image`, `types.ts` |
| **Feature Explorer** | `src/features/explorer/ExplorerScreen.tsx` | エクスプローラ画面UI（権限バナー、検索、本棚へ、未登録フォルダ一括削除、表示切替・ソート・表示オプション・設定） | `useExplorerViewModel`, `ExplorerDialogs`, `ExplorerItemViews` |
| **Feature Viewer** | `src/features/viewer/ViewerDirectoryNavigator.ts` | 次/前フォルダ移動探索（通常時は親直下非ループ、本棚起動時はカテゴリ絞り込み対象フォルダ群をカスタム昇順/降順含むソート順で循環移動） | `EevStorage`, `SortUtils`, `types.ts` |
| **Feature Viewer** | `src/features/viewer/useViewerViewModel.ts` | ビューワ表示画像一覧、ページ送り、漫画モード左右クロップ状態、独自ソート順、お気に入りトグル、次/前フォルダ移動 | `EevStorage`, `ViewerDirectoryNavigator`, `AppSettingsStore`, `SortUtils` |
| **Feature Viewer** | `src/features/viewer/ViewerDialogs.tsx` | 画像ビューワ下端ボトムシート設定（カスタム昇順/降順含む）＆トリプルタップメニューダイアログ | `types.ts` |
| **Feature Viewer** | `src/features/viewer/ViewerScreen.tsx` | フルスクリーンビューワUI（漫画見開き左右クロップ描画、タップ送り・トリプルタップ判定、お気に入り星、常時点灯） | `expo-image`, `expo-keep-awake`, `useViewerViewModel`, `ViewerDialogs` |
| **Feature Settings** | `src/features/settings/SettingsScreen.tsx` | 全体設定画面UI（漫画モード、隠しファイル表示、通常フォルダサムネイルキャッシュクリア） | `AppSettingsStore`, `FolderMetadataDb`, `EevStorage` |
| **App Entry** | `App.tsx` | ルートコンポーネント、設定/状態フック統合、画面切り替え、システム戻る制御統合 | 全 Feature Screens, `AppSettingsStore`, `AppBackHandler` |
