package com.example.eev.feature.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.core.model.SortOrder
import com.example.eev.feature.translation.TranslationOverlay

/**
 * トリプルタップ判定に用いる連続タップの許容間隔（ミリ秒）。
 * この時間内に3回タップされると操作モーダルを表示する。
 */
private const val TRIPLE_TAP_TIMEOUT_MS = 400L

/** 読み上げ中ハイライト枠の色。 */
private val AUDIBLE_HIGHLIGHT_COLOR = Color(0xFF4CAF50)

fun parseHexColor(hexString: String, defaultColor: Color): Color {
    return try {
        val colorInt = android.graphics.Color.parseColor(hexString)
        Color(colorInt)
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun ViewerScreen(
    uiState: ViewerUiState,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onCloseViewer: () -> Unit,
    onOpenSettingsBottomSheet: () -> Unit,
    onToggleAudiblePlayback: () -> Unit,
    onAudibleModeChanged: (AudibleMode) -> Unit,
    onAudibleSilentWaitSecondsChanged: (Float) -> Unit,
    onAudibleSpeechRateChanged: (Float) -> Unit,
    onConfirmAudibleModelDownload: () -> Unit,
    onCancelAudibleModelDownload: () -> Unit,
    onAppBackgrounded: () -> Unit,
    onAppForegrounded: () -> Unit,
    onMangaModeChanged: (Boolean) -> Unit,
    onTranslationEnabledChanged: (Boolean) -> Unit,
    onTextColorHexChanged: (String) -> Unit,
    onBackgroundColorHexChanged: (String) -> Unit,
    onSourceLanguageChanged: (Language) -> Unit,
    onTargetLanguageChanged: (Language) -> Unit,
    onViewerSortOrderChanged: (SortOrder) -> Unit,
    onDismissBottomSheet: () -> Unit,
    onTripleTapNextFolder: () -> Unit,
    onTripleTapFirstImage: () -> Unit
) {
    var showTripleTapDialog by remember { mutableStateOf(false) }
    val currentFile = uiState.imageFiles.getOrNull(uiState.currentIndex)
    val currentBitmap = remember(currentFile?.absolutePath, uiState.cropPosition) {
        currentFile?.let { file ->
            val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@remember null
            when (uiState.cropPosition) {
                CropPosition.FULL -> original
                CropPosition.LEFT_HALF -> {
                    val halfWidth = original.width / 2
                    android.graphics.Bitmap.createBitmap(original, 0, 0, halfWidth, original.height)
                }
                CropPosition.RIGHT_HALF -> {
                    val halfWidth = original.width / 2
                    android.graphics.Bitmap.createBitmap(original, halfWidth, 0, halfWidth, original.height)
                }
            }
        }
    }

    val textColor = remember(uiState.textColorHex) {
        parseHexColor(uiState.textColorHex, Color.White)
    }
    val backgroundColor = remember(uiState.backgroundColorHex) {
        parseHexColor(uiState.backgroundColorHex, Color(0xCC000000))
    }

    // オーディブル再生中のみ画面を常時点灯させる（前面時のみ再生する方針に合わせる）。
    val view = LocalView.current
    DisposableEffect(uiState.isAudiblePlaying) {
        view.keepScreenOn = uiState.isAudiblePlaying
        onDispose { view.keepScreenOn = false }
    }

    // アプリが背景へ回ったら再生を止め、前面復帰時に再開する。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onAppBackgrounded()
                Lifecycle.Event.ON_START -> onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // ダブルタップは無効化し、3連続タップ（トリプルタップ）で操作モーダルを表示する。
                var tapCount = 0
                var lastTapTime = 0L
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < TRIPLE_TAP_TIMEOUT_MS) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = now
                        if (tapCount >= 3) {
                            tapCount = 0
                            showTripleTapDialog = true
                        } else {
                            onNextPage()
                        }
                    },
                    onLongPress = { onPreviousPage() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount < -20f && change.position.x > size.width * 0.6f) {
                            onOpenSettingsBottomSheet()
                        }
                    }
                )
            }
    ) {
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "表示画像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // オーディブル再生中は翻訳テキストを描画せず、読み上げ中の検出領域のみを強調する。
            if (uiState.audibleMode != AudibleMode.OFF) {
                if (uiState.isAudiblePlaying && uiState.audibleHighlightRect != null) {
                    AudibleHighlightOverlay(
                        rect = uiState.audibleHighlightRect,
                        imageWidth = currentBitmap.width.toFloat(),
                        imageHeight = currentBitmap.height.toFloat(),
                        borderColor = AUDIBLE_HIGHLIGHT_COLOR,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (uiState.isTranslationEnabled && uiState.translationOverlayItems.isNotEmpty()) {
                TranslationOverlay(
                    items = uiState.translationOverlayItems,
                    imageWidth = currentBitmap.width.toFloat(),
                    imageHeight = currentBitmap.height.toFloat(),
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Text(
                text = "画像を読み込めませんでした",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (uiState.isTranslating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = Color.White
            )
        }

        // 再生中の枚数バッジ（停止中もモード ON の間は表示する）。
        if (uiState.audibleMode != AudibleMode.OFF) {
            Text(
                text = buildAudibleBadgeText(uiState),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // 画面右上コントロールエリア（翻訳 ON/OFF トグル & オーディブル再生トグル）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onTranslationEnabledChanged(!uiState.isTranslationEnabled) }
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "翻訳ON/OFF",
                    tint = if (uiState.isTranslationEnabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }

            // オーディブル再生の開始／停止トグル（既存の手動読み上げを統合したボタン）。
            IconButton(onClick = onToggleAudiblePlayback) {
                Icon(
                    imageVector = if (uiState.isAudiblePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isAudiblePlaying) "オーディブル再生を停止" else "オーディブル再生を開始",
                    tint = if (uiState.isAudiblePlaying) Color.Red else Color.Green,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = "${uiState.currentIndex + 1} / ${uiState.imageFiles.size}",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        if (showTripleTapDialog) {
            ViewerTripleTapDialog(
                onDismiss = { showTripleTapDialog = false },
                onCloseViewer = {
                    showTripleTapDialog = false
                    onCloseViewer()
                },
                onNextFolder = {
                    showTripleTapDialog = false
                    onTripleTapNextFolder()
                },
                onFirstImage = {
                    showTripleTapDialog = false
                    onTripleTapFirstImage()
                }
            )
        }

        if (uiState.isAudibleModelDownloadRequired) {
            AlertDialog(
                onDismissRequest = onCancelAudibleModelDownload,
                title = { Text("翻訳モデルのダウンロード") },
                text = {
                    Text(
                        "「翻訳してからオーディブル再生」には翻訳モデルが必要です。" +
                            "初回のみダウンロード（ネットワーク通信）を行い、以降はオフラインで再生できます。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = onConfirmAudibleModelDownload) {
                        Text("ダウンロードして開始")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancelAudibleModelDownload) {
                        Text("キャンセル")
                    }
                }
            )
        }

        if (uiState.isBottomSheetVisible) {
            ViewerBottomSheet(
                isMangaMode = uiState.isMangaMode,
                onMangaModeChanged = onMangaModeChanged,
                isTranslationEnabled = uiState.isTranslationEnabled,
                onTranslationEnabledChanged = onTranslationEnabledChanged,
                showHiddenFiles = uiState.showHiddenFiles,
                onShowHiddenFilesChanged = { _ -> },
                textColorHex = uiState.textColorHex,
                onTextColorHexChanged = onTextColorHexChanged,
                backgroundColorHex = uiState.backgroundColorHex,
                onBackgroundColorHexChanged = onBackgroundColorHexChanged,
                sourceLanguage = uiState.sourceLanguage,
                onSourceLanguageChanged = onSourceLanguageChanged,
                targetLanguage = uiState.targetLanguage,
                onTargetLanguageChanged = onTargetLanguageChanged,
                viewerSortOrder = uiState.viewerSortOrder,
                onViewerSortOrderChanged = onViewerSortOrderChanged,
                audibleMode = uiState.audibleMode,
                onAudibleModeChanged = onAudibleModeChanged,
                audibleSilentWaitSeconds = uiState.audibleSilentWaitSeconds,
                onAudibleSilentWaitSecondsChanged = onAudibleSilentWaitSecondsChanged,
                audibleSpeechRate = uiState.audibleSpeechRate,
                onAudibleSpeechRateChanged = onAudibleSpeechRateChanged,
                onDismiss = onDismissBottomSheet
            )
        }
    }
}

/**
 * 再生中の枚数バッジ文言を生成する。
 *
 * [責任]: 再生位置・再生状態・無音待機状態を 1 行で提示する。
 * [影響する状態]: なし（表示文字列の生成のみ）。
 * [潜在的エラー]: なし。
 */
private fun buildAudibleBadgeText(uiState: ViewerUiState): String = buildString {
    append("${uiState.currentIndex + 1} / ${uiState.imageFiles.size}")
    append(if (uiState.isAudiblePlaying) "  再生中" else "  停止中")
    if (uiState.isAudibleSilentWaiting) append("（無音待機）")
}
