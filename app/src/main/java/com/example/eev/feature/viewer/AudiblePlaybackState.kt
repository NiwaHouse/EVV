package com.example.eev.feature.viewer

import android.graphics.RectF
import com.example.eev.core.model.AudibleMode

/**
 * 読み上げ 1 単位（[com.example.eev.feature.translation.OcrGroupMerger] の結合単位）を表す不変データ。
 *
 * [責任]: 「読み上げる文字列」と「ハイライト描画用の検出領域」の組を保持する。
 * [影響する状態]: なし（純粋なデータ保持のみ）。
 * [潜在的エラー]: [boundingBox] は OCR 失敗時などに null となり得る（描画側でスキップする）。
 */
data class AudibleUnit(
    /** 実際に TTS へ渡す文字列（READ_RAW なら原文、READ_TRANSLATED なら訳文）。 */
    val spokenText: String,
    /** 元画像座標系の検出領域。null の場合はハイライト描画を行わない。 */
    val boundingBox: RectF?
)

/**
 * オーディブル再生の進行状態を表すインメモリ状態。
 *
 * [責任]: 再生中か否か、読み上げ対象の画像・単位、ハイライト領域、待機／モデルDL要求の有無を一元管理する。
 * [影響する状態]: [ViewerUiState] の各 audible 系フィールドへ展開される。
 * [潜在的エラー]: なし（状態保持のみ）。
 */
data class AudiblePlaybackState(
    /** 現在の再生モード。OFF の場合は停止中。 */
    val mode: AudibleMode = AudibleMode.OFF,
    /** 再生セッションが動作中かどうか（OFF 以外で駆動中）。 */
    val isPlaying: Boolean = false,
    /** 読み上げ対象の画像インデックス。未再生時は -1。 */
    val imageIndex: Int = -1,
    /** 読み上げ中の結合単位インデックス。未再生時は -1。 */
    val itemIndex: Int = -1,
    /** 読み上げ中の検出領域（ハイライト枠の描画用）。 */
    val highlightRect: RectF? = null,
    /** 文字が 1 件も検出できず、無音時待機秒数だけ待っている状態かどうか。 */
    val isSilentWaiting: Boolean = false,
    /** 翻訳モデル未取得のため、ユーザー確認ダイアログを表示中かどうか。 */
    val isAwaitingModelDownload: Boolean = false
)
