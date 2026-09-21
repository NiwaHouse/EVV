package com.example.eev.feature.viewer

import android.util.Log
import org.json.JSONArray

/**
 * 簡略モード（`TRANS_IMAGE_RUBY_PLACEHOLDER_READING=1`）で読みに入る固定値。
 *
 * この値は実読みではないため、読み上げには使わず語そのものへフォールバックする。
 */
private const val PLACEHOLDER_READING = "REPLACE"

/**
 * HTML 実体参照の開始文字。
 *
 * 実体参照そのもの（開始文字＋名前＋`;`）をソースへ直書きすると、環境によって復号・欠落する
 * 事故が起きるため、Unicode エスケープで組み立てる。値は半角アンパサンド 1 文字。
 */
private const val AMP = "\u0026"

/**
 * 語と読みから読み上げに使う文字列を選ぶ。
 *
 * [責任]: 実読み（非空かつ [PLACEHOLDER_READING] でない）なら読みを、それ以外は語そのものを返す。
 * [影響する状態]: なし（純粋な関数）。
 * [潜在的エラー]: なし（`null` は空文字として扱う）。
 *
 * @param base 本文の語（`ruby_*` の `base`、またはルビの内側の語）。
 * @param reading 対応する読み（漢字を含まない語は空文字）。
 */
private fun spokenOf(base: String?, reading: String?): String {
    val word = base.orEmpty()
    val read = reading.orEmpty()
    if (read.isBlank() || read.contains(PLACEHOLDER_READING)) return word
    return read
}

/**
 * ルビ列（`ruby_source` / `ruby_translated`）を正として、読み上げ用の素テキストを復元する。
 *
 * [責任]: ルビ JSON を語順どおりに連結し、漢字語は読み（`reading`）へ置換する。
 *          JSON が無い・`[]`・解釈不可の場合はルビ入り HTML からの復元へ委譲する（2 段方式）。
 * [影響する状態]: なし（純粋な変換。DB・UI・キャッシュのいずれにも触れない）。
 * [潜在的エラー]: 例外は投げない。復元できない場合は空文字を返し、呼び出し側が「本文が空」として扱う。
 *
 * DB 仕様は `../lib/trans/DB仕様書.md` §3.1・§8（段階15）に準拠する。
 * スキーマ版は `2` のまま変わらないため、版ではなく内容（ルビ列の有無・本文のルビ有無）で新旧を吸収する。
 */
object RubyTextRestorer {

    /**
     * ルビ JSON を正として読み上げ用テキストを組み立てる。
     *
     * [責任]: ルビ JSON からの復元を試み、不可なら本文（ルビ入り HTML）からの復元へ委譲する。
     * [影響する状態]: なし。
     * [潜在的エラー]: 例外は投げない（復元不可時は空文字）。
     *
     * @param rubyJson `ruby_source` または `ruby_translated` の値（空文字は未計算）。
     * @param body 同じ内容を持つ本文列（`source_text` / `translated_text`。ルビ入り HTML の可能性がある）。
     * @return 読み上げ用の素テキスト。復元できない場合は空文字。
     */
    fun toSpokenText(rubyJson: String, body: String): String {
        val fromJson = fromRubyJson(rubyJson)
        if (fromJson != null) return fromJson
        return HtmlTextRestorer.toPlainText(body)
    }

    /**
     * ルビ JSON の配列から読み上げ用テキストを組み立てる。
     *
     * [責任]: 語順を保って [spokenOf] の結果を連結する。
     * [影響する状態]: なし。
     * [潜在的エラー]: 空文字・`[]`・JSON 解釈例外・連結結果が空白のみの場合は `null`（＝HTML 経路へ委譲）。
     */
    private fun fromRubyJson(rubyJson: String): String? {
        if (rubyJson.isBlank()) return null
        return try {
            val tokens = JSONArray(rubyJson)
            if (tokens.length() == 0) return null
            val builder = StringBuilder()
            for (index in 0 until tokens.length()) {
                val token = tokens.optJSONObject(index) ?: continue
                builder.append(
                    spokenOf(token.optString("base"), token.optString("reading"))
                )
            }
            builder.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG_TRANS_DB, "ルビ JSON を解釈できず本文 HTML へフォールバックします。", e)
            null
        }
    }
}

