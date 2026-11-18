package com.goodnewsguys.app

import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
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
    val c = p.cat("local")
    val city = cityOf(ui.city)
    if (city != null) {
        val stories = remember(city, ui.week) { ui.week.filter { it.region == city.slug } }
        val near = ui.nearby
        val nearIds = remember(near) { near.map { it.first.id }.toSet() }
        ListScaffold(
            title = city.name,
            subtitle = when {
                near.isNotEmpty() -> "${near.size} stories within ${NEAR_KM.toInt()} km of you this week"
                stories.size == 1 -> "1 local story this week"
                else -> "${stories.size} local stories this week"
            },
            emoji = "📍", a = a, action = "Change city" to { a.setCity(null) },
        ) {
            if (ui.here == null) {
                item(key = "locate") {
                    LocateTile(
                        "See what’s happening on your street",
                        "Sorts ${city.name}’s stories by how close they are to you. Your location stays on this phone.",
                        ui, a,
                    )
                }
            }
            if (near.isNotEmpty()) {
                item(key = "near-title") { SectionTitle("Around you", trailing = "Closest first") }
                items(near.size, key = { "near-${near[it].first.id}" }) { i ->
                    WideStoryTile(near[i].first, ui, a, tinted = i == 0)
                }
                if (stories.size > near.size) item(key = "rest-title") { SectionTitle("Across ${city.name}") }
            }
            if (stories.isEmpty()) item { EmptyTile("Nothing from ${city.name} this week yet. Check back tomorrow.") }
            storiesByDay(stories.filter { it.id !in nearIds }, ui, a)
        }
        return
    }

    val counts = remember(ui.week) { CITIES.associate { c -> c.slug to ui.week.count { it.region == c.slug } } }
    val tints = listOf("local", "science", "culture", "general", "space", "environment", "health", "tech")
    ListScaffold(title = "Near you", subtitle = "Find good news from your own neighbourhood.", emoji = "📍", a = a) {
        item(key = "locate") {
            LocateTile("Use my location", "We’ll pick your city and show the stories closest to you first. Your location stays on this phone.", ui, a)
        }
        item(key = "or") {
            Text(
                "OR PICK A CITY", Modifier.padding(start = 4.dp, top = 10.dp),
                color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }
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

/** The big "use my location" tile. */
@Composable
private fun LocateTile(title: String, body: String, ui: UiData, a: Actions) {
    val p = LocalPalette.current
    val c = p.cat("local")
    Tile(Modifier.fillMaxWidth(), color = c, onClick = a.useLocation) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), size.height * 1.1f, Offset(size.width * 0.95f, 0f))
            drawCircle(Color.White.copy(alpha = 0.12f), size.height * 0.55f, Offset(size.width * 0.95f, 0f))
        }
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = c, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            Headline(if (ui.locating) "Finding you…" else title, 22.sp, color = p.surface)
            Spacer(Modifier.height(4.dp))
            Text(body, color = p.surface.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
            ui.locationNote?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = p.surface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
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
