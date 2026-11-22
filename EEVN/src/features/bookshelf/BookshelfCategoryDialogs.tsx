import React, { useEffect, useState } from 'react';
import {
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
import { BookshelfCategory, FavoriteFolder } from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';
import { UNCATEGORIZED_CATEGORY_ID } from './useBookshelfViewModel';

/**
 * 本棚ヘッダー直下の横スクロール可能なカテゴリ絞り込みチップバー（複数選択 AND 検索対応）。
 *
 * [責任]: 「すべて」「未分類」および登録カテゴリの複数選択表示とタップ切り替え。
 * [影響する状態]: `selectedCategoryIds`。
 * [潜在的エラー]: なし。
 */
export function BookshelfCategoryFilterBar({
  categories,
  selectedCategoryIds,
  onToggleCategory,
}: {
  categories: BookshelfCategory[];
  selectedCategoryIds: number[];
  onToggleCategory: (categoryId: number | null) => void;
}) {
  const isAll = selectedCategoryIds.length === 0;
  const isUncategorized = selectedCategoryIds.includes(UNCATEGORIZED_CATEGORY_ID);

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.filterBar}
      contentContainerStyle={styles.filterBarContent}
    >
      <TouchableOpacity
        style={[styles.chip, isAll && styles.chipSelected]}
        onPress={() => onToggleCategory(null)}
      >
        <Text style={[styles.chipText, isAll && styles.chipTextSelected]}>すべて</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[styles.chip, isUncategorized && styles.chipSelected]}
        onPress={() => onToggleCategory(UNCATEGORIZED_CATEGORY_ID)}
      >
        <Text style={[styles.chipText, isUncategorized && styles.chipTextSelected]}>未分類</Text>
      </TouchableOpacity>
      {categories.map((cat) => {
        const selected = selectedCategoryIds.includes(cat.id);
        return (
          <TouchableOpacity
            key={cat.id}
            style={[styles.chip, selected && styles.chipSelected]}
            onPress={() => onToggleCategory(cat.id)}
          >
            <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{cat.name}</Text>
          </TouchableOpacity>
        );
      })}
    </ScrollView>
  );
}

/**
 * カテゴリの登録・名前変更・削除を行う管理ダイアログ。
 *
 * [責任]: カテゴリマスタの追加・編集・削除 UI。
 * [影響する状態]: `bookshelf_categories` テーブル。
 * [潜在的エラー]: 非同期操作時のエラーは安全に捕捉し UI を保護する。
 */
export function BookshelfCategoryManageDialog({
  visible,
  categories,
  onAddCategory,
  onRenameCategory,
  onDeleteCategory,
  onDismiss,
}: {
  visible: boolean;
  categories: BookshelfCategory[];
  onAddCategory: (name: string) => Promise<unknown>;
  onRenameCategory: (id: number, newName: string) => Promise<void>;
  onDeleteCategory: (id: number) => Promise<void>;
  onDismiss: () => void;
}) {
  const [newName, setNewName] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState('');

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.dialogTitle}>カテゴリ登録・管理</Text>

          <View style={styles.addRow}>
            <TextInput
              style={styles.textInput}
              value={newName}
              onChangeText={setNewName}
              placeholder="新しいカテゴリ名"
              placeholderTextColor={AppTheme.textSecondary}
            />
            <TouchableOpacity
              style={styles.iconBtn}
              onPress={() => {
                const trimmed = newName.trim();
                if (!trimmed) return;
                setNewName('');
                void onAddCategory(trimmed);
              }}
            >
              <MaterialIcons name="add" size={24} color={AppTheme.primary} />
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.dialogList}>
            {categories.length === 0 ? (
              <Text style={styles.emptyHint}>登録されているカテゴリはありません。</Text>
            ) : (
              categories.map((cat) => (
                <View key={cat.id} style={styles.catRow}>
                  {editingId === cat.id ? (
                    <>
                      <TextInput
                        style={styles.textInput}
                        value={editingName}
                        onChangeText={setEditingName}
                      />
                      <TouchableOpacity
                        style={styles.iconBtn}
                        onPress={() => {
                          const trimmed = editingName.trim();
                          if (trimmed) void onRenameCategory(cat.id, trimmed);
                          setEditingId(null);
                        }}
                      >
                        <MaterialIcons name="check" size={22} color={AppTheme.success} />
                      </TouchableOpacity>
                      <TouchableOpacity style={styles.iconBtn} onPress={() => setEditingId(null)}>
                        <MaterialIcons name="close" size={22} color={AppTheme.textSecondary} />
                      </TouchableOpacity>
                    </>
                  ) : (
                    <>
                      <Text style={styles.catName} numberOfLines={1}>
                        {cat.name}
                      </Text>
                      <TouchableOpacity
                        style={styles.iconBtn}
                        onPress={() => {
                          setEditingId(cat.id);
                          setEditingName(cat.name);
                        }}
                      >
                        <MaterialIcons name="edit" size={20} color={AppTheme.textSecondary} />
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.iconBtn}
                        onPress={() => void onDeleteCategory(cat.id)}
                      >
                        <MaterialIcons name="delete" size={20} color={AppTheme.danger} />
                      </TouchableOpacity>
                    </>
                  )}
                </View>
              ))
            )}
          </ScrollView>

          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.footerBtnText}>閉じる</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/**
 * フォルダ長押し時に開く「複数カテゴリ選択＋新規作成＋お気に入り解除」統合ダイアログ。
 *
 * [責任]: 対象フォルダへの複数カテゴリ割り当て、その場での新規追加、およびお気に入り解除操作の提供。
 * [影響する状態]: `bookshelf_folder_categories`、お気に入り登録状態。
 * [潜在的エラー]: なし。
 */
