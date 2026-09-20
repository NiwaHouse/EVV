package com.example.eev.feature.viewer

/**
 * 台本テキストを行単位で発話単位へ変換するスプリッタ。
 *
 * [責任]: 行分割、記号のみ行の破棄、断片行の結合、TTS 入力上限（[MAX_UTTERANCE_LENGTH]）に応じた再分割。
 * [影響する状態]: なし（純粋関数のみ・副作用なし）。
 * [潜在的エラー]: なし（空文字は空の [SplitResult] を返す）。
 *
 * `.trans_image.db` の本文は trans_image 側 OCR の行単位テキストであり、`'．．．'` や `'、'` `'～'` の
 * ような記号のみ行、`'ノミ／'` のような数文字の断片行を含む。これをそのまま 1 発話ずつ TTS へ渡すと
 * 不自然な細切れ読み上げになるため、行順を維持したまま文単位へまとめ直す。
 *
 * 結合ルール（確定仕様）:
 * 1. 記号・空白のみの行（英数字も文字も含まない行）は破棄する。
 * 2. 「文末記号（`SENTENCE_TERMINATORS`）で終わらない行」または「[MIN_MERGE_LENGTH] 文字未満の行」は
 *    直前の行へ区切り文字を入れずに連結する。
 * 3. 先頭行が断片の場合は直前の行が無いため、次の行の先頭へ連結する。
 */
object AudibleScriptSplitter {

    /** TTS に 1 発話として渡せる最大文字数（`TextToSpeech.getMaxSpeechInputLength()` の既定値に合わせる）。 */
    private const val MAX_UTTERANCE_LENGTH = 4000

    /** 長い段落を再分割する際の文末記号。 */
    private const val SENTENCE_DELIMITERS = "。！？!?"

    /** 文の終端とみなす末尾文字（閉じ括弧・三点リーダを含む）。 */
    private const val SENTENCE_TERMINATORS = "。！？!?…」』"

    /** この文字数未満の行は前後と結合すべき断片として扱う。 */
    private const val MIN_MERGE_LENGTH = 12

    /**
     * 変換結果。
     *
     * @property units 台本。DB 由来のため座標情報を持たず `boundingBox` は常に `null`。
     * @property rawLineCount 入力の非空行数（破棄・結合を適用する前）。切り分けログと検証に用いる。
     * @property mergedLineCount 記号のみ行の破棄と断片結合を適用した後の行数。
     */
    data class SplitResult(
        val units: List<AudibleUnit>,
        val rawLineCount: Int,
        val mergedLineCount: Int
    )

    /**
     * 本文を発話単位の [AudibleUnit] 列へ変換する。
     *
     * [責任]: 改行正規化・行抽出・断片結合・上限分割の統括。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし。
     *
     * @param text DB から取得した読み上げ本文。
     * @return 台本と行数の統計。
     */
    fun toUnits(text: String): SplitResult {
        val lines = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val mergedLines = mergeFragments(lines)
        val units = mergedLines
            .flatMap { splitLongParagraph(it) }
            .map { AudibleUnit(spokenText = it, boundingBox = null) }

        return SplitResult(units = units, rawLineCount = lines.size, mergedLineCount = mergedLines.size)
    }

    /**
     * 断片行の結合と記号のみ行の破棄を行う。
     *
     * [責任]: 行順を維持したまま発話単位を文単位へ近づける。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし。
     *
     * @param lines 改行分割済みの非空行（前後の空白は除去済み）。
     * @return 結合後の行。全行が破棄対象なら空リスト。
     */
    private fun mergeFragments(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()

        // 先頭が断片だった場合の持ち越し。直前の行が無いため次の行の先頭へ連結する。
        var pending = ""

        for (line in lines) {
            // 記号・句読点・空白のみの行（例: '．．．', '、', '～', '／'）は読み上げ対象から除外する。
            if (line.none { it.isLetterOrDigit() }) continue

            val isFragment = line.length < MIN_MERGE_LENGTH || !endsWithSentenceTerminator(line)
            val text = pending + line
            pending = ""

            when {
                !isFragment -> merged += text
                merged.isEmpty() -> pending = text
                else -> merged[merged.lastIndex] = merged.last() + text
            }
        }

        if (pending.isNotEmpty()) merged += pending
        return merged
    }

    /**
     * 行末が文末記号（[SENTENCE_TERMINATORS]）かどうかを判定する。
     *
     * [責任]: 「この行だけで文として完結しているか」の判定。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（空行は `false`）。
     */
    private fun endsWithSentenceTerminator(line: String): Boolean =
        line.isNotEmpty() && SENTENCE_TERMINATORS.contains(line.last())

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
     * 段落を文末記号（[SENTENCE_DELIMITERS]）の直後で区切って文単位へ分解する。
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