/**
 * ルビ入り HTML（本文列）から素テキストを復元する。
 *
 * [責任]: ルビ付きの語を読みへ置換し、残るタグを除去し、HTML 実体参照を復号する。
 * [影響する状態]: なし（純粋な変換）。
 * [潜在的エラー]: 例外は投げない（正規表現の置換のみ。解釈できない場合は空文字）。
 *
 * [RubyTextRestorer] のフォールバック専用（`ruby_*` が未計算の行、`[]` の行、解釈不可の行）。
 * DB 仕様書 §8.2（形式）・§8.3（素テキストの取り出し）に対応する。
 */
internal object HtmlTextRestorer {

    /** ルビ付きの語と読み（本文は複数行を含むため改行を跨いで一致させる）。 */
    private val RUBY_TAG = Regex(
        pattern = "<ruby>(.*?)<rt>(.*?)</rt></ruby>",
        option = RegexOption.DOT_MATCHES_ALL
    )

    /** ルビ以外に残り得る HTML タグ。 */
    private val HTML_TAG = Regex("<[^>]+>")

    /**
     * HTML 実体参照（[AMP]＋`#数値`／`#x16進`／名前＋`;`）に一致する正規表現。
     *
     * 本文列は `html.escape(text, quote=True)` 相当で生成されるため、名前付き参照に加えて
     * `&#x27;` のような数値参照も現れる。両方をここで拾う。
     */
    private val HTML_ENTITY = Regex(
        AMP + "(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);"
    )

    /**
     * 名前付き実体参照の対応表（`html.escape(..., quote=True)` が生成し得る集合）。
     *
     * 値側の [AMP] は「開始文字そのもの」を表す。
     */
    private val NAMED_ENTITIES = mapOf(
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "amp" to AMP
    )

    /**
     * ルビ入り HTML を素テキストへ戻す。
     *
     * [責任]: ルビ語の読み採用（[spokenOf]）→ タグ除去 → 実体参照復号の順で処理する。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（入力が空なら空文字）。
     *
     * @param html 本文列の値（ルビ入り HTML、または素テキスト）。
     * @return 読み上げ用の素テキスト。
     */
    fun toPlainText(html: String): String {
        if (html.isBlank()) return ""
        var text = RUBY_TAG.replace(html) { match ->
            spokenOf(match.groupValues[1], match.groupValues[2])
        }
        text = HTML_TAG.replace(text, "")
        return decodeEntities(text)
    }

    /**
     * 実体参照を実文字へ復号する。
     *
     * [責任]: 解釈できる参照だけを置換し、未知の参照は原文のまま残す。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（未知・不正な参照は置換しない）。
     */
    private fun decodeEntities(text: String): String =
        HTML_ENTITY.replace(text) { match ->
            decodeEntity(match.groupValues[1]) ?: match.value
        }

    /**
     * 参照名（`;` を除いた部分）を実文字へ変換する。
     *
     * [責任]: 数値参照（10 進・16 進）を符号位置として復号し、名前付き参照は
     *          [NAMED_ENTITIES] を引く。`&` は他の参照の一部になり得るため、
     *          置換は 1 パスで完結させ再帰的な復号を避ける。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（解釈できない場合は `null` を返し、呼び出し側が原文を残す）。
     */
    private fun decodeEntity(body: String): String? = when {
        body.startsWith("#x", ignoreCase = true) ->
            body.drop(2).toIntOrNull(16)?.let(::codePointToString)
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointToString)
        else -> NAMED_ENTITIES[body]
    }

    /**
     * 符号位置を実文字列へ変換する。
     *
     * [責任]: Unicode の有効範囲のみを受理し、サロゲートペアも 1 文字として扱う。
     * [影響する状態]: なし。
     * [潜在的エラー]: 範囲外・不正値は `null`（＝参照を置換しない）。
     */
    private fun codePointToString(codePoint: Int): String? =
        if (codePoint in 1..0x10FFFF) {
            runCatching { String(Character.toChars(codePoint)) }.getOrNull()
        } else {
            null
        }
}
