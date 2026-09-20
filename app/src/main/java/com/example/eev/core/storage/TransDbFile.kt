package com.example.eev.core.storage

import java.io.File

/**
 * trans_image（外部 OCR サーバ）が生成する翻訳 DB ファイルの定義を一元管理するユーティリティ。
 *
 * [責任]: 翻訳 DB のファイル名の唯一の定義と、対象フォルダからの解決。
 * [影響する状態]: なし（純粋関数・定数のみ）。
 * [潜在的エラー]: なし（読み取り可否の判定は呼び出し側が行う）。
 *
 * ファイル名は trans_image 側の仕様（`lib/trans/DB仕様書.md`）で定まるため、
 * Core が本定義を保持し、Plugin 側（翻訳 DB リーダー）は本定義を参照する。
 */
object TransDbFile {

    /** trans_image が対象フォルダ直下に生成する翻訳 DB のファイル名。 */
    const val FILE_NAME = ".trans_image.db"

    /**
     * 指定フォルダ直下の翻訳 DB ファイルを返す（存在確認は行わない）。
     *
     * [責任]: フォルダパスから翻訳 DB の File を解決する。
     * [影響する状態]: なし（純粋関数）。
     * [潜在的エラー]: なし。
     *
     * @param folder 対象フォルダ。
     * @return 直下の翻訳 DB を指す File（存在しない場合もある）。
     */
    fun of(folder: File): File = File(folder, FILE_NAME)

    /**
     * 指定フォルダ直下に翻訳 DB が存在するかを判定する。
     *
     * [責任]: 直下 1 階層のみの存在確認。
     * [影響する状態]: なし（純粋関数）。
     * [潜在的エラー]: アクセス権限が無い場合は false を返す。
     *
     * 表示設定（隠しファイル表示）とは独立して判定する。
     *
     * @param folder 対象フォルダ。
     * @return 直下に翻訳 DB ファイルが存在する場合 true。
     */
    fun existsIn(folder: File): Boolean = runCatching { of(folder).isFile }.getOrDefault(false)
}
