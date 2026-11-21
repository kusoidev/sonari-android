package app.music.backend.sonari.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import app.music.backend.sonari.innertube.utils.parseCookieString
import app.music.backend.sonari.BuildConfig
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.AccountEmailKey
import app.music.backend.sonari.constants.AudioQuality
import app.music.backend.sonari.constants.AudioQualityKey
import app.music.backend.sonari.constants.InnerTubeCookieKey
import app.music.backend.sonari.constants.UseLoginForBrowse
import app.music.backend.sonari.constants.YtmSyncKey
import app.music.backend.sonari.ui.component.Material3SettingsGroup
import app.music.backend.sonari.ui.component.Material3SettingsItem
import app.music.backend.sonari.utils.rememberEnumPreference
import app.music.backend.sonari.utils.rememberPreference
import app.music.backend.sonari.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingDialoge(
  onDismissRequest: () -> Unit,
  onNavigate: (String) -> Unit,
  homeViewModel: HomeViewModel
) {
  val uriHandler = LocalUriHandler.current
  val (audioQuality) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
  val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
  val isLoggedIn =
    remember(innerTubeCookie) {
      innerTubeCookie.isNotEmpty() && "SAPISID" in parseCookieString(innerTubeCookie)
    }

  val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
  val accountName by homeViewModel.accountName.collectAsState()
  val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

  val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
  val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    containerColor = MaterialTheme.colorScheme.surfaceContainer
  ) {
    val primaryColor = MaterialTheme.colorScheme.onSurface
    val onSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Material3SettingsGroup(
        title = "App",
        compact = true,
        items =
          listOf(
            Material3SettingsItem(
              title = { Text(androidx.compose.ui.res.stringResource(R.string.settings)) },
              description = {
                Text(androidx.compose.ui.res.stringResource(R.string.setting_desc_settings_main))
              },
              icon = painterResource(R.drawable.settings),
              onClick = {
                onDismissRequest()
                onNavigate("settings")
              }
            )
          )
      )
    }
  }
}
