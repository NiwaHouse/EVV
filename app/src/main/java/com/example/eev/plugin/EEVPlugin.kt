package com.example.eev.plugin

import android.content.Context

/**
 * EEV アプリのプラグインモジュールが実装すべき基本インターフェース。
 *
 * [責任]: 各プラグイン（ビューワ、翻訳、サムネイル等）の識別ID取得、初期化、有効化判定。
 * [影響する状態]: プラグインレジストリにおける状態および呼び出し可否。
 * [潜在的エラー]: なし。
 */
interface EEVPlugin {
    val pluginId: String
    val displayName: String
    fun initialize(context: Context)
    fun isEnabled(): Boolean
}
