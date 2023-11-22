package com.kickstarter.ui.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import com.kickstarter.R
import com.kickstarter.libs.BaseActivity
import com.kickstarter.libs.RefTag
import com.kickstarter.libs.qualifiers.RequiresActivityViewModel
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.utils.ApplicationUtils
import com.kickstarter.libs.utils.ThirdPartyEventValues
import com.kickstarter.libs.utils.UrlUtils.commentId
import com.kickstarter.libs.utils.UrlUtils.refTag
import com.kickstarter.libs.utils.UrlUtils.saveFlag
import com.kickstarter.libs.utils.extensions.getProjectIntent
import com.kickstarter.libs.utils.extensions.path
import com.kickstarter.ui.IntentKey
import com.kickstarter.ui.extensions.startPreLaunchProjectActivity
import com.kickstarter.viewmodels.DeepLinkViewModel
import rx.android.schedulers.AndroidSchedulers

@RequiresActivityViewModel(DeepLinkViewModel.ViewModel::class)
class DeepLinkActivity : BaseActivity<DeepLinkViewModel.ViewModel?>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setSplashScreenTheme(R.style.SplashTheme)
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                val slideUp = ObjectAnimator.ofFloat(
                    splashScreenView,
                    View.TRANSLATION_Y,
                    0f,
                    -splashScreenView.height.toFloat()
                )
                slideUp.interpolator = AnticipateInterpolator()
                slideUp.duration = 100L
            }
        }

        // - initialized on super will never be null within OnCreate context
        val viewModel = requireNotNull(this.viewModel)

        viewModel.outputs.startBrowser()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { url: String -> startBrowser(url) }

        viewModel.outputs.startDiscoveryActivity()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { startDiscoveryActivity() }

        viewModel.outputs.startProjectActivity()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { uri: Uri -> startProjectActivity(uri) }

        viewModel.outputs.startProjectActivityToSave()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { startProjectActivityForSave(it) }

        viewModel.outputs.startProjectActivityForComment()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { startProjectActivityForComment(it) }

        viewModel.outputs.startProjectActivityForUpdate()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { startProjectActivityForUpdate(it) }

        viewModel.outputs.startProjectActivityForCommentToUpdate()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { startProjectActivityForCommentToUpdate(it) }

        viewModel.outputs.startProjectActivityForCheckout()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { uri: Uri ->
                startProjectActivityForCheckout(
                    uri
                )
            }

        viewModel.outputs.finishDeeplinkActivity()
            .compose(bindToLifecycle())
            .compose(Transformers.observeForUI())
            .subscribe { finish() }

        viewModel.outputs.startPreLaunchProjectActivity()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                startPreLaunchProjectActivity(it, "DEEPLINK")
            }
    }

    private fun projectIntent(uri: Uri): Intent {
        val projectIntent = Intent().getProjectIntent(this)
            .setData(uri)
            .putExtra(IntentKey.PREVIOUS_SCREEN, ThirdPartyEventValues.ScreenName.DEEPLINK.value)
        val ref = refTag(uri.toString())
        if (ref != null) {
            projectIntent.putExtra(IntentKey.REF_TAG, RefTag.from(ref))
        }
        return projectIntent
    }

    private fun startDiscoveryActivity() {
        ApplicationUtils.startNewDiscoveryActivity(this)
        finish()
    }

    private fun startProjectActivity(uri: Uri) {
        startActivity(projectIntent(uri))
        finish()
    }

    private fun startProjectActivityForSave(uri: Uri) {
        val projectIntent = Intent().getProjectIntent(this)
            .setData(uri)
            .putExtra(IntentKey.DEEP_LINK_SCREEN_PROJECT_SAVE, true)
            .putExtra(IntentKey.PREVIOUS_SCREEN, ThirdPartyEventValues.ScreenName.DEEPLINK.value)

        saveFlag(uri.toString())?.let {
            projectIntent.putExtra(IntentKey.SAVE_FLAG_VALUE, it)
        }

        startActivity(projectIntent)
        finish()
    }

    private fun startProjectActivityForComment(uri: Uri) {
        val projectIntent = projectIntent(uri)
            .putExtra(IntentKey.DEEP_LINK_SCREEN_PROJECT_COMMENT, true)

        commentId(uri.toString())?.let {
            projectIntent.putExtra(IntentKey.COMMENT, it)
        }

        startActivity(projectIntent)
        finish()
    }

    private fun startProjectActivityForCommentToUpdate(uri: Uri) {
        val path = uri.path().split("/")

        val projectIntent = projectIntent(uri)
            .putExtra(IntentKey.DEEP_LINK_SCREEN_PROJECT_UPDATE, path[path.lastIndex - 1])
            .putExtra(IntentKey.DEEP_LINK_SCREEN_PROJECT_UPDATE_COMMENT, true)

        commentId(uri.toString())?.let {
            projectIntent.putExtra(IntentKey.COMMENT, it)
        }

        startActivity(projectIntent)
        finish()
    }

    private fun startProjectActivityForUpdate(uri: Uri) {
        val projectIntent = projectIntent(uri)
            .putExtra(IntentKey.DEEP_LINK_SCREEN_PROJECT_UPDATE, uri.lastPathSegment)

        startActivity(projectIntent)
        finish()
    }

    private fun startProjectActivityForCheckout(uri: Uri) {
        val projectIntent = projectIntent(uri)
            .putExtra(IntentKey.EXPAND_PLEDGE_SHEET, true)
        startActivity(projectIntent)
        finish()
    }

    private fun startBrowser(url: String) {
        ApplicationUtils.openUrlExternally(this, url)
        finish()
    }
}
