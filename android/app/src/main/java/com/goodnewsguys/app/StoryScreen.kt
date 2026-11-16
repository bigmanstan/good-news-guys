package com.goodnewsguys.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private fun openLink(context: Context, url: String) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun share(context: Context, story: Story) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, story.headline)
        .putExtra(Intent.EXTRA_TEXT, "${story.headline}\n${story.sourceLink}")
    runCatching { context.startActivity(Intent.createChooser(send, "Share this good news")) }
}

@Composable
fun StoryScreen(story: Story, ui: UiData, a: Actions) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val cat = categoryOf(story.category)
    val c = p.cat(story.category)
    val related = remember(story, ui.week) {
        ui.week.filter { it.category == story.category && it.id != story.id }.take(3)
    }
    val glass = Color.Black.copy(alpha = 0.36f)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // Hero: the photo, or a wash of the topic colour when there isn't one.
            val base = if (p.dark) lerp(c, Color.Black, 0.45f) else c
            Box(
                Modifier.fillMaxWidth().height(if (story.image != null) 360.dp else 250.dp)
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.5f)))),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Color.White.copy(alpha = 0.10f), size.width * 0.5f, Offset(size.width * 0.9f, size.height * 0.15f))
                    drawCircle(Color.White.copy(alpha = 0.08f), size.width * 0.28f, Offset(size.width * 0.12f, size.height * 0.85f))
                }
                if (story.image != null) {
                    Photo(story.image, Modifier.matchParentSize())
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 0.35f to Color.Transparent),
                        ),
                    )
                } else {
                    Text(cat.emoji, Modifier.align(Alignment.Center).padding(top = 40.dp), fontSize = 84.sp)
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(22.dp))
                StoryChips(story)
                Spacer(Modifier.height(14.dp))
                Headline(story.headline, 30.sp)
                if (story.summary.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(story.summary, color = p.ink.copy(alpha = 0.86f), fontSize = 18.sp, lineHeight = 29.sp)
                }

                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    Fact("Source", story.sourceName, p.tint(c), Modifier.weight(1.4f))
                    Fact("Read", "${story.readTime} min", p.surface2, Modifier.weight(1f))
                    Fact("Good vibes", "${(story.positivity * 100).roundToInt()}%", p.accentSoft, Modifier.weight(1f))
                }

                Spacer(Modifier.height(Gap))
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clip(CircleShape).background(p.accent)
                        .clickable { openLink(context, story.sourceLink) }.padding(start = 26.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Read the full story", Modifier.weight(1f), color = OnAccent,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                    Box(Modifier.size(44.dp).clip(CircleShape).background(OnAccent), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = p.accent, modifier = Modifier.size(22.dp))
                    }
                }

                if (story.alsoCoveredBy.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text("ALSO COVERED BY", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        story.alsoCoveredBy.forEach { other ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface2)
                                    .clickable { openLink(context, other.link) }.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(other.name, Modifier.weight(1f), color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = p.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (related.isNotEmpty()) {
                    SectionTitle("More ${cat.label.lowercase()} news")
                    Spacer(Modifier.height(Gap))
                    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                        related.forEach { WideStoryTile(it, ui, a) }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
            val onPhoto = story.image != null
            CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", a.back, container = glass, tint = Color.White)
            Spacer(Modifier.weight(1f))
            CircleButton(Icons.Filled.Share, "Share", { share(context, story) }, container = glass, tint = Color.White)
            Spacer(Modifier.size(8.dp))
            SaveButton(
                ui.isSaved(story), { a.toggleSave(story) }, container = glass, tint = Color.White,
                activeTint = if (onPhoto) Color(0xFFFFC65C) else Color.White,
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String, color: Color, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(color).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label.uppercase(), color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
        Text(
            value, color = p.ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
