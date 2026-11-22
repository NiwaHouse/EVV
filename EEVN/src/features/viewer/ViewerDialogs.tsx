import React from 'react';
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
import { SORT_ORDER_LABELS, SortOrder } from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';

const VIEWER_SORT_OPTIONS: SortOrder[] = [
  SortOrder.NAME_ASC,
  SortOrder.NAME_DESC,
  SortOrder.DATE_DESC,
  SortOrder.DATE_ASC,
  SortOrder.CUSTOM,
  SortOrder.CUSTOM_DESC,
];

/**
 * 画像ビューワ下端から表示する設定ボトムシート。
 *
 * ビューワ独自の並び順（「カスタム順 (昇順)」「カスタム順 (降順)」含む）、
 * 漫画モード、隠しファイル表示の切り替えを提供する。
 *
 * [責任]: ビューワ設定 UI の提供。
 * [影響する状態]: `viewerSortOrder`, `isMangaMode`, `showHiddenFiles`。
 * [潜在的エラー]: なし。
 */
export function ViewerBottomSheet({
  visible,
  viewerSortOrder,
  isMangaMode,
  showHiddenFiles,
  onViewerSortOrderChanged,
  onMangaModeChanged,
  onShowHiddenFilesChanged,
  onDismiss,
}: {
  visible: boolean;
  viewerSortOrder: SortOrder;
  isMangaMode: boolean;
  showHiddenFiles: boolean;
  onViewerSortOrderChanged: (order: SortOrder) => void;
  onMangaModeChanged: (enabled: boolean) => void;
  onShowHiddenFilesChanged: (enabled: boolean) => void;
  onDismiss: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onDismiss}>
      <Pressable style={styles.sheetBackdrop} onPress={onDismiss}>
        <Pressable style={styles.sheetContainer} onPress={() => {}}>
          <View style={styles.sheetHandle} />
          <Text style={styles.sheetTitle}>画像ビューワ設定</Text>

          <ScrollView style={styles.sheetScroll}>
            <Text style={styles.sectionLabel}>ビューワの並び順（次のフォルダ選択基準）</Text>
            <View style={styles.sortGrid}>
              {VIEWER_SORT_OPTIONS.map((order) => {
                const selected = order === viewerSortOrder;
                return (
                  <TouchableOpacity
                    key={order}
                    style={[styles.sortRow, selected && styles.sortRowSelected]}
                    onPress={() => onViewerSortOrderChanged(order)}
                  >
                    <MaterialIcons
                      name={selected ? 'radio-button-checked' : 'radio-button-unchecked'}
                      size={20}
                      color={selected ? AppTheme.primary : AppTheme.textSecondary}
                    />
                    <Text style={[styles.sortText, selected && styles.sortTextSelected]}>
                      {SORT_ORDER_LABELS[order]}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            <View style={styles.switchRow}>
              <Text style={styles.switchLabel}>漫画モード（見開き右→左）</Text>
              <Switch value={isMangaMode} onValueChange={onMangaModeChanged} />
            </View>

            <View style={styles.switchRow}>
              <Text style={styles.switchLabel}>隠しファイルを表示</Text>
              <Switch value={showHiddenFiles} onValueChange={onShowHiddenFilesChanged} />
            </View>

            <Text style={styles.phaseNotice}>
              ※ OCR・翻訳・オーディブル読み上げはフェーズ2（trans_image 準拠再設計）で追加予定です。
            </Text>
          </ScrollView>

          <TouchableOpacity style={styles.closeBtn} onPress={onDismiss}>
            <Text style={styles.closeBtnText}>閉じる</Text>
          </TouchableOpacity>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/**
 * 3連続タップ（トリプルタップ）時に表示するクイック操作ダイアログ。
 *
 * [責任]: ビューワ終了・次フォルダ移動・先頭画像ジャンプの選択 UI。
 * [影響する状態]: 画面遷移、現在フォルダ、ページインデックス。
 * [潜在的エラー]: なし。
 */
export function ViewerTripleTapDialog({
  visible,
  onCloseViewer,
  onNextFolder,
  onFirstImage,
  onDismiss,
}: {
  visible: boolean;
  onCloseViewer: () => void;
  onNextFolder: () => void;
  onFirstImage: () => void;
  onDismiss: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.dialogBackdrop} onPress={onDismiss}>
        <Pressable style={styles.dialogBox} onPress={() => {}}>
          <Text style={styles.dialogTitle}>ビューワ操作</Text>

          <TouchableOpacity
            style={styles.menuBtn}
            onPress={() => {
              onDismiss();
              onCloseViewer();
            }}
          >
            <MaterialIcons name="close" size={20} color={AppTheme.textPrimary} />
            <Text style={styles.menuBtnText}>画像ビューワを終了</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.menuBtn}
            onPress={() => {
              onDismiss();
              onNextFolder();
            }}
          >
            <MaterialIcons name="skip-next" size={20} color={AppTheme.primary} />
            <Text style={styles.menuBtnText}>次のフォルダへ移動</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.menuBtn}
            onPress={() => {
              onDismiss();
              onFirstImage();
            }}
          >
            <MaterialIcons name="first-page" size={20} color={AppTheme.textPrimary} />
            <Text style={styles.menuBtnText}>最初の画像に戻る</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.cancelRow} onPress={onDismiss}>
            <Text style={styles.cancelText}>キャンセル</Text>
          </TouchableOpacity>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  sheetBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'flex-end',
  },
  sheetContainer: {
    backgroundColor: AppTheme.background,
    borderTopLeftRadius: 18,
    borderTopRightRadius: 18,
    paddingHorizontal: 20,
    paddingTop: 10,
    paddingBottom: 24,
    maxHeight: '80%',
  },
  sheetHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: AppTheme.border,
    alignSelf: 'center',
    marginBottom: 12,
  },
  sheetTitle: { fontSize: 18, fontWeight: '700', color: AppTheme.textPrimary, marginBottom: 12 },
  sheetScroll: { maxHeight: 420 },
  sectionLabel: { fontSize: 13, fontWeight: '600', color: AppTheme.textSecondary, marginBottom: 8 },
  sortGrid: { gap: 4, marginBottom: 14 },
  sortRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 8,
  },
  sortRowSelected: { backgroundColor: AppTheme.primaryContainer },
  sortText: { fontSize: 14, color: AppTheme.textPrimary },
  sortTextSelected: { color: AppTheme.onPrimaryContainer, fontWeight: '700' },
  switchRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10 },
  switchLabel: { flex: 1, fontSize: 15, color: AppTheme.textPrimary },
  phaseNotice: { fontSize: 12, color: AppTheme.textSecondary, marginTop: 8, lineHeight: 18 },
  closeBtn: { alignSelf: 'flex-end', paddingHorizontal: 14, paddingVertical: 8, marginTop: 8 },
  closeBtnText: { fontSize: 15, fontWeight: '700', color: AppTheme.primary },
  dialogBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  dialogBox: {
    width: '100%',
    maxWidth: 340,
    backgroundColor: AppTheme.background,
    borderRadius: 14,
    padding: 18,
  },
  dialogTitle: { fontSize: 17, fontWeight: '700', color: AppTheme.textPrimary, marginBottom: 10 },
  menuBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
  },
  menuBtnText: { fontSize: 15, color: AppTheme.textPrimary },
  cancelRow: { alignSelf: 'flex-end', paddingTop: 8 },
  cancelText: { fontSize: 14, color: AppTheme.textSecondary, fontWeight: '600' },
});
