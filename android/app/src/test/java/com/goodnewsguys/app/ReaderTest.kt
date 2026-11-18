package com.goodnewsguys.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderTest {
    private val para = "Volunteers planted three hundred trees along the river on Saturday morning, and the neighbours brought soup. "

    @Test
    fun turnsArticleHtmlIntoBlocks() {
        val html = """
            <div><h2>A green Saturday</h2>
            <p>${para.repeat(4)}</p>
            <p>Advertisement</p>
            <figure><img src="/img/trees.jpg" width="800"><figcaption>New saplings by the water.</figcaption></figure>
            <blockquote><p>It felt like the whole street showed up.</p></blockquote>
            <ul><li>Bring gloves</li><li>Bring water</li></ul>
            <p>${para.repeat(2)}</p></div>
        """
        val a = parseArticle(html, "https://example.com/news/trees")
        assertNotNull(a)
        val blocks = a!!.blocks
        assertEquals(Block.Heading("A green Saturday"), blocks[0])
        assertTrue(blocks.none { it is Block.Para && it.text == "Advertisement" })
        assertEquals(Block.Image("https://example.com/img/trees.jpg", "New saplings by the water."), blocks.first { it is Block.Image })
        assertTrue(blocks.any { it == Block.Quote("It felt like the whole street showed up.") })
        assertEquals(2, blocks.count { it is Block.Bullet })
    }

    @Test
    fun aTeaserIsNotAnArticle() {
        assertNull(parseArticle("<p>Subscribe to keep reading this story.</p>", "https://example.com/x"))
    }

    /** ./gradlew testDebugUnitTest -Pgng.live=true --tests '*ReaderTest*' runs the reader on today's real stories. */
    @Test
    fun readsRealStories() = runBlocking {
        assumeTrue(System.getProperty("gng.live") == "true")
        val dir = File(System.getProperty("gng.digests")!!)
        val digest = dir.listFiles { f -> f.name.endsWith(".json") }!!.maxBy { it.name }.let { parseDigest(it.readText()) }
        val reader = Reader(Files.createTempDirectory("articles").toFile())
        val bySource = digest.stories.groupBy { it.sourceName }.map { it.value.first() }
        var ok = 0
        for (s in bySource) {
            val a = reader.load(s)
            if (a != null) ok++
            println("${if (a != null) "OK  " else "FAIL"} ${a?.words?.toString()?.padStart(5) ?: "     "}w ${a?.blocks?.count { it is Block.Image } ?: 0}img  ${s.sourceName} | ${s.headline.take(60)}")
        }
        println("Reader worked for $ok of ${bySource.size} sources")
    }
}
