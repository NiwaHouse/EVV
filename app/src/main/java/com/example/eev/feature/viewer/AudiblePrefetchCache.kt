package com.example.eev.feature.viewer

import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import java.util.Collections

/**
 * オーディブル再生の読み上げスクリプト（1 画像分の [AudibleUnit] 列）を保持する LRU キャッシュ。
 *
 * [責任]: 次画像の OCR／翻訳結果を先行生成して保持し、読み上げ待ち時間を短縮する。
 * [影響する状態]: なし（永続化されないメモリ内キャッシュ）。
 * [潜在的エラー]: 画像ファイルが削除された場合はキャッシュ済み内容が古くなるため、
 * 再生停止・フォルダ移動時に [clear] で破棄する運用とする。
 *
 * 複数のコルーチン（メイン／IO）から同時にアクセスされるため [Collections.synchronizedMap] で保護する。
 */
class AudiblePrefetchCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {

    /**
     * アクセス順 LRU マップ。`maxEntries` を超えた最古のエントリは自動的に破棄される。
     */
    private val cache: MutableMap<String, List<AudibleUnit>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, List<AudibleUnit>>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<AudibleUnit>>
                ): Boolean = size > maxEntries
            }
        )

    /**
     * 画像・クロップ位置・モード・言語の組から、キャッシュキーを生成する。
     *
     * [責任]: 設定変更時に別エントリとして扱うための一意キー生成。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし。
     */
    fun buildKey(
        imagePath: String,
        cropPosition: CropPosition,
        mode: AudibleMode,
        sourceLanguage: Language,
        targetLanguage: Language
    ): String = "$imagePath|${cropPosition.name}|${mode.name}|${sourceLanguage.code}|${targetLanguage.code}"

    /**
     * キャッシュ済みスクリプトを取得する。未キャッシュ時は null。
     */
    fun get(key: String): List<AudibleUnit>? = cache[key]

    /**
     * スクリプトを登録する。上限超過時は最も古く参照されたエントリが自動破棄される。
     */
    fun put(key: String, units: List<AudibleUnit>) {
        cache[key] = units
    }

    /**
     * 全エントリを破棄する。再生停止・フォルダ移動時に呼び出す。
     */
    fun clear() {
        cache.clear()
    }

    companion object {
        /** 既定の保持枚数。直前・次画像に加え、ページ送り戻しにも耐える最小限の枚数。 */
        private const val DEFAULT_MAX_ENTRIES = 3
    }
}
