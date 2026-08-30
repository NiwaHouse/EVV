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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.Language
import com.example.eev.feature.translation.TranslationOverlay

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
    onStartTts: () -> Unit,
    onStopTts: () -> Unit,
    onMangaModeChanged: (Boolean) -> Unit,
    onTranslationEnabledChanged: (Boolean) -> Unit,
    onTextColorHexChanged: (String) -> Unit,
    onBackgroundColorHexChanged: (String) -> Unit,
    onSourceLanguageChanged: (Language) -> Unit,
    onTargetLanguageChanged: (Language) -> Unit,
    onViewerSortOrderChanged: (SortOrder) -> Unit,
    onDismissBottomSheet: () -> Unit
) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onNextPage() },
                    onLongPress = { onPreviousPage() },
                    onDoubleTap = { onCloseViewer() }
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

            if (uiState.isTranslationEnabled && uiState.translationOverlayItems.isNotEmpty()) {
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

        // 画面右上コントロールエリア（翻訳 ON/OFF トグル & 音声読み上げ TTS ボタン）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 翻訳 ON/OFF 切り替えトグルボタン
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

            // 音声読み上げ（TTS）再生/停止ボタン
            if (uiState.isTranslationEnabled && uiState.translationOverlayItems.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (uiState.isReadingTts) onStopTts() else onStartTts()
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isReadingTts) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isReadingTts) "読み上げ停止" else "順に読み上げ",
                        tint = if (uiState.isReadingTts) Color.Red else Color.Green,
                        modifier = Modifier.size(32.dp)
                    )
                }
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
                onDismiss = onDismissBottomSheet
            )
        }
    }
}
