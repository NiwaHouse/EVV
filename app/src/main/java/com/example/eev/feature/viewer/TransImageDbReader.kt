package com.example.eev.feature.viewer

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.eev.core.model.AudibleMode
import java.io.File

/**
 * trans_image 翻訳 DB 連携の切り分けログ用タグ。
 *
 * `adb -s <serial> logcat -s EEV_TRANS_DB` で本機能のログだけを抽出できる。
 */
internal const val TAG_TRANS_DB = "EEV_TRANS_DB"

/**
 * trans_image（OCR＋翻訳サーバ）が生成する `.trans_image.db` からの本文読み出しを担当する。
 *
 * [責任]: 読み取り専用オープン、`meta.schema_version` の検証、有効行（`error = ''`）の抽出、
 *          モード別の採用列選択、ルビ（`ruby_*` を正・本文 HTML をフォールバック）からの
 *          読み上げテキスト復元、読み取り失敗・該当行なしの理由ログ出力。
 * [影響する状態]: なし（読み取り専用。DB への書き込みは一切行わない）。
 * [潜在的エラー]: オープン失敗・スキーマ版不一致・`SQLiteException`（列欠落・旧スキーマ等）・
 *                  該当行なしの場合は `null` を返す（呼び出し側がオンデバイス OCR 経路へ委譲する）。
 *
 * DB 仕様は `../lib/trans/DB仕様書.md`（スキーマ版 [SUPPORTED_SCHEMA_VERSION]）に準拠する。
 * 1 画像 = 1 行であり文字座標を持たない。段階15 以降、本文列（`source_text` / `translated_text`）は
 * ルビ入り HTML であり、素テキストは `ruby_*`（JSON）を正として復元する（§3.1・§8）。
 */
object TransImageDbReader {

    /** 本リーダーが解釈できるスキーマ版（`meta.schema_version`）。 */
    private const val SUPPORTED_SCHEMA_VERSION = "2"

    /** `entries` から取得する列（SELECT 順に依存するため変更時はインデックスも更新すること）。 */
    private const val SELECT_COLUMNS =
        "image_name, dst_lang, source_text, translated_text, polished_text, " +
            "ruby_source, ruby_translated"

    /** `SELECT_COLUMNS` 内の `dst_lang` インデックス。 */
    private const val COLUMN_DST_LANG = 1

    /** `SELECT_COLUMNS` 内の `source_text` インデックス。 */
    private const val COLUMN_SOURCE_TEXT = 2

    /** `SELECT_COLUMNS` 内の `translated_text` インデックス。 */
    private const val COLUMN_TRANSLATED_TEXT = 3

    /** `SELECT_COLUMNS` 内の `polished_text` インデックス。 */
    private const val COLUMN_POLISHED_TEXT = 4

    /** `SELECT_COLUMNS` 内の `ruby_source` インデックス（原文ルビ JSON。段階15・§8）。 */
    private const val COLUMN_RUBY_SOURCE = 5

    /** `SELECT_COLUMNS` 内の `ruby_translated` インデックス（訳文ルビ JSON。段階15・§8）。 */
    private const val COLUMN_RUBY_TRANSLATED = 6

    /** フォールバック理由ログに列挙する `dst_lang` の最大件数（ログ肥大化の抑止）。 */
    private const val LOG_ROW_SUMMARY_LIMIT = 3

    /**
     * DB を読み取り専用で開き、対象画像の読み上げ本文を取得する。
     *
     * [責任]: オープン、スキーマ版検証、クエリ実行、クローズの保証。
     * [影響する状態]: なし（読み取り専用・書き込みなし）。
     * [潜在的エラー]: オープン失敗・スキーマ不一致・クエリ例外時は `null` を返す（DB は必ず close する）。
     *
     * @param dbFile 対象フォルダ直下の `.trans_image.db`。
     * @param imageName 対象画像のファイル名（`entries.image_name` と一致する値）。
     * @param mode オーディブル再生モード（採用列の選択に使用）。
     * @param dstLang 翻訳先言語コード（`dst_lang` の優先絞り込みに使用）。
     * @return 読み上げ本文。取得できない場合は `null`。
     */
    fun read(dbFile: File, imageName: String, mode: AudibleMode, dstLang: String): String? {
        val db = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.w(TAG_TRANS_DB, "[$imageName] DB を開けませんでした: ${dbFile.absolutePath}", e)
            return null
        }

