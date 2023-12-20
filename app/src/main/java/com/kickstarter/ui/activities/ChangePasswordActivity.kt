package com.kickstarter.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kickstarter.libs.Logout
import com.kickstarter.libs.featureflag.FlagKey
import com.kickstarter.libs.utils.ApplicationUtils
import com.kickstarter.libs.utils.extensions.getEnvironment
import com.kickstarter.ui.IntentKey
import com.kickstarter.ui.SharedPreferenceKey
import com.kickstarter.ui.activities.compose.ChangePasswordScreen
import com.kickstarter.ui.compose.designsystem.KickstarterApp
import com.kickstarter.ui.data.LoginReason
import com.kickstarter.viewmodels.ChangePasswordViewModel
import com.kickstarter.viewmodels.ChangePasswordViewModelFactory
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.launch

class ChangePasswordActivity : ComponentActivity() {

    private var logout: Logout? = null
    private lateinit var disposables: CompositeDisposable
    private var theme = AppThemes.MATCH_SYSTEM.ordinal
    private lateinit var viewModelFactory: ChangePasswordViewModelFactory
    private val viewModel: ChangePasswordViewModel by viewModels {
        viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disposables = CompositeDisposable()
        var darkModeEnabled = false

        this.getEnvironment()?.let { env ->
            viewModelFactory = ChangePasswordViewModelFactory(env)

            darkModeEnabled = env.featureFlagClient()?.getBoolean(FlagKey.ANDROID_DARK_MODE_ENABLED) ?: false
            theme = env.sharedPreferences()
                ?.getInt(SharedPreferenceKey.APP_THEME, AppThemes.MATCH_SYSTEM.ordinal)
                ?: AppThemes.MATCH_SYSTEM.ordinal
        }

        setContent {
            var showProgressBar = viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)

            var error = viewModel.error.collectAsStateWithLifecycle(initialValue = "")

            var scaffoldState = rememberScaffoldState()

            KickstarterApp(
                useDarkTheme =
                if (darkModeEnabled) {
                    when (theme) {
                        AppThemes.MATCH_SYSTEM.ordinal -> isSystemInDarkTheme()
                        AppThemes.DARK.ordinal -> true
                        AppThemes.LIGHT.ordinal -> false
                        else -> false
                    }
                } else false
            ) {
                ChangePasswordScreen(
                    onBackClicked = { onBackPressedDispatcher.onBackPressed() },
                    onAcceptButtonClicked = { current, new ->
                        viewModel.updatePassword(current, new)
                    },
                    showProgressBar = showProgressBar.value,
                    scaffoldState = scaffoldState
                )
            }

            when {
                error.value.isNotEmpty() -> {
                    LaunchedEffect(scaffoldState) {
                        scaffoldState.snackbarHostState.showSnackbar(error.value)
                        viewModel.resetError()
                    }
                }
            }
        }

        this.logout = getEnvironment()?.logout()

        lifecycleScope.launch {
            viewModel.success.collect { email ->
                if (email.isNotEmpty()) logout(email)
            }
        }
    }

    private fun logout(email: String) {
        this.logout?.execute()
        ApplicationUtils.startNewDiscoveryActivity(this)
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(IntentKey.LOGIN_REASON, LoginReason.CHANGE_PASSWORD)
                .putExtra(IntentKey.EMAIL, email)
        )
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }
}
