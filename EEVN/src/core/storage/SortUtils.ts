import { FavoriteFolder, FileItem, SortOrder } from '../models/types';

/**
 * 大文字・小文字および数値桁を自然に比較するための文字列比較関数。
 *
 * [責任]: フォルダ名・ファイル名の安全な昇順比較。
 * [影響する状態]: なし（純粋関数）。
 * [潜在的エラー]: `localeCompare` のオプション非対応環境では `try-catch` で単純小文字比較へフォールバックする。
 */
export function compareNamesNatural(a: string, b: string): number {
  try {
    return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
  } catch {
    const la = a.toLowerCase();
    const lb = b.toLowerCase();
    if (la < lb) return -1;
    if (la > lb) return 1;
    return 0;
  }
}

/**
 * FileItem リストをフォルダとファイルに分離し、指定ソート順で並び替える。
 *
 * [責任]: フォルダ・ファイル個別ソートおよびカスタム順（昇順/降順）対応。
 * [影響する状態]: なし（新しい配列を返却）。
 * [潜在的エラー]: ソート中の予期せぬ例外は `try-catch` で捕捉し元配列を返す。
 */
export function sortFileItemsSeparately(
  items: FileItem[],
  folderOrder: SortOrder,
  fileOrder: SortOrder
): FileItem[] {
  try {
    const folders = items.filter((item) => item.isDirectory);
    const files = items.filter((item) => !item.isDirectory);

    const sortedFolders = sortDirectoriesInternal(folders, folderOrder);
    const sortedFiles = [...files].sort((a, b) => {
      switch (fileOrder) {
        case SortOrder.NAME_DESC:
        case SortOrder.CUSTOM_DESC:
          return compareNamesNatural(b.name, a.name);
        case SortOrder.DATE_DESC:
          return b.lastModified - a.lastModified || compareNamesNatural(a.name, b.name);
        case SortOrder.DATE_ASC:
          return a.lastModified - b.lastModified || compareNamesNatural(a.name, b.name);
        case SortOrder.IMAGE_COUNT_DESC:
          return b.imageCount - a.imageCount || compareNamesNatural(a.name, b.name);
        case SortOrder.IMAGE_COUNT_ASC:
          return a.imageCount - b.imageCount || compareNamesNatural(a.name, b.name);
        case SortOrder.NAME_ASC:
        case SortOrder.CUSTOM:
        default:
          return compareNamesNatural(a.name, b.name);
      }
    });

    return [...sortedFolders, ...sortedFiles];
  } catch (e) {
    console.warn('sortFileItemsSeparately error:', e);
    return items;
  }
}

function sortDirectoriesInternal(folders: FileItem[], sortOrder: SortOrder): FileItem[] {
  const copy = [...folders];
  switch (sortOrder) {
    case SortOrder.NAME_ASC:
      return copy.sort((a, b) => compareNamesNatural(a.name, b.name));
    case SortOrder.NAME_DESC:
      return copy.sort((a, b) => compareNamesNatural(b.name, a.name));
    case SortOrder.DATE_DESC:
      return copy.sort((a, b) => b.lastModified - a.lastModified || compareNamesNatural(a.name, b.name));
    case SortOrder.DATE_ASC:
      return copy.sort((a, b) => a.lastModified - b.lastModified || compareNamesNatural(a.name, b.name));
    case SortOrder.IMAGE_COUNT_DESC:
      return copy.sort((a, b) => b.imageCount - a.imageCount || compareNamesNatural(a.name, b.name));
    case SortOrder.IMAGE_COUNT_ASC:
      return copy.sort((a, b) => a.imageCount - b.imageCount || compareNamesNatural(a.name, b.name));
    case SortOrder.CUSTOM:
      return copy;
    case SortOrder.CUSTOM_DESC:
      return copy.reverse();
  }
}

/**
 * お気に入りフォルダ一覧を指定の SortOrder（CUSTOM / CUSTOM_DESC 含む）に従ってソートする。
 *
 * [責任]: 本棚一覧および本棚由来ビューワナビゲータでの一貫したソート順序提供。
 * [影響する状態]: なし（新しい配列を返却）。
 * [潜在的エラー]: ソート例外時は元配列を返す。
 */
export function sortFavoriteFolders(
  folders: FavoriteFolder[],
  sortOrder: SortOrder
): FavoriteFolder[] {
  try {
    const copy = [...folders];
    switch (sortOrder) {
      case SortOrder.CUSTOM:
        return copy.sort(
          (a, b) => a.customOrder - b.customOrder || compareNamesNatural(a.name, b.name)
        );
      case SortOrder.CUSTOM_DESC:
        return copy.sort(
          (a, b) => b.customOrder - a.customOrder || compareNamesNatural(b.name, a.name)
        );
      case SortOrder.NAME_DESC:
        return copy.sort((a, b) => compareNamesNatural(b.name, a.name));
      case SortOrder.DATE_DESC:
        return copy.sort((a, b) => b.lastModified - a.lastModified || compareNamesNatural(a.name, b.name));
      case SortOrder.DATE_ASC:
        return copy.sort((a, b) => a.lastModified - b.lastModified || compareNamesNatural(a.name, b.name));
      case SortOrder.IMAGE_COUNT_DESC:
        return copy.sort((a, b) => b.imageCount - a.imageCount || compareNamesNatural(a.name, b.name));
      case SortOrder.IMAGE_COUNT_ASC:
        return copy.sort((a, b) => a.imageCount - b.imageCount || compareNamesNatural(a.name, b.name));
      case SortOrder.NAME_ASC:
      default:
        return copy.sort((a, b) => compareNamesNatural(a.name, b.name));
    }
  } catch (e) {
    console.warn('sortFavoriteFolders error:', e);
    return folders;
  }
}
