import React, { useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { FileItem, THUMBNAIL_SIZE_METRICS } from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';
import {
  DeleteConfirmDialog,
  DeleteNonFavoriteFoldersDialog,
  DisplayOptionDialog,
  ItemContextMenuDialog,
  SortOptionDialog,
} from './ExplorerDialogs';
import { BreadcrumbBar, ExplorerGridCell, ExplorerListRow } from './ExplorerItemViews';
import { useExplorerViewModel } from './useExplorerViewModel';

/**
 * ファイルエクスプローラ画面コンポーネント。
 *
 * [責任]: フォルダ階層ブラウズ、検索、本棚遷移、表示設定・ソート変更、永続サムネイル表示、本棚未登録フォルダ一括削除操作の統合UI。
 * [影響する状態]: エクスプローラ階層・表示一覧、お気に入り登録、フォルダ削除、共有設定値。
 * [潜在的エラー]: 操作時のエラーは ViewModel 側 `try-catch` により安全に吸収する。
 */
export function ExplorerScreen({
  vm,
  onOpenBookshelf,
  onOpenSettings,
  onOpenViewerFromFolder,
}: {
  vm: ReturnType<typeof useExplorerViewModel>;
  onOpenBookshelf: () => void;
  onOpenSettings: () => void;
  onOpenViewerFromFolder: (directoryPath: string, initialFilePath: string) => void;
}) {
  const [contextItem, setContextItem] = useState<FileItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<FileItem | null>(null);
  const [showSortDialog, setShowSortDialog] = useState(false);
  const [showDisplayDialog, setShowDisplayDialog] = useState(false);

  const [bulkDeleteVisible, setBulkDeleteVisible] = useState(false);
  const [bulkScanning, setBulkScanning] = useState(false);
  const [bulkTargets, setBulkTargets] = useState<FileItem[]>([]);

  const favoriteSet = new Set(vm.settings.favoriteFolders);
  const gridCols = THUMBNAIL_SIZE_METRICS[vm.settings.thumbnailSize].gridColumns;

  const handleOpenBulkDelete = () => {
    setBulkDeleteVisible(true);
    setBulkScanning(true);
    setBulkTargets([]);
    void vm.collectDeletableNonFavoriteFolders().then((found) => {
      setBulkTargets(found);
      setBulkScanning(false);
    });
  };

  const handleItemPress = (item: FileItem) => {
    if (item.isDirectory) {
      vm.navigateToFolder(item.path, true);
    } else if (item.isImage) {
      const parentIdx = item.path.lastIndexOf('/');
      const dir = parentIdx > 0 ? item.path.slice(0, parentIdx) : vm.currentPath;
      onOpenViewerFromFolder(dir, item.path);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.topBar}>
        {vm.canNavigateBack && !vm.isSearchActive && (
          <TouchableOpacity style={styles.iconBtn} onPress={() => vm.navigateBack()}>
            <MaterialIcons name="arrow-back" size={24} color={AppTheme.textPrimary} />
          </TouchableOpacity>
        )}
        {vm.isSearchActive ? (
          <TextInput
            style={styles.searchInput}
            value={vm.searchQuery}
            onChangeText={vm.updateSearchQuery}
            placeholder="ファイル・フォルダ名で検索..."
            placeholderTextColor={AppTheme.textSecondary}
            autoFocus
          />
        ) : (
          <Text style={styles.topTitle} numberOfLines={1}>
            EEVN
          </Text>
        )}
        <View style={styles.actions}>
          <TouchableOpacity
            style={styles.iconBtn}
            onPress={() => vm.toggleSearchActive(!vm.isSearchActive)}
          >
            <MaterialIcons
              name={vm.isSearchActive ? 'close' : 'search'}
              size={22}
              color={AppTheme.textPrimary}
            />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconBtn} onPress={onOpenBookshelf}>
            <MaterialIcons name="collections-bookmark" size={22} color={AppTheme.primary} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconBtn} onPress={handleOpenBulkDelete}>
            <MaterialIcons name="delete-sweep" size={22} color={AppTheme.danger} />
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.iconBtn}
            onPress={() => vm.updateViewMode(vm.settings.viewMode === 'LIST' ? 'GRID' : 'LIST')}
          >
            <MaterialIcons
              name={vm.settings.viewMode === 'LIST' ? 'grid-view' : 'view-list'}
              size={22}
              color={AppTheme.textPrimary}
            />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconBtn} onPress={() => setShowSortDialog(true)}>
            <MaterialIcons name="sort" size={22} color={AppTheme.textPrimary} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconBtn} onPress={() => setShowDisplayDialog(true)}>
            <MaterialIcons name="tune" size={22} color={AppTheme.textPrimary} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconBtn} onPress={onOpenSettings}>
            <MaterialIcons name="settings" size={22} color={AppTheme.textPrimary} />
          </TouchableOpacity>
        </View>
      </View>

      {!vm.hasStoragePermission && (
        <TouchableOpacity style={styles.permBanner} onPress={vm.requestAllFilesAccess}>
          <MaterialIcons name="warning" size={18} color={AppTheme.warningText} />
          <Text style={styles.permText}>
            端末内フォルダの閲覧には「全ファイルアクセス権限」が必要です（タップして設定）
          </Text>
        </TouchableOpacity>
      )}

      <BreadcrumbBar
        rootPath={vm.rootPath}
        currentPath={vm.currentPath}
        onNavigateToPath={(path) => vm.navigateToFolder(path, true)}
      />

      {vm.isLoading && vm.items.length === 0 ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={AppTheme.primary} />
        </View>
      ) : vm.items.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.emptyText}>項目がありません</Text>
        </View>
      ) : vm.settings.viewMode === 'LIST' ? (
        <FlatList
          data={vm.items}
          keyExtractor={(item) => item.path}
          renderItem={({ item }) => (
            <ExplorerListRow
              item={item}
              isFavorite={favoriteSet.has(item.path)}
              thumbnailSize={vm.settings.thumbnailSize}
              showItemInfo={vm.settings.showItemInfo}
              onPress={() => handleItemPress(item)}
              onLongPress={() => setContextItem(item)}
            />
          )}
        />
      ) : (
        <ScrollView contentContainerStyle={styles.gridContainer}>
          {vm.items.map((item) => (
            <ExplorerGridCell
              key={item.path}
              item={item}
              isFavorite={favoriteSet.has(item.path)}
              thumbnailSize={vm.settings.thumbnailSize}
              showItemInfo={vm.settings.showItemInfo}
              widthPercent={100 / gridCols}
              onPress={() => handleItemPress(item)}
              onLongPress={() => setContextItem(item)}
            />
          ))}
        </ScrollView>
      )}

      <SortOptionDialog
        visible={showSortDialog}
        currentFolderSortOrder={vm.settings.folderSortOrder}
        currentFileSortOrder={vm.settings.fileSortOrder}
        onFolderSortSelected={vm.updateFolderSortOrder}
        onFileSortSelected={vm.updateFileSortOrder}
        onDismiss={() => setShowSortDialog(false)}
      />

      <DisplayOptionDialog
        visible={showDisplayDialog}
        currentThumbnailSize={vm.settings.thumbnailSize}
        currentShowItemInfo={vm.settings.showItemInfo}
        onThumbnailSizeSelected={vm.updateThumbnailSize}
        onShowItemInfoChanged={vm.updateShowItemInfo}
        onDismiss={() => setShowDisplayDialog(false)}
      />

      <ItemContextMenuDialog
        item={contextItem}
        isFavorite={Boolean(contextItem && favoriteSet.has(contextItem.path))}
        onToggleFavorite={(it) => void vm.toggleFavorite(it)}
        onRequestDelete={(it) => setDeleteTarget(it)}
        onDismiss={() => setContextItem(null)}
      />

      <DeleteConfirmDialog
        item={deleteTarget}
        onConfirm={(it) => void vm.deleteItem(it)}
        onDismiss={() => setDeleteTarget(null)}
      />

      <DeleteNonFavoriteFoldersDialog
        visible={bulkDeleteVisible}
        isScanning={bulkScanning}
        targets={bulkTargets}
        onConfirmDelete={(targets) => void vm.executeDeleteNonFavoriteFolders(targets)}
        onDismiss={() => setBulkDeleteVisible(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: AppTheme.background },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: AppTheme.border,
  },
  topTitle: { flex: 1, fontSize: 18, fontWeight: '700', color: AppTheme.textPrimary, marginLeft: 8 },
  searchInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: AppTheme.border,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontSize: 14,
    color: AppTheme.textPrimary,
    marginRight: 6,
  },
  actions: { flexDirection: 'row', alignItems: 'center' },
  iconBtn: { padding: 6 },
  permBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: AppTheme.warningBg,
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 8,
  },
  permText: { flex: 1, fontSize: 12, color: AppTheme.warningText, fontWeight: '600' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { fontSize: 14, color: AppTheme.textSecondary },
  gridContainer: { flexDirection: 'row', flexWrap: 'wrap', padding: 4 },
});
