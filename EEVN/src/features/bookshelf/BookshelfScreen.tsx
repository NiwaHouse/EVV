import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { FavoriteFolder, THUMBNAIL_SIZE_METRICS } from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';
import { DisplayOptionDialog, SortOptionDialog } from '../explorer/ExplorerDialogs';
import {
  BookshelfCategoryFilterBar,
  BookshelfCategoryManageDialog,
  BookshelfFolderCategoryDialog,
} from './BookshelfCategoryDialogs';
import { BookshelfGridCard, BookshelfListRow } from './BookshelfItemViews';
import { useBookshelfViewModel } from './useBookshelfViewModel';

/**
 * 本棚（お気に入りフォルダ）画面コンポーネント。
 *
 * カテゴリ複数選択 AND 絞り込み、カテゴリ管理、フォルダ長押しカテゴリ選択＋お気に入り解除、
 * カスタム並び替え（リストドラッグ＆グリッド順序移動）、および共有設定ダイアログを提供する。
 *
 * [責任]: 本棚一覧 UI 描画、各種ダイアログ制御、ビューワ直接起動の仲介。
 * [影響する状態]: 選択中カテゴリ、検索文字列、カスタム並び順、お気に入り解除、共有設定値。
 * [潜在的エラー]: なし。
 */
