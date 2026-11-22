import React from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Image } from 'expo-image';
import { MaterialIcons } from '@expo/vector-icons';
import { FileItem, THUMBNAIL_SIZE_METRICS, ThumbnailSize } from '../../core/models/types';
import { AppTheme } from '../../core/ui/AppTheme';

function formatExplorerItemInfo(item: FileItem): string {
  try {
    const d = new Date(item.lastModified);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    if (item.isDirectory) {
      return `${yyyy}/${mm}/${dd} ・ ${item.imageCount}枚`;
    }
    const kb = Math.max(1, Math.round(item.size / 1024));
    return `${yyyy}/${mm}/${dd} ・ ${kb} KB`;
  } catch {
    return item.isDirectory ? `${item.imageCount}枚` : '';
  }
}

/**
 * フォルダ階層を示すパンくずリストバー。
 *
 * [責任]: 現在パスをルートからの階層セグメントに分解し、任意階層へのワンタップジャンプを提供する。
 * [影響する状態]: エクスプローラの現在表示パス。
 * [潜在的エラー]: なし。
 */
export function BreadcrumbBar({
  rootPath,
  currentPath,
  onNavigateToPath,
}: {
  rootPath: string;
  currentPath: string;
  onNavigateToPath: (path: string) => void;
}) {
  if (!currentPath) return null;

  const segments: { label: string; path: string }[] = [{ label: 'ルート', path: rootPath }];
  if (currentPath.startsWith(rootPath) && currentPath.length > rootPath.length) {
    const rel = currentPath.slice(rootPath.length).replace(/^\/+/, '');
    const parts = rel.split('/').filter(Boolean);
    let acc = rootPath;
    for (const part of parts) {
      acc = `${acc}/${part}`;
      segments.push({ label: part, path: acc });
    }
  } else if (currentPath !== rootPath) {
    segments.push({ label: currentPath.split('/').pop() || currentPath, path: currentPath });
  }

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.breadcrumbBar}
      contentContainerStyle={styles.breadcrumbContent}
    >
      {segments.map((seg, index) => {
        const isLast = index === segments.length - 1;
        return (
          <View key={seg.path} style={styles.breadcrumbSegment}>
            {index > 0 && (
              <MaterialIcons name="chevron-right" size={16} color={AppTheme.textSecondary} />
            )}
            <TouchableOpacity disabled={isLast} onPress={() => onNavigateToPath(seg.path)}>
              <Text style={[styles.breadcrumbText, isLast && styles.breadcrumbActive]}>
                {seg.label}
              </Text>
            </TouchableOpacity>
          </View>
        );
      })}
    </ScrollView>
  );
}

/**
 * エクスプローラ用の永続サムネイル＋種別アイコン＋翻訳DBバッジコンポーネント。
 *
 * [責任]: フォルダの永続サムネイル・画像プレビュー・翻訳DB完了チェックバッジの描画。
 * [影響する状態]: なし。
 * [潜在的エラー]: なし。
 */
export function ExplorerThumbnailIcon({ item, size }: { item: FileItem; size: number }) {
  const imageUri = item.isDirectory
    ? item.thumbPath
      ? `file://${item.thumbPath}`
      : null
    : item.isImage
      ? `file://${item.path}`
      : null;

  return (
    <View style={[styles.thumbBox, { width: size, height: size }]}>
      {imageUri ? (
        <Image
          source={{ uri: imageUri }}
          style={{ width: size, height: size, borderRadius: 8 }}
          contentFit="cover"
          cachePolicy="memory-disk"
          recyclingKey={imageUri}
        />
      ) : (
        <View style={[styles.iconFallback, { width: size, height: size }]}>
          <MaterialIcons
            name={item.isDirectory ? 'folder' : item.isImage ? 'image' : 'insert-drive-file'}
            size={size * 0.65}
            color={item.isDirectory ? AppTheme.folderTint : AppTheme.textSecondary}
          />
        </View>
      )}
      {item.isDirectory && item.hasTransDb && (
        <View style={styles.transBadge}>
          <MaterialIcons name="check-circle" size={16} color={AppTheme.success} />
        </View>
      )}
    </View>
  );
}

