# 状態仕様書 (State Specification - EEVN)

## 1. 共有データモデル・列挙型 (`src/core/models/types.ts`)

### 1.1 `SortOrder`
| 値 | 表示名 | 説明 |
| :--- | :--- | :--- |
| `NAME_ASC` | ファイル名昇順 | 名前 A→Z / あ→ん（自然順比較対応）。 |
| `NAME_DESC` | ファイル名降順 | 名前降順。 |
| `DATE_DESC` | 日時(新しい順) | `lastModified` 降順。 |
| `DATE_ASC` | 日時(古い順) | `lastModified` 昇順。 |
| `IMAGE_COUNT_DESC` | 画像数順(多い順) | `imageCount` 降順（フォルダ向け）。 |
| `IMAGE_COUNT_ASC` | 画像数順(少ない順) | `imageCount` 昇順（フォルダ向け）。 |
| `CUSTOM` | カスタム順 (昇順) | `customOrder` 昇順。 |
| `CUSTOM_DESC` | カスタム順 (降順) | `customOrder` 降順。 |

### 1.2 `ViewMode` / `ThumbnailSize` / `CropPosition`
* `ViewMode`: `'LIST' | 'GRID'`
* `ThumbnailSize`: `'SMALL'`（list 40 / grid 64 / 4列）, `'MEDIUM'`（list 56 / grid 96 / 3列）, `'LARGE'`（list 72 / grid 136 / 2列）
* `CropPosition`: `'FULL' | 'RIGHT_HALF' | 'LEFT_HALF'`

### 1.3 `FileItem`
| フィールド | 型 | 説明 |
| :--- | :--- | :--- |
| `path` | `string` | 絶対パス。 |
| `name` | `string` | ファイル/フォルダ名。 |
| `isDirectory` | `boolean` | フォルダかどうか。 |
| `lastModified` | `number` | 最終更新日時（ミリ秒）。 |
| `size` | `number` | バイトサイズ（ファイル用）。 |
| `imageCount` | `number` | 直下の画像ファイル枚数。 |
| `thumbPath` | `string \| null` | `filesDir/folder_thumbs` 等に保存された永続サムネイルパス。 |
| `hasTransDb` | `boolean` | 直下に `.trans_image.db` が存在するか。 |
| `hasSubDirs` | `boolean` | 直下にサブフォルダが存在するか（未登録フォルダ一括削除の除外判定にも利用）。 |

### 1.4 `FavoriteFolder` & `BookshelfCategory`
* `BookshelfCategory`: `{ id: number; name: string }`
* `FavoriteFolder`:
  * `path: string`, `name: string`, `lastModified: number`, `thumbPath: string | null`, `imageCount: number`, `hasTransDb: boolean`, `customOrder: number`, `categoryIds: number[]`, `categoryNames: string[]`

### 1.5 `Screen`
* `{ type: 'explorer' }`
* `{ type: 'bookshelf' }`
* `{ type: 'viewer'; directoryPath: string; initialFilePath: string; isFromBookshelf: boolean }`
* `{ type: 'settings'; fromBookshelf: boolean }`

---

## 2. 永続化ストレージ仕様

### 2.1 `AppSettingsStore` (`@react-native-async-storage/async-storage`)
| キー | 型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- |
| `eevn_folder_sort_order` | `SortOrder` | `NAME_ASC` | エクスプローラ・本棚共有のフォルダ並び順。 |
| `eevn_file_sort_order` | `SortOrder` | `NAME_ASC` | エクスプローラ・本棚共有のファイル並び順。 |
| `eevn_viewer_sort_order` | `SortOrder` | `NAME_ASC` | 画像ビューワ独自の並び順（次フォルダ基準含む）。 |
| `eevn_view_mode` | `ViewMode` | `LIST` | リスト / グリッド表示モード。 |
| `eevn_thumbnail_size` | `ThumbnailSize` | `MEDIUM` | サムネイル表示サイズ（小・中・大）。 |
| `eevn_show_item_info` | `boolean` | `false` | 日付・枚数などの付加情報表示。 |
| `eevn_show_hidden_files` | `boolean` | `false` | ドット始まりの隠しファイル表示。 |
| `eevn_is_manga_mode` | `boolean` | `false` | 漫画モード（見開き右→左）。 |
| `eevn_favorite_folders` | `string[]` | `[]` | 本棚（お気に入り）登録済みフォルダ絶対パスの配列。 |

### 2.2 通常フォルダキャッシュ DB (`folder_cache.db`)
* テーブル `folder_cache_items`:
  * `folder_path TEXT PRIMARY KEY`
  * `last_modified INTEGER NOT NULL DEFAULT 0`
  * `image_count INTEGER NOT NULL DEFAULT 0`
  * `thumb_path TEXT`
  * `has_trans_db INTEGER NOT NULL DEFAULT 0`
  * `has_sub_dirs INTEGER NOT NULL DEFAULT 0`

### 2.3 本棚 DB (`bookshelf.db`)
* テーブル `bookshelf_items`: `folder_path (PK)`, `folder_name`, `custom_order`, `thumb_path`, `image_count`, `last_modified`, `has_trans_db`
* テーブル `bookshelf_categories`: `id (INTEGER PK AUTOINCREMENT)`, `name (TEXT UNIQUE)`, `created_at`
* テーブル `bookshelf_folder_categories`: `folder_path`, `category_id`（複合PK）

---

## 3. 各画面の ViewModel 状態

### 3.1 `ExplorerUiState` (`useExplorerViewModel`)
* `currentPath: string`, `rootPath: string`, `historyStack: string[]`
* `items: FileItem[]`, `isLoading: boolean`, `hasStoragePermission: boolean`
* `searchQuery: string`, `isSearchActive: boolean`
* `deletableNonFavoriteFolders: FileItem[]`（確認ダイアログ用の抽出結果）

### 3.2 `BookshelfUiState` (`useBookshelfViewModel`)
* `favoriteFolders: FavoriteFolder[]`, `categories: BookshelfCategory[]`
* `selectedCategoryIds: number[]`（空=すべて、`-1`含む=未分類、正数配列=AND絞り込み）
* `searchQuery: string`, `isSearchActive: boolean`, `isLoading: boolean`

### 3.3 `ViewerUiState` (`useViewerViewModel`)
* `directoryPath: string`, `imagePaths: string[]`, `currentIndex: number`
* `cropPosition: CropPosition`, `isMangaMode: boolean`, `viewerSortOrder: SortOrder`
* `showHiddenFiles: boolean`, `isFromBookshelf: boolean`, `isCurrentFolderFavorite: boolean`
* `isBottomSheetVisible: boolean`, `isTripleTapDialogVisible: boolean`
