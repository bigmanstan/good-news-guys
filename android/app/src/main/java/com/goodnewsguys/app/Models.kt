package com.goodnewsguys.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Coverage(val name: String, val link: String)

/** A neighbourhood or nearby town a local story mentions. */
data class Place(val name: String, val lat: Double, val lon: Double)

data class Story(
    val id: String,
    val headline: String,
    val summary: String,
    val sourceName: String,
    val sourceLink: String,
    val alsoCoveredBy: List<Coverage>,
    val category: String,
    val region: String?,
    val places: List<Place> = emptyList(),
    val positivity: Double,
    val readTime: Int,
    val digestDate: String,
    val image: String?,
    val pinned: Boolean,
)

data class Digest(val date: String, val top3: List<String>, val stories: List<Story>)

data class Category(val slug: String, val label: String, val emoji: String)

val CATEGORIES = listOf(
    Category("science", "Science", "🔬"),
    Category("space", "Space", "🚀"),
    Category("health", "Health", "💗"),
    Category("environment", "Environment", "🌿"),
    Category("animals", "Animals", "🐾"),
    Category("community", "Community", "🤝"),
    Category("tech", "Tech", "💡"),
    Category("culture", "Culture", "🎨"),
    Category("general", "Uplifting", "☀️"),
)

fun categoryOf(slug: String?): Category = CATEGORIES.find { it.slug == slug } ?: CATEGORIES.last()

data class City(val slug: String, val name: String, val country: String, val lat: Double, val lon: Double)

val CITIES = listOf(
    City("toronto", "Toronto", "Canada", 43.6532, -79.3832),
    City("montreal", "Montréal", "Canada", 45.5019, -73.5674),
    City("vancouver", "Vancouver", "Canada", 49.2827, -123.1207),
    City("calgary", "Calgary", "Canada", 51.0447, -114.0719),
    City("ottawa", "Ottawa", "Canada", 45.4215, -75.6972),
    City("new-york", "New York", "USA", 40.7128, -74.0060),
    City("chicago", "Chicago", "USA", 41.8781, -87.6298),
    City("london", "London", "UK", 51.5072, -0.1276),
)

fun cityOf(slug: String?): City? = CITIES.find { it.slug == slug }

/** org.json turns a JSON null into the string "null" unless asked first. */
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

fun parseStory(o: JSONObject, fallbackDate: String = ""): Story {
    val also = o.optJSONArray("alsoCoveredBy") ?: JSONArray()
    return Story(
        id = o.getString("id"),
        headline = o.str("headline").orEmpty(),
        summary = o.str("summary").orEmpty(),
        sourceName = o.str("sourceName").orEmpty(),
        sourceLink = o.str("sourceLink").orEmpty(),
        alsoCoveredBy = (0 until also.length()).mapNotNull { i ->
            also.optJSONObject(i)?.let { Coverage(it.str("name").orEmpty(), it.str("link").orEmpty()) }
        },
        category = o.str("category") ?: "general",
        region = o.str("region")?.takeIf { it.isNotBlank() },
        places = o.optJSONArray("places")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Place(it.str("name").orEmpty(), it.optDouble("lat"), it.optDouble("lon")) }
            }.filter { it.name.isNotBlank() && !it.lat.isNaN() && !it.lon.isNaN() }
        }.orEmpty(),
        positivity = o.optDouble("positivity", 0.0),
        readTime = o.optInt("readTime", 3),
        digestDate = o.str("digestDate") ?: fallbackDate,
        image = o.str("image")?.takeIf { it.startsWith("https://") },
        pinned = o.optBoolean("pinned", false),
    )
}

fun Story.toJson(): JSONObject = JSONObject()
    .put("id", id).put("headline", headline).put("summary", summary)
    .put("sourceName", sourceName).put("sourceLink", sourceLink)
    .put("alsoCoveredBy", JSONArray(alsoCoveredBy.map { JSONObject().put("name", it.name).put("link", it.link) }))
    .put("category", category).put("region", region ?: JSONObject.NULL)
    .put("places", JSONArray(places.map { JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon) }))
    .put("positivity", positivity).put("readTime", readTime)
    .put("digestDate", digestDate).put("image", image ?: JSONObject.NULL).put("pinned", pinned)

fun parseDigest(json: String): Digest {
    val o = JSONObject(json)
    val date = o.getString("date")
    val top = o.optJSONArray("top3") ?: JSONArray()
    val stories = o.getJSONArray("stories")
    return Digest(
        date = date,
        top3 = (0 until top.length()).map { top.getString(it) },
        stories = (0 until stories.length()).map { parseStory(stories.getJSONObject(it), date) },
    )
}

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

fun longDate(date: String): String = runCatching { LocalDate.parse(date).format(LONG_DATE) }.getOrDefault(date)

/** "Today", "Yesterday" or a short date, relative to the newest digest we have. */
fun dayLabel(date: String, newest: String): String = runCatching {
    val d = LocalDate.parse(date)
    val n = LocalDate.parse(newest)
    when (d) {
        n -> "Today"
        n.minusDays(1) -> "Yesterday"
        else -> d.format(SHORT_DATE)
    }
}.getOrDefault(date)
