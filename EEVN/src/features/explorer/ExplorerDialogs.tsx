import React, { useState } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import {
  FileItem,
  SORT_ORDER_LABELS,
  SortOrder,
  THUMBNAIL_SIZE_METRICS,
  ThumbnailSize,
} from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';

export {
  DeleteConfirmDialog,
  DeleteNonFavoriteFoldersDialog,
} from './DeleteNonFavoriteFoldersDialog';

const FOLDER_SORT_OPTIONS: SortOrder[] = [
  SortOrder.NAME_ASC,
  SortOrder.NAME_DESC,
  SortOrder.DATE_DESC,
  SortOrder.DATE_ASC,
  SortOrder.IMAGE_COUNT_DESC,
  SortOrder.IMAGE_COUNT_ASC,
  SortOrder.CUSTOM,
  SortOrder.CUSTOM_DESC,
];

const FILE_SORT_OPTIONS: SortOrder[] = [
  SortOrder.NAME_ASC,
  SortOrder.NAME_DESC,
  SortOrder.DATE_DESC,
  SortOrder.DATE_ASC,
];

/**
 * フォルダとファイルの並び順を個別に選択するダイアログ（エクスプローラ・本棚共通）。
 *
 * [責任]: フォルダ用（カスタム順昇降含む）・ファイル用のタブ切り替えソート選択 UI。
 * [影響する状態]: `folderSortOrder` および `fileSortOrder`。
 * [潜在的エラー]: なし。
 */
