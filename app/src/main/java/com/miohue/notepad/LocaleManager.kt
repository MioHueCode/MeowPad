package com.miohue.notepad

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * 应用内语言切换。
 *
 * 背景：本应用 UI 文案全部走 `stringResource` / `getString`，因此只要把 Activity 的
 * base context 换成带目标 locale 的 Configuration，系统就会解析对应 `values-xx/strings.xml`。
 *
 * - Android 13+（API 33）优先调用系统 `LocaleManager.setApplicationLocales`，
 *   这样系统设置里的「应用语言」与本应用内的选择保持一致；
 * - 低版本（本项目 minSdk 26）回退到 `attachBaseContext` 覆盖 Configuration 的方案。
 *
 * 之前跳 `ACTION_APP_LOCALE_SETTINGS` 无效的根因是：文案是硬编码中文，即使系统语言改成
 * English 也没有任何资源参与渲染，所以界面看起来「改了也不生效」。
 */
object LocaleManager {

    const val PREFS = "notepad_prefs"
    const val KEY_LANGUAGE = "app_language"

    /** "system" | "zh-CN" | "en"（与 locales_config.xml 声明的 tag 保持一致） */
    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "system") ?: "system"

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
        applyToSystem(context, language)
    }

    /**
     * Android 13+ 同步给系统（系统设置里的应用语言会跟随）。
     *
     * 注意：locale tag 必须落在 `res/xml/locales_config.xml` 声明的集合内（zh-CN / en），
     * 否则系统会直接忽略本次设置，表现为「选完语言没反应」。
     */
    private fun applyToSystem(context: Context, language: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val lm = context.getSystemService(android.app.LocaleManager::class.java) ?: return
            lm.applicationLocales =
                if (language == "system") android.os.LocaleList.getEmptyLocaleList()
                else android.os.LocaleList(Locale.forLanguageTag(language))
        }
    }

    /** 用当前选择包装 base context；由 [android.app.Activity.attachBaseContext] 调用。 */
    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        if (language == "system") return context
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
