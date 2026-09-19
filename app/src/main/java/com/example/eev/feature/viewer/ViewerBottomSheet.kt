package com.example.eev.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.core.model.SortOrder
import com.example.eev.settings.BG_COLOR_OPTIONS
import com.example.eev.settings.LanguageDropdownSelector
import com.example.eev.settings.TEXT_COLOR_OPTIONS

/** 無音時待機秒数のスライダー範囲（1.0〜10.0 秒）。 */
private val AUDIBLE_SILENT_WAIT_RANGE = 1.0f..10.0f

/** 0.5 秒刻みを実現するためのステップ数（(10.0-1.0)/0.5 - 1）。 */
private const val AUDIBLE_SILENT_WAIT_STEPS = 17

/** 読み上げ速度のスライダー範囲（0.5〜2.0 倍）。 */
private val AUDIBLE_SPEECH_RATE_RANGE = 0.5f..2.0f

/** 0.1 倍刻みを実現するためのステップ数（(2.0-0.5)/0.1 - 1）。 */
private const val AUDIBLE_SPEECH_RATE_STEPS = 14

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerBottomSheet(
    isMangaMode: Boolean,
    onMangaModeChanged: (Boolean) -> Unit,
    isTranslationEnabled: Boolean,
    onTranslationEnabledChanged: (Boolean) -> Unit,
    showHiddenFiles: Boolean,
    onShowHiddenFilesChanged: (Boolean) -> Unit,
    textColorHex: String,
    onTextColorHexChanged: (String) -> Unit,
    backgroundColorHex: String,
    onBackgroundColorHexChanged: (String) -> Unit,
    sourceLanguage: Language,
    onSourceLanguageChanged: (Language) -> Unit,
    targetLanguage: Language,
    onTargetLanguageChanged: (Language) -> Unit,
    viewerSortOrder: SortOrder,
    onViewerSortOrderChanged: (SortOrder) -> Unit,
    audibleMode: AudibleMode,
    onAudibleModeChanged: (AudibleMode) -> Unit,
    audibleSilentWaitSeconds: Float,
    onAudibleSilentWaitSecondsChanged: (Float) -> Unit,
    audibleSpeechRate: Float,
    onAudibleSpeechRateChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "画像ビューワ設定",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ビューワ独自の並び順。エクスプローラのソート順とは独立して、
            // フォルダ内画像の表示順および「次のフォルダ選択基準」に適用される。
            SortOrderDropdownSelector(
                label = "ビューワの並び順（次のフォルダ選択基準）",
                selectedSortOrder = viewerSortOrder,
                onSortOrderSelected = onViewerSortOrderChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "画像内リアルタイム翻訳",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isTranslationEnabled,
                    onCheckedChange = onTranslationEnabledChanged
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "漫画モード（右→左 見開き表示）",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isMangaMode,
                    onCheckedChange = onMangaModeChanged
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "隠しファイル・フォルダを表示",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = showHiddenFiles,
                    onCheckedChange = onShowHiddenFilesChanged
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // オーディブル再生は 2 トグルで排他選択する。
            // 片方を ON にすると他方の値（OFF）が書き込まれ、自動的に解除される。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "オーディブル再生（原文を読み上げ）",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = audibleMode == AudibleMode.READ_RAW,
                    onCheckedChange = { checked ->
                        onAudibleModeChanged(if (checked) AudibleMode.READ_RAW else AudibleMode.OFF)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "翻訳してからオーディブル再生",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = audibleMode == AudibleMode.READ_TRANSLATED,
                    onCheckedChange = { checked ->
                        onAudibleModeChanged(if (checked) AudibleMode.READ_TRANSLATED else AudibleMode.OFF)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "無音時の待機時間: ${"%.1f".format(audibleSilentWaitSeconds)} 秒",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = audibleSilentWaitSeconds,
                onValueChange = onAudibleSilentWaitSecondsChanged,
                valueRange = AUDIBLE_SILENT_WAIT_RANGE,
                steps = AUDIBLE_SILENT_WAIT_STEPS
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "読み上げ速度: ${"%.1f".format(audibleSpeechRate)} 倍",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = audibleSpeechRate,
                onValueChange = onAudibleSpeechRateChanged,
                valueRange = AUDIBLE_SPEECH_RATE_RANGE,
                steps = AUDIBLE_SPEECH_RATE_STEPS
            )

            Spacer(modifier = Modifier.height(16.dp))

            LanguageDropdownSelector(
                label = "翻訳元言語（原文）",
                selectedLanguage = sourceLanguage,
                onLanguageSelected = onSourceLanguageChanged
            )

            Spacer(modifier = Modifier.height(12.dp))

            LanguageDropdownSelector(
                label = "翻訳先言語（訳文）",
                selectedLanguage = targetLanguage,
                onLanguageSelected = onTargetLanguageChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "翻訳文字色", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TEXT_COLOR_OPTIONS.forEach { (hex, color) ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (textColorHex == hex) 3.dp else 1.dp,
                                color = if (textColorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onTextColorHexChanged(hex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "翻訳背景色", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BG_COLOR_OPTIONS.forEach { (hex, color) ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (backgroundColorHex == hex) 3.dp else 1.dp,
                                color = if (backgroundColorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onBackgroundColorHexChanged(hex) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOrderDropdownSelector(
    label: String,
    selectedSortOrder: SortOrder,
    onSortOrderSelected: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val viewerSortOptions = listOf(
        SortOrder.NAME_ASC,
        SortOrder.NAME_DESC,
        SortOrder.DATE_DESC,
        SortOrder.DATE_ASC
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedSortOrder.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            viewerSortOptions.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.displayName) },
                    onClick = {
                        onSortOrderSelected(order)
                        expanded = false
                    }
                )
            }
        }
    }
}