export function SortOptionDialog({
  visible,
  currentFolderSortOrder,
  currentFileSortOrder,
  onFolderSortSelected,
  onFileSortSelected,
  onDismiss,
}: {
  visible: boolean;
  currentFolderSortOrder: SortOrder;
  currentFileSortOrder: SortOrder;
  onFolderSortSelected: (order: SortOrder) => void;
  onFileSortSelected: (order: SortOrder) => void;
  onDismiss: () => void;
}) {
  const [selectedTab, setSelectedTab] = useState<'folder' | 'file'>('folder');
  const options = selectedTab === 'folder' ? FOLDER_SORT_OPTIONS : FILE_SORT_OPTIONS;
  const activeValue =
    selectedTab === 'folder' ? currentFolderSortOrder : currentFileSortOrder;

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.title}>並び替え設定</Text>
          <View style={styles.tabRow}>
            <TouchableOpacity
              style={[styles.tabBtn, selectedTab === 'folder' && styles.tabBtnActive]}
              onPress={() => setSelectedTab('folder')}
            >
              <Text style={[styles.tabText, selectedTab === 'folder' && styles.tabTextActive]}>
                フォルダ順
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.tabBtn, selectedTab === 'file' && styles.tabBtnActive]}
              onPress={() => setSelectedTab('file')}
            >
              <Text style={[styles.tabText, selectedTab === 'file' && styles.tabTextActive]}>
                ファイル順
              </Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.scrollArea}>
            {options.map((opt) => {
              const selected = opt === activeValue;
              return (
                <TouchableOpacity
                  key={opt}
                  style={styles.optionRow}
                  onPress={() => {
                    if (selectedTab === 'folder') onFolderSortSelected(opt);
                    else onFileSortSelected(opt);
                  }}
                >
                  <MaterialIcons
                    name={selected ? 'radio-button-checked' : 'radio-button-unchecked'}
                    size={22}
                    color={selected ? AppTheme.primary : AppTheme.textSecondary}
                  />
                  <Text style={styles.optionLabel}>{SORT_ORDER_LABELS[opt]}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>

          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.primaryBtnText}>完了</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/**
 * サムネイルサイズ（小・中・大）と付加情報表示を切り替えるダイアログ（エクスプローラ・本棚共通）。
 *
 * [責任]: サムネイルサイズ3段階および日付/枚数表示スイッチ UI。
 * [影響する状態]: `thumbnailSize` および `showItemInfo`。
 * [潜在的エラー]: なし。
 */
export function DisplayOptionDialog({
  visible,
  currentThumbnailSize,
  currentShowItemInfo,
  onThumbnailSizeSelected,
  onShowItemInfoChanged,
  onDismiss,
}: {
  visible: boolean;
  currentThumbnailSize: ThumbnailSize;
  currentShowItemInfo: boolean;
  onThumbnailSizeSelected: (size: ThumbnailSize) => void;
  onShowItemInfoChanged: (enabled: boolean) => void;
  onDismiss: () => void;
}) {
  const sizes: ThumbnailSize[] = ['SMALL', 'MEDIUM', 'LARGE'];

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.title}>表示オプション</Text>
          <Text style={styles.sectionLabel}>サムネイルサイズ</Text>
          <View style={styles.sizeRow}>
            {sizes.map((size) => {
              const active = size === currentThumbnailSize;
              return (
                <TouchableOpacity
                  key={size}
                  style={[styles.sizeChip, active && styles.sizeChipActive]}
                  onPress={() => onThumbnailSizeSelected(size)}
                >
                  <Text style={[styles.sizeChipText, active && styles.sizeChipTextActive]}>
                    {THUMBNAIL_SIZE_METRICS[size].label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          <View style={styles.switchRow}>
            <Text style={styles.optionLabel}>日付・画像枚数などの情報を表示</Text>
            <Switch value={currentShowItemInfo} onValueChange={onShowItemInfoChanged} />
          </View>

          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.primaryBtnText}>完了</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/**
 * ファイル・フォルダ長押し時の操作メニューダイアログ。
 *
 * [責任]: お気に入り登録/解除および削除メニューの表示。
 * [影響する状態]: お気に入り設定、個別削除確認ダイアログ表示。
 * [潜在的エラー]: なし。
 */
export function ItemContextMenuDialog({
  item,
  isFavorite,
  onToggleFavorite,
  onRequestDelete,
  onDismiss,
}: {
  item: FileItem | null;
  isFavorite: boolean;
  onToggleFavorite: (item: FileItem) => void;
  onRequestDelete: (item: FileItem) => void;
  onDismiss: () => void;
}) {
  if (!item) return null;
  return (
    <Modal visible={Boolean(item)} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.title} numberOfLines={2}>
            {item.name}
          </Text>
          {item.isDirectory && (
            <TouchableOpacity
              style={styles.menuRow}
              onPress={() => {
                onToggleFavorite(item);
                onDismiss();
              }}
            >
              <MaterialIcons
                name={isFavorite ? 'star' : 'star-border'}
                size={22}
                color={AppTheme.starActive}
              />
              <Text style={styles.optionLabel}>
                {isFavorite ? 'お気に入り（本棚）から解除' : 'お気に入り（本棚）に追加'}
              </Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity
            style={styles.menuRow}
            onPress={() => {
              onDismiss();
              onRequestDelete(item);
            }}
          >
            <MaterialIcons name="delete" size={22} color={AppTheme.danger} />
            <Text style={[styles.optionLabel, { color: AppTheme.danger }]}>削除</Text>
          </TouchableOpacity>
          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.cancelBtnText}>閉じる</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  dialog: {
    width: '100%',
    maxWidth: 400,
    backgroundColor: AppTheme.background,
    borderRadius: 14,
    padding: 18,
  },
  title: { fontSize: 17, fontWeight: '700', color: AppTheme.textPrimary, marginBottom: 12 },
  tabRow: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: AppTheme.border, marginBottom: 8 },
  tabBtn: { flex: 1, paddingVertical: 8, alignItems: 'center' },
  tabBtnActive: { borderBottomWidth: 2, borderBottomColor: AppTheme.primary },
  tabText: { fontSize: 14, color: AppTheme.textSecondary },
  tabTextActive: { color: AppTheme.primary, fontWeight: '700' },
  scrollArea: { maxHeight: 260 },
  optionRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 10 },
  menuRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 11, gap: 12 },
  optionLabel: { fontSize: 15, color: AppTheme.textPrimary, flex: 1 },
  sectionLabel: { fontSize: 13, color: AppTheme.textSecondary, marginBottom: 8 },
  sizeRow: { flexDirection: 'row', gap: 10, marginBottom: 16 },
  sizeChip: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: AppTheme.border,
    alignItems: 'center',
  },
  sizeChipActive: { backgroundColor: AppTheme.primaryContainer, borderColor: AppTheme.primary },
  sizeChipText: { fontSize: 14, color: AppTheme.textPrimary },
  sizeChipTextActive: { color: AppTheme.onPrimaryContainer, fontWeight: '700' },
  switchRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8 },
  footerRow: { flexDirection: 'row', justifyContent: 'flex-end', gap: 16, marginTop: 6 },
  footerBtn: { paddingHorizontal: 10, paddingVertical: 6 },
  primaryBtnText: { fontSize: 15, fontWeight: '700', color: AppTheme.primary },
  cancelBtnText: { fontSize: 15, fontWeight: '600', color: AppTheme.textSecondary },
});
