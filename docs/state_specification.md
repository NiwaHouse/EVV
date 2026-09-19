# グローバル状態仕様書 (State Specification)

本ドキュメントは、EEV アプリにおける永続化状態およびインメモリ画面状態の仕様を定義する。
記載内容は実装コード（[`EEVDataStore.kt`](../app/src/main/java/com/example/eev/core/datastore/EEVDataStore.kt)、[`ExplorerViewModel.kt`](../app/src/main/java/com/example/eev/feature/explorer/ExplorerViewModel.kt)、[`ViewerViewModel.kt`](../app/src/main/java/com/example/eev/feature/viewer/ViewerViewModel.kt) 等）と一致させること。

## 1. 永続化状態 (Persistent State - DataStore)

`EEVDataStore` により Jetpack DataStore (Preferences) で永続化される設定値。
DataStore のファイル名は `eev_settings`。

| 状態識別子 (State ID) | データ型 | デフォルト値 | 説明・影響範囲 |
| :--- | :--- | :--- | :--- |
| `is_manga_mode_enabled` | `Boolean` | `false` | 漫画モード（見開き右→左表示）のON/OFF。画像ビューワの表示領域制御に影響。 |
| `is_translation_enabled` | `Boolean` | `false` | 画像内翻訳機能のON/OFF。 |
| `source_language` | `String` | `"ja"` | 翻訳元言語コード。`Language` enum の `code` 値（`"ja"`, `"en"`, `"zh"`, `"ko"`, `"es"`, `"fr"`, `"de"`）。 |
| `target_language` | `String` | `"en"` | 翻訳先言語コード。`Language` enum の `code` 値（`"ja"`, `"en"`, `"zh"`, `"ko"`, `"es"`, `"fr"`, `"de"`）。 |
| `sort_order` | `String` | `"NAME_ASC"` | エクスプローラのソート順。`SortOrder` enum の `name` 値（`NAME_ASC`, `NAME_DESC`, `DATE_DESC`, `DATE_ASC`, `IMAGE_COUNT_DESC`, `IMAGE_COUNT_ASC`）。 |
| `view_mode` | `String` | `"LIST"` | エクスプローラの表示モード。`ViewMode` enum の `name` 値（`LIST`, `GRID`）。 |
| `show_hidden_files` | `Boolean` | `false` | 隠しファイル（ドットファイル）を一覧に表示するか。エクスプローラおよびビューワの一覧取得に影響。 |
| `text_color_hex` | `String` | `"#FFFFFF"` | 翻訳オーバーレイのテキスト色（16進カラーコード）。 |
| `bg_color_hex` | `String` | `"#CC000000"` | 翻訳オーバーレイの背景色（16進カラーコード、ARGB）。 |
| `viewer_sort_order` | `String` | `"NAME_ASC"` | 画像ビューワ独自のソート順。`SortOrder` enum の `name` 値。エクスプローラのソート順とは独立し、フォルダ内画像の表示順および「次のフォルダ選択基準」に適用される。 |
| `explorer_scroll_positions` | `String` | `""` | フォルダパスをキーとしたエクスプローラのスクロール位置（先頭表示インデックス）マップ。`パス=インデックス` を改行で連結した文字列で保存。 |
| `thumbnail_size` | `String` | `"MEDIUM"` | エクスプローラのサムネイル表示サイズ。`ThumbnailSize` enum の `name` 値（`SMALL`, `MEDIUM`, `LARGE`）。 |
| `show_item_info` | `Boolean` | `false` | エクスプローラで日付・枚数などの付加情報を表示するか。 |
| `audible_mode` | `String` | `"OFF"` | オーディブル再生モード。`AudibleMode` enum の `name` 値（`OFF`, `READ_RAW`, `READ_TRANSLATED`）。ビューワ起動時の自動再生可否に影響。 |
| `audible_silent_wait_seconds` | `Float` | `3.0f` | テキスト未検出画像を表示し続ける秒数。UIは1〜10秒・0.5秒刻みのスライダー。 |
| `audible_speech_rate` | `Float` | `1.0f` | TTS の読み上げ速度（0.5〜2.0倍）。 |

### 1.1 読み書きAPI

* 読み込み: 各設定値は `Flow<T>` として公開され、`ViewModel` 側で `collectLatest` により購読される。
* 書き込み: `setMangaModeEnabled` / `setTranslationEnabled` / `setSourceLanguage` / `setTargetLanguage` / `setSortOrder` / `setViewMode` / `setShowHiddenFiles` / `setTextColorHex` / `setBackgroundColorHex` / `setViewerSortOrder` / `setThumbnailSize` / `setShowItemInfo` / `setScrollPosition` / `setAudibleMode` / `setAudibleSilentWaitSeconds` / `setAudibleSpeechRate` の各 suspend 関数により更新する。
* エラー時: DataStore 読み書き失敗時はデフォルト値を返却して保護する。
* `explorer_scroll_positions` は `explorerScrollPositions: Flow<Map<String, Int>>` として公開し、`ExplorerViewModel.scrollPositions`（`StateFlow`）経由で画面に配信する。パスに `=` または改行を含む場合は保存をスキップする。

