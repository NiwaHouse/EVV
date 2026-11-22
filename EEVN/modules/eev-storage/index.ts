import { requireOptionalNativeModule } from 'expo-modules-core';

/**
 * ネイティブストレージ走査から返却されるディレクトリ項目型。
 *
 * [責任]: Kotlin モジュールから渡されるファイル・フォルダ基本情報の型定義。
 * [影響する状態]: エクスプローラ・ビューワの一覧構築。
 * [潜在的エラー]: なし。
 */
export interface NativeDirectoryEntry {
  path: string;
  name: string;
  isDirectory: boolean;
  lastModified: number;
  size: number;
  isImage: boolean;
}

/**
 * ネイティブ層で計算されたフォルダメタデータ型。
 *
 * [責任]: 画像枚数、先頭画像、永続サムネイルパス、翻訳DB有無、サブフォルダ有無の保持。
 * [影響する状態]: 通常フォルダDB・本棚DB・一覧UI表示。
 * [潜在的エラー]: なし。
 */
export interface NativeFolderMetadata {
  imageCount: number;
  firstImagePath: string | null;
  thumbPath: string | null;
  hasTransDb: boolean;
  hasSubDirs: boolean;
  lastModified: number;
}

interface EevStorageNativeModule {
  hasAllFilesAccess(): Promise<boolean>;
  requestAllFilesAccess(): Promise<boolean>;
  getInitialRootPath(): Promise<string>;
  listDirectory(path: string, showHidden: boolean): Promise<NativeDirectoryEntry[]>;
  getFolderMetadata(
    path: string,
    showHidden: boolean,
    thumbDirName: string
  ): Promise<NativeFolderMetadata>;
  listImagesInDirectory(path: string, showHidden: boolean): Promise<NativeDirectoryEntry[]>;
  listSubDirectories(path: string, showHidden: boolean): Promise<NativeDirectoryEntry[]>;
  checkExistingFiles(paths: string[]): Promise<Record<string, boolean>>;
  deletePath(path: string): Promise<boolean>;
  deleteThumbnail(folderPath: string, thumbDirName: string): Promise<boolean>;
  clearThumbnails(thumbDirName: string): Promise<boolean>;
}

const nativeModule = requireOptionalNativeModule<EevStorageNativeModule>('EevStorage');

const DEFAULT_METADATA: NativeFolderMetadata = {
  imageCount: 0,
  firstImagePath: null,
  thumbPath: null,
  hasTransDb: false,
  hasSubDirs: false,
  lastModified: 0,
};

/**
 * 極薄 Kotlin ネイティブモジュール（`EevStorage`）への安全ラッパー。
 *
 * すべての呼び出しを `try-catch` で保護し、未接続環境や例外発生時でもクラッシュせず安全値へフォールバックする。
 *
 * [責任]: 権限確認/要求、ファイル一覧・メタデータ取得、永続サムネイル操作、削除の JS ブリッジ。
 * [影響する状態]: 端末ストレージ、サムネイル永続領域。
 * [潜在的エラー]: ネイティブ例外またはモジュール未リンク時はログ出力して安全値を返す。
 */
export const EevStorage = {
  async hasAllFilesAccess(): Promise<boolean> {
    try {
      if (!nativeModule) return false;
      return await nativeModule.hasAllFilesAccess();
    } catch (e) {
      console.warn('hasAllFilesAccess error:', e);
      return false;
    }
  },

  async requestAllFilesAccess(): Promise<boolean> {
    try {
      if (!nativeModule) return false;
      return await nativeModule.requestAllFilesAccess();
    } catch (e) {
      console.warn('requestAllFilesAccess error:', e);
      return false;
    }
  },

  async getInitialRootPath(): Promise<string> {
    try {
      if (!nativeModule) return '/sdcard';
      return await nativeModule.getInitialRootPath();
    } catch (e) {
      console.warn('getInitialRootPath error:', e);
      return '/sdcard';
    }
  },

  async listDirectory(path: string, showHidden: boolean): Promise<NativeDirectoryEntry[]> {
    try {
      if (!nativeModule) return [];
      return (await nativeModule.listDirectory(path, showHidden)) ?? [];
    } catch (e) {
      console.warn('listDirectory error:', path, e);
      return [];
    }
  },

  async getFolderMetadata(
    path: string,
    showHidden: boolean,
    thumbDirName: string = 'folder_thumbs'
  ): Promise<NativeFolderMetadata> {
    try {
      if (!nativeModule) return DEFAULT_METADATA;
      return (await nativeModule.getFolderMetadata(path, showHidden, thumbDirName)) ?? DEFAULT_METADATA;
    } catch (e) {
      console.warn('getFolderMetadata error:', path, e);
      return DEFAULT_METADATA;
    }
  },

  async listImagesInDirectory(path: string, showHidden: boolean): Promise<NativeDirectoryEntry[]> {
    try {
      if (!nativeModule) return [];
      return (await nativeModule.listImagesInDirectory(path, showHidden)) ?? [];
    } catch (e) {
      console.warn('listImagesInDirectory error:', path, e);
      return [];
    }
  },

  async listSubDirectories(path: string, showHidden: boolean): Promise<NativeDirectoryEntry[]> {
    try {
      if (!nativeModule) return [];
      return (await nativeModule.listSubDirectories(path, showHidden)) ?? [];
    } catch (e) {
      console.warn('listSubDirectories error:', path, e);
      return [];
    }
  },

  async checkExistingFiles(paths: string[]): Promise<Record<string, boolean>> {
    try {
      if (!nativeModule || paths.length === 0) return {};
      return (await nativeModule.checkExistingFiles(paths)) ?? {};
    } catch (e) {
      console.warn('checkExistingFiles error:', e);
      return {};
    }
  },

  async deletePath(path: string): Promise<boolean> {
    try {
      if (!nativeModule) return false;
      return await nativeModule.deletePath(path);
    } catch (e) {
      console.warn('deletePath error:', path, e);
      return false;
    }
  },

  async deleteThumbnail(folderPath: string, thumbDirName: string): Promise<boolean> {
    try {
      if (!nativeModule) return false;
      return await nativeModule.deleteThumbnail(folderPath, thumbDirName);
    } catch (e) {
      console.warn('deleteThumbnail error:', folderPath, e);
      return false;
    }
  },

  async clearThumbnails(thumbDirName: string): Promise<boolean> {
    try {
      if (!nativeModule) return false;
      return await nativeModule.clearThumbnails(thumbDirName);
    } catch (e) {
      console.warn('clearThumbnails error:', thumbDirName, e);
      return false;
    }
  },
};
