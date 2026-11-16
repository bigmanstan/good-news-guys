package com.goodnewsguys.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

private sealed interface BentoRow {
    data class Wide(val story: Story, val tinted: Boolean) : BentoRow
    data class Pair(val left: Story, val right: Story, val tintLeft: Boolean) : BentoRow
}

/** Alternate one wide tile with a pair of half tiles, the bento rhythm. */
private fun bentoRows(stories: List<Story>): List<BentoRow> {
    val rows = mutableListOf<BentoRow>()
    var i = 0
    var n = 0
    while (i < stories.size) {
        if (n % 2 == 1 && i + 1 < stories.size) {
            rows += BentoRow.Pair(stories[i], stories[i + 1], tintLeft = n % 4 == 1)
            i += 2
        } else {
            rows += BentoRow.Wide(stories[i], tinted = n % 4 == 2)
            i += 1
        }
        n++
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ui: UiData,
    a: Actions,
    refreshing: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
) {
    val p = LocalPalette.current
    val today = ui.digests.first()
    val top = remember(today) { today.top3.mapNotNull { id -> today.stories.find { it.id == id } } }
    val rest = remember(today, ui.city) {
        today.stories.filter { it.id !in today.top3 && (it.region == null || it.region == ui.city) }
    }
    val rows = remember(rest) { bentoRows(rest) }
    val topics = remember(ui.week) {
        CATEGORIES.map { c -> c to ui.week.count { it.category == c.slug } }.filter { it.second > 0 }
    }
    val topicRows = remember(topics) {
        // Rows of 2 and 3 so the tiles come out in different sizes.
        val out = mutableListOf<List<Pair<Category, Int>>>()
        var i = 0
        var two = true
        while (i < topics.size) {
            val left = topics.size - i
            val take = if (left == 4) 2 else if (two) minOf(2, left) else minOf(3, left)
            out += topics.subList(i, i + take)
            i += take
            two = !two
        }
        out
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = a.refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            item(key = "header") { Header(today) }

            top.getOrNull(0)?.let { lead -> item(key = "lead") { LeadTile(lead, ui, a) } }

            item(key = "trio") {
                Row(Modifier.fillMaxWidth().height(248.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    top.getOrNull(1)?.let { SecondTile(it, ui, a, Modifier.weight(1.2f).fillMaxHeight()) }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                        CountTile(today.stories.size, Modifier.weight(1.3f).fillMaxWidth())
                        SavedTile(ui.saved.size, Modifier.weight(1f).fillMaxWidth()) { a.open(Screen.Saved) }
                    }
                }
            }

            top.getOrNull(2)?.let { third -> item(key = "third") { WideStoryTile(third, ui, a) } }

            item(key = "near") { NearYouTile(ui, a) }

            item(key = "topics-title") { SectionTitle("Browse by topic", trailing = "This week") }
            items(topicRows.size, key = { "topics-$it" }) { index ->
                val row = topicRows[index]
                Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    row.forEachIndexed { i, (cat, count) ->
                        // In a row of two, one tile is wider, and the wide side swaps each time.
                        val wide = row.size == 2 && (i == 0) == (index % 4 == 0)
                        TopicTile(cat, count, Modifier.weight(if (wide) 1.5f else 1f).fillMaxHeight()) {
                            a.open(Screen.Topic(cat.slug))
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) item(key = "more-title") { SectionTitle("More good news", trailing = "${rest.size} stories") }
            items(rows, key = { row ->
                when (row) {
                    is BentoRow.Wide -> row.story.id
                    is BentoRow.Pair -> row.left.id + row.right.id
                }
            }) { row ->
                when (row) {
                    is BentoRow.Wide -> WideStoryTile(row.story, ui, a, tinted = row.tinted)
                    is BentoRow.Pair -> Row(Modifier.fillMaxWidth().height(248.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        HalfStoryTile(row.left, ui, a, Modifier.weight(1f).fillMaxHeight(), tinted = row.tintLeft)
                        HalfStoryTile(row.right, ui, a, Modifier.weight(1f).fillMaxHeight(), tinted = !row.tintLeft)
                    }
                }
            }

            item(key = "footer") {
                Text(
                    "Fresh every morning at 6.  No ads, no tracking.",
                    Modifier.fillMaxWidth().padding(top = 20.dp, start = 4.dp),
                    color = p.muted, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun Header(today: Digest) {
    val p = LocalPalette.current
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning."
            in 12..17 -> "Good afternoon."
            else -> "Good evening."
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                longDate(today.date).uppercase(), color = p.accentInk, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Headline(greeting, 36.sp)
            Text("Here’s what went right today.", color = p.muted, fontSize = 16.sp)
        }
        Sun(Modifier.size(54.dp))
    }
}

/** The little sun logo, drawn in code. */
@Composable
fun Sun(modifier: Modifier = Modifier, color: Color = LocalPalette.current.accent) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(color.copy(alpha = 0.16f), r)
        drawCircle(color.copy(alpha = 0.28f), r * 0.72f)
        drawCircle(color, r * 0.44f)
    }
}

@Composable
private fun LeadTile(story: Story, ui: UiData, a: Actions) {
    val p = LocalPalette.current
    val c = p.cat(story.category)
    val base = if (p.dark) lerp(c, Color.Black, 0.45f) else c
    Box(
        Modifier.fillMaxWidth().then(if (story.image != null) Modifier.height(420.dp) else Modifier.heightIn(min = 300.dp))
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.5f))))
            .clickable { a.open(Screen.Detail(story)) },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), size.width * 0.55f, Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.08f), size.width * 0.30f, Offset(size.width * 0.10f, size.height * 0.55f))
        }
        if (story.image != null) {
            Photo(story.image, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
            )
        }
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Top story", Color.White, container = Color.Black.copy(alpha = 0.32f))
            Spacer(Modifier.weight(1f))
            SaveButton(
                ui.isSaved(story), { a.toggleSave(story) },
                container = Color.Black.copy(alpha = 0.32f), tint = Color.White, activeTint = Color(0xFFFFC65C),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 22.dp, top = 92.dp)) {
            Text(
                categoryOf(story.category).emoji + "  " + categoryOf(story.category).label.uppercase(), color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Headline(story.headline, 29.sp, color = Color.White, maxLines = 5)
            if (story.image == null && story.summary.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    story.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 22.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Meta(story, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SecondTile(story: Story, ui: UiData, a: Actions, modifier: Modifier) {
    val p = LocalPalette.current
    val c = p.cat(story.category)
    Tile(modifier, color = p.tint(c, if (p.dark) 0.24f else 0.16f), onClick = { a.open(Screen.Detail(story)) }) {
        Text(
            categoryOf(story.category).emoji,
            Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp), fontSize = 26.sp,
        )
        Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 16.dp, end = 10.dp, bottom = 6.dp)) {
            Chip(categoryOf(story.category).label, c)
            Spacer(Modifier.height(12.dp))
            Headline(story.headline, 19.sp, Modifier.weight(1f), maxLines = 6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    story.sourceName, Modifier.weight(1f), color = p.muted, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                SaveButton(ui.isSaved(story), { a.toggleSave(story) })
            }
        }
    }
}

@Composable
private fun CountTile(count: Int, modifier: Modifier) {
    val p = LocalPalette.current
    Tile(modifier, color = p.accent) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.92f, size.height * 0.08f)
            drawCircle(Color.White.copy(alpha = 0.22f), size.width * 0.46f, c)
            drawCircle(Color.White.copy(alpha = 0.26f), size.width * 0.28f, c)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp)) {
            Text(
                "$count", color = OnAccent, fontFamily = Serif, fontWeight = FontWeight.Bold,
                fontSize = 52.sp, lineHeight = 52.sp,
            )
            Text("good stories today", color = OnAccent.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SavedTile(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    // Inverted tile: dark on the light theme, cream on the dark one.
    Tile(modifier, color = p.ink, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saved", color = p.bg, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    if (count == 0) "Nothing yet" else if (count == 1) "1 story" else "$count stories",
                    color = p.bg.copy(alpha = 0.65f), fontSize = 13.sp,
                )
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(p.bg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = p.accent, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun NearYouTile(ui: UiData, a: Actions) {
    val p = LocalPalette.current
    val c = p.cat("local")
    val city = cityOf(ui.city)
    val local = remember(ui.week, ui.city) { ui.week.filter { it.region != null && it.region == ui.city } }
    Tile(Modifier.fillMaxWidth(), color = p.tint(c, if (p.dark) 0.24f else 0.16f), onClick = { a.open(Screen.Local) }) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(c.copy(alpha = 0.10f), size.height * 0.9f, Offset(size.width, size.height))
        }
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = p.surface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEAR YOU", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Headline(city?.name ?: "Pick your city", 22.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = p.ink)
            }
            Spacer(Modifier.height(12.dp))
            if (city == null) {
                Text("Good news from Toronto, New York, London and five more cities.", color = p.muted, fontSize = 15.sp, lineHeight = 21.sp)
            } else if (local.isEmpty()) {
                Text("Nothing from ${city.name} this week yet. Check back tomorrow.", color = p.muted, fontSize = 15.sp, lineHeight = 21.sp)
            } else {
                local.take(2).forEach { s ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            s.headline, color = p.ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (local.size == 1) "1 story this week" else "${local.size} stories this week",
                    color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TopicTile(cat: Category, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = p.cat(cat.slug)
    Tile(modifier, color = p.tint(c, if (p.dark) 0.22f else 0.15f), onClick = onClick) {
        Text(cat.emoji, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp), fontSize = 26.sp)
        Text(
            "$count", Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp),
            color = c, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )
        Text(
            cat.label, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp, end = 8.dp),
            color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown on first launch while the first digest downloads. */
@Composable
fun LoadingScreen() {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(screenPadding()), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Spacer(Modifier.height(96.dp))
        Tile(Modifier.fillMaxWidth().height(340.dp), color = p.surface2) {}
        Row(Modifier.fillMaxWidth().height(248.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Tile(Modifier.weight(1.2f).fillMaxHeight(), color = p.surface2) {}
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                Tile(Modifier.weight(1.3f).fillMaxWidth(), color = p.accentSoft) {}
                Tile(Modifier.weight(1f).fillMaxWidth(), color = p.surface2) {}
            }
        }
    }
}

@Composable
fun ErrorScreen(onRetry: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sun(Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Headline("Can’t reach the good news", 24.sp)
        Spacer(Modifier.height(8.dp))
        Text("Check your connection and try again.", color = p.muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Try again",
            Modifier.clip(CircleShape).background(p.accent).clickable(onClick = onRetry).padding(horizontal = 26.dp, vertical = 14.dp),
            color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        )
    }
}
