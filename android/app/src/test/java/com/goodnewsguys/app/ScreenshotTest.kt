package com.goodnewsguys.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws each screen with the real digest files from data/digests and saves
 * PNGs to app/build/shots, so the design can be checked without a phone.
 * Photos don't load here (no network), so photo tiles show their fallback colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val digests: List<Digest> by lazy {
        File(System.getProperty("gng.digests")!!).listFiles { f -> f.name.endsWith(".json") }!!
            .sortedByDescending { it.name }.map { parseDigest(it.readText()) }
    }

    private fun ui(city: String? = "toronto") = UiData(digests, digests.first().stories.take(2), city)

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            GngTheme(dark = dark) {
                val p = LocalPalette.current
                Surface(Modifier.fillMaxSize(), color = p.bg, contentColor = p.ink, content = content)
            }
        }
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test @Config(qualifiers = "w411dp-h3400dp-420dpi")
    fun homeLight() = shoot("home-light", false) { HomeScreen(ui(), Actions()) }

    @Test @Config(qualifiers = "w411dp-h3400dp-420dpi")
    fun homeDark() = shoot("home-dark", true) { HomeScreen(ui(), Actions()) }

    private val sampleArticle by lazy {
        val p = "Volunteers planted three hundred trees along the river on Saturday morning, and neighbours brought soup and music. "
        parseArticle(
            "<p>${p.repeat(3)}</p><h2>A street that showed up</h2><p>${p.repeat(2)}</p>" +
                "<blockquote>It felt like the whole street came out to help, even the kids.</blockquote>" +
                "<ul><li>Next planting day is in the spring</li><li>Gloves and tools are provided</li></ul><p>${p.repeat(2)}</p>",
            "https://example.com/story",
        )!!
    }

    /** Toronto, near Leslieville. */
    private val here = LatLon(43.665, -79.335)

    @Test @Config(qualifiers = "w411dp-h2200dp-420dpi")
    fun storyLight() = shoot("story-light", false) {
        val s = digests.first().stories.first { it.image == null && it.region == null }
        StoryScreen(s, ui(), Actions(), ArticleState.Ready(sampleArticle))
    }

    @Test @Config(qualifiers = "w411dp-h1500dp-420dpi")
    fun storyDark() = shoot("story-dark", true) {
        val s = digests.first().stories.first { it.image != null }
        StoryScreen(s, ui(), Actions(), ArticleState.Loading)
    }

    @Test @Config(qualifiers = "w411dp-h1500dp-420dpi")
    fun storyUnavailable() = shoot("story-unavailable", false) {
        val s = digests.first().stories.first { it.image == null && it.region != null }
        StoryScreen(s, ui(), Actions(), ArticleState.Unavailable)
    }

    @Test @Config(qualifiers = "w411dp-h3400dp-420dpi")
    fun homeNearMe() = shoot("home-near", false) { HomeScreen(ui().copy(here = here), Actions()) }

    @Test @Config(qualifiers = "w411dp-h2600dp-420dpi")
    fun localNearMe() = shoot("local-near", false) { LocalScreen(ui().copy(here = here), Actions()) }

    @Test @Config(qualifiers = "w411dp-h1600dp-420dpi")
    fun localNoLocation() = shoot("local-city", true) { LocalScreen(ui(), Actions()) }

    @Test @Config(qualifiers = "w411dp-h900dp-420dpi")
    fun cityPicker() = shoot("cities-light", false) { LocalScreen(ui(city = null), Actions()) }

    @Test @Config(qualifiers = "w411dp-h1400dp-420dpi")
    fun topic() = shoot("topic-dark", true) { TopicScreen("community", ui(), Actions()) }
}
