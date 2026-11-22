import { EevStorage } from '../../../modules/eev-storage';
import { SortOrder } from '../../core/models/types';
import { compareNamesNatural, sortFileItemsSeparately } from '../../core/storage/SortUtils';

export interface DirectoryNavigationResult {
  directoryPath: string;
  imagePaths: string[];
}

function getParentPath(path: string): string | null {
  const normalized = path.endsWith('/') && path.length > 1 ? path.slice(0, -1) : path;
  const idx = normalized.lastIndexOf('/');
  if (idx <= 0) return null;
  return normalized.slice(0, idx);
}

function getBaseName(path: string): string {
  const idx = path.lastIndexOf('/');
  return idx >= 0 && idx < path.length - 1 ? path.slice(idx + 1) : path;
}

/**
 * フォルダ内の画像パス一覧を指定ソート順で取得する。
 *
 * [責任]: ディレクトリ内の表示対象画像パス列の生成。
 * [影響する状態]: なし（読み取りのみ）。
 * [潜在的エラー]: I/O 失敗時は空配列を返す。
 */
export async function getSortedImagePaths(
  directoryPath: string,
  sortOrder: SortOrder,
  showHiddenFiles: boolean
): Promise<string[]> {
  try {
    const entries = await EevStorage.listImagesInDirectory(directoryPath, showHiddenFiles);
    const items = entries.map((e) => ({
      path: e.path,
      name: e.name,
      isDirectory: false,
      lastModified: e.lastModified,
      size: e.size,
      isImage: true,
      imageCount: 0,
      thumbPath: null,
      hasTransDb: false,
      hasSubDirs: false,
    }));
    const sorted = sortFileItemsSeparately(items, sortOrder, sortOrder);
    return sorted.map((it) => it.path);
  } catch (e) {
    console.warn('getSortedImagePaths error:', directoryPath, e);
    return [];
  }
}

async function sortCandidateDirectories(
  directoryPaths: string[],
  sortOrder: SortOrder,
  showHiddenFiles: boolean,
  customOrderMap: Record<string, number>
): Promise<string[]> {
  try {
    if (sortOrder === SortOrder.CUSTOM) {
      return [...directoryPaths].sort((a, b) => {
        const oa = customOrderMap[a] ?? Number.MAX_SAFE_INTEGER;
        const ob = customOrderMap[b] ?? Number.MAX_SAFE_INTEGER;
        return oa - ob || compareNamesNatural(getBaseName(a), getBaseName(b));
      });
    }
    if (sortOrder === SortOrder.CUSTOM_DESC) {
      return [...directoryPaths].sort((a, b) => {
        const oa = customOrderMap[a] ?? Number.MIN_SAFE_INTEGER;
        const ob = customOrderMap[b] ?? Number.MIN_SAFE_INTEGER;
        return ob - oa || compareNamesNatural(getBaseName(b), getBaseName(a));
      });
    }

    const needMeta =
      sortOrder === SortOrder.DATE_ASC ||
      sortOrder === SortOrder.DATE_DESC ||
      sortOrder === SortOrder.IMAGE_COUNT_ASC ||
      sortOrder === SortOrder.IMAGE_COUNT_DESC;

    const items = await Promise.all(
      directoryPaths.map(async (path) => {
        const meta = needMeta
          ? await EevStorage.getFolderMetadata(path, showHiddenFiles, 'folder_thumbs')
          : null;
        return {
          path,
          name: getBaseName(path),
          isDirectory: true,
          lastModified: meta?.lastModified ?? 0,
          size: 0,
          isImage: false,
          imageCount: meta?.imageCount ?? 0,
          thumbPath: null,
          hasTransDb: false,
          hasSubDirs: false,
        };
      })
    );
    return sortFileItemsSeparately(items, sortOrder, sortOrder).map((it) => it.path);
  } catch (e) {
    console.warn('sortCandidateDirectories error:', e);
    return directoryPaths;
  }
}