/**
 * エクスプローラ項目（リスト行表示）。
 */
export function ExplorerListRow({
  item,
  isFavorite,
  thumbnailSize,
  showItemInfo,
  onPress,
  onLongPress,
}: {
  item: FileItem;
  isFavorite: boolean;
  thumbnailSize: ThumbnailSize;
  showItemInfo: boolean;
  onPress: () => void;
  onLongPress: () => void;
}) {
  const size = THUMBNAIL_SIZE_METRICS[thumbnailSize].listSizeDp;
  return (
    <TouchableOpacity
      style={styles.listRow}
      onPress={onPress}
      onLongPress={onLongPress}
      delayLongPress={350}
    >
      <ExplorerThumbnailIcon item={item} size={size} />
      <View style={styles.listTextArea}>
        <Text style={styles.itemTitle} numberOfLines={1}>
          {item.name}
        </Text>
        {showItemInfo && (
          <Text style={styles.itemSub} numberOfLines={1}>
            {formatExplorerItemInfo(item)}
          </Text>
        )}
      </View>
      {item.isDirectory && isFavorite && (
        <MaterialIcons name="star" size={18} color={AppTheme.starActive} />
      )}
    </TouchableOpacity>
  );
}

/**
 * エクスプローラ項目（グリッドセル表示）。
 */
export function ExplorerGridCell({
  item,
  isFavorite,
  thumbnailSize,
  showItemInfo,
  widthPercent,
  onPress,
  onLongPress,
}: {
  item: FileItem;
  isFavorite: boolean;
  thumbnailSize: ThumbnailSize;
  showItemInfo: boolean;
  widthPercent: number;
  onPress: () => void;
  onLongPress: () => void;
}) {
  const size = THUMBNAIL_SIZE_METRICS[thumbnailSize].gridSizeDp;
  return (
    <View style={[styles.gridWrap, { width: `${widthPercent}%` }]}>
      <TouchableOpacity
        style={styles.gridCard}
        onPress={onPress}
        onLongPress={onLongPress}
        delayLongPress={350}
      >
        <ExplorerThumbnailIcon item={item} size={size} />
        <Text style={styles.gridTitle} numberOfLines={2}>
          {item.name}
        </Text>
        {showItemInfo && (
          <Text style={styles.gridSub} numberOfLines={1}>
            {formatExplorerItemInfo(item)}
          </Text>
        )}
        {item.isDirectory && isFavorite && (
          <View style={styles.gridStar}>
            <MaterialIcons name="star" size={14} color={AppTheme.starActive} />
          </View>
        )}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  breadcrumbBar: { maxHeight: 38, backgroundColor: AppTheme.surface },
  breadcrumbContent: { paddingHorizontal: 12, alignItems: 'center' },
  breadcrumbSegment: { flexDirection: 'row', alignItems: 'center' },
  breadcrumbText: { fontSize: 13, color: AppTheme.primary, paddingHorizontal: 4, paddingVertical: 6 },
  breadcrumbActive: { color: AppTheme.textPrimary, fontWeight: '700' },
  thumbBox: { position: 'relative', justifyContent: 'center', alignItems: 'center' },
  iconFallback: {
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
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: AppTheme.border,
    gap: 14,
  },
  listTextArea: { flex: 1 },
  itemTitle: { fontSize: 15, color: AppTheme.textPrimary, fontWeight: '500' },
  itemSub: { fontSize: 12, color: AppTheme.textSecondary, marginTop: 2 },
  gridWrap: { padding: 5 },
  gridCard: {
    backgroundColor: AppTheme.surfaceVariant,
    borderRadius: 10,
    padding: 10,
    alignItems: 'center',
    position: 'relative',
  },
  gridTitle: {
    fontSize: 12,
    color: AppTheme.textPrimary,
    textAlign: 'center',
    marginTop: 6,
    fontWeight: '500',
  },
  gridSub: { fontSize: 11, color: AppTheme.textSecondary, textAlign: 'center', marginTop: 2 },
  gridStar: { position: 'absolute', top: 6, left: 6 },
});
