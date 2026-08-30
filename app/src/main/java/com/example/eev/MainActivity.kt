package com.example.eev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.FileItem
import com.example.eev.core.storage.FileStorageManager
import com.example.eev.core.ui.EEVTheme
import com.example.eev.feature.explorer.ExplorerScreen
import com.example.eev.feature.explorer.ExplorerViewModel
import com.example.eev.feature.viewer.ViewerScreen
import com.example.eev.feature.viewer.ViewerViewModel
import com.example.eev.plugin.PluginRegistry
import com.example.eev.settings.SettingsScreen
import com.example.eev.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 画面状態（Navigation Screen）。
 */
sealed class Screen {
    object Explorer : Screen()
    data class Viewer(val directory: File, val initialFile: File) : Screen()
    object Settings : Screen()
}

/**
 * EEV アプリのメインアクティビティ。
 *
 * [責任]: アプリの初期化、パーミッション要求、画面遷移ルーティング。
 * [影響する状態]: 表示アクティビティおよび画面ルーティング。
 * [潜在的エラー]: パーミッション拒否時のファイル読み込み不可。
 */
class MainActivity : ComponentActivity() {

    private lateinit var dataStore: EEVDataStore
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataStore = EEVDataStore(applicationContext)
        PluginRegistry.instance.initializeAll(applicationContext)
        checkAndRequestPermissions()

        val storageManager = FileStorageManager()
        val ttsEngine = com.example.eev.feature.translation.TtsEngine(applicationContext)
        val explorerViewModel = ExplorerViewModel(storageManager, dataStore)
        val viewerViewModel = ViewerViewModel(storageManager = storageManager, dataStore = dataStore, ttsEngine = ttsEngine)
        val settingsViewModel = SettingsViewModel(dataStore)

        setContent {
            EEVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EEVApp(
                        explorerViewModel = explorerViewModel,
                        viewerViewModel = viewerViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

@Composable
fun EEVApp(
    explorerViewModel: ExplorerViewModel,
    viewerViewModel: ViewerViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Explorer) }

    val explorerUiState by explorerViewModel.uiState.collectAsState()
    val viewerUiState by viewerViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity

    androidx.compose.runtime.SideEffect {
        activity?.window?.let { window ->
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            if (currentScreen is Screen.Viewer) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val storageManager = remember { com.example.eev.core.storage.FileStorageManager() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    when (val screen = currentScreen) {
        is Screen.Explorer -> {
            ExplorerScreen(
                uiState = explorerUiState,
                onFolderClick = { folder ->
                    explorerViewModel.loadDirectory(folder)
                },
                onFileClick = { item ->
                    if (storageManager.isImageFile(item.file)) {
                        viewerViewModel.loadImagesFromDirectory(
                            directory = explorerUiState.currentDirectory,
                            initialFile = item.file,
                            sortOrder = explorerUiState.sortOrder
                        )
                        currentScreen = Screen.Viewer(
                            directory = explorerUiState.currentDirectory,
                            initialFile = item.file
                        )
                    } else {
                        openFileWithExternalApp(context, item.file)
                    }
                },
                onSortChanged = { sortOrder ->
                    explorerViewModel.updateSortOrder(sortOrder)
                },
                onViewModeChanged = { viewMode ->
                    explorerViewModel.updateViewMode(viewMode)
                },
                onSearchQueryChanged = { query ->
                    explorerViewModel.updateSearchQuery(query)
                },
                onToggleSearch = { active ->
                    explorerViewModel.toggleSearchActive(active)
                },
                onUpClick = {
                    explorerViewModel.navigateToParent()
                },
                onQuickLaunchViewer = { launchSortOrder ->
                    coroutineScope.launch {
                        val images = storageManager.getImageFiles(
                            directory = explorerUiState.currentDirectory,
                            sortOrder = launchSortOrder,
                            showHiddenFiles = explorerUiState.showHiddenFiles
                        )
                        val firstImage = images.firstOrNull()
                        if (firstImage != null) {
                            viewerViewModel.updateViewerSortOrder(launchSortOrder)
                            viewerViewModel.loadImagesFromDirectory(
                                directory = explorerUiState.currentDirectory,
                                initialFile = firstImage,
                                sortOrder = launchSortOrder
                            )
                            currentScreen = Screen.Viewer(
                                directory = explorerUiState.currentDirectory,
                                initialFile = firstImage
                            )
                        }
                    }
                },
                onSettingsClick = {
                    currentScreen = Screen.Settings
                }
            )
        }
        is Screen.Viewer -> {
            ViewerScreen(
                uiState = viewerUiState,
                onNextPage = { viewerViewModel.onNextClicked() },
                onPreviousPage = { viewerViewModel.onPreviousClicked() },
                onCloseViewer = { currentScreen = Screen.Explorer },
                onOpenSettingsBottomSheet = { viewerViewModel.toggleBottomSheet(true) },
                onStartTts = { viewerViewModel.startTtsReading() },
                onStopTts = { viewerViewModel.stopTtsReading() },
                onMangaModeChanged = { enabled -> viewerViewModel.updateMangaMode(enabled) },
                onTranslationEnabledChanged = { enabled -> viewerViewModel.updateTranslationEnabled(enabled) },
                onTextColorHexChanged = { hex -> viewerViewModel.updateTextColorHex(hex) },
                onBackgroundColorHexChanged = { hex -> viewerViewModel.updateBackgroundColorHex(hex) },
                onSourceLanguageChanged = { lang -> viewerViewModel.updateSourceLanguage(lang) },
                onTargetLanguageChanged = { lang -> viewerViewModel.updateTargetLanguage(lang) },
                onViewerSortOrderChanged = { sort -> viewerViewModel.updateViewerSortOrder(sort) },
                onDismissBottomSheet = { viewerViewModel.toggleBottomSheet(false) }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                uiState = settingsUiState,
                onMangaModeChanged = { enabled -> settingsViewModel.updateMangaMode(enabled) },
                onTranslationEnabledChanged = { enabled -> settingsViewModel.updateTranslationEnabled(enabled) },
                onShowHiddenFilesChanged = { enabled -> settingsViewModel.updateShowHiddenFiles(enabled) },
                onTextColorHexChanged = { hex -> settingsViewModel.updateTextColorHex(hex) },
                onBackgroundColorHexChanged = { hex -> settingsViewModel.updateBackgroundColorHex(hex) },
                onSourceLanguageChanged = { lang -> settingsViewModel.updateSourceLanguage(lang) },
                onTargetLanguageChanged = { lang -> settingsViewModel.updateTargetLanguage(lang) },
                onBackClick = { currentScreen = Screen.Explorer }
            )
        }
    }
}

private fun openFileWithExternalApp(context: android.content.Context, file: java.io.File) {
    try {
        val extension = file.extension.lowercase()
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "ファイルを開くアプリを選択")
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "対応するアプリが見つかりませんでした", android.widget.Toast.LENGTH_SHORT).show()
    }
}