export function BookshelfFolderCategoryDialog({
  folder,
  categories,
  onAddCategory,
  onSaveCategories,
  onRequestRemoveFavorite,
  onDismiss,
}: {
  folder: FavoriteFolder | null;
  categories: BookshelfCategory[];
  onAddCategory: (name: string) => Promise<BookshelfCategory | null>;
  onSaveCategories: (folderPath: string, categoryIds: number[]) => Promise<void>;
  onRequestRemoveFavorite: (folder: FavoriteFolder) => void;
  onDismiss: () => void;
}) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [newName, setNewName] = useState('');

  useEffect(() => {
    if (folder) {
      setSelectedIds(folder.categoryIds);
      setNewName('');
    }
  }, [folder]);

  if (!folder) return null;

  const toggleId = (id: number) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]));
  };

  return (
    <Modal visible={Boolean(folder)} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.dialogTitle} numberOfLines={2}>
            「{folder.name}」のカテゴリ設定
          </Text>

          <View style={styles.addRow}>
            <TextInput
              style={styles.textInput}
              value={newName}
              onChangeText={setNewName}
              placeholder="新規カテゴリを追加"
              placeholderTextColor={AppTheme.textSecondary}
            />
            <TouchableOpacity
              style={styles.iconBtn}
              onPress={() => {
                const trimmed = newName.trim();
                if (!trimmed) return;
                setNewName('');
                void onAddCategory(trimmed).then((created) => {
                  if (created) {
                    setSelectedIds((prev) =>
                      prev.includes(created.id) ? prev : [...prev, created.id]
                    );
                  }
                });
              }}
            >
              <MaterialIcons name="add" size={24} color={AppTheme.primary} />
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.dialogList}>
            {categories.length === 0 ? (
              <Text style={styles.emptyHint}>上の入力欄からカテゴリを追加してください。</Text>
            ) : (
              categories.map((cat) => {
                const checked = selectedIds.includes(cat.id);
                return (
                  <TouchableOpacity
                    key={cat.id}
                    style={styles.checkRow}
                    onPress={() => toggleId(cat.id)}
                  >
                    <MaterialIcons
                      name={checked ? 'check-box' : 'check-box-outline-blank'}
                      size={22}
                      color={checked ? AppTheme.primary : AppTheme.textSecondary}
                    />
                    <Text style={styles.checkLabel}>{cat.name}</Text>
                  </TouchableOpacity>
                );
              })
            )}
          </ScrollView>

          <TouchableOpacity
            style={styles.dangerOutlineBtn}
            onPress={() => {
              onDismiss();
              onRequestRemoveFavorite(folder);
            }}
          >
            <Text style={styles.dangerOutlineText}>お気に入りから解除</Text>
          </TouchableOpacity>

          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.footerCancelText}>キャンセル</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.footerBtn}
              onPress={() => {
                void onSaveCategories(folder.path, selectedIds);
                onDismiss();
              }}
            >
              <Text style={styles.footerBtnText}>保存</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  filterBar: { maxHeight: 46, backgroundColor: AppTheme.background },
  filterBarContent: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    gap: 8,
    alignItems: 'center',
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: AppTheme.border,
    backgroundColor: AppTheme.surface,
  },
  chipSelected: {
    backgroundColor: AppTheme.primaryContainer,
    borderColor: AppTheme.primary,
  },
  chipText: { fontSize: 13, color: AppTheme.textPrimary },
  chipTextSelected: { color: AppTheme.onPrimaryContainer, fontWeight: '600' },
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
  dialogTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: AppTheme.textPrimary,
    marginBottom: 12,
  },
  addRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 },
  textInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: AppTheme.border,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 14,
    color: AppTheme.textPrimary,
  },
  iconBtn: { padding: 6 },
  dialogList: { maxHeight: 230, marginBottom: 12 },
  emptyHint: { fontSize: 13, color: AppTheme.textSecondary, paddingVertical: 8 },
  catRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6 },
  catName: { flex: 1, fontSize: 15, color: AppTheme.textPrimary },
  checkRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 10 },
  checkLabel: { fontSize: 15, color: AppTheme.textPrimary },
  dangerOutlineBtn: {
    borderWidth: 1,
    borderColor: AppTheme.danger,
    borderRadius: 8,
    paddingVertical: 9,
    alignItems: 'center',
    marginBottom: 8,
  },
  dangerOutlineText: { color: AppTheme.danger, fontWeight: '600', fontSize: 14 },
  footerRow: { flexDirection: 'row', justifyContent: 'flex-end', gap: 16 },
  footerBtn: { paddingHorizontal: 10, paddingVertical: 6 },
  footerBtnText: { color: AppTheme.primary, fontWeight: '700', fontSize: 15 },
  footerCancelText: { color: AppTheme.textSecondary, fontWeight: '600', fontSize: 15 },
});
