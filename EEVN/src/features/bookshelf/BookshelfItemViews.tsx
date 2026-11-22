import React, { useMemo, useRef, useState } from 'react';
import {
  PanResponder,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Image } from 'expo-image';
import { MaterialIcons } from '@expo/vector-icons';
import {
  FavoriteFolder,
  THUMBNAIL_SIZE_METRICS,
  ThumbnailSize,
} from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';

function formatFolderInfo(folder: FavoriteFolder): string {
  try {
    const d = new Date(folder.lastModified);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}/${mm}/${dd} ・ ${folder.imageCount}枚`;
  } catch {
    return `${folder.imageCount}枚`;
  }
}

/**
 * 本棚用サムネイル＋翻訳DBバッジコンポーネント。
 *
 * [責任]: 永続キャッシュ済みサムネイル画像またはフォルダアイコンと `.trans_image.db` 完了バッジの描画。
 * [影響する状態]: なし。
 * [潜在的エラー]: 画像読み込み失敗時は expo-image のプレースホルダー描画により安全に継続。
 */
export function BookshelfThumbnailIcon({
  thumbPath,
  hasTransDb,
  size,
}: {
  thumbPath: string | null;
  hasTransDb: boolean;
  size: number;
}) {
  return (
    <View style={[styles.thumbWrapper, { width: size, height: size }]}>
      {thumbPath ? (
        <Image
          source={{ uri: `file://${thumbPath}` }}
          style={{ width: size, height: size, borderRadius: 8 }}
          contentFit="cover"
          cachePolicy="memory-disk"
          recyclingKey={thumbPath}
        />
      ) : (
        <View style={[styles.placeholderBox, { width: size, height: size }]}>
          <MaterialIcons name="folder" size={size * 0.65} color={AppTheme.folderTint} />
        </View>
      )}
      {hasTransDb && (
        <View style={styles.transBadge}>
          <MaterialIcons name="check-circle" size={16} color={AppTheme.success} />
        </View>
      )}
    </View>
  );
}

/**
 * ドラッグ＆ドロップ（ハンドル縦ドラッグ）およびタップ・長押し対応の本棚リスト行コンポーネント。
 *
 * [責任]: リスト形式のお気に入りフォルダ表示、カテゴリバッジ、ドラッグによる並べ替えイベント発火。
 * [影響する状態]: カスタム並び順（ドラッグ移動/確定）、カテゴリ選択ダイアログ起動、ビューワ起動。
 * [潜在的エラー]: ジェスチャ計測計算の例外は `try-catch` で捕捉する。
 */
