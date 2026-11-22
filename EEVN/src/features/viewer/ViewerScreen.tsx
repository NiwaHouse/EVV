import React, { useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  PanResponder,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Image, ImageLoadEventData } from 'expo-image';
import { useKeepAwake } from 'expo-keep-awake';
import { MaterialIcons } from '@expo/vector-icons';
import { AppTheme } from '../../core/ui/AppTheme';
import { ViewerBottomSheet, ViewerTripleTapDialog } from './ViewerDialogs';
import { useViewerViewModel } from './useViewerViewModel';

const TRIPLE_TAP_TIMEOUT_MS = 350;

/**
 * フルスクリーン画像ビューワ画面コンポーネント。
 *
 * 画面常時点灯（`useKeepAwake`）、漫画モード（見開き右→左クロップ表示）、
 * タップ次送り／長押し前送り／3連続タップメニュー／右端スワイプ＆設定ボタンによるボトムシートを提供する。
 *
 * [責任]: 高速フルスクリーン画像描画とビューワジェスチャ・ダイアログの統括。
 * [影響する状態]: ページ位置、クロップ位置、設定ボトムシート、トリプルタップダイアログ。
 * [潜在的エラー]: 画像ロード失敗時やサイズ計算異常時は `try-catch` とフォールバックにより継続動作する。
 */