## 2. インメモリ画面状態 (In-Memory Screen State)

各画面の `ViewModel` が保持する `StateFlow` ベースの UI State。

### 2.1 エクスプローラ画面状態 (`ExplorerUiState`)

`ExplorerViewModel` が保持する。

| フィールド | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `currentDirectory` | `File` | `Environment.getExternalStorageDirectory()` | 現在表示中のフォルダパス。 |
| `fileItems` | `List<FileItem>` | `emptyList()` | ソート済みのファイル・フォルダ一覧。 |
| `sortOrder` | `SortOrder` | `SortOrder.NAME_ASC` | 現在適用中のソート順。 |
| `viewMode` | `ViewMode` | `ViewMode.LIST` | 現在の表示モード（リスト / グリッド）。 |
| `showHiddenFiles` | `Boolean` | `false` | 隠しファイルを表示するか。 |
| `thumbnailSize` | `ThumbnailSize` | `ThumbnailSize.MEDIUM` | サムネイル表示サイズ（大・中・小）。グリッド列数にも影響する。 |
| `showItemInfo` | `Boolean` | `false` | 日付・枚数などの付加情報を表示するか。 |
| `canNavigateBack` | `Boolean` | `false` | 戻るボタンで前のフォルダへ戻れるか（履歴が存在するか）。 |
| `searchQuery` | `String` | `""` | 検索フィルタ文字列。 |
| `isSearchActive` | `Boolean` | `false` | 検索バーの表示/非表示状態。 |
| `isLoading` | `Boolean` | `false` | ファイル読み込み・サムネイル生成状態。UI 側では「一覧が未取得（`filteredFileItems` が空）」の場合のみ全画面の読み込み表示に用い、一覧表示中は再び `true` になってもリストを破棄しない（スクロール位置喪失・ちらつき防止）。 |

* 初期ディレクトリ: `ExplorerViewModel` のコンストラクタ引数 `initialDirectory` で決定する。全ファイルアクセス許可時は外部ストレージ直下、未許可時は権限不要なアプリ専用フォルダ（`filesDir`）を `MainActivity.resolveInitialDirectory()` が選択する。
* スクロール位置: `ExplorerViewModel.scrollPositions: StateFlow<Map<String, Int>>` が DataStore の保存値を配信し、`ExplorerScreen` が `LazyListState` / `LazyGridState` の復元・保存に用いる。保存は `snapshotFlow` に `distinctUntilChanged()` と `debounce(300ms)` を適用し、スクロール停止時のみ実行する（DataStore への高頻度書き込み抑止）。
* フォルダ移動履歴: `ExplorerViewModel` が `directoryHistory: ArrayDeque<String>`（絶対パス）を保持する。`loadDirectory(directory, recordHistory = true)` で移動前に現在フォルダを積み、`navigateBack()` で1つ戻る。権限のないフォルダへ入り込んだ際の復帰手段として機能する。履歴はメモリ上のみで永続化しない。
* 重複ロード抑止: `sortOrder` / `showHiddenFiles` の DataStore 初回購読通知では再ロードせず、値が実際に変化した場合のみ `loadDirectory` を実行する（`isInitialLoadDone` フラグで管理）。
* メタデータ反映: フォルダのサムネイル・画像数は `METADATA_BATCH_SIZE`（8件）ごと、および完了時に一括反映し、再コンポーズ回数を抑える。

* 派生プロパティ `filteredFileItems`: `List<FileItem>` - `searchQuery` が空なら `fileItems` をそのまま返し、そうでなければ `name` に `searchQuery` を部分一致（大文字小文字無視）で含む要素に絞り込んだ一覧。

### 2.2 画像ビューワ画面状態 (`ViewerUiState`)

`ViewerViewModel` が保持する。

