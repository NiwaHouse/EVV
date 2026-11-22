package com.example.eev.feature.bookshelf

import com.example.eev.core.model.BookshelfCategory
import com.example.eev.core.model.FavoriteFolder
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.core.model.ViewMode

/**
 * カテゴリ未割り当て（「未分類」）フィルタを表す予約ID。
 */
const val UNCATEGORIZED_CATEGORY_ID = -1L

/**
 * 本棚画面のUI State。
 *
 * [責任]: お気に入りフォルダ一覧、カテゴリ一覧、選択中カテゴリID集合（複数選択AND検索）、ソート順、表示モード、サムネイルサイズ、情報表示、検索状態、ローディング状態の保持。
 * [影響する状態]: 本棚画面のリスト・グリッド描画、カテゴリ絞り込みバー、検索バー、各種設定ダイアログおよびプログレス表示。
 * [潜在的エラー]: なし。
 */
data class BookshelfUiState(
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val categories: List<BookshelfCategory> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val folderSortOrder: SortOrder = SortOrder.NAME_ASC,
    val fileSortOrder: SortOrder = SortOrder.NAME_ASC,
    val viewMode: ViewMode = ViewMode.LIST,
    val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    val showItemInfo: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false
) {
    /**
     * 選択中カテゴリ集合（[selectedCategoryIds]・AND検索）および検索クエリ（[searchQuery]）によるフィルタリング適用後のお気に入りフォルダ一覧。
     *
     * [責任]: カテゴリ複数選択AND絞り込み（「すべて」「未分類」「複数カテゴリすべてに所属」）とフォルダ名部分一致による表示対象絞り込み。
     * [影響する状態]: 本棚画面の一覧表示要素および画像ビューワ起動時の巡回対象フォルダ群。
     * [潜在的エラー]: なし。
     */
    val filteredFavoriteFolders: List<FavoriteFolder>
        get() {
            val byCategory = when {
                selectedCategoryIds.isEmpty() -> favoriteFolders
                selectedCategoryIds.contains(UNCATEGORIZED_CATEGORY_ID) ->
                    favoriteFolders.filter { it.categoryIds.isEmpty() }
                else ->
                    favoriteFolders.filter { it.categoryIds.containsAll(selectedCategoryIds) }
            }
            return if (searchQuery.isBlank()) {
                byCategory
            } else {
                byCategory.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }
}
