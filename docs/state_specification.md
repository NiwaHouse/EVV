# グローバル状態仕様書 (State Specification)

## 1. 永続化状態 (Persistent State - DataStore)

| 状態識別子 (State ID) | データ型 | デフォルト値 | 説明・影響範囲 |
| :--- | :--- | :--- | :--- |
| `is_manga_mode_enabled` | `Boolean` | `false` | 漫画モード（見開き右→左表示）のON/OFF。画像ビューワの表示領域制御に影響。 |
| `is_translation_enabled` | `Boolean` | `false` | 画像内翻訳機能のON/OFF。 |
| `source_language` | `String` | `"ja"` | 翻訳元言語コード (`"ja"`, `"en"`, `"zh"`). |
| `target_language` | `String` | `"en"` | 翻訳先言語コード (`"ja"`, `"en"`, `"zh"`). |
| `sort_order` | `String` | `"NAME_ASC"` | エクスプローラおよびビューワのソート順 (`NAME_ASC`, `DATE_DESC`, `IMAGE_COUNT_DESC`). |

## 2. インメモリ画面状態 (In-Memory Screen State)

### 2.1 エクスプローラ画面状態 (`ExplorerUiState`)
* `currentDirectory`: `File` - 現在表示中のフォルダパス
* `fileItems`: `List<FileItem>` - ソート済みのファイル・フォルダ一覧
* `isLoading`: `Boolean` - ファイル読み込み・サムネイル生成状態
* `sortOrder`: `SortOrder` - 現在適用中のソート順

### 2.2 画像ビューワ画面状態 (`ViewerUiState`)
* `currentImageIndex`: `Int` - フォルダ内での現在の画像インデックス
* `currentCropPosition`: `CropPosition` - 漫画モード時の切り出し位置 (`RIGHT_HALF`, `LEFT_HALF`, `FULL`)
* `isBottomSheetVisible`: `Boolean` - 設定ボトムシートの表示/非表示
* `translationState`: `TranslationUiState` - OCR・翻訳のオーバーレイ表示データ

### 2.3 翻訳オーバーレイ状態 (`TranslationUiState`)
* `status`: `Enum` (`IDLE`, `PROCESSING`, `SUCCESS`, `ERROR`)
* `overlayItems`: `List<TextOverlayItem>`
  * `boundingElement`: `RectF` - 画像上の相対座標枠
  * `translatedText`: `String` - 翻訳後の表示テキスト