export function BookshelfListRow({
  folder,
  thumbnailSize,
  showItemInfo,
  isReorderEnabled,
  itemPathsOrder,
  onPress,
  onLongPress,
  onDragStateChange,
  onMoveFolder,
  onDragEnd,
}: {
  folder: FavoriteFolder;
  thumbnailSize: ThumbnailSize;
  showItemInfo: boolean;
  isReorderEnabled: boolean;
  itemPathsOrder: string[];
  onPress: () => void;
  onLongPress: () => void;
  onDragStateChange: (draggingPath: string | null) => void;
  onMoveFolder: (fromPath: string, toPath: string) => void;
  onDragEnd: () => void;
}) {
  const [offsetY, setOffsetY] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const rowHeightRef = useRef(74);
  const accumYRef = useRef(0);
  const pathsRef = useRef(itemPathsOrder);
  pathsRef.current = itemPathsOrder;
  const moveRef = useRef(onMoveFolder);
  moveRef.current = onMoveFolder;
  const dragEndRef = useRef(onDragEnd);
  dragEndRef.current = onDragEnd;
  const dragStateRef = useRef(onDragStateChange);
  dragStateRef.current = onDragStateChange;

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => isReorderEnabled,
        onMoveShouldSetPanResponder: () => isReorderEnabled,
        onPanResponderGrant: () => {
          accumYRef.current = 0;
          setIsDragging(true);
          setOffsetY(0);
          dragStateRef.current(folder.path);
        },
        onPanResponderMove: (_evt, gesture) => {
          try {
            const rowH = Math.max(48, rowHeightRef.current);
            const deltaSteps = Math.trunc((gesture.dy - accumYRef.current) / rowH);
            if (deltaSteps !== 0) {
              const paths = pathsRef.current;
              const currentIdx = paths.indexOf(folder.path);
              const targetIdx = Math.max(0, Math.min(paths.length - 1, currentIdx + deltaSteps));
              if (currentIdx >= 0 && targetIdx !== currentIdx) {
                moveRef.current(folder.path, paths[targetIdx]);
                accumYRef.current += (targetIdx - currentIdx) * rowH;
              }
            }
            setOffsetY(gesture.dy - accumYRef.current);
          } catch (e) {
            console.warn('BookshelfListRow drag move error:', e);
          }
        },
        onPanResponderRelease: () => {
          setIsDragging(false);
          setOffsetY(0);
          dragStateRef.current(null);
          dragEndRef.current();
        },
        onPanResponderTerminate: () => {
          setIsDragging(false);
          setOffsetY(0);
          dragStateRef.current(null);
          dragEndRef.current();
        },
      }),
    [folder.path, isReorderEnabled]
  );

  const thumbSize = THUMBNAIL_SIZE_METRICS[thumbnailSize].listSizeDp;

  return (
    <View
      onLayout={(e) => {
        rowHeightRef.current = e.nativeEvent.layout.height;
      }}
      style={[
        styles.listRow,
        isDragging && styles.draggingRow,
        { transform: [{ translateY: offsetY }], zIndex: isDragging ? 20 : 1 },
      ]}
    >
      <TouchableOpacity
        style={styles.rowBody}
        onPress={onPress}
        onLongPress={onLongPress}
        delayLongPress={350}
      >
        <BookshelfThumbnailIcon
          thumbPath={folder.thumbPath}
          hasTransDb={folder.hasTransDb}
          size={thumbSize}
        />
        <View style={styles.rowTextWrap}>
          <Text style={styles.rowTitle} numberOfLines={1}>
            {folder.name}
          </Text>
          {folder.categoryNames.length > 0 && (
            <Text style={styles.categoryTag} numberOfLines={1}>
              {folder.categoryNames.join(' / ')}
            </Text>
          )}
          {showItemInfo && (
            <Text style={styles.infoText} numberOfLines={1}>
              {formatFolderInfo(folder)}
            </Text>
          )}
        </View>
      </TouchableOpacity>
      {isReorderEnabled && (
        <View style={styles.dragHandle} {...panResponder.panHandlers}>
          <MaterialIcons name="drag-handle" size={24} color={AppTheme.textSecondary} />
        </View>
      )}
    </View>
  );
}

/**
 * グリッドカード形式の本棚フォルダ表示コンポーネント。
 *
 * 通常時はタップでビューワ・長押しでカテゴリ選択・右上`×`でお気に入り解除。
 * 並び替えモード時は左右移動／ドラッグハンドルまたはステップ入れ替えボタンで順序を直接変更できる。
 *
 * [責任]: グリッドセルでのお気に入りフォルダ描画と操作受付。
 * [影響する状態]: カスタム順序、お気に入り解除、カテゴリ設定、ビューワ起動。
 * [潜在的エラー]: なし。
 */
