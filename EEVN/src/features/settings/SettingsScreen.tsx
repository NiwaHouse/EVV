import React, { useEffect, useState } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  ToastAndroid,
  TouchableOpacity,
  View,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { EevStorage } from '../../../modules/eev-storage';
import { AppSettingsStore, DEFAULT_APP_SETTINGS } from '../../core/storage/AppSettingsStore';
import { FolderMetadataDb } from '../../core/storage/FolderMetadataDb';
import { AppTheme } from '../../core/ui/AppTheme';

/**
 * 全体設定画面コンポーネント。
 *
 * [責任]: 漫画モード、隠しファイル表示、通常フォルダ永続キャッシュの一括削除設定 UI。
 * [影響する状態]: AsyncStorage 永続設定、`folder_cache.db`、`filesDir/folder_thumbs`。
 * [潜在的エラー]: キャッシュ削除エラー時は `try-catch` で捕捉し通知のみ行う。
 */
export function SettingsScreen({
  onBackClick,
  onCacheCleared,
}: {
  onBackClick: () => void;
  onCacheCleared: () => void;
}) {
  const [settings, setSettings] = useState(AppSettingsStore.getSnapshot() ?? DEFAULT_APP_SETTINGS);
  const [confirmClearVisible, setConfirmClearVisible] = useState(false);

  useEffect(() => {
    let mounted = true;
    void AppSettingsStore.load().then((s) => {
      if (mounted) setSettings(s);
    });
    const unsub = AppSettingsStore.subscribe((s) => {
      if (mounted) setSettings(s);
    });
    return () => {
      mounted = false;
      unsub();
    };
  }, []);

  const handleClearNormalFolderCache = async () => {
    try {
      await Promise.all([FolderMetadataDb.clearAll(), EevStorage.clearThumbnails('folder_thumbs')]);
      onCacheCleared();
      ToastAndroid.show('通常フォルダのサムネイルキャッシュを削除しました', ToastAndroid.SHORT);
    } catch (e) {
      console.warn('handleClearNormalFolderCache error:', e);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.backBtn} onPress={onBackClick}>
          <MaterialIcons name="arrow-back" size={24} color={AppTheme.textPrimary} />
        </TouchableOpacity>
        <Text style={styles.title}>全体設定</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.sectionTitle}>閲覧・表示設定</Text>

        <View style={styles.settingRow}>
          <View style={styles.settingText}>
            <Text style={styles.settingLabel}>漫画モード（見開き右→左）</Text>
            <Text style={styles.settingDesc}>画像を右半分→左半分の順で表示します</Text>
          </View>
          <Switch
            value={settings.isMangaMode}
            onValueChange={(v) => void AppSettingsStore.setIsMangaMode(v)}
          />
        </View>

        <View style={styles.settingRow}>
          <View style={styles.settingText}>
            <Text style={styles.settingLabel}>隠しファイル・フォルダを表示</Text>
            <Text style={styles.settingDesc}>ドット（.）から始まる項目を表示対象に含めます</Text>
          </View>
          <Switch
            value={settings.showHiddenFiles}
            onValueChange={(v) => void AppSettingsStore.setShowHiddenFiles(v)}
          />
        </View>

        <Text style={styles.sectionTitle}>ストレージ・キャッシュ管理</Text>

        <TouchableOpacity style={styles.actionCard} onPress={() => setConfirmClearVisible(true)}>
          <MaterialIcons name="cleaning-services" size={22} color={AppTheme.primary} />
          <View style={styles.settingText}>
            <Text style={styles.settingLabel}>通常フォルダのサムネイルキャッシュをクリア</Text>
            <Text style={styles.settingDesc}>
              `folder_thumbs` の永続サムネイルとキャッシュDBを再構築対象にします（お気に入り本棚は保持）
            </Text>
          </View>
        </TouchableOpacity>

        <View style={styles.infoCard}>
          <Text style={styles.infoTitle}>フェーズ2 実装予定機能</Text>
          <Text style={styles.infoBody}>
            OCR・翻訳およびオーディブル読み上げ機能は `trans_image` の最新設計に基づき、フェーズ2でゼロベース実装します。
          </Text>
        </View>
      </ScrollView>

      <Modal
        visible={confirmClearVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setConfirmClearVisible(false)}
      >
        <Pressable style={styles.backdrop} onPress={() => setConfirmClearVisible(false)}>
          <Pressable style={styles.dialog} onPress={() => {}}>
            <Text style={styles.dialogTitle}>キャッシュの削除</Text>
            <Text style={styles.dialogBody}>
              通常フォルダ用に保存された永続サムネイル画像とメタデータDBを削除しますか？
              （次回フォルダ閲覧時に自動再生成されます）
            </Text>
            <View style={styles.footerRow}>
              <TouchableOpacity
                style={styles.footerBtn}
                onPress={() => setConfirmClearVisible(false)}
              >
                <Text style={styles.cancelText}>キャンセル</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.footerBtn}
                onPress={() => {
                  setConfirmClearVisible(false);
                  void handleClearNormalFolderCache();
                }}
              >
                <Text style={styles.confirmText}>削除する</Text>
              </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: AppTheme.background },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: AppTheme.border,
  },
  backBtn: { padding: 6 },
  title: { fontSize: 18, fontWeight: '700', color: AppTheme.textPrimary, marginLeft: 8 },
  content: { padding: 16, gap: 12 },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: AppTheme.primary,
    marginTop: 8,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: AppTheme.border,
    gap: 12,
  },
  settingText: { flex: 1 },
  settingLabel: { fontSize: 15, fontWeight: '500', color: AppTheme.textPrimary },
  settingDesc: { fontSize: 12, color: AppTheme.textSecondary, marginTop: 2, lineHeight: 17 },
  actionCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    backgroundColor: AppTheme.surface,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: AppTheme.border,
  },
  infoCard: {
    marginTop: 12,
    padding: 14,
    backgroundColor: AppTheme.surfaceVariant,
    borderRadius: 10,
  },
  infoTitle: { fontSize: 13, fontWeight: '700', color: AppTheme.textPrimary, marginBottom: 4 },
  infoBody: { fontSize: 12, color: AppTheme.textSecondary, lineHeight: 18 },
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
  dialogBody: { fontSize: 14, color: AppTheme.textPrimary, marginBottom: 14, lineHeight: 20 },
  footerRow: { flexDirection: 'row', justifyContent: 'flex-end', gap: 16 },
  footerBtn: { paddingHorizontal: 10, paddingVertical: 6 },
  cancelText: { fontSize: 15, color: AppTheme.textSecondary, fontWeight: '600' },
  confirmText: { fontSize: 15, color: AppTheme.primary, fontWeight: '700' },
});