async function navigateDirectory(
  currentDirPath: string,
  sortOrder: SortOrder,
  showHiddenFiles: boolean,
  isFromBookshelf: boolean,
  targetFavoritePaths: string[],
  customOrderMap: Record<string, number>,
  forward: boolean
): Promise<DirectoryNavigationResult | null> {
  try {
    if (isFromBookshelf) {
      if (targetFavoritePaths.length === 0) return null;
      const existMap = await EevStorage.checkExistingFiles(targetFavoritePaths);
      const validFavorites = targetFavoritePaths.filter((p) => existMap[p] !== false);
      if (validFavorites.length === 0) return null;

      const sortedDirs = await sortCandidateDirectories(
        validFavorites,
        sortOrder,
        showHiddenFiles,
        customOrderMap
      );
      const currentIdx = sortedDirs.indexOf(currentDirPath);
      const len = sortedDirs.length;

      for (let offset = 1; offset <= len; offset++) {
        const targetIdx =
          currentIdx < 0
            ? forward
              ? (offset - 1) % len
              : (len - offset) % len
            : forward
              ? (currentIdx + offset) % len
              : ((currentIdx - offset) % len + len) % len;
        const candidate = sortedDirs[targetIdx];
        if (len > 1 && candidate === currentDirPath) continue;
        const imagePaths = await getSortedImagePaths(candidate, sortOrder, showHiddenFiles);
        if (imagePaths.length > 0) {
          return { directoryPath: candidate, imagePaths };
        }
      }
      return null;
    }

    const parentPath = getParentPath(currentDirPath);
    if (!parentPath) return null;
    const subEntries = await EevStorage.listSubDirectories(parentPath, showHiddenFiles);
    const subPaths = subEntries.map((s) => s.path);
    const sortedDirs = await sortCandidateDirectories(
      subPaths,
      sortOrder,
      showHiddenFiles,
      customOrderMap
    );
    const currentIdx = sortedDirs.indexOf(currentDirPath);
    if (currentIdx < 0) return null;

    if (forward) {
      for (let i = currentIdx + 1; i < sortedDirs.length; i++) {
        const imagePaths = await getSortedImagePaths(sortedDirs[i], sortOrder, showHiddenFiles);
        if (imagePaths.length > 0) {
          return { directoryPath: sortedDirs[i], imagePaths };
        }
      }
    } else {
      for (let i = currentIdx - 1; i >= 0; i--) {
        const imagePaths = await getSortedImagePaths(sortedDirs[i], sortOrder, showHiddenFiles);
        if (imagePaths.length > 0) {
          return { directoryPath: sortedDirs[i], imagePaths };
        }
      }
    }
    return null;
  } catch (e) {
    console.warn('navigateDirectory error:', e);
    return null;
  }
}

/**
 * 次の画像フォルダを探索して返す（本棚起動時はカテゴリ絞り込み内ループ、通常時は非ループ）。
 */
export function findNextDirectory(
  currentDirPath: string,
  sortOrder: SortOrder,
  showHiddenFiles: boolean,
  isFromBookshelf: boolean,
  targetFavoritePaths: string[],
  customOrderMap: Record<string, number>
): Promise<DirectoryNavigationResult | null> {
  return navigateDirectory(
    currentDirPath,
    sortOrder,
    showHiddenFiles,
    isFromBookshelf,
    targetFavoritePaths,
    customOrderMap,
    true
  );
}

/**
 * 前の画像フォルダを探索して返す（本棚起動時はカテゴリ絞り込み内ループ、通常時は非ループ）。
 */
export function findPreviousDirectory(
  currentDirPath: string,
  sortOrder: SortOrder,
  showHiddenFiles: boolean,
  isFromBookshelf: boolean,
  targetFavoritePaths: string[],
  customOrderMap: Record<string, number>
): Promise<DirectoryNavigationResult | null> {
  return navigateDirectory(
    currentDirPath,
    sortOrder,
    showHiddenFiles,
    isFromBookshelf,
    targetFavoritePaths,
    customOrderMap,
    false
  );
}
