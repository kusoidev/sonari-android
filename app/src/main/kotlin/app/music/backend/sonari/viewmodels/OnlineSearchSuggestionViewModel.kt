package app.music.backend.sonari.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.music.backend.sonari.innertube.YouTube
import app.music.backend.sonari.innertube.models.YTItem
import app.music.backend.sonari.innertube.models.filterExplicit
import app.music.backend.sonari.innertube.models.filterVideoSongs
import app.music.backend.sonari.innertube.utils.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.music.backend.sonari.constants.HideExplicitKey
import app.music.backend.sonari.constants.HideVideoSongsKey
import app.music.backend.sonari.db.MusicDatabase
import app.music.backend.sonari.db.entities.SearchHistory
import app.music.backend.sonari.utils.dataStore
import app.music.backend.sonari.utils.get
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
  @ApplicationContext val context: Context,
  database: MusicDatabase,
) : ViewModel() {
  val query = MutableStateFlow("")
  private val _viewState = MutableStateFlow(SearchSuggestionViewState())
  val viewState = _viewState.asStateFlow()

  init {
    viewModelScope.launch {
      query
        .flatMapLatest { query ->
          if (query.isEmpty()) {
            database.searchHistory().map { history ->
              SearchSuggestionViewState(
                history = history,
              )
            }
          } else {
            val parsedUrl = YouTubeUrlParser.parse(query)
            val parsedItem = if (parsedUrl != null) fetchParsedUrlItem(parsedUrl) else null

            val result =
              if (parsedUrl != null) null else YouTube.searchSuggestions(query).getOrNull()
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

            database
              .searchHistory(query)
              .map { it.take(3) }
              .map { history ->
                SearchSuggestionViewState(
                  history = history,
                  suggestions =
                    result
                      ?.queries
                      ?.filter { suggestionQuery -> history.none { it.query == suggestionQuery } }
                      .orEmpty(),
                  items =
                    listOfNotNull(parsedItem) +
                      result
                        ?.recommendedItems
                        ?.distinctBy { it.id }
                        ?.filter { it.id != parsedItem?.id }
                        ?.filterExplicit(hideExplicit)
                        ?.filterVideoSongs(hideVideoSongs)
                        .orEmpty(),
                  isFromLink = parsedUrl != null
                )
              }
          }
        }
        .collect { _viewState.value = it }
    }
  }

  private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? {
    return try {
      when (parsedUrl) {
        is YouTubeUrlParser.ParsedUrl.Video -> {
          YouTube.queue(listOf(parsedUrl.id)).getOrNull()?.firstOrNull()
        }
        is YouTubeUrlParser.ParsedUrl.Artist -> {
          YouTube.artist(parsedUrl.id).getOrNull()?.artist
        }
      }
    } catch (e: Exception) {
      null
    }
  }
}

data class SearchSuggestionViewState(
  val history: List<SearchHistory> = emptyList(),
  val suggestions: List<String> = emptyList(),
  val items: List<YTItem> = emptyList(),
  val isFromLink: Boolean = false,
)