| フィールド | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `imageFiles` | `List<File>` | `emptyList()` | 現在のフォルダ内の画像ファイル一覧（ソート済み）。 |
| `currentIndex` | `Int` | `0` | フォルダ内での現在の画像インデックス。 |
| `cropPosition` | `CropPosition` | `CropPosition.FULL` | 漫画モード時の切り出し位置（`RIGHT_HALF`, `LEFT_HALF`, `FULL`）。 |
| `isMangaMode` | `Boolean` | `false` | 漫画モードのON/OFF。 |
| `isTranslationEnabled` | `Boolean` | `false` | 画像内翻訳機能のON/OFF。 |
| `showHiddenFiles` | `Boolean` | `false` | 隠しファイルを表示するか。 |
| `sourceLanguage` | `Language` | `Language.JAPANESE` | 翻訳元言語。 |
| `targetLanguage` | `Language` | `Language.ENGLISH` | 翻訳先言語。 |
| `textColorHex` | `String` | `"#FFFFFF"` | 翻訳オーバーレイのテキスト色。 |
| `backgroundColorHex` | `String` | `"#CC000000"` | 翻訳オーバーレイの背景色。 |
| `viewerSortOrder` | `SortOrder` | `SortOrder.NAME_ASC` | ビューワ独自のソート順。`loadImagesFromDirectory` の引数で確定した値が反映される。 |
| `explorerSortOrder` | `SortOrder` | `SortOrder.NAME_ASC` | エクスプローラのソート順（フォルダまたぎ移動時の並び順に使用）。 |
| `anchorFilePath` | `String?` | `null` | ビューワ起動時に指定された起点画像の絶対パス。ソート順変更時の再ソートで表示位置を維持する基準。 |
| `isBottomSheetVisible` | `Boolean` | `false` | 設定ボトムシートの表示/非表示。 |
| `translationOverlayItems` | `List<TranslatedOverlayItem>` | `emptyList()` | OCR・翻訳のオーバーレイ表示データ。 |
| `isTranslating` | `Boolean` | `false` | 翻訳処理の実行中フラグ。 |
| `audibleMode` | `AudibleMode` | `AudibleMode.OFF` | オーディブル再生モード。DataStore の `audible_mode` を購読して反映する。 |
| `isAudiblePlaying` | `Boolean` | `false` | オーディブル再生の実行中フラグ。 |
| `audibleItemIndex` | `Int` | `-1` | 現在読み上げ中の項目インデックス（未実行時は `-1`）。 |
| `audibleHalf` | `CropPosition` | `CropPosition.FULL` | 漫画モード時の読み上げ進行位置（右→左→次ページ）。 |
| `audibleSilentWaitSeconds` | `Float` | `3.0f` | テキスト未検出時に画像を表示し続ける秒数（DataStore 値を反映）。 |
| `audibleSpeechRate` | `Float` | `1.0f` | TTS の読み上げ速度（DataStore 値を反映）。 |

* 旧仕様の `isReadingTts` / `readingTtsIndex`（手動読み上げ）はオーディブル機能へ統合したため廃止する。

#### 2.2.1 ソート順の唯一の真実源（Single Source of Truth）

ビューワのソート順は、**`ViewerViewModel.loadImagesFromDirectory(directory, initialFile, sortOrder)` の引数 `sortOrder` を唯一の真実源**とする。

* 読み込み時の一覧ソートは、必ずこの引数 `sortOrder` を用いて `FileStorageManager.getImageFiles()` を呼び出す。
* `ViewerUiState.viewerSortOrder` は、上記引数で確定した値を保持する表示用の写像であり、読み込み時の決定には使用しない。
* DataStore の `viewer_sort_order` は「次回起動時のデフォルト値」を永続化する保存先であり、現在の表示順の決定には関与しない。
* `updateViewerSortOrder()` は永続化のみを行い、表示中一覧の再ソートは行わない。
* DataStore 値の購読（`init` 内 `collectLatest`）は、state と異なる値が通知された場合のみ `resortCurrentImages()` を実行する。これにより、`loadImagesFromDirectory` で確定した表示位置を不必要に上書きしない。
* 再ソート時の表示位置は `anchorFilePath`（起点画像）を基準に維持する。

#### 2.2.2 オーディブル再生状態機械

* 再生モードは DataStore の `audible_mode` を購読して `ViewerUiState.audibleMode` に反映する（`OFF` / `READ_RAW` / `READ_TRANSLATED` の排他制御）。
* ビューワを開いた時点で `audible_mode` が `READ_RAW` / `READ_TRANSLATED` の場合は自動的に再生を開始する。
* パイプライン: 現在画像（漫画モード時はクロップ画像）→ OCR →（`READ_TRANSLATED` 時のみ翻訳）→ `OcrGroupMerger` の結合単位を「上→下、同じ行は右→左」順に TTS へ投入 → 読み上げ完了と同時に次の画像へ移動する。
* テキスト未検出時は `audibleSilentWaitSeconds` 秒だけ表示して次の画像へ進む。エラー発生時はログのみ記録してスキップする（自動送りは維持）。
* `READ_TRANSLATED` を選択した際は `is_translation_enabled` を自動的に ON にして永続化する。
* 翻訳モデル未取得の場合は確認ダイアログを表示し、同意後にダウンロードして完了後に再生を開始する（失敗時は `audible_mode` を `OFF` に戻す）。
* 再生はアプリ前面時のみ行い、再生中は画面を常時点灯（`KEEP_SCREEN_ON`）する。背景移行時は停止する。
* フォルダ末尾到達時は既存の次フォルダ移動ロジックへ委譲して読み上げを継続する。手動で画像を移動した場合は移動先から継続する。
* 先読み: `AudiblePrefetchCache` が次画像の OCR・翻訳結果を先行取得して保持する。
* 表示: 翻訳テキストのオーバーレイは描画せず、`AudibleHighlightOverlay` が読み上げ中の検出領域を枠線でハイライトする。

