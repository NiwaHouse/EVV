import React from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { FileItem } from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';

/**
 * 個別削除の確認ダイアログ。
 *
 * [責任]: 選択ファイル・フォルダ削除時の最終確認 UI。
 * [影響する状態]: 個別削除実行トリガー。
 * [潜在的エラー]: なし。
 */
export function DeleteConfirmDialog({
  item,
  onConfirm,
  onDismiss,
}: {
  item: FileItem | null;
  onConfirm: (item: FileItem) => void;
  onDismiss: () => void;
}) {
  if (!item) return null;
  return (
    <Modal visible={Boolean(item)} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.title}>削除の確認</Text>
          <Text style={styles.bodyText}>
            「{item.name}」{item.isDirectory ? 'フォルダとその中身すべて' : ''}を完全に削除しますか？
          </Text>
          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.cancelBtnText}>キャンセル</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.footerBtn}
              onPress={() => {
                onConfirm(item);
                onDismiss();
              }}
            >
              <Text style={styles.dangerBtnText}>削除する</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/**
 * 本棚未登録フォルダの一括削除（サブフォルダを含まない）確認ダイアログ。
 *
 * [責任]: 削除対象フォルダの件数・フォルダ名一覧・保護ルールをユーザーに提示し承認を得る UI。
 * [影響する状態]: 一括削除処理の実行トリガー。
 * [潜在的エラー]: なし。
 */
export function DeleteNonFavoriteFoldersDialog({
  visible,
  isScanning,
  targets,
  onConfirmDelete,
  onDismiss,
}: {
  visible: boolean;
  isScanning: boolean;
  targets: FileItem[];
  onConfirmDelete: (targets: FileItem[]) => void;
  onDismiss: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.backdrop} onPress={onDismiss}>
        <Pressable style={styles.dialog} onPress={() => {}}>
          <Text style={styles.title}>本棚未登録フォルダの一括削除</Text>
          <Text style={styles.noteText}>
            直下フォルダのうち本棚未登録のみ対象（直下ファイル・本棚登録済み・サブフォルダ含む・本棚子孫含むフォルダは保護）。
          </Text>

          {isScanning ? (
            <Text style={styles.bodyText}>対象フォルダを確認しています...</Text>
          ) : targets.length === 0 ? (
            <Text style={styles.bodyText}>削除対象の未登録フォルダはありません。</Text>
          ) : (
            <>
              <Text style={styles.countText}>削除対象: {targets.length} 件</Text>
              <ScrollView style={styles.targetList}>
                {targets.map((folder) => (
                  <Text key={folder.path} style={styles.targetItem} numberOfLines={1}>
                    ・{folder.name}
                  </Text>
                ))}
              </ScrollView>
            </>
          )}

          <View style={styles.footerRow}>
            <TouchableOpacity style={styles.footerBtn} onPress={onDismiss}>
              <Text style={styles.cancelBtnText}>閉じる</Text>
            </TouchableOpacity>
            {!isScanning && targets.length > 0 && (
              <TouchableOpacity
                style={styles.footerBtn}
                onPress={() => {
                  onConfirmDelete(targets);
                  onDismiss();
                }}
              >
                <Text style={styles.dangerBtnText}>一括削除する</Text>
              </TouchableOpacity>
            )}
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
  bodyText: { fontSize: 14, color: AppTheme.textPrimary, marginBottom: 12, lineHeight: 20 },
  noteText: { fontSize: 12, color: AppTheme.textSecondary, marginBottom: 10, lineHeight: 17 },
  countText: { fontSize: 14, fontWeight: '700', color: AppTheme.danger, marginBottom: 6 },
  targetList: {
    maxHeight: 180,
    backgroundColor: AppTheme.surface,
    borderRadius: 8,
    padding: 8,
    marginBottom: 10,
  },
  targetItem: { fontSize: 13, color: AppTheme.textPrimary, paddingVertical: 3 },
  footerRow: { flexDirection: 'row', justifyContent: 'flex-end', gap: 16, marginTop: 6 },
  footerBtn: { paddingHorizontal: 10, paddingVertical: 6 },
  cancelBtnText: { fontSize: 15, fontWeight: '600', color: AppTheme.textSecondary },
  dangerBtnText: { fontSize: 15, fontWeight: '700', color: AppTheme.danger },
});
