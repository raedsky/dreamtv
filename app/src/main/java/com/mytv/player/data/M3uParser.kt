package com.mytv.player.data

import java.io.BufferedReader

data class M3uEntry(val name: String, val logo: String?, val group: String, val url: String)

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        var pendingName: String? = null
        var logo: String? = null
        var group = ""

        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.trim().orEmpty()
                    val lastQuote = line.lastIndexOf('"')
                    val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    pendingName = title.ifEmpty { attrs["tvg-name"].orEmpty() }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    out += M3uEntry(
                        name = pendingName?.takeIf { it.isNotEmpty() } ?: line,
                        logo = logo,
                        group = group.ifEmpty { "Other" },
                        url = line
                    )
                    pendingName = null
                    logo = null
                    group = ""
                }
            }
        }
        return out
    }

    /** تصنيف عناصر M3U حسب شكل الرابط (طريقة سيرفرات Xtream). */
    fun classify(url: String): ContentType {
        val u = url.lowercase()
        return when {
            "/movie/" in u -> ContentType.MOVIE
            "/series/" in u -> ContentType.SERIES
            else -> ContentType.LIVE
        }
    }
}
