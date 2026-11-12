package echo.music.iad1tya.ui.screens.search.suggestions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * The search tab used to show a curated Apple Music "Top 100" / Trending Artist / Trending Album
 * browse page here. That page was removed so the tab is just a plain search box: the area below
 * the search bar stays empty until the user starts typing, at which point the online search screen
 * takes over.
 */
@Composable
fun SuggestionsTabContent(
  navController: NavController,
  contentPadding: PaddingValues,
) {
  Box(modifier = Modifier.fillMaxSize())
}