export function BookshelfGridCard({
  folder,
  thumbnailSize,
  showItemInfo,
  isReorderMode,
  canMovePrev,
  canMoveNext,
  widthPercent,
  onPress,
  onLongPress,
  onRemovePress,
  onMovePrev,
  onMoveNext,
}: {
  folder: FavoriteFolder;
  thumbnailSize: ThumbnailSize;
  showItemInfo: boolean;
  isReorderMode: boolean;
  canMovePrev: boolean;
  canMoveNext: boolean;
  widthPercent: number;
  onPress: () => void;
  onLongPress: () => void;
  onRemovePress: () => void;
  onMovePrev: () => void;
  onMoveNext: () => void;
}) {
  const thumbSize = THUMBNAIL_SIZE_METRICS[thumbnailSize].gridSizeDp;

  return (
    <View style={[styles.gridCell, { width: `${widthPercent}%` }]}>
      <TouchableOpacity
        style={styles.gridCard}
        onPress={onPress}
        onLongPress={onLongPress}
        delayLongPress={350}
      >
        <BookshelfThumbnailIcon
          thumbPath={folder.thumbPath}
          hasTransDb={folder.hasTransDb}
          size={thumbSize}
        />
        <Text style={styles.gridTitle} numberOfLines={2}>
          {folder.name}
        </Text>
        {folder.categoryNames.length > 0 && (
          <Text style={styles.categoryTagCenter} numberOfLines={1}>
            {folder.categoryNames.join(' / ')}
          </Text>
        )}
        {showItemInfo && (
          <Text style={styles.infoTextCenter} numberOfLines={1}>
            {formatFolderInfo(folder)}
          </Text>
        )}
        {isReorderMode && (
          <View style={styles.gridReorderRow}>
            <TouchableOpacity
              disabled={!canMovePrev}
              onPress={onMovePrev}
              style={[styles.stepBtn, !canMovePrev && styles.stepBtnDisabled]}
            >
              <MaterialIcons name="chevron-left" size={20} color={AppTheme.primary} />
            </TouchableOpacity>
            <TouchableOpacity
              disabled={!canMoveNext}
              onPress={onMoveNext}
              style={[styles.stepBtn, !canMoveNext && styles.stepBtnDisabled]}
            >
              <MaterialIcons name="chevron-right" size={20} color={AppTheme.primary} />
            </TouchableOpacity>
          </View>
        )}
        <TouchableOpacity style={styles.gridRemoveBtn} onPress={onRemovePress}>
          <MaterialIcons name="close" size={14} color={AppTheme.textSecondary} />
        </TouchableOpacity>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  thumbWrapper: { position: 'relative', justifyContent: 'center', alignItems: 'center' },
  placeholderBox: {
    borderRadius: 8,
    backgroundColor: AppTheme.surfaceVariant,
    justifyContent: 'center',
    alignItems: 'center',
  },
  transBadge: {
    position: 'absolute',
    top: -4,
    right: -4,
    backgroundColor: AppTheme.background,
    borderRadius: 10,
  },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: AppTheme.background,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: AppTheme.border,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  draggingRow: {
    backgroundColor: AppTheme.primaryContainer,
    elevation: 6,
  },
  rowBody: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 14 },
  rowTextWrap: { flex: 1 },
  rowTitle: { fontSize: 15, fontWeight: '500', color: AppTheme.textPrimary },
  categoryTag: { fontSize: 11, color: AppTheme.primary, marginTop: 2 },
  categoryTagCenter: { fontSize: 11, color: AppTheme.primary, marginTop: 2, textAlign: 'center' },
  infoText: { fontSize: 12, color: AppTheme.textSecondary, marginTop: 2 },
  infoTextCenter: { fontSize: 11, color: AppTheme.textSecondary, marginTop: 2, textAlign: 'center' },
  dragHandle: { padding: 8, justifyContent: 'center', alignItems: 'center' },
  gridCell: { padding: 5 },
  gridCard: {
    backgroundColor: AppTheme.surfaceVariant,
    borderRadius: 10,
    padding: 10,
    alignItems: 'center',
    position: 'relative',
  },
  gridTitle: {
    fontSize: 12,
    fontWeight: '500',
    color: AppTheme.textPrimary,
    textAlign: 'center',
    marginTop: 6,
  },
  gridRemoveBtn: {
    position: 'absolute',
    top: 4,
    right: 4,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: 'rgba(255,255,255,0.85)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  gridReorderRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 6,
  },
  stepBtn: {
    backgroundColor: AppTheme.background,
    borderRadius: 12,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  stepBtnDisabled: { opacity: 0.3 },
});
