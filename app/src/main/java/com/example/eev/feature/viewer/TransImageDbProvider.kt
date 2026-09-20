package com.example.eev.feature.viewer

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.core.storage.TransDbFile
import java.io.File

/**
 * trans_image（OCR＋翻訳サーバ）が生成する翻訳データベース `.trans_image.db` から、
 * オーディブル再生用の読み上げ台本（[AudibleUnit] 列）を生成するプロバイダ。
 *
 * [責任]: DB ファイルの存在確認、読み取り専用オープン、スキーマ版検証、有効行（`error = ''`）の抽出、
 *          モード別の採用列選択、TTS 入力上限に応じた段落分割、および [AudibleUnit] 列への変換。
 * [影響する状態]: なし（永続化状態・UI 状態を一切変更しない。DB への書き込みも行わない）。
 * [潜在的エラー]: DB 不在・スキーマ版不一致・`SQLiteException`・対象行なしの場合は `null` を返し、
 *                  呼び出し側（[AudibleScriptProvider]）がオンデバイス OCR 経路へフォールバックする。
 *
 * DB 仕様は `../lib/trans/DB仕様書.md`（スキーマ版 2）に準拠する。
 * 1 画像 = 1 行であり文字座標を持たないため、生成する [AudibleUnit.boundingBox] は常に `null` となる。
 */
object TransImageDbProvider {

    /** trans_image が対象フォルダ直下に生成する DB ファイル名（Core の [TransDbFile] に一元定義）。 */
    const val DB_FILE_NAME = TransDbFile.FILE_NAME

    /** 本プロバイダが解釈できるスキーマ版（`meta.schema_version`）。 */
    private const val SUPPORTED_SCHEMA_VERSION = "2"

    /**
     * TTS に 1 発話として渡せる最大文字数。
     * `TextToSpeech.getMaxSpeechInputLength()` の既定値（4000）に合わせる。
     */
    private const val MAX_UTTERANCE_LENGTH = 4000

    /** 長い段落を再分割する際の文末記号。 */
    private const val SENTENCE_DELIMITERS = "。！？!?"

    /** ログタグ。 */
    private const val TAG = "EEV_TRANS_DB"

    /** `entries` から取得する列（SELECT 順に依存するため変更時はインデックスも更新すること）。 */
    private const val SELECT_COLUMNS =
        "image_name, dst_lang, source_text, translated_text, polished_text"

    /** `SELECT_COLUMNS` 内の `dst_lang` インデックス。 */
    private const val COLUMN_DST_LANG = 1

    /** `SELECT_COLUMNS` 内の `source_text` インデックス。 */
    private const val COLUMN_SOURCE_TEXT = 2

    /** `SELECT_COLUMNS` 内の `translated_text` インデックス。 */
    private const val COLUMN_TRANSLATED_TEXT = 3

    /** `SELECT_COLUMNS` 内の `polished_text` インデックス。 */
    private const val COLUMN_POLISHED_TEXT = 4

    /**
     * 対象画像に対応する読み上げ台本を DB から生成する。
     *
     * [責任]: DB 存在判定、クロップ位置に応じた適用可否判定、読み取り・分割の統括。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（取得不可時は `null`、DB 適用済みで本文が無い場合は空リスト）。
     *
     * @param file 読み上げ対象の画像ファイル。
     * @param cropPosition 漫画モードの表示位置。`LEFT_HALF` はページ単位 DB の二重読み上げを避けるため空台本を返す。
     * @param mode 現在のオーディブル再生モード。`OFF` の場合は `null`。
     * @param targetLanguage 翻訳先言語（`dst_lang` の絞り込みに使用）。
     * @return 台本の [AudibleUnit] 列。DB が使えない場合は `null`（呼び出し側で OCR へフォールバック）。
     */
    fun provide(
        file: File,
        cropPosition: CropPosition,
        mode: AudibleMode,
        targetLanguage: Language
    ): List<AudibleUnit>? {
        if (mode == AudibleMode.OFF) return null

        // ルート直下など親を持たないパスでは DB を参照できないため、OCR 経路へ委譲する。
        val parent = file.parentFile ?: return null
        val dbFile = TransDbFile.of(parent)
        if (!dbFile.isFile) return null

        // 漫画モードの左半分はページ全文を読んだ後の表示のため、DB 台本を返さず無音時待機へ委ねる。
        if (cropPosition == CropPosition.LEFT_HALF) return emptyList()

        val spokenText = readSpokenText(dbFile, file.name, mode, targetLanguage.code) ?: return null
        if (spokenText.isBlank()) return null

        return splitIntoUnits(spokenText)
    }

