package com.goodnewsguys.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ListScaffold(
    title: String,
    subtitle: String,
    emoji: String?,
    a: Actions,
    action: Pair<String, () -> Unit>? = null,
    content: LazyListScope.() -> Unit,
) {
    val p = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(Gap)) {
        item(key = "head") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", a.back)
                    Spacer(Modifier.weight(1f))
                    if (action != null) {
                        Text(
                            action.first,
                            Modifier.clip(CircleShape).background(p.surface2).clickable(onClick = action.second)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                if (emoji != null) Text(emoji, Modifier.padding(start = 2.dp, bottom = 4.dp), fontSize = 40.sp)
                Headline(title, 36.sp, Modifier.padding(horizontal = 4.dp))
                Text(subtitle, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), color = p.muted, fontSize = 16.sp)
            }
        }
        content()
    }
}

/** Stories grouped under Today / Yesterday / a date. */
private fun LazyListScope.storiesByDay(stories: List<Story>, ui: UiData, a: Actions, tint: Boolean = true) {
    val groups = stories.groupBy { it.digestDate }.toSortedMap(compareByDescending { it })
    groups.forEach { (date, list) ->
        item(key = "day-$date") {
            Text(
                dayLabel(date, ui.newestDate).uppercase(),
                Modifier.padding(start = 4.dp, top = 10.dp),
                color = LocalPalette.current.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }
        items(list.size, key = { "$date-${list[it].id}" }) { i ->
            WideStoryTile(list[i], ui, a, tinted = tint && i % 3 == 0)
        }
    }
}

@Composable
private fun EmptyTile(text: String) {
    val p = LocalPalette.current
    Tile(Modifier.fillMaxWidth(), color = p.surface2) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Sun(Modifier.height(56.dp).fillMaxWidth(0.2f))
            Spacer(Modifier.height(16.dp))
            Text(text, color = p.muted, fontSize = 16.sp, lineHeight = 23.sp)
        }
    }
}

@Composable
fun TopicScreen(slug: String, ui: UiData, a: Actions) {
    val cat = categoryOf(slug)
    val stories = remember(slug, ui.week) { ui.week.filter { it.category == cat.slug } }
    ListScaffold(
        title = cat.label,
        subtitle = if (stories.size == 1) "1 story this week" else "${stories.size} stories this week",
        emoji = cat.emoji, a = a,
    ) {
        if (stories.isEmpty()) item { EmptyTile("Nothing here this week. Check back tomorrow.") }
        storiesByDay(stories, ui, a)
    }
}

@Composable
fun SavedScreen(ui: UiData, a: Actions) {
    ListScaffold(
        title = "Saved",
        subtitle = if (ui.saved.isEmpty()) "Kept on this phone only" else "${ui.saved.size} kept on this phone only",
        emoji = null, a = a,
    ) {
        if (ui.saved.isEmpty()) item { EmptyTile("Tap the heart on any story to keep it here.") }
        items(ui.saved, key = { it.id }) { WideStoryTile(it, ui, a) }
    }
}

@Composable
fun LocalScreen(ui: UiData, a: Actions) {
    val p = LocalPalette.current
    val city = cityOf(ui.city)
    if (city != null) {
        val stories = remember(city, ui.week) { ui.week.filter { it.region == city.slug } }
        ListScaffold(
            title = city.name,
            subtitle = if (stories.size == 1) "1 local story this week" else "${stories.size} local stories this week",
            emoji = "📍", a = a, action = "Change city" to { a.setCity(null) },
        ) {
            if (stories.isEmpty()) item { EmptyTile("Nothing from ${city.name} this week yet. Check back tomorrow.") }
            storiesByDay(stories, ui, a)
        }
        return
    }

    val counts = remember(ui.week) { CITIES.associate { c -> c.slug to ui.week.count { it.region == c.slug } } }
    val tints = listOf("local", "science", "culture", "general", "space", "environment", "health", "tech")
    ListScaffold(title = "Near you", subtitle = "Pick a city. It’s saved on this phone only.", emoji = "📍", a = a) {
        val rows = CITIES.chunked(2)
        items(rows.size, key = { "cities-$it" }) { r ->
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                rows[r].forEachIndexed { i, c ->
                    val color = p.cat(tints[(r * 2 + i) % tints.size])
                    // The wider tile swaps sides on each row.
                    val wide = (i == 0) == (r % 2 == 0)
                    CityTile(c, counts[c.slug] ?: 0, color, Modifier.weight(if (wide) 1.35f else 1f).fillMaxHeight()) {
                        a.setCity(c.slug)
                    }
                }
            }
        }
    }
}

@Composable
private fun CityTile(city: City, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Tile(modifier, color = p.tint(color, if (p.dark) 0.22f else 0.15f), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(city.country.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Column {
                Headline(city.name, 22.sp, maxLines = 1)
                Text(
                    if (count == 0) "Nothing this week" else if (count == 1) "1 story" else "$count stories",
                    color = p.muted, fontSize = 13.sp,
                )
            }
        }
    }
}
