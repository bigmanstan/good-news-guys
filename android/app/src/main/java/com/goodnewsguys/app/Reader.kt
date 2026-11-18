package com.goodnewsguys.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File

/** One piece of an article, in reading order. */
sealed interface Block {
    data class Para(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Quote(val text: String) : Block
    data class Bullet(val text: String, val number: Int?) : Block
    data class Image(val url: String, val caption: String?) : Block
}

data class Article(val blocks: List<Block>, val words: Int) {
    val minutes: Int get() = maxOf(1, (words + 115) / 230)
}

sealed interface ArticleState {
    data object Loading : ArticleState
    data class Ready(val article: Article) : ArticleState
    /** The site blocks apps, hides the text behind a paywall, or the phone is offline. */
    data object Unavailable : ArticleState
}

/**
 * Reader view. Downloads the publisher's page on the phone, keeps only the article
 * (Readability, the same approach as Firefox Reader View), and caches it so saved
 * stories can be read offline.
 */
class Reader(private val cacheDir: File) {

    suspend fun load(story: Story): Article? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "${story.id}.html")
        val html = if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            file.readText()
        } else {
            download(story.sourceLink)?.also { runCatching { file.writeText(it); trim() } }
        }
        html?.let { parseArticle(it, story.sourceLink, story) }
    }

    private fun download(url: String): String? = runCatching {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15_000)
            .followRedirects(true)
            .maxBodySize(6 * 1024 * 1024)
            .get()
        Readability4JExtended(doc.location(), doc).parse().content
    }.getOrNull()

    /** Keep the newest articles only. */
    private fun trim() {
        val files = cacheDir.listFiles() ?: return
        if (files.size <= KEEP) return
        files.sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
    }

    private companion object {
        const val KEEP = 150
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}

private val JUNK = Regex(
    "^(advertisement|ad|sponsored|subscribe|sign up|read more|related|recommended|share this|share on|follow us|" +
        "click here|getty images|image:|photo:|credit:|listen to|watch:|also read|you may also like|this article|" +
        "the post .* appeared first)",
    RegexOption.IGNORE_CASE,
)

private fun clean(text: String) = text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

private fun isJunk(text: String) = text.length < 2 || (text.length < 90 && JUNK.containsMatchIn(text))

private fun imageUrl(img: Element): String? {
    val base = img.baseUri()
    fun abs(u: String) = runCatching { java.net.URL(java.net.URL(base), u).toString() }.getOrNull()
    val candidates = listOf("src", "data-src", "data-lazy-src", "data-original").map { img.attr(it) } +
        img.attr("srcset").split(",").map { it.trim().substringBefore(" ") }
    return candidates.filter { it.isNotBlank() && !it.startsWith("data:") }.mapNotNull(::abs)
        .firstOrNull { it.startsWith("https://") && !it.substringBefore('?').endsWith(".svg") }
}

private fun sameImage(a: String?, b: String?) =
    a != null && b != null && a.substringBefore('?').substringAfterLast('/') == b.substringBefore('?').substringAfterLast('/')

/** Turn Readability's cleaned HTML into blocks the app can lay out itself. */
fun parseArticle(html: String, url: String, story: Story? = null): Article? {
    val root = Jsoup.parseBodyFragment(html, url).body()
    val out = mutableListOf<Block>()
    val seenImages = mutableSetOf<String>()

    fun addImage(img: Element, caption: String?) {
        val src = imageUrl(img) ?: return
        val w = img.attr("width").toIntOrNull()
        if (w != null && w < 120) return
        if (sameImage(src, story?.image) || !seenImages.add(src.substringBefore('?'))) return
        out += Block.Image(src, caption?.let(::clean)?.takeIf { it.isNotBlank() })
    }

    fun text(el: Element) = clean(el.text())

    fun walk(parent: Element) {
        for (el in parent.children()) {
            when (el.tagName()) {
                "p" -> {
                    el.select("img").forEach { addImage(it, null) }
                    val t = text(el)
                    if (!isJunk(t)) out += Block.Para(t)
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val t = text(el)
                    if (!isJunk(t) && !t.equals(story?.headline, ignoreCase = true)) out += Block.Heading(t)
                }
                "blockquote" -> {
                    val t = text(el)
                    if (!isJunk(t)) out += Block.Quote(t)
                }
                "ul", "ol" -> {
                    val ordered = el.tagName() == "ol"
                    el.children().filter { it.tagName() == "li" }.forEachIndexed { i, li ->
                        val t = text(li)
                        if (!isJunk(t)) out += Block.Bullet(t, if (ordered) i + 1 else null)
                    }
                }
                "figure" -> el.selectFirst("img")?.let { addImage(it, el.selectFirst("figcaption")?.text()) }
                "img" -> addImage(el, null)
                "table", "script", "style", "noscript", "iframe", "form", "button", "nav", "aside", "footer" -> Unit
                else -> {
                    // A wrapper: go inside if it holds blocks, otherwise treat its text as a paragraph.
                    val hasBlocks = el.children().any { it.tagName() in BLOCK_TAGS }
                    if (hasBlocks) walk(el) else {
                        el.select("img").forEach { addImage(it, null) }
                        val t = text(el)
                        if (!isJunk(t)) out += Block.Para(t)
                    }
                }
            }
        }
    }
    walk(root)

    // Drop repeats (some sites print the standfirst twice) and a trailing image with nothing after it.
    val blocks = out.filterIndexed { i, b -> i == 0 || b != out[i - 1] }
    val words = blocks.sumOf { b ->
        when (b) {
            is Block.Para -> b.text.split(' ').size
            is Block.Quote -> b.text.split(' ').size
            is Block.Bullet -> b.text.split(' ').size
            else -> 0
        }
    }
    // Under ~80 words is a teaser, a paywall or a cookie wall, not the story.
    return if (words < 80) null else Article(blocks, words)
}

private val BLOCK_TAGS = setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote", "figure", "img", "header", "main")
