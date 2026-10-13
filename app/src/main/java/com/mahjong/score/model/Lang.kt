package com.mahjong.score.model

/** 介面語言 */
enum class AppLang(val code: String) {
    ZH("zh"),
    JA("ja");

    fun t(zh: String, ja: String) = if (this == JA) ja else zh

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}
