package com.example.eev.plugin

import android.content.Context

/**
 * プラグインの登録・初期化・一覧管理を行うレジストリクラス。
 *
 * [責任]: 登録済みプラグインの保持、一括初期化、アクティブなプラグインの検索。
 * [影響する状態]: プラグインの動作状態。
 * [潜在的エラー]: 重複したpluginId登録時の警告 log 検出。
 */
class PluginRegistry private constructor() {

    private val plugins = mutableMapOf<String, EEVPlugin>()

    companion object {
        val instance: PluginRegistry by lazy { PluginRegistry() }
    }

    fun registerPlugin(plugin: EEVPlugin) {
        plugins[plugin.pluginId] = plugin
    }

    fun getPlugin(pluginId: String): EEVPlugin? {
        return plugins[pluginId]
    }

    fun getAllPlugins(): List<EEVPlugin> {
        return plugins.values.toList()
    }

    fun initializeAll(context: Context) {
        plugins.values.forEach { plugin ->
            plugin.initialize(context)
        }
    }
}
