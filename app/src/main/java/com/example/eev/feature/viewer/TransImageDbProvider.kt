package com.example.eev.feature.viewer

import android.util.Log
import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.core.storage.TransDbFile
import java.io.File

/**
 * trans_image（OCR＋翻訳サーバ）が生成する翻訳データベース `.trans_image.db` から、
 * オーディブル再生用の読み上げ台本（[AudibleUnit] 列）を生成するプロバイダ。
 *
 * [責任]: DB ファイルの存在確認、適用可否判定（モード・クロップ位置）、[TransImageDbReader] による本文取得、
 *          [AudibleScriptSplitter] による台本化（断片結合・上限分割）、
 *          および取得元（DB 採用／OCR フォールバック）の切り分けログ出力。
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

    /** ログタグ（[TAG_TRANS_DB] に一元定義）。 */
    private const val TAG = TAG_TRANS_DB

    /**
     * 対象画像に対応する読み上げ台本を DB から生成する。
     *
     * [責任]: DB 存在判定、クロップ位置に応じた適用可否判定、読み取り・分割の統括、取得元の切り分けログ出力。
     * [影響する状態]: なし（ログ出力のみ）。
     * [潜在的エラー]: なし（取得不可時は `null`、DB 適用済みで本文が無い場合は空リスト）。
     *
     * 取得元の切り分けは `adb logcat -s EEV_TRANS_DB`（[TAG]）で確認できる。
     * 画像 1 枚につき「DB 採用」は `Log.i` 1 行、「DB 不使用 → OCR経路」は `Log.d` 1 行を出力する。
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
        if (mode == AudibleMode.OFF) {
            logFallback(file.name, "オーディブルOFF")
            return null
        }

        // ルート直下など親を持たないパスでは DB を参照できないため、OCR 経路へ委譲する。
        val parent = file.parentFile
        if (parent == null) {
            logFallback(file.name, "親フォルダなし（ルート直下）")
            return null
        }
        val dbFile = TransDbFile.of(parent)
        if (!dbFile.isFile) {
            logFallback(file.name, "翻訳DBなし: ${dbFile.absolutePath}")
            return null
        }

        // 漫画モードの左半分はページ全文を読んだ後の表示のため、DB 台本を返さず無音時待機へ委ねる。
        if (cropPosition == CropPosition.LEFT_HALF) {
            Log.d(TAG, "[${file.name}] DB 適用・空台本（漫画モード左半分 → 無音時待機）")
            return emptyList()
        }

        val spokenText = TransImageDbReader.read(dbFile, file.name, mode, targetLanguage.code)
        if (spokenText == null) {
            logFallback(file.name, "DB行なし／本文空（原因は直前の詳細ログを参照）")
            return null
        }
        if (spokenText.isBlank()) {
            logFallback(file.name, "本文が空白のみ")
            return null
        }

        val result = AudibleScriptSplitter.toUnits(spokenText)
        Log.i(
            TAG,
            "[${file.name}] DB採用: mode=${mode.name}, dst=${targetLanguage.code}, " +
                "文字数=${spokenText.length}, 発話数=${result.units.size}" +
                "（DB行数=${result.rawLineCount} → 記号破棄・断片結合後=${result.mergedLineCount}）"
        )
        return result.units
    }

    /**
     * DB を使用せず OCR 経路へ委譲する理由をログ出力する。
     *
     * [責任]: フォールバック理由の可視化（[TAG] による切り分け支援）。
     * [影響する状態]: なし（ログ出力のみ）。
     * [潜在的エラー]: なし。
     *
     * @param imageName 対象画像のファイル名。
     * @param reason フォールバック理由。
     */
    private fun logFallback(imageName: String, reason: String) {
        Log.d(TAG, "[$imageName] DB不使用 → OCR経路: $reason")
    }

}
