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
    private var explorerViewModel: ExplorerViewModel? = null

    /**
     * 全ファイルアクセス許可前に表示していた初期ディレクトリ（アプリ専用フォルダ）。
     * 権限許可後に外部ストレージへ自動で切り替えるための比較基準として保持する。
     */
    private var lastResolvedDirectory: File? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    /**
     * Android 11 以降の全ファイルアクセス（MANAGE_EXTERNAL_STORAGE）要求ランチャー。
     * 許可されると設定画面から戻るため、再読み込みは onResume 側で行う。
     */
    private val requestAllFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataStore = EEVDataStore(applicationContext)
        PluginRegistry.instance.initializeAll(applicationContext)
        checkAndRequestPermissions()

        val storageManager = FileStorageManager()
        val ttsEngine = com.example.eev.feature.translation.TtsEngine(applicationContext)
        val thumbnailCache = com.example.eev.feature.thumbnail.FolderThumbnailCache(applicationContext)
        // 全ファイルアクセス許可時は外部ストレージ、未許可時は権限不要なアプリ専用フォルダを初期表示する。
        val initialDirectory = resolveInitialDirectory()
        lastResolvedDirectory = initialDirectory
        val explorerViewModel = ExplorerViewModel(storageManager, dataStore, thumbnailCache, initialDirectory)
        this.explorerViewModel = explorerViewModel
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

    /**
     * 全ファイルアクセス許可の設定画面から戻った際に、初期ディレクトリを再解決する。
     *
     * [責任]: 権限許可後に外部ストレージ直下へ自動的に切り替える。
     * [影響する状態]: エクスプローラの表示フォルダ。
     * [潜在的エラー]: 権限未許可のままの場合は何もしない。
     *
     * 起動時はアプリ専用フォルダを表示していたが、ユーザーが設定画面で
     * 全ファイルアクセスを許可した場合、外部ストレージ直下へ移動させる。
     */
    override fun onResume() {
        super.onResume()
        val resolved = resolveInitialDirectory()
        val previous = lastResolvedDirectory
        if (previous != null && resolved.absolutePath != previous.absolutePath) {
            lastResolvedDirectory = resolved
            explorerViewModel?.loadDirectory(resolved, recordHistory = false)
        }
    }

    /**
     * 起動時の初期表示ディレクトリを決定する。
     *
     * [責任]: 権限状態に応じた安全な初期ディレクトリの選択。
     * [影響する状態]: エクスプローラの初期表示フォルダ。
     * [潜在的エラー]: 外部ストレージが読めない場合はアプリ専用フォルダへフォールバックする。
     *
     * Android 11 以降で全ファイルアクセスが許可されていれば外部ストレージ直下、
     * そうでなければ権限不要でアクセス可能なアプリ専用フォルダ（filesDir）を返す。
     */
    private fun resolveInitialDirectory(): File {
        val external = Environment.getExternalStorageDirectory()
        val hasAllFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
        return if (hasAllFilesAccess && external.canRead()) external else filesDir
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
        requestAllFilesAccessIfNeeded()
    }

    /**
     * Android 11 以降で全ファイルアクセス権限（MANAGE_EXTERNAL_STORAGE）を要求する。
     *
     * [責任]: スコープドストレージ制限を超えたフォルダ走査のための権限誘導。
     * [影響する状態]: アプリのストレージアクセス可否。
     * [潜在的エラー]: 設定画面が存在しない端末では例外を握りつぶす。
     *
     * 未許可の場合は設定画面へ遷移する。アプリ専用フォルダ（filesDir）は権限不要で
     * アクセス可能なため、未許可でもアプリ内フォルダの閲覧は可能である。
     */
    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (Environment.isExternalStorageManager()) return
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            requestAllFilesAccessLauncher.launch(intent)
        } catch (e: Exception) {
            // 一部端末では専用設定画面が無いため、全体設定画面へフォールバックする。
            try {
                requestAllFilesAccessLauncher.launch(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                )
            } catch (ignored: Exception) {
                // 設定画面が開けない場合は何もしない（アプリ専用フォルダのみ利用可能）。
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
    val explorerScrollPositions by explorerViewModel.scrollPositions.collectAsState()

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

    when (currentScreen) {
        is Screen.Explorer -> {
            ExplorerScreen(
                uiState = explorerUiState,
                onFolderClick = { folder ->
                    explorerViewModel.loadDirectory(folder)
                },
                onFileClick = { item ->
                    if (storageManager.isImageFile(item.file)) {
                        // エクスプローラの並び順をそのままビューワの起点ソート順として引き継ぐ。
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
                onThumbnailSizeChanged = { size ->
                    explorerViewModel.updateThumbnailSize(size)
                },
                onShowItemInfoChanged = { enabled ->
                    explorerViewModel.updateShowItemInfo(enabled)
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
                onBackClick = {
                    explorerViewModel.navigateBack()
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
                            // ソート順の決定は loadImagesFromDirectory の引数が唯一の真実源。
                            // updateViewerSortOrder は選択されたソート順を次回起動用に永続化するのみ。
                            viewerViewModel.loadImagesFromDirectory(
                                directory = explorerUiState.currentDirectory,
                                initialFile = firstImage,
                                sortOrder = launchSortOrder
                            )
                            viewerViewModel.updateViewerSortOrder(launchSortOrder)
                            currentScreen = Screen.Viewer(
                                directory = explorerUiState.currentDirectory,
                                initialFile = firstImage
                            )
                        }
                    }
                },
                onSettingsClick = {
                    currentScreen = Screen.Settings
                },
                savedScrollPositions = explorerScrollPositions,
                onScrollPositionChanged = { path, index ->
                    explorerViewModel.saveScrollPosition(path, index)
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
                onDismissBottomSheet = { viewerViewModel.toggleBottomSheet(false) },
                onTripleTapNextFolder = { viewerViewModel.navigateToNextDirectoryFromMenu() },
                onTripleTapFirstImage = { viewerViewModel.jumpToFirstImage() }
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