export function ViewerScreen({
  vm,
  onCloseViewer,
}: {
  vm: ReturnType<typeof useViewerViewModel>;
  onCloseViewer: () => void;
}) {
  useKeepAwake();

  const { uiState } = vm;
  const currentPath = uiState.imagePaths[uiState.currentIndex] ?? null;

  const [containerSize, setContainerSize] = useState({ width: 0, height: 0 });
  const [imageDimensions, setImageDimensions] = useState({ width: 0, height: 0 });
  const tapMetaRef = useRef({ count: 0, lastAt: 0 });

  const containerSizeRef = useRef(containerSize);
  containerSizeRef.current = containerSize;
  const openSheetRef = useRef(vm.setBottomSheetVisible);
  openSheetRef.current = vm.setBottomSheetVisible;

  const swipeResponder = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponder: (evt, gesture) => {
          try {
            const w = containerSizeRef.current.width;
            return (
              w > 0 &&
              evt.nativeEvent.locationX > w * 0.55 &&
              gesture.dx < -18 &&
              Math.abs(gesture.dx) > Math.abs(gesture.dy)
            );
          } catch {
            return false;
          }
        },
        onPanResponderRelease: (_evt, gesture) => {
          try {
            if (gesture.dx < -40) {
              openSheetRef.current(true);
            }
          } catch (e) {
            console.warn('Viewer swipe release error:', e);
          }
        },
      }),
    []
  );

  const handleTap = () => {
    try {
      const now = Date.now();
      if (now - tapMetaRef.current.lastAt < TRIPLE_TAP_TIMEOUT_MS) {
        tapMetaRef.current.count += 1;
      } else {
        tapMetaRef.current.count = 1;
      }
      tapMetaRef.current.lastAt = now;

      if (tapMetaRef.current.count >= 3) {
        tapMetaRef.current.count = 0;
        vm.setTripleTapDialogVisible(true);
      } else {
        vm.nextPage();
      }
    } catch (e) {
      console.warn('Viewer tap error:', e);
    }
  };

  const handleImageLoad = (event: ImageLoadEventData) => {
    try {
      const { width, height } = event.source;
      if (width > 0 && height > 0) {
        setImageDimensions({ width, height });
      }
    } catch {
      // 安全に無視
    }
  };

  const renderViewerImage = () => {
    if (!currentPath) {
      if (uiState.isLoading) {
        return <ActivityIndicator size="large" color="#FFFFFF" />;
      }
      return <Text style={styles.emptyText}>画像が見つかりません</Text>;
    }

    const uri = `file://${currentPath}`;
    const canCropHalf =
      uiState.cropPosition !== 'FULL' &&
      imageDimensions.width > 0 &&
      imageDimensions.height > 0 &&
      containerSize.width > 0 &&
      containerSize.height > 0;

    if (!canCropHalf) {
      return (
        <Image
          source={{ uri }}
          style={styles.fullImage}
          contentFit="contain"
          cachePolicy="memory-disk"
          recyclingKey={uri}
          onLoad={handleImageLoad}
        />
      );
    }

    const halfW = imageDimensions.width / 2;
    const scale = Math.min(containerSize.width / halfW, containerSize.height / imageDimensions.height);
    const boxW = Math.max(1, halfW * scale);
    const boxH = Math.max(1, imageDimensions.height * scale);
    const fullW = imageDimensions.width * scale;
    const marginLeft = uiState.cropPosition === 'RIGHT_HALF' ? -boxW : 0;

    return (
      <View style={{ width: boxW, height: boxH, overflow: 'hidden' }}>
        <Image
          source={{ uri }}
          style={{ width: fullW, height: boxH, marginLeft }}
          contentFit="fill"
          cachePolicy="memory-disk"
          recyclingKey={uri}
          onLoad={handleImageLoad}
        />
      </View>
    );
  };

  const folderName = uiState.directoryPath.split('/').pop() || '';
  const pageCounter =
    uiState.imagePaths.length > 0
      ? `${uiState.currentIndex + 1} / ${uiState.imagePaths.length}`
      : '0 / 0';

  return (
    <View
      style={styles.container}
      onLayout={(e) => {
        const { width, height } = e.nativeEvent.layout;
        setContainerSize({ width, height });
      }}
      {...swipeResponder.panHandlers}
    >
      <TouchableOpacity
        activeOpacity={1}
        style={styles.imageTapStage}
        onPress={handleTap}
        onLongPress={() => vm.prevPage()}
        delayLongPress={400}
      >
        {renderViewerImage()}
      </TouchableOpacity>

      <View style={styles.topOverlay} pointerEvents="box-none">
        <TouchableOpacity style={styles.overlayBtn} onPress={onCloseViewer}>
          <MaterialIcons name="arrow-back" size={22} color="#FFFFFF" />
        </TouchableOpacity>

        <View style={styles.titlePill}>
          <Text style={styles.titleText} numberOfLines={1}>
            {folderName} ({pageCounter})
          </Text>
        </View>

        <View style={styles.rightOverlayActions}>
          <TouchableOpacity style={styles.overlayBtn} onPress={vm.toggleFavoriteCurrentFolder}>
            <MaterialIcons
              name={uiState.isCurrentFolderFavorite ? 'star' : 'star-border'}
              size={22}
              color={uiState.isCurrentFolderFavorite ? AppTheme.starActive : '#FFFFFF'}
            />
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.overlayBtn}
            onPress={() => vm.setBottomSheetVisible(true)}
          >
            <MaterialIcons name="tune" size={22} color="#FFFFFF" />
          </TouchableOpacity>
        </View>
      </View>

      <ViewerBottomSheet
        visible={uiState.isBottomSheetVisible}
        viewerSortOrder={uiState.viewerSortOrder}
        isMangaMode={uiState.isMangaMode}
        showHiddenFiles={uiState.showHiddenFiles}
        onViewerSortOrderChanged={vm.updateViewerSortOrder}
        onMangaModeChanged={vm.updateMangaMode}
        onShowHiddenFilesChanged={vm.updateShowHiddenFiles}
        onDismiss={() => vm.setBottomSheetVisible(false)}
      />

      <ViewerTripleTapDialog
        visible={uiState.isTripleTapDialogVisible}
        onCloseViewer={onCloseViewer}
        onNextFolder={vm.navigateToNextDirectory}
        onFirstImage={vm.jumpToFirstImage}
        onDismiss={() => vm.setTripleTapDialogVisible(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: AppTheme.viewerBg },
  imageTapStage: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  fullImage: { width: '100%', height: '100%' },
  emptyText: { color: '#CCCCCC', fontSize: 15 },
  topOverlay: {
    position: 'absolute',
    top: 10,
    left: 12,
    right: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  overlayBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  titlePill: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  titleText: { color: '#FFFFFF', fontSize: 13, textAlign: 'center', fontWeight: '600' },
  rightOverlayActions: { flexDirection: 'row', gap: 8 },
});