        return try {
            if (!hasSupportedSchema(db)) {
                Log.w(
                    TAG_TRANS_DB,
                    "[$imageName] スキーマ版が想定($SUPPORTED_SCHEMA_VERSION)と異なるため DB を使用しません。"
                )
                return null
            }
            querySpokenText(db, imageName, mode, dstLang)
        } catch (e: Exception) {
            Log.w(
                TAG_TRANS_DB,
                "[$imageName] DB の読み取りに失敗しました（列欠落・旧スキーマ等）: ${dbFile.absolutePath}",
                e
            )
            null
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * `meta.schema_version` が本リーダーの想定版と一致するかを検証する。
     *
     * [責任]: 互換性のない DB を読み出して誤った解釈をすることを防ぐ。
     * [影響する状態]: なし。
     * [潜在的エラー]: テーブル不在等の例外時は `false`。
     */
    private fun hasSupportedSchema(db: SQLiteDatabase): Boolean = try {
        db.rawQuery("SELECT value FROM meta WHERE key = 'schema_version'", null).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == SUPPORTED_SCHEMA_VERSION
        }
    } catch (e: Exception) {
        Log.w(TAG_TRANS_DB, "meta テーブルを読めませんでした。", e)
        false
    }

    /**
     * 有効行（`error = ''`）から読み上げ本文を 1 件選択する。
     *
     * [責任]: `dst_lang` が翻訳先言語に一致する行を優先し、無ければ `processed_at` 最新の行を採用する。
     * [影響する状態]: なし。
     * [潜在的エラー]: 該当行が無い場合は `null`（理由を `Log.d` で出力）。
     */
    private fun querySpokenText(
        db: SQLiteDatabase,
        imageName: String,
        mode: AudibleMode,
        dstLang: String
    ): String? {
        db.rawQuery(
            "SELECT $SELECT_COLUMNS FROM entries " +
                "WHERE image_name = ? AND error = '' ORDER BY processed_at DESC",
            arrayOf(imageName)
        ).use { cursor ->
            var fallback: String? = null
            var rowCount = 0
            var emptyTextRows = 0
            val emptyRowDstLangs = StringBuilder()

            while (cursor.moveToNext()) {
                rowCount++
                val rowDstLang = cursor.getString(COLUMN_DST_LANG).orEmpty()
                val spoken = pickSpokenText(
                    mode = mode,
                    sourceText = cursor.getString(COLUMN_SOURCE_TEXT).orEmpty(),
                    translatedText = cursor.getString(COLUMN_TRANSLATED_TEXT).orEmpty(),
                    polishedText = cursor.getString(COLUMN_POLISHED_TEXT).orEmpty(),
                    rubySource = cursor.getString(COLUMN_RUBY_SOURCE).orEmpty(),
                    rubyTranslated = cursor.getString(COLUMN_RUBY_TRANSLATED).orEmpty()
                )
                if (spoken == null) {
                    // 例: READ_TRANSLATED なのに translated_text が空（未翻訳）など、本文を採用できない行。
                    emptyTextRows++
                    if (emptyTextRows <= LOG_ROW_SUMMARY_LIMIT) {
                        if (emptyRowDstLangs.isNotEmpty()) emptyRowDstLangs.append('/')
                        emptyRowDstLangs.append(rowDstLang.ifEmpty { "(空)" })
                    }
                    continue
                }

                if (rowDstLang == dstLang) return spoken
                if (fallback == null) fallback = spoken
            }

            if (fallback == null) {
                val reason = if (rowCount == 0) {
                    "対象行なし（image_name='$imageName' かつ error='' の行が0件）"
                } else {
                    "行はあるが読み上げ本文が空（$emptyTextRows/$rowCount 行が必要な列を持たない / " +
                        "mode=${mode.name}, dst_lang=${emptyRowDstLangs}）"
                }
                Log.d(TAG_TRANS_DB, "[$imageName] DB不使用 → OCR経路: $reason")
            }
            return fallback
        }
    }

    /**
     * モードに応じて実際に読み上げる本文を選択する。
     *
     * [責任]: `READ_RAW` は整形済み原文（プレーンなテキスト）を最優先し、空なら原文のルビから復元する。
     *          `READ_TRANSLATED` は訳文のルビから復元する。ルビの復元は [RubyTextRestorer] に委譲する。
     * [影響する状態]: なし。
     * [潜在的エラー]: 採用できる本文が無い場合は `null`（無効行として扱う）。
     */
    private fun pickSpokenText(
        mode: AudibleMode,
        sourceText: String,
        translatedText: String,
        polishedText: String,
        rubySource: String,
        rubyTranslated: String
    ): String? = when (mode) {
        AudibleMode.OFF -> null
        AudibleMode.READ_RAW -> polishedText.ifBlank {
            RubyTextRestorer.toSpokenText(rubySource, sourceText)
        }
        AudibleMode.READ_TRANSLATED ->
            RubyTextRestorer.toSpokenText(rubyTranslated, translatedText)
    }?.takeIf { it.isNotBlank() }
}