export function BookshelfScreen({
  vm,
  onBackClick,
  onOpenFolderViewer,
  onSettingsClick,
}: {
  vm: ReturnType<typeof useBookshelfViewModel>;
  onBackClick: () => void;
  onOpenFolderViewer: (folder: FavoriteFolder) => void;
  onSettingsClick: () => void;
}) {
  const { uiState, filteredFavoriteFolders } = vm;
  const [showCategoryManage, setShowCategoryManage] = useState(false);
  const [folderForCategory, setFolderForCategory] = useState<FavoriteFolder | null>(null);
  const [folderToRemove, setFolderToRemove] = useState<FavoriteFolder | null>(null);
  const [showSortDialog, setShowSortDialog] = useState(false);
  const [showDisplayDialog, setShowDisplayDialog] = useState(false);
  const [draggingPath, setDraggingPath] = useState<string | null>(null);
  const [gridReorderMode, setGridReorderMode] = useState(false);

  const isReorderEnabled =
    uiState.searchQuery.trim() === '' && uiState.selectedCategoryIds.length === 0;
  const gridCols = THUMBNAIL_SIZE_METRICS[uiState.thumbnailSize].gridColumns;
  const pathsOrder = filteredFavoriteFolders.map((f) => f.path);

  const activeCategoryTarget = folderForCategory
    ? uiState.favoriteFolders.find((f) => f.path === folderForCategory.path) ?? folderForCategory
    : null;

  return (
    <View style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.iconBtn} onPress={onBackClick}>
          <MaterialIcons name="arrow-back" size={24} color={AppTheme.textPrimary} />
        </TouchableOpacity>
        {uiState.isSearchActive ? (
          <TextInput
            style={styles.searchInput}
            value={uiState.searchQuery}
            onChangeText={vm.updateSearchQuery}
            placeholder="フォルダ名で検索..."
            placeholderTextColor={AppTheme.textSecondary}
            autoFocus
          />
        ) : (
          <Text style={styles.topTitle} numberOfLines={1}>
            本棚（お気に入り）
          </Text>
        )}
        <View style={styles.actions}>
          <TouchableOpacity
            style={styles.iconBtn}
            onPress={() => vm.toggleSearchActive(!uiState.isSearchActive)}
          >
            <MaterialIcons
              name={uiState.isSearchActive ? 'close' : 'search'}
              size={22}
              color={AppTheme.textPrimary}
            />
          </TouchableOpacity>
          <TouchableOpacity style={styles.iconBtn} onPress={() => setShowCategoryManage(true)}>
            <MaterialIcons name="category" size={22} color={AppTheme.primary} />
          </TouchableOpacity>
          {uiState.viewMode === 'GRID' && isReorderEnabled && (
            <TouchableOpacity
              style={styles.iconBtn}
              onPress={() => setGridReorderMode((prev) => !prev)}
            >
              <MaterialIcons
                name="swap-vert"
                size={22}
                color={gridReorderMode ? AppTheme.primary : AppTheme.textSecondary}
              />
            </TouchableOpacity>
          )}
          <TouchableOpacity
            style={styles.iconBtn}
            onPress={() => vm.updateViewMode(uiState.viewMode === 'LIST' ? 'GRID' : 'LIST')}
          >
            <MaterialIcons
              name={uiState.viewMode === 'LIST' ? 'grid-view' : 'view-list'}
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
          <TouchableOpacity style={styles.iconBtn} onPress={onSettingsClick}>
            <MaterialIcons name="settings" size={22} color={AppTheme.textPrimary} />
          </TouchableOpacity>
        </View>
      </View>

      <BookshelfCategoryFilterBar
        categories={uiState.categories}
        selectedCategoryIds={uiState.selectedCategoryIds}
        onToggleCategory={vm.toggleCategoryFilter}
      />

      {uiState.isLoading && filteredFavoriteFolders.length === 0 ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={AppTheme.primary} />
        </View>
      ) : filteredFavoriteFolders.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.emptyText}>
            {uiState.searchQuery.trim() !== '' || uiState.selectedCategoryIds.length > 0
              ? '該当するフォルダがありません'
              : 'お気に入りに登録されたフォルダがありません。\nエクスプローラでフォルダを長押しして「お気に入りに追加」できます。'}
          </Text>
        </View>
      ) : uiState.viewMode === 'LIST' ? (
        <ScrollView scrollEnabled={draggingPath === null}>
          {filteredFavoriteFolders.map((folder) => (
            <BookshelfListRow
              key={folder.path}
              folder={folder}
              thumbnailSize={uiState.thumbnailSize}
              showItemInfo={uiState.showItemInfo}
              isReorderEnabled={isReorderEnabled}
              itemPathsOrder={pathsOrder}
              onPress={() => onOpenFolderViewer(folder)}
              onLongPress={() => setFolderForCategory(folder)}
              onDragStateChange={setDraggingPath}
              onMoveFolder={vm.moveFolderInMemory}
              onDragEnd={() => void vm.commitCustomOrder()}
            />
          ))}
        </ScrollView>
      ) : (
        <ScrollView contentContainerStyle={styles.gridContainer}>
          {filteredFavoriteFolders.map((folder, index) => (
            <BookshelfGridCard
              key={folder.path}
              folder={folder}
              thumbnailSize={uiState.thumbnailSize}
              showItemInfo={uiState.showItemInfo}
              isReorderMode={gridReorderMode && isReorderEnabled}
              canMovePrev={index > 0}
              canMoveNext={index < filteredFavoriteFolders.length - 1}
              widthPercent={100 / gridCols}
              onPress={() => onOpenFolderViewer(folder)}
              onLongPress={() => setFolderForCategory(folder)}
              onRemovePress={() => setFolderToRemove(folder)}
              onMovePrev={() => vm.moveFolderStep(folder.path, -1)}
              onMoveNext={() => vm.moveFolderStep(folder.path, 1)}
            />
          ))}
        </ScrollView>
      )}

      <BookshelfCategoryManageDialog
        visible={showCategoryManage}
        categories={uiState.categories}
        onAddCategory={vm.addCategory}
        onRenameCategory={vm.renameCategory}
        onDeleteCategory={vm.deleteCategory}
        onDismiss={() => setShowCategoryManage(false)}
      />

      <BookshelfFolderCategoryDialog
        folder={activeCategoryTarget}
        categories={uiState.categories}
        onAddCategory={vm.addCategory}
        onSaveCategories={vm.updateFolderCategories}
        onRequestRemoveFavorite={(folder) => setFolderToRemove(folder)}
        onDismiss={() => setFolderForCategory(null)}
      />

      <SortOptionDialog
        visible={showSortDialog}
        currentFolderSortOrder={uiState.folderSortOrder}
        currentFileSortOrder={uiState.fileSortOrder}
        onFolderSortSelected={vm.updateFolderSortOrder}
        onFileSortSelected={vm.updateFileSortOrder}
        onDismiss={() => setShowSortDialog(false)}
      />

      <DisplayOptionDialog
        visible={showDisplayDialog}
        currentThumbnailSize={uiState.thumbnailSize}
        currentShowItemInfo={uiState.showItemInfo}
        onThumbnailSizeSelected={vm.updateThumbnailSize}
        onShowItemInfoChanged={vm.updateShowItemInfo}
        onDismiss={() => setShowDisplayDialog(false)}
      />

      {folderToRemove && (
        <Modal
          visible={Boolean(folderToRemove)}
          transparent
          animationType="fade"
          onRequestClose={() => setFolderToRemove(null)}
        >
          <Pressable style={styles.backdrop} onPress={() => setFolderToRemove(null)}>
            <Pressable style={styles.dialog} onPress={() => {}}>
              <Text style={styles.dialogTitle}>お気に入りの解除</Text>
              <Text style={styles.dialogBody}>
                「{folderToRemove.name}」をお気に入り（本棚）から解除しますか？
              </Text>
              <View style={styles.footerRow}>
                <TouchableOpacity style={styles.footerBtn} onPress={() => setFolderToRemove(null)}>
                  <Text style={styles.cancelText}>キャンセル</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.footerBtn}
                  onPress={() => {
                    void vm.removeFavorite(folderToRemove.path);
                    setFolderToRemove(null);
                  }}
                >
                  <Text style={styles.dangerText}>解除する</Text>
                </TouchableOpacity>
              </View>
            </Pressable>
          </Pressable>
        </Modal>
      )}
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
  topTitle: { flex: 1, fontSize: 18, fontWeight: '700', color: AppTheme.textPrimary, marginLeft: 4 },
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
  iconBtn: { padding: 5 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  emptyText: { fontSize: 14, color: AppTheme.textSecondary, textAlign: 'center', lineHeight: 22 },
  gridContainer: { flexDirection: 'row', flexWrap: 'wrap', padding: 4 },
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  dialog: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: AppTheme.background,
    borderRadius: 14,
    padding: 18,
  },
  dialogTitle: { fontSize: 17, fontWeight: '700', color: AppTheme.textPrimary, marginBottom: 10 },
  dialogBody: { fontSize: 14, color: AppTheme.textPrimary, marginBottom: 14 },
  footerRow: { flexDirection: 'row', justifyContent: 'flex-end', gap: 16 },
  footerBtn: { paddingHorizontal: 10, paddingVertical: 6 },
  cancelText: { fontSize: 15, color: AppTheme.textSecondary, fontWeight: '600' },
  dangerText: { fontSize: 15, color: AppTheme.danger, fontWeight: '700' },
});
