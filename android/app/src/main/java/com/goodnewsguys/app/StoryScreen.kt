package com.goodnewsguys.app

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private fun share(context: Context, story: Story) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, story.headline)
        .putExtra(Intent.EXTRA_TEXT, "${story.headline}\n${story.sourceLink}")
    runCatching { context.startActivity(Intent.createChooser(send, "Share this good news")) }
}

@Composable
fun StoryScreen(story: Story, ui: UiData, a: Actions, article: ArticleState = ArticleState.Loading) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val cat = categoryOf(story.category)
    val c = p.cat(story.category)
    val related = remember(story, ui.week) {
        ui.week.filter { it.category == story.category && it.id != story.id }.take(3)
    }
    val glass = Color.Black.copy(alpha = 0.36f)
    val minutes = (article as? ArticleState.Ready)?.article?.minutes ?: story.readTime

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

            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(22.dp))
                StoryChips(story, place = if (story.region != null) ui.placeLabel(story) else null)
                Spacer(Modifier.height(14.dp))
                Headline(story.headline, 30.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "${story.sourceName}  ·  $minutes min read",
                    color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(20.dp))

                AnimatedContent(
                    targetState = article,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    contentKey = { it::class },
                    label = "article",
                ) { state ->
                    when (state) {
                        ArticleState.Loading -> LoadingBody(story)
                        is ArticleState.Ready -> ArticleBody(state.article, c)
                        ArticleState.Unavailable -> UnavailableBody(story) { a.openLink(story.sourceLink) }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth().height(108.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    Fact("Source", story.sourceName, p.tint(c), Modifier.weight(1.4f))
                    Fact("Read", "$minutes min", p.surface2, Modifier.weight(1f))
                    Fact("Good vibes", "${(story.positivity * 100).roundToInt()}%", p.accentSoft, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Gap))
                // The original stays one tap away, to credit the publisher and for anything the reader view missed.
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(CircleShape)
                        .border(1.dp, p.line, CircleShape)
                        .clickable { a.openLink(story.sourceLink) }.padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Open on ${story.sourceName}", Modifier.weight(1f), color = p.ink,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.size(44.dp).clip(CircleShape).background(p.surface2), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = p.ink, modifier = Modifier.size(20.dp))
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
                                    .clickable { a.openLink(other.link) }.padding(horizontal = 18.dp, vertical = 16.dp),
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

private val BodySize = 18.sp
private val BodyLine = 30.sp

@Composable
private fun ArticleBody(article: Article, accent: Color) {
    val p = LocalPalette.current
    SelectionContainer {
        Column {
            article.blocks.forEach { b ->
                when (b) {
                    is Block.Para -> Text(
                        b.text, Modifier.padding(bottom = 18.dp),
                        color = p.ink, fontFamily = Serif, fontSize = BodySize, lineHeight = BodyLine,
                    )
                    is Block.Heading -> Text(
                        b.text, Modifier.padding(top = 10.dp, bottom = 12.dp),
                        color = p.ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp,
                    )
                    is Block.Quote -> Row(Modifier.padding(top = 8.dp, bottom = 22.dp).height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(accent))
                        Spacer(Modifier.size(16.dp))
                        Text(
                            b.text, color = p.ink, fontFamily = Serif, fontStyle = FontStyle.Italic,
                            fontSize = 20.sp, lineHeight = 31.sp,
                        )
                    }
                    is Block.Bullet -> Row(Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                        Text(
                            b.number?.let { "$it." } ?: "•", Modifier.width(26.dp),
                            color = accent, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = BodySize, lineHeight = BodyLine,
                        )
                        Text(b.text, color = p.ink, fontFamily = Serif, fontSize = BodySize, lineHeight = BodyLine)
                    }
                    is Block.Image -> Column(Modifier.padding(top = 6.dp, bottom = 22.dp)) {
                        AsyncImage(
                            model = b.url, contentDescription = b.caption, contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(22.dp)).background(p.surface2),
                        )
                        b.caption?.let {
                            Text(it, Modifier.padding(top = 8.dp, start = 4.dp), color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBody(story: Story) {
    val p = LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
    )
    Column {
        if (story.summary.isNotBlank()) {
            Text(story.summary, color = p.ink, fontFamily = Serif, fontSize = BodySize, lineHeight = BodyLine)
            Spacer(Modifier.height(22.dp))
        }
        Column(Modifier.alpha(pulse), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1f, 0.94f, 0.98f, 0.7f, 0f, 1f, 0.9f, 0.55f).forEach { w ->
                if (w == 0f) Spacer(Modifier.height(10.dp))
                else Box(Modifier.fillMaxWidth(w).height(14.dp).clip(CircleShape).background(p.surface2))
            }
        }
    }
}

@Composable
private fun UnavailableBody(story: Story, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Column {
        if (story.summary.isNotBlank()) {
            Text(story.summary, color = p.ink, fontFamily = Serif, fontSize = BodySize, lineHeight = BodyLine)
            Spacer(Modifier.height(20.dp))
        }
        Tile(Modifier.fillMaxWidth(), color = p.accentSoft) {
            Column(Modifier.padding(20.dp)) {
                Headline("Read the rest on ${story.sourceName}", 19.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This site doesn’t share its full text with apps, or you’re offline.",
                    color = p.muted, fontSize = 14.sp, lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.clip(CircleShape).background(p.accent).clickable(onClick = onOpen)
                        .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open the story", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
                }
            }
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
