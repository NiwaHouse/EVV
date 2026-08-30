package com.example.eev.feature.translation

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * 近接・重複するOCR検出領域をスマートに検出・統合（Union）するユーティリティ。
 *
 * [責任]: フキダシ内の複数テキスト行の自動グループ化と、まとめ翻訳用バウンディングボックスの生成。
 * [影響する状態]: 翻訳処理前の OCRTextLine リスト。
 */
object OcrGroupMerger {

    fun groupAndMergeLines(lines: List<OcrTextLine>): List<OcrTextLine> {
        if (lines.size <= 1) return lines

        val unvisited = lines.toMutableList()
        val mergedGroups = mutableListOf<List<OcrTextLine>>()

        while (unvisited.isNotEmpty()) {
            val currentGroup = mutableListOf<OcrTextLine>()
            val queue = mutableListOf(unvisited.removeAt(0))

            while (queue.isNotEmpty()) {
                val item = queue.removeAt(0)
                currentGroup.add(item)

                val iterator = unvisited.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (shouldMerge(item.boundingBox, candidate.boundingBox)) {
                        queue.add(candidate)
                        iterator.remove()
                    }
                }
            }
            mergedGroups.add(currentGroup)
        }

        return mergedGroups.map { group -> mergeGroupToSingleLine(group) }
    }

    private fun shouldMerge(a: RectF, b: RectF): Boolean {
        if (RectF.intersects(a, b)) return true

        // 垂直方向の距離
        val verticalGap = max(0f, max(a.top - b.bottom, b.top - a.bottom))
        // 水平方向の距離
        val horizontalGap = max(0f, max(a.left - b.right, b.left - a.right))

        val maxGapY = min(a.height(), b.height()) * 0.8f + 25f
        val maxGapX = min(a.width(), b.width()) * 0.8f + 35f

        return verticalGap <= maxGapY && horizontalGap <= maxGapX
    }

    private fun mergeGroupToSingleLine(group: List<OcrTextLine>): OcrTextLine {
        if (group.size == 1) return group[0]

        // 漫画のコマ順（上から下、同じ高さなら右から左）にソート
        val sorted = group.sortedWith { a, b ->
            if (kotlin.math.abs(a.boundingBox.top - b.boundingBox.top) > 30f) {
                a.boundingBox.top.compareTo(b.boundingBox.top)
            } else {
                b.boundingBox.right.compareTo(a.boundingBox.right)
            }
        }

        val combinedText = sorted.joinToString("\n") { it.text.trim() }

        var minLeft = Float.MAX_VALUE
        var minTop = Float.MAX_VALUE
        var maxRight = Float.MIN_VALUE
        var maxBottom = Float.MIN_VALUE

        sorted.forEach { line ->
            minLeft = min(minLeft, line.boundingBox.left)
            minTop = min(minTop, line.boundingBox.top)
            maxRight = max(maxRight, line.boundingBox.right)
            maxBottom = max(maxBottom, line.boundingBox.bottom)
        }

        val unionRect = RectF(minLeft, minTop, maxRight, maxBottom)
        return OcrTextLine(text = combinedText, boundingBox = unionRect)
    }
}
