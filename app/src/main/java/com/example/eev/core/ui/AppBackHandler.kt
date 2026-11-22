package com.example.eev.core.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.example.eev.Screen

/**
 * アプリ全体の端末の戻るボタン（システムバック操作）を画面状態に応じて一元制御するコンポーザブル。
 *
 * - エクスプローラ画面: 検索バー表示中は検索バーを閉じ、移動履歴がある場合は1つ前のフォルダへ戻る。
 *   履歴がない（初期フォルダ）場合は `BackHandler` を無効化し、OS標準のアプリ終了（バックグラウンド移行）へ委ねる。
 * - 画像ビューワ画面: 設定ボトムシート表示中はボトムシートを閉じ、それ以外は起動元の画面（本棚またはエクスプローラ）へ戻る。
 * - 本棚画面: 検索バー表示中は検索バーを閉じ、それ以外はエクスプローラ画面へ戻る。
 * - 設定画面: 起動元の画面（本棚から開いた場合は本棚画面、エクスプローラから開いた場合はエクスプローラ画面）へ戻る。
 *
 * [責任]: 画面別のシステムバック操作ルーティングおよびランタイム例外からの保護。
 * [影響する状態]: 表示中の画面（[Screen]）、検索バー表示状態、ボトムシート表示状態、エクスプローラの現在フォルダ。
 * [潜在的エラー]: コールバック実行時の予期せぬ例外は `try-catch` で捕捉しログ出力してクラッシュを防止する。
 */
@Composable
fun AppBackHandler(
    currentScreen: Screen,
    isExplorerSearchActive: Boolean,
    canExplorerNavigateBack: Boolean,
    isBookshelfSearchActive: Boolean,
    isViewerBottomSheetVisible: Boolean,
    isViewerFromBookshelf: Boolean,
    isSettingsFromBookshelf: Boolean,
    onCloseExplorerSearch: () -> Unit,
    onExplorerNavigateBack: () -> Unit,
    onCloseBookshelfSearch: () -> Unit,
    onDismissViewerBottomSheet: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit
) {
    val shouldInterceptBack = when (currentScreen) {
        is Screen.Explorer -> isExplorerSearchActive || canExplorerNavigateBack
        is Screen.Viewer,
        is Screen.Bookshelf,
        is Screen.Settings -> true
    }

    BackHandler(enabled = shouldInterceptBack) {
        try {
            when (currentScreen) {
                is Screen.Explorer -> {
                    if (isExplorerSearchActive) {
                        onCloseExplorerSearch()
                    } else if (canExplorerNavigateBack) {
                        onExplorerNavigateBack()
                    }
                }
                is Screen.Viewer -> {
                    if (isViewerBottomSheetVisible) {
                        onDismissViewerBottomSheet()
                    } else {
                        val targetScreen =
                            if (isViewerFromBookshelf) Screen.Bookshelf else Screen.Explorer
                        onNavigateToScreen(targetScreen)
                    }
                }
                is Screen.Bookshelf -> {
                    if (isBookshelfSearchActive) {
                        onCloseBookshelfSearch()
                    } else {
                        onNavigateToScreen(Screen.Explorer)
                    }
                }
                is Screen.Settings -> {
                    val targetScreen =
                        if (isSettingsFromBookshelf) Screen.Bookshelf else Screen.Explorer
                    onNavigateToScreen(targetScreen)
                }
            }
        } catch (e: Exception) {
            Log.e("AppBackHandler", "戻るボタン処理中にエラーが発生しました", e)
        }
    }
}
