package com.goodnewsguys.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the daily digest files straight from the public GitHub repo, the same
 * files the website is built from. Each day is cached on the phone so the app
 * opens instantly and still works offline.
 */
class Repository(private val cacheDir: File) {
    private val base = "https://raw.githubusercontent.com/bigmanstan/good-news-guys/main/data/digests/"

    /** Whatever is already on the phone, newest first. */
    suspend fun cached(): List<Digest> = withContext(Dispatchers.IO) {
        (cacheDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .sortedByDescending { it.name }
            .take(DAYS)
            .mapNotNull { read(it) }
    }

    /** The past week, newest first. Days with no digest are skipped. */
    suspend fun week(): List<Digest> = coroutineScope {
        val today = LocalDate.now(ZoneId.of("America/Toronto"))
        (0 until DAYS).map { back ->
            // Recent days can still change (review approvals), older ones are settled.
            async(Dispatchers.IO) { day(today.minusDays(back.toLong()).toString(), refetch = back <= 1) }
        }.awaitAll().filterNotNull()
    }

    private fun day(date: String, refetch: Boolean): Digest? {
        val file = File(cacheDir, "$date.json")
        if (!refetch && file.exists()) read(file)?.let { return it }
        val text = fetch("$base$date.json")
        if (text != null) {
            runCatching { parseDigest(text) }.getOrNull()?.let {
                runCatching { file.writeText(text) }
                return it
            }
        }
        return if (file.exists()) read(file) else null
    }

    private fun read(file: File): Digest? = runCatching { parseDigest(file.readText()) }.getOrNull()

    private fun fetch(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val DAYS = 7
    }
}