    /**
     * DB を読み取り専用で開き、対象画像の読み上げ本文を取得する。
     *
     * [責任]: オープン、スキーマ版検証、クエリ実行、クローズの保証。
     * [影響する状態]: なし（読み取り専用・書き込みなし）。
     * [潜在的エラー]: オープン失敗・スキーマ不一致・クエリ例外時は `null` を返す（DB は必ず close する）。
     */
    private fun readSpokenText(
        dbFile: File,
        imageName: String,
        mode: AudibleMode,
        dstLang: String
    ): String? {
        val db = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.w(TAG, "DB を開けませんでした: ${dbFile.absolutePath}", e)
            return null
        }

        return try {
            if (!hasSupportedSchema(db)) {
                Log.w(TAG, "スキーマ版が想定($SUPPORTED_SCHEMA_VERSION)と異なるため DB を使用しません。")
                return null
            }
            querySpokenText(db, imageName, mode, dstLang)
        } catch (e: Exception) {
            Log.w(TAG, "DB の読み取りに失敗しました: ${dbFile.absolutePath}", e)
            null
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * `meta.schema_version` が本プロバイダの想定版と一致するかを検証する。
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
        Log.w(TAG, "meta テーブルを読めませんでした。", e)
        false
    }

    /**
     * 有効行（`error = ''`）から読み上げ本文を 1 件選択する。
     *
     * [責任]: `dst_lang` が翻訳先言語に一致する行を優先し、無ければ `processed_at` 最新の行を採用する。
     * [影響する状態]: なし。
     * [潜在的エラー]: 該当行が無い場合は `null`。
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
            while (cursor.moveToNext()) {
                val spoken = pickSpokenText(
                    mode = mode,
                    sourceText = cursor.getString(COLUMN_SOURCE_TEXT).orEmpty(),
                    translatedText = cursor.getString(COLUMN_TRANSLATED_TEXT).orEmpty(),
                    polishedText = cursor.getString(COLUMN_POLISHED_TEXT).orEmpty()
                ) ?: continue

                if (cursor.getString(COLUMN_DST_LANG) == dstLang) return spoken
                if (fallback == null) fallback = spoken
            }
            return fallback
        }
    }

    /**
     * モードに応じて実際に読み上げる本文を選択する。
     *
     * [責任]: `READ_RAW` は整形済み原文を優先し、`READ_TRANSLATED` は訳文を採用する。
     * [影響する状態]: なし。
     * [潜在的エラー]: 採用できる本文が無い場合は `null`（無効行として扱う）。
     */
    private fun pickSpokenText(
        mode: AudibleMode,
        sourceText: String,
        translatedText: String,
        polishedText: String
    ): String? = when (mode) {
        AudibleMode.OFF -> null
        AudibleMode.READ_RAW -> polishedText.ifBlank { sourceText }.takeIf { it.isNotBlank() }
        AudibleMode.READ_TRANSLATED -> translatedText.takeIf { it.isNotBlank() }
    }

    /**
     * 本文を改行で段落分割し、TTS の入力上限を超える段落を句点で再分割して台本化する。
     *
     * [責任]: 読み上げ単位の生成（座標情報を持たないため `boundingBox` は `null`）。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（空段落は除外する）。
     */
    private fun splitIntoUnits(text: String): List<AudibleUnit> =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { splitLongParagraph(it) }
            .map { AudibleUnit(spokenText = it, boundingBox = null) }

    /**
     * 1 段落を TTS 入力上限以下に分割する。
     *
     * [責任]: 上限超過段落を句点単位でまとめ直し、単一句が上限を超える場合は文字数で強制分割する。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし。
     */
    private fun splitLongParagraph(paragraph: String): List<String> {
        if (paragraph.length <= MAX_UTTERANCE_LENGTH) return listOf(paragraph)

        val pieces = mutableListOf<String>()
        val builder = StringBuilder()
        for (sentence in splitBySentenceDelimiter(paragraph)) {
            if (builder.isNotEmpty() && builder.length + sentence.length > MAX_UTTERANCE_LENGTH) {
                pieces += builder.toString()
                builder.setLength(0)
            }
            builder.append(sentence)
            while (builder.length > MAX_UTTERANCE_LENGTH) {
                pieces += builder.substring(0, MAX_UTTERANCE_LENGTH)
                builder.delete(0, MAX_UTTERANCE_LENGTH)
            }
        }
        if (builder.isNotEmpty()) pieces += builder.toString()
        return pieces.filter { it.isNotBlank() }
    }

    /**
     * 段落を文末記号（`。！？!?`）の直後で区切って文単位へ分解する。
     *
     * [責任]: 長文再分割のための文境界抽出。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（文末記号が無い場合は段落全体を 1 文として返す）。
     */
    private fun splitBySentenceDelimiter(paragraph: String): List<String> {
        val sentences = mutableListOf<String>()
        val builder = StringBuilder()
        for (ch in paragraph) {
            builder.append(ch)
            if (SENTENCE_DELIMITERS.contains(ch)) {
                sentences += builder.toString()
                builder.setLength(0)
            }
        }
        if (builder.isNotEmpty()) sentences += builder.toString()
        return sentences
    }
}
