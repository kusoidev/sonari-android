@file:OptIn(ExperimentalMaterial3Api::class)

package app.music.backend.sonari.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import app.music.backend.sonari.LocalPlayerAwareWindowInsets
import app.music.backend.sonari.R
import app.music.backend.sonari.constants.AppIconTypeKey
import app.music.backend.sonari.utils.AppIconType
import app.music.backend.sonari.utils.CustomAppIcon
import app.music.backend.sonari.utils.IconUtils
import app.music.backend.sonari.utils.rememberEnumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppIconSettingsScreen(
  navController: NavController,
  activity: Activity,
  snackbarHostState: SnackbarHostState
) {
  val (appIconType, onAppIconTypeChange) =
    rememberEnumPreference(AppIconTypeKey, defaultValue = AppIconType.DEFAULT)
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current

  var editingSource by remember { mutableStateOf<Bitmap?>(null) }
  var customIcon by remember { mutableStateOf(CustomAppIcon.load(context)) }

  fun handleIconChange(iconType: AppIconType) {
    if (appIconType == iconType) return
    onAppIconTypeChange(iconType)
    IconUtils.setIcon(activity, iconType)
    coroutineScope.launch {
      val result =
        snackbarHostState.showSnackbar(
          message = "Icon updated, restart to apply",
          actionLabel = "Restart"
        )
      if (result == SnackbarResult.ActionPerformed) {
        val packageManager = activity.packageManager
        val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
        if (intent != null) {
          val componentName = intent.component
          val mainIntent = Intent.makeRestartActivityTask(componentName)
          activity.startActivity(mainIntent)
          Runtime.getRuntime().exit(0)
        }
      }
    }
  }

  val pickImage =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val bitmap = withContext(Dispatchers.IO) { CustomAppIcon.readSource(context, uri) }
        if (bitmap == null) {
          snackbarHostState.showSnackbar("That image could not be opened")
        } else {
          editingSource = bitmap
        }
      }
    }

  editingSource?.let { source ->
    CustomAppIconEditorDialog(
      source = source,
      onDismiss = { editingSource = null },
      onConfirm = { icon ->
        editingSource = null
        coroutineScope.launch {
          val saved = withContext(Dispatchers.IO) { CustomAppIcon.save(context, icon) }
          if (!saved) {
            snackbarHostState.showSnackbar("The icon could not be saved")
            return@launch
          }
          customIcon = icon
          val pinned = CustomAppIcon.pin(context, icon)
          snackbarHostState.showSnackbar(
            if (pinned) "Your icon is on the home screen"
            else "Saved, but this launcher does not allow adding icons"
          )
        }
      }
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      TopAppBar(
        title = { Text("App Icon", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.surface)
          .verticalScroll(rememberScrollState())
          .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
          )
          .padding(innerPadding)
          .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      SectionLabel("Your own photo")

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
      ) {
        Column {
          IconPreviewRow(
            preview = customIcon,
            title = if (customIcon == null) "Pick a picture" else "Change the picture",
            description = "Crop any photo into an icon for your home screen",
            onClick = { pickImage.launch("image/*") }
          )

          val current = customIcon
          if (current != null) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              TextButton(
                onClick = {
                  coroutineScope.launch {
                    val pinned = CustomAppIcon.pin(context, current)
                    snackbarHostState.showSnackbar(
                      if (pinned) "Added to the home screen again"
                      else "This launcher does not allow adding icons"
                    )
                  }
                }
              ) {
                Text("Add to the home screen again")
              }
              Spacer(modifier = Modifier.weight(1f))
              TextButton(
                onClick = {
                  coroutineScope.launch {
                    withContext(Dispatchers.IO) { CustomAppIcon.delete(context) }
                    customIcon = null
                    snackbarHostState.showSnackbar(
                      "Custom icon removed. A copy already on the home screen stays until you remove it there"
                    )
                  }
                }
              ) {
                Text("Remove")
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      SectionLabel("Built-in icons")

      val icons =
        listOf(
          AppIconOption(AppIconType.CRAFT, "Craft", "Craft icon by CR3TO_", R.mipmap.ic_launcher_craft),
          AppIconOption(
            AppIconType.SADIE,
            "Sadie Sink",
            "Sadie Sink on community demand",
            R.mipmap.ic_launcher_sadie
          ),
          AppIconOption(
            AppIconType.BILLIE_EILISH,
            "Billie Eilish",
            "billie eilish suggested by Lalo salamanca",
            R.mipmap.billie_eilish_icon
          ),
          AppIconOption(
            AppIconType.CRAZY_BLUE,
            "Crazy Blue Icon",
            "A vibrant crazy blue icon",
            R.mipmap.crazy_blue_icon
          ),
          AppIconOption(
            AppIconType.DEFAULT,
            "New Icon",
            "The standard vibrant icon",
            R.mipmap.ic_launcher
          ),
          AppIconOption(
            AppIconType.SABRINA_CARPENTER,
            "Sabrina Carpenter",
            "Suggested by notdieinganymore",
            R.mipmap.ic_launcher_sabrina
          ),
          AppIconOption(
            AppIconType.SABRINA_CARPENTER_2,
            "Sabrina Carpenter 2",
            "Suggested by Falvik",
            R.mipmap.ic_launcher_sabrina2
          ),
          AppIconOption(
            AppIconType.MILLIE_BOBBY_BROWN,
            "Millie Bobby Brown",
            "Millie Bobby Brown on community demand",
            R.mipmap.ic_launcher_millie
          )
        ).sortedBy { it.title.lowercase() }

      Column(modifier = Modifier.fillMaxWidth()) {
        icons.forEachIndexed { index, option ->
          val shape =
            when {
              icons.size == 1 -> RoundedCornerShape(24.dp)
              index == 0 ->
                RoundedCornerShape(
                  topStart = 24.dp,
                  topEnd = 24.dp,
                  bottomStart = 4.dp,
                  bottomEnd = 4.dp
                )
              index == icons.size - 1 ->
                RoundedCornerShape(
                  topStart = 4.dp,
                  topEnd = 4.dp,
                  bottomStart = 24.dp,
                  bottomEnd = 24.dp
                )
              else -> RoundedCornerShape(4.dp)
            }

          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors =
              CardDefaults.cardColors(
                containerColor =
                  if (appIconType == option.type)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  else MaterialTheme.colorScheme.surfaceContainerHigh
              ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
          ) {
            AppIconRow(
              option = option,
              isSelected = (appIconType == option.type),
              onClick = { handleIconChange(option.type) }
            )
          }

          if (index < icons.size - 1) {
            Spacer(modifier = Modifier.height(2.dp))
          }
        }
      }

      Spacer(
        modifier =
          Modifier.windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
          )
      )
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

data class AppIconOption(
  val type: AppIconType,
  val title: String,
  val description: String,
  val iconRes: Int
)

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
  )
}

@Composable
private fun IconPreviewRow(
  preview: Bitmap?,
  title: String,
  description: String,
  onClick: () -> Unit
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center
    ) {
      if (preview != null) {
        Image(
          bitmap = preview.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.grid_view),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.width(20.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun AppIconRow(option: AppIconOption, isSelected: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val context = LocalContext.current
    val bitmap =
      remember(option.iconRes) {
        val drawable = ContextCompat.getDrawable(context, option.iconRes)
        drawable?.toBitmap(width = 192, height = 192)?.asImageBitmap()
      }

    Box(
      modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = null,
          modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
        )
      }
    }

    Spacer(modifier = Modifier.width(20.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = option.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = option.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.width(8.dp))
    RadioButton(selected = isSelected, onClick = onClick)
  }
}
