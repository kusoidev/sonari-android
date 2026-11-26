package app.music.backend.sonari.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.music.backend.sonari.R
import app.music.backend.sonari.ui.component.Material3MenuGroup
import app.music.backend.sonari.ui.component.Material3MenuItemData

/**
 * What holding a "For you" card offers: grow it with more of the same, or tell the engine that
 * nothing like it belongs in the mixes again.
 */
@Composable
fun ForYouMixMenu(
  onDismiss: () -> Unit,
  onMoreLikeThis: () -> Unit,
  onNeverRecommend: () -> Unit,
) {
  Material3MenuGroup(
    items =
      listOf(
        Material3MenuItemData(
          title = { Text(stringResource(R.string.for_you_more_like)) },
          description = { Text(stringResource(R.string.for_you_more_like_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.tune), contentDescription = null) },
          onClick = {
            onDismiss()
            onMoreLikeThis()
          },
        ),
        Material3MenuItemData(
          title = { Text(stringResource(R.string.for_you_never)) },
          description = { Text(stringResource(R.string.for_you_never_desc)) },
          icon = { Icon(painter = painterResource(R.drawable.close), contentDescription = null) },
          onClick = {
            onDismiss()
            onNeverRecommend()
          },
        ),
      )
  )
}
