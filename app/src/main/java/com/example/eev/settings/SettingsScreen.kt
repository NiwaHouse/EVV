package com.example.eev.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.eev.core.model.Language

val TEXT_COLOR_OPTIONS = listOf(
    "#FFFFFF" to Color.White,
    "#000000" to Color.Black,
    "#FFFF00" to Color.Yellow,
    "#FF0000" to Color.Red,
    "#00FFFF" to Color.Cyan
)

val BG_COLOR_OPTIONS = listOf(
    "#CC000000" to Color(0xCC000000),
    "#CCFFFFFF" to Color(0xCCFFFFFF),
    "#CCFFFF00" to Color(0xCCFFFF00),
    "#00000000" to Color.Transparent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onMangaModeChanged: (Boolean) -> Unit,
    onTranslationEnabledChanged: (Boolean) -> Unit,
    onShowHiddenFilesChanged: (Boolean) -> Unit,
    onTextColorHexChanged: (String) -> Unit,
    onBackgroundColorHexChanged: (String) -> Unit,
    onSourceLanguageChanged: (Language) -> Unit,
    onTargetLanguageChanged: (Language) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アプリ設定") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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
                    checked = uiState.showHiddenFiles,
                    onCheckedChange = onShowHiddenFilesChanged
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                    checked = uiState.isMangaModeEnabled,
                    onCheckedChange = onMangaModeChanged
                )
            }

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
                    checked = uiState.isTranslationEnabled,
                    onCheckedChange = onTranslationEnabledChanged
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LanguageDropdownSelector(
                label = "翻訳元言語（原文）",
                selectedLanguage = uiState.sourceLanguage,
                onLanguageSelected = onSourceLanguageChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            LanguageDropdownSelector(
                label = "翻訳先言語（訳文）",
                selectedLanguage = uiState.targetLanguage,
                onLanguageSelected = onTargetLanguageChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "翻訳文字色",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TEXT_COLOR_OPTIONS.forEach { (hex, color) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (uiState.textColorHex == hex) 3.dp else 1.dp,
                                color = if (uiState.textColorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onTextColorHexChanged(hex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "翻訳背景色",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BG_COLOR_OPTIONS.forEach { (hex, color) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (uiState.backgroundColorHex == hex) 3.dp else 1.dp,
                                color = if (uiState.backgroundColorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
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
fun LanguageDropdownSelector(
    label: String,
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
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
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    }
                )
            }
        }
    }
}
