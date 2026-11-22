/**
 * アプリ全体で共有するドメインモデル・列挙型定義。
 *
 * Core 基盤レイヤーに属し、Feature モジュールへは依存しない。
 *
 * [責任]: ソート順、表示モード、サムネイルサイズ、ファイル/お気に入り/カテゴリモデル、画面状態の型提供。
 * [影響する状態]: エクスプローラ・本棚・ビューワ・設定のすべての UI と永続ストレージ。
 * [潜在的エラー]: なし。
 */

export enum SortOrder {
  NAME_ASC = 'NAME_ASC',
  NAME_DESC = 'NAME_DESC',
  DATE_DESC = 'DATE_DESC',
  DATE_ASC = 'DATE_ASC',
  IMAGE_COUNT_DESC = 'IMAGE_COUNT_DESC',
  IMAGE_COUNT_ASC = 'IMAGE_COUNT_ASC',
  CUSTOM = 'CUSTOM',
  CUSTOM_DESC = 'CUSTOM_DESC',
}

export const SORT_ORDER_LABELS: Record<SortOrder, string> = {
  [SortOrder.NAME_ASC]: 'ファイル名昇順',
  [SortOrder.NAME_DESC]: 'ファイル名降順',
  [SortOrder.DATE_DESC]: '日時(新しい順)',
  [SortOrder.DATE_ASC]: '日時(古い順)',
  [SortOrder.IMAGE_COUNT_DESC]: '画像数順(多い順)',
  [SortOrder.IMAGE_COUNT_ASC]: '画像数順(少ない順)',
  [SortOrder.CUSTOM]: 'カスタム順 (昇順)',
  [SortOrder.CUSTOM_DESC]: 'カスタム順 (降順)',
};

export type ViewMode = 'LIST' | 'GRID';

export type ThumbnailSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export const THUMBNAIL_SIZE_METRICS: Record<
  ThumbnailSize,
  { label: string; listSizeDp: number; gridSizeDp: number; gridColumns: number }
> = {
  SMALL: { label: '小', listSizeDp: 40, gridSizeDp: 64, gridColumns: 4 },
  MEDIUM: { label: '中', listSizeDp: 56, gridSizeDp: 96, gridColumns: 3 },
  LARGE: { label: '大', listSizeDp: 72, gridSizeDp: 136, gridColumns: 2 },
};

export type CropPosition = 'FULL' | 'RIGHT_HALF' | 'LEFT_HALF';

export interface FileItem {
  path: string;
  name: string;
  isDirectory: boolean;
  lastModified: number;
  size: number;
  isImage: boolean;
  imageCount: number;
  thumbPath: string | null;
  hasTransDb: boolean;
  hasSubDirs: boolean;
}

export interface BookshelfCategory {
  id: number;
  name: string;
}

export interface FavoriteFolder {
  path: string;
  name: string;
  lastModified: number;
  thumbPath: string | null;
  imageCount: number;
  hasTransDb: boolean;
  customOrder: number;
  categoryIds: number[];
  categoryNames: string[];
}

export type Screen =
  | { type: 'explorer' }
  | { type: 'bookshelf' }
  | {
      type: 'viewer';
      directoryPath: string;
      initialFilePath: string;
      isFromBookshelf: boolean;
    }
  | { type: 'settings'; fromBookshelf: boolean };

/**
 * 文字列が有効な SortOrder か判定し、不正値時は NAME_ASC へフォールバックする。
 */
export function parseSortOrder(raw: string | null | undefined): SortOrder {
  if (!raw) return SortOrder.NAME_ASC;
  const entries = Object.values(SortOrder) as string[];
  return entries.includes(raw) ? (raw as SortOrder) : SortOrder.NAME_ASC;
}