### 2.3 翻訳オーバーレイ状態 (`TranslatedOverlayItem`)

独立した `TranslationUiState` クラスは存在せず、翻訳状態は `ViewerUiState` の `translationOverlayItems` および `isTranslating` として保持される。
各オーバーレイ項目は `TranslatedOverlayItem` で表現する。

| フィールド | データ型 | 説明 |
| :--- | :--- | :--- |
| `translatedText` | `String` | 翻訳後の表示テキスト。 |
| `boundingBox` | `RectF?` | 画像上の相対座標枠（元画像解像度基準）。未検出時は `null`。 |

* 翻訳処理の進行状態は `ViewerUiState.isTranslating`（`Boolean`）で表現する。仕様書旧版にあった `status` enum（`IDLE`/`PROCESSING`/`SUCCESS`/`ERROR`）は実装に存在しない。

## 3. 関連モデル定義

### 3.1 `CropPosition` (`feature/viewer/MangaCropHelper.kt`)

| 値 | 説明 |
| :--- | :--- |
| `RIGHT_HALF` | 右半分表示。 |
| `LEFT_HALF` | 左半分表示。 |
| `FULL` | 通常全体表示。 |

### 3.2 `SortOrder` (`core/model/SortOrder.kt`)

| 値 | 表示名 |
| :--- | :--- |
| `NAME_ASC` | ファイル名昇順 |
| `NAME_DESC` | ファイル名降順 |
| `DATE_DESC` | 日時(新しい順) |
| `DATE_ASC` | 日時(古い順) |
| `IMAGE_COUNT_DESC` | 画像数順(多い順) |
| `IMAGE_COUNT_ASC` | 画像数順(少ない順) |

### 3.3 `ViewMode` (`core/model/ViewMode.kt`)

| 値 | 表示名 |
| :--- | :--- |
| `LIST` | リスト表示 |
| `GRID` | グリッド表示 |

### 3.3.1 `ThumbnailSize` (`core/model/ThumbnailSize.kt`)

| 値 | 表示名 | リストサイズ(dp) | グリッドサイズ(dp) | グリッド列数 |
| :--- | :--- | :--- | :--- | :--- |
| `SMALL` | 小 | 40 | 56 | 4 |
| `MEDIUM` | 中 | 56 | 88 | 3 |
| `LARGE` | 大 | 72 | 128 | 2 |

### 3.4 `Language` (`core/model/Language.kt`)

| 値 | code | 表示名 |
| :--- | :--- | :--- |
| `JAPANESE` | `"ja"` | 日本語 |
| `ENGLISH` | `"en"` | 英語 |
| `CHINESE` | `"zh"` | 中国語 |
| `KOREAN` | `"ko"` | 韓国語 |
| `SPANISH` | `"es"` | スペイン語 |
| `FRENCH` | `"fr"` | フランス語 |
| `GERMAN` | `"de"` | ドイツ語 |

### 3.5 `FileItem` (`core/model/FileItem.kt`)

| フィールド | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `file` | `File` | - | 対象ファイル/フォルダ。 |
| `isDirectory` | `Boolean` | - | フォルダかどうか。 |
| `name` | `String` | `file.name` | 表示名。 |
| `lastModified` | `Long` | `file.lastModified()` | 更新日時（ミリ秒）。 |
| `imageCount` | `Int` | `0` | フォルダ内の画像数。 |
| `firstImagePath` | `String?` | `null` | フォルダ先頭画像のパス（サムネイル用）。 |

### 3.6 `AudibleMode` (`core/model/AudibleMode.kt`)

| 値 | 説明 |
| :--- | :--- |
| `OFF` | オーディブル停止。 |
| `READ_RAW` | OCR 結果を翻訳せず、`source_language` の音声で読み上げる。 |
| `READ_TRANSLATED` | OCR → 翻訳を行い、`target_language` の音声で読み上げる（`is_translation_enabled` を自動 ON）。 |
