package com.goodnewsguys.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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

/** How far "around you" reaches: neighbourhoods plus the nearby towns the local feeds cover. */
const val NEAR_KM = 40.0

/** Everything the screens need to draw. */
data class UiData(
    val digests: List<Digest>,
    val saved: List<Story>,
    val city: String?,
    val here: LatLon? = null,
    val locating: Boolean = false,
    val locationNote: String? = null,
) {
    val week: List<Story> by lazy { digests.flatMap { it.stories }.distinctBy { it.id } }
    val newestDate: String get() = digests.firstOrNull()?.date.orEmpty()
    private val savedIds by lazy { saved.map { it.id }.toSet() }
    fun isSaved(story: Story) = story.id in savedIds

    /** This week's stories from the reader's city that name a place, closest first. */
    val nearby: List<Pair<Story, Pair<Place, Double>>> by lazy {
        val at = here ?: return@lazy emptyList()
        week.filter { it.region == city }
            .mapNotNull { s -> s.nearest(at)?.let { s to it } }
            .filter { it.second.second <= NEAR_KM }
            .sortedBy { it.second.second }
    }

    /** "1.2 km · Leslieville" for a story, when we know where the reader is. */
    fun placeLabel(story: Story): String? {
        val at = here ?: return story.places.firstOrNull()?.name
        val (place, km) = story.nearest(at) ?: return null
        return if (km <= NEAR_KM) "${formatKm(km)} · ${place.name}" else place.name
    }
}

class Actions(
    val open: (Screen) -> Unit = {},
    val back: () -> Unit = {},
    val toggleSave: (Story) -> Unit = {},
    val setCity: (String?) -> Unit = {},
    val refresh: () -> Unit = {},
    val useLocation: () -> Unit = {},
    val openLink: (String) -> Unit = {},
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(File(app.filesDir, "digests").apply { mkdirs() })
    private val reader = Reader(File(app.cacheDir, "articles").apply { mkdirs() })
    private val prefs = app.getSharedPreferences("gng", 0)

    var digests by mutableStateOf(emptyList<Digest>()); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var saved by mutableStateOf(loadSaved()); private set
    var city by mutableStateOf(prefs.getString("city", null)); private set
    var here by mutableStateOf(loadHere()); private set
    var locating by mutableStateOf(false); private set
    var locationNote by mutableStateOf<String?>(null); private set
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val articles = mutableStateMapOf<String, ArticleState>()

    init {
        viewModelScope.launch {
            val local = repo.cached()
            if (local.isNotEmpty()) {
                digests = local
                loading = false
            }
            load()
        }
        // If the reader turned location on before, quietly refresh it on launch.
        if (prefs.getBoolean("useLocation", false)) locate(quiet = true)
    }

    fun open(screen: Screen) {
        stack.add(screen)
        if (screen is Screen.Detail) loadArticle(screen.story)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun chooseCity(slug: String?) {
        city = slug
        prefs.edit().putString("city", slug).apply()
        if (slug == null) {
            // "Change city" also forgets the location, so the picker is a real choice.
            here = null
            prefs.edit().remove("lat").remove("lon").putBoolean("useLocation", false).apply()
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            load()
            refreshing = false
        }
        if (prefs.getBoolean("useLocation", false)) locate(quiet = true)
    }

    fun locationDenied() {
        locationNote = "Location is off for this app. You can still pick your city."
    }

    /** Find the reader on the phone, then pick the closest city we cover. */
    fun locate(quiet: Boolean = false) {
        if (locating) return
        viewModelScope.launch {
            locating = true
            if (!quiet) locationNote = null
            val fix = currentLocation(getApplication())
            locating = false
            if (fix == null) {
                if (!quiet) locationNote = "Couldn’t get your location. Check that location is on, or pick your city."
                return@launch
            }
            // About 100 m of precision is plenty, and all we keep.
            val rounded = LatLon(Math.round(fix.lat * 1000) / 1000.0, Math.round(fix.lon * 1000) / 1000.0)
            val nearest = nearestCity(rounded)
            if (nearest == null) {
                if (!quiet) locationNote = "We don’t cover your area yet. Pick the closest city for now."
                return@launch
            }
            here = rounded
            city = nearest.slug
            locationNote = null
            prefs.edit()
                .putString("city", nearest.slug)
                .putString("lat", rounded.lat.toString())
                .putString("lon", rounded.lon.toString())
                .putBoolean("useLocation", true)
                .apply()
        }
    }

    fun loadArticle(story: Story) {
        val now = articles[story.id]
        if (now is ArticleState.Ready || now == ArticleState.Loading) return
        articles[story.id] = ArticleState.Loading
        viewModelScope.launch {
            val article = runCatching { reader.load(story) }.getOrNull()
            articles[story.id] = if (article != null) ArticleState.Ready(article) else ArticleState.Unavailable
        }
    }

    fun toggleSave(story: Story) {
        val adding = saved.none { it.id == story.id }
        saved = if (adding) listOf(story) + saved else saved.filter { it.id != story.id }
        prefs.edit().putString("saved", JSONArray(saved.map { it.toJson() }).toString()).apply()
        // Download saved stories right away so they can be read offline later.
        if (adding) loadArticle(story)
    }

    private suspend fun load() {
        val week = repo.week()
        if (week.isNotEmpty()) digests = week
        loading = false
    }

    private fun loadHere(): LatLon? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return LatLon(lat, lon)
    }

    private fun loadSaved(): List<Story> = runCatching {
        val arr = JSONArray(prefs.getString("saved", "[]"))
        (0 until arr.length()).map { parseStory(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
