package com.goodnewsguys.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class Detail(val story: Story) : Screen
    data class Topic(val slug: String) : Screen
    data object Local : Screen
    data object Saved : Screen
}

/** Everything the screens need to draw. */
data class UiData(val digests: List<Digest>, val saved: List<Story>, val city: String?) {
    val week: List<Story> by lazy { digests.flatMap { it.stories }.distinctBy { it.id } }
    val newestDate: String get() = digests.firstOrNull()?.date.orEmpty()
    private val savedIds by lazy { saved.map { it.id }.toSet() }
    fun isSaved(story: Story) = story.id in savedIds
}

class Actions(
    val open: (Screen) -> Unit = {},
    val back: () -> Unit = {},
    val toggleSave: (Story) -> Unit = {},
    val setCity: (String?) -> Unit = {},
    val refresh: () -> Unit = {},
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(File(app.filesDir, "digests").apply { mkdirs() })
    private val prefs = app.getSharedPreferences("gng", 0)

    var digests by mutableStateOf(emptyList<Digest>()); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var saved by mutableStateOf(loadSaved()); private set
    var city by mutableStateOf(prefs.getString("city", null)); private set
    val stack = mutableStateListOf<Screen>(Screen.Home)

    val actions = Actions(
        open = { stack.add(it) },
        back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
        toggleSave = ::toggleSave,
        setCity = { slug ->
            city = slug
            prefs.edit().putString("city", slug).apply()
        },
        refresh = ::refresh,
    )

    init {
        viewModelScope.launch {
            val local = repo.cached()
            if (local.isNotEmpty()) {
                digests = local
                loading = false
            }
            load()
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            load()
            refreshing = false
        }
    }

    private suspend fun load() {
        val week = repo.week()
        if (week.isNotEmpty()) digests = week
        loading = false
    }

    private fun toggleSave(story: Story) {
        saved = if (saved.any { it.id == story.id }) saved.filter { it.id != story.id } else listOf(story) + saved
        prefs.edit().putString("saved", JSONArray(saved.map { it.toJson() }).toString()).apply()
    }

    private fun loadSaved(): List<Story> = runCatching {
        val arr = JSONArray(prefs.getString("saved", "[]"))
        (0 until arr.length()).map { parseStory(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
