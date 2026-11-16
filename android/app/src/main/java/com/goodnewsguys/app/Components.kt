package com.goodnewsguys.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

val TileShape = RoundedCornerShape(28.dp)
val Gap = 12.dp

/** Screen edge padding that also clears the status and navigation bars. */
@Composable
fun screenPadding(): PaddingValues {
    val bars = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = 16.dp, end = 16.dp,
        top = bars.calculateTopPadding() + 12.dp,
        bottom = bars.calculateBottomPadding() + 28.dp,
    )
}

/** The basic bento tile: a big rounded block of colour. */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.surface,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var m = modifier.clip(TileShape).background(color)
    if (outlined) m = m.border(1.dp, LocalPalette.current.line, TileShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, container: Color = color.copy(alpha = 0.14f)) {
    Text(
        text.uppercase(),
        modifier = modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 5.dp),
        color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1,
    )
}

@Composable
fun Headline(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text, modifier = modifier, color = color, fontFamily = Serif, fontWeight = FontWeight.Bold,
        fontSize = size, lineHeight = size * 1.2f, letterSpacing = (-0.2).sp,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Meta(story: Story, modifier: Modifier = Modifier, color: Color = LocalPalette.current.muted) {
    Text(
        "${story.sourceName}  ·  ${story.readTime} min",
        modifier = modifier, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun CircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = LocalPalette.current.surface2,
    tint: Color = LocalPalette.current.ink,
) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(container).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp)) }
}

@Composable
fun SaveButton(
    saved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.Transparent,
    tint: Color = LocalPalette.current.muted,
    activeTint: Color = LocalPalette.current.accent,
) {
    CircleButton(
        icon = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        label = if (saved) "Remove from saved" else "Save story",
        onClick = onClick, modifier = modifier, container = container,
        tint = if (saved) activeTint else tint,
    )
}

@Composable
fun Photo(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
fun StoryChips(story: Story, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(categoryOf(story.category).label, p.cat(story.category))
        cityOf(story.region)?.let { Chip(it.name, p.cat("local")) }
    }
}

/** Full-width story tile: text on the left, a rounded photo on the right. */
@Composable
fun WideStoryTile(story: Story, ui: UiData, a: Actions, modifier: Modifier = Modifier, tinted: Boolean = false) {
    val p = LocalPalette.current
    Tile(
        modifier.fillMaxWidth(),
        color = if (tinted) p.tint(p.cat(story.category)) else p.surface,
        outlined = !tinted,
        onClick = { a.open(Screen.Detail(story)) },
    ) {
        Row(Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp, end = 14.dp)) {
            Column(Modifier.weight(1f)) {
                StoryChips(story)
                Spacer(Modifier.height(10.dp))
                Headline(story.headline, 18.sp, maxLines = 4)
                if (story.summary.isNotBlank() && story.image == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        story.summary, color = p.muted, fontSize = 14.sp, lineHeight = 20.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meta(story, Modifier.weight(1f))
                    SaveButton(ui.isSaved(story), { a.toggleSave(story) })
                }
            }
            if (story.image != null) {
                Spacer(Modifier.size(14.dp))
                Photo(
                    story.image,
                    Modifier.padding(top = 2.dp, end = 4.dp).size(96.dp).clip(RoundedCornerShape(20.dp)).background(p.surface2),
                )
            }
        }
    }
}

/** Half-width story tile, used in pairs. */
@Composable
fun HalfStoryTile(story: Story, ui: UiData, a: Actions, modifier: Modifier = Modifier, tinted: Boolean = false) {
    val p = LocalPalette.current
    val c = p.cat(story.category)
    Tile(
        modifier,
        color = if (tinted) p.tint(c) else p.surface,
        outlined = !tinted,
        onClick = { a.open(Screen.Detail(story)) },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (story.image != null) {
                Photo(
                    story.image,
                    Modifier.padding(8.dp).fillMaxWidth().height(92.dp).clip(RoundedCornerShape(21.dp)).background(p.surface2),
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp, top = if (story.image != null) 2.dp else 16.dp)) {
                if (story.image == null) {
                    Chip(categoryOf(story.category).label, c)
                    Spacer(Modifier.height(10.dp))
                } else {
                    Text(
                        categoryOf(story.category).label.uppercase(), color = c, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Headline(story.headline, 16.sp, Modifier.weight(1f), maxLines = if (story.image != null) 4 else 7)
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
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
        Headline(text, 24.sp, Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
