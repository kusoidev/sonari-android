package app.music.backend.sonari.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconType(val value: Int) {
  DEFAULT(0),
  STATIC(2),
  CRAZY_BLUE(4),
  BILLIE_EILISH(13),
  SABRINA_CARPENTER(14),
  SABRINA_CARPENTER_2(15),
  CRAFT(16),
  SADIE(17),
  MILLIE_BOBBY_BROWN(19)
}

object IconUtils {
  private const val PACKAGE = "app.music.backend.sonari"

  private fun alias(context: Context, name: String) = ComponentName(context, "$PACKAGE.$name")

  /** Every launcher entry the app declares, as an activity alias with its own icon. */
  private fun aliases(context: Context): List<Pair<AppIconType, ComponentName>> =
    listOf(
      AppIconType.DEFAULT to alias(context, "MainActivityAlias"),
      AppIconType.STATIC to alias(context, "MainActivityStatic"),
      AppIconType.CRAZY_BLUE to alias(context, "MainActivityCrazyBlue"),
      AppIconType.BILLIE_EILISH to alias(context, "MainActivityBillieEilish"),
      AppIconType.SABRINA_CARPENTER to alias(context, "MainActivitySabrina"),
      AppIconType.SABRINA_CARPENTER_2 to alias(context, "MainActivitySabrina2"),
      AppIconType.CRAFT to alias(context, "MainActivityCraft"),
      AppIconType.SADIE to alias(context, "MainActivitySadie"),
      AppIconType.MILLIE_BOBBY_BROWN to alias(context, "MainActivityMillie")
    )

  fun setIcon(context: Context, iconType: AppIconType) {
    val pm = context.packageManager
    aliases(context).forEach { (type, component) ->
      pm.setComponentEnabledSetting(
        component,
        if (type == iconType) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
      )
    }
  }

  /**
   * Turns the default launcher entry back on when every one of them is off.
   *
   * That happens after an update that removes the icon someone had picked: their alias is gone and
   * the rest are disabled, so the app would have no launcher entry at all.
   */
  fun ensureLaunchableIcon(context: Context) {
    val pm = context.packageManager
    val allDisabled =
      aliases(context).all { (_, component) ->
        pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      }
    if (!allDisabled) return

    pm.setComponentEnabledSetting(
      alias(context, "MainActivityAlias"),
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
      PackageManager.DONT_KILL_APP
    )
  }
}
