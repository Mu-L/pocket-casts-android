package au.com.shiftyjelly.pocketcasts.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava2.subscribeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.user.UserManager
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.utils.SystemBatteryRestrictions
import au.com.shiftyjelly.pocketcasts.utils.extensions.pxToDp
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : BaseFragment() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var batteryRestrictions: SystemBatteryRestrictions

    @Inject
    lateinit var settings: Settings

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        AppThemeWithBackground(theme.activeTheme) {
            var isUnrestrictedBattery by remember { mutableStateOf(batteryRestrictions.isUnrestricted()) }
            DisposableEffect(this) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        isUnrestrictedBattery = batteryRestrictions.isUnrestricted()
                    }
                }

                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            userManager
                .getSignInState()
                .subscribeAsState(null)
                .value
                ?.let { signInState ->
                    val bottomInset = settings.bottomInset.collectAsStateWithLifecycle(0)
                    SettingsFragmentPage(
                        signInState = signInState,
                        onBackPress = {
                            @Suppress("DEPRECATION")
                            activity?.onBackPressed()
                        },
                        isDebug = BuildConfig.DEBUG,
                        isUnrestrictedBattery = isUnrestrictedBattery,
                        openFragment = { fragment ->
                            (activity as? FragmentHostListener)?.addFragment(fragment)
                        },
                        bottomInset = bottomInset.value.pxToDp(LocalContext.current).dp,
                    )
                }
        }
    }
}
